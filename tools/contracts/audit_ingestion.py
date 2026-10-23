"""Read-only, bounded fidelity comparison against an independently authored inventory.

Private mode checks every attestation before even opening inventory/store paths. Output
contains only fixed aggregate categories; no identifiers, source text or raw exceptions.
"""
import argparse
import base64
from collections import Counter
from contextlib import closing
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import struct

MAX_INVENTORY_BYTES = 8 * 1024 * 1024
MAX_PAYLOAD_BYTES = 1_310_724
MAX_RECORDS = 100_000
MAX_TOTAL_PAYLOAD_BYTES = 256 * 1024 * 1024
MAX_TOTAL_METADATA_BYTES = 16 * 1024 * 1024
MAX_SECTION_ROWS = 100_000
METADATA_COLUMNS = ("record_id,sdk_product,sdk_version,record_type,knowledge_kind,symbol_coordinate,"
                    "locator_kind,source_path,source_anchor,source_first_line,source_last_line,source_section,"
                    "absent_reason,approval,owner,provenance,approval_evidence,applicability,narrative_kind")
CATALOG_COLUMNS = "record_id,sdk_version,canonical_key,kind,type_fqn,simple_name,relative_path,anchor"
GATES = ("approval10_1", "approvalB2", "approvalB6", "corpusOwnerApproved",
         "developmentOnly", "heldOutExcluded", "independentInventoryAttested")

class AuditInvalid(ValueError):
    pass

def require(condition):
    if not condition:
        raise AuditInvalid()

def sha256(value):
    return hashlib.sha256(value).hexdigest()

def decode_fields(payload):
    require(isinstance(payload, bytes) and 4 <= len(payload) <= MAX_PAYLOAD_BYTES)
    offset = 0
    def number():
        nonlocal offset
        require(offset + 4 <= len(payload))
        result = struct.unpack_from(">i", payload, offset)[0]
        offset += 4
        return result
    count = number()
    require(0 <= count <= 65536 and count <= (len(payload)-4)//4)
    fields = []
    for _ in range(count):
        length = number()
        require(-1 <= length <= 1_048_576 and offset + max(length, 0) <= len(payload))
        fields.append(None if length == -1 else payload[offset:offset+length].decode("utf-8", errors="strict"))
        offset += max(length, 0)
    require(offset == len(payload))
    return fields

def decode_section(payload):
    fields = decode_fields(payload)
    position = 0
    def take():
        nonlocal position
        require(position < len(fields))
        value = fields[position]
        position += 1
        return value
    def count(maximum):
        value = take()
        require(isinstance(value, str) and value.isascii() and value.isdecimal())
        value = int(value)
        require(0 <= value <= maximum and value <= len(fields)-position)
        return value
    def locator():
        values = [take() for _ in range(6)]
        require(values[0] in ("SOURCE", "EXTERNAL", "ABSENT"))
    def items(depth=0):
        require(depth <= 32)
        for _ in range(count(65536)):
            require(isinstance(take(), str))
            items(depth+1)
    require(count(256) == 1)
    section_id, title = take(), take()
    require(isinstance(section_id,str) and section_id and isinstance(title,str))
    locator()
    blocks = []
    for _ in range(count(4096)):
        start = position
        marker = take()
        require(marker in ("table", "absent"))
        if marker == "table":
            width = count(65536)
            for _ in range(width): take()
            rows = count(65536)
            require(width * rows <= 65536)
            for _ in range(width * rows): take()
        require(take() in ("true", "false"))
        items()
        kind, label, text = take(), take(), take()
        require(kind in ("PARAGRAPH", "CODE", "TABLE", "LIST", "NOTE"))
        require(isinstance(label,str) and isinstance(text,str))
        for _ in range(count(65536)):
            take(); take(); take(); locator()
        blocks.append({"kind":kind, "label":label, "textSha256":sha256(text.encode("utf-8")),
                       "fieldsSha256":sha256(encode_fields(fields[start:position]))})
    require(position == len(fields))
    return blocks

def encode_fields(fields):
    result = bytearray(struct.pack(">i", len(fields)))
    for field in fields:
        value = None if field is None else field.encode("utf-8")
        result.extend(struct.pack(">i", -1 if value is None else len(value)))
        if value is not None: result.extend(value)
    return bytes(result)

def _hash(value):
    return isinstance(value,str) and re.fullmatch(r"[0-9a-f]{64}",value) is not None

def validate_inventory(data):
    require(isinstance(data,dict) and data.get("inventoryVersion") == 1)
    require(isinstance(data.get("buildId"),str) and 0 < len(data["buildId"]) <= 256)
    require(isinstance(data.get("sdkVersion"),str) and 0 < len(data["sdkVersion"]) <= 256)
    require(data.get("independent") is True and data.get("inventoryComplete") is True)
    records = data.get("records")
    require(isinstance(records,list) and len(records) <= MAX_RECORDS)
    ids = set()
    for record in records:
        require(isinstance(record,dict) and _hash(record.get("recordId")))
        require(record["recordId"] not in ids); ids.add(record["recordId"])
        require(record.get("recordType") in ("API", "NARRATIVE"))
        require(isinstance(record.get("member"),bool))
        require(isinstance(record.get("sections"),list) and 0 < len(record["sections"]) <= 256)
        for section in record["sections"]:
            require(isinstance(section,dict) and type(section.get("ordinal")) is int and 0 <= section["ordinal"] <= 255 and _hash(section.get("payloadSha256")))
            require(isinstance(section.get("blocks"),list) and len(section["blocks"]) <= 4096)
            for block in section["blocks"]:
                require(isinstance(block,dict) and block.get("kind") in ("PARAGRAPH","CODE","TABLE","LIST","NOTE"))
                require(isinstance(block.get("label"),str) and len(block["label"]) <= 4096)
                require(_hash(block.get("textSha256")) and _hash(block.get("fieldsSha256")))
    return data

def _digest(domain, fields):
    digest=hashlib.sha256()
    for field in [domain]+list(fields):
        raw=None if field is None else field.encode("utf-8")
        digest.update(b"-1:" if raw is None else str(len(raw)).encode()+b":"+raw)
    return digest.hexdigest()

def _length(column):
    return "length(CAST("+column+" AS BLOB))"

def _metadata_association(fields, catalog, sdk):
    require(len(fields)==19 and fields[1:5]==["LuciadLightspeed",sdk,fields[3],"AUTHORITATIVE_FACT"])
    require(fields[3] in ("API","NARRATIVE") and fields[6]=="SOURCE")
    path=fields[7]
    require(isinstance(path,str) and path and not path.startswith("/") and "\\" not in path and ":" not in path and ".." not in path.split("/"))
    require(fields[8] is None or isinstance(fields[8],str) and fields[8].strip())
    require(fields[13]=="APPROVED" and all(isinstance(f,str) and f.strip() for f in fields[14:17]))
    require((fields[9] is None)==(fields[10] is None))
    if fields[9] is not None: require(1 <= int(fields[9]) <= int(fields[10]))
    applicability=decode_fields(base64.b64decode(fields[17],validate=True))
    require(applicability[:4]==["LuciadLightspeed","VERSION_SCOPED","1",sdk])
    require(all(isinstance(f,str) for f in applicability))
    if fields[3]=="NARRATIVE":
        require(catalog is None and fields[5] is None and fields[18] in ("PACKAGE","OVERVIEW"))
        require(fields[0]==_digest("narrative/1",["LuciadLightspeed",sdk,path,fields[18]]))
    else:
        require(catalog is not None and fields[18] is None)
        require(all(isinstance(value,str) for value in catalog))
        record_id,version,key,kind,owner,simple,source,anchor=catalog
        require(version==sdk and kind in ("TYPE","METHOD","CONSTRUCTOR","FIELD","ENUM_CONSTANT"))
        owner_key,separator,member=key.partition("#")
        parameters=[]
        if "(" in member:
            member,arguments=member.split("(",1)
            require(arguments.endswith(")"));parameters=arguments[:-1].split(",") if arguments[:-1] else []
        coordinate=["LuciadLightspeed",sdk,"java",owner_key.rpartition(".")[0],owner_key,member,kind,str(len(parameters))]+parameters
        wire="symbol:1:"+".".join(base64.urlsafe_b64encode(f.encode()).decode().rstrip("=") for f in coordinate)
        require(fields[5]==wire and source==path and anchor==(fields[8] or ""))
        require(fields[0]==record_id==_digest("identity/1/record",["ApiElement" if kind=="TYPE" else "MethodElement",wire,sdk]))

def _admit_store(db, sdk):
    # Admit all metadata before fetching any untrusted text or blobs. This includes unexpected rows.
    total_metadata=0
    for table,columns in (("knowledge_records",METADATA_COLUMNS),("records",CATALOG_COLUMNS),
                          ("documentation","record_id,source_hash,content_hash,section_count,metadata")):
        query="SELECT "+",".join(_length(c) for c in columns.split(","))+" FROM "+table+" LIMIT ?"
        rows=0
        for lengths in db.execute(query,(MAX_RECORDS+1,)):
            rows+=1;require(rows<=MAX_RECORDS)
            sizes=[0 if n is None else n for n in lengths]
            require(all(type(n) is int and 0 <= n <= MAX_PAYLOAD_BYTES for n in sizes) and sum(sizes)<=MAX_PAYLOAD_BYTES)
            total_metadata+=sum(sizes);require(total_metadata<=MAX_TOTAL_METADATA_BYTES)
    actual={row[0]:row[1:] for row in db.execute("SELECT record_id,record_type,sdk_version FROM knowledge_records LIMIT ?",(MAX_RECORDS+1,))}
    require(all(_hash(key) for key in actual))
    parents={row[0]:row[1:] for row in db.execute("SELECT record_id,source_hash,content_hash,section_count FROM documentation LIMIT ?",(MAX_RECORDS+1,))}
    require(all(_hash(key) and _hash(row[0]) and _hash(row[1]) and type(row[2]) is int and 1<=row[2]<=256 for key,row in parents.items()))
    catalogs={row[0]:row for row in db.execute("SELECT "+CATALOG_COLUMNS+" FROM records LIMIT ?",(MAX_RECORDS+1,))}
    require(set(parents)==set(actual) and set(catalogs)<=set(actual))
    lengths={};total_bytes=0;rows=0
    # Admission precedes every payload SELECT, including all unexpected record/section payloads.
    for record_id,ordinal,size,hash_size in db.execute("SELECT CASE WHEN length(CAST(record_id AS BLOB))=64 THEN record_id END,CASE WHEN typeof(ordinal)='integer' THEN ordinal END,"+_length("payload")+","+_length("content_hash")+" FROM documentation_sections LIMIT ?",(MAX_SECTION_ROWS+1,)):
        rows+=1;require(rows<=MAX_SECTION_ROWS and record_id in parents)
        require(type(ordinal) is int and 0 <= ordinal < 256 and type(size) is int and 4 <= size <= MAX_PAYLOAD_BYTES)
        total_bytes+=size;require(total_bytes<=MAX_TOTAL_PAYLOAD_BYTES)
        require(hash_size==64)
        total_metadata+=hash_size;require(total_metadata<=MAX_TOTAL_METADATA_BYTES)
        selected=lengths.setdefault(record_id,{})
        require(ordinal not in selected);selected[ordinal]=size
    metadata={}
    for record_id,parent in parents.items():
        selected=lengths.get(record_id,{})
        require(sorted(selected)==list(range(parent[2])) and sum(selected.values())<=MAX_PAYLOAD_BYTES)
        fields=decode_fields(db.execute("SELECT metadata FROM documentation WHERE record_id=?",(record_id,)).fetchone()[0])
        row=db.execute("SELECT "+METADATA_COLUMNS+" FROM knowledge_records WHERE record_id=?",(record_id,)).fetchone()
        require(fields==[None if f is None else str(f) for f in row])
        _metadata_association(fields,catalogs.get(record_id),sdk)
        metadata[record_id]=fields
    return actual,parents,lengths,metadata

def report(status, categories, records=0, sections=0):
    return {"auditVersion":1,"status":status,"categories":dict(sorted(categories.items())),
            "comparedRecords":records,"comparedSections":sections,"technicalRatification":"PENDING"}

def audit(inventory_path, store_path, *, mode="private", attestations=None):
    if mode not in ("private","synthetic"):
        return report("BLOCKED",{"INVALID_MODE":1})
    if mode == "private":
        missing = sum((attestations or {}).get(gate) is not True for gate in GATES)
        if missing:
            return report("NOT_RUN",{"MISSING_GATE":missing})
    try:
        with Path(inventory_path).open("rb") as source:
            raw = source.read(MAX_INVENTORY_BYTES+1)
        require(len(raw) <= MAX_INVENTORY_BYTES)
        data = validate_inventory(json.loads(raw))
        # SQLite read-only URI prevents creating a missing store. One transaction pins all comparisons.
        uri = Path(store_path).resolve().as_uri() + "?mode=ro"
        categories = Counter()
        compared_records = compared_sections = total_bytes = 0
        with closing(sqlite3.connect(uri,uri=True)) as db:
            db.setlimit(sqlite3.SQLITE_LIMIT_LENGTH,MAX_PAYLOAD_BYTES + 4096)
            steps = 0
            def progress():
                nonlocal steps
                steps += 1000
                return int(steps > 10_000_000)
            db.set_progress_handler(progress,1000)
            db.execute("PRAGMA query_only=ON")
            db.execute("BEGIN")
            binding_lengths=db.execute("SELECT "+_length("build_id")+","+_length("sdk_version")+" FROM build_metadata LIMIT 2").fetchmany(2)
            require(len(binding_lengths)==1 and all(type(n) is int and 0<n<=1024 for n in binding_lengths[0]))
            binding = db.execute("SELECT build_id,sdk_version FROM build_metadata LIMIT 2").fetchmany(2)
            require(binding == [(data["buildId"],data["sdkVersion"])])
            actual_records,parents,section_lengths,metadata = _admit_store(db,data["sdkVersion"])
            expected_ids = {r["recordId"] for r in data["records"]}
            categories["UNEXPECTED_RECORD"] += len(set(actual_records)-expected_ids)
            for record in data["records"]:
                record_id = record["recordId"]
                if record_id not in actual_records:
                    category = "MISSING_NARRATIVE" if record["recordType"] == "NARRATIVE" else "MISSING_MEMBER" if record["member"] else "MISSING_RECORD"
                    categories[category] += 1
                    continue
                compared_records += 1
                if actual_records[record_id][0] != record["recordType"]: categories["CHANGED_RECORD_TYPE"] += 1
                if actual_records[record_id][1] != data["sdkVersion"]: categories["CHANGED_RECORD_SCOPE"] += 1
                # Lengths of every stored section already passed aggregate admission in _admit_store.
                rows = db.execute("SELECT ordinal,payload,content_hash FROM documentation_sections WHERE record_id=? ORDER BY ordinal LIMIT 257",(record_id,)).fetchmany(257)
                actual_sections = {ordinal:payload for ordinal,payload,_ in rows}
                canonical=[str(len(rows))]
                for ordinal,payload,stored_hash in rows:
                    fields=decode_fields(payload)
                    require(fields[0]=="1")
                    expected_locator=[metadata[record_id][6],metadata[record_id][7],metadata[record_id][8],metadata[record_id][12],
                                      None if metadata[record_id][9] is None else metadata[record_id][9]+":"+metadata[record_id][10],metadata[record_id][11]]
                    require(fields[3:9]==expected_locator)
                    if _digest("content/1",fields)!=stored_hash: categories["CORRUPT_SECTION_HASH"]+=1
                    canonical.extend(fields[1:])
                if _digest("content/1",canonical)!=parents[record_id][1]: categories["CORRUPT_CONTENT_HASH"]+=1
                categories["UNEXPECTED_SECTION"] += len(set(actual_sections)-{s["ordinal"] for s in record["sections"]})
                narrative_changed = False
                for section in record["sections"]:
                    payload = actual_sections.get(section["ordinal"])
                    if payload is None:
                        categories["MISSING_SECTION"] += 1; narrative_changed = True
                        continue
                    compared_sections += 1
                    if sha256(payload) != section["payloadSha256"]:
                        categories["CHANGED_SECTION"] += 1; narrative_changed = True
                    blocks = decode_section(payload)
                    expected = section["blocks"]
                    categories["UNEXPECTED_BLOCK"] += max(0,len(blocks)-len(expected))
                    for i, block in enumerate(expected):
                        note = block["kind"] == "NOTE"
                        if i >= len(blocks): categories["MISSING_NOTE" if note else "MISSING_BLOCK"] += 1
                        elif blocks[i] != block: categories["CHANGED_NOTE" if note else "CHANGED_BLOCK"] += 1
                if narrative_changed and record["recordType"] == "NARRATIVE": categories["CHANGED_NARRATIVE"] += 1
        categories = +categories
        return report("DISCREPANCIES" if categories else "PASS",categories,compared_records,compared_sections)
    except (OSError, ValueError, TypeError, KeyError, OverflowError, RecursionError, sqlite3.Error):
        # Do not echo an exception: SQLite/path/JSON messages may include protected identifiers/bodies.
        return report("BLOCKED",{"INVALID_OR_UNREADABLE_INPUT":1})

class AggregateArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        print(json.dumps(report("BLOCKED",{"INVALID_ARGUMENTS":1}),sort_keys=True,separators=(",",":")))
        raise SystemExit(2)

def main(argv=None):
    parser=AggregateArgumentParser(description=__doc__)
    parser.add_argument("--inventory",required=True)
    parser.add_argument("--store",required=True)
    parser.add_argument("--synthetic",action="store_true",help="Caller attests both inputs contain synthetic data only")
    for gate in GATES: parser.add_argument("--"+gate,action="store_true")
    args=parser.parse_args(argv)
    result=audit(args.inventory,args.store,mode="synthetic" if args.synthetic else "private",attestations={gate:getattr(args,gate) for gate in GATES})
    print(json.dumps(result,sort_keys=True,separators=(",",":")))
    return 0 if result["status"] == "PASS" else 2

if __name__ == "__main__":
    raise SystemExit(main())
