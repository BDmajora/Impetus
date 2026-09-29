package com.bdmajora.impetus.engine.impl.util.collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoubleBufferedQueueTest {
    @Test
    void flipExposesWrittenElementsInOrder() {
        DoubleBufferedQueue<String> queue = new DoubleBufferedQueue<>();
        assertFalse(queue.flip());
        queue.write().enqueue("a");
        queue.write().enqueue("b");
        assertNull(queue.read().dequeue());
        assertTrue(queue.flip());
        assertEquals("a", queue.read().dequeue());
        assertEquals("b", queue.read().dequeue());
        assertNull(queue.read().dequeue());
    }

    @Test
    void growsPastInitialCapacity() {
        DoubleBufferedQueue<Integer> queue = new DoubleBufferedQueue<>();
        queue.write().ensureCapacity(1000);
        for (int i = 0; i < 1000; i++) {
            queue.write().enqueue(i);
        }
        queue.write().ensureCapacity(1);
        for (int i = 0; i < 300; i++) {
            queue.write().enqueue(i);
        }
        assertTrue(queue.flip());
        int count = 0;
        while (queue.read().dequeue() != null) {
            count++;
        }
        assertEquals(1300, count);
    }

    @Test
    void sizeCountsUnreadElements() throws ReflectiveOperationException {
        DoubleBufferedQueue<String> queue = new DoubleBufferedQueue<>();
        queue.write().enqueue("x");
        queue.write().enqueue("y");
        queue.flip();
        queue.read().dequeue();
        var size = queue.read().getClass().getDeclaredMethod("size");
        size.setAccessible(true);
        assertEquals(1, size.invoke(queue.read()));
    }

    @Test
    void resetEmptiesBothSides() {
        DoubleBufferedQueue<String> queue = new DoubleBufferedQueue<>();
        queue.write().enqueue("x");
        queue.flip();
        queue.write().enqueue("y");
        queue.reset();
        assertNull(queue.read().dequeue());
        assertFalse(queue.flip());
    }
}
