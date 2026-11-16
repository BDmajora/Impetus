package com.bdmajora.impetus.engine.impl.util.collections;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// Queue with a side that is filled and a side that is consumed.
public final class DoubleBufferedQueue<E> {
    // Side that is consumed and side that is filled.
    private QueueImpl<E> read, write;

    // Creates empty sides.
    public DoubleBufferedQueue() {
        this.read = new QueueImpl<>();
        this.write = new QueueImpl<>();
    }

    // Swaps the sides and returns whether the filled side had elements.
    public boolean flip() {
        if (this.write.isEmpty()) {
            return false;
        }

        var tmp = this.read;
        this.read = this.write;
        this.write = tmp;

        this.write.clear();

        return true;
    }

    // Empties both sides.
    public void reset() {
        this.read.clear();
        this.write.clear();
    }

    // Returns the side that is consumed.
    public ReadQueue<E> read() {
        return this.read;
    }

    // Returns the side that is filled.
    public WriteQueue<E> write() {
        return this.write;
    }

    // Array queue that is consumed once and then cleared.
    private static final class QueueImpl<E> implements ReadQueue<E>, WriteQueue<E> {
        // Elements of the queue.
        private E[] elements;
        // Position of the next read and the next write.
        private int readIndex, writeIndex;

        // Creates a queue with the default capacity.
        QueueImpl() {
            this(256);
        }

        // Creates a queue with a capacity.
        @SuppressWarnings("unchecked")
        QueueImpl(int capacity) {
            this.elements = (E[]) new Object[capacity];
        }

        // Grows the queue to hold more elements.
        @Override
        public void ensureCapacity(int numElements) {
            int len = this.writeIndex + numElements;

            if (len > this.elements.length) {
                this.grow(len);
            }
        }

        // Removes and returns the next element or null when empty.
        @Override
        public @Nullable E dequeue() {
            if (this.readIndex == this.writeIndex) {
                return null;
            }

            return this.elements[this.readIndex++];
        }

        // Adds an element and grows when full and rejects null.
        @Override
        public void enqueue(@NotNull E e) {
            java.util.Objects.requireNonNull(e, "e");
            if (this.writeIndex >= this.elements.length) {
                this.grow(this.writeIndex + 1);
            }

            this.elements[this.writeIndex++] = e;
        }

        // Removes every element and clears the references.
        public void clear() {
            if (this.writeIndex != 0) {
                Arrays.fill(this.elements, 0, this.writeIndex, null);
            }

            this.readIndex = 0;
            this.writeIndex = 0;
        }

        // Returns the number of elements.
        public int size() {
            return this.writeIndex - this.readIndex;
        }

        // Returns whether the queue has no elements.
        public boolean isEmpty() {
            return this.readIndex == this.writeIndex;
        }

        // Grows to at least a size.
        private void grow(int minimumSize) {
            this.resize(getNextSize(minimumSize, this.elements.length));
        }

        // Copies the elements into an array of a length.
        private void resize(int length) {
            @SuppressWarnings("unchecked")
            E[] elements = (E[]) new Object[length];
            System.arraycopy(this.elements, 0, elements, 0, this.writeIndex);

            this.elements = elements;
        }

        // Returns the size to grow to.
        private static int getNextSize(int minimumSize, int currentSize) {
            return Math.max(minimumSize, currentSize << 1);
        }
    }
}
