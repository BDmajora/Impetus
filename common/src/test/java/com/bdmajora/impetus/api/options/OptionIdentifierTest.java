package com.bdmajora.impetus.api.options;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionIdentifierTest {
    @Test
    void identifiersAreInterned() {
        OptionIdentifier<Void> a = OptionIdentifier.create("mod", "path");
        OptionIdentifier<Void> b = OptionIdentifier.create("mod", "path");
        assertSame(a, b);
        assertTrue(a.matches(b));
        assertFalse(a.matches(OptionIdentifier.create("mod", "other")));
        assertEquals("mod", a.getModId());
        assertEquals("path", a.getPath());
        assertEquals(void.class, a.getType());
        assertEquals("mod:path", a.toString());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, null);
        assertNotEquals(a, "mod:path");
        assertSame(a, a.<Integer>cast());
    }

    @Test
    void typeMismatchIsRejectedAndEmptyIsAbsent() {
        OptionIdentifier<Integer> typed = OptionIdentifier.create("mod", "typed", Integer.class);
        assertEquals(Integer.class, typed.getType());
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("mod", "typed", String.class));
        assertFalse(OptionIdentifier.isPresent(null));
        assertFalse(OptionIdentifier.isPresent(OptionIdentifier.EMPTY));
        assertTrue(OptionIdentifier.isPresent(typed));
    }
}
