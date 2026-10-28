package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Tests of the client memory draw command batch over real native memory and mocked command lists
class DirectMultiDrawBatchTest {
    // Byte alignment that every array of a batch keeps
    private static final long ALIGNMENT = 32L;

    // Batches that a test created and has not freed
    private final List<DirectMultiDrawBatch> live = new ArrayList<>();

    // Render device that a test activated or null when the test uses none
    private GLRenderDevice device;

    // Skips the class on a platform whose pointers are not 8 bytes wide
    @BeforeAll
    static void requireEightBytePointers() {
        assumeTrue(Pointer.POINTER_SIZE == 8);
    }

    // Frees every batch that a test left live and deactivates the render device
    @AfterEach
    void release() {
        for (DirectMultiDrawBatch batch : live) {
            batch.delete();
        }
        live.clear();
        if (device != null) {
            device.makeInactive();
        }
    }

    // Creates a batch that the cleanup frees
    private DirectMultiDrawBatch batch(int capacity) {
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(capacity);
        live.add(batch);
        return batch;
    }

    // Removes the batch from the cleanup list and frees it
    private void free(DirectMultiDrawBatch batch) {
        live.remove(batch);
        batch.delete();
    }

    // Activates a fresh render device and returns its command list with no recorded GL calls
    private CommandList deviceList() {
        device = new GLRenderDevice();
        device.makeActive();
        CommandList list = device.createCommandList();
        Mockito.clearInvocations(TestGl.gl());
        return list;
    }

    // Returns a command list mock that hands out the draw list for every tessellation
    private static CommandList listDrawingWith(DrawCommandList draw) {
        CommandList list = Mockito.mock(CommandList.class);
        Mockito.when(list.beginTessellating(Mockito.any())).thenReturn(draw);
        return list;
    }

    // Returns the byte count rounded up to the alignment
    private static long alignUp(long bytes) {
        return (bytes + ALIGNMENT - 1) & -ALIGNMENT;
    }

    // Returns the block size that a batch with the slot count requests
    private static long blockBytes(long slots) {
        return alignUp(8L * slots) + alignUp(4L * slots) + 4L * slots;
    }

    // Checks that the slot holds the base vertex and count and pointer
    private static void assertSlot(DirectMultiDrawBatch batch, int slot, int baseVertex, int elementCount, long elementPointer) {
        assertEquals(baseVertex, MemoryUtil.memGetInt(batch.pBaseVertex + 4L * slot), "base vertex of slot " + slot);
        assertEquals(elementCount, MemoryUtil.memGetInt(batch.pElementCount + 4L * slot), "element count of slot " + slot);
        assertEquals(elementPointer, MemoryUtil.memGetAddress(batch.pElementPointer + 8L * slot), "element pointer of slot " + slot);
    }

    // Checks that the three arrays of the batch are aligned and do not overlap
    private static void assertLayout(DirectMultiDrawBatch batch) {
        long slots = batch.capacity();
        long[][] arrays = {
                {batch.pElementPointer, batch.pElementPointer + 8L * slots},
                {batch.pElementCount, batch.pElementCount + 4L * slots},
                {batch.pBaseVertex, batch.pBaseVertex + 4L * slots}};
        for (long[] array : arrays) {
            assertNotEquals(MemoryUtil.NULL, array[0]);
            assertEquals(0L, array[0] % ALIGNMENT);
        }
        for (int a = 0; a < arrays.length; a++) {
            for (int b = a + 1; b < arrays.length; b++) {
                assertTrue(arrays[a][1] <= arrays[b][0] || arrays[b][1] <= arrays[a][0], "arrays " + a + " and " + b + " overlap");
            }
        }
    }

    // Returns the message of the exception of the type that the body throws
    private static String message(Class<? extends Throwable> type, Executable body) {
        return assertThrows(type, body).getMessage();
    }

    // Checks that every negative capacity is rejected before any allocation
    @Test
    void constructorRejectsNegativeCapacityBeforeAllocating() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            for (int capacity : new int[]{-1, -2, -1024, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
                assertEquals("Capacity must be between 0 and 2147483646: " + capacity,
                        message(IllegalArgumentException.class, () -> new DirectMultiDrawBatch(capacity)));
            }
            memory.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()), Mockito.never());
        }
    }

    // Checks that the largest int capacity is rejected before any allocation
    @Test
    void constructorRejectsMaximumCapacityBeforeAllocating() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            assertEquals("Capacity must be between 0 and 2147483646: 2147483647",
                    message(IllegalArgumentException.class, () -> new DirectMultiDrawBatch(Integer.MAX_VALUE)));
            memory.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()), Mockito.never());
        }
    }

    // Checks that each capacity requests one aligned block that holds all three arrays
    @Test
    void constructorAllocatesOneBlockForAllArrays() {
        for (int capacity = 0; capacity <= 8; capacity++) {
            int slots = capacity + 1;
            DirectMultiDrawBatch batch;
            try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
                batch = batch(capacity);
                memory.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, blockBytes(slots)));
                memory.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()));
            }
            long end = batch.pElementPointer + blockBytes(slots);
            assertEquals(slots, batch.capacity());
            assertLayout(batch);
            assertTrue(batch.pElementCount + 4L * slots <= end);
            assertTrue(batch.pBaseVertex + 4L * slots <= end);
            assertTrue(batch.pElementCount > batch.pElementPointer);
            assertTrue(batch.pBaseVertex > batch.pElementPointer);
        }
    }

    // Checks that real allocations keep every array aligned and apart for every slot count residue
    @Test
    void constructorAlignsAndSeparatesTheArrays() {
        for (int capacity : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 255, MultiDrawBatch.MAX_COMMAND_COUNT, 4096}) {
            DirectMultiDrawBatch batch = batch(capacity);
            assertEquals(capacity + 1, batch.capacity());
            assertEquals(0, batch.size());
            assertTrue(batch.isEmpty());
            assertEquals(0, batch.getIndexBufferSize());
            assertLayout(batch);
        }
    }

    // Checks that the block size for the largest capacities is computed in long arithmetic
    @Test
    void constructorSizesLargeBlocksWithoutOverflow() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(MemoryUtil.NULL);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(Integer.MAX_VALUE - 1));
            memory.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, 34_359_738_364L));
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(1 << 28));
            memory.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, 4_294_967_364L));
        }
    }

    // Checks that a failed allocation throws OutOfMemoryError and frees nothing
    @Test
    void constructorReportsAllocationFailureWithoutFreeing() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenReturn(MemoryUtil.NULL);
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(0));
            assertThrows(OutOfMemoryError.class, () -> new DirectMultiDrawBatch(MultiDrawBatch.MAX_COMMAND_COUNT));
            memory.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
        }
    }

    // Checks that a zero capacity holds one command in its spare slot and rejects a second
    @Test
    void zeroCapacityHoldsOneCommand() {
        DirectMultiDrawBatch batch = batch(0);
        assertEquals(1, batch.capacity());
        batch.appendDrawCommand(7, 3, 12L);
        assertEquals(1, batch.size());
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(1, 1, 1L)));
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(1, 0, 1L)));
        assertEquals(1, batch.size());
        assertEquals(3, batch.getIndexBufferSize());
        assertSlot(batch, 0, 7, 3, 12L);
    }

    // Checks that each append stores the three fields in the slot at the current size
    @Test
    void appendStoresEveryFieldInItsSlot() {
        DirectMultiDrawBatch batch = batch(4);
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 9, 48L);
        batch.appendDrawCommand(300, 3, 96L);
        assertEquals(3, batch.size());
        assertFalse(batch.isEmpty());
        assertEquals(9, batch.getIndexBufferSize());
        assertSlot(batch, 0, 100, 6, 24L);
        assertSlot(batch, 1, 200, 9, 48L);
        assertSlot(batch, 2, 300, 3, 96L);
    }

    // Checks that int and long limits and misaligned pointers are stored without change
    @Test
    void appendStoresLimitsAndMisalignedPointersVerbatim() {
        int[] bases = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
        long[] pointers = {Long.MIN_VALUE, -1L, 0L, 1L, 2L, 3L, 5L, 7L, 0x7FFF_FFFF_FFFF_FFFDL, Long.MAX_VALUE};
        DirectMultiDrawBatch batch = batch(bases.length * pointers.length);
        int slot = 0;
        for (int base : bases) {
            for (long pointer : pointers) {
                batch.appendDrawCommand(base, (slot & 1) == 0 ? Integer.MAX_VALUE : 1, pointer);
                slot++;
            }
        }
        assertEquals(slot, batch.size());
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        slot = 0;
        for (int base : bases) {
            for (long pointer : pointers) {
                assertSlot(batch, slot, base, (slot & 1) == 0 ? Integer.MAX_VALUE : 1, pointer);
                slot++;
            }
        }
    }

    // Checks that an empty command keeps the size and largest count and leaves its slot for the next append
    @Test
    void appendDiscardsEmptyCommandsInPlace() {
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(5, 0, 2L);
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.getIndexBufferSize());
        assertSlot(batch, 0, 5, 0, 2L);
        batch.appendDrawCommand(10, 4, 8L);
        batch.appendDrawCommand(20, 0, 16L);
        assertEquals(1, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        assertSlot(batch, 1, 20, 0, 16L);
        batch.appendDrawCommand(30, 0, 32L);
        assertEquals(1, batch.size());
        assertSlot(batch, 1, 30, 0, 32L);
        batch.appendDrawCommand(40, 2, 64L);
        assertEquals(2, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        assertSlot(batch, 0, 10, 4, 8L);
        assertSlot(batch, 1, 40, 2, 64L);
    }

    // Checks that a negative count is rejected without writing any slot
    @Test
    void appendRejectsNegativeCounts() {
        DirectMultiDrawBatch batch = batch(3);
        batch.appendDrawCommand(10, 4, 8L);
        batch.appendDrawCommand(55, 0, 66L);
        for (int count : new int[]{-1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, count, 99L)));
            assertEquals(1, batch.size());
            assertEquals(4, batch.getIndexBufferSize());
            assertSlot(batch, 0, 10, 4, 8L);
            assertSlot(batch, 1, 55, 0, 66L);
        }
    }

    // Checks that the largest count only grows across appends
    @Test
    void appendTracksTheLargestCount() {
        DirectMultiDrawBatch batch = batch(4);
        int[] counts = {5, 3, 0, 8, 1};
        int[] largest = {5, 5, 5, 8, 8};
        for (int i = 0; i < counts.length; i++) {
            batch.appendDrawCommand(0, counts[i], 0L);
            assertEquals(largest[i], batch.getIndexBufferSize());
        }
        assertEquals(4, batch.size());
    }

    // Checks that every slot fills and that any further append is rejected without a write
    @Test
    void appendRejectsCommandsPastTheLastSlot() {
        DirectMultiDrawBatch batch = batch(3);
        for (int i = 0; i < 4; i++) {
            batch.appendDrawCommand(i, i + 1, i * 4L);
        }
        assertEquals(batch.capacity(), batch.size());
        for (int count : new int[]{1, 0, Integer.MAX_VALUE}) {
            assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(99, count, 99L)));
        }
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, -1, 99L)));
        assertEquals(4, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        for (int i = 0; i < 4; i++) {
            assertSlot(batch, i, i, i + 1, i * 4L);
        }
    }

    // Checks that the spare slot takes an empty command at the requested capacity and one more command after it
    @Test
    void spareSlotTakesOneCommandPastTheRequestedCapacity() {
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(1, 1, 4L);
        batch.appendDrawCommand(2, 2, 8L);
        batch.appendDrawCommand(3, 0, 12L);
        assertEquals(2, batch.size());
        assertSlot(batch, 2, 3, 0, 12L);
        batch.appendDrawCommand(4, 4, 16L);
        assertEquals(3, batch.size());
        assertSlot(batch, 2, 4, 4, 16L);
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(5, 0, 20L)));
        assertSlot(batch, 2, 4, 4, 16L);
    }

    // Checks that clear reopens every slot from the start and resets the largest count on every round
    @Test
    void clearReopensEverySlot() {
        DirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(1, 5, 4L);
        batch.appendDrawCommand(2, 6, 8L);
        for (int round = 0; round < 3; round++) {
            batch.clear();
            assertTrue(batch.isEmpty());
            assertEquals(0, batch.getIndexBufferSize());
            batch.appendDrawCommand(10 + round, 1 + round, 16L);
            batch.appendDrawCommand(20 + round, 2, 32L);
            assertEquals(2, batch.size());
            assertEquals(Math.max(1 + round, 2), batch.getIndexBufferSize());
            assertSlot(batch, 0, 10 + round, 1 + round, 16L);
            assertSlot(batch, 1, 20 + round, 2, 32L);
        }
        batch.clear();
        batch.clear();
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.getIndexBufferSize());
    }

    // Checks that a merge adds to the count of the last command only
    @Test
    void mergeAddsToTheLastCommandOnly() {
        DirectMultiDrawBatch batch = batch(3);
        batch.appendDrawCommand(10, 6, 24L);
        batch.appendDrawCommand(20, 3, 48L);
        batch.mergeIntoLastCommand(5);
        assertEquals(2, batch.size());
        assertEquals(8, batch.getIndexBufferSize());
        assertSlot(batch, 0, 10, 6, 24L);
        assertSlot(batch, 1, 20, 8, 48L);
        batch.mergeIntoLastCommand(1);
        assertEquals(9, batch.getIndexBufferSize());
        assertSlot(batch, 1, 20, 9, 48L);
        batch.mergeIntoLastCommand(0);
        assertEquals(9, batch.getIndexBufferSize());
        assertSlot(batch, 1, 20, 9, 48L);
        assertEquals(2, batch.size());
    }

    // Checks that a merge that stays below an earlier count keeps the largest count
    @Test
    void mergeKeepsALargerEarlierCount() {
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(0, 50, 0L);
        batch.appendDrawCommand(0, 2, 0L);
        batch.mergeIntoLastCommand(3);
        assertEquals(50, batch.getIndexBufferSize());
        assertSlot(batch, 1, 0, 5, 0L);
    }

    // Checks that a merge after an empty append targets the last counted command
    @Test
    void mergeSkipsTheDiscardedSlot() {
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(10, 4, 8L);
        batch.appendDrawCommand(20, 0, 16L);
        batch.mergeIntoLastCommand(2);
        assertEquals(1, batch.size());
        assertEquals(6, batch.getIndexBufferSize());
        assertSlot(batch, 0, 10, 6, 8L);
        assertSlot(batch, 1, 20, 0, 16L);
    }

    // Checks that a merge into the last slot of a full batch reaches the int limit
    @Test
    void mergeIntoTheLastSlot() {
        DirectMultiDrawBatch batch = batch(2);
        for (int i = 0; i < 3; i++) {
            batch.appendDrawCommand(i, 1, i * 4L);
        }
        batch.mergeIntoLastCommand(Integer.MAX_VALUE - 1);
        assertEquals(3, batch.size());
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertSlot(batch, 1, 1, 1, 4L);
        assertSlot(batch, 2, 2, Integer.MAX_VALUE, 8L);
    }

    // Checks that a merge into an empty batch is rejected without a write
    @Test
    void mergeRejectsAnEmptyBatch() {
        DirectMultiDrawBatch batch = batch(2);
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        batch.appendDrawCommand(1, 0, 4L);
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));
        assertSlot(batch, 0, 1, 0, 4L);
        batch.appendDrawCommand(2, 3, 8L);
        batch.clear();
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(Integer.MAX_VALUE)));
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(-1)));
        assertEquals(0, batch.size());
        assertEquals(0, batch.getIndexBufferSize());
        assertSlot(batch, 0, 2, 3, 8L);
    }

    // Checks that a negative merge is rejected and leaves the command unchanged
    @Test
    void mergeRejectsNegativeCounts() {
        DirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(10, 6, 24L);
        for (int count : new int[]{-1, -6, -7, Integer.MIN_VALUE}) {
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(count)));
            assertEquals(1, batch.size());
            assertEquals(6, batch.getIndexBufferSize());
            assertSlot(batch, 0, 10, 6, 24L);
        }
    }

    // Checks that a merge past the int limit is rejected and leaves the command unchanged
    @Test
    void mergeRejectsOverflow() {
        DirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(10, Integer.MAX_VALUE - 5, 24L);
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(6));
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 5, batch.getIndexBufferSize());
        assertSlot(batch, 0, 10, Integer.MAX_VALUE - 5, 24L);
        batch.mergeIntoLastCommand(5);
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(1));
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertSlot(batch, 0, 10, Integer.MAX_VALUE, 24L);
    }

    // Checks that upload rejects a null command list
    @Test
    void uploadRejectsNullCommandList() {
        DirectMultiDrawBatch batch = batch(1);
        assertEquals("Command list must not be null", message(NullPointerException.class, () -> batch.upload(null)));
    }

    // Checks that repeated uploads touch neither the command list nor GL nor the batch
    @Test
    void uploadLeavesEverythingUntouched() {
        Mockito.clearInvocations(TestGl.gl());
        CommandList list = Mockito.mock(CommandList.class);
        DirectMultiDrawBatch batch = batch(1);
        batch.upload(list);
        batch.appendDrawCommand(1, 2, 3L);
        batch.upload(list);
        batch.upload(list);
        Mockito.verifyNoInteractions(list, TestGl.gl());
        assertEquals(1, batch.size());
        assertEquals(2, batch.getIndexBufferSize());
        assertSlot(batch, 0, 1, 2, 3L);
    }

    // Checks that execute rejects each null argument before binding the tessellation
    @Test
    void executeRejectsNullArgumentsBeforeBinding() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(0, 3, 0L);
        assertEquals("Command list must not be null",
                message(NullPointerException.class, () -> batch.execute(null, tessellation, GlPrimitiveType.TRIANGLES)));
        assertEquals("Tessellation must not be null",
                message(NullPointerException.class, () -> batch.execute(list, null, GlPrimitiveType.TRIANGLES)));
        assertEquals("Primitive type must not be null",
                message(NullPointerException.class, () -> batch.execute(list, tessellation, null)));
        Mockito.verify(list, Mockito.never()).beginTessellating(Mockito.any());
        Mockito.verifyNoInteractions(draw, tessellation);
    }

    // Checks that each execute issues one multidraw of the batch inside the tessellation for every primitive type
    @Test
    void executeIssuesOneMultidrawPerCall() {
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(0, 6, 0L);
        for (GlPrimitiveType type : GlPrimitiveType.values()) {
            DrawCommandList draw = Mockito.mock(DrawCommandList.class);
            CommandList list = listDrawingWith(draw);
            batch.execute(list, tessellation, type);
            batch.execute(list, tessellation, type);
            InOrder order = Mockito.inOrder(list, draw);
            for (int call = 0; call < 2; call++) {
                order.verify(list).beginTessellating(tessellation);
                order.verify(draw).multiDrawElementsBaseVertex(batch, type, GlIndexType.UNSIGNED_INT);
                order.verify(draw).close();
            }
            Mockito.verifyNoMoreInteractions(list, draw);
        }
        Mockito.verifyNoInteractions(tessellation);
        assertEquals(1, batch.size());
        assertSlot(batch, 0, 0, 6, 0L);
    }

    // Checks that execute closes the draw list when the multidraw throws
    @Test
    void executeClosesTheDrawListWhenTheDrawThrows() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        IllegalStateException failure = new IllegalStateException("draw failed");
        Mockito.doThrow(failure).when(draw).multiDrawElementsBaseVertex(Mockito.any(), Mockito.any(), Mockito.any());
        CommandList list = listDrawingWith(draw);
        DirectMultiDrawBatch batch = batch(1);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));
        Mockito.verify(draw).close();
    }

    // Checks that the render device hands the three arrays and the size to glMultiDrawElementsBaseVertex
    @Test
    void executePassesTheArraysAndSizeToGl() {
        CommandList list = deviceList();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = batch(3);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(TestGl.gl()).glMultiDrawElementsBaseVertex(GlPrimitiveType.TRIANGLES.getId(), batch.pElementCount,
                GlIndexType.UNSIGNED_INT.getFormatId(), batch.pElementPointer, 0, batch.pBaseVertex);
        batch.appendDrawCommand(0, 6, 0L);
        batch.appendDrawCommand(4, 0, 24L);
        batch.appendDrawCommand(8, 12, 24L);
        batch.appendDrawCommand(16, 3, 72L);
        batch.execute(list, tessellation, GlPrimitiveType.LINES);
        batch.execute(list, tessellation, GlPrimitiveType.LINES);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glMultiDrawElementsBaseVertex(GlPrimitiveType.LINES.getId(), batch.pElementCount,
                GlIndexType.UNSIGNED_INT.getFormatId(), batch.pElementPointer, 3, batch.pBaseVertex);
        Mockito.verify(tessellation, Mockito.times(3)).bind(list);
        Mockito.verify(tessellation, Mockito.times(3)).unbind(list);
    }

    // Checks that repeated deletes free the block once
    @Test
    void deleteFreesTheBlockOnce() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            DirectMultiDrawBatch batch = new DirectMultiDrawBatch(4);
            batch.appendDrawCommand(1, 2, 3L);
            batch.delete();
            batch.delete();
            batch.clear();
            batch.delete();
            memory.verify(() -> MemoryUtil.nmemAlignedFree(batch.pElementPointer));
            memory.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()));
        }
    }

    // Checks that a deleted batch rejects every use of its arrays and still reports its counters
    @Test
    void deletedBatchRejectsEveryUseOfItsArrays() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        DirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(1, 5, 4L);
        free(batch);
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 3, 8L)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 0, 8L)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.upload(list)));
        assertEquals("Batch is deleted",
                message(IllegalStateException.class, () -> batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES)));
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.appendDrawCommand(2, -1, 8L)));
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(-1)));
        assertEquals("Command list must not be null", message(NullPointerException.class, () -> batch.upload(null)));
        Mockito.verifyNoInteractions(list, draw, tessellation);
        assertEquals(1, batch.size());
        assertEquals(5, batch.getIndexBufferSize());
        assertEquals(3, batch.capacity());
        batch.clear();
        assertTrue(batch.isEmpty());
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 3, 8L)));
        assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
    }

    // Checks that random appends and merges and clears match a plain array model of the slots
    @Test
    void matchesAnArrayModelOverRandomOperations() {
        Random random = new Random(0x5EEDL);
        for (int capacity : new int[]{0, 1, 2, 5, 16}) {
            DirectMultiDrawBatch batch = batch(capacity);
            int slots = capacity + 1;
            int[] bases = new int[slots];
            int[] counts = new int[slots];
            long[] pointers = new long[slots];
            boolean[] written = new boolean[slots];
            int size = 0;
            int largest = 0;
            for (int step = 0; step < 2000; step++) {
                int operation = random.nextInt(10);
                if (operation < 6) {
                    int base = random.nextInt();
                    int count = random.nextInt(4) == 0 ? 0 : random.nextInt(1000) + 1;
                    long pointer = random.nextLong();
                    if (size == slots) {
                        assertThrows(IllegalStateException.class, () -> batch.appendDrawCommand(base, count, pointer));
                    } else {
                        batch.appendDrawCommand(base, count, pointer);
                        bases[size] = base;
                        counts[size] = count;
                        pointers[size] = pointer;
                        written[size] = true;
                        size += count > 0 ? 1 : 0;
                        largest = Math.max(largest, count);
                    }
                } else if (operation < 9) {
                    int extra = random.nextInt(1000);
                    if (size == 0) {
                        assertThrows(IllegalStateException.class, () -> batch.mergeIntoLastCommand(extra));
                    } else {
                        batch.mergeIntoLastCommand(extra);
                        counts[size - 1] += extra;
                        largest = Math.max(largest, counts[size - 1]);
                    }
                } else {
                    batch.clear();
                    size = 0;
                    largest = 0;
                }
                assertEquals(size, batch.size());
                assertEquals(size == 0, batch.isEmpty());
                assertEquals(largest, batch.getIndexBufferSize());
                for (int slot = 0; slot < slots; slot++) {
                    if (written[slot]) {
                        assertSlot(batch, slot, bases[slot], counts[slot], pointers[slot]);
                    }
                }
            }
        }
    }
}
