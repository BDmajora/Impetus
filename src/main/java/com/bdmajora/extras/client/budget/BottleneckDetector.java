package com.bdmajora.extras.client.budget;

// Which side of the frame holds it back, GpuShift 1.2.8's detector (MIT): EMAs of the CPU's frame time and the GPU's time for its share of a frame, compared once enough samples are in; a new verdict needs a run of agreeing frames, so one hitch cannot swing the budget. GpuShift ships this unplugged; here the render budget's GPU timer feeds it
public final class BottleneckDetector {
    public enum State {
        // No timer queries on this driver
        UNAVAILABLE,
        // Not enough samples yet
        UNKNOWN,
        // Frames are at target, nothing to shift
        HEADROOM,
        CPU_BOUND,
        GPU_BOUND,
        MIXED
    }

    static final int MIN_VALID_SAMPLES = 20;
    static final int SWITCH_CONFIRMATIONS = 30;
    private static final double EMA_ALPHA = 0.15;
    // GPU time at or under this share of CPU time leaves the GPU idle waiting on the CPU; at or over the other, the GPU is the one behind
    private static final double CPU_BOUND_RATIO = 0.65;
    private static final double GPU_BOUND_RATIO = 0.85;
    // CPU frames this close to target count as headroom whatever the split
    private static final double HEADROOM_SLACK = 1.05;
    // A GPU reading this long is a stall or a driver hiccup, not a frame
    private static final double MAX_GPU_MILLIS = 250.0;

    private boolean available = true;
    private double cpuEma;
    private double gpuEma;
    private int validSamples;
    private State state = State.UNKNOWN;
    private State candidate = State.UNKNOWN;
    private int confirmations;

    // One frame's CPU and GPU milliseconds against the profile's target; nonsense readings are ignored
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
            this.confirmations++;
        }
        if (this.confirmations >= SWITCH_CONFIRMATIONS) {
            this.state = this.candidate;
        }
        return this.state;
    }

    public State state() {
        return this.state;
    }

    // The GPU time EMA, 0 until measured
    public double gpuFrameMillis() {
        return this.gpuEma;
    }

    // The driver has no timer queries; stays so until reset
    public void markUnavailable() {
        this.available = false;
        this.state = State.UNAVAILABLE;
    }

    public void reset() {
        this.available = true;
        this.cpuEma = 0.0;
        this.gpuEma = 0.0;
        this.validSamples = 0;
        this.state = State.UNKNOWN;
        this.candidate = State.UNKNOWN;
        this.confirmations = 0;
    }

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

    private static double ema(double current, double sample) {
        return current <= 0.0 ? sample : current + EMA_ALPHA * (sample - current);
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
