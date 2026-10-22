"""Synthetic independent specimens: no private inventory, SDK corpus or held-out access."""
from contextlib import closing
import copy
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
import audit_ingestion as audit

# These field expectations are hand-authored independently of ingestion/extraction outputs.
LOCATOR=["SOURCE","p/T.html",None,"",None,None]
def block(kind,label,text):
    return ["absent","false","0",kind,label,text,"0"]
NARRATIVE=[block("PARAGRAPH","description","Narrative caveat."),block("NOTE","API Note","Must preserve this note.")]
MEMBER=[block("CODE","signature","void run()"),block("PARAGRAPH","description","Member caveat.")]

def section(blocks,kind="narrative"):
    return ["1",kind,"Documentation"]+LOCATOR+[str(len(blocks))]+sum(blocks,[])

def expectation(record_id,record_type,blocks):
    payload=audit.encode_fields(section(blocks,"api" if record_type=="API" else "narrative"))
    expected_blocks=[]
    for fields in blocks:
        expected_blocks.append({"kind":fields[3],"label":fields[4],"textSha256":audit.sha256(fields[5].encode()),"fieldsSha256":audit.sha256(audit.encode_fields(fields))})
    return {"recordId":record_id,"recordType":record_type,"member":record_type=="API","sections":[{"ordinal":0,"payloadSha256":audit.sha256(payload),"blocks":expected_blocks}]},payload

class AuditTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name);self.inventory=self.root/"inventory.json";self.store=self.root/"catalog.sqlite"
        narrative,one=expectation("a"*64,"NARRATIVE",NARRATIVE)
        member,two=expectation("b"*64,"API",MEMBER)
        self.data={"inventoryVersion":1,"buildId":"b-synthetic","sdkVersion":"1","independent":True,"inventoryComplete":True,"records":[narrative,member]}
        self.write_inventory()
        with closing(sqlite3.connect(self.store)) as db, db:
            db.executescript("CREATE TABLE build_metadata(build_id TEXT,sdk_version TEXT);CREATE TABLE knowledge_records(record_id TEXT PRIMARY KEY,record_type TEXT,sdk_version TEXT);CREATE TABLE documentation_sections(record_id TEXT,ordinal INTEGER,payload BLOB,content_hash TEXT);")
            db.execute("INSERT INTO build_metadata VALUES('b-synthetic','1')")
            for record,payload in [(narrative,one),(member,two)]:
                db.execute("INSERT INTO knowledge_records VALUES(?,?,'1')",(record["recordId"],record["recordType"]))
                db.execute("INSERT INTO documentation_sections VALUES(?,0,?,'unchanged-column')",(record["recordId"],payload))
    def write_inventory(self): self.inventory.write_text(json.dumps(self.data))
    def run_audit(self): return audit.audit(self.inventory,self.store,mode="synthetic")
    def mutate(self,sql,params=()):
        with closing(sqlite3.connect(self.store)) as db, db: db.execute(sql,params)
    def test_exact_independent_expectations_pass(self):
        result=self.run_audit();self.assertEqual("PASS",result["status"]);self.assertEqual(2,result["comparedSections"])
    def test_private_missing_gates_touch_neither_inventory_nor_store(self):
        with patch.object(Path,"open",side_effect=AssertionError("protected access")),patch.object(sqlite3,"connect",side_effect=AssertionError("protected access")):
            result=audit.audit("private-inventory","private-store")
        self.assertEqual("NOT_RUN",result["status"]);self.assertEqual({"MISSING_GATE":7},result["categories"])
    def test_every_private_gate_is_required(self):
        for missing in audit.GATES:
            gates=dict.fromkeys(audit.GATES,True);gates[missing]=False
            with patch.object(Path,"open",side_effect=AssertionError("protected access")):
                self.assertEqual("NOT_RUN",audit.audit(self.inventory,self.store,attestations=gates)["status"])
        self.assertEqual("PASS",audit.audit(self.inventory,self.store,attestations=dict.fromkeys(audit.GATES,True))["status"])
    def test_missing_member_and_narrative_are_distinct(self):
        self.mutate("DELETE FROM knowledge_records")
        self.assertEqual({"MISSING_MEMBER":1,"MISSING_NARRATIVE":1},self.run_audit()["categories"])
    def test_missing_note_detected_even_when_hash_column_unchanged(self):
        payload=audit.encode_fields(section(NARRATIVE[:1]))
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(payload,"a"*64))
        result=self.run_audit();self.assertEqual("DISCREPANCIES",result["status"])
        self.assertEqual(1,result["categories"]["MISSING_NOTE"]);self.assertEqual(1,result["categories"]["CHANGED_NARRATIVE"])
    def test_changed_note_detected_without_count_change(self):
        blocks=copy.deepcopy(NARRATIVE);blocks[1]=block("NOTE","API Note","Changed private sentinel.")
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(blocks)),"a"*64))
        result=self.run_audit();self.assertEqual(1,result["categories"]["CHANGED_NOTE"])
        self.assertNotIn("sentinel",json.dumps(result));self.assertNotIn("a"*64,json.dumps(result))
    def test_missing_member_block_detected(self):
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(MEMBER[:1],"api")),"b"*64))
        self.assertEqual(1,self.run_audit()["categories"]["MISSING_BLOCK"])
    def test_changed_member_block_detected(self):
        blocks=copy.deepcopy(MEMBER);blocks[1]=block("PARAGRAPH","description","Different.")
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(blocks,"api")),"b"*64))
        self.assertEqual(1,self.run_audit()["categories"]["CHANGED_BLOCK"])
    def test_missing_section_and_wrong_build_are_rejected(self):
        self.mutate("DELETE FROM documentation_sections WHERE record_id=?",("a"*64,))
        self.assertEqual(1,self.run_audit()["categories"]["MISSING_SECTION"])
        self.mutate("UPDATE build_metadata SET sdk_version='other'")
        self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_foreign_sdk_scope_cannot_pass(self):
        self.mutate("UPDATE knowledge_records SET sdk_version='other'")
        self.assertEqual(2,self.run_audit()["categories"]["CHANGED_RECORD_SCOPE"])
    def test_corrupt_actual_payload_is_sanitized(self):
        self.mutate("UPDATE documentation_sections SET payload=?",(b"proprietary-body-and-identifier",))
        result=self.run_audit();self.assertEqual({"INVALID_OR_UNREADABLE_INPUT":1},result["categories"])
        self.assertNotIn("proprietary",json.dumps(result))
    def test_read_only_audit_preserves_original_bytes(self):
        original=self.store.read_bytes();self.run_audit();self.assertEqual(original,self.store.read_bytes())
    def test_inventory_and_payload_limits(self):
        data=json.dumps(self.data).encode();self.inventory.write_bytes(data+b" "*(audit.MAX_INVENTORY_BYTES-len(data)))
        self.assertEqual("PASS",self.run_audit()["status"])
        self.inventory.write_bytes(b" "*(audit.MAX_INVENTORY_BYTES+1));self.assertEqual("BLOCKED",self.run_audit()["status"])
        with self.assertRaises(audit.AuditInvalid):audit.decode_fields(b"x"*(audit.MAX_PAYLOAD_BYTES+1))
        self.write_inventory();self.data["records"].append(self.data["records"][0]);self.write_inventory()
        self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_missing_inputs_and_raw_exception_text_never_leak(self):
        result=audit.audit(self.root/"private-name",self.root/"private-store",mode="synthetic")
        self.assertEqual("BLOCKED",result["status"]);self.assertNotIn("private",json.dumps(result))
        with patch.object(Path,"open",side_effect=OSError("private body identifier")):
            self.assertNotIn("private",json.dumps(self.run_audit()))

if __name__=="__main__":unittest.main()
