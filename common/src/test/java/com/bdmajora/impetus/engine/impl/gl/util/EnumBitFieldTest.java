package com.bdmajora.impetus.engine.impl.gl.util;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests flag combination and membership.
class EnumBitFieldTest {
    // Flag enum used for the tests.
    private enum Flag implements EnumBit {
        A(1), B(2), C(0x80000000);

        // Bit mask of the flag.
        private final int bits;

        // Creates a flag.
        Flag(int bits) {
            this.bits = bits;
        }

        // Returns the bit mask of the flag.
        @Override
        public int getBits() {
            return this.bits;
        }
    }

    // Combines bits and answers membership.
    @Test
    void combinesBitsAndAnswersMembership() {
        EnumBitField<GlBufferMapFlags> field = EnumBitField.of(GlBufferMapFlags.READ, GlBufferMapFlags.WRITE);
        assertEquals(GlBufferMapFlags.READ.getBits() | GlBufferMapFlags.WRITE.getBits(), field.getBitField());
        assertTrue(field.contains(GlBufferMapFlags.READ));
        assertFalse(field.contains(GlBufferMapFlags.PERSISTENT));
    }

    // Rejects an empty field.
    @Test
    void emptyFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnumBitField.<Flag>of());
    }

    // Counts a repeated flag once.
    @Test
    void duplicatesAreIgnored() {
        EnumBitField<Flag> field = EnumBitField.of(Flag.A, Flag.A, Flag.B);
        assertEquals(3, field.getBitField());
        assertTrue(field.contains(Flag.B));
        assertFalse(field.contains(Flag.C));
        assertFalse(field.contains(null));
    }

    // Keeps the sign bit of a flag.
    @Test
    void signBitIsKept() {
        assertEquals(Integer.MIN_VALUE, EnumBitField.of(Flag.C).getBitField());
        assertEquals(Integer.MIN_VALUE | 3, EnumBitField.of(Flag.C, Flag.B, Flag.A).getBitField());
    }

    // Rejects null flags and a null array.
    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> EnumBitField.of(Flag.A, null));
        assertThrows(NullPointerException.class, () -> EnumBitField.of((Flag[]) null));
    }

    // Stores a single flag and its bits.
    @Test
    void singleFlag() {
        EnumBitField<Flag> field = EnumBitField.of(Flag.B);
        assertEquals(2, field.getBitField());
        assertTrue(field.contains(Flag.B));
        assertFalse(field.contains(Flag.A));
    }

    // Rejects a null first flag before reading the rest.
    @Test
    void nullFirstFlagIsRejected() {
        assertThrows(NullPointerException.class, () -> EnumBitField.of(null, Flag.A));
        assertThrows(NullPointerException.class, () -> EnumBitField.of((Flag) null));
    }

    // Combines a large repeated array into every flag once.
    @Test
    void largeRepeatedInput() {
        Flag[] values = new Flag[30_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = Flag.values()[i % 3];
        }
        EnumBitField<Flag> field = EnumBitField.of(values);
        assertEquals(Integer.MIN_VALUE | 3, field.getBitField());
        for (Flag flag : Flag.values()) {
            assertTrue(field.contains(flag));
        }
    }

    // Ignores later changes to the source array.
    @Test
    void sourceArrayIsNotRetained() {
        Flag[] values = {Flag.A};
        EnumBitField<Flag> field = EnumBitField.of(values);
        values[0] = Flag.C;
        assertTrue(field.contains(Flag.A));
        assertFalse(field.contains(Flag.C));
        assertEquals(1, field.getBitField());
    }
}
