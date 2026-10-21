package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * Story 2.5 extraction: the field extractor captures the relationship-source references — a type's
 * superclass / super-interfaces / implemented-interfaces / see-also, and each member's return/field
 * type, overrides, specified-by, see-also — with their link hrefs (resolved to root-relative paths),
 * identically for modern (JDK 11+) and legacy (JDK 8) layouts. Fixtures are synthetic (no proprietary
 * content).
 */
class JavadocRelationshipExtractionTest {

    private final JavadocFieldExtractor extractor = new JavadocFieldExtractor();

    private static final String MODERN_WIDGET = """
            <!DOCTYPE html><html><head><title>Widget</title></head>
            <body class="class-declaration-page">
            <main>
            <h1 class="title">Class Widget</h1>
            <section class="class-description" id="class-description">
              <div class="block">A widget.</div>
              <hr>
              <div class="type-signature"><span class="modifiers">public class </span><span class="element-name">Widget</span>
               <span class="extends-implements">extends <a href="Base.html" title="class in com.example">Base</a>
               implements <a href="Drawable.html" title="interface in com.example">Drawable</a></span></div>
              <dl class="notes"><dt>See Also:</dt><dd><a href="Point.html"><code>Point</code></a></dd></dl>
            </section>
            <section class="method-details" id="method-detail">
              <ul class="member-list">
               <li><section class="detail" id="getOrigin()">
                 <h3>getOrigin</h3>
                 <div class="member-signature"><span class="return-type"><a href="Point.html">Point</a></span>&nbsp;getOrigin()</div>
                 <div class="block">Gets it.</div>
               </section></li>
               <li><section class="detail" id="doThing(int)">
                 <h3>doThing</h3>
                 <div class="member-signature">void&nbsp;doThing&#8203;(int&nbsp;count)</div>
                 <div class="block">Does.</div>
                 <dl class="notes"><dt>Overrides:</dt>
                  <dd><code><a href="Base.html#doThing(int)">doThing</a></code> in class <code><a href="Base.html">Base</a></code></dd></dl>
               </section></li>
               <li><section class="detail" id="draw()">
                 <h3>draw</h3>
                 <div class="member-signature">void&nbsp;draw()</div>
                 <div class="block">Draws.</div>
                 <dl class="notes"><dt>Specified by:</dt>
                  <dd><code><a href="Drawable.html#draw()">draw</a></code> in interface <code><a href="Drawable.html">Drawable</a></code></dd></dl>
               </section></li>
              </ul>
            </section>
            <section class="field-details" id="field-detail">
              <ul class="member-list">
               <li><section class="detail" id="anchor">
                 <h3>anchor</h3>
                 <div class="member-signature"><a href="Point.html">Point</a>&nbsp;anchor</div>
                 <div class="block">Anchor.</div>
               </section></li>
              </ul>
            </section>
            </main></body></html>
            """;

    private static final String LEGACY_WIDGET = """
            <!DOCTYPE html><html><head><title>Widget</title></head>
            <body>
            <div class="header"><h2 class="title">Class Widget</h2></div>
            <div class="contentContainer">
            <div class="description"><ul class="blockList"><li class="blockList">
              <pre>public class Widget
            extends <a href="Base.html" title="class in com.example">Base</a>
            implements <a href="Drawable.html" title="interface in com.example">Drawable</a></pre>
              <div class="block">A widget.</div>
            </li></ul></div>
            <div class="details"><ul class="blockList">
             <li class="blockList"><h3>Method Detail</h3>
              <ul class="blockList"><li class="blockList">
                <h4>doThing</h4>
                <pre>void doThing(int count)</pre>
                <div class="block">Does.</div>
                <dl><dt><span class="overrideSpecifyLabel">Overrides:</span></dt>
                 <dd><code><a href="Base.html#doThing(int)">doThing</a></code>&nbsp;in class&nbsp;<code><a href="Base.html">Base</a></code></dd></dl>
              </li></ul>
             </li>
            </ul></div>
            </div>
            </body></html>
            """;

    private static final String LEGACY_INTERFACE = """
            <!DOCTYPE html><html><head><title>Shape</title></head>
            <body>
            <div class="header"><h2 class="title">Interface Shape</h2></div>
            <div class="contentContainer">
            <div class="description"><ul class="blockList"><li class="blockList">
              <pre>public interface Shape
            extends <a href="Drawable.html" title="interface in com.example">Drawable</a></pre>
              <div class="block">A shape.</div>
            </li></ul></div>
            </div>
            </body></html>
            """;

    private ExtractedType extract(String html, String path) {
        Document doc = Jsoup.parse(html);
        ExtractionResult result = extractor.extract(doc, path);
        assertTrue(result.issues().isEmpty(), () -> path + " issues: " + result.issues());
        return result.type();
    }

    private static ExtractedMember member(ExtractedType type, String name) {
        return type.members().stream().filter(m -> m.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no member named " + name));
    }

    /** Modern: superclass, implemented interface, type see-also all captured with resolved hrefs. */
    @Test
    void modernTypeHeaderAndSeeAlsoCaptureHrefs() {
        ExtractedType type = extract(MODERN_WIDGET, "com/example/Widget.html");

        assertNotNull(type.superclass(), "superclass must be captured");
        assertEquals("Base", type.superclass().name());
        assertEquals("com/example/Base.html", type.superclass().relativePath());
        assertEquals("", type.superclass().anchor());
        assertEquals("Base.html", type.superclass().originalLocator());
        assertEquals(TypeRef.LocatorKind.LOCAL, type.superclass().locatorKind());

        assertEquals(1, type.implementedInterfaces().size());
        assertEquals("com/example/Drawable.html", type.implementedInterfaces().get(0).relativePath());
        assertEquals("Drawable.html", type.implementedInterfaces().get(0).originalLocator());
        assertTrue(type.superInterfaces().isEmpty(), "a class has no super-interfaces");

        assertEquals(1, type.seeAlso().size());
        assertEquals("com/example/Point.html", type.seeAlso().get(0).relativePath());
    }

    /** Modern: return type, field type (href from signature), overrides + specified-by (href from notes). */
    @Test
    void modernMemberReferencesCaptureHrefs() {
        ExtractedType type = extract(MODERN_WIDGET, "com/example/Widget.html");

        ExtractedMember getOrigin = member(type, "getOrigin");
        assertNotNull(getOrigin.returnType());
        assertEquals("Point", getOrigin.returnType().name());
        assertEquals("com/example/Point.html", getOrigin.returnType().relativePath());

        ExtractedMember anchor = member(type, "anchor");
        assertNotNull(anchor.fieldType());
        assertEquals("com/example/Point.html", anchor.fieldType().relativePath());

        ExtractedMember doThing = member(type, "doThing");
        assertNotNull(doThing.overrides(), "Overrides: note must be captured");
        assertEquals("com/example/Base.html", doThing.overrides().relativePath());
        assertEquals("doThing(int)", doThing.overrides().anchor(), "the override is member-precise (anchored)");
        assertNull(doThing.returnType(), "a void method names no documented return type");

        ExtractedMember draw = member(type, "draw");
        assertNotNull(draw.specifiedBy(), "Specified by: note must be captured");
        assertEquals("com/example/Drawable.html", draw.specifiedBy().relativePath());
        assertEquals("draw()", draw.specifiedBy().anchor());
    }

    /** Legacy JDK 8: the {@code <pre>} type header yields the same superclass/interface references. */
    @Test
    void legacyTypeHeaderCaptureHrefs() {
        ExtractedType type = extract(LEGACY_WIDGET, "com/example/Widget.html");
        assertNotNull(type.superclass());
        assertEquals("com/example/Base.html", type.superclass().relativePath());
        assertEquals(1, type.implementedInterfaces().size());
        assertEquals("com/example/Drawable.html", type.implementedInterfaces().get(0).relativePath());

        ExtractedMember doThing = member(type, "doThing");
        assertNotNull(doThing.overrides());
        assertEquals("com/example/Base.html", doThing.overrides().relativePath());
        assertEquals("doThing(int)", doThing.overrides().anchor());
    }

    /** Legacy interface: the {@code extends} clause is captured as super-interfaces, not a superclass. */
    @Test
    void legacyInterfaceExtendsIsSuperInterface() {
        ExtractedType type = extract(LEGACY_INTERFACE, "com/example/Shape.html");
        assertNull(type.superclass(), "an interface has no superclass");
        assertTrue(type.implementedInterfaces().isEmpty(), "an interface implements nothing");
        assertEquals(1, type.superInterfaces().size());
        assertEquals("Drawable", type.superInterfaces().get(0).name());
        assertEquals("com/example/Drawable.html", type.superInterfaces().get(0).relativePath());
    }

    /** An absent header (no signature element) yields no relationship references, not a crash. */
    @Test
    void undocumentedHeaderYieldsNoReferences() {
        String bare = """
                <!DOCTYPE html><html><head><title>Bare</title></head>
                <body class="class-declaration-page">
                <main><h1 class="title">Class Bare</h1>
                <section class="class-description"><div class="block">Bare.</div></section>
                </main></body></html>
                """;
        ExtractedType type = extract(bare, "com/example/Bare.html");
        assertNull(type.superclass());
        assertTrue(type.superInterfaces().isEmpty());
        assertTrue(type.implementedInterfaces().isEmpty());
        assertEquals(List.of(), type.seeAlso());
    }
}
