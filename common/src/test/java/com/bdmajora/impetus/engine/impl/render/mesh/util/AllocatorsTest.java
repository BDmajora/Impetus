package com.bdmajora.impetus.engine.impl.render.mesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllocatorsTest {
    @Test
    void idAllocatorReusesAndShrinksTheTail() {
        IdAllocator ids = new IdAllocator();
        assertEquals(0, ids.allocate());
        assertEquals(1, ids.allocate());
        assertEquals(2, ids.allocate());
        assertEquals(3, ids.maxIndex());
        ids.release(1);
        assertEquals(3, ids.maxIndex());
        assertEquals(1, ids.allocate());
        ids.release(2);
        assertEquals(2, ids.maxIndex());
        ids.release(0);
        ids.release(1);
        assertEquals(0, ids.maxIndex());
        assertEquals(0, ids.allocate());
    }

    @Test
    void segmentedAllocatorFirstFitsCoalescesAndGrowsInPlace() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(0));
        long a = allocator.alloc(10);
        long b = allocator.alloc(20);
        long c = allocator.alloc(30);
        assertEquals(0, a);
        assertEquals(10, b);
        assertEquals(30, c);
        assertEquals(60, allocator.getTotalSize());
        assertTrue(allocator.didResize());
        assertEquals(20, allocator.getSize(b));
        assertThrows(IllegalArgumentException.class, () -> allocator.getSize(999));
        assertThrows(IllegalStateException.class, () -> allocator.getSize(5));

        // Freeing the middle leaves a hole that the next fitting request reuses, split in two
        assertEquals(20, allocator.free(b));
        assertFalse(allocator.didResize());
        long d = allocator.alloc(5);
        assertEquals(10, d);
        assertFalse(allocator.didResize());
        long e = allocator.alloc(15);
        assertEquals(15, e);
        assertEquals(60, allocator.getTotalSize());

        // Freeing the tail shrinks the space; freeing neighbours merges holes on both sides
        allocator.free(c);
        assertEquals(30, allocator.getTotalSize());
        allocator.free(d);
        allocator.free(a);
        allocator.free(e);
        assertEquals(0, allocator.getTotalSize());
        assertThrows(java.util.NoSuchElementException.class, () -> allocator.free(0));

        long x = allocator.alloc(10);
        long y0 = allocator.alloc(10);
        assertThrows(IllegalStateException.class, () -> allocator.free(5));
        allocator.free(y0);
        long y = allocator.alloc(10);
        long z = allocator.alloc(10);
        allocator.free(y);
        assertTrue(allocator.expand(x, 5));
        assertEquals(15, allocator.getSize(x));
        assertFalse(allocator.expand(x, 6));
        assertTrue(allocator.expand(x, 5));
        assertFalse(allocator.expand(x, 1));
        assertTrue(allocator.expand(z, 10));
        assertTrue(allocator.didResize());
        assertEquals(40, allocator.getTotalSize());
        assertFalse(allocator.expand(999, 1));
        assertThrows(IllegalStateException.class, () -> allocator.expand(5, 1));
        allocator.free(x);
        allocator.free(z);
        assertEquals(0, allocator.getTotalSize());

        allocator.setLimit(25);
        long p = allocator.alloc(20);
        assertEquals(SegmentedAllocator.OUT_OF_SPACE, allocator.alloc(10));
        assertFalse(allocator.expand(p, 10));
        long q = allocator.alloc(5);
        allocator.free(p);
        // The freed 20 at address 0 is first-fit split, so the new allocation lands at the front of it
        assertEquals(0, allocator.alloc(5));
        allocator.free(q);
        assertEquals(5, allocator.getTotalSize());
        long r = allocator.alloc(3);
        allocator.free(r);
        assertEquals(5, allocator.getTotalSize());
        long s = allocator.alloc(2);
        long t = allocator.alloc(3);
        allocator.free(t);
        allocator.free(s);
        assertEquals(5, allocator.getTotalSize());
        allocator.free(0);
        assertEquals(0, allocator.getTotalSize());

        // Freeing from the front leaves a hole at address 0 that later frees absorb
        allocator.setLimit(Long.MAX_VALUE);
        long a0 = allocator.alloc(10);
        long b0 = allocator.alloc(10);
        long c0 = allocator.alloc(10);
        allocator.free(a0);
        allocator.free(b0);
        assertEquals(30, allocator.getTotalSize());
        allocator.free(c0);
        assertEquals(0, allocator.getTotalSize());
    }
}
