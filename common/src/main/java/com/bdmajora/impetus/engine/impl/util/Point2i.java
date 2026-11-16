package com.bdmajora.impetus.engine.impl.util;

// Integer point in two dimensions.
public interface Point2i {
    // Point at the origin.
    Point2i ZERO = new Point2i() {
        // Returns zero.
        @Override
        public int x() {
            return 0;
        }

        // Returns zero.
        @Override
        public int y() {
            return 0;
        }
    };

    // Returns the horizontal coordinate.
    int x();

    // Returns the vertical coordinate.
    int y();
}
