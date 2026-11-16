package com.bdmajora.fulgor.collections;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.NoSuchElementException;
import java.util.Objects;

// First in first out queue of longs in pooled segments that can skip values it has seen.
public final class DeduplicatedLongQueue {
    // Number of values in one segment.
    private static final int SEGMENT_SIZE = 1 << 10;

    // Largest seen set size that is cleared and kept on a reset.
    private static final int RETAINED_SET_CAPACITY = 1 << 15;

    // Pool the segments come from.
    private final Pool pool;
    // Capacity of a new seen set.
    private final int initialSetCapacity;

    // Deduplication state of the queue.
    private final boolean deduplicate;

    // Values queued since the last reset or null without deduplication.
    private LongOpenHashSet seen;

    // Segment read next or null when empty.
    private Segment head;
    // Segment written next or null when empty.
    private Segment tail;

    // Position of the next read in the head segment.
    private int headIndex;

    // Number of queued values.
    private int size;

    // Empty state readable from other threads.
    private volatile boolean empty = true;

    // Creates a queue on a pool and rejects a null pool.
    public DeduplicatedLongQueue(Pool pool, int initialCapacity, boolean deduplicate) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.initialSetCapacity = initialCapacity;
        this.deduplicate = deduplicate;
        this.seen = deduplicate ? new LongOpenHashSet(initialCapacity) : null;
    }

    // Appends a value and returns false when deduplication already saw it.
    public boolean enqueue(long value) {
        if (this.deduplicate && !this.seen.add(value)) {
            return false;
        }

        if (this.tail == null) {
            this.head = this.tail = this.pool.acquire();
            this.headIndex = 0;
            this.empty = false;
        } else if (this.tail.writeIndex == SEGMENT_SIZE) {
            Segment segment = this.pool.acquire();
            this.tail.next = segment;
            this.tail = segment;
        }

        this.tail.values[this.tail.writeIndex++] = value;
        this.size++;

        return true;
    }

    // Removes and returns the oldest value and rejects an empty queue.
    public long dequeue() {
        if (this.head == null) {
            throw new NoSuchElementException("The queue is empty");
        }

        long value = this.head.values[this.headIndex++];

        if (this.headIndex == this.head.writeIndex) {
            Segment next = this.head.next;

            this.pool.release(this.head);

            this.head = next;
            this.headIndex = 0;

            if (next == null) {
                this.tail = null;
                this.empty = true;
            }
        }

        this.size--;

        return value;
    }

    // Returns whether no value is queued.
    public boolean isEmpty() {
        return this.empty;
    }

    // Returns the number of queued values.
    public int size() {
        return this.size;
    }

    // Forgets the seen values and replaces a large seen set.
    public void resetDeduplication() {
        if (!this.deduplicate) {
            return;
        }

        if (this.seen.size() > RETAINED_SET_CAPACITY) {
            this.seen = new LongOpenHashSet(this.initialSetCapacity);
        } else {
            this.seen.clear();
        }
    }

    // Pool of free segments shared by queues of one thread.
    public static final class Pool {
        // Largest number of free segments kept.
        private static final int MAX_CACHED_SEGMENTS = 1 << 10;

        // First free segment or null.
        private Segment free;
        // Number of free segments.
        private int freeCount;

        // Returns a free segment or a new one.
        Segment acquire() {
            Segment segment = this.free;

            if (segment == null) {
                return new Segment();
            }

            this.free = segment.next;
            this.freeCount--;

            segment.next = null;
            segment.writeIndex = 0;

            return segment;
        }

        // Keeps a segment for reuse unless the pool is full.
        void release(Segment segment) {
            if (this.freeCount >= MAX_CACHED_SEGMENTS) {
                return;
            }

            segment.next = this.free;
            segment.writeIndex = 0;

            this.free = segment;
            this.freeCount++;
        }
    }

    // Fixed block of queued values.
    private static final class Segment {
        // Stored values.
        private final long[] values = new long[SEGMENT_SIZE];
        // Position of the next write.
        private int writeIndex;
        // Following segment or the next free segment.
        private Segment next;
    }
}
