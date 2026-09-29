package com.bdmajora.testing;

// Answers for spliced interface methods that are not plain accessors, so a test can drive both sides of the
// decisions production code makes through them
public final class SplicedAnswers {
    // Whether Block reports a position-aware luminance or opacity, which is what Fulgor's cached path turns on
    public static boolean positionAwareLightValue;
    public static boolean positionAwareOpacity;

    private SplicedAnswers() {}

    public static void reset() {
        positionAwareLightValue = false;
        positionAwareOpacity = false;
    }

    public static boolean fulgor$hasPositionAwareLightValue() {
        return positionAwareLightValue;
    }

    public static boolean fulgor$hasPositionAwareOpacity() {
        return positionAwareOpacity;
    }
}
