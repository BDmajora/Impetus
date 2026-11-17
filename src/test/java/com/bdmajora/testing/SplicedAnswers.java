package com.bdmajora.testing;

// Answers for spliced interface methods that are not plain accessors.
public final class SplicedAnswers {
    // Whether Block reports a position aware light value.
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
