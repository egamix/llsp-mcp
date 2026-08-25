package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The build identity is a content hash over the complete input set: deterministic for equal inputs,
 * distinct for any changed input — path structure OR file bytes (AC 1, 5).
 */
class BuildContextTest {

    /** AC 7: the same inputs always yield the same buildId, digest, and staging dir. */
    @Test
    void sameInputsProduceSameIdentity(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        BuildContext a = BuildContext.forInputs(args);
        BuildContext b = BuildContext.forInputs(args);
        assertEquals(a, b);
        assertEquals(a.buildId(), b.buildId());
        assertEquals(a.sealedInputDigest(), b.sealedInputDigest());
    }

    /** AC 4: changing any input path changes the digest, buildId, and staging dir. */
    @Test
    void changedInputChangesIdentity(@TempDir Path tmp) throws Exception {
        IngestArguments base = PipelineTestSupport.argsIn(tmp.resolve("base"));
        IngestArguments changed = PipelineTestSupport.argsIn(tmp.resolve("other"));
        BuildContext a = BuildContext.forInputs(base);
        BuildContext b = BuildContext.forInputs(changed);
        assertNotEquals(a.sealedInputDigest(), b.sealedInputDigest());
        assertNotEquals(a.buildId(), b.buildId());
        assertNotEquals(a.stagingDir(), b.stagingDir());
    }

    /** Adding the optional trainings path changes the identity (it is part of the sealed input set). */
    @Test
    void trainingsPresenceChangesIdentity(@TempDir Path tmp) throws Exception {
        IngestArguments without = PipelineTestSupport.argsIn(tmp);
        IngestArguments with = new IngestArguments(
                without.javadoc(), without.samples(), without.guides(), without.model(), without.out(),
                Optional.of(tmp.resolve("trainings")));
        assertNotEquals(
                BuildContext.forInputs(without).sealedInputDigest(),
                BuildContext.forInputs(with).sealedInputDigest());
    }

    /** The staging dir is build-scoped under {out}/.staging/{buildId}, not the final layout. */
    @Test
    void stagingDirIsUnderOutKeyedByBuildId(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        BuildContext ctx = BuildContext.forInputs(args);
        Path expected = args.out().resolve(".staging").resolve(ctx.buildId());
        assertEquals(expected, ctx.stagingDir());
        assertTrue(ctx.buildId().startsWith("b-"), "buildId should be the b-<digest> form");
    }

    /** AC 5: input file content is part of the identity — equal content reproduces the same buildId. */
    @Test
    void sameFileContentProducesSameBuildId(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        Files.writeString(args.javadoc().resolve("Foo.html"), "<html>alpha</html>", StandardCharsets.UTF_8);
        String first = BuildContext.forInputs(args).buildId();
        // Rewrite byte-identical content; the identity must not change.
        Files.writeString(args.javadoc().resolve("Foo.html"), "<html>alpha</html>", StandardCharsets.UTF_8);
        assertEquals(first, BuildContext.forInputs(args).buildId());
    }

    /** AC 3, 5: changing one byte of one input file changes the buildId (fresh run, not a resume). */
    @Test
    void changedInputByteChangesBuildId(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        Path file = args.javadoc().resolve("Foo.html");
        Files.writeString(file, "<html>alpha</html>", StandardCharsets.UTF_8);
        BuildContext before = BuildContext.forInputs(args);

        Files.writeString(file, "<html>alphb</html>", StandardCharsets.UTF_8); // one byte differs
        BuildContext after = BuildContext.forInputs(args);

        assertNotEquals(before.sealedInputDigest(), after.sealedInputDigest());
        assertNotEquals(before.buildId(), after.buildId());
    }

    /** Adding a file under an input root changes the identity even when existing files are untouched. */
    @Test
    void addingInputFileChangesBuildId(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        Files.writeString(args.samples().resolve("A.java"), "class A {}", StandardCharsets.UTF_8);
        String before = BuildContext.forInputs(args).buildId();
        Files.writeString(args.samples().resolve("B.java"), "class B {}", StandardCharsets.UTF_8);
        assertNotEquals(before, BuildContext.forInputs(args).buildId());
    }
}
