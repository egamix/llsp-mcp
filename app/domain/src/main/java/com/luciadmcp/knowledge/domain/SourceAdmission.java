package com.luciadmcp.knowledge.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finite admission shared by discovery and input sealing; never materialize an unbounded walk. */
public final class SourceAdmission {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_FILES = 100_000;
    public static final int MAX_PATH_BYTES = 4096;
    public static final long MAX_TOTAL_PATH_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TRAVERSAL_ENTRIES=200_001, MAX_TRAVERSAL_DEPTH=128;
    private SourceAdmission() {}

    /** Read at most limit+1 bytes, including when a file grows after enumeration. */
    public static byte[] read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("SOURCE_FILE_TOO_LARGE");
            return bytes;
        }
    }
    public static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
    /** Incremental admission also used before appending any path to the retained universe. */
    static final class Budget {
        private final int maxFiles;
        Budget() { this(MAX_FILES); }
        Budget(int maxFiles) { this.maxFiles=maxFiles; }
        private int files;
        private long bytes;
        void admit(String path) {
            int size=path.getBytes(StandardCharsets.UTF_8).length;
            if(size>MAX_PATH_BYTES)throw new IllegalArgumentException("SOURCE_PATH_TOO_LARGE");
            if(files>=maxFiles || bytes+size>MAX_TOTAL_PATH_BYTES)throw new IllegalArgumentException("SOURCE_ENUMERATION_LIMIT");
            files++;bytes+=size;
        }
    }
    @FunctionalInterface
    interface AttributeReader { java.nio.file.attribute.BasicFileAttributes read(Path path) throws IOException; }
    public static List<Path> enumerate(Path root) {
        return enumerate(root,MAX_FILES,MAX_TRAVERSAL_ENTRIES,MAX_TRAVERSAL_DEPTH,
                path->Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class));
    }
    // Package-private limits and attribute seam exercise the production walk using small fixtures.
    static List<Path> enumerate(Path root,int maxFiles,int maxEntries,int maxDepth,AttributeReader attributes) {
        List<Path> files = new ArrayList<>();
        Budget budget=new Budget(maxFiles);
        try {
            Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<Path>() {
                private int entries;
                private void charge(Path path) {
                    int depth=path.equals(root)?0:root.relativize(path).getNameCount();
                    if(++entries>maxEntries || depth>maxDepth)throw new IllegalArgumentException("SOURCE_TRAVERSAL_LIMIT");
                }
                @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs) {
                    charge(dir);return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    charge(file);
                    // Explicit attribute reads preserve regular-file symlink behavior and propagate failures.
                    if(attributes.read(file).isRegularFile()) { budget.admit(relative(root,file));files.add(file); }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult visitFileFailed(Path file,IOException error) throws IOException { throw error; }
            });
        } catch (IOException e) { throw new UncheckedIOException("SOURCE_ENUMERATION_FAILED", e); }
        files.sort(Comparator.comparing(file -> relative(root, file)));
        return List.copyOf(files);
    }
}
