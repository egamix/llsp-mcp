package com.luciadmcp.knowledge.ingest.examples;

import com.luciadmcp.knowledge.domain.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaExampleExtractorTest {
    static final String SAMPLE = """
        package samples.shape;

        import com.luciad.shape.shape2D.TLcdLonLatPoint;
        import java.util.List;

        /** Shows how to create and move a point. */
        public class PointSample {
          /** Creates a point and moves it. */
          public TLcdLonLatPoint create(double lon, double lat) {
            TLcdLonLatPoint point = new TLcdLonLatPoint(lon, lat);
            point.move2D(lon + 1, lat);
            return point;
          }

          int tiny() { return 1; }

          void listener() {
            Runnable r = new Runnable() {
              public void run() {
                System.out.println("anonymous");
              }
            };
            r.run();
          }

          static class Inner {
            void work(String... names) {
              System.out.println(names.length);
              System.out.println("done");
            }
          }
        }
        """;

    @Test void typesAndMethodsBecomeCitedExampleRecords() {
        var result = new JavaExampleExtractor().extract("2024.0", "samples/shape/PointSample.java", SAMPLE.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(), result.issues());
        var titles = result.examples().stream().map(e -> e.payload().sections().getFirst().title()).toList();
        assertEquals(List.of("Example samples.shape.PointSample", "Example samples.shape.PointSample#create(double,double)",
                "Example samples.shape.PointSample#listener()", "Example samples.shape.PointSample.Inner",
                "Example samples.shape.PointSample.Inner#work(String...)"), titles);

        var type = result.examples().getFirst();
        assertEquals(KnowledgeRecord.RecordType.EXAMPLE, type.record().recordType());
        assertTrue(type.record().symbol().isEmpty());
        assertEquals(Optional.of(new SourceLocator.LineRange(1, SAMPLE.split("\n", -1).length)), type.record().locator().lines());
        var typeBlocks = type.payload().sections().getFirst().blocks();
        assertEquals("Shows how to create and move a point.", typeBlocks.get(0).text());
        assertEquals("com.luciad.shape.shape2D.TLcdLonLatPoint", typeBlocks.get(1).text());
        assertEquals(SAMPLE, typeBlocks.get(2).text());

        var create = result.examples().get(1);
        assertEquals(Optional.of(new SourceLocator.LineRange(9, 13)), create.record().locator().lines());
        var code = create.payload().sections().getFirst().blocks().getLast();
        assertEquals(DocumentationPayload.BlockKind.CODE, code.kind());
        assertTrue(code.text().startsWith("public TLcdLonLatPoint create(double lon, double lat) {\n  TLcdLonLatPoint point"), code.text());
        assertTrue(create.payload().sections().getFirst().blocks().get(1).text().contains(type.record().id()));
        assertEquals(KnowledgeRecord.exampleId("2024.0", "samples/shape/PointSample.java", "samples.shape.PointSample", "create(double,double)"), create.record().id());
        assertTrue(create.usages().contains(new JavaExampleExtractor.Usage(JavaExampleExtractor.UsageKind.CONSTRUCT, "TLcdLonLatPoint", "", 10)), create.usages().toString());
        assertTrue(create.usages().contains(new JavaExampleExtractor.Usage(JavaExampleExtractor.UsageKind.CALL, "TLcdLonLatPoint", "move2D", 11)), "receiver typed from local variable");
        assertEquals("samples.shape", result.packageName());
        assertEquals(List.of("com.luciad.shape.shape2D.TLcdLonLatPoint", "java.util.List"), result.imports());
    }

    @Test void unparseableSourceIsReportedNotThrown() {
        var result = new JavaExampleExtractor().extract("2024.0", "Broken.java", "class { nope".getBytes(StandardCharsets.UTF_8));
        assertTrue(result.examples().isEmpty());
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().getFirst().startsWith("Broken.java: not parsed as Java"), result.issues().toString());
    }
}
