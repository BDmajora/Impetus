package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

// Clips angular intervals of the view frustum with lane wise integer arithmetic.
final class FastFrustumClamping {
    // Bits of a table coordinate.
    private static final int TABLE_SHIFT = 5;
    // Entries per table axis.
    private static final int TABLE_SIZE = 1 << TABLE_SHIFT;
    // Bits of a rank.
    private static final int RANK_BITS = 9;
    // Mask of a rank.
    private static final int RANK_MASK = (1 << RANK_BITS) - 1;
    // Bits of a lane.
    private static final int LANE_BITS = 10;
    // Number of lanes.
    private static final int LANES = 6;
    // Number of lanes of lower ranks.
    private static final int LOW_LANES = 3;

    // One bit at the start of each lane.
    private static final long LANE_ONES = laneOnes(LANES);
    // Spare bit of each lane.
    private static final long SPARE = LANE_ONES << RANK_BITS;
    // Rank bits of each lane.
    private static final long RANKS = LANE_ONES * RANK_MASK;

    // One bit at the start of each lower lane.
    private static final long LOW_LANE_ONES = laneOnes(LOW_LANES);
    // Spare bit of each lower lane.
    private static final long LOW_SPARE = LOW_LANE_ONES << RANK_BITS;
    // Mask of the lower lanes.
    private static final long LOW_MASK = (1L << (LOW_LANES * LANE_BITS)) - 1;

    // Quarter turn in radians.
    private static final double HALF_PI = Math.PI / 2.0;

    // Bounds of every cell of the table.
    private static final long[] BOUNDS = new long[TABLE_SIZE * TABLE_SIZE];

    // Aperture without any view.
    static final long EMPTY = -1L;
    // Aperture that allows every view.
    static final long FULL = RANKS;

    static {
        for (int x = 0; x < TABLE_SIZE; x++) {
            for (int y = 0; y < TABLE_SIZE; y++) {
                int lower = rankDown(angle(x + 1, y - 1));
                int upper = rankUp(angle(x - 1, y + 1));

                BOUNDS[index(x, y)] = (RANK_MASK - lower) | ((long) upper << (LOW_LANES * LANE_BITS));
            }
        }
    }

    // Returns the shift that brings coordinates up to a maximum into the table.
    static int normalizationShift(int maxCoordinate) {
        return Math.max(0, (Integer.SIZE - Integer.numberOfLeadingZeros(maxCoordinate)) - TABLE_SHIFT);
    }

    // Intersects an aperture with the bounds of a cell and returns the empty aperture when an interval closed.
    static long clip(long aperture, int x, int y, int z) {
        // Combines the three table entries
        long bounds = BOUNDS[index(x, y)]
                | (BOUNDS[index(x, z)] << LANE_BITS)
                | (BOUNDS[index(y, z)] << (2 * LANE_BITS));

        long result = min(aperture, bounds);

        return (overlap(result) & LOW_SPARE) == LOW_SPARE ? result : EMPTY;
    }

    // Returns the lane wise maximum of two apertures.
    static long hull(long left, long right) {
        long m = geMask(left, right);

        return (left & m) | (right & ~m);
    }

    // Returns the lane wise minimum of two apertures.
    private static long min(long left, long right) {
        long m = geMask(left, right);

        return (right & m) | (left & ~m);
    }

    // Returns a mask that is set in the lanes where the left value is not smaller.
    private static long geMask(long left, long right) {
        // Compares every lane by subtraction
        long flags = ((left | SPARE) - right) & SPARE;

        // Spreads each flag over its lane
        return (SPARE - (flags >>> RANK_BITS)) & RANKS;
    }

    // Returns a value whose spare bits are set for the intervals that are not empty.
    private static long overlap(long aperture) {
        // Reads the complemented lower ranks
        long lowers = aperture & LOW_MASK;
        // Reads the upper ranks
        long uppers = (aperture >>> (LOW_LANES * LANE_BITS)) & LOW_MASK;

        // Fills the spare bit when the lower rank does not pass the upper rank
        return lowers + uppers + LOW_LANE_ONES;
    }

    // Returns one bit at the start of each of the first lanes.
    private static long laneOnes(int count) {
        long ones = 0L;
        for (int i = 0; i < count; i++) {
            ones |= 1L << (i * LANE_BITS);
        }
        return ones;
    }

    // Returns the angle of a cell corner within the first quadrant.
    private static double angle(int run, int rise) {
        return Math.max(0.0, Math.min(HALF_PI, Math.atan2(rise, run)));
    }

    // Returns the rank of a lower angle rounded down.
    private static int rankDown(double angle) {
        return Math.max(0, Math.min(RANK_MASK, (int) Math.floor(angle * RANK_MASK / HALF_PI)));
    }

    // Returns the rank of an upper angle rounded up.
    private static int rankUp(double angle) {
        return Math.max(0, Math.min(RANK_MASK, (int) Math.ceil(angle * RANK_MASK / HALF_PI)));
    }

    // Returns the table index of a coordinate pair.
    private static int index(int x, int y) {
        return ((x << TABLE_SHIFT) | y) & (TABLE_SIZE * TABLE_SIZE - 1);
    }
}
