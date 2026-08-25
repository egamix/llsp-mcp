package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The provisional build identity: deterministic for equal inputs, distinct for changed inputs. */
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

    /** The staging dir is build-scoped under {out}/.staging/{buildId} (Task 2), not the final layout. */
    @Test
    void stagingDirIsUnderOutKeyedByBuildId(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        BuildContext ctx = BuildContext.forInputs(args);
        Path expected = args.out().resolve(".staging").resolve(ctx.buildId());
        assertEquals(expected, ctx.stagingDir());
        assertTrue(ctx.buildId().startsWith("b-"), "buildId should be the provisional b-<digest> form");
    }
}
