package com.bdmajora.impetus.engine.impl.util.iterator;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class IteratorsTest {
    @Test
    void byteArrayIteratorWidensUnsigned() {
        ByteArrayIterator it = new ByteArrayIterator(new byte[]{(byte) 0xFF, 1, 2}, 2);
        assertTrue(it.hasNext());
        assertEquals(255, it.nextByteAsInt());
        assertEquals(1, it.nextByteAsInt());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::nextByteAsInt);
    }

    @Test
    void reversibleObjectIteratorRunsBothWays() {
        ObjectArrayList<String> list = ObjectArrayList.wrap(new String[]{"a", "b", "c"});
        ReversibleObjectArrayIterator<String> forward = new ReversibleObjectArrayIterator<>(list, false);
        assertEquals("a", forward.next());
        assertEquals("b", forward.next());
        assertEquals("c", forward.next());
        assertFalse(forward.hasNext());
        assertThrows(NoSuchElementException.class, forward::next);
        ReversibleObjectArrayIterator<String> backward = new ReversibleObjectArrayIterator<>(new String[]{"a", "b", "c"}, 1, 3, true);
        assertEquals("c", backward.next());
        assertEquals("b", backward.next());
        assertFalse(backward.hasNext());
    }
}
