package com.luciadmcp.knowledge.domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class SourceAdmissionTest {
    @TempDir Path root;
    @Test void exactAndAboveReadBoundaryAndStreamingDigest() throws Exception {
        Path file=root.resolve("source.html");byte[] bytes=new byte[SourceAdmission.MAX_FILE_BYTES];bytes[bytes.length-1]=42;
        Files.write(file,bytes);assertArrayEquals(bytes,SourceAdmission.read(file));
        assertEquals(ContentHashes.sourceHash(bytes),ContentHashes.sourceFileHash(file));
        Files.write(file,new byte[]{1},StandardOpenOption.APPEND);
        assertEquals("SOURCE_FILE_TOO_LARGE",assertThrows(java.io.IOException.class,()->SourceAdmission.read(file)).getMessage());
    }
    @Test void sortedEnumerationIncludesNonHtmlInputs() throws Exception {
        Files.writeString(root.resolve("z.js"),"z");Files.writeString(root.resolve("a.html"),"a");
        assertEquals(java.util.List.of("a.html","z.js"),SourceAdmission.enumerate(root).stream().map(p->SourceAdmission.relative(root,p)).toList());
    }
}
