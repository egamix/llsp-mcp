package com.luciadmcp.knowledge.retrieval;

import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.catalog.sqlite.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.luciadmcp.knowledge.domain.ExactSymbolResult.Proof.*;

class ExactSymbolResolverTest {
    @TempDir Path temp;
    private static final String SDK="2023.5", BUILD="b-"+"1".repeat(64);
    private Path catalog() {
        Path path=temp.resolve("source.sqlite");
        try(var store=new SqliteCatalogStore(path);var edges=new SqliteRelationshipStore(path)) {
            store.bindManifest(new IndexManifest(BUILD,SDK,"synthetic"));
            for(String owner:List.of("alpha.Widget","beta.Widget")) {
                put(store,owner,"TYPE",owner,"Widget","");
                for(String param:List.of("int","java.lang.String")) {
                    put(store,owner+"#Widget("+param+")","CONSTRUCTOR",owner,"Widget","ctor-"+param);
                    put(store,owner+"#map("+param+")","METHOD",owner,"map","map-"+param);
                }
            }
            put(store,"alpha.Widget#accept(java.util.List)","METHOD","alpha.Widget","accept","");
            put(store,"alpha.Widget#only(int)","METHOD","alpha.Widget","only","only-int");
        }
        return path;
    }
    private static void put(CatalogStore store,String key,String kind,String owner,String simple,String anchor) {
        store.put(new CatalogRecord(SDK,key,kind,owner,simple,owner.replace('.','/')+".html",anchor));
    }
    private static ExactSymbolQuery query(String text,boolean aliases,int page,int candidates,int work,Optional<String> source,Optional<String> build,Optional<String> target) {
        return new ExactSymbolQuery(text,aliases,page,candidates,work,source,build,target);
    }
    private static ExactSymbolQuery page(String text,int size) {
        return query(text,true,size,1000,5000,Optional.empty(),Optional.empty(),Optional.empty());
    }
    @Test void canonicalIdentityProvenanceAndReopenNeverMutateCatalog() throws Exception {
        Path path=catalog();
        Map<String,String> ids=new TreeMap<>(); List<String> keys;
        try(var store=new SqliteCatalogStore(path)) { for(var r:store.allRecords())ids.put(r.record().canonicalKey(),r.recordId()); keys=List.copyOf(ids.keySet()); }
        byte[] before=Files.readAllBytes(path);
        for(int reopen=0;reopen<2;reopen++)try(var source=new SqliteSymbolSource(path)) {
            var resolver=new ExactSymbolResolver(source);
            for(String key:keys) {
                var result=resolver.resolve(new ExactSymbolQuery(key));
                assertEquals(EXACT,result.proof(),key);assertEquals(1,result.matches().size());
                var actual=result.matches().getFirst();
                assertEquals(SDK,actual.source().sdkVersion());assertEquals(BUILD,actual.source().buildId());
                assertEquals(SDK,actual.symbol().sdkVersion());assertEquals("canonical",actual.matchedBy());
                assertEquals(ids.get(key),actual.recordId());
                assertTrue(actual.locator().relativePath().endsWith("Widget.html"));
                if(key.endsWith("#accept(java.util.List)"))assertTrue(actual.locator().anchor().isEmpty());
            }
        }
        assertArrayEquals(before,Files.readAllBytes(path));
    }
    @Test void aliasesOverloadsAndLimitOnePreserveAllAmbiguity() {
        try(var source=new SqliteSymbolSource(catalog())) {
            var resolver=new ExactSymbolResolver(source);
            for(String q:List.of("Widget","Widget.map","alpha.Widget#map","alpha.Widget.Widget")) {
                var all=resolver.resolve(page(q,50));var limited=resolver.resolve(page(q,1));
                assertEquals(AMBIGUOUS,all.proof(),q);assertEquals(AMBIGUOUS,limited.proof(),q);
                assertEquals(all.candidatesMatched(),limited.candidatesMatched());assertEquals(1,limited.matches().size());
                assertTrue(limited.truncated());assertTrue(limited.complete());
                assertEquals(all.matches().stream().map(ExactSymbolResult.Match::recordId).sorted().toList(),all.matches().stream().map(ExactSymbolResult.Match::recordId).toList());
            }
            assertEquals(EXACT,resolver.resolve(page("alpha.Widget.map(String)",10)).proof());
            assertEquals(EXACT,resolver.resolve(page("alpha.Widget#accept(java.util.List<java.lang.String>)",10)).proof());
            assertEquals(INCOMPLETE_EVIDENCE,resolver.resolve(page("alpha.Widget.only",10)).proof());
            assertEquals(UNKNOWN_IN_SCOPE,resolver.resolve(query("Widget",false,10,1000,5000,Optional.empty(),Optional.empty(),Optional.empty())).proof());
            for(String absent:List.of("ALPHA.Widget","gamma.Widget#map(int)","map(int)","alpha.Missing"))assertEquals(UNKNOWN_IN_SCOPE,resolver.resolve(page(absent,10)).proof(),absent);
        }
    }
    @Test void targetContextAndExplicitSourceFiltersAreIndependent() {
        try(var source=new SqliteSymbolSource(catalog())) {
            var resolver=new ExactSymbolResolver(source);var ordinary=resolver.resolve(new ExactSymbolQuery("alpha.Widget"));
            var target=resolver.resolve(query("alpha.Widget",true,10,1000,5000,Optional.of(SDK),Optional.of(BUILD),Optional.of("2023.1")));
            assertEquals(ordinary.matches(),target.matches());assertEquals(ordinary.proof(),target.proof());assertEquals(Optional.of("2023.1"),target.targetSdkVersion());
            assertEquals(SymbolSourceException.Code.UNKNOWN_SDK,assertThrows(SymbolSourceException.class,()->resolver.resolve(query("alpha.Widget",true,10,1000,5000,Optional.of("2023.1"),Optional.empty(),Optional.empty()))).code());
            assertEquals(SymbolSourceException.Code.BUILD_UNAVAILABLE,assertThrows(SymbolSourceException.class,()->resolver.resolve(query("alpha.Widget",true,10,1000,5000,Optional.empty(),Optional.of("other"),Optional.empty()))).code());
        }
    }
    @Test void candidateAndWorkExhaustionCannotProveUniqueOrAbsence() {
        try(var source=new SqliteSymbolSource(catalog())) {
            var resolver=new ExactSymbolResolver(source);
            var limited=resolver.resolve(query("Widget",true,1,1,5000,Optional.empty(),Optional.empty(),Optional.empty()));
            assertEquals(INCOMPLETE_EVIDENCE,limited.proof());assertFalse(limited.complete());
            var noWork=resolver.resolve(query("missing",true,1,1000,1,Optional.empty(),Optional.empty(),Optional.empty()));
            assertEquals(INCOMPLETE_EVIDENCE,noWork.proof());assertEquals(1,noWork.workCharged());
            assertEquals(EXACT,resolver.resolve(query("alpha.Widget",true,1,1,1,Optional.empty(),Optional.empty(),Optional.empty())).proof());
        }
    }
    @Test void oversizedCitationDoesNotProduceUnboundedResultOrFalseExact() {
        Path path=catalog();
        try(var catalog=new SqliteCatalogStore(path)) {
            catalog.put(new CatalogRecord(SDK,"large.Widget","TYPE","large.Widget","Widget","x".repeat(200000)+".html",""));
        }
        try(var source=new SqliteSymbolSource(path)) {
            var result=new ExactSymbolResolver(source).resolve(new ExactSymbolQuery("large.Widget"));
            assertEquals(INCOMPLETE_EVIDENCE,result.proof());assertTrue(result.matches().isEmpty());
            assertTrue(result.truncated());assertEquals(1,result.candidatesMatched());
        }
    }
    @Test void invalidQueriesAndUtf8BytesRejectedBeforeReading() {
        for(String text:List.of(""," ","a\n","x".repeat(2049),"é".repeat(1025),"\uD800"))assertThrows(IllegalArgumentException.class,()->new ExactSymbolQuery(text));
        assertDoesNotThrow(()->new ExactSymbolQuery("é".repeat(1024)));
        assertDoesNotThrow(()->new ExactSymbolQuery("😀".repeat(512)));
        assertThrows(IllegalArgumentException.class,()->page("Widget",51));
    }
}



