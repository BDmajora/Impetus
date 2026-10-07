package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.client.budget.BottleneckDetector.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BottleneckDetectorTest {
    private static final double TARGET = 16.7;

    // Feeds the same reading until the detector has had the chance to settle on it
    private static State settle(BottleneckDetector detector, double cpu, double gpu) {
        State state = detector.state();
        for (int i = 0; i < BottleneckDetector.MIN_VALID_SAMPLES + BottleneckDetector.SWITCH_CONFIRMATIONS; i++) {
            state = detector.record(cpu, gpu, TARGET);
        }
        return state;
    }

    @Test
    void theSplitOfFrameTimeNamesTheBottleneck() {
        // At target nothing is behind; past it, the GPU's share of the frame decides
        assertEquals(State.HEADROOM, BottleneckDetector.classify(17.0, 5.0, TARGET));
        assertEquals(State.CPU_BOUND, BottleneckDetector.classify(30.0, 15.0, TARGET));
        assertEquals(State.GPU_BOUND, BottleneckDetector.classify(30.0, 27.0, TARGET));
        assertEquals(State.MIXED, BottleneckDetector.classify(30.0, 22.5, TARGET));
    }

    @Test
    void aVerdictNeedsWarmUpAndARunOfAgreeingFrames() {
        BottleneckDetector detector = new BottleneckDetector();
        assertEquals(State.UNKNOWN, detector.state());
        assertEquals(0.0, detector.gpuFrameMillis());
        // Twenty samples before any verdict, then thirty agreeing ones before it is adopted
        for (int i = 0; i < BottleneckDetector.MIN_VALID_SAMPLES - 1; i++) {
            assertEquals(State.UNKNOWN, detector.record(30.0, 10.0, TARGET));
        }
        for (int i = 0; i < BottleneckDetector.SWITCH_CONFIRMATIONS - 1; i++) {
            assertEquals(State.UNKNOWN, detector.record(30.0, 10.0, TARGET));
        }
        assertEquals(State.CPU_BOUND, detector.record(30.0, 10.0, TARGET));
        assertEquals(10.0, detector.gpuFrameMillis(), 1e-9);

        // One disagreeing frame starts a new run without changing the verdict
        detector.record(30.0, 60.0, TARGET);
        assertEquals(State.CPU_BOUND, detector.state());
        assertEquals(State.GPU_BOUND, settle(detector, 30.0, 60.0));

        // Readings that cannot be frames are ignored
        assertEquals(State.GPU_BOUND, detector.record(Double.NaN, 10.0, TARGET));
        assertEquals(State.GPU_BOUND, detector.record(30.0, 0.0, TARGET));
        assertEquals(State.GPU_BOUND, detector.record(30.0, 400.0, TARGET));
        assertEquals(State.GPU_BOUND, detector.record(30.0, 10.0, -1.0));

        // A driver without timer queries says so until reset
        detector.markUnavailable();
        assertEquals(State.UNAVAILABLE, detector.record(30.0, 10.0, TARGET));
        detector.reset();
        assertEquals(State.UNKNOWN, detector.state());
        assertEquals(0.0, detector.gpuFrameMillis());
        assertEquals(State.HEADROOM, settle(detector, 12.0, 11.0));
    }
}
