package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationExtractionTest {
    @Test void nonemptyTypeAndMemberGenericDocsAndActualLegacyAnchors() {
        String notes="<dl><dt>Type Parameters:</dt><dd><code>T</code> - the type</dd></dl>";
        String memberNotes="<dl><dt>Type Parameters:</dt><dd><code>R</code> - the result type</dd><dt>Parameters:</dt><dd><code>n</code> - the count</dd></dl>";
        String modern="<body class='class-declaration-page'><h1 class='title'>Class Generic</h1><section class='class-description'><div class='type-signature'>class Generic&lt;T&gt;</div><div class='block'>Generic.</div>"+notes+"</section><section class='method-details'><section class='detail' id='actual-map'><h3>map</h3><div class='member-signature'>public &lt;R&gt; R map(int n)</div><div class='block'>Maps.</div>"+memberNotes+"</section></section></body>";
        String legacy="<body><h2 class='title'>Class Generic</h2><div class='description'><ul class='blockList'><li class='blockList'><pre>class Generic&lt;T&gt;</pre><div class='block'>Generic.</div>"+notes+"</li></ul></div><div class='details'><h3>Method Detail</h3><a name='actual-map'></a><ul class='blockList'><li class='blockList'><h4>map</h4><pre>public &lt;R&gt; R map(int n)</pre><div class='block'>Maps.</div>"+memberNotes+"</li></ul></div></body>";
        for(String html:List.of(modern,legacy)) {
            var type=new JavadocFieldExtractor().extract(Jsoup.parse(html),"Generic.html").type();
            assertEquals(List.of(new Param("T","the type")),type.typeParameters());assertEquals("class Generic<T>",type.signature());
            var member=type.members().get(0);assertEquals(List.of(new Param("R","the result type")),member.typeParameters());assertEquals(List.of(new Param("n","the count")),member.params());assertEquals(Optional.of("actual-map"),member.anchor());
        }
    }

    @Test void tableRowsAndCellsConsumeTheExtractionWorkBudget() {
        String html="<section class='package-description'><table><tr><th>h</th></tr>"+"<tr><td>v</td></tr>".repeat(32769)+"</table></section>";
        var owner=Jsoup.parse(html).selectFirst("section.package-description");
        var error=assertThrows(IllegalArgumentException.class,()->OwnedDocumentationExtractor.narrative(owner,"package-summary.html",new ArrayList<>()));
        assertEquals("PAYLOAD_TOO_LARGE: owned documentation package-summary.html",error.getMessage());
    }
    @Test void tableInlineWidthAndDepthConsumeTheExtractionBudget() {
        for(String content:List.of("<span></span>".repeat(65537),"<span>".repeat(33)+"deep"+"</span>".repeat(33))) {
            var owner=Jsoup.parse("<section class='package-description'><table><tr><th>h</th></tr><tr><td>"+content+"</td></tr></table></section>").selectFirst("section.package-description");
            var error=assertThrows(IllegalArgumentException.class,()->OwnedDocumentationExtractor.narrative(owner,"package-summary.html",new ArrayList<>()));
            assertEquals("PAYLOAD_TOO_LARGE: owned documentation package-summary.html",error.getMessage());
        }
    }

}
