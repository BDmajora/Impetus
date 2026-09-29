package com.bdmajora.impetus.engine.impl.render.chunk.compile.estimation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadDurationEstimatorTest {
    @Test
    void movingAverageTracksObservedThroughput() {
        UploadDurationEstimator estimator = new UploadDurationEstimator();
        assertEquals(0, estimator.estimateUploadDuration(0));
        assertEquals(250, estimator.estimateUploadDuration(1000));
        estimator.recordUpload(0, 5);
        estimator.recordUpload(5, 0);
        estimator.recordUpload(1000, 2000);
        assertEquals(1000, estimator.getLastUploadBytes());
        assertEquals(2000, estimator.getLastUploadDurationNanos());
        assertEquals(250, estimator.getLastUploadEstimateNanos());
        assertEquals(2000, estimator.estimateUploadDuration(1000));
        estimator.recordUpload(1000, 4000);
        assertTrue(estimator.estimateUploadDuration(1000) > 2000);
        assertTrue(estimator.estimateUploadDuration(1000) < 4000);
        assertEquals(1, estimator.estimateUploadDuration(1) >= 1 ? 1 : 0);
    }
}
