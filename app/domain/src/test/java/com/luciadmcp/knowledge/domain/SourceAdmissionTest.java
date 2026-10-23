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
    @Test void exactAndAboveEnumerationFilePathAndAggregateBoundaries() {
        var files=new SourceAdmission.Budget();
        for(int i=0;i<100_000;i++)files.admit("a");
        assertEquals("SOURCE_ENUMERATION_LIMIT",assertThrows(IllegalArgumentException.class,()->files.admit("a")).getMessage());
        var path=new SourceAdmission.Budget();path.admit("\u00e9".repeat(2048));
        assertEquals("SOURCE_PATH_TOO_LARGE",assertThrows(IllegalArgumentException.class,()->path.admit("\u00e9".repeat(2048)+"a")).getMessage());
        var aggregate=new SourceAdmission.Budget();for(int i=0;i<4096;i++)aggregate.admit("a".repeat(4096));
        assertEquals("SOURCE_ENUMERATION_LIMIT",assertThrows(IllegalArgumentException.class,()->aggregate.admit("a")).getMessage());
    }
    @Test void realWalkRejectsFilesEmptyDirectoriesDepthAndAttributeFailures() throws Exception {
        Files.writeString(root.resolve("a"),"a");Files.writeString(root.resolve("b"),"b");
        SourceAdmission.AttributeReader attributes=p->Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
        assertEquals(2,SourceAdmission.enumerate(root,2,3,1,attributes).size());
        assertEquals("SOURCE_ENUMERATION_LIMIT",assertThrows(IllegalArgumentException.class,()->SourceAdmission.enumerate(root,1,10,10,attributes)).getMessage());
        assertThrows(java.io.UncheckedIOException.class,()->SourceAdmission.enumerate(root,10,10,10,p->{throw new java.io.IOException("synthetic attribute failure");}));
        Files.delete(root.resolve("a"));Files.delete(root.resolve("b"));
        Files.createDirectories(root.resolve("a/b"));
        assertEquals(0,SourceAdmission.enumerate(root,1,3,2,attributes).size());
        assertEquals("SOURCE_TRAVERSAL_LIMIT",assertThrows(IllegalArgumentException.class,()->SourceAdmission.enumerate(root,1,2,2,attributes)).getMessage());
        assertEquals("SOURCE_TRAVERSAL_LIMIT",assertThrows(IllegalArgumentException.class,()->SourceAdmission.enumerate(root,1,3,1,attributes)).getMessage());
        assertThrows(java.io.UncheckedIOException.class,()->SourceAdmission.enumerate(root.resolve("missing")));
    }
}
