"""Synthetic independent specimens: no private inventory, SDK corpus or held-out access."""
from contextlib import closing
import copy
import base64
import os
import shutil
import jsonschema
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
        self.narrative_id=audit._digest("narrative/1",["LuciadLightspeed","1","p/T.html","PACKAGE"])
        coordinate=["LuciadLightspeed","1","java","p","p.T","run","METHOD","0"]
        self.wire="symbol:1:"+".".join(base64.urlsafe_b64encode(f.encode()).decode().rstrip("=") for f in coordinate)
        self.member_id=audit._digest("identity/1/record",["MethodElement",self.wire,"1"])
        narrative,one=expectation(self.narrative_id,"NARRATIVE",NARRATIVE)
        member,two=expectation(self.member_id,"API",MEMBER)
        self.data={"inventoryVersion":1,"buildId":"b-synthetic","sdkVersion":"1","independent":True,"inventoryComplete":True,"records":[narrative,member]}
        self.write_inventory()
        with closing(sqlite3.connect(self.store)) as db, db:
            knowledge=",".join(c+" TEXT" for c in audit.METADATA_COLUMNS.split(","))
            catalog=",".join(c+" TEXT" for c in audit.CATALOG_COLUMNS.split(","))
            db.executescript("CREATE TABLE build_metadata(build_id TEXT,sdk_version TEXT);CREATE TABLE knowledge_records("+knowledge+");CREATE TABLE records("+catalog+");CREATE TABLE documentation(record_id TEXT PRIMARY KEY,source_hash TEXT,content_hash TEXT,section_count INTEGER,metadata BLOB);CREATE TABLE documentation_sections(record_id TEXT,ordinal INTEGER,payload BLOB,content_hash TEXT);")
            db.execute("INSERT INTO build_metadata VALUES('b-synthetic','1')")
            applicability=base64.b64encode(audit.encode_fields(["LuciadLightspeed","VERSION_SCOPED","1","1"])).decode()
            for record,payload in [(narrative,one),(member,two)]:
                is_api=record["recordType"]=="API"
                metadata=[record["recordId"],"LuciadLightspeed","1",record["recordType"],"AUTHORITATIVE_FACT",self.wire if is_api else None,
                          "SOURCE","p/T.html",None,None,None,None,"","APPROVED","javadoc","local Javadoc source","source API documentation" if is_api else "source narrative documentation",applicability,None if is_api else "PACKAGE"]
                db.execute("INSERT INTO knowledge_records VALUES("+",".join("?" for _ in metadata)+")",metadata)
                if is_api: db.execute("INSERT INTO records VALUES(?,?,?,?,?,?,?,?)",(record["recordId"],"1","p.T#run()","METHOD","p.T","run","p/T.html",""))
                content=audit._digest("content/1",audit.decode_fields(payload))
                db.execute("INSERT INTO documentation VALUES(?,?,?,1,?)",(record["recordId"],"0"*64,content,audit.encode_fields(metadata)))
                db.execute("INSERT INTO documentation_sections VALUES(?,0,?,?)",(record["recordId"],payload,content))
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
        for table in ("documentation_sections","documentation","knowledge_records","records"):self.mutate("DELETE FROM "+table)
        self.assertEqual({"MISSING_MEMBER":1,"MISSING_NARRATIVE":1},self.run_audit()["categories"])
    def test_missing_note_detected_even_when_hash_column_unchanged(self):
        payload=audit.encode_fields(section(NARRATIVE[:1]))
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(payload,self.narrative_id))
        result=self.run_audit();self.assertEqual("DISCREPANCIES",result["status"])
        self.assertEqual(1,result["categories"]["MISSING_NOTE"]);self.assertEqual(1,result["categories"]["CHANGED_NARRATIVE"])
    def test_changed_note_detected_without_count_change(self):
        blocks=copy.deepcopy(NARRATIVE);blocks[1]=block("NOTE","API Note","Changed private sentinel.")
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(blocks)),self.narrative_id))
        result=self.run_audit();self.assertEqual(1,result["categories"]["CHANGED_NOTE"])
        self.assertNotIn("sentinel",json.dumps(result));self.assertNotIn(self.narrative_id,json.dumps(result))
    def test_missing_member_block_detected(self):
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(MEMBER[:1],"api")),self.member_id))
        self.assertEqual(1,self.run_audit()["categories"]["MISSING_BLOCK"])
    def test_changed_member_block_detected(self):
        blocks=copy.deepcopy(MEMBER);blocks[1]=block("PARAGRAPH","description","Different.")
        self.mutate("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(section(blocks,"api")),self.member_id))
        self.assertEqual(1,self.run_audit()["categories"]["CHANGED_BLOCK"])
    def test_missing_section_and_wrong_build_are_rejected(self):
        self.mutate("DELETE FROM documentation_sections WHERE record_id=?",(self.narrative_id,))
        self.assertNotEqual("PASS",self.run_audit()["status"])
        self.mutate("UPDATE build_metadata SET sdk_version='other'")
        self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_foreign_sdk_scope_cannot_pass(self):
        self.mutate("UPDATE knowledge_records SET sdk_version='other'")
        self.assertEqual("BLOCKED",self.run_audit()["status"])
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

    def test_documentation_parent_count_and_ordinals_are_required(self):
        original=self.store.read_bytes()
        mutations=[("DELETE FROM documentation WHERE record_id=?",(self.narrative_id,)),
                   ("UPDATE documentation SET section_count=2 WHERE record_id=?",(self.narrative_id,)),
                   ("UPDATE documentation_sections SET ordinal=1 WHERE record_id=?",(self.narrative_id,)),
                   ("UPDATE documentation SET metadata=? WHERE record_id=?",(b"broken",self.narrative_id))]
        for sql,params in mutations:
            self.store.write_bytes(original);self.mutate(sql,params)
            self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_all_payloads_are_admitted_before_any_blob_fetch(self):
        real_connect=sqlite3.connect;queries=[]
        class Connection:
            def __init__(self,*args,**kwargs):self.db=real_connect(*args,**kwargs)
            def __getattr__(self,name):return getattr(self.db,name)
            def execute(self,query,*args):
                queries.append(query)
                self.assertion(query)
                return self.db.execute(query,*args)
            @staticmethod
            def assertion(query):
                if query.startswith("SELECT metadata ") or query.startswith("SELECT ordinal,payload,"):raise AssertionError("blob fetched before byte admission")
        with patch.object(audit,"MAX_TOTAL_PAYLOAD_BYTES",1),patch.object(sqlite3,"connect",Connection):
            self.assertEqual("BLOCKED",self.run_audit()["status"])
        # Unexpected section payload must also consume the aggregate budget before materialization.
        size=sum(len(audit.encode_fields(section(b,"api" if t=="API" else "narrative"))) for t,b in [("NARRATIVE",NARRATIVE),("API",MEMBER)])
        with patch.object(audit,"MAX_TOTAL_PAYLOAD_BYTES",size):
            self.assertEqual("PASS",self.run_audit()["status"])
        extra=audit.encode_fields(section(NARRATIVE))
        self.mutate("INSERT INTO documentation_sections VALUES(?,1,?,?)",(self.narrative_id,extra,audit._digest("content/1",audit.decode_fields(extra))))
        self.mutate("UPDATE documentation SET section_count=2 WHERE record_id=?",(self.narrative_id,))
        with patch.object(audit,"MAX_TOTAL_PAYLOAD_BYTES",size),patch.object(sqlite3,"connect",Connection):
            self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_metadata_lengths_are_admitted_before_fields_or_blob_fetch(self):
        real_connect=sqlite3.connect
        class Connection:
            def __init__(self,*args,**kwargs):self.db=real_connect(*args,**kwargs)
            def __getattr__(self,name):return getattr(self.db,name)
            def execute(self,query,*args):
                if query.startswith("SELECT record_id,") or query.startswith("SELECT metadata "):
                    raise AssertionError("metadata fetched before length admission")
                return self.db.execute(query,*args)
        with patch.object(audit,"MAX_TOTAL_METADATA_BYTES",1),patch.object(sqlite3,"connect",Connection):
            self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_inventory_reordered_ordinals_and_multibyte_labels_match_schema(self):
        schema=json.loads((Path(__file__).resolve().parents[2]/"docs/contracts/ingestion-audit-inventory.schema.json").read_text())
        positive=copy.deepcopy(self.data)
        first=positive["records"][0]["sections"][0]
        second=copy.deepcopy(first);second["ordinal"]=1
        first["blocks"][0]["label"]="\u754c"*4096
        positive["records"][0]["sections"]=[second,first]
        jsonschema.validate(positive,schema);audit.validate_inventory(positive)
        for mutation in ("ordinal","label"):
            negative=copy.deepcopy(positive)
            if mutation=="ordinal":negative["records"][0]["sections"][0]["ordinal"]=256
            else:negative["records"][0]["sections"][1]["blocks"][0]["label"]+="x"
            with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(negative,schema)
            with self.assertRaises(audit.AuditInvalid):audit.validate_inventory(negative)


class JavaStoreAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        export=os.environ.get("LUCIAD_AUDIT_STORE")
        if not export or not Path(export).is_file():
            raise unittest.SkipTest("Export CoverageIntegrationTest.independentAuditSpecimenUsesRealStoredPayload with LUCIAD_AUDIT_STORE first")
        cls.original=Path(export).read_bytes()
        cls.inventory_path=Path(__file__).resolve().parents[2]/"docs/contracts/fixtures/ingestion-audit.synthetic.json"
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.store=Path(self.temporary.name)/"catalog.sqlite";self.store.write_bytes(self.original)
    def run_audit(self):return audit.audit(self.inventory_path,self.store,mode="synthetic")
    def mutate(self,sql,params=()):
        with closing(sqlite3.connect(self.store)) as db,db:db.execute(sql,params)
    def test_real_java_structured_payload_passes_independent_expectations(self):
        result=self.run_audit();self.assertEqual("PASS",result["status"]);self.assertEqual(1,result["comparedSections"])
    def test_real_parent_removal_count_hash_and_metadata_corruption_cannot_pass(self):
        for sql,params in [("DELETE FROM documentation",()),("UPDATE documentation SET section_count=2",()),
                           ("UPDATE documentation SET source_hash='bad'",()),("UPDATE documentation SET content_hash=?",("0"*64,)),
                           ("UPDATE documentation SET metadata=?",(b"corrupt",))]:
            self.store.write_bytes(self.original);self.mutate(sql,params)
            self.assertNotEqual("PASS",self.run_audit()["status"])
    def test_all_19_real_knowledge_columns_are_bound_to_parent_metadata(self):
        replacements={"record_id":"f"*64,"sdk_product":"Other","sdk_version":"other","record_type":"API","knowledge_kind":"RECIPE",
                      "symbol_coordinate":"symbol:1:bad","locator_kind":"ABSENT","source_path":"other.html","source_anchor":"changed",
                      "source_first_line":1,"source_last_line":2,"source_section":"changed","absent_reason":"changed","approval":"CANDIDATE",
                      "owner":"changed","provenance":"changed","approval_evidence":"changed","applicability":"AAAA","narrative_kind":"OVERVIEW"}
        self.assertEqual(set(audit.METADATA_COLUMNS.split(",")),set(replacements))
        for column,value in replacements.items():
            with self.subTest(column=column):
                self.store.write_bytes(self.original);self.mutate("UPDATE knowledge_records SET "+column+"=?",(value,))
                self.assertEqual("BLOCKED",self.run_audit()["status"])
    def test_real_table_nested_list_and_link_mutations_are_detected(self):
        for before,after in [("Key","Changed header"),("Value","Changed cell"),("Nested","Changed nested item"),
                             ("package-summary.html#target","package-summary.html#other"),("target","other-anchor")]:
            self.store.write_bytes(self.original)
            with closing(sqlite3.connect(self.store)) as db,db:
                record_id,payload=db.execute("SELECT record_id,payload FROM documentation_sections").fetchone()
                fields=audit.decode_fields(payload);self.assertIn(before,fields)
                fields[fields.index(before)]=after
                db.execute("UPDATE documentation_sections SET payload=? WHERE record_id=?",(audit.encode_fields(fields),record_id))
            result=self.run_audit();self.assertEqual("DISCREPANCIES",result["status"])
            self.assertGreaterEqual(result["categories"].get("CHANGED_BLOCK",0),1)

if __name__=="__main__":unittest.main()
