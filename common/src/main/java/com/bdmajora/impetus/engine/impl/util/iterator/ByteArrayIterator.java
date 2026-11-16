package com.bdmajora.impetus.engine.impl.util.iterator;

import java.util.NoSuchElementException;
import java.util.Objects;

// Iterator over the start of a byte array.
public class ByteArrayIterator implements ByteIterator {
    // Bytes to read.
    private final byte[] elements;
    // Number of bytes to read.
    private final int lastIndex;

    // Position of the next byte.
    private int index;

    // Creates an iterator over the first bytes and rejects a null array or a count past its end.
    public ByteArrayIterator(byte[] elements, int lastIndex) {
        Objects.requireNonNull(elements, "elements");
        if (lastIndex > elements.length) {
            throw new IndexOutOfBoundsException("lastIndex " + lastIndex + " exceeds length " + elements.length);
        }

        this.elements = elements;
        this.lastIndex = lastIndex;
        this.index = 0;
    }

    // Returns whether another byte is available.
    @Override
    public boolean hasNext() {
        return this.index < this.lastIndex;
    }

    // Returns the next byte as an unsigned int.
    @Override
    public int nextByteAsInt() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        return Byte.toUnsignedInt(this.elements[this.index++]);
    }
}
