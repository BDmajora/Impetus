package com.bdmajora.impetus.engine.impl.util.collections.quadtree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuadTreeTest {
    @Test
    void findsItemsByPointAcrossQuadrants() {
        Map<String, Rect2i> rects = Map.of(
                "tl", new Rect2i(0, 0, 4, 4),
                "br", new Rect2i(28, 28, 4, 4),
                "big", new Rect2i(10, 10, 12, 12));
        QuadTree<String> tree = new QuadTree<>(new Rect2i(0, 0, 32, 32), 4, rects.keySet(), rects::get);
        assertEquals("tl", tree.find(1, 1));
        assertEquals("br", tree.find(31, 31));
        assertEquals("big", tree.find(15, 15));
        assertNull(tree.find(5, 5));
        assertNull(tree.find(-1, 0));
        assertNull(tree.find(40, 40));
    }

    @Test
    void tallAndWideRootsSplitInTwo() {
        QuadTree<String> tall = new QuadTree<>(new Rect2i(0, 0, 8, 16), 2, List.of("a"), s -> new Rect2i(0, 12, 2, 2));
        assertEquals("a", tall.find(1, 13));
        assertNull(tall.find(1, 1));
        QuadTree<String> wide = new QuadTree<>(new Rect2i(0, 0, 16, 8), 2, List.of("b"), s -> new Rect2i(12, 0, 2, 2));
        assertEquals("b", wide.find(13, 1));
        assertNull(wide.find(1, 1));
    }

    @Test
    void emptyTreesFindNothing() {
        assertNull(QuadTree.<String>empty().find(0, 0));
        QuadTree<String> odd = new QuadTree<>(new Rect2i(0, 0, 7, 7), 1, List.of("x"), s -> new Rect2i(3, 3, 1, 1));
        assertEquals("x", odd.find(3, 3));
        QuadTree<String> none = new QuadTree<>(new Rect2i(0, 0, 8, 8), 1, List.of(), null);
        assertNull(none.find(3, 3));
    }

    @Test
    void rectContainmentAndEquality() {
        Rect2i r = new Rect2i(1, 2, 3, 4);
        assertTrue(r.contains(1, 2));
        assertTrue(r.contains(3, 5));
        assertFalse(r.contains(4, 2));
        assertFalse(r.contains(1, 6));
        assertTrue(r.contains(new Rect2i(1, 2, 3, 4)));
        assertFalse(r.contains(new Rect2i(1, 2, 4, 4)));
        assertEquals(r, new Rect2i(r));
        assertEquals(r.hashCode(), new Rect2i(1, 2, 3, 4).hashCode());
        assertNotEquals(r, new Rect2i(0, 2, 3, 4));
        assertNotEquals(r, null);
        assertNotEquals(r, "x");
        assertEquals("Rect2i[x=1, y=2, width=3, height=4]", r.toString());
        assertEquals(1, r.x());
        assertEquals(2, r.y());
        assertEquals(3, r.width());
        assertEquals(4, r.height());
    }
}
