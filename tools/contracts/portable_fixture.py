"""Independent UTF-8/framing oracle. Runs without dependencies on Windows or Linux.
This compares logical fixture recipes, not Linux JVM/parser/SQLite behavior."""
import base64,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def digest(domain,fields):
    data=b""
    for value in [domain]+fields:
        if value is None:data+=b"-1:"
        else:
            raw=value.encode("utf-8");data+=str(len(raw)).encode("ascii")+b":"+raw
    return hashlib.sha256(data).hexdigest()
def fixture():
    wire="symbol:1:"+".".join(base64.urlsafe_b64encode(f.encode()).decode().rstrip("=") for f in ["p","1","java","a","a.T","m","METHOD","2","x.Value[]","y.Value"])
    return {"narrativeId":digest("narrative/1",["LuciadLightspeed","1","p/package-summary.html","PACKAGE"]),"symbol":wire,"recordId":digest("identity/1/record",["MethodElement",wire,"1"]),"edgeId":digest("identity/1/relationship",["1","source","SEE_ALSO","target",""]),"contentHash":digest("content/1",["paragraph","synthetic"]),"recipeDigest":digest("javadoc-lexical/3",["javadoc-lexical"]),"runtimeDigest":digest("catalog-runtime/3",["3","identity/1","read-tools/2"]),"capabilityFingerprint":digest("capabilities/1",["api-documentation","catalog","narrative-documentation","relationships","structured-documentation"]),"manifestVersion":"1","reportVersion":"1","profile":"javadoc-lexical","technicalRatification":"PENDING"}
if __name__=="__main__":
    expected=dict(line.split("=",1) for line in (ROOT/"docs/contracts/fixtures/portable.properties").read_text().splitlines())
    actual=fixture();assert expected==actual
    print(json.dumps(actual,sort_keys=True,separators=(",",":")))
