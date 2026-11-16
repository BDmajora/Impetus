package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

// Directions between neighboring sections as numbers from zero to five.
public class GraphDirection {
    // Direction of negative y.
    public static final int DOWN    = 0;
    // Direction of positive y.
    public static final int UP      = 1;
    // Direction of negative z.
    public static final int NORTH   = 2;
    // Direction of positive z.
    public static final int SOUTH   = 3;
    // Direction of negative x.
    public static final int WEST    = 4;
    // Direction of positive x.
    public static final int EAST    = 5;

    // Number of directions.
    public static final int COUNT   = 6;

    // Letter of each direction.
    public static final char[] LETTERS = new char[] { 'D', 'U', 'N', 'S', 'W', 'E' };

    // Opposite of each direction.
    private static final int[] OPPOSITE;
    // Unit offset of each direction on each axis.
    private static final int[] X, Y, Z;

    static {
        OPPOSITE = new int[COUNT];
        OPPOSITE[DOWN] = UP;
        OPPOSITE[UP] = DOWN;
        OPPOSITE[NORTH] = SOUTH;
        OPPOSITE[SOUTH] = NORTH;
        OPPOSITE[WEST] = EAST;
        OPPOSITE[EAST] = WEST;

        X = new int[COUNT];
        X[WEST] = -1;
        X[EAST] = 1;

        Y = new int[COUNT];
        Y[DOWN] = -1;
        Y[UP] = 1;

        Z = new int[COUNT];
        Z[NORTH] = -1;
        Z[SOUTH] = 1;
    }

    // Returns the opposite direction.
    public static int opposite(int direction) {
        return OPPOSITE[direction];
    }

    // Returns the x step of a direction.
    public static int x(int direction) {
        return X[direction];
    }

    // Returns the y step of a direction.
    public static int y(int direction) {
        return Y[direction];
    }

    // Returns the z step of a direction.
    public static int z(int direction) {
        return Z[direction];
    }
}