package com.bdmajora.impetus.engine.impl.render.mesh.util;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;

// Best fit allocator of address ranges with merging of free space.
public class SegmentedAllocator {
    // Result of an allocation that does not fit.
    public static final long OUT_OF_SPACE = -1L;

    // Bits of an address.
    private static final int ADDR_BITS = 34;
    // Bits of a size.
    private static final int SIZE_BITS = 64 - ADDR_BITS;
    // Mask of a size.
    private static final long SIZE_MASK = (1L << SIZE_BITS) - 1;
    // Mask of an address.
    private static final long ADDR_MASK = (1L << ADDR_BITS) - 1;

    // Free blocks ordered by size and address.
    private final LongRBTreeSet free = new LongRBTreeSet();
    // Allocated blocks ordered by address.
    private final LongRBTreeSet taken = new LongRBTreeSet();

    // Largest total size.
    private long sizeLimit = Long.MAX_VALUE;
    // Highest address in use.
    private long totalSize;

    // Resize state of the last call.
    private boolean resized;

    // Stores the largest total size and rejects a negative size.
    public void setLimit(long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Negative limit " + limit);
        }
        this.sizeLimit = limit;
    }

    // Returns the highest address in use.
    public long getTotalSize() {
        return this.totalSize;
    }

    // Returns whether the last call changed the total size.
    public boolean didResize() {
        return this.resized;
    }

    // Returns the address of a new block or the out of space marker and rejects a size out of range.
    public long alloc(int size) {
        if (size <= 0 || size > SIZE_MASK) {
            throw new IllegalArgumentException("Allocation size out of range " + size);
        }

        LongBidirectionalIterator iterator = this.free.iterator(((long) size << ADDR_BITS) - 1);

        if (!iterator.hasNext()) {
            this.resized = true;

            if (this.totalSize + size > Math.min(this.sizeLimit, ADDR_MASK + 1)) {
                return OUT_OF_SPACE;
            }

            long address = this.totalSize;
            this.totalSize += size;
            this.taken.add((address << SIZE_BITS) | size);
            return address;
        }

        long slot = iterator.nextLong();
        iterator.remove();

        long slotSize = slot >>> ADDR_BITS;
        long slotAddress = slot & ADDR_MASK;

        if (slotSize == size) {
            this.taken.add((slotAddress << SIZE_BITS) | slotSize);
        } else {
            this.taken.add((slotAddress << SIZE_BITS) | size);
            this.free.add(((slotSize - size) << ADDR_BITS) | (slotAddress + size));
        }

        this.resized = false;
        return slotAddress;
    }

    // Releases a block and returns its size and rejects an address that was not allocated.
    public int free(long address) {
        address &= ADDR_MASK;

        LongBidirectionalIterator iterator = this.taken.iterator(address << SIZE_BITS);
        if (!iterator.hasNext()) {
            throw new IllegalStateException("Freeing an address that was never allocated: " + address);
        }
        long slot = iterator.nextLong();

        if (slot >>> SIZE_BITS != address) {
            throw new IllegalStateException("Freeing an address that was never allocated: " + address);
        }

        long size = slot & SIZE_MASK;
        iterator.remove();

        if (iterator.hasPrevious()) {
            long previous = iterator.previousLong();
            long previousEnd = (previous >>> SIZE_BITS) + (previous & SIZE_MASK);

            if (previousEnd != address) {
                long gap = address - previousEnd;
                this.free.remove((gap << ADDR_BITS) | previousEnd);
                slot = (previousEnd << SIZE_BITS) | ((slot & SIZE_MASK) + gap);
            }

            iterator.nextLong();
        } else if (!this.free.isEmpty()) {
            if (this.free.remove(address << ADDR_BITS)) {
                slot = address + size;
            }
        }

        if (iterator.hasNext()) {
            long next = iterator.nextLong();
            long end = (slot >>> SIZE_BITS) + (slot & SIZE_MASK);

            if (end != next >>> SIZE_BITS) {
                long gap = (next >>> SIZE_BITS) - end;
                this.free.remove((gap << ADDR_BITS) | end);
                slot = (slot & (ADDR_MASK << SIZE_BITS)) | ((slot & SIZE_MASK) + gap);
            }
        } else {
            this.resized = true;
            this.totalSize -= slot & SIZE_MASK;
            return (int) size;
        }

        this.resized = false;
        this.free.add((slot >>> SIZE_BITS) | (slot << ADDR_BITS));
        return (int) size;
    }

    // Grows a block in place and returns whether it fit and rejects an extra size out of range or an address that was never allocated.
    public boolean expand(long address, int extra) {
        if (extra < 0 || extra > SIZE_MASK) {
            throw new IllegalArgumentException("Extra size out of range " + extra);
        }
        address &= ADDR_MASK;

        LongBidirectionalIterator iterator = this.taken.iterator(address << SIZE_BITS);

        if (!iterator.hasNext()) {
            return false;
        }

        long slot = iterator.nextLong();

        if (slot >>> SIZE_BITS != address) {
            throw new IllegalStateException("Expanding an address that was never allocated: " + address);
        }

        this.resized = false;
        // Refuses a size that does not fit the size field
        if ((slot & SIZE_MASK) + extra > SIZE_MASK) {
            return false;
        }
        long grownSlot = (slot & (ADDR_MASK << SIZE_BITS)) | ((slot & SIZE_MASK) + extra);

        if (!iterator.hasNext()) {
            if (this.totalSize + extra > Math.min(this.sizeLimit, ADDR_MASK + 1)) {
                return false;
            }

            iterator.remove();
            this.taken.add(grownSlot);
            this.totalSize += extra;
            this.resized = true;
            return true;
        }

        long next = iterator.nextLong();
        long end = (slot >>> SIZE_BITS) + (slot & SIZE_MASK);
        long gap = (next >>> SIZE_BITS) - end;

        if (extra > gap) {
            return false;
        }

        this.free.remove((gap << ADDR_BITS) | end);
        iterator.previousLong();
        iterator.previousLong();
        iterator.remove();
        this.taken.add(grownSlot);

        if (extra != gap) {
            this.free.add(((gap - extra) << ADDR_BITS) | (end + extra));
        }

        return true;
    }

    // Returns the size of an allocated block.
    public long getSize(long address) {
        address &= ADDR_MASK;

        LongBidirectionalIterator iterator = this.taken.iterator(address << SIZE_BITS);

        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("No allocation at address " + address);
        }

        long slot = iterator.nextLong();

        if (slot >>> SIZE_BITS != address) {
            throw new IllegalStateException("No allocation at address " + address);
        }

        return slot & SIZE_MASK;
    }
}
