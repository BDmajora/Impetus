package com.bdmajora.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Checks every primitive kind.
class StaticsTest {
    // One static of each kind.
    static final class Fields {
        static boolean flag;
        static byte smallInt;
        static short mediumInt;
        static char letter;
        static int number;
        static long wide;
        static float fraction;
        static double precise;
        static String text;
        static final String FIXED = String.valueOf("fixed");
    }

    @Test
    void everyPrimitiveKeepsItsValue() {
        Statics.set(Fields.class, "flag", true);
        Statics.set(Fields.class, "smallInt", (byte) -7);
        Statics.set(Fields.class, "mediumInt", (short) 30000);
        Statics.set(Fields.class, "letter", 'Q');
        Statics.set(Fields.class, "number", Integer.MIN_VALUE);
        Statics.set(Fields.class, "wide", Long.MAX_VALUE);
        Statics.set(Fields.class, "fraction", -2.5F);
        Statics.set(Fields.class, "precise", Math.PI);

        assertTrue(Fields.flag);
        assertEquals((byte) -7, Fields.smallInt);
        assertEquals((short) 30000, Fields.mediumInt);
        assertEquals('Q', Fields.letter);
        assertEquals(Integer.MIN_VALUE, Fields.number);
        assertEquals(Long.MAX_VALUE, Fields.wide);
        assertEquals(-2.5F, Fields.fraction);
        assertEquals(Math.PI, Fields.precise);
        assertEquals(-2.5F, Statics.<Float>get(Fields.class, "fraction"));
    }

    @Test
    void referencesAndFinalsAreWrittenToo() {
        Statics.set(Fields.class, "text", "swapped");
        assertEquals("swapped", Fields.text);
        String before = Statics.get(Fields.class, "FIXED");
        try {
            Statics.set(Fields.class, "FIXED", "changed");
            assertEquals("changed", Statics.get(Fields.class, "FIXED"));
        } finally {
            Statics.set(Fields.class, "FIXED", before);
        }
    }

    @Test
    void aMissingFieldOrWrongBoxFails() {
        assertThrows(AssertionError.class, () -> Statics.set(Fields.class, "absent", 1));
        // Checks a boxed int.
        Statics.set(Fields.class, "fraction", 1.5F);
        AssertionError wrongBox = assertThrows(AssertionError.class, () -> Statics.set(Fields.class, "fraction", 1));
        assertInstanceOf(ClassCastException.class, wrongBox.getCause());
        assertEquals(1.5F, Fields.fraction);
    }
}
