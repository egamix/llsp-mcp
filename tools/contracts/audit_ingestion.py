"""Read-only, bounded fidelity comparison against an independently authored inventory.

Private mode checks every attestation before even opening inventory/store paths. Output
contains only fixed aggregate categories; no identifiers, source text or raw exceptions.
"""
import argparse
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
        for ordinal, section in enumerate(record["sections"]):
            require(isinstance(section,dict) and section.get("ordinal") == ordinal and _hash(section.get("payloadSha256")))
            require(isinstance(section.get("blocks"),list) and len(section["blocks"]) <= 4096)
            for block in section["blocks"]:
                require(isinstance(block,dict) and block.get("kind") in ("PARAGRAPH","CODE","TABLE","LIST","NOTE"))
                require(isinstance(block.get("label"),str) and len(block["label"].encode("utf-8")) <= 4096)
                require(_hash(block.get("textSha256")) and _hash(block.get("fieldsSha256")))
    return data

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
            binding = db.execute("SELECT build_id,sdk_version FROM build_metadata LIMIT 2").fetchmany(2)
            require(binding == [(data["buildId"],data["sdkVersion"])])
            actual_records = {row[0]:row[1:] for row in db.execute("SELECT record_id,record_type,sdk_version FROM knowledge_records LIMIT ?",(MAX_RECORDS+1,))}
            require(len(actual_records) <= MAX_RECORDS)
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
                # Read actual bounded blobs, never trust content_hash columns as the oracle.
                rows = db.execute("SELECT ordinal,payload FROM documentation_sections WHERE record_id=? ORDER BY ordinal LIMIT 257",(record_id,)).fetchmany(257)
                require(len(rows) <= 256)
                actual_sections = dict(rows)
                categories["UNEXPECTED_SECTION"] += len(set(actual_sections)-{s["ordinal"] for s in record["sections"]})
                narrative_changed = False
                for section in record["sections"]:
                    payload = actual_sections.get(section["ordinal"])
                    if payload is None:
                        categories["MISSING_SECTION"] += 1; narrative_changed = True
                        continue
                    total_bytes += len(payload)
                    require(total_bytes <= MAX_TOTAL_PAYLOAD_BYTES)
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
