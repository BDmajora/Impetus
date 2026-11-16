package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

// Limits how far from the camera sections are tested by the rasterizer.
public final class RasterBudget {
    // Smallest squared chunk distance of the limit.
    public static final int FLOOR = 12;

    // Yield above which the limit doubles.
    public static final int HIGH_YIELD = percent(15);
    // Yield below which the limit halves.
    public static final int LOW_YIELD = percent(5);

    // Fewest tests of a frame that update the average.
    public static final int MIN_SAMPLE = 16;

    // Number of far sections that are tested regardless of the limit.
    public static final int CHEAP_PASS = 32;

    // Frames between two probe frames.
    public static final int PROBE_PERIOD = 64;

    // Tests a probe may add for every section it removes.
    public static final int TESTS_PER_REMOVED = 2;

    // Shift of the weight of a new sample.
    private static final int EMA_SHIFT = 2;
    // Bits of the fixed point yield.
    private static final int Q = 16;

    // Limit that tests every section.
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    // Current limit.
    private int limit = FLOOR;
    // Moving average of the yield.
    private int yieldEma;
    // Largest reachable squared distance.
    private int ceiling = UNBOUNDED;

    // Limit of the current frame.
    private int frameLimit = FLOOR;

    // Squared distance beyond which nothing is tested in the frame.
    private int testable = UNBOUNDED;

    // Fixed limit or zero.
    private int pinned;

    // Frame counter.
    private int frames;
    // Whether the frame is a probe.
    private boolean probing;

    // Moving averages of the visible count and the tests.
    private int visibleEma = -1;
    private int testedEma;

    // Far sections reached this frame.
    private int farReached;
    // Largest squared distance reached this frame.
    private int farthest;
    // Sections tested this frame.
    private int tested;
    // Sections culled this frame.
    private int culled;

    // Starts a frame with the largest reachable squared distance.
    public void beginFrame(int ceiling) {
        this.ceiling = Math.max(FLOOR, ceiling);
        this.probing = this.pinned == 0 && this.limit < this.ceiling && (++this.frames & (PROBE_PERIOD - 1)) == 0
                && this.visibleEma >= 0;
        this.frameLimit = this.probing ? UNBOUNDED : this.limit();
        this.testable = this.frameLimit >= this.ceiling ? UNBOUNDED
                : Math.min(this.ceiling, saturatingDouble(saturatingDouble(this.frameLimit)));
        this.farReached = 0;
        this.farthest = 0;
        this.tested = 0;
        this.culled = 0;
    }

    // Returns whether a far section at a squared distance is tested.
    public boolean shouldTest(int squaredChunkDist) {
        this.farReached++;

        if (squaredChunkDist > this.farthest) {
            this.farthest = squaredChunkDist;
        }

        if (squaredChunkDist > this.testable || (squaredChunkDist > this.frameLimit && this.farReached > CHEAP_PASS)) {
            return false;
        }

        this.tested++;
        return true;
    }

    // Counts a culled section.
    public void recordCulled() {
        this.culled++;
    }

    // Ends a frame with the number of visible sections and adapts the limit.
    public void endFrame(int visible) {
        if (!this.probing) {
            if (this.visibleEma < 0) {
                this.visibleEma = visible << 4;
                this.testedEma = this.tested << 4;
            } else {
                this.visibleEma += ((visible << 4) - this.visibleEma) >> EMA_SHIFT;
                this.testedEma += ((this.tested << 4) - this.testedEma) >> EMA_SHIFT;
            }
        }

        if (this.tested < MIN_SAMPLE) return;

        int yield = (int) (((long) this.culled << Q) / this.tested);

        if (this.probing) {
            int removed = (this.visibleEma >> 4) - visible;
            int added = this.tested - (this.testedEma >> 4);

            if (removed > 0 && (long) removed * TESTS_PER_REMOVED >= added) {
                this.limit = this.ceiling;
                this.yieldEma = yield;
            }

            return;
        }

        this.yieldEma += (yield - this.yieldEma) >> EMA_SHIFT;

        if (this.pinned != 0) {
            return;
        }

        if (this.yieldEma >= HIGH_YIELD && yield >= HIGH_YIELD) {
            this.limit = Math.min(saturatingDouble(this.limit), this.ceiling);
        } else if (this.yieldEma <= LOW_YIELD) {
            this.limit = Math.max(this.limit >> 1, FLOOR);
        } else {
            this.limit = Math.max(Math.min(this.limit, this.ceiling), FLOOR);
        }
    }

    // Holds the limit at a value and restores adaptive behavior for zero and rejects a negative value.
    public void pin(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Negative limit " + limit);
        }
        this.pinned = limit;

        if (limit != 0) {
            this.limit = limit;
        }
    }

    // Copies the adaptive state of another budget.
    public void copyFrom(RasterBudget other) {
        java.util.Objects.requireNonNull(other, "other");
        this.limit = other.limit;
        this.yieldEma = other.yieldEma;
        this.visibleEma = other.visibleEma;
        this.testedEma = other.testedEma;
    }

    // Returns the limit.
    public int limit() {
        return this.pinned != 0 ? this.pinned : this.limit;
    }

    // Returns the squared distance beyond which nothing is tested.
    public int testableLimit() {
        return this.testable;
    }

    // Returns the far sections reached.
    public int farReached() {
        return this.farReached;
    }

    // Returns the sections tested.
    public int tested() {
        return this.tested;
    }

    // Returns the sections culled.
    public int culled() {
        return this.culled;
    }

    // Returns the moving average of the yield.
    public float yield() {
        return this.yieldEma / (float) (1 << Q);
    }

    // Returns whether every reached section was tested.
    public boolean wasFullPass() {
        return this.tested == this.farReached;
    }

    // Returns whether the frame was a probe.
    public boolean wasProbe() {
        return this.probing;
    }

    // Returns the state as text.
    @Override
    public String toString() {
        return String.format("limit=%s%s yield=%.1f%% far=%d farthest=%d tested=%d culled=%d",
                this.limit() == UNBOUNDED ? "max" : Integer.toString(this.limit()), this.probing ? " (probe)" : "",
                this.yield() * 100.0f, this.farReached, this.farthest, this.tested, this.culled);
    }

    // Returns a percentage as a fixed point value.
    private static int percent(int value) {
        return (int) (((long) value << Q) / 100);
    }

    // Returns twice a value or the largest int.
    private static int saturatingDouble(int value) {
        return value >= (1 << 30) ? Integer.MAX_VALUE : value << 1;
    }
}
