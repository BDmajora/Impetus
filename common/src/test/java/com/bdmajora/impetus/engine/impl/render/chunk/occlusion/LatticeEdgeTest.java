package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests construction and window argument checks of the lattice.
class LatticeEdgeTest {
    // Rejects an inverted height range and a range that is too tall.
    @Test
    void constructorRefusals() {
        assertThrows(IllegalArgumentException.class, () -> new SectionLattice(5, 4, false, false));
        assertThrows(IllegalStateException.class, () -> new SectionLattice(0, 5000, false, false));
        new SectionLattice(0, 0, false, false);
        new SectionLattice(-4, 19, true, true);
    }

    // Rejects a missing camera and a distance that is negative or not a number.
    @Test
    void windowRefusals() {
        SectionLattice lattice = new SectionLattice(0, 4, false, false);
        assertThrows(NullPointerException.class, () -> lattice.ensureWindowCovers(null, 64f));
        assertThrows(IllegalArgumentException.class, () -> lattice.ensureWindowCovers(new Vector3i(), -1f));
        assertThrows(IllegalArgumentException.class, () -> lattice.ensureWindowCovers(new Vector3i(), Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> lattice.ensureWindowCovers(new Vector3i(), Float.NEGATIVE_INFINITY));
        lattice.ensureWindowCovers(new Vector3i(1000, 2, -1000), 0f);
        lattice.ensureWindowCovers(new Vector3i(-1000, 2, 1000), 256f);
        assertNull(lattice.getRenderSection(0, 0, 0));
        assertFalse(lattice.updateSectionMetadata(0, 0, 0, 0L));
    }

    // Reports nothing visible before any search and for positions outside the window.
    @Test
    void emptySnapshot() {
        SectionLattice.VisibilitySnapshot empty = SectionLattice.VisibilitySnapshot.EMPTY;
        assertFalse(empty.isSectionVisible(0, 0, 0, Integer.MIN_VALUE));
        assertFalse(empty.isSectionVisible(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 0));
        assertThrows(IllegalStateException.class, () -> new SectionLattice(0, 4, false, false).findShadowVisible(null, null, 1f, 1, null, 0));
    }
}
