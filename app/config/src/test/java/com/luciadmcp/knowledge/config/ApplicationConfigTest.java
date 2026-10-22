package com.luciadmcp.knowledge.config;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ApplicationConfigTest {
 @TempDir Path temp;
 @Test void safeStrictBoundedConfiguration() throws Exception {
  Path yaml=temp.resolve("application.yaml");Files.writeString(yaml,"indexRoot: index\nsourceDefault: synthetic\n");
  var config=ApplicationConfig.load(yaml);assertEquals(8766,config.port());assertEquals(temp.resolve("index"),config.indexRoot());assertFalse(config.stdio());
  for(String invalid:new String[]{"!!java.lang.ProcessBuilder {}","indexRoot: x\nindexRoot: y\nsourceDefault: a","indexRoot: x\nsourceDefault: a\nport: bad","indexRoot: x\nsourceDefault: a\nunknown: x","[bad]"}) {
   Files.writeString(yaml,invalid);assertThrows(IllegalArgumentException.class,()->ApplicationConfig.load(yaml));
  }
 }
}
