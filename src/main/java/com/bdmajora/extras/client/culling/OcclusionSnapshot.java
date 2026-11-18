package com.bdmajora.extras.client.culling;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

// Targets and their test boxes gathered on the main thread for one worker pass.
public final class OcclusionSnapshot {
    // Kind of an entity target.
    public static final byte ENTITY = 0;
    // Kind of a block entity target.
    public static final byte BLOCK_ENTITY = 1;
    // Starting number of target slots.
    private static final int INITIAL_CAPACITY = 256;

    // Target of each slot.
    private Cullable[] targets = new Cullable[INITIAL_CAPACITY];
    // Kind of each slot.
    private byte[] kinds = new byte[INITIAL_CAPACITY];
    // Six box bounds of each slot in min then max order.
    private double[] boxes = new double[INITIAL_CAPACITY * 6];
    // Number of filled slots.
    private int count;
    // Set by the producer when published and cleared by the consumer when taken.
    private volatile boolean fresh;

    // Empties the snapshot and drops the target references.
    public void clear() {
        Arrays.fill(this.targets, 0, this.count, null);
        this.count = 0;
    }

    // Adds a target with its box.
    public void add(Cullable target, byte kind, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (this.count == this.targets.length) {
            int capacity = this.targets.length * 2;
            this.targets = Arrays.copyOf(this.targets, capacity);
            this.kinds = Arrays.copyOf(this.kinds, capacity);
            this.boxes = Arrays.copyOf(this.boxes, capacity * 6);
        }
        int slot = this.count++;
        this.targets[slot] = target;
        this.kinds[slot] = kind;
        int base = slot * 6;
        this.boxes[base] = minX;
        this.boxes[base + 1] = minY;
        this.boxes[base + 2] = minZ;
        this.boxes[base + 3] = maxX;
        this.boxes[base + 4] = maxY;
        this.boxes[base + 5] = maxZ;
    }

    // Returns the number of targets.
    public int count() {
        return this.count;
    }

    // Returns the target of a slot.
    public Cullable target(int slot) {
        return this.targets[slot];
    }

    // Returns the kind of a slot.
    public byte kind(int slot) {
        return this.kinds[slot];
    }

    // Returns one bound of a slot where index zero to five is min east to max south.
    public double bound(int slot, int index) {
        return this.boxes[slot * 6 + index];
    }

    // Returns whether the snapshot was published and not yet taken.
    public boolean isFresh() {
        return this.fresh;
    }

    // Hands snapshots from the main thread to the worker through three rotating buffers.
    public static final class Exchange {
        // Buffer that moves between the two threads.
        private final AtomicReference<OcclusionSnapshot> middle = new AtomicReference<>(new OcclusionSnapshot());
        // Buffer the main thread fills.
        private OcclusionSnapshot back = new OcclusionSnapshot();

        // Returns the buffer the main thread fills next.
        public OcclusionSnapshot producer() {
            return this.back;
        }

        // Publishes the filled buffer and takes a free one back.
        public void publish() {
            this.back.fresh = true;
            this.back = this.middle.getAndSet(this.back);
            this.back.clear();
        }

        // Returns the newest snapshot or the current one when nothing new was published.
        public OcclusionSnapshot take(OcclusionSnapshot current) {
            if (!this.middle.get().fresh) {
                return current;
            }
            OcclusionSnapshot spare = current == null ? new OcclusionSnapshot() : current;
            OcclusionSnapshot taken = this.middle.getAndSet(spare);
            taken.fresh = false;
            return taken;
        }
    }
}
