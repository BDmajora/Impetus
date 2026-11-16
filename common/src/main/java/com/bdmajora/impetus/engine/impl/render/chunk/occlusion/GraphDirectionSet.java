package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

// Sets of directions stored as bit masks.
public class GraphDirectionSet {
    // Set without directions.
    public static final int NONE    = 0;
    // Set of every direction.
    public static final int ALL     = (1 << GraphDirection.COUNT) - 1;

    // Returns the set of one direction and rejects a number that is not a direction.
    public static int of(int direction) {
        if (direction < 0 || direction >= GraphDirection.COUNT) {
            throw new IllegalArgumentException("Not a direction " + direction);
        }
        return 1 << direction;
    }

    // Returns whether the set holds a direction.
    public static boolean contains(int set, int direction) {
        return (set & GraphDirectionSet.of(direction)) != 0;
    }
}
