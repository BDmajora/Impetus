package com.bdmajora.impetus.engine.impl.util.iterator;

// Iterator over bytes that avoids boxing.
public interface ByteIterator {
    // Returns whether another byte is available.
    boolean hasNext();

    // Returns the next byte as an unsigned int.
    int nextByteAsInt();
}
