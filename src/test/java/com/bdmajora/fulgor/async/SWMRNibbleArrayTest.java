package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.NibbleArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SWMRNibbleArrayTest {
    @Test
    void theEmptyStatesDescribeWhatTheSectionHolds() {
        SWMRNibbleArray uninitialised = new SWMRNibbleArray();
        assertTrue(uninitialised.isUninitialisedUpdating());
        assertTrue(uninitialised.isUninitialisedVisible());
        assertFalse(uninitialised.isNullNibbleUpdating());
        assertTrue(uninitialised.isZeroUpdating());
        assertFalse(uninitialised.isFullUpdating());
        assertFalse(uninitialised.isDirty());
        assertEquals(0, uninitialised.getUpdating(0, 0, 0));
        assertEquals(0, uninitialised.getVisible(0, 0, 0));
        assertNull(uninitialised.getVisibleData());

        SWMRNibbleArray nothing = new SWMRNibbleArray(null, true);
        assertTrue(nothing.isNullNibbleUpdating());
        assertTrue(nothing.isNullNibbleVisible());
        assertFalse(nothing.isZeroUpdating());

        byte[] data = new byte[SWMRNibbleArray.ARRAY_SIZE];
        SWMRNibbleArray initialised = new SWMRNibbleArray(data);
        assertTrue(initialised.isInitialisedUpdating());
        assertTrue(initialised.isInitialisedVisible());
        assertSame(data, initialised.getVisibleData());
        assertThrows(IllegalArgumentException.class, () -> new SWMRNibbleArray(new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> new SWMRNibbleArray(new byte[3], SWMRNibbleArray.INIT_STATE_INIT));
        assertThrows(IllegalArgumentException.class, () -> new SWMRNibbleArray(null, SWMRNibbleArray.INIT_STATE_INIT));
        SWMRNibbleArray hidden = new SWMRNibbleArray(data.clone(), SWMRNibbleArray.INIT_STATE_HIDDEN);
        assertTrue(hidden.isHiddenUpdating());
    }

    @Test
    void writesGoToTheUpdatingCopyUntilTheyArePublished() {
        SWMRNibbleArray nibble = new SWMRNibbleArray();
        nibble.set(1, 2, 3, 9);
        assertEquals(9, nibble.getUpdating(1, 2, 3));
        // Nothing is visible to another thread until the copy is swapped in
        assertEquals(0, nibble.getVisible(1, 2, 3));
        assertTrue(nibble.isDirty());
        assertTrue(nibble.updateVisible());
        assertEquals(9, nibble.getVisible(1, 2, 3));
        assertFalse(nibble.isDirty());
        assertFalse(nibble.updateVisible());
        // A second write reuses the same storage and publishes again
        nibble.set(1, 2, 3, 4);
        assertTrue(nibble.updateVisible());
        assertEquals(4, nibble.getVisible(1, 2, 3));
        // Indices and coordinates address the same nibble
        nibble.set((5 & 15) | ((6 & 15) << 4) | ((7 & 15) << 8), 3);
        assertEquals(3, nibble.getUpdating(5, 7, 6));

        // Whole-section shortcuts
        nibble.setFull();
        assertTrue(nibble.isFullUpdating());
        assertFalse(nibble.isZeroUpdating());
        assertEquals(15, nibble.getUpdating(0, 0, 0));
        nibble.updateVisible();
        assertTrue(nibble.isFullVisible());
        nibble.setZero();
        assertTrue(nibble.isZeroUpdating());
        assertEquals(0, nibble.getUpdating(0, 0, 0));
        nibble.updateVisible();
        assertTrue(nibble.isZeroVisible());
        // Writing into a full section clears both shortcuts
        nibble.setFull();
        nibble.set(0, 0, 0, 1);
        assertFalse(nibble.isFullUpdating());
        assertFalse(nibble.isZeroUpdating());
    }

    @Test
    void stateTransitionsFreeAndReallocateTheStorage() {
        SWMRNibbleArray nibble = new SWMRNibbleArray();
        nibble.setNonNull();
        assertTrue(nibble.isUninitialisedUpdating());
        nibble.setNull();
        assertTrue(nibble.isNullNibbleUpdating());
        nibble.setNonNull();
        assertTrue(nibble.isUninitialisedUpdating());
        nibble.set(0, 0, 0, 5);
        nibble.setHidden();
        assertTrue(nibble.isHiddenUpdating());
        nibble.setHidden();
        assertTrue(nibble.isHiddenUpdating());
        // A hidden section keeps its data through a write and comes back with setNonNull
        nibble.set(0, 0, 0, 6);
        assertTrue(nibble.isHiddenUpdating());
        nibble.setFull();
        assertTrue(nibble.isHiddenUpdating());
        nibble.setZero();
        assertTrue(nibble.isHiddenUpdating());
        nibble.setNonNull();
        assertTrue(nibble.isInitialisedUpdating());
        assertTrue(nibble.updateVisible());
        // Hiding a section that holds nothing drops it entirely
        SWMRNibbleArray empty = new SWMRNibbleArray();
        empty.setHidden();
        assertTrue(empty.isNullNibbleUpdating());
        nibble.setUninitialised();
        assertTrue(nibble.isUninitialisedUpdating());
        assertTrue(nibble.updateVisible());
        assertNull(nibble.getVisibleData());
    }

    @Test
    void savingCollapsesEmptySections() {
        assertNull(new SWMRNibbleArray(null, true).getSaveState());
        SWMRNibbleArray uninitialised = new SWMRNibbleArray();
        SWMRNibbleArray.SaveState blank = uninitialised.getSaveState();
        assertNull(blank.data);
        assertEquals(SWMRNibbleArray.INIT_STATE_UNINIT, blank.state);

        SWMRNibbleArray lit = new SWMRNibbleArray();
        lit.set(2, 3, 4, 12);
        lit.updateVisible();
        SWMRNibbleArray.SaveState saved = lit.getSaveState();
        assertEquals(SWMRNibbleArray.INIT_STATE_INIT, saved.state);
        assertNotNull(saved.data);
        assertNotSame(lit.getVisibleData(), saved.data);
        // An initialised but all-zero section saves as uninitialised rather than 2 KB of zeros
        lit.setZero();
        lit.updateVisible();
        SWMRNibbleArray.SaveState zeroed = lit.getSaveState();
        assertNull(zeroed.data);
        assertEquals(SWMRNibbleArray.INIT_STATE_UNINIT, zeroed.state);
        // A hidden all-zero section is not saved at all
        SWMRNibbleArray hidden = new SWMRNibbleArray(new byte[SWMRNibbleArray.ARRAY_SIZE], SWMRNibbleArray.INIT_STATE_HIDDEN);
        assertNull(hidden.getSaveState());
    }

    @Test
    void vanillaArraysAreCopiedInAndSkylightIsExtrudedDown() {
        assertTrue(SWMRNibbleArray.fromVanilla(null).isNullNibbleUpdating());
        NibbleArray vanilla = new NibbleArray();
        vanilla.set(1, 2, 3, 11);
        SWMRNibbleArray copied = SWMRNibbleArray.fromVanilla(vanilla);
        assertEquals(11, copied.getUpdating(1, 2, 3));
        assertNotSame(vanilla.getData(), copied.getVisibleData());

        // Extruding copies the bottom layer of the section above through every layer below
        SWMRNibbleArray above = new SWMRNibbleArray();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                above.set(x, 0, z, 15);
            }
        }
        SWMRNibbleArray below = new SWMRNibbleArray();
        below.extrudeLower(above);
        assertEquals(15, below.getUpdating(0, 0, 0));
        assertEquals(15, below.getUpdating(15, 15, 15));
        assertTrue(below.isInitialisedUpdating());
        // Extruding again writes into the storage it already owns
        below.extrudeLower(above);
        assertEquals(15, below.getUpdating(8, 8, 8));
        // An empty section above leaves the one below uninitialised, and a null one is refused
        SWMRNibbleArray emptyAbove = new SWMRNibbleArray();
        SWMRNibbleArray target = new SWMRNibbleArray();
        target.extrudeLower(emptyAbove);
        assertTrue(target.isUninitialisedUpdating());
        SWMRNibbleArray nothing = new SWMRNibbleArray(null, true);
        assertThrows(IllegalArgumentException.class, () -> target.extrudeLower(nothing));
    }
}
