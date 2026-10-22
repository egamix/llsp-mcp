package com.luciadmcp.knowledge.cli.pipeline;
import com.luciadmcp.knowledge.cli.*;
import com.luciadmcp.knowledge.config.ApplicationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MvpLifecycleTest {
 @TempDir Path temp;
 PipelineResult ingest(Path root,boolean changed) throws Exception {
  var args=StructuredDocumentationRoundtripTest.args(root);
  StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
  if(changed) { Path type=args.javadoc().resolve("p/T.html");Files.writeString(type,Files.readString(type)+"<!-- new immutable input -->"); }
  var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);assertFalse(result.aborted());return result;
 }
 @Test void invalidReloadRetainsOldLeaseAndColdStartRejects() throws Exception {
  var first=ingest(temp.resolve("first"),false);var second=ingest(temp.resolve("second"),true);assertNotEquals(first.manifest().buildId(),second.manifest().buildId());
  var config=new ApplicationConfig(temp.resolve("index"),"synthetic",8766,false);var repo=new BundleRepository(config);
  try(var writer=BundleRepository.writer(config.indexRoot())) { repo.publish(first);repo.publish(second);repo.activate("1",first.manifest().buildId()); }
  try(var service=new BundleRepository.Serving(repo);var old=service.lease()) {
   repo.activate("1",second.manifest().buildId());assertTrue(service.reloadIfChanged());assertEquals(first.manifest().buildId(),old.manifest().buildId());
   try(var current=service.lease()) { assertEquals(second.manifest().buildId(),current.manifest().buildId()); }
   assertTrue(service.reloadIfChanged());assertTrue(Files.isDirectory(config.indexRoot().resolve("1").resolve(first.manifest().buildId())));
   Files.writeString(config.indexRoot().resolve("active.json"),"{torn");assertFalse(service.reloadIfChanged());assertFalse(service.reload());
   assertThrows(IllegalStateException.class,()->new BundleRepository.Serving(repo));
   assertNull(old.tools().call("resolve_symbol",Map.of("contractVersion","read-tools/2","symbol","p.T")).get("error"));
   repo.activate("1",second.manifest().buildId());assertTrue(service.reload());
   try(var competing=BundleRepository.writer(config.indexRoot())) { assertThrows(IllegalStateException.class,()->BundleRepository.writer(config.indexRoot())); }
   Path bundle=config.indexRoot().resolve("1").resolve(second.manifest().buildId());Files.writeString(bundle.resolve("manifest.json"),"{}");
   String reason=assertThrows(IllegalStateException.class,()->repo.validate("1",second.manifest().buildId())).getMessage();
   assertEquals(reason,assertThrows(IllegalStateException.class,()->repo.activate("1",second.manifest().buildId())).getMessage());assertFalse(service.reload());
  }
 }
}
