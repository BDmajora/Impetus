package com.bdmajora.impetus.engine.impl.util.iterator;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

// Iterator over a range of an array in either direction.
public class ReversibleObjectArrayIterator<T> implements Iterator<T> {
    // Elements to read.
    private final T[] array;
    // Step of the index.
    private final int direction;

    // Position of the next element.
    private int currentIndex;
    // Number of elements left.
    private int remaining;

    // Creates an iterator over the elements of a list.
    public ReversibleObjectArrayIterator(ObjectArrayList<T> list, boolean reverse) {
        this(list.elements(), 0, list.size(), reverse);
    }

    // Creates an iterator over a range and rejects a null array or a range outside it.
    public ReversibleObjectArrayIterator(T[] array, int start, int end, boolean reverse) {
        Objects.requireNonNull(array, "array");

        // An empty or inverted range has no elements
        if (start < end) {
            Objects.checkFromToIndex(start, end, array.length);
            this.remaining = end - start;
        }

        this.array = array;
        this.direction = reverse ? -1 : 1;
        this.currentIndex = reverse ? end - 1 : start;
    }

    // Returns whether another element is available.
    @Override
    public boolean hasNext() {
        return this.remaining > 0;
    }

    // Returns the next element in the chosen direction.
    @Override
    public T next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        T result = this.array[this.currentIndex];

        this.currentIndex += this.direction;
        this.remaining--;

        return result;
    }
}
