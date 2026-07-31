package com.myagent.assistant.observability;

import com.myagent.assistant.rag.dto.RagTiming;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagTimingTraceTest {

    @AfterEach
    void clearTrace() {
        RagTimingTrace.clear();
    }

    @Test
    void snapshotAggregatesRepeatedStagesAndCalculatesTotal() {
        RagTimingTrace.begin();
        long totalStartedAt = System.nanoTime() - 20_000_000L;

        RagTimingTrace.addElapsed(RagTimingTrace.EMBEDDING, System.nanoTime() - 3_000_000L);
        RagTimingTrace.addElapsed(RagTimingTrace.EMBEDDING, System.nanoTime() - 2_000_000L);
        RagTimingTrace.addElapsed(RagTimingTrace.LLM_GENERATION, System.nanoTime() - 8_000_000L);

        RagTiming timing = RagTimingTrace.snapshot(totalStartedAt);

        assertThat(timing.getEmbeddingMs()).isGreaterThanOrEqualTo(4L);
        assertThat(timing.getLlmGenerationMs()).isGreaterThanOrEqualTo(7L);
        assertThat(timing.getTotalMs()).isGreaterThanOrEqualTo(19L);
        assertThat(timing.getOtherMs()).isNotNegative();
        assertThat(timing.getDominantStage()).isEqualTo(RagTimingTrace.LLM_GENERATION);
    }
}
