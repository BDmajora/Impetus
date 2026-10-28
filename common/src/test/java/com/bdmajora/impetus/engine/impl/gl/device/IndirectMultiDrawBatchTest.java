package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the indirect draw command batch over guarded native memory and mocked or device command lists
class IndirectMultiDrawBatchTest {
    // Byte size of one indirect command
    private static final int COMMAND_SIZE = 20;
    // Byte alignment that the batch requests for its block
    private static final long ALIGNMENT = 32L;
    // Byte count of each guard zone around a block
    private static final long GUARD = 128L;
    // Byte value that fills the guard zones and every fresh block
    private static final byte GUARD_BYTE = (byte) 0xA5;

    // Byte offset of the element count field in a command
    private static final long COUNT = 0L;
    // Byte offset of the instance count field in a command
    private static final long INSTANCES = 4L;
    // Byte offset of the first index field in a command
    private static final long FIRST_INDEX = 8L;
    // Byte offset of the base vertex field in a command
    private static final long BASE_VERTEX = 12L;
    // Byte offset of the base instance field in a command
    private static final long BASE_INSTANCE = 16L;

    // Largest element pointer that the batch accepts
    private static final long MAX_POINTER = 0x3_FFFF_FFFCL;
    // Message prefix of every element pointer refusal
    private static final String POINTER_MESSAGE = "Element pointer must be a multiple of 4 between 0 and 17179869180: ";

    // GL enum of the indirect binding point
    private static final int DRAW_INDIRECT = GlBufferTarget.DRAW_INDIRECT_BUFFER.getTargetParameter();
    // GL enum of the array binding point
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();

    // Batches that a test created and has not freed
    private final List<IndirectMultiDrawBatch> live = new ArrayList<>();

    // Guarded memory mock that is open for the whole test
    private GuardedMemory memory;

    // Render device that a test activated or null when the test uses none
    private GLRenderDevice device;

    // Opens the guarded memory mock
    @BeforeEach
    void openMemory() {
        memory = new GuardedMemory();
    }

    // Frees every batch that a test left live and deactivates the render device and closes the guarded memory mock
    @AfterEach
    void release() {
        try {
            for (IndirectMultiDrawBatch batch : live) {
                batch.delete();
            }
            live.clear();
            if (device != null) {
                device.makeInactive();
            }
        } finally {
            memory.close();
        }
    }

    // Creates a batch that the cleanup frees
    private IndirectMultiDrawBatch batch(int capacity) {
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(capacity);
        live.add(batch);
        return batch;
    }

    // Removes the batch from the cleanup list and frees it
    private void free(IndirectMultiDrawBatch batch) {
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

    // Uploads the batch through a fresh command list mock and returns the buffer that the upload filled
    private static GlMutableBuffer uploadThroughMock(IndirectMultiDrawBatch batch, CommandList list) {
        batch.upload(list);
        ArgumentCaptor<GlMutableBuffer> buffer = ArgumentCaptor.forClass(GlMutableBuffer.class);
        Mockito.verify(list).uploadData(buffer.capture(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(GlBufferUsage.class));
        Mockito.clearInvocations(list);
        return buffer.getValue();
    }

    // Stubs the upload of the list to copy the uploaded commands into the holder
    private static void captureUploads(CommandList list, int[][] holder) {
        Mockito.doAnswer(inv -> {
            holder[0] = ints(inv.<Long>getArgument(1), inv.<Long>getArgument(2));
            return null;
        }).when(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(GlBufferUsage.class));
    }

    // Returns the bytes at the address
    private static byte[] bytes(long address, long count) {
        byte[] out = new byte[(int) count];
        MemoryUtil.memByteBuffer(address, out.length).get(out);
        return out;
    }

    // Returns the ints in the byte range at the address
    private static int[] ints(long address, long byteCount) {
        int[] out = new int[(int) (byteCount / Integer.BYTES)];
        MemoryUtil.memIntBuffer(address, out.length).get(out);
        return out;
    }

    // Returns the five fields of one command in field order
    private static int[] command(int count, long pointer, int baseVertex) {
        return new int[]{count, 1, (int) (pointer >>> 2), baseVertex, 0};
    }

    // Returns the commands joined in slot order
    private static int[] commands(int[]... commands) {
        int[] out = new int[commands.length * 5];
        for (int i = 0; i < commands.length; i++) {
            System.arraycopy(commands[i], 0, out, i * 5, 5);
        }
        return out;
    }

    // Checks every field of the command in the slot of the block
    private static void assertCommand(long block, int slot, int count, long pointer, int baseVertex) {
        long command = block + (long) slot * COMMAND_SIZE;
        assertEquals(count, MemoryUtil.memGetInt(command + COUNT), "count of slot " + slot);
        assertEquals(1, MemoryUtil.memGetInt(command + INSTANCES), "instance count of slot " + slot);
        assertEquals((int) (pointer >>> 2), MemoryUtil.memGetInt(command + FIRST_INDEX), "first index of slot " + slot);
        assertEquals(baseVertex, MemoryUtil.memGetInt(command + BASE_VERTEX), "base vertex of slot " + slot);
        assertEquals(0, MemoryUtil.memGetInt(command + BASE_INSTANCE), "base instance of slot " + slot);
    }

    // Checks that the slot of the block holds only guard bytes
    private static void assertUntouched(long block, int slot) {
        for (byte value : bytes(block + (long) slot * COMMAND_SIZE, COMMAND_SIZE)) {
            assertEquals(GUARD_BYTE, value, "slot " + slot + " was written");
        }
    }

    // Returns the message of the exception of the type that the body throws
    private static String message(Class<? extends Throwable> type, Executable body) {
        return assertThrows(type, body).getMessage();
    }

    // Checks that every negative capacity is rejected before any allocation or buffer generation
    @Test
    void constructorRejectsNegativeCapacityBeforeAllocating() {
        for (int capacity : new int[]{-1, -2, -1024, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
            assertEquals("Capacity must be between 0 and 2147483646: " + capacity,
                    message(IllegalArgumentException.class, () -> new IndirectMultiDrawBatch(capacity)));
        }
        memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()), Mockito.never());
        Mockito.verify(TestGl.gl(), Mockito.never()).glGenBuffers();
    }

    // Checks that the largest int capacity is rejected before any allocation or buffer generation
    @Test
    void constructorRejectsMaximumCapacityBeforeAllocating() {
        assertEquals("Capacity must be between 0 and 2147483646: 2147483647",
                message(IllegalArgumentException.class, () -> new IndirectMultiDrawBatch(Integer.MAX_VALUE)));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()), Mockito.never());
        Mockito.verify(TestGl.gl(), Mockito.never()).glGenBuffers();
    }

    // Checks that each capacity requests one aligned block with one command per slot and one buffer name
    @Test
    void constructorRequestsOneAlignedBlockAndOneBuffer() {
        int[] capacities = {0, 1, 2, 3, 7, 8, 255, MultiDrawBatch.MAX_COMMAND_COUNT};
        for (int capacity : capacities) {
            IndirectMultiDrawBatch batch = batch(capacity);
            long bytes = (capacity + 1L) * COMMAND_SIZE;
            memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, bytes));
            assertEquals(0L, memory.last % ALIGNMENT);
            assertEquals(capacity + 1, batch.capacity());
            assertEquals(0, batch.size());
            assertTrue(batch.isEmpty());
            assertEquals(0, batch.getIndexBufferSize());
        }
        memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong()), Mockito.times(capacities.length));
        Mockito.verify(TestGl.gl(), Mockito.times(capacities.length)).glGenBuffers();
    }

    // Checks that the constructor writes no byte of the block
    @Test
    void constructorWritesNothingIntoTheBlock() {
        IndirectMultiDrawBatch batch = batch(MultiDrawBatch.MAX_COMMAND_COUNT);
        byte[] block = bytes(memory.last, (long) batch.capacity() * COMMAND_SIZE);
        for (int offset = 0; offset < block.length; offset++) {
            assertEquals(GUARD_BYTE, block[offset], "byte " + offset + " was written");
        }
        memory.mock.verify(() -> MemoryUtil.memPutInt(Mockito.anyLong(), Mockito.anyInt()), Mockito.never());
    }

    // Checks that the block size for the largest capacities is computed in long arithmetic
    @Test
    void constructorSizesLargeBlocksWithoutOverflow() {
        memory.failing = true;
        assertEquals("Failed to allocate indirect command buffer",
                message(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(Integer.MAX_VALUE - 1)));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, 42_949_672_940L));
        assertThrows(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(1 << 28));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedAlloc(ALIGNMENT, 5_368_709_140L));
        Mockito.verify(TestGl.gl(), Mockito.never()).glGenBuffers();
    }

    // Checks that a failed allocation throws OutOfMemoryError and neither frees nor generates a buffer
    @Test
    void constructorReportsAllocationFailureWithoutFreeing() {
        memory.failing = true;
        assertThrows(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(0));
        assertThrows(OutOfMemoryError.class, () -> new IndirectMultiDrawBatch(MultiDrawBatch.MAX_COMMAND_COUNT));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
        Mockito.verify(TestGl.gl(), Mockito.never()).glGenBuffers();
    }

    // Checks that the block is freed when the buffer name is zero or its generation throws
    @Test
    void constructorFreesTheBlockWhenTheBufferCannotBeGenerated() {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals("Failed to generate a buffer", message(IllegalStateException.class, () -> new IndirectMultiDrawBatch(4)));
        long first = memory.last;
        IllegalStateException failure = new IllegalStateException("generation failed");
        Mockito.doThrow(failure).when(gl).glGenBuffers();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new IndirectMultiDrawBatch(0)));
        long second = memory.last;
        assertNotEquals(first, second);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(first));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(second));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.times(2));
    }

    // Checks that a zero capacity holds one command in its spare slot and rejects a second
    @Test
    void zeroCapacityHoldsOneCommand() {
        IndirectMultiDrawBatch batch = batch(0);
        long block = memory.last;
        assertEquals(1, batch.capacity());
        batch.appendDrawCommand(7, 3, 12L);
        assertEquals(1, batch.size());
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(1, 1, 4L)));
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(1, 0, 4L)));
        assertEquals(1, batch.size());
        assertEquals(3, batch.getIndexBufferSize());
        assertCommand(block, 0, 3, 12L, 7);
    }

    // Checks that each append stores all five fields in the slot at the current size and nothing past it
    @Test
    void appendStoresEveryFieldInItsSlot() {
        IndirectMultiDrawBatch batch = batch(4);
        long block = memory.last;
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 9, 48L);
        batch.appendDrawCommand(300, 3, 96L);
        assertEquals(3, batch.size());
        assertFalse(batch.isEmpty());
        assertEquals(9, batch.getIndexBufferSize());
        assertCommand(block, 0, 6, 24L, 100);
        assertCommand(block, 1, 9, 48L, 200);
        assertCommand(block, 2, 3, 96L, 300);
        assertUntouched(block, 3);
        assertUntouched(block, 4);
    }

    // Checks that int limits and every pointer up to the index limit are stored in full
    @Test
    void appendStoresIntAndPointerLimits() {
        int[] bases = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
        long[] pointers = {0L, 4L, 1L << 31, 4L * Integer.MAX_VALUE, 1L << 33, MAX_POINTER - 4L, MAX_POINTER};
        IndirectMultiDrawBatch batch = batch(bases.length * pointers.length);
        long block = memory.last;
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
                assertCommand(block, slot, (slot & 1) == 0 ? Integer.MAX_VALUE : 1, pointer, base);
                slot++;
            }
        }
        assertEquals(-1, MemoryUtil.memGetInt(block + (long) (pointers.length - 1) * COMMAND_SIZE + FIRST_INDEX));
        assertEquals(Integer.MIN_VALUE, MemoryUtil.memGetInt(block + 4L * COMMAND_SIZE + FIRST_INDEX));
    }

    // Checks the refusal of each pointer for an empty and a counted command without any write
    private void assertPointersRejected(long... pointers) {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(10, 4, 8L);
        for (long pointer : pointers) {
            for (int count : new int[]{0, 5, Integer.MAX_VALUE}) {
                assertEquals(POINTER_MESSAGE + pointer,
                        message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, count, pointer)));
            }
        }
        assertEquals(1, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        assertCommand(block, 0, 4, 8L, 10);
        assertUntouched(block, 1);
    }

    // Checks that a pointer that is not a multiple of 4 is rejected without a write
    @Test
    void appendRejectsMisalignedPointers() {
        assertPointersRejected(1L, 2L, 3L, 5L, 6L, 7L, 4002L, MAX_POINTER - 1L, MAX_POINTER + 1L, MAX_POINTER + 3L);
    }

    // Checks that a negative pointer is rejected without a write
    @Test
    void appendRejectsNegativePointers() {
        assertPointersRejected(-1L, -4L, -MAX_POINTER, -(1L << 34), Long.MIN_VALUE + 4L, Long.MIN_VALUE);
    }

    // Checks that a pointer past the 32 bit index limit is rejected without a write
    @Test
    void appendRejectsPointersPastTheIndexLimit() {
        assertPointersRejected(MAX_POINTER + 4L, 1L << 34, (1L << 34) + 4L, 1L << 62, Long.MAX_VALUE - 3L, Long.MAX_VALUE);
    }

    // Checks that an empty command keeps the size and largest count and leaves its slot for the next append
    @Test
    void appendDiscardsEmptyCommandsInPlace() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(5, 0, 4L);
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.getIndexBufferSize());
        assertCommand(block, 0, 0, 4L, 5);
        batch.appendDrawCommand(10, 4, 8L);
        batch.appendDrawCommand(20, 0, 16L);
        assertEquals(1, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        assertCommand(block, 1, 0, 16L, 20);
        batch.appendDrawCommand(30, 0, 32L);
        assertEquals(1, batch.size());
        assertCommand(block, 1, 0, 32L, 30);
        batch.appendDrawCommand(40, 2, 64L);
        assertEquals(2, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        assertCommand(block, 0, 4, 8L, 10);
        assertCommand(block, 1, 2, 64L, 40);
        assertUntouched(block, 2);
    }

    // Checks that a negative count is rejected before the pointer and slot checks and without a write
    @Test
    void appendRejectsNegativeCounts() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(10, 4, 8L);
        for (int count : new int[]{-1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, count, 96L)));
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, count, 3L)));
            assertEquals(1, batch.size());
            assertEquals(4, batch.getIndexBufferSize());
        }
        batch.appendDrawCommand(20, 2, 16L);
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, -1, -1L)));
        assertCommand(block, 0, 4, 8L, 10);
        assertCommand(block, 1, 2, 16L, 20);
    }

    // Checks that the largest count only grows across appends
    @Test
    void appendTracksTheLargestCount() {
        IndirectMultiDrawBatch batch = batch(4);
        int[] counts = {5, 3, 0, 8, 1};
        int[] largest = {5, 5, 5, 8, 8};
        for (int i = 0; i < counts.length; i++) {
            batch.appendDrawCommand(0, counts[i], 0L);
            assertEquals(largest[i], batch.getIndexBufferSize());
        }
        assertEquals(4, batch.size());
    }

    // Checks that every slot fills and that any further append is rejected without a write past the block
    @Test
    void appendRejectsCommandsPastTheLastSlot() {
        IndirectMultiDrawBatch batch = batch(3);
        long block = memory.last;
        for (int i = 0; i < 4; i++) {
            batch.appendDrawCommand(i, i + 1, i * 4L);
        }
        assertEquals(batch.capacity(), batch.size());
        for (int count : new int[]{1, 0, Integer.MAX_VALUE}) {
            assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(99, count, 96L)));
        }
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, -1, 96L)));
        assertEquals(POINTER_MESSAGE + 98, message(IllegalArgumentException.class, () -> batch.appendDrawCommand(99, 1, 98L)));
        assertEquals(4, batch.size());
        assertEquals(4, batch.getIndexBufferSize());
        for (int i = 0; i < 4; i++) {
            assertCommand(block, i, i + 1, i * 4L, i);
        }
    }

    // Checks that the spare slot takes an empty command at the requested capacity and one more command after it
    @Test
    void spareSlotTakesOneCommandPastTheRequestedCapacity() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(1, 1, 4L);
        batch.appendDrawCommand(2, 2, 8L);
        batch.appendDrawCommand(3, 0, 12L);
        assertEquals(2, batch.size());
        assertCommand(block, 2, 0, 12L, 3);
        batch.appendDrawCommand(4, 4, 16L);
        assertEquals(3, batch.size());
        assertCommand(block, 2, 4, 16L, 4);
        assertEquals("Batch is full", message(IllegalStateException.class, () -> batch.appendDrawCommand(5, 0, 20L)));
        assertCommand(block, 2, 4, 16L, 4);
    }

    // Checks that clear reopens every slot from the start and resets the largest count on every round
    @Test
    void clearReopensEverySlot() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
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
            assertCommand(block, 0, 1 + round, 16L, 10 + round);
            assertCommand(block, 1, 2, 32L, 20 + round);
        }
        batch.clear();
        batch.clear();
        assertTrue(batch.isEmpty());
        assertEquals(0, batch.getIndexBufferSize());
    }

    // Checks that a merge adds to the count of the last command only and keeps its other fields
    @Test
    void mergeAddsToTheLastCommandOnly() {
        IndirectMultiDrawBatch batch = batch(3);
        long block = memory.last;
        batch.appendDrawCommand(10, 6, 24L);
        batch.appendDrawCommand(20, 3, 48L);
        batch.mergeIntoLastCommand(5);
        assertEquals(2, batch.size());
        assertEquals(8, batch.getIndexBufferSize());
        assertCommand(block, 0, 6, 24L, 10);
        assertCommand(block, 1, 8, 48L, 20);
        batch.mergeIntoLastCommand(1);
        assertEquals(9, batch.getIndexBufferSize());
        assertCommand(block, 1, 9, 48L, 20);
        batch.mergeIntoLastCommand(0);
        assertEquals(9, batch.getIndexBufferSize());
        assertCommand(block, 1, 9, 48L, 20);
        assertEquals(2, batch.size());
        assertUntouched(block, 2);
    }

    // Checks that a merge that stays below an earlier count keeps the largest count
    @Test
    void mergeKeepsALargerEarlierCount() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(0, 50, 0L);
        batch.appendDrawCommand(0, 2, 0L);
        batch.mergeIntoLastCommand(3);
        assertEquals(50, batch.getIndexBufferSize());
        assertCommand(block, 1, 5, 0L, 0);
    }

    // Checks that a merge after an empty append targets the last counted command
    @Test
    void mergeSkipsTheDiscardedSlot() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(10, 4, 8L);
        batch.appendDrawCommand(20, 0, 16L);
        batch.mergeIntoLastCommand(2);
        assertEquals(1, batch.size());
        assertEquals(6, batch.getIndexBufferSize());
        assertCommand(block, 0, 6, 8L, 10);
        assertCommand(block, 1, 0, 16L, 20);
    }

    // Checks that a merge into the last slot of a full batch reaches the int limit
    @Test
    void mergeIntoTheLastSlot() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        for (int i = 0; i < 3; i++) {
            batch.appendDrawCommand(i, 1, i * 4L);
        }
        batch.mergeIntoLastCommand(Integer.MAX_VALUE - 1);
        assertEquals(3, batch.size());
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertCommand(block, 1, 1, 4L, 1);
        assertCommand(block, 2, Integer.MAX_VALUE, 8L, 2);
    }

    // Checks that a merge into an empty batch is rejected without a write before the block
    @Test
    void mergeRejectsAnEmptyBatch() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        batch.appendDrawCommand(1, 0, 4L);
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));
        assertCommand(block, 0, 0, 4L, 1);
        batch.appendDrawCommand(2, 3, 8L);
        batch.clear();
        assertEquals("Batch is empty", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(Integer.MAX_VALUE)));
        assertEquals("Element count must not be negative: -1",
                message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(-1)));
        assertEquals(0, batch.size());
        assertEquals(0, batch.getIndexBufferSize());
        assertCommand(block, 0, 3, 8L, 2);
    }

    // Checks that a negative merge is rejected and leaves the command unchanged
    @Test
    void mergeRejectsNegativeCounts() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(10, 6, 24L);
        for (int count : new int[]{-1, -6, -7, Integer.MIN_VALUE}) {
            assertEquals("Element count must not be negative: " + count,
                    message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(count)));
            assertEquals(1, batch.size());
            assertEquals(6, batch.getIndexBufferSize());
            assertCommand(block, 0, 6, 24L, 10);
        }
    }

    // Checks that a merge past the int limit is rejected and leaves the command unchanged
    @Test
    void mergeRejectsOverflow() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(10, Integer.MAX_VALUE - 5, 24L);
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(6));
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 5, batch.getIndexBufferSize());
        assertCommand(block, 0, Integer.MAX_VALUE - 5, 24L, 10);
        batch.mergeIntoLastCommand(5);
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertThrows(ArithmeticException.class, () -> batch.mergeIntoLastCommand(1));
        assertEquals(Integer.MAX_VALUE, batch.getIndexBufferSize());
        assertCommand(block, 0, Integer.MAX_VALUE, 24L, 10);
    }

    // Checks that upload rejects a null command list and keeps the batch open
    @Test
    void uploadRejectsNullCommandList() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        assertEquals("Command list must not be null", message(NullPointerException.class, () -> batch.upload(null)));
        batch.appendDrawCommand(1, 2, 4L);
        assertCommand(block, 0, 2, 4L, 1);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
    }

    // Checks that upload copies the counted commands into the buffer and then unbinds the array buffer and frees the block
    @Test
    void uploadCopiesTheCommandsAndFreesTheBlock() {
        IndirectMultiDrawBatch batch = batch(3);
        long block = memory.last;
        batch.appendDrawCommand(100, 6, 24L);
        batch.appendDrawCommand(200, 0, 0L);
        batch.appendDrawCommand(300, 12, 0L);
        batch.mergeIntoLastCommand(6);
        CommandList list = Mockito.mock(CommandList.class);
        int[][] uploaded = new int[1][];
        int[] freesDuringUpload = new int[1];
        Mockito.doAnswer(inv -> {
            uploaded[0] = ints(inv.<Long>getArgument(1), inv.<Long>getArgument(2));
            freesDuringUpload[0] = memory.blocks.get(block).frees;
            return null;
        }).when(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(GlBufferUsage.class));
        batch.upload(list);
        InOrder order = Mockito.inOrder(list);
        order.verify(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(block), Mockito.eq(40L), Mockito.eq(GlBufferUsage.STATIC_DRAW));
        order.verify(list).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        Mockito.verifyNoMoreInteractions(list);
        assertArrayEquals(commands(command(6, 24L, 100), command(18, 0L, 300)), uploaded[0]);
        assertEquals(0, freesDuringUpload[0]);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
        assertEquals(2, batch.size());
        assertEquals(18, batch.getIndexBufferSize());
    }

    // Checks that an empty batch uploads zero bytes and still frees its block
    @Test
    void uploadOfAnEmptyBatchUploadsNoBytes() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(5, 0, 8L);
        CommandList list = Mockito.mock(CommandList.class);
        batch.upload(list);
        Mockito.verify(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(block), Mockito.eq(0L), Mockito.eq(GlBufferUsage.STATIC_DRAW));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
    }

    // Checks that a full batch uploads every slot including the spare one
    @Test
    void uploadOfAFullBatchUploadsEverySlot() {
        IndirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(1, 1, 4L);
        batch.appendDrawCommand(2, 2, 8L);
        batch.appendDrawCommand(3, Integer.MAX_VALUE, MAX_POINTER);
        CommandList list = Mockito.mock(CommandList.class);
        int[][] uploaded = new int[1][];
        captureUploads(list, uploaded);
        batch.upload(list);
        Mockito.verify(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.anyLong(), Mockito.eq(60L), Mockito.eq(GlBufferUsage.STATIC_DRAW));
        assertArrayEquals(commands(command(1, 4L, 1), command(2, 8L, 2), command(Integer.MAX_VALUE, MAX_POINTER, 3)), uploaded[0]);
    }

    // Checks that the device binds the buffer and hands GL the block and then unbinds the array buffer
    @Test
    void uploadThroughTheDeviceHandsGlTheBlock() {
        CommandList list = deviceList();
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(41).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(7, 6, 12L);
        int[][] uploaded = new int[1][];
        Mockito.doAnswer(inv -> {
            uploaded[0] = ints(inv.<Long>getArgument(2), inv.<Long>getArgument(1));
            return null;
        }).when(gl).glBufferData(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        batch.upload(list);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glBindBuffer(ARRAY, 41);
        order.verify(gl).glBufferData(ARRAY, 20L, block, GlBufferUsage.STATIC_DRAW.getId());
        order.verify(gl).glBindBuffer(ARRAY, 0);
        assertArrayEquals(command(6, 12L, 7), uploaded[0]);
    }

    // Checks that a second upload is rejected without a command list call or a second free
    @Test
    void secondUploadIsRejected() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(1, 2, 4L);
        CommandList list = Mockito.mock(CommandList.class);
        uploadThroughMock(batch, list);
        assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.upload(list)));
        assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.upload(list)));
        Mockito.verifyNoInteractions(list);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()));
    }

    // Checks that a failed upload unbinds the array buffer and keeps the block for more appends and a retry
    @Test
    void failedUploadKeepsTheBlockForARetry() {
        IndirectMultiDrawBatch batch = batch(2);
        long block = memory.last;
        batch.appendDrawCommand(1, 3, 4L);
        CommandList list = Mockito.mock(CommandList.class);
        IllegalStateException failure = new IllegalStateException("upload failed");
        Mockito.doThrow(failure).doNothing().when(list)
                .uploadData(Mockito.any(GlMutableBuffer.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(GlBufferUsage.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.upload(list)));
        Mockito.verify(list).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
        batch.appendDrawCommand(2, 5, 8L);
        assertCommand(block, 0, 3, 4L, 1);
        assertCommand(block, 1, 5, 8L, 2);
        batch.upload(list);
        Mockito.verify(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(block), Mockito.eq(20L), Mockito.eq(GlBufferUsage.STATIC_DRAW));
        Mockito.verify(list).uploadData(Mockito.any(GlMutableBuffer.class), Mockito.eq(block), Mockito.eq(40L), Mockito.eq(GlBufferUsage.STATIC_DRAW));
        Mockito.verify(list, Mockito.times(2)).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
    }

    // Checks that a failed unbind after the upload keeps the block and the batch open
    @Test
    void failedUnbindAfterUploadKeepsTheBlock() {
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(1, 3, 4L);
        CommandList list = Mockito.mock(CommandList.class);
        IllegalStateException failure = new IllegalStateException("unbind failed");
        Mockito.doThrow(failure).doNothing().when(list).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.upload(list)));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
        batch.mergeIntoLastCommand(1);
        assertCommand(block, 0, 4, 4L, 1);
        batch.upload(list);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
    }

    // Checks that a failed upload keeps its failure when the unbind fails too and suppresses a distinct unbind failure
    @Test
    void failedUploadKeepsItsFailureWhenTheUnbindFails() {
        IndirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(1, 3, 4L);
        CommandList list = Mockito.mock(CommandList.class);
        IllegalStateException failure = new IllegalStateException("upload failed");
        IllegalStateException unbindFailure = new IllegalStateException("unbind failed");
        Mockito.doThrow(failure).when(list)
                .uploadData(Mockito.any(GlMutableBuffer.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(GlBufferUsage.class));
        Mockito.doThrow(unbindFailure).doThrow(failure).when(list).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.upload(list)));
        assertArrayEquals(new Throwable[]{unbindFailure}, failure.getSuppressed());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.upload(list)));
        assertArrayEquals(new Throwable[]{unbindFailure}, failure.getSuppressed());
        Mockito.verify(list, Mockito.times(2)).bindBuffer(GlBufferTarget.ARRAY_BUFFER, null);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()), Mockito.never());
        batch.mergeIntoLastCommand(1);
        assertEquals(4, batch.getIndexBufferSize());
    }

    // Checks that writes after the upload are rejected and that a clear does not reopen the slots
    @Test
    void writesAfterUploadAreRejected() {
        IndirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(1, 5, 4L);
        uploadThroughMock(batch, Mockito.mock(CommandList.class));
        for (int round = 0; round < 2; round++) {
            assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 3, 8L)));
            assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 0, 8L)));
            assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
            assertEquals("Batch is already uploaded", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(0)));
            assertEquals("Element count must not be negative: -1",
                    message(IllegalArgumentException.class, () -> batch.appendDrawCommand(2, -1, 8L)));
            assertEquals("Element count must not be negative: -1",
                    message(IllegalArgumentException.class, () -> batch.mergeIntoLastCommand(-1)));
            assertEquals(POINTER_MESSAGE + 9, message(IllegalArgumentException.class, () -> batch.appendDrawCommand(2, 3, 9L)));
            batch.clear();
            assertEquals(0, batch.size());
            assertEquals(0, batch.getIndexBufferSize());
        }
    }

    // Checks that the name of a deleted uploaded batch stays out of the cached array buffer binding
    @Test
    void uploadLeavesNoCachedBindingOfTheDeletedName() {
        CommandList list = deviceList();
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(42).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(1);
        batch.appendDrawCommand(0, 6, 0L);
        batch.upload(list);
        batch.delete();
        GlMutableBuffer reused = list.createMutableBuffer();
        try {
            list.uploadData(reused, 0L, 0L, GlBufferUsage.STATIC_DRAW);
            Mockito.verify(gl, Mockito.times(2)).glBindBuffer(ARRAY, 42);
        } finally {
            list.deleteBuffer(reused);
        }
    }

    // Checks that execute rejects each null argument before any command list call
    @Test
    void executeRejectsNullArgumentsBeforeBinding() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch batch = batch(1);
        batch.appendDrawCommand(0, 3, 0L);
        uploadThroughMock(batch, list);
        assertEquals("Command list must not be null",
                message(NullPointerException.class, () -> batch.execute(null, tessellation, GlPrimitiveType.TRIANGLES)));
        assertEquals("Tessellation must not be null",
                message(NullPointerException.class, () -> batch.execute(list, null, GlPrimitiveType.TRIANGLES)));
        assertEquals("Primitive type must not be null",
                message(NullPointerException.class, () -> batch.execute(list, tessellation, null)));
        Mockito.verifyNoInteractions(list, draw, tessellation);
    }

    // Checks that a batch that is not uploaded refuses to draw before any command list call
    @Test
    void executeRejectsABatchThatIsNotUploaded() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch batch = batch(1);
        assertEquals("Batch is not uploaded",
                message(IllegalStateException.class, () -> batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES)));
        batch.appendDrawCommand(0, 3, 0L);
        assertEquals("Batch is not uploaded",
                message(IllegalStateException.class, () -> batch.execute(list, tessellation, GlPrimitiveType.LINES)));
        Mockito.verifyNoInteractions(list, draw, tessellation);
    }

    // Checks that each execute binds the buffer and draws every command inside the tessellation and unbinds the buffer
    @Test
    void executeBindsDrawsAndUnbindsForEveryPrimitiveType() {
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch batch = batch(2);
        batch.appendDrawCommand(0, 6, 0L);
        batch.appendDrawCommand(4, 3, 24L);
        GlMutableBuffer buffer = uploadThroughMock(batch, Mockito.mock(CommandList.class));
        for (GlPrimitiveType type : GlPrimitiveType.values()) {
            DrawCommandList draw = Mockito.mock(DrawCommandList.class);
            CommandList list = listDrawingWith(draw);
            batch.execute(list, tessellation, type);
            batch.execute(list, tessellation, type);
            InOrder order = Mockito.inOrder(list, draw);
            for (int call = 0; call < 2; call++) {
                order.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer);
                order.verify(list).beginTessellating(tessellation);
                order.verify(draw).multiDrawElementsIndirect(0L, 2, type, GlIndexType.UNSIGNED_INT);
                order.verify(draw).close();
                order.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
            }
            Mockito.verifyNoMoreInteractions(list, draw);
        }
        Mockito.verifyNoInteractions(tessellation);
        assertEquals(2, batch.size());
    }

    // Checks that an uploaded empty batch and a batch cleared after its upload draw zero commands
    @Test
    void executeDrawsTheCountedCommandsOnly() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch empty = batch(0);
        uploadThroughMock(empty, list);
        empty.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        IndirectMultiDrawBatch cleared = batch(1);
        cleared.appendDrawCommand(0, 6, 0L);
        cleared.appendDrawCommand(6, 6, 24L);
        uploadThroughMock(cleared, list);
        cleared.clear();
        cleared.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        Mockito.verify(draw, Mockito.times(2)).multiDrawElementsIndirect(0L, 0, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
        Mockito.verify(draw, Mockito.times(2)).close();
    }

    // Checks that execute closes the draw list and unbinds the buffer when the draw throws
    @Test
    void executeUnbindsWhenTheDrawThrows() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        IllegalStateException failure = new IllegalStateException("draw failed");
        Mockito.doThrow(failure).when(draw).multiDrawElementsIndirect(Mockito.anyLong(), Mockito.anyInt(), Mockito.any(), Mockito.any());
        CommandList list = listDrawingWith(draw);
        IndirectMultiDrawBatch batch = batch(1);
        uploadThroughMock(batch, list);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));
        InOrder order = Mockito.inOrder(list, draw);
        order.verify(draw).close();
        order.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
    }

    // Checks that execute unbinds the buffer when the tessellation cannot begin
    @Test
    void executeUnbindsWhenTheTessellationThrows() {
        CommandList list = Mockito.mock(CommandList.class);
        IllegalStateException failure = new IllegalStateException("bind failed");
        Mockito.when(list.beginTessellating(Mockito.any())).thenThrow(failure);
        IndirectMultiDrawBatch batch = batch(1);
        GlMutableBuffer buffer = uploadThroughMock(batch, list);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));
        InOrder order = Mockito.inOrder(list);
        order.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer);
        order.verify(list).beginTessellating(Mockito.any());
        order.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        Mockito.verifyNoMoreInteractions(list);
    }

    // Checks that execute unbinds the buffer when its bind throws and begins no tessellation
    @Test
    void executeUnbindsWhenTheBindThrows() {
        CommandList list = Mockito.mock(CommandList.class);
        IndirectMultiDrawBatch batch = batch(1);
        GlMutableBuffer buffer = uploadThroughMock(batch, list);
        IllegalStateException failure = new IllegalStateException("bind failed");
        Mockito.doThrow(failure).when(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, buffer);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));
        Mockito.verify(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        Mockito.verify(list, Mockito.never()).beginTessellating(Mockito.any());
    }

    // Checks that a failed draw keeps its failure when the unbind fails too and suppresses a distinct unbind failure
    @Test
    void failedDrawKeepsItsFailureWhenTheUnbindFails() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        IllegalStateException failure = new IllegalStateException("draw failed");
        IllegalStateException unbindFailure = new IllegalStateException("unbind failed");
        Mockito.doThrow(failure).when(draw).multiDrawElementsIndirect(Mockito.anyLong(), Mockito.anyInt(), Mockito.any(), Mockito.any());
        CommandList list = listDrawingWith(draw);
        IndirectMultiDrawBatch batch = batch(1);
        uploadThroughMock(batch, list);
        Mockito.doThrow(unbindFailure).doThrow(failure).when(list).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES)));
        assertArrayEquals(new Throwable[]{unbindFailure}, failure.getSuppressed());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES)));
        assertArrayEquals(new Throwable[]{unbindFailure}, failure.getSuppressed());
        Mockito.verify(list, Mockito.times(2)).bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, null);
        Mockito.verify(draw, Mockito.times(2)).close();
    }

    // Checks that the device draws every uploaded command from the indirect buffer and unbinds it after each draw
    @Test
    void executeThroughTheDeviceDrawsFromTheIndirectBuffer() {
        CommandList list = deviceList();
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(43).when(gl).glGenBuffers();
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch batch = batch(3);
        batch.appendDrawCommand(0, 6, 0L);
        batch.appendDrawCommand(4, 0, 24L);
        batch.appendDrawCommand(8, 12, 24L);
        batch.upload(list);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        batch.execute(list, tessellation, GlPrimitiveType.TRIANGLES);
        InOrder order = Mockito.inOrder(gl);
        for (int call = 0; call < 2; call++) {
            order.verify(gl).glBindBuffer(DRAW_INDIRECT, 43);
            order.verify(gl).glMultiDrawElementsIndirect(GlPrimitiveType.TRIANGLES.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0L, 2, 0);
            order.verify(gl).glBindBuffer(DRAW_INDIRECT, 0);
        }
        Mockito.verify(tessellation, Mockito.times(2)).bind(list);
        Mockito.verify(tessellation, Mockito.times(2)).unbind(list);
    }

    // Checks that the name of a deleted batch whose draw failed stays out of the cached indirect buffer binding
    @Test
    void failedDrawLeavesNoCachedBindingOfTheDeletedName() {
        CommandList list = deviceList();
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(44).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(1);
        batch.appendDrawCommand(0, 6, 0L);
        batch.upload(list);
        IllegalStateException failure = new IllegalStateException("draw failed");
        Mockito.doThrow(failure).when(gl).glMultiDrawElementsIndirect(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES)));
        batch.delete();
        GlMutableBuffer reused = list.createMutableBuffer();
        try {
            list.bindBuffer(GlBufferTarget.DRAW_INDIRECT_BUFFER, reused);
            Mockito.verify(gl, Mockito.times(2)).glBindBuffer(DRAW_INDIRECT, 44);
        } finally {
            list.deleteBuffer(reused);
        }
    }

    // Checks that repeated deletes of a batch that was never uploaded free the block and the buffer once
    @Test
    void deleteBeforeUploadFreesTheBlockAndTheBufferOnce() {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(51).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = batch(4);
        long block = memory.last;
        batch.appendDrawCommand(1, 2, 4L);
        free(batch);
        batch.delete();
        batch.clear();
        batch.delete();
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()));
        Mockito.verify(gl).glDeleteBuffers(51);
        Mockito.verify(gl).glDeleteBuffers(Mockito.anyInt());
    }

    // Checks that deletes after the upload delete the buffer once and never free the block again
    @Test
    void deleteAfterUploadDeletesOnlyTheBuffer() {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(52).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = batch(1);
        long block = memory.last;
        batch.appendDrawCommand(1, 2, 4L);
        uploadThroughMock(batch, Mockito.mock(CommandList.class));
        free(batch);
        batch.delete();
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()));
        Mockito.verify(gl).glDeleteBuffers(52);
        Mockito.verify(gl).glDeleteBuffers(Mockito.anyInt());
    }

    // Checks that a failed buffer deletion frees the block once and is retried by the next delete
    @Test
    void deleteRetriesAFailedBufferDeletion() {
        LWJGLService gl = TestGl.gl();
        Mockito.doReturn(53).when(gl).glGenBuffers();
        IndirectMultiDrawBatch batch = new IndirectMultiDrawBatch(1);
        long block = memory.last;
        IllegalStateException failure = new IllegalStateException("delete failed");
        Mockito.doThrow(failure).doNothing().when(gl).glDeleteBuffers(53);
        assertSame(failure, assertThrows(IllegalStateException.class, batch::delete));
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(block));
        batch.delete();
        batch.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteBuffers(53);
        memory.mock.verify(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong()));
    }

    // Checks that a batch deleted before or after its upload rejects every use and still reports its counters
    @Test
    void deletedBatchRejectsEveryUse() {
        DrawCommandList draw = Mockito.mock(DrawCommandList.class);
        CommandList list = listDrawingWith(draw);
        GlTessellation tessellation = Mockito.mock(GlTessellation.class);
        IndirectMultiDrawBatch assembling = batch(2);
        assembling.appendDrawCommand(1, 5, 4L);
        IndirectMultiDrawBatch uploaded = batch(2);
        uploaded.appendDrawCommand(1, 5, 4L);
        uploadThroughMock(uploaded, list);
        for (IndirectMultiDrawBatch batch : new IndirectMultiDrawBatch[]{assembling, uploaded}) {
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
            assertEquals(POINTER_MESSAGE + 2, message(IllegalArgumentException.class, () -> batch.appendDrawCommand(2, 3, 2L)));
            assertEquals("Command list must not be null", message(NullPointerException.class, () -> batch.upload(null)));
            assertEquals("Command list must not be null",
                    message(NullPointerException.class, () -> batch.execute(null, tessellation, GlPrimitiveType.TRIANGLES)));
            assertEquals(1, batch.size());
            assertEquals(5, batch.getIndexBufferSize());
            assertEquals(3, batch.capacity());
            batch.clear();
            assertTrue(batch.isEmpty());
            assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.appendDrawCommand(2, 3, 8L)));
            assertEquals("Batch is deleted", message(IllegalStateException.class, () -> batch.mergeIntoLastCommand(1)));
        }
        Mockito.verifyNoInteractions(list, draw, tessellation);
    }

    // Checks that the class keeps its public shape
    @Test
    void publicShapeIsUnchanged() {
        assertTrue(Modifier.isFinal(IndirectMultiDrawBatch.class.getModifiers()));
        assertSame(MultiDrawBatch.class, IndirectMultiDrawBatch.class.getSuperclass());
        Constructor<?>[] constructors = IndirectMultiDrawBatch.class.getConstructors();
        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());
        Set<String> methods = new TreeSet<>();
        for (Method method : IndirectMultiDrawBatch.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        assertEquals(Set.of("appendDrawCommand", "delete", "execute", "mergeIntoLastCommand", "upload"), methods);
    }

    // Checks that random appends and merges and clears match a model and that the upload hands over the modelled commands
    @Test
    void matchesAModelOverRandomOperations() {
        Random random = new Random(0x1D1EC7L);
        for (int capacity : new int[]{0, 1, 2, 5, 16}) {
            IndirectMultiDrawBatch batch = batch(capacity);
            long block = memory.last;
            int slots = capacity + 1;
            int[] bases = new int[slots];
            int[] counts = new int[slots];
            long[] pointers = new long[slots];
            boolean[] written = new boolean[slots];
            int size = 0;
            int largest = 0;
            for (int step = 0; step < 1500; step++) {
                int operation = random.nextInt(20);
                if (operation < 11) {
                    int base = random.nextInt();
                    int count = random.nextInt(4) == 0 ? 0 : random.nextInt(1000) + 1;
                    long pointer = (random.nextLong() & MAX_POINTER);
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
                } else if (operation < 12) {
                    long pointer = random.nextLong() | 1L;
                    assertThrows(IllegalArgumentException.class, () -> batch.appendDrawCommand(1, 1, pointer));
                } else if (operation < 18) {
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
                int[] held = ints(block, (long) slots * COMMAND_SIZE);
                for (int slot = 0; slot < slots; slot++) {
                    if (written[slot]) {
                        int[] expected = command(counts[slot], pointers[slot], bases[slot]);
                        assertArrayEquals(expected, Arrays.copyOfRange(held, slot * 5, slot * 5 + 5), "slot " + slot);
                    }
                }
            }
            int[][] expected = new int[size][];
            for (int slot = 0; slot < size; slot++) {
                expected[slot] = command(counts[slot], pointers[slot], bases[slot]);
            }
            DrawCommandList draw = Mockito.mock(DrawCommandList.class);
            CommandList list = listDrawingWith(draw);
            int[][] uploaded = new int[1][];
            captureUploads(list, uploaded);
            batch.upload(list);
            assertArrayEquals(commands(expected), uploaded[0]);
            batch.execute(list, Mockito.mock(GlTessellation.class), GlPrimitiveType.TRIANGLES);
            Mockito.verify(draw).multiDrawElementsIndirect(0L, size, GlPrimitiveType.TRIANGLES, GlIndexType.UNSIGNED_INT);
            free(batch);
        }
    }

    // Guarded native block with its raw region and the number of frees it received
    private static final class Block {
        // Address of the raw region that holds both guard zones
        private final long raw;
        // Address of the block that the batch receives
        private final long address;
        // Byte count that the batch requested
        private final long bytes;
        // Byte count of the raw region
        private final long total;
        // Number of frees of the block
        private int frees;
        // Description of the first damaged guard byte or null when both zones are intact
        private String damage;

        // Stores the raw region and the block inside it
        private Block(long raw, long address, long bytes, long total) {
            this.raw = raw;
            this.address = address;
            this.bytes = bytes;
            this.total = total;
        }

        // Counts the free and checks the guard zones and releases the raw region on the first free only
        private void free() {
            if (++frees == 1) {
                release();
            }
        }

        // Checks the guard zones and releases the raw region
        private void release() {
            damage = scan(raw, address - raw, "before");
            if (damage == null) {
                damage = scan(address + bytes, raw + total - address - bytes, "after");
            }
            MemoryUtil.nmemFree(raw);
        }

        // Returns a description of the first byte of the zone that is not the guard byte or null when none is
        private String scan(long start, long count, String side) {
            byte[] zone = bytes(start, count);
            for (int i = 0; i < zone.length; i++) {
                if (zone[i] != GUARD_BYTE) {
                    return "guard byte " + i + " " + side + " block " + address + " was written";
                }
            }
            return null;
        }
    }

    // Static mock of MemoryUtil that places every aligned block between two guard zones and counts its frees
    private static final class GuardedMemory implements AutoCloseable {
        // Static mock that routes the aligned allocations and frees
        private final MockedStatic<MemoryUtil> mock = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS);
        // Blocks by address in allocation order
        private final Map<Long, Block> blocks = new LinkedHashMap<>();
        // Flag that makes every aligned allocation return the null address
        private boolean failing;
        // Address of the latest block
        private long last;

        // Routes the aligned allocations and frees of the mock through the guard zones
        private GuardedMemory() {
            mock.when(() -> MemoryUtil.nmemAlignedAlloc(Mockito.anyLong(), Mockito.anyLong())).thenAnswer(inv -> {
                if (!calledFromBatch()) {
                    return inv.callRealMethod();
                }
                return allocate(inv.<Long>getArgument(0), inv.<Long>getArgument(1));
            });
            mock.when(() -> MemoryUtil.nmemAlignedFree(Mockito.anyLong())).thenAnswer(inv -> {
                Block block = blocks.get(inv.<Long>getArgument(0));
                if (block == null) {
                    return inv.callRealMethod();
                }
                block.free();
                return null;
            });
        }

        // Returns whether the batch class is on the stack of the current call
        private static boolean calledFromBatch() {
            String batch = IndirectMultiDrawBatch.class.getName();
            return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(batch)));
        }

        // Returns the null address while failing and otherwise a fresh block between two guard zones
        private long allocate(long alignment, long bytes) {
            if (failing) {
                return MemoryUtil.NULL;
            }
            long total = bytes + 2 * GUARD + alignment;
            long raw = MemoryUtil.nmemAlloc(total);
            if (raw == MemoryUtil.NULL) {
                return MemoryUtil.NULL;
            }
            MemoryUtil.memSet(raw, GUARD_BYTE, total);
            long address = (raw + GUARD + alignment - 1) & -alignment;
            blocks.put(address, new Block(raw, address, bytes, total));
            last = address;
            return address;
        }

        // Releases every block that was never freed and closes the mock and fails on a damaged guard or a free count other than one
        @Override
        public void close() {
            try {
                for (Block block : blocks.values()) {
                    if (block.frees == 0) {
                        block.release();
                    }
                }
            } finally {
                mock.close();
            }
            for (Block block : blocks.values()) {
                assertNull(block.damage);
                assertEquals(1, block.frees, "frees of block " + block.address);
            }
        }
    }
}
