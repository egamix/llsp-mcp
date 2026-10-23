package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.config.ApplicationConfig;
import com.luciadmcp.knowledge.catalog.sqlite.SqliteRelationshipStore;
import com.luciadmcp.knowledge.catalog.sqlite.SqliteSymbolSource;
import com.luciadmcp.knowledge.search.lucene.LuceneLexicalIndex;
import com.luciadmcp.knowledge.cli.pipeline.PipelineResult;
import com.luciadmcp.knowledge.mcp.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
import java.security.*;
import java.util.*;
import static com.luciadmcp.knowledge.mcp.Wire.map;

/** CLI-owned immutable bundles and atomic pointer. Every entry route calls admit(). */
public final class BundleRepository {
    private final ApplicationConfig config;
    private final byte[] key=new byte[32];
    public BundleRepository(ApplicationConfig config) { this.config=config;new SecureRandom().nextBytes(key); }
    public static WriterLock writer(Path root) {
        try { Files.createDirectories(root);return new WriterLock(root.resolve(".writer.lock")); }
        catch(IOException e) { throw invalid("WRITER_UNAVAILABLE"); }
    }
    public static final class WriterLock implements AutoCloseable {
        private final FileChannel channel;private final FileLock lock;
        WriterLock(Path path) throws IOException {
            channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            try { lock=channel.tryLock();if(lock==null)throw new IOException(); }
            catch(IOException|OverlappingFileLockException e) { channel.close();throw invalid("WRITER_BUSY"); }
        }
        public void close() { try { lock.release();channel.close(); }catch(IOException e) { throw invalid("WRITER_UNAVAILABLE"); } }
    }
    private static IllegalStateException invalid(String reason) { return new IllegalStateException("BUILD_INVALID: "+reason); }
    private Path bundle(String sdk,String build) {
        if(sdk==null || !sdk.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}") || build==null || !build.matches("b-[a-f0-9]{64}"))throw invalid("INVALID_SCOPE");
        return config.indexRoot().resolve(sdk).resolve(build);
    }
    public Path publish(PipelineResult result) {
        if(result.aborted() || result.failures().stream().anyMatch(f->f.severity().name().equals("BUILD_INVALIDATING")))throw invalid("BUILD_INVALIDATING_OUTCOME");
        var context=result.buildContext();var destination=bundle(context.sdkVersion(),context.buildId());
        if(Files.exists(destination)) { try(var handle=scoped(admit(destination,BuildValidator.Operation.CLI),context.sdkVersion(),context.buildId())) { return destination; } }
        Path temporary=config.indexRoot().resolve(".publishing").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(temporary);
            Files.copy(context.catalogDbPath(),temporary.resolve("catalog.sqlite"));
            copyTree(context.lexicalIndexPath(),temporary.resolve("lexical-index"));
            Path report=context.stagingDir().getParent().getParent().resolve(context.buildId()).resolve("ingestion-report.json");
            Files.copy(report,temporary.resolve("ingestion-report.json"));
            Files.write(temporary.resolve("manifest.json"),Wire.bytes(result.manifest()));
            Files.write(temporary.resolve("bundle.json"),Wire.bytes(map("bundleVersion",1,"assets",inventory(temporary))));
            try(var candidate=admit(temporary,BuildValidator.Operation.CLI)) { }
            Files.createDirectories(destination.getParent());
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
            return destination;
        }catch(IOException e) { throw invalid("PUBLICATION_FAILED"); }
    }
    private static void copyTree(Path from,Path to) throws IOException {
        try(var paths=Files.walk(from,8)) {
            for(Path p:paths.toList()) {
                if(Files.isSymbolicLink(p))throw invalid("LINK_ASSET");
                Path dest=to.resolve(from.relativize(p));
                if(Files.isDirectory(p))Files.createDirectories(dest);else if(Files.isRegularFile(p))Files.copy(p,dest);else throw invalid("INVALID_ASSET");
            }
        }
    }
    public void activate(String sdk,String build) {
        Path candidate=bundle(sdk,build);
        try(var admitted=scoped(admit(candidate,BuildValidator.Operation.ACTIVATION),sdk,build)) {
            Path temporary=config.indexRoot().resolve(".active-"+UUID.randomUUID()+".json");
            Files.write(temporary,Wire.bytes(map("pointerVersion",1,"source",config.sourceDefault(),"sdkVersion",sdk,"buildId",build)),StandardOpenOption.CREATE_NEW);
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary,config.indexRoot().resolve("active.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException e) { throw invalid("ATOMIC_ACTIVATION_FAILED"); }
    }
    private com.fasterxml.jackson.databind.JsonNode pointer() { return read(config.indexRoot().resolve("active.json"),16384); }
    public Handle current(BuildValidator.Operation operation) { return current(pointer(),operation); }
    private Handle current(com.fasterxml.jackson.databind.JsonNode pointer,BuildValidator.Operation operation) {
        if(pointer.size()!=4 || pointer.path("pointerVersion").asInt()!=1 || !pointer.path("source").asText().equals(config.sourceDefault()))throw invalid("ACTIVE_POINTER");
        return scoped(admit(bundle(pointer.path("sdkVersion").asText(),pointer.path("buildId").asText()),operation),pointer.path("sdkVersion").asText(),pointer.path("buildId").asText());
    }
    public Handle validate(String sdk,String build) { return scoped(admit(bundle(sdk,build),BuildValidator.Operation.CLI),sdk,build); }
    private static Handle scoped(Handle handle,String sdk,String build) {
        if(!handle.manifest.sdkVersion().equals(sdk) || !handle.manifest.buildId().equals(build)) { handle.close();throw invalid("INVALID_SCOPE"); }return handle;
    }
    public Handle admit(Path directory,BuildValidator.Operation operation) {
        SqliteSymbolSource source=null;LuceneLexicalIndex index=null;SqliteRelationshipStore graph=null;
        try {
            if(!Files.isDirectory(directory) || Files.isSymbolicLink(directory))throw invalid("BUNDLE_UNAVAILABLE");
            var seal=read(directory.resolve("bundle.json"),1048576);
            if(seal.size()!=2 || seal.path("bundleVersion").asInt()!=1 || !seal.path("assets").equals(Wire.JSON.valueToTree(inventory(directory))))throw invalid("BUNDLE_INTEGRITY");
            var manifestNode=read(directory.resolve("manifest.json"),65536);Wire.validate("manifest",manifestNode);
            IndexManifest manifest=Wire.JSON.treeToValue(manifestNode,IndexManifest.class);
            if(!manifest.capabilities().equals(IndexManifest.withDocumentation(manifest.buildId(),manifest.sdkVersion(),manifest.toolchainFingerprint()).capabilities()) || !ContractVersions.PROFILE.equals(manifest.profile()) || !ContentHashes.runtimeFingerprint().equals(manifest.runtimeDigest())
                || !ContentHashes.recipeFingerprint(ContractVersions.PROFILE,List.of()).equals(manifest.recipeDigest())
                || !manifest.buildId().equals("b-"+manifest.inputDigest()))throw invalid("INCOMPATIBLE_PROFILE");
            var report=read(directory.resolve("ingestion-report.json"),8388608);Wire.validate("ingestion-report",report);
            if(!report.path("buildId").asText().equals(manifest.buildId()) || !report.path("sdkVersion").asText().equals(manifest.sdkVersion())
                || !report.path("toolchainFingerprint").asText().equals(manifest.toolchainFingerprint()) || !report.path("profile").asText().equals(manifest.profile()) || !report.path("runtimeFingerprint").asText().equals(manifest.runtimeDigest())
                || !report.path("recipeFingerprint").asText().equals(manifest.recipeDigest()) || !report.path("capabilityFingerprint").asText().equals(manifest.capabilityFingerprint())
                || report.path("aborted").asBoolean())throw invalid("REPORT_BINDING");
            for(var f:report.path("failures"))if(f.path("severity").asText().equals("BUILD_INVALIDATING"))throw invalid("BUILD_INVALIDATING_OUTCOME");
            for(String required:List.of("CATALOG","RELATIONSHIPS","LEXICAL_INDEX","VALIDATION")) {
                boolean done=false;for(var stage:report.path("stages"))if(stage.path("stage").asText().equals(required))done=Set.of("COMPLETED","SKIPPED_ON_RESUME").contains(stage.path("status").asText()) || required.equals("CATALOG")&&stage.path("status").asText().equals("FAILED");
                if(!done)throw invalid("INCOMPLETE_STAGING");
            }
            source=new SqliteSymbolSource(directory.resolve("catalog.sqlite"),operation);source.verifyManifest(manifest);
            index=new LuceneLexicalIndex(directory.resolve("lexical-index"),LexicalIndex.Binding.of(source));index.verifyIntegrity();
            graph=new SqliteRelationshipStore(directory.resolve("catalog.sqlite"),manifest.buildId(),new RelationshipScope(manifest.sdkVersion()),key);
            boolean complete=report.path("completion").asText().equals("COMPLETE_FOR_PROFILE");
            var status=new ReadTools.Status(operation==BuildValidator.Operation.CLI?"VALIDATED":"ACTIVE",manifest,report);
            return new Handle(directory,manifest,source,index,graph,new ReadTools(source,source,index,graph,status,key,complete,
                complete?"Complete for implemented ingestion profile; independent expectations may be unavailable":"Partial ingestion coverage; inspect ingestion report"));
        }catch(Exception e) {
            if(graph!=null)graph.close();if(index!=null)index.close();if(source!=null)source.close();
            if(e instanceof IllegalStateException s && s.getMessage()!=null && s.getMessage().startsWith("BUILD_INVALID:"))throw s;
            throw invalid("INCOMPATIBLE_OR_CORRUPT_BUNDLE");
        }
    }
    private static com.fasterxml.jackson.databind.JsonNode read(Path path,int max) {
        try(var in=Files.newInputStream(path)) { byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw invalid("METADATA_LIMIT");return Wire.JSON.readTree(bytes); }
        catch(IOException e) { throw invalid("METADATA_UNAVAILABLE"); }
    }
    private static Map<String,String> inventory(Path root) throws IOException {
        var assets=new TreeMap<String,String>();long bytes=0;
        try(var paths=Files.walk(root,8)) {
            var iterator=paths.iterator();int entries=0;
            while(iterator.hasNext()) {
                Path path=iterator.next();if(++entries>4096 || Files.isSymbolicLink(path))throw invalid("ASSET_LIMIT_OR_LINK");
                if(Files.isDirectory(path))continue;if(!Files.isRegularFile(path))throw invalid("INVALID_ASSET");
                String relative=root.relativize(path).toString().replace('\\','/');if(relative.equals("bundle.json"))continue;
                if((bytes+=Files.size(path))>32L*1024*1024*1024)throw invalid("BUNDLE_BYTE_LIMIT");
                MessageDigest digest;try { digest=MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException e) { throw new AssertionError(e); }
                try(var in=Files.newInputStream(path)) { byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1)digest.update(block,0,n); }
                assets.put(relative,HexFormat.of().formatHex(digest.digest()));
            }
        }
        if(!assets.containsKey("catalog.sqlite") || !assets.containsKey("manifest.json") || !assets.containsKey("ingestion-report.json") || assets.keySet().stream().noneMatch(p->p.startsWith("lexical-index/")))throw invalid("MISSING_REQUIRED_ASSET");
        return assets;
    }
    public static final class Handle implements AutoCloseable {
        public final Path path;public final IndexManifest manifest;public final ReadTools tools;
        private final SqliteSymbolSource source;private final LuceneLexicalIndex index;private final SqliteRelationshipStore graph;
        Handle(Path path,IndexManifest manifest,SqliteSymbolSource source,LuceneLexicalIndex index,SqliteRelationshipStore graph,ReadTools tools) {
            this.path=path;this.manifest=manifest;this.source=source;this.index=index;this.graph=graph;this.tools=tools;
        }
        public void close() { try { graph.close(); }finally { try { index.close(); }finally { source.close(); } } }
    }
    /** Publication swaps ownership; old handles close only after their last request lease. */
    public static final class Serving implements AutoCloseable {
        private final BundleRepository repository;private Entry current;private boolean closed;private com.fasterxml.jackson.databind.JsonNode observed;
        private static final class Entry { final Handle handle;int readers;boolean retired;Entry(Handle h) { handle=h; } }
        public Serving(BundleRepository repository) {
            this.repository=repository;var pointer=repository.pointer();
            current=new Entry(repository.current(pointer,BuildValidator.Operation.STARTUP));observed=pointer;
        }
        public synchronized Lease lease() { if(closed)throw invalid("SERVICE_CLOSED");current.readers++;return new Lease(current); }
        public boolean reloadIfChanged() {
            try { synchronized(this) { if(closed)return false;if(repository.pointer().equals(observed))return true; } }
            catch(RuntimeException e) { return false; }
            return reload();
        }
        public boolean reload() {
            Handle replacement;com.fasterxml.jackson.databind.JsonNode pointer;
            try { pointer=repository.pointer();replacement=repository.current(pointer,BuildValidator.Operation.RELOAD); }
            catch(RuntimeException e) { return false; }
            synchronized(this) {
                if(closed) { replacement.close();return false; }
                if(replacement.path.equals(current.handle.path)) { replacement.close();observed=pointer;return true; }
                Entry old=current;current=new Entry(replacement);observed=pointer;old.retired=true;if(old.readers==0)old.handle.close();return true;
            }
        }
        public final class Lease implements AutoCloseable {
            private Entry entry;Lease(Entry e) { entry=e; }
            public ReadTools tools() { return entry.handle.tools; }
            public IndexManifest manifest() { return entry.handle.manifest; }
            public void close() { synchronized(Serving.this) { if(entry!=null) { if(--entry.readers==0 && entry.retired)entry.handle.close();entry=null; } } }
        }
        public synchronized void close() { if(closed)return;closed=true;current.retired=true;if(current.readers==0)current.handle.close(); }
    }
}
