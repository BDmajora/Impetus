package com.bdmajora.impetus.engine.impl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Dim2iTest {
    private final Dim2i dim = new Dim2i(10, 20, 30, 40);

    @Test
    void limitsAndCentres() {
        assertEquals(40, dim.getLimitX());
        assertEquals(60, dim.getLimitY());
        assertEquals(25, dim.getCenterX());
        assertEquals(40, dim.getCenterY());
    }

    @Test
    void cursorContainmentIsHalfOpen() {
        assertTrue(dim.containsCursor(10, 20));
        assertTrue(dim.containsCursor(39.9, 59.9));
        assertFalse(dim.containsCursor(40, 20));
        assertFalse(dim.containsCursor(10, 60));
        assertFalse(dim.containsCursor(9, 20));
        assertFalse(dim.containsCursor(10, 19));
    }

    @Test
    void withersCopyOneField() {
        assertEquals(new Dim2i(10, 20, 30, 5), dim.withHeight(5));
        assertEquals(new Dim2i(10, 20, 5, 40), dim.withWidth(5));
        assertEquals(new Dim2i(5, 20, 30, 40), dim.withX(5));
        assertEquals(new Dim2i(10, 5, 30, 40), dim.withY(5));
        assertEquals(new Dim2i(11, 22, 30, 40), dim.withParentOffset(new Dim2i(1, 2, 0, 0)));
    }

    @Test
    void fitAndOverlap() {
        assertTrue(dim.canFitDimension(new Dim2i(10, 20, 30, 40)));
        assertTrue(dim.canFitDimension(new Dim2i(12, 22, 5, 5)));
        assertFalse(dim.canFitDimension(new Dim2i(9, 20, 30, 40)));
        assertFalse(dim.canFitDimension(new Dim2i(10, 19, 30, 40)));
        assertFalse(dim.canFitDimension(new Dim2i(10, 20, 31, 40)));
        assertFalse(dim.canFitDimension(new Dim2i(10, 20, 30, 41)));
        assertTrue(dim.overlapsWith(new Dim2i(39, 59, 5, 5)));
        assertFalse(dim.overlapsWith(new Dim2i(40, 20, 5, 5)));
        assertFalse(dim.overlapsWith(new Dim2i(0, 20, 10, 5)));
        assertFalse(dim.overlapsWith(new Dim2i(10, 60, 5, 5)));
        assertFalse(dim.overlapsWith(new Dim2i(10, 0, 5, 20)));
    }

    @Test
    void zeroPointIsOrigin() {
        assertEquals(0, Point2i.ZERO.x());
        assertEquals(0, Point2i.ZERO.y());
        assertEquals(10, dim.x());
        assertEquals(20, dim.y());
        assertEquals(30, dim.width());
        assertEquals(40, dim.height());
        assertEquals(dim.hashCode(), new Dim2i(10, 20, 30, 40).hashCode());
        assertTrue(dim.toString().contains("10"));
    }
}
