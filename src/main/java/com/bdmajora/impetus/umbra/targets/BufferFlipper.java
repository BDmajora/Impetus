package com.bdmajora.impetus.umbra.targets;

import java.util.BitSet;

// Tracker of which render targets are flipped.
public class BufferFlipper {
    // Targets that are flipped.
    private final BitSet flippedBuffers = new BitSet();

    // Toggles the flip of a target.
    public void flip(int target) {
        this.flippedBuffers.flip(target);
    }

    // Returns whether a target is flipped.
    public boolean isFlipped(int target) {
        return this.flippedBuffers.get(target);
    }

    // Returns a copy of the flips.
    public BitSet snapshot() {
        return (BitSet) this.flippedBuffers.clone();
    }

    // Clears every flip.
    public void reset() {
        this.flippedBuffers.clear();
    }
}
