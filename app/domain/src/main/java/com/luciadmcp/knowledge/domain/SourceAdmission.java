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
        private int files;
        private long bytes;
        void admit(String path) {
            int size=path.getBytes(StandardCharsets.UTF_8).length;
            if(size>MAX_PATH_BYTES)throw new IllegalArgumentException("SOURCE_PATH_TOO_LARGE");
            if(files>=MAX_FILES || bytes+size>MAX_TOTAL_PATH_BYTES)throw new IllegalArgumentException("SOURCE_ENUMERATION_LIMIT");
            files++;bytes+=size;
        }
    }
    public static List<Path> enumerate(Path root) {
        List<Path> files = new ArrayList<>();
        Budget budget=new Budget();
        try (var walk = Files.walk(root)) {
            var iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (!Files.isRegularFile(file)) continue;
                budget.admit(relative(root,file));
                files.add(file);
            }
        } catch (IOException e) { throw new UncheckedIOException("SOURCE_ENUMERATION_FAILED", e); }
        files.sort(Comparator.comparing(file -> relative(root, file)));
        return List.copyOf(files);
    }
}
