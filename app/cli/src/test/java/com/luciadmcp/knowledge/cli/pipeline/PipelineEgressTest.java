package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.cli.testsupport.EgressGuard;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC 8: no pipeline path — including checkpoint write, restart, and resume — performs a network
 * request. The run happens under the Story-1.2 egress guard; a single recorded resolution fails the
 * test. The {@code trainings}/AD-28 exception is deliberately not exercised.
 */
class PipelineEgressTest {

    @AfterEach
    void disarmGuard() {
        EgressGuard.disarm();
    }

    /** A full pipeline plus a crash-and-resume cycle attempts zero host resolution. */
    @Test
    void noEgressAcrossFullRunAndResume(@TempDir Path tmp) throws Exception {
        // Fail loudly if the guard provider was not wired — otherwise "no egress" would be vacuous.
        EgressGuard.assertInstalledAndTrips();

        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        EgressGuard.arm();
        PipelineResult resumed;
        try {
            // Crash mid-run: checkpoint is written to disk for the completed stages.
            List<PipelineStage> crashLog = new ArrayList<>();
            List<Stage> crashing = PipelineTestSupport.recordingStages(crashLog);
            PipelineTestSupport.injectCrash(crashing, PipelineStage.VECTOR_INDEX, crashLog);
            IngestPipelineRunner first = new IngestPipelineRunner(crashing, FailurePolicy.FAIL_FAST);
            assertThrows(RuntimeException.class, () -> first.runToResult(args));

            // Resume: reads the checkpoint from disk and finishes the remaining stages.
            resumed = new IngestPipelineRunner(
                    PipelineTestSupport.recordingStages(new ArrayList<>()), FailurePolicy.FAIL_FAST)
                    .runToResult(args);
        } finally {
            EgressGuard.disarm();
        }

        assertTrue(EgressGuard.violations().isEmpty(),
                () -> "pipeline (incl. resume) must not attempt egress; violations=" + EgressGuard.violations());
        assertEquals(EnumSet.allOf(PipelineStage.class), resumed.completedStages());
        assertTrue(resumed.activationOccurred());
    }
}
