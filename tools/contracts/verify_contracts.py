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
    # The envelope declares exactly one immutable source. Target context never admits foreign evidence.
    for notice in response["versionNotices"]:
        assert response["targetSdkVersion"] is not None and response["targetSdkVersion"] in notice["relevantTargets"]
        assert notice["citation"]["locator"]["kind"] == "SOURCE"
    def visit(value,depth=0):
        assert depth<=64
        if isinstance(value,list):
            for child in value:visit(child,depth+1)
        elif isinstance(value,dict):
            if "first" in value and "last" in value:assert value["last"]>=value["first"]
            if "recordId" in value and "sdkVersion" in value and "buildId" in value and "locator" in value:
                assert value["sdkVersion"]==response["sdkVersion"] and value["buildId"]==response["buildId"]
            if "recordId" in value and "citations" in value:
                assert all(c["recordId"]==value["recordId"] for c in value["citations"])
            if "selectedRecordId" in value:
                assert (value["selectedRecordId"] is not None)==(value["proof"]=="EXACT")
            if "headers" in value and "rows" in value:assert all(len(row)==len(value["headers"]) for row in value["rows"])
            for child in value.values():visit(child,depth+1)
    visit(response)
def coverage_semantic(report):
    coverage=report["coverage"]
    if coverage["availability"] == "UNAVAILABLE":
        assert report["completion"] != "COMPLETE_FOR_PROFILE"
        assert all(coverage[key] is None for key in ("pages","records","sections"))
        return
    pages=coverage["pages"]
    assert pages["discovered"] == sum(pages[key] for key in ("excluded","complete","partial","failed"))
    for key in ("records","sections"):
        counts=coverage[key];assert counts["emitted"] == counts["persisted"]+counts["failed"]
    cells=coverage["outcomes"]
    keys=[(c["pageType"],c["disposition"],c["reason"]) for c in cells]
    assert keys == sorted(set(keys))
    for disposition in ("EXCLUDED","COMPLETE","PARTIAL","FAILED"):
        assert pages[disposition.lower()] == sum(c["count"] for c in cells if c["disposition"] == disposition)
    if report["completion"] == "COMPLETE_FOR_PROFILE":
        assert pages["partial"] == pages["failed"] == 0 and not coverage["discoveryIssues"]
        assert coverage["records"]["failed"] == coverage["sections"]["failed"] == 0

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
    report=json.loads((DIRECTORY/"ingestion-report.example.json").read_text());coverage_semantic(report);checked+=1
    for key in ("pages","records","sections"):
        bad=copy.deepcopy(report);bad["coverage"][key]["failed"]+=1
        try:coverage_semantic(bad)
        except AssertionError:checked+=1
        else:raise AssertionError("non-reconciled coverage accepted")
    inventory=json.loads((DIRECTORY/"fixtures/ingestion-audit.synthetic.json").read_text())
    jsonschema.Draft202012Validator(json.loads((DIRECTORY/"ingestion-audit-inventory.schema.json").read_text())).validate(inventory);checked+=1
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
        bad={"contractVersion":"read-tools/2","targetSdkVersion":"2024.0","seed":"T","family":family,"types":[kind]}
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
    for name in ("search_knowledge.missing-build.example.json","search_knowledge.unknown-sdk.example.json","search_knowledge.browse.example.json"):
        specimen=json.loads((DIRECTORY/name).read_text())
        validator("search_knowledge","request").validate(specimen["request"])
        validator("search_knowledge","response").validate(specimen["response"]);semantic(specimen["response"]);checked+=2
    # Absence is explicit only before binding; successful envelopes cannot invent missing build metadata.
    for tool in TOOLS:
        bad=json.loads((DIRECTORY/f"{tool}.example.json").read_text())["response"]
        bad["buildId"]=None;bad["trace"]["capabilityFingerprint"]=None
        assert list(validator(tool,"response").iter_errors(bad));checked+=1
    for tool in TOOLS:
        unavailable=json.loads((DIRECTORY/f"{tool}.example.json").read_text())["response"]
        unavailable.update(sdkVersion=None,buildId=None,data=None,proof="NOT_APPLICABLE",error={"code":"BUILD_UNAVAILABLE","message":"No immutable build is available","retryable":True},counts={"value":None,"relation":"UNKNOWN"},continuation=None)
        unavailable["trace"]["capabilityFingerprint"]=None
        validator(tool,"response").validate(unavailable);semantic(unavailable);checked+=1
        unavailable["trace"]["capabilityFingerprint"]="invented-sentinel"
        assert list(validator(tool,"response").iter_errors(unavailable));checked+=1
    bad=copy.deepcopy(populated["response"]);bad["data"]["records"][0]["record"]["snippet"]=None
    bad["data"]["records"][0]["record"]["citations"]=[]
    assert list(validator("get_record","response").iter_errors(bad));checked+=1
    for request in (
        {"contractVersion":"read-tools/2","targetSdkVersion":"2024.0"},
        {"contractVersion":"read-tools/2","targetSdkVersion":"2024.0","mode":"LEXICAL","query":""},
        {"contractVersion":"read-tools/2","targetSdkVersion":"2024.0","query":"   "},
        {"contractVersion":"read-tools/2","targetSdkVersion":"2024.0","mode":"BROWSE","query":"dummy"}):
        assert list(validator("search_knowledge","request").iter_errors(request));checked+=1
    validator("search_knowledge","request").validate({"contractVersion":"read-tools/2","targetSdkVersion":"2024.0","mode":"BROWSE","query":""});checked+=1
    for tool in TOOLS:
        request=json.loads((DIRECTORY/f"{tool}.example.json").read_text())["request"]
        request.pop("targetSdkVersion",None)
        validator(tool,"request").validate(request);checked+=1
        request["sourceSdkVersion"]="2023.5"
        validator(tool,"request").validate(request);checked+=1
        request["sdkVersion"]="2023.1"
        assert list(validator(tool,"request").iter_errors(request));checked+=1
    for name in ("search_knowledge.target-mismatch.example.json", "search_knowledge.change-notice.example.json",
                 "search_knowledge.general.example.json", "resolve_symbol.target-mismatch.example.json", "check_symbols.target-mismatch.example.json"):
        specimen=json.loads((DIRECTORY/name).read_text());tool=specimen["response"]["tool"]
        validator(tool,"request").validate(specimen["request"])
        validator(tool,"response").validate(specimen["response"]);semantic(specimen["response"]);checked+=2
    mismatch=json.loads((DIRECTORY/"search_knowledge.target-mismatch.example.json").read_text())["response"]
    assert mismatch["targetSdkVersion"]=="2023.1" and mismatch["sdkVersion"]=="2023.5" and mismatch["versionNotices"]==[];checked+=1
    resolved=json.loads((DIRECTORY/"resolve_symbol.target-mismatch.example.json").read_text())["response"]
    assert resolved["counts"]=={"value":len(resolved["data"]["matches"]),"relation":"EXACT"} and resolved["counts"]["value"]==1 and resolved["emptyReason"] is None
    no_target=copy.deepcopy(mismatch);no_target["targetSdkVersion"]=None
    validator("search_knowledge","response").validate(no_target);semantic(no_target);checked+=1
    notice=json.loads((DIRECTORY/"search_knowledge.change-notice.example.json").read_text())["response"]
    for mutation in ("uncited","irrelevant","foreign","unverified","absent-locator"):
        bad=copy.deepcopy(notice)
        if mutation=="uncited":bad["versionNotices"][0].pop("citation")
        if mutation=="irrelevant":bad["versionNotices"][0]["relevantTargets"]=["2020.0"]
        if mutation=="foreign":bad["versionNotices"][0]["citation"]["sdkVersion"]="2023.1"
        if mutation=="unverified":bad["versionNotices"][0]["kind"]="UNVERIFIED"
        if mutation=="absent-locator":bad["versionNotices"][0]["citation"]["locator"].update(kind="ABSENT",relativePath="",reason="missing",section=None)
        rejected=bool(list(validator("search_knowledge","response").iter_errors(bad)))
        if not rejected:
            try:semantic(bad)
            except AssertionError:rejected=True
        assert rejected,mutation;checked+=1
    print(f"PASS: {checked} synthetic schema/example/adverse checks; no Story 10.1 acceptance claim")
if __name__=="__main__":main()
