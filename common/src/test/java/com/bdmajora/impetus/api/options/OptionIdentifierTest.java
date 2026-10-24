package com.bdmajora.impetus.api.options;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void internAnswersAPairWhateverItsTypeAndMakesANewOneUntyped() {
        // A pair nobody made yet comes back untyped and is the one create hands out after
        OptionIdentifier<?> made = OptionIdentifier.intern("intern_mod", "new");
        assertSame(void.class, made.getType());
        assertEquals("intern_mod:new", made.toString());
        assertSame(made, OptionIdentifier.create("intern_mod", "new"));
        assertSame(made, OptionIdentifier.create("intern_mod", "new", void.class));
        assertSame(made, OptionIdentifier.intern("intern_mod", "new"));
        // ...and still refuses a typed create, as one made by create would
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("intern_mod", "new", Integer.class));
        assertSame(void.class, made.getType());
        // A typed pair is answered as it is, where an untyped create refuses it
        OptionIdentifier<Integer> typed = OptionIdentifier.create("intern_mod", "typed", Integer.class);
        assertSame(typed, OptionIdentifier.intern("intern_mod", "typed"));
        assertSame(Integer.class, typed.getType());
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("intern_mod", "typed"));
        assertSame(typed, OptionIdentifier.intern("intern_mod", "typed"));
        // The empty sentinel is a pair like any other, so it is never duplicated
        assertSame(OptionIdentifier.EMPTY, OptionIdentifier.intern("", ""));
        assertSame(Void.class, OptionIdentifier.EMPTY.getType());
        // Case and the mod id both tell pairs apart
        assertNotSame(made, OptionIdentifier.intern("intern_mod", "New"));
        assertNotSame(made, OptionIdentifier.intern("intern_mod_", "new"));
    }

    @Test
    void theHashIsObjectsHashOfModAndPath() {
        // The value Objects.hash always gave, so hashed collections built before keep their order and buckets
        String[][] pairs = {
                {"mod", "path"}, {"", ""}, {"a", ""}, {"", "a"}, {"ab", "c"}, {"a", "bc"},
                {"hash_mod", "Aa"}, {"hash_mod", "BB"}, {"\u00fc", "\u2202/x"}, {"\ud83d\ude00", "\u0000"}};
        for (String[] pair : pairs) {
            OptionIdentifier<?> id = OptionIdentifier.intern(pair[0], pair[1]);
            assertEquals(Objects.hash(pair[0], pair[1]), id.hashCode(), id.toString());
            assertEquals(id.hashCode(), id.hashCode());
        }
        // Equal pairs hash alike whatever their types
        OptionIdentifier<Integer> typed = OptionIdentifier.create("hash_mod", "typed", Integer.class);
        assertEquals(Objects.hash("hash_mod", "typed"), typed.hashCode());
        // Ids work as hash keys: each pair finds its own entry
        Map<OptionIdentifier<?>, String> map = new HashMap<>();
        for (String[] pair : pairs) {
            map.put(OptionIdentifier.intern(pair[0], pair[1]), pair[0] + ":" + pair[1]);
        }
        assertEquals(pairs.length, map.size());
        for (String[] pair : pairs) {
            assertEquals(pair[0] + ":" + pair[1], map.get(OptionIdentifier.intern(pair[0], pair[1])));
        }
    }

    @Test
    void aWarmHashCodeAllocatesNothing() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        OptionIdentifier<?> id = OptionIdentifier.create("hash_mod", "allocation");
        int sink = 0;
        for (int i = 0; i < 20_000; i++) {
            sink += id.hashCode();
        }
        threads.getCurrentThreadAllocatedBytes();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100_000; i++) {
            sink += id.hashCode();
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertEquals(id.hashCode() * 120_000, sink);
        // A varargs array per call would be at least 2.4 MB here; the slack covers the measuring calls themselves
        assertTrue(allocated < 16_384, allocated + " bytes allocated");
    }
}
