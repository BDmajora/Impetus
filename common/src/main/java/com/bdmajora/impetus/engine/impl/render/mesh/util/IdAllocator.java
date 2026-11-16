package com.bdmajora.impetus.engine.impl.render.mesh.util;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

// Hands out the smallest ids and shrinks its range when the last ids are released.
public class IdAllocator {
    // Released ids.
    private final IntSortedSet released = new IntAVLTreeSet();
    // Smallest id that was never handed out.
    private int next;

    // Returns the smallest released id or a new id and rejects an overflow.
    public int allocate() {
        if (this.released.isEmpty()) {
            if (this.next == Integer.MAX_VALUE) {
                throw new IllegalStateException("No ids left");
            }
            return this.next++;
        }

        int id = this.released.firstInt();
        this.released.remove(id);
        return id;
    }

    // Returns an id and rejects an id that is not allocated.
    public void release(int id) {
        if (id < 0 || id >= this.next) {
            throw new IllegalArgumentException("Id was not allocated " + id);
        }
        if (!this.released.add(id)) {
            throw new IllegalArgumentException("Id was released already " + id);
        }

        while (!this.released.isEmpty() && this.released.lastInt() + 1 == this.next) {
            this.released.remove(--this.next);
        }
    }

    // Returns one past the largest id in use.
    public int maxIndex() {
        return this.next;
    }
}
