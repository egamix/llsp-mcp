package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The search index is parsed as text only (AD-10): its JSON-ish array is read with regexes, never
 * executed as JavaScript. These tests pin the path mapping and the malformed-input contract.
 */
class SearchIndexTest {

    @Test
    void mapsPackageAndLabelToTypePagePath() {
        String js = "typeSearchIndex = ["
                + "{\"p\":\"com.example\",\"l\":\"Widget\"},"
                + "{\"p\":\"com.example.sub\",\"l\":\"Service\"}"
                + "];";
        List<String> paths = SearchIndex.typePagePaths(js);
        assertEquals(List.of("com/example/Widget.html", "com/example/sub/Service.html"), paths);
    }

    @Test
    void emptyPackageMapsToTopLevelPath() {
        String js = "typeSearchIndex = [{\"p\":\"\",\"l\":\"TopLevel\"}];";
        assertEquals(List.of("TopLevel.html"), SearchIndex.typePagePaths(js));
    }

    @Test
    void entriesWithEmptyLabelAreSkipped() {
        String js = "typeSearchIndex = [{\"p\":\"com.example\",\"l\":\"\"},{\"p\":\"com.example\",\"l\":\"Ok\"}];";
        assertEquals(List.of("com/example/Ok.html"), SearchIndex.typePagePaths(js));
    }

    @Test
    void extractArrayReturnsTheBracketedPayload() {
        String js = "typeSearchIndex = [{\"a\":1}];";
        assertEquals("[{\"a\":1}]", SearchIndex.extractArray(js));
    }

    @Test
    void extractArrayRejectsInputWithoutAnArray() {
        assertThrows(IllegalArgumentException.class, () -> SearchIndex.extractArray("not json at all"));
    }

    @Test
    void typePagePathsOnEmptyArrayIsEmpty() {
        assertTrue(SearchIndex.typePagePaths("typeSearchIndex = [];").isEmpty());
    }
}
