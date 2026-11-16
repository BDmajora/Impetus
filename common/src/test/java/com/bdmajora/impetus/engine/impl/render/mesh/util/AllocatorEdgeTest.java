package com.bdmajora.impetus.engine.impl.render.mesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests id and range allocation at their limits.
class AllocatorEdgeTest {
    // Reuses the smallest released id and shrinks from the tail.
    @Test
    void idsAreDense() {
        IdAllocator ids = new IdAllocator();
        assertEquals(0, ids.maxIndex());
        assertEquals(0, ids.allocate());
        assertEquals(1, ids.allocate());
        assertEquals(2, ids.allocate());
        ids.release(1);
        assertEquals(3, ids.maxIndex());
        assertEquals(1, ids.allocate());
        ids.release(2);
        assertEquals(2, ids.maxIndex());
        ids.release(1);
        ids.release(0);
        assertEquals(0, ids.maxIndex());
        assertEquals(0, ids.allocate());
    }

    // Rejects ids that were never handed out and ids released twice.
    @Test
    void idRefusals() {
        IdAllocator ids = new IdAllocator();
        assertThrows(IllegalArgumentException.class, () -> ids.release(0));
        ids.allocate();
        ids.allocate();
        assertThrows(IllegalArgumentException.class, () -> ids.release(-1));
        assertThrows(IllegalArgumentException.class, () -> ids.release(2));
        assertThrows(IllegalArgumentException.class, () -> ids.release(Integer.MAX_VALUE));
        ids.release(0);
        assertThrows(IllegalArgumentException.class, () -> ids.release(0));
        assertEquals(0, ids.allocate());
    }

    // Rejects sizes out of range and honours limits.
    @Test
    void rangeRefusals() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(0));
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(-1));
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(1 << 30));
        assertThrows(IllegalArgumentException.class, () -> allocator.alloc(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> allocator.setLimit(-1));
        allocator.setLimit(10);
        assertEquals(0, allocator.alloc(10));
        assertEquals(SegmentedAllocator.OUT_OF_SPACE, allocator.alloc(1));
        assertEquals(10, allocator.free(0));
        assertEquals(0, allocator.getTotalSize());
        assertEquals(SegmentedAllocator.OUT_OF_SPACE, new SegmentedAllocator() {{ setLimit(0); }}.alloc(1));
    }

    // Rejects freeing and sizing and expanding unknown addresses and bad growth.
    @Test
    void addressRefusals() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        assertThrows(IllegalStateException.class, () -> allocator.free(5));
        assertThrows(IllegalArgumentException.class, () -> allocator.getSize(5));
        assertFalse(allocator.expand(5, 1));
        long a = allocator.alloc(4);
        assertThrows(IllegalArgumentException.class, () -> allocator.expand(a, -1));
        assertThrows(IllegalArgumentException.class, () -> allocator.expand(a, 1 << 30));
        assertTrue(allocator.expand(a, 0));
        assertEquals(4, allocator.getSize(a));
        assertThrows(IllegalStateException.class, () -> allocator.free(a + 1));
        assertThrows(RuntimeException.class, () -> allocator.getSize(a + 1));
        assertEquals(4, allocator.free(a));
        assertThrows(IllegalStateException.class, () -> allocator.free(a));
    }

    // Keeps the address space bounded by the address bit count.
    @Test
    void addressSpaceCap() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        long big = (1L << 30) - 1;
        long last = 0;
        int count = 0;
        while (last != SegmentedAllocator.OUT_OF_SPACE && count < 40) {
            last = allocator.alloc((int) big);
            count++;
        }
        assertEquals(SegmentedAllocator.OUT_OF_SPACE, last);
        assertTrue(allocator.getTotalSize() <= (1L << 34));
    }

    // Rejects a bad arena configuration.
    @Test
    void arenaRefusals() {
        assertThrows(IllegalArgumentException.class, () -> new QuadArena(-1, 16));
        assertThrows(IllegalArgumentException.class, () -> new QuadArena(1024, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuadArena(1024, -4));
    }

    // Refuses to grow a block past the size field and keeps it unchanged.
    @Test
    void expandRefusesSizesPastTheField() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        int max = (1 << 30) - 1;
        long first = allocator.alloc(max);
        assertFalse(allocator.expand(first, 1));
        assertEquals(max, allocator.getSize(first));
        assertEquals(max, allocator.getTotalSize());
        assertTrue(allocator.expand(first, 0));
        long second = allocator.alloc(16);
        assertEquals(max, second);
        assertEquals(16, allocator.getSize(second));
        assertThrows(IllegalStateException.class, () -> allocator.expand(first + 1, 1));
        assertEquals(16, allocator.free(second));
        assertEquals(max, allocator.free(first));
        assertEquals(0, allocator.getTotalSize());
    }

    // Refuses to grow the last block past the address space.
    @Test
    void expandRefusesPastTheAddressSpace() {
        SegmentedAllocator allocator = new SegmentedAllocator();
        int max = (1 << 30) - 1;
        for (int i = 0; i < 16; i++) {
            assertEquals((long) i * max, allocator.alloc(max));
        }
        long remaining = (1L << 34) - allocator.getTotalSize();
        long tail = allocator.alloc((int) remaining);
        assertEquals((1L << 34), allocator.getTotalSize());
        assertFalse(allocator.expand(tail, 1));
        assertEquals(SegmentedAllocator.OUT_OF_SPACE, allocator.alloc(1));
    }
}
