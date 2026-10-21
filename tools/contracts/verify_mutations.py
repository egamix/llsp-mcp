"""Selected synthetic mutation screen. Each source is restored byte-for-byte in finally.
Run only with an idle workspace; uses the existing Gradle toolchain/cache, no mutation dependency."""
import json,os,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CASES=[
("hash","app/domain/src/main/java/com/luciadmcp/knowledge/domain/ContentHashes.java",'frame(md, ContractVersions.IDENTITY + "/record");','frame(md, "mutant-record");',"domain","com.luciadmcp.knowledge.domain.ContentHashesTest.recordHashIsPinned"),
("identity","app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/CanonicalSymbolMinter.java","SignatureNormalizer.parseQualified(member.signature())","SignatureNormalizer.parse(member.signature())","ingest-javadoc","com.luciadmcp.knowledge.ingest.javadoc.CanonicalSymbolMinterTest.qualifiedOverloadsAndPartialOccurrencesSurviveReordering"),
("scope","app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/RelationshipsStage.java","if (!record.sdkVersion().equals(version)) continue;","if (false) continue;","cli","com.luciadmcp.knowledge.cli.pipeline.RelationshipsStageTest.locatorCollisionsRemainAmbiguousAndVersionScoped"),
("ambiguity","app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/RelationshipsStage.java","ids.size() == 1 ? ReferenceResolution.Status.EXACT","ids.size() >= 1 ? ReferenceResolution.Status.EXACT","cli","com.luciadmcp.knowledge.cli.pipeline.RelationshipsStageTest.ambiguousSimpleNameIsLeftUnresolved"),
("resolution","app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/RelationshipsStage.java","matches = byFqnType.getOrDefault(name, List.of());","matches = List.of();","cli","com.luciadmcp.knowledge.cli.pipeline.RelationshipsStageTest.qualifiedReferenceResolvesPreciselyWithoutFallback"),
("activation","app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/CatalogStage.java",'issue.contains("duplicate canonical key") ? FailureSeverity.BUILD_INVALIDATING : FailureSeverity.RECOVERABLE',"FailureSeverity.RECOVERABLE","cli","com.luciadmcp.knowledge.cli.pipeline.CatalogStageTest.identicalPartialOccurrencesInvalidateContinueBuild")]
def main():
    evidence=[]
    logdir=ROOT/"build/contract-mutations";logdir.mkdir(parents=True,exist_ok=True)
    for name,relative,before,after,module,test in CASES:
        path=ROOT/relative;original=path.read_bytes();old=before.encode();new=after.encode()
        assert original.count(old)==1,(name,"mutation site changed")
        try:
            path.write_bytes(original.replace(old,new,1))
            command=[str(ROOT/"app/gradlew.bat"),"--no-daemon",f":{module}:test","--tests",test,"--rerun-tasks"]
            testclass=test.rsplit(".",1)[0];xml=ROOT/f"app/{module}/build/test-results/test/TEST-{testclass}.xml"
            xml.unlink(missing_ok=True)
            result=subprocess.run(command,cwd=ROOT/"app",env=os.environ.copy(),stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            (logdir/f"{name}.log").write_bytes(result.stdout)
            testclass=test.rsplit(".",1)[0];xml=ROOT/f"app/{module}/build/test-results/test/TEST-{testclass}.xml"
            tree=ET.parse(xml) if xml.exists() else None
            selected=[] if tree is None else [case for case in tree.getroot().findall("testcase") if case.attrib.get("name","").startswith(test.rsplit(".",1)[1]+"(")]
            failures=sum(case.find("failure") is not None or case.find("error") is not None for case in selected)
            killed=result.returncode!=0 and failures>0
            evidence.append({"mutation":name,"test":test,"detected":killed,"exit":result.returncode})
            print(name+": "+("DETECTED" if killed else "SURVIVED"),flush=True)
            assert killed,(name,"selected test did not detect mutation")
        finally:path.write_bytes(original)
    (ROOT/"docs/contracts/mutation-evidence.json").write_text(json.dumps({"scope":"selected synthetic screening; not Story 10.1 acceptance","results":evidence},indent=2)+"\n",encoding="utf-8")
if __name__=="__main__":main()
