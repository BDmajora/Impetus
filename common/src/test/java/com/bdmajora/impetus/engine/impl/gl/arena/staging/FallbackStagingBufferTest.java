package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

// Tests of the fallback staging buffer against a mock command list and the test render device
class FallbackStagingBufferTest {
    // Usage hint of every scratch upload and release
    private static final GlBufferUsage USAGE = GlBufferUsage.STREAM_COPY;
    // Native allocations freed after each test
    private final List<ByteBuffer> allocations = new ArrayList<>();
    // Command list mock that records every call of the staging buffer
    private CommandList commands;
    // Scratch buffer the mock command list hands out
    private GlMutableBuffer scratch;
    // Destination buffer of the copies
    private GlMutableBuffer dst;

    // Creates the mock command list and the scratch and destination buffers
    @BeforeEach
    void createCommandList() {
        scratch = new GlMutableBuffer();
        dst = new GlMutableBuffer();
        commands = Mockito.mock(CommandList.class);
        Mockito.when(commands.createMutableBuffer()).thenReturn(scratch);
    }

    // Frees the native allocations of the test
    @AfterEach
    void freeAllocations() {
        for (ByteBuffer allocation : allocations) {
            MemoryUtil.memFree(allocation);
        }
        allocations.clear();
    }

    // Creates the staging buffer under test and clears the construction call from the mock
    private FallbackStagingBuffer staging() {
        FallbackStagingBuffer staging = new FallbackStagingBuffer(commands);
        Mockito.clearInvocations(commands);
        return staging;
    }

    // Allocates zeroed native memory that is freed after the test
    private ByteBuffer alloc(int bytes) {
        ByteBuffer buffer = MemoryUtil.memCalloc(bytes);
        allocations.add(buffer);
        return buffer;
    }

    // Verifies one upload of the data into the scratch buffer
    private void verifyUpload(ByteBuffer data) {
        Mockito.verify(commands).uploadData(same(scratch), same(data), eq(USAGE));
    }

    // Verifies one copy from the start of the scratch buffer into the destination
    private void verifyCopy(long writeOffset, long bytes) {
        Mockito.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(writeOffset), eq(bytes));
    }

    // Runs the body against a spy of the command list of the active render device
    private static void onDevice(Consumer<CommandList> body) {
        GLRenderDevice device = Devices.active();
        try {
            body.accept(Mockito.spy(device.createCommandList()));
        } finally {
            device.makeInactive();
        }
    }

    // Checks that the class implements the staging buffer interface
    @Test
    void implementsStagingBuffer() {
        assertInstanceOf(StagingBuffer.class, staging());
    }

    // Checks that construction creates one scratch buffer and makes no other call
    @Test
    void constructorCreatesOneScratchBuffer() {
        new FallbackStagingBuffer(commands);
        Mockito.verify(commands).createMutableBuffer();
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that construction rejects a null command list
    @Test
    void constructorRejectsNullCommandList() {
        assertThrows(NullPointerException.class, () -> new FallbackStagingBuffer(null));
    }

    // Checks that each instance uploads through its own scratch buffer
    @Test
    void constructorGivesEachInstanceItsOwnScratchBuffer() {
        GlMutableBuffer second = new GlMutableBuffer();
        Mockito.when(commands.createMutableBuffer()).thenReturn(scratch, second);
        FallbackStagingBuffer first = new FallbackStagingBuffer(commands);
        FallbackStagingBuffer other = new FallbackStagingBuffer(commands);
        ByteBuffer data = alloc(4);
        first.enqueueCopy(commands, data, dst, 0);
        other.enqueueCopy(commands, data, dst, 0);
        Mockito.verify(commands).uploadData(same(scratch), same(data), eq(USAGE));
        Mockito.verify(commands).uploadData(same(second), same(data), eq(USAGE));
        Mockito.verify(commands).copyBufferSubData(same(second), same(dst), eq(0L), eq(0L), eq(4L));
    }

    // Checks that a copy uploads the data into the scratch buffer before it copies every byte into the destination
    @Test
    void enqueueCopyUploadsThenCopies() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        staging.enqueueCopy(commands, data, dst, 16);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).uploadData(same(scratch), same(data), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(16L), eq(8L));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a single byte is uploaded and copied
    @Test
    void enqueueCopyMovesASingleByte() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(1);
        staging.enqueueCopy(commands, data, dst, 0);
        verifyUpload(data);
        verifyCopy(0, 1);
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that empty or drained data makes no call and leaves nothing for flush to release
    @Test
    void enqueueCopySkipsEmptyData() {
        FallbackStagingBuffer staging = staging();
        staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, 0);
        ByteBuffer drained = alloc(8);
        drained.position(8);
        staging.enqueueCopy(commands, drained, dst, 64);
        staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, Long.MAX_VALUE);
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        assertEquals(8, drained.position());
    }

    // Checks that only the bytes between position and limit are copied and that the bounds of the data stay unchanged
    @Test
    void enqueueCopyUsesRemainingBytesAndKeepsBounds() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(16);
        data.position(3).limit(10);
        staging.enqueueCopy(commands, data, dst, 5);
        verifyUpload(data);
        verifyCopy(5, 7);
        assertEquals(3, data.position());
        assertEquals(10, data.limit());
        assertEquals(16, data.capacity());
    }

    // Checks that data at an odd native address and odd write offsets pass through unchanged
    @Test
    void enqueueCopyPassesMisalignedDataAndOffsetsThrough() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer block = alloc(16);
        ByteBuffer odd = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + 1, 15);
        assertEquals(1L, MemoryUtil.memAddress(odd) & 7L);
        long[] offsets = {1, 3, 7, 4095, 4097};
        for (long offset : offsets) {
            staging.enqueueCopy(commands, odd, dst, offset);
            verifyCopy(offset, 15);
        }
        Mockito.verify(commands, Mockito.times(offsets.length)).uploadData(same(scratch), same(odd), eq(USAGE));
        assertEquals(0, odd.position());
    }

    // Checks that write offsets past the int limit reach the copy without truncation
    @Test
    void enqueueCopyKeepsWriteOffsetsPastTheIntLimit() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        staging.enqueueCopy(commands, data, dst, Integer.MAX_VALUE + 1L);
        staging.enqueueCopy(commands, data, dst, 1L << 40);
        verifyCopy(Integer.MAX_VALUE + 1L, 8);
        verifyCopy(1L << 40, 8);
    }

    // Checks that negative write offsets are rejected before any call for empty and filled data
    @Test
    void enqueueCopyRejectsNegativeWriteOffsets() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        ByteBuffer data = alloc(8);
        for (long offset : new long[]{-1, -8, Integer.MIN_VALUE, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, empty, dst, offset));
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, offset));
        }
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that a write end exactly at the long limit is accepted
    @Test
    void enqueueCopyAcceptsAWriteEndAtTheLongLimit() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        staging.enqueueCopy(commands, data, dst, Long.MAX_VALUE - 8);
        verifyCopy(Long.MAX_VALUE - 8, 8);
        ByteBuffer one = alloc(1);
        staging.enqueueCopy(commands, one, dst, Long.MAX_VALUE - 1);
        verifyCopy(Long.MAX_VALUE - 1, 1);
    }

    // Checks that a write end past the long limit is rejected before any call
    @Test
    void enqueueCopyRejectsAWriteEndPastTheLongLimit() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        ByteBuffer one = alloc(1);
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, Long.MAX_VALUE - 7));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, one, dst, Long.MAX_VALUE));
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that data of the largest int length is copied in full and bounded by the long limit
    @Test
    void enqueueCopyHandlesTheLargestIntLength() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer block = alloc(1);
        ByteBuffer huge = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block), Integer.MAX_VALUE);
        long lastStart = Long.MAX_VALUE - Integer.MAX_VALUE;
        staging.enqueueCopy(commands, huge, dst, lastStart);
        verifyUpload(huge);
        verifyCopy(lastStart, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, lastStart + 1));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, Long.MIN_VALUE));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that null arguments are rejected before any call
    @Test
    void enqueueCopyRejectsNullArguments() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, data, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, null, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, data, null, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, empty, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, empty, null, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, null, null, -1));
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that heap data is rejected before any call
    @Test
    void enqueueCopyRejectsHeapData() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer[] heaps = {ByteBuffer.allocate(8), ByteBuffer.allocate(0), ByteBuffer.wrap(new byte[4], 1, 2), ByteBuffer.allocate(4).asReadOnlyBuffer()};
        for (ByteBuffer heap : heaps) {
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, heap, dst, 0));
        }
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that read only direct data is uploaded and copied
    @Test
    void enqueueCopyAcceptsReadOnlyDirectData() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(4).asReadOnlyBuffer();
        staging.enqueueCopy(commands, data, dst, 12);
        verifyUpload(data);
        verifyCopy(12, 4);
    }

    // Checks that repeated copies go through the same scratch buffer in call order
    @Test
    void enqueueCopyRepeatsInOrderThroughOneScratchBuffer() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer first = alloc(8);
        ByteBuffer second = alloc(4);
        ByteBuffer third = alloc(16);
        staging.enqueueCopy(commands, first, dst, 0);
        staging.enqueueCopy(commands, second, dst, 8);
        staging.enqueueCopy(commands, third, dst, 100);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).uploadData(same(scratch), same(first), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(0L), eq(8L));
        order.verify(commands).uploadData(same(scratch), same(second), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(8L), eq(4L));
        order.verify(commands).uploadData(same(scratch), same(third), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(100L), eq(16L));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a failed upload propagates without a copy and leaves storage for flush to release
    @Test
    void enqueueCopyFailedUploadLeavesStorageForFlush() {
        FallbackStagingBuffer staging = staging();
        IllegalStateException failure = new IllegalStateException("upload");
        Mockito.doThrow(failure).when(commands).uploadData(any(GlMutableBuffer.class), any(ByteBuffer.class), any(GlBufferUsage.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, alloc(8), dst, 0)));
        Mockito.verify(commands, Mockito.never()).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that a failed copy propagates and leaves storage for flush to release
    @Test
    void enqueueCopyFailedCopyLeavesStorageForFlush() {
        FallbackStagingBuffer staging = staging();
        IllegalStateException failure = new IllegalStateException("copy");
        Mockito.doThrow(failure).when(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        ByteBuffer data = alloc(8);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data, dst, 0)));
        verifyUpload(data);
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that copies after delete are rejected before any call
    @Test
    void enqueueCopyRejectsUseAfterDelete() {
        FallbackStagingBuffer staging = staging();
        staging.delete(commands);
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, alloc(8), dst, 0));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, 0));
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flush on a fresh buffer makes no call
    @Test
    void flushWithoutStorageMakesNoCall() {
        FallbackStagingBuffer staging = staging();
        staging.flush(commands);
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flush releases the storage once and that repeated flushes make no further call
    @Test
    void flushReleasesStorageOnce() {
        FallbackStagingBuffer staging = staging();
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        staging.flush(commands);
        staging.flush(commands);
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that every flush after new data releases the storage again
    @Test
    void flushReleasesAfterEachNewUpload() {
        FallbackStagingBuffer staging = staging();
        ByteBuffer data = alloc(8);
        for (int round = 1; round <= 3; round++) {
            staging.enqueueCopy(commands, data, dst, 0);
            staging.enqueueCopy(commands, data, dst, 8);
            staging.flush(commands);
            Mockito.verify(commands, Mockito.times(round)).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        }
    }

    // Checks that flush rejects a null command list with or without held storage
    @Test
    void flushRejectsNullCommandList() {
        FallbackStagingBuffer staging = staging();
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        Mockito.verify(commands, Mockito.never()).allocateStorage(any(), anyLong(), any());
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that flush after delete is rejected before any call with or without held storage
    @Test
    void flushRejectsUseAfterDelete() {
        FallbackStagingBuffer fresh = staging();
        fresh.delete(commands);
        FallbackStagingBuffer holding = staging();
        holding.enqueueCopy(commands, alloc(8), dst, 0);
        holding.delete(commands);
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> fresh.flush(commands));
        assertThrows(IllegalStateException.class, () -> holding.flush(commands));
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that a failed release propagates and that the next flush retries it
    @Test
    void flushRetriesAfterAFailedRelease() {
        FallbackStagingBuffer staging = staging();
        IllegalStateException failure = new IllegalStateException("release");
        Mockito.doThrow(failure).doNothing().when(commands).allocateStorage(any(), anyLong(), any());
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.flush(commands)));
        staging.flush(commands);
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that delete frees the scratch buffer through the command list
    @Test
    void deleteFreesScratchBuffer() {
        FallbackStagingBuffer staging = staging();
        staging.delete(commands);
        Mockito.verify(commands).deleteBuffer(same(scratch));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that repeated deletes free the scratch buffer once
    @Test
    void deleteFreesOnceOnRepeatedCalls() {
        FallbackStagingBuffer staging = staging();
        staging.delete(commands);
        staging.delete(commands);
        staging.delete(commands);
        Mockito.verify(commands).deleteBuffer(same(scratch));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that delete rejects a null command list before and after the scratch buffer is freed
    @Test
    void deleteRejectsNullCommandList() {
        FallbackStagingBuffer staging = staging();
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        Mockito.verifyNoInteractions(commands);
        staging.delete(commands);
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        Mockito.verify(commands).deleteBuffer(same(scratch));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that delete with held storage frees the buffer without a separate release
    @Test
    void deleteWithHeldStorageOnlyFreesTheBuffer() {
        FallbackStagingBuffer staging = staging();
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        Mockito.clearInvocations(commands);
        staging.delete(commands);
        Mockito.verify(commands).deleteBuffer(same(scratch));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a failed delete propagates and leaves the buffer usable until a later delete succeeds
    @Test
    void deleteRetriesAfterAFailure() {
        FallbackStagingBuffer staging = staging();
        IllegalStateException failure = new IllegalStateException("delete");
        Mockito.doThrow(failure).doNothing().when(commands).deleteBuffer(any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.delete(commands)));
        ByteBuffer data = alloc(4);
        staging.enqueueCopy(commands, data, dst, 0);
        verifyCopy(0, 4);
        staging.delete(commands);
        staging.delete(commands);
        Mockito.verify(commands, Mockito.times(2)).deleteBuffer(same(scratch));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data, dst, 0));
    }

    // Checks that flip makes no call in any state and keeps held storage for flush
    @Test
    void flipMakesNoCall() {
        FallbackStagingBuffer staging = staging();
        staging.flip();
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        Mockito.clearInvocations(commands);
        staging.flip();
        staging.flip();
        Mockito.verifyNoInteractions(commands);
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        staging.delete(commands);
        Mockito.clearInvocations(commands);
        staging.flip();
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that the label stays the same instance in every state
    @Test
    void toStringReturnsTheSameLabelInEveryState() {
        FallbackStagingBuffer staging = staging();
        String label = staging.toString();
        assertEquals("Fallback", label);
        staging.enqueueCopy(commands, alloc(8), dst, 0);
        assertSame(label, staging.toString());
        staging.delete(commands);
        assertSame(label, staging.toString());
    }

    // Checks the buffer data and copy calls and the scratch size on the test render device
    @Test
    void enqueueCopyAndFlushOnTheDevice() {
        onDevice(list -> {
            FallbackStagingBuffer staging = new FallbackStagingBuffer(list);
            GlMutableBuffer target = list.createMutableBuffer();
            ByteBuffer data = alloc(24);
            data.position(4);
            staging.enqueueCopy(list, data, target, 40);
            ArgumentCaptor<GlMutableBuffer> used = ArgumentCaptor.forClass(GlMutableBuffer.class);
            Mockito.verify(list).uploadData(used.capture(), same(data), eq(USAGE));
            GlMutableBuffer scratchOnDevice = used.getValue();
            assertNotSame(target, scratchOnDevice);
            assertEquals(20L, scratchOnDevice.getSize());
            assertEquals(4, data.position());
            Mockito.verify(TestGl.gl()).glBufferData(eq(GlBufferTarget.ARRAY_BUFFER.getTargetParameter()), same(data), eq(USAGE.getId()));
            Mockito.verify(TestGl.gl()).glCopyBufferSubData(GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), GlBufferTarget.COPY_WRITE_BUFFER.getTargetParameter(), 0L, 40L, 20L);
            staging.flush(list);
            staging.flush(list);
            assertEquals(0L, scratchOnDevice.getSize());
            Mockito.verify(TestGl.gl()).glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 0L, USAGE.getId());
            list.deleteBuffer(target);
        });
    }

    // Checks that delete frees the scratch name once and that later use is rejected on the test render device
    @Test
    void deleteAndUseAfterDeleteOnTheDevice() {
        onDevice(list -> {
            FallbackStagingBuffer staging = new FallbackStagingBuffer(list);
            GlMutableBuffer target = list.createMutableBuffer();
            ByteBuffer data = alloc(8);
            staging.enqueueCopy(list, data, target, 0);
            ArgumentCaptor<GlMutableBuffer> used = ArgumentCaptor.forClass(GlMutableBuffer.class);
            Mockito.verify(list).uploadData(used.capture(), same(data), eq(USAGE));
            int name = used.getValue().handle();
            staging.delete(list);
            staging.delete(list);
            Mockito.verify(TestGl.gl()).glDeleteBuffers(name);
            assertThrows(IllegalStateException.class, used.getValue()::handle);
            assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(list, data, target, 0));
            assertThrows(IllegalStateException.class, () -> staging.flush(list));
            Mockito.verify(TestGl.gl()).glBufferData(eq(GlBufferTarget.ARRAY_BUFFER.getTargetParameter()), any(ByteBuffer.class), Mockito.anyInt());
            list.deleteBuffer(target);
        });
    }
}
