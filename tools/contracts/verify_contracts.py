"""Validate exact six-tool schemas/examples and selected adverse request/envelope specimens."""
import copy, json
from pathlib import Path
import jsonschema
from referencing import Registry, Resource
ROOT=Path(__file__).resolve().parents[2]
DIRECTORY=ROOT/"docs/contracts"
TOOLS=("search_knowledge","resolve_symbol","get_record","get_relationships","check_symbols","index_status")
registry=Registry()
for path in DIRECTORY.glob("*.schema.json"):
    schema=json.loads(path.read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator.check_schema(schema)
    registry=registry.with_resource(path.name,Resource.from_contents(schema))
def validator(tool,kind):
    schema=json.loads((DIRECTORY/f"{tool}.{kind}.schema.json").read_text())
    return jsonschema.Draft202012Validator(schema,registry=registry)
def semantic(response):
    assert len(json.dumps(response,ensure_ascii=True).encode())<=1048576
    def visit(value,depth=0):
        assert depth<=64
        if isinstance(value,list):
            for child in value:visit(child,depth+1)
        elif isinstance(value,dict):
            if "first" in value and "last" in value:assert value["last"]>=value["first"]
            if "recordId" in value and "sdkVersion" in value and "buildId" in value and "locator" in value:
                assert value["sdkVersion"]==response["sdkVersion"] and value["buildId"]==response["buildId"]
            if "selectedRecordId" in value:
                assert (value["selectedRecordId"] is not None)==(value["proof"]=="EXACT")
            if "headers" in value and "rows" in value:assert all(len(row)==len(value["headers"]) for row in value["rows"])
            for child in value.values():visit(child,depth+1)
    visit(response)
def main():
    checked=0
    for tool in TOOLS:
        example=json.loads((DIRECTORY/f"{tool}.example.json").read_text())
        for kind in ("request","response"):
            validator(tool,kind).validate(example[kind]); checked+=1
        semantic(example["response"])
        for field,value in (("pageSize",51),("pageSize",0),("contractVersion","read-tools/999"),("admin",True)):
            bad=copy.deepcopy(example["request"]);bad[field]=value
            assert list(validator(tool,"request").iter_errors(bad)),(tool,field)
            checked+=1
        bad=copy.deepcopy(example["response"]);bad["counts"]["relation"]="ESTIMATE_AS_EXACT"
        assert list(validator(tool,"response").iter_errors(bad));checked+=1
        assert len(json.dumps(example["response"],ensure_ascii=True).encode())<=1048576
    for schema_name,example_name in (("manifest.schema.json","manifest.example.json"),("ingestion-report.schema.json","ingestion-report.example.json")):
        jsonschema.Draft202012Validator(json.loads((DIRECTORY/schema_name).read_text())).validate(json.loads((DIRECTORY/example_name).read_text()));checked+=1
    common=json.loads((DIRECTORY/"common.schema.json").read_text())
    def invalid(definition,value):
        schema={"$schema":common["$schema"],"$id":"adverse.schema.json","$ref":"common.schema.json#/$defs/"+definition}
        assert list(jsonschema.Draft202012Validator(schema,registry=registry).iter_errors(value))
    for relation,value in (("UNKNOWN",2),("EXACT",None),("LOWER_BOUND",None)):
        invalid("count",{"value":value,"relation":relation});checked+=1
    for locator in (
        {"kind":"SOURCE","relativePath":"/absolute","anchor":None,"reason":"","lines":None,"section":None},
        {"kind":"SOURCE","relativePath":"../escape","anchor":None,"reason":"","lines":None,"section":None},
        {"kind":"ABSENT","relativePath":"","anchor":"fake","reason":"","lines":None,"section":None}):
        invalid("locator",locator);checked+=1
    for family,kind in (("SDK","REQUIRED"),("KNOWLEDGE_DEPENDENCY","SEE_ALSO"),("SDK","invented")):
        bad={"contractVersion":"read-tools/1","sdkVersion":"2024.0","seed":"T","family":family,"types":[kind]}
        assert list(validator("get_relationships","request").iter_errors(bad));checked+=1
    populated=json.loads((DIRECTORY/"get_record.example.json").read_text())
    assert [r["inputRecordId"] for r in populated["response"]["data"]["records"]]==populated["request"]["recordIds"];checked+=1
    bad=copy.deepcopy(populated["response"]);bad["data"]["records"][0]["record"]["citations"][0]["buildId"]="wrong-build"
    try:semantic(bad)
    except AssertionError:checked+=1
    else:raise AssertionError("cross-build citation accepted")
    bad=copy.deepcopy(populated["response"]);bad["data"]["records"][0]["record"]["citations"]=[]
    assert list(validator("get_record","response").iter_errors(bad));checked+=1
    error=json.loads((DIRECTORY/"search_knowledge.error.example.json").read_text())
    validator("search_knowledge","response").validate(error["response"]);semantic(error["response"]);checked+=1
    print(f"PASS: {checked} synthetic schema/example/adverse checks; no Story 10.1 acceptance claim")
if __name__=="__main__":main()
