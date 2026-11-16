package com.bdmajora.impetus.engine.impl.render.chunk.compile.estimation;

// Estimates upload times from a moving average of past uploads.
public final class UploadDurationEstimator {
    // Weight of a new sample in the average.
    private static final double NEW_SAMPLE_WEIGHT = 0.05D;
    // Nanoseconds per byte before any sample.
    private static final double INITIAL_NANOS_PER_BYTE = 0.25D;

    // Current estimate of nanoseconds per byte.
    private double nanosPerByte = INITIAL_NANOS_PER_BYTE;
    // Whether a sample was recorded.
    private boolean hasRealSamples;

    // Bytes of the last upload.
    private long lastUploadBytes;
    // Duration of the last upload.
    private long lastUploadDurationNanos;
    // Estimate made for the last upload.
    private long lastUploadEstimateNanos;

    // Returns the estimated nanoseconds of an upload and zero for no bytes.
    public long estimateUploadDuration(long byteCount) {
        if (byteCount <= 0) {
            return 0L;
        }

        return Math.max(1L, (long)(this.nanosPerByte * byteCount));
    }

    // Adds an upload to the moving average and ignores empty or instant uploads.
    public void recordUpload(long byteCount, long durationNanos) {
        if (byteCount <= 0 || durationNanos <= 0) {
            return;
        }

        this.lastUploadBytes = byteCount;
        this.lastUploadDurationNanos = durationNanos;
        this.lastUploadEstimateNanos = this.estimateUploadDuration(byteCount);

        double sampleNanosPerByte = (double)durationNanos / byteCount;

        if (this.hasRealSamples) {
            this.nanosPerByte = (this.nanosPerByte * (1.0D - NEW_SAMPLE_WEIGHT)) + (sampleNanosPerByte * NEW_SAMPLE_WEIGHT);
        } else {
            this.nanosPerByte = sampleNanosPerByte;
            this.hasRealSamples = true;
        }
    }

    // Returns the bytes of the last upload.
    public long getLastUploadBytes() {
        return this.lastUploadBytes;
    }

    // Returns the duration of the last upload.
    public long getLastUploadDurationNanos() {
        return this.lastUploadDurationNanos;
    }

    // Returns the estimate made for the last upload.
    public long getLastUploadEstimateNanos() {
        return this.lastUploadEstimateNanos;
    }
}
