package com.bdmajora.extras.client.budget;

// Decides whether the processor or the graphics card limits the frame rate.
public final class BottleneckDetector {
    // Verdict of the detector.
    public enum State {
        // The graphics timing is not available.
        UNAVAILABLE,
        // Not enough samples yet.
        UNKNOWN,
        // The frame time is within the target.
        HEADROOM,
        // The processor limits the frame rate.
        CPU_BOUND,
        // The graphics card limits the frame rate.
        GPU_BOUND,
        // Both limit the frame rate.
        MIXED
    }

    // Samples needed before a verdict.
    static final int MIN_VALID_SAMPLES = 20;
    // Consecutive verdicts needed to change the state.
    static final int SWITCH_CONFIRMATIONS = 30;
    // Weight of a new sample.
    private static final double EMA_ALPHA = 0.15;
    // Largest graphics to processor ratio of a processor limit.
    private static final double CPU_BOUND_RATIO = 0.65;
    // Smallest graphics to processor ratio of a graphics limit.
    private static final double GPU_BOUND_RATIO = 0.85;
    // Factor over the target that still counts as headroom.
    private static final double HEADROOM_SLACK = 1.05;
    // Largest graphics time that counts as a valid sample.
    private static final double MAX_GPU_MILLIS = 250.0;

    // Whether graphics timing works.
    private boolean available = true;
    // Smoothed processor time.
    private double cpuEma;
    // Smoothed graphics time.
    private double gpuEma;
    // Number of valid samples.
    private int validSamples;
    // Confirmed state.
    private State state = State.UNKNOWN;
    // Verdict that waits for confirmation.
    private State candidate = State.UNKNOWN;
    // Number of consecutive verdicts of the candidate.
    private int confirmations;

    // Adds a sample and returns the confirmed state.
    public State record(double cpuMillis, double gpuMillis, double targetMillis) {
        if (!this.available || !valid(cpuMillis) || !valid(gpuMillis) || gpuMillis > MAX_GPU_MILLIS || !valid(targetMillis)) {
            return this.state;
        }

        this.cpuEma = ema(this.cpuEma, cpuMillis);
        this.gpuEma = ema(this.gpuEma, gpuMillis);
        if (++this.validSamples < MIN_VALID_SAMPLES) {
            return this.state;
        }

        State verdict = classify(this.cpuEma, this.gpuEma, targetMillis);
        if (verdict != this.candidate) {
            this.candidate = verdict;
            this.confirmations = 1;
        } else {
            this.confirmations = Math.min(this.confirmations + 1, SWITCH_CONFIRMATIONS);
        }
        if (this.confirmations >= SWITCH_CONFIRMATIONS) {
            this.state = this.candidate;
        }
        return this.state;
    }

    // Returns the confirmed state.
    public State state() {
        return this.state;
    }

    // Returns the smoothed graphics time.
    public double gpuFrameMillis() {
        return this.gpuEma;
    }

    // Marks the graphics timing as unavailable.
    public void markUnavailable() {
        this.available = false;
        this.state = State.UNAVAILABLE;
    }

    // Forgets every sample.
    public void reset() {
        this.available = true;
        this.cpuEma = 0.0;
        this.gpuEma = 0.0;
        this.validSamples = 0;
        this.state = State.UNKNOWN;
        this.candidate = State.UNKNOWN;
        this.confirmations = 0;
    }

    // Returns the verdict for the smoothed times.
    static State classify(double cpuMillis, double gpuMillis, double targetMillis) {
        if (cpuMillis <= targetMillis * HEADROOM_SLACK) {
            return State.HEADROOM;
        }
        double ratio = gpuMillis / cpuMillis;
        if (ratio <= CPU_BOUND_RATIO) {
            return State.CPU_BOUND;
        }
        return ratio >= GPU_BOUND_RATIO ? State.GPU_BOUND : State.MIXED;
    }

    // Returns the smoothed value after a sample.
    private static double ema(double current, double sample) {
        return current <= 0.0 ? sample : current + EMA_ALPHA * (sample - current);
    }

    // Returns whether the value is a positive finite number.
    private static boolean valid(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
