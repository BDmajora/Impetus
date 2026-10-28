package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Unit tests of every member of IndexedVertexData
class IndexedVertexDataTest {
    // Message of a free on a buffer that was already freed
    private static final String DELETED = "Buffer has been deleted";
    // Capacities on the byte and word and page boundaries
    private static final int[] CAPACITIES = {0, 1, 2, 3, 4, 7, 8, 9, 15, 16, 17, 4095, 4096, 4097, 1 << 20};
    // Number of records in the bulk test
    private static final int MANY = 1000;
    // Number of rounds in the race test
    private static final int ROUNDS = 200;

    // Format of one float attribute that the records of the tests hold
    private final GlVertexFormat format = format("position");

    // Creates a format of one float attribute with the given name
    private static GlVertexFormat format(String name) {
        return GlVertexFormat.builder(4).addElement(name, 0, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
    }

    // Returns the total of live native bytes
    private static long total() {
        return NativeBuffer.getTotalAllocated();
    }

    // Returns the start address of a live buffer
    private static long address(NativeBuffer buffer) {
        return MemoryUtil.memAddress(buffer.getDirectBuffer());
    }

    // Returns whether a buffer was freed and refuses new views
    private static boolean freed(NativeBuffer buffer) {
        try {
            buffer.getDirectBuffer();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // Opens a static mock of MemoryUtil that records each freed address and answers nmemAlloc with the stand in address or the real allocator when it is null
    private static MockedStatic<MemoryUtil> memory(List<Long> freed, LongFunction<Long> standIn) {
        return Mockito.mockStatic(MemoryUtil.class, invocation -> {
            String name = invocation.getMethod().getName();

            if (name.equals("nmemFree")) {
                freed.add(invocation.getArgument(0));
            } else if (name.equals("nmemAlloc")) {
                Long address = standIn.apply(invocation.<Long>getArgument(0));

                if (address != null) {
                    return address;
                }
            }

            return invocation.callRealMethod();
        });
    }

    // Opens a static mock of MemoryUtil that records each freed address and calls the real methods
    private static MockedStatic<MemoryUtil> recordingFrees(List<Long> freed) {
        return memory(freed, size -> null);
    }

    // Creates a mock buffer whose free throws the given exception
    private static NativeBuffer failing(Throwable failure) {
        NativeBuffer buffer = Mockito.mock(NativeBuffer.class);
        Mockito.doThrow(failure).when(buffer).free();
        return buffer;
    }

    // Creates and deletes a record whose buffers become unreachable and returns their addresses
    private long[] deleteDropped() {
        NativeBuffer vertices = new NativeBuffer(20);
        NativeBuffer indices = new NativeBuffer(10);
        long[] addresses = {address(vertices), address(indices)};

        new IndexedVertexData(this.format, vertices, indices).delete();

        return addresses;
    }

    // Checks that the accessors return the stored instances on every call
    @Test
    void storesTheComponents() {
        NativeBuffer vertices = new NativeBuffer(8);
        NativeBuffer indices = new NativeBuffer(4);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        for (int call = 0; call < 3; call++) {
            assertSame(format, data.vertexFormat());
            assertSame(vertices, data.vertexBuffer());
            assertSame(indices, data.indexBuffer());
        }

        data.delete();
    }

    // Checks that a null vertex format is rejected without touching the buffers
    @Test
    void rejectsANullVertexFormat() {
        NativeBuffer vertices = Mockito.mock(NativeBuffer.class);
        NativeBuffer indices = Mockito.mock(NativeBuffer.class);

        NullPointerException error = assertThrows(NullPointerException.class, () -> new IndexedVertexData(null, vertices, indices));

        assertEquals("Vertex format must not be null", error.getMessage());
        Mockito.verifyNoInteractions(vertices, indices);
    }

    // Checks that a null vertex buffer is rejected without touching the index buffer
    @Test
    void rejectsANullVertexBuffer() {
        NativeBuffer indices = Mockito.mock(NativeBuffer.class);

        NullPointerException error = assertThrows(NullPointerException.class, () -> new IndexedVertexData(format, null, indices));

        assertEquals("Vertex buffer must not be null", error.getMessage());
        Mockito.verifyNoInteractions(indices);
    }

    // Checks that a null index buffer is rejected without touching the vertex buffer
    @Test
    void rejectsANullIndexBuffer() {
        NativeBuffer vertices = Mockito.mock(NativeBuffer.class);

        NullPointerException error = assertThrows(NullPointerException.class, () -> new IndexedVertexData(format, vertices, null));

        assertEquals("Index buffer must not be null", error.getMessage());
        Mockito.verifyNoInteractions(vertices);
    }

    // Checks that the first null component in declaration order is reported
    @Test
    void reportsTheFirstNullComponent() {
        NativeBuffer buffer = Mockito.mock(NativeBuffer.class);

        assertEquals("Vertex format must not be null", assertThrows(NullPointerException.class, () -> new IndexedVertexData(null, null, null)).getMessage());
        assertEquals("Vertex format must not be null", assertThrows(NullPointerException.class, () -> new IndexedVertexData(null, buffer, null)).getMessage());
        assertEquals("Vertex buffer must not be null", assertThrows(NullPointerException.class, () -> new IndexedVertexData(format, null, null)).getMessage());
        Mockito.verifyNoInteractions(buffer);
    }

    // Checks that a negative capacity fails in NativeBuffer before any record or native block exists
    @Test
    void negativeCapacitiesNeverReachARecord() {
        long before = total();

        for (int capacity : new int[] {-1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new IndexedVertexData(format, new NativeBuffer(capacity), new NativeBuffer(1)));
            assertEquals(before, total());
        }
    }

    // Checks that delete frees the vertex buffer before the index buffer and touches nothing else
    @Test
    void deleteFreesTheVertexBufferFirst() {
        NativeBuffer vertices = Mockito.mock(NativeBuffer.class);
        NativeBuffer indices = Mockito.mock(NativeBuffer.class);

        new IndexedVertexData(format, vertices, indices).delete();

        InOrder order = Mockito.inOrder(vertices, indices);
        order.verify(vertices).free();
        order.verify(indices).free();
        Mockito.verifyNoMoreInteractions(vertices, indices);
    }

    // Checks that delete releases both native blocks once at each capacity and restores the total
    @Test
    void deleteReleasesBothBuffersAtEachCapacity() {
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            for (int i = 0; i < CAPACITIES.length; i++) {
                int vertexCapacity = CAPACITIES[i];
                int indexCapacity = CAPACITIES[CAPACITIES.length - 1 - i];
                NativeBuffer vertices = new NativeBuffer(vertexCapacity);
                NativeBuffer indices = new NativeBuffer(indexCapacity);
                long vertexAddress = address(vertices);
                long indexAddress = address(indices);

                freed.clear();
                assertNotEquals(vertexAddress, indexAddress);
                assertEquals(before + vertexCapacity + indexCapacity, total());
                new IndexedVertexData(format, vertices, indices).delete();
                assertEquals(List.of(vertexAddress, indexAddress), freed);
                assertEquals(before, total());
                assertTrue(freed(vertices));
                assertTrue(freed(indices));
                assertEquals(vertexCapacity, vertices.getLength());
                assertEquals(indexCapacity, indices.getLength());
            }
        }

        assertEquals(before, total());
    }

    // Checks that delete releases two empty buffers and their distinct one byte blocks
    @Test
    void deleteReleasesEmptyBuffers() {
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            NativeBuffer vertices = new NativeBuffer(0);
            NativeBuffer indices = new NativeBuffer(0);
            long vertexAddress = address(vertices);
            long indexAddress = address(indices);

            assertNotEquals(MemoryUtil.NULL, vertexAddress);
            assertNotEquals(MemoryUtil.NULL, indexAddress);
            assertNotEquals(vertexAddress, indexAddress);
            assertEquals(before, total());
            new IndexedVertexData(format, vertices, indices).delete();
            assertEquals(List.of(vertexAddress, indexAddress), freed);
        }

        assertEquals(before, total());
    }

    // Checks that delete releases two buffers at the int limit and restores the total in long arithmetic
    @Test
    void deleteReleasesBuffersAtTheIntLimit() {
        long first = MemoryUtil.nmemAlloc(16);
        long second = MemoryUtil.nmemAlloc(16);
        long[] standIns = {first, second};
        AtomicInteger next = new AtomicInteger();
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory(freed, size -> size == Integer.MAX_VALUE ? standIns[next.getAndIncrement()] : null)) {
            NativeBuffer vertices = new NativeBuffer(Integer.MAX_VALUE);
            NativeBuffer indices = new NativeBuffer(Integer.MAX_VALUE);
            IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

            assertEquals(before + 2L * Integer.MAX_VALUE, total());
            data.delete();
            assertEquals(List.of(first, second), freed);
            assertEquals(Integer.MAX_VALUE, vertices.getLength());
            assertEquals(Integer.MAX_VALUE, indices.getLength());
        }

        assertEquals(before, total());
    }

    // Checks that delete releases buffers copied from sources at odd offsets and from a single byte
    @Test
    void deleteReleasesCopiesOfMisalignedSources() {
        ByteBuffer source = MemoryUtil.memAlloc(64);
        long before = total();

        try {
            for (int i = 0; i < source.capacity(); i++) {
                source.put(i, (byte) (i * 13 + 5));
            }

            NativeBuffer vertices = NativeBuffer.copy(source.position(3).limit(40));
            NativeBuffer indices = NativeBuffer.copy(source.limit(64).position(63));

            assertEquals(37, vertices.getLength());
            assertEquals(1, indices.getLength());
            assertEquals(source.get(3), vertices.getDirectBuffer().get(0));
            assertEquals(source.get(63), indices.getDirectBuffer().get(0));
            assertEquals(before + 38, total());
            new IndexedVertexData(format, vertices, indices).delete();
            assertTrue(freed(vertices));
            assertTrue(freed(indices));
            assertEquals(before, total());
        } finally {
            MemoryUtil.memFree(source);
        }
    }

    // Checks that delete frees a buffer that serves as vertex and index buffer once and a second delete is refused
    @Test
    void deleteFreesAnAliasedBufferOnce() {
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            NativeBuffer shared = new NativeBuffer(32);
            long address = address(shared);
            IndexedVertexData data = new IndexedVertexData(format, shared, shared);

            assertDoesNotThrow(data::delete);
            assertEquals(List.of(address), freed);
            assertTrue(freed(shared));
            assertEquals(before, total());

            IllegalStateException again = assertThrows(IllegalStateException.class, data::delete);

            assertEquals(DELETED, again.getMessage());
            assertEquals(0, again.getSuppressed().length);
            assertEquals(List.of(address), freed);
        }

        assertEquals(before, total());
    }

    // Checks that an aliased mock buffer receives one free call
    @Test
    void deleteCallsFreeOnceOnAnAliasedBuffer() {
        NativeBuffer shared = Mockito.mock(NativeBuffer.class);

        new IndexedVertexData(format, shared, shared).delete();

        Mockito.verify(shared).free();
        Mockito.verifyNoMoreInteractions(shared);
    }

    // Checks that a failed free of an aliased buffer is rethrown and not retried
    @Test
    void deleteDoesNotRetryAFailedAliasedBuffer() {
        IllegalStateException failure = new IllegalStateException(DELETED);
        NativeBuffer shared = failing(failure);
        IndexedVertexData data = new IndexedVertexData(format, shared, shared);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, data::delete);

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        Mockito.verify(shared).free();
        Mockito.verifyNoMoreInteractions(shared);
    }

    // Checks that repeated deletes are refused with the index refusal suppressed and free nothing again
    @Test
    void repeatedDeleteIsRefused() {
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            NativeBuffer vertices = new NativeBuffer(12);
            NativeBuffer indices = new NativeBuffer(6);
            IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

            data.delete();
            assertEquals(2, freed.size());

            for (int call = 0; call < 3; call++) {
                IllegalStateException again = assertThrows(IllegalStateException.class, data::delete);

                assertEquals(DELETED, again.getMessage());
                assertEquals(1, again.getSuppressed().length);
                assertTrue(again.getSuppressed()[0] instanceof IllegalStateException);
                assertEquals(DELETED, again.getSuppressed()[0].getMessage());
                assertNotSame(again, again.getSuppressed()[0]);
            }

            assertEquals(2, freed.size());
            assertEquals(12, vertices.getLength());
            assertEquals(6, indices.getLength());
        }

        assertEquals(before, total());
    }

    // Checks that delete still frees the index buffer when the vertex buffer was freed elsewhere
    @Test
    void deleteFreesTheIndexBufferAfterAFreedVertexBuffer() {
        long before = total();
        NativeBuffer vertices = new NativeBuffer(16);
        NativeBuffer indices = new NativeBuffer(8);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        vertices.free();

        IllegalStateException error = assertThrows(IllegalStateException.class, data::delete);

        assertEquals(DELETED, error.getMessage());
        assertEquals(0, error.getSuppressed().length);
        assertTrue(freed(indices));
        assertEquals(before, total());
    }

    // Checks that delete frees the vertex buffer and then reports an index buffer that was freed elsewhere
    @Test
    void deleteReportsAFreedIndexBufferAfterFreeingTheVertexBuffer() {
        long before = total();
        NativeBuffer vertices = new NativeBuffer(16);
        NativeBuffer indices = new NativeBuffer(8);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        indices.free();

        IllegalStateException error = assertThrows(IllegalStateException.class, data::delete);

        assertEquals(DELETED, error.getMessage());
        assertEquals(0, error.getSuppressed().length);
        assertTrue(freed(vertices));
        assertEquals(before, total());
    }

    // Checks that a runtime failure of the vertex buffer still frees the index buffer and is rethrown unchanged
    @Test
    void deleteFreesTheIndexBufferAfterAVertexRuntimeFailure() {
        long before = total();
        RuntimeException failure = new UnsupportedOperationException("vertex");
        NativeBuffer vertices = failing(failure);
        NativeBuffer indices = new NativeBuffer(8);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        RuntimeException thrown = assertThrows(RuntimeException.class, data::delete);

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertTrue(freed(indices));
        assertEquals(before, total());
    }

    // Checks that an error from the vertex buffer still frees the index buffer and is rethrown unchanged
    @Test
    void deleteFreesTheIndexBufferAfterAVertexError() {
        long before = total();
        InternalError failure = new InternalError("vertex");
        NativeBuffer vertices = failing(failure);
        NativeBuffer indices = new NativeBuffer(8);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        InternalError thrown = assertThrows(InternalError.class, data::delete);

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertTrue(freed(indices));
        assertEquals(before, total());
    }

    // Checks that a second failure from the index buffer is kept as suppressed on the vertex failure
    @Test
    void deleteKeepsTheIndexFailureAsSuppressed() {
        IllegalStateException vertexFailure = new IllegalStateException("vertex");
        InternalError indexFailure = new InternalError("index");
        NativeBuffer vertices = failing(vertexFailure);
        NativeBuffer indices = failing(indexFailure);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, data::delete);

        assertSame(vertexFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(indexFailure, thrown.getSuppressed()[0]);
        Mockito.verify(vertices).free();
        Mockito.verify(indices).free();
    }

    // Checks that one exception thrown by both buffers is rethrown without suppressing itself
    @Test
    void deleteRethrowsASharedFailureWithoutSelfSuppression() {
        IllegalStateException failure = new IllegalStateException("shared");
        NativeBuffer vertices = failing(failure);
        NativeBuffer indices = failing(failure);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, data::delete);

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        Mockito.verify(vertices).free();
        Mockito.verify(indices).free();
    }

    // Checks that a failure of the index buffer after a successful vertex free is rethrown unchanged
    @Test
    void deleteRethrowsAnIndexFailure() {
        long before = total();
        NativeBuffer vertices = new NativeBuffer(8);
        IllegalStateException failure = new IllegalStateException("index");
        NativeBuffer indices = failing(failure);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, data::delete);

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertTrue(freed(vertices));
        assertEquals(before, total());
        Mockito.verify(indices).free();
    }

    // Checks that two records over the same buffers free them once and the second delete is refused
    @Test
    void recordsOverTheSameBuffersFreeThemOnce() {
        List<Long> freed = new ArrayList<>();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            NativeBuffer vertices = new NativeBuffer(24);
            NativeBuffer indices = new NativeBuffer(12);
            IndexedVertexData first = new IndexedVertexData(format, vertices, indices);
            IndexedVertexData second = new IndexedVertexData(format, vertices, indices);

            first.delete();

            IllegalStateException error = assertThrows(IllegalStateException.class, second::delete);

            assertEquals(DELETED, error.getMessage());
            assertEquals(2, freed.size());
        }

        assertEquals(before, total());
    }

    // Checks that reclaiming after delete frees no address again
    @Test
    void reclaimAfterDeleteFreesNothingAgain() {
        List<Long> freed = new ArrayList<>();

        try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
            long[] addresses = deleteDropped();

            for (int pass = 0; pass < 3; pass++) {
                NativeBuffer.reclaim(true);
            }

            assertEquals(1, Collections.frequency(freed, addresses[0]));
            assertEquals(1, Collections.frequency(freed, addresses[1]));
        }
    }

    // Checks that two threads deleting the same record release each buffer once and at most one delete succeeds
    @Test
    void racingDeletesReleaseEachBufferOnce() throws Exception {
        long before = total();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (int round = 0; round < ROUNDS; round++) {
                IndexedVertexData data = new IndexedVertexData(format, new NativeBuffer(64), new NativeBuffer(32));
                CyclicBarrier start = new CyclicBarrier(2);
                Callable<Boolean> delete = () -> {
                    start.await();

                    try {
                        data.delete();
                        return true;
                    } catch (IllegalStateException e) {
                        assertEquals(DELETED, e.getMessage());
                        return false;
                    }
                };
                Future<Boolean> a = pool.submit(delete);
                Future<Boolean> b = pool.submit(delete);
                int succeeded = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);

                assertTrue(succeeded <= 1);
                assertTrue(freed(data.vertexBuffer()));
                assertTrue(freed(data.indexBuffer()));
                assertEquals(before, total());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that many records each free their own buffers in reverse order and restore the total
    @Test
    void manyRecordsFreeEveryBuffer() {
        long before = total();
        IndexedVertexData[] records = new IndexedVertexData[MANY];

        for (int i = 0; i < MANY; i++) {
            records[i] = new IndexedVertexData(format, new NativeBuffer(i), new NativeBuffer(MANY - i));
        }

        assertEquals(before + (long) MANY * MANY, total());

        for (int i = MANY - 1; i >= 0; i--) {
            records[i].delete();
        }

        assertEquals(before, total());

        for (IndexedVertexData data : records) {
            assertTrue(freed(data.vertexBuffer()));
            assertTrue(freed(data.indexBuffer()));
        }
    }

    // Checks that records are equal exactly when every component is the same instance in the same slot
    @Test
    void equalsComparesEveryComponent() {
        NativeBuffer vertices = Mockito.mock(NativeBuffer.class);
        NativeBuffer indices = Mockito.mock(NativeBuffer.class);
        NativeBuffer other = Mockito.mock(NativeBuffer.class);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);
        IndexedVertexData same = new IndexedVertexData(format, vertices, indices);

        assertEquals(data, data);
        assertEquals(data, same);
        assertEquals(same, data);
        assertEquals(data.hashCode(), same.hashCode());
        assertNotEquals(data, new IndexedVertexData(format("normal"), vertices, indices));
        assertNotEquals(data, new IndexedVertexData(format, other, indices));
        assertNotEquals(data, new IndexedVertexData(format, vertices, other));
        assertNotEquals(data, new IndexedVertexData(format, indices, vertices));
        assertNotEquals(data, null);
        assertNotEquals(data, (Object) "data");
        Mockito.verifyNoInteractions(vertices, indices, other);
    }

    // Checks that toString names each component with its own text
    @Test
    void toStringNamesEachComponent() {
        NativeBuffer vertices = Mockito.mock(NativeBuffer.class);
        NativeBuffer indices = Mockito.mock(NativeBuffer.class);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);

        assertEquals("IndexedVertexData[vertexFormat=" + format + ", vertexBuffer=" + vertices + ", indexBuffer=" + indices + "]", data.toString());
        assertEquals(data.toString(), data.toString());
    }

    // Checks that the accessors and equality and text still answer the same after delete
    @Test
    void membersAnswerAfterDelete() {
        NativeBuffer vertices = new NativeBuffer(4);
        NativeBuffer indices = new NativeBuffer(2);
        IndexedVertexData data = new IndexedVertexData(format, vertices, indices);
        int hash = data.hashCode();
        String text = data.toString();

        data.delete();

        assertSame(format, data.vertexFormat());
        assertSame(vertices, data.vertexBuffer());
        assertSame(indices, data.indexBuffer());
        assertEquals(hash, data.hashCode());
        assertEquals(text, data.toString());
        assertEquals(data, new IndexedVertexData(format, vertices, indices));
    }
}
