package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class ExplicitReferenceExtractionTest {
    @Test void modernAndLegacyPreserveQualifiedReferencesAndActualTypeLinks() {
        for (boolean modern : List.of(true, false)) {
            String signature = "@<a href='Annotation.html'>Annotation</a> public <a href='https://elsewhere/Point.html'>java.awt.Point</a> "
                    + "call(<a href='Dead.html'>outside.Point</a>[] points)";
            String member = modern
                    ? "<section class='method-details'><section class='detail' id='call(outside.Point[])'><h3>call</h3><div class='member-signature'>" + signature + "</div>"
                    : "<div class='methodDetails'><ul class='blockList'><li class='blockList'><h4>call</h4><pre>" + signature + "</pre>";
            String html = "<html><body><h1>Class Widget</h1><div class='description'><div class='block'>Widget.</div></div>" + member
                    + "<div class='block'>Calls.</div><dl><dt>Throws:</dt><dd><code><a href='../../../Escape.html'>outside.Failure</a></code> - bad</dd></dl>"
                    + (modern ? "</section></section>" : "</li></ul></div>") + "</body></html>";
            var result = new JavadocFieldExtractor().extract(Jsoup.parse(html), "com/example/Widget.html");
            assertEquals(1, result.type().members().size(), () -> result.issues().toString());
            var m = result.type().members().get(0);
            assertEquals("java.awt.Point", m.returnType().name());
            assertEquals("https://elsewhere/Point.html", m.returnType().originalLocator());
            assertEquals(TypeRef.LocatorKind.EXTERNAL, m.returnType().locatorKind());
            assertEquals("outside.Point[]", m.parameterRefs().get(0).name());
            assertEquals("Dead.html", m.parameterRefs().get(0).originalLocator());
            assertEquals("outside.Failure", m.throwsRefs().get(0).name());
            assertEquals(TypeRef.LocatorKind.ROOT_ESCAPE, m.throwsRefs().get(0).locatorKind());
            assertEquals(List.of("Point[]"), SignatureNormalizer.parameterTypes(m.signature()).orElseThrow());
        }
    }
    @Test void signatureQualificationSurvivesSimpleThrowsNotesAndRepeatedTypeLinks() {
        var html = "<html><body><h1>Class Widget</h1><section class='class-description'><div class='block'>Widget.</div></section>"
                + "<section class='method-details'><section class='detail' id='get(Point)'><h3>get</h3>"
                + "<div class='member-signature'>@<a href='Annotation.html'>Annotation</a> "
                + "<a href='https://external/Point.html'>Point</a> get(<a href='Point.html'>Point</a> p) throws outside.Failure</div>"
                + "<div class='block'>Gets.</div><dl><dt>Throws:</dt><dd><code>Failure</code> - bad</dd></dl>"
                + "</section></section></body></html>";
        var member = new JavadocFieldExtractor().extract(Jsoup.parse(html), "Widget.html").type().members().get(0);
        assertEquals("https://external/Point.html", member.returnType().originalLocator());
        assertEquals("Point.html", member.parameterRefs().get(0).originalLocator());
        assertEquals(List.of("outside.Failure"), member.throwsRefs().stream().map(TypeRef::name).toList());
        assertEquals(List.of("Point"), SignatureNormalizer.parameterTypes(member.signature()).orElseThrow());
    }

    @Test void linkedUncheckedThrowsNoteDoesNotReplaceDistinctQualifiedDeclaration() {
        var html = "<html><body><h1>Class Widget</h1><section class='class-description'><div class='block'>Widget.</div></section>"
                + "<section class='method-details'><section class='detail' id='get()'><h3>get</h3>"
                + "<div class='member-signature'>void get() throws outside.Failure</div>"
                + "<div class='block'>Gets.</div><dl><dt>Throws:</dt>"
                + "<dd><code><a href='local/Failure.html'>local.Failure</a></code> - unchecked</dd></dl>"
                + "</section></section></body></html>";
        var refs = new JavadocFieldExtractor().extract(Jsoup.parse(html), "Widget.html")
                .type().members().get(0).throwsRefs();
        assertEquals(List.of("outside.Failure", "local.Failure"), refs.stream().map(TypeRef::name).toList());
        assertFalse(refs.get(0).hasHref());
        assertEquals("local/Failure.html", refs.get(1).originalLocator());
        assertEquals(TypeRef.LocatorKind.LOCAL, refs.get(1).locatorKind());
    }

}
