package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

// Tests of the mutable buffer alone and behind a real command list
class GlMutableBufferTest {
    // Message of a negative size
    private static final String NEGATIVE = "Size must not be negative";
    // Message of a zero name from the driver
    private static final String NO_NAME = "Failed to generate a buffer";
    // Message of a handle read after delete
    private static final String INVALID = "Handle is not valid";
    // Message of a write or unmap through a disposed mapping
    private static final String DISPOSED = "Buffer mapping is already disposed";
    // Number of buffers and calls in the bulk tests
    private static final int MANY = 1000;
    // Target that the device binds for storage and mapping
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // Usage hint of every device upload in the tests
    private static final GlBufferUsage USAGE = GlBufferUsage.STREAM_DRAW;
    // Map flags of a write mapping
    private static final EnumBitField<GlBufferMapFlags> WRITE = EnumBitField.of(GlBufferMapFlags.WRITE);

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();
    // Native blocks that the test frees afterwards
    private final List<ByteBuffer> blocks = new ArrayList<>();

    // Frees every native block that the test allocated
    @AfterEach
    void freeBlocks() {
        for (ByteBuffer block : blocks) {
            MemoryUtil.memFree(block);
        }
        blocks.clear();
    }

    // Creates a mutable buffer whose generated name is the given value
    private GlMutableBuffer named(int name) {
        Mockito.doReturn(name).when(gl).glGenBuffers();
        return new GlMutableBuffer();
    }

    // Allocates a zeroed native block that the test frees afterwards
    private ByteBuffer block(int bytes) {
        ByteBuffer block = MemoryUtil.memCalloc(bytes);
        blocks.add(block);
        return block;
    }

    // Opens a mapping over a native block on the buffer and returns it
    private GlBufferMapping open(GlMutableBuffer buffer) {
        GlBufferMapping mapping = new GlBufferMapping(buffer, block(16));
        buffer.setActiveMapping(mapping);
        return mapping;
    }

    // Runs the body with a command list of an active render device and no recorded GL calls
    private void onDevice(Consumer<CommandList> body) {
        GLRenderDevice device = new GLRenderDevice();
        device.makeActive();
        try {
            CommandList list = device.createCommandList();
            Mockito.clearInvocations(gl);
            body.accept(list);
        } finally {
            device.makeInactive();
        }
    }

    // Generates exactly one name with no storage and no mapping and makes no other GL call
    @Test
    void constructorGeneratesOneNameWithNoStorage() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        assertEquals(1, buffer.handle());
        assertEquals(0L, buffer.getSize());
        assertNull(buffer.getActiveMapping());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Rejects a zero name from the driver and deletes nothing
    @Test
    void constructorRejectsAZeroName() {
        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals(NO_NAME, assertThrows(IllegalStateException.class, GlMutableBuffer::new).getMessage());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Passes a driver failure through and creates a working buffer on the next attempt
    @Test
    void constructorRecoversAfterADriverFailure() {
        Mockito.doThrow(new IllegalStateException("No context")).doReturn(4).when(gl).glGenBuffers();
        assertEquals("No context", assertThrows(IllegalStateException.class, GlMutableBuffer::new).getMessage());
        GlMutableBuffer buffer = new GlMutableBuffer();
        assertEquals(4, buffer.handle());
        assertEquals(0L, buffer.getSize());
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(anyInt());
    }

    // Keeps every non zero name from the driver and deletes it by the same value
    @ParameterizedTest
    @ValueSource(ints = {1, 2, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1})
    void constructorKeepsEveryNonZeroName(int name) {
        GlMutableBuffer buffer = named(name);
        assertEquals(name, buffer.handle());
        buffer.setSize(12L);
        buffer.delete();
        Mockito.verify(gl).glDeleteBuffers(name);
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::handle).getMessage());
        assertEquals(0L, buffer.getSize());
    }

    // Returns the stored size on every read without any GL call
    @Test
    void getSizeRepeatsWithoutGlCalls() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        for (int i = 0; i < MANY; i++) {
            assertEquals(0L, buffer.getSize());
        }
        buffer.setSize(24L);
        for (int i = 0; i < MANY; i++) {
            assertEquals(24L, buffer.getSize());
        }
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Stores every non negative size at the byte and int and long limits without any GL call
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 3L, 7L, 8L, 255L, 256L, Integer.MAX_VALUE, 1L << 31, 0xFFFF_FFFFL, 1L << 32, Long.MAX_VALUE - 1L, Long.MAX_VALUE})
    void setSizeStoresEveryNonNegativeSize(long size) {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(size);
        assertEquals(size, buffer.getSize());
        assertEquals(1, buffer.handle());
        assertNull(buffer.getActiveMapping());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Rejects every negative size and keeps the previous size and the open mapping
    @ParameterizedTest
    @ValueSource(longs = {-1L, -8L, Integer.MIN_VALUE, Integer.MIN_VALUE - 1L, Long.MIN_VALUE + 1L, Long.MIN_VALUE})
    void setSizeRejectsEveryNegativeSize(long size) {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(40L);
        GlBufferMapping mapping = open(buffer);
        for (int i = 0; i < 3; i++) {
            assertEquals(NEGATIVE, assertThrows(IllegalArgumentException.class, () -> buffer.setSize(size)).getMessage());
        }
        assertEquals(40L, buffer.getSize());
        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Rejects a byte count that wrapped in int arithmetic and stores the same product computed in long
    @Test
    void setSizeRejectsAWrappedIntByteCount() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        int count = Integer.MAX_VALUE;
        int stride = 2;
        assertEquals(NEGATIVE, assertThrows(IllegalArgumentException.class, () -> buffer.setSize(count * stride)).getMessage());
        assertEquals(0L, buffer.getSize());
        buffer.setSize((long) count * stride);
        assertEquals(0xFFFF_FFFEL, buffer.getSize());
    }

    // Overwrites the size on each call as it grows and shrinks and repeats and returns to zero
    @Test
    void setSizeOverwritesOnEveryCall() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        long[] sizes = {16L, 64L, 64L, 8L, 0L, 0L, Long.MAX_VALUE, 1L, 0L};
        for (long size : sizes) {
            buffer.setSize(size);
            assertEquals(size, buffer.getSize());
        }
        for (int i = 0; i < MANY; i++) {
            buffer.setSize(i * 3L + 1L);
        }
        assertEquals((MANY - 1) * 3L + 1L, buffer.getSize());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Rejects every size after delete and reports the deleted buffer ahead of a negative size
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 64L, Long.MAX_VALUE, -1L, Long.MIN_VALUE})
    void setSizeRejectsUseAfterDelete(long size) {
        GlMutableBuffer buffer = named(6);
        buffer.setSize(64L);
        buffer.delete();
        for (int i = 0; i < 3; i++) {
            assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> buffer.setSize(size)).getMessage());
        }
        assertEquals(0L, buffer.getSize());
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(6);
    }

    // Clears the size on the first delete and makes no GL call on a second delete
    @Test
    void deleteClearsTheSizeOnce() {
        GlMutableBuffer buffer = named(7);
        buffer.setSize(Long.MAX_VALUE);
        buffer.delete();
        assertEquals(0L, buffer.getSize());
        buffer.delete();
        assertEquals(0L, buffer.getSize());
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(7);
    }

    // Clears the size through the deprecated destroy alias like delete
    @Test
    @SuppressWarnings("deprecation")
    void destroyClearsTheSizeLikeDelete() {
        GlMutableBuffer buffer = named(8);
        buffer.setSize(32L);
        buffer.destroy();
        assertEquals(0L, buffer.getSize());
        buffer.destroy();
        buffer.delete();
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(8);
        assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> buffer.setSize(1L)).getMessage());
    }

    // Keeps the size and the handle and the mapping when the driver fails to delete and clears them on the retry
    @Test
    void deleteFailureKeepsTheSizeAndTheMapping() {
        GlMutableBuffer buffer = named(9);
        buffer.setSize(48L);
        GlBufferMapping mapping = open(buffer);
        Mockito.doThrow(new IllegalStateException("Lost context")).doNothing().when(gl).glDeleteBuffers(9);
        assertEquals("Lost context", assertThrows(IllegalStateException.class, buffer::delete).getMessage());
        assertEquals(48L, buffer.getSize());
        assertEquals(9, buffer.handle());
        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        buffer.delete();
        assertEquals(0L, buffer.getSize());
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        Mockito.verify(gl, Mockito.times(2)).glDeleteBuffers(9);
    }

    // Clears the size and disposes the open mapping on delete and refuses a write through the mapping
    @Test
    void deleteClearsTheSizeAndDisposesTheMapping() {
        GlMutableBuffer buffer = named(10);
        buffer.setSize(16L);
        GlBufferMapping mapping = open(buffer);
        buffer.delete();
        assertEquals(0L, buffer.getSize());
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(block(1), 0)).getMessage());
    }

    // Clears and disposes the open mapping on new storage and accepts a new mapping afterwards
    @Test
    void setSizeDisposesTheOpenMapping() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(16L);
        GlBufferMapping first = open(buffer);
        buffer.setSize(32L);
        assertEquals(32L, buffer.getSize());
        assertNull(buffer.getActiveMapping());
        assertTrue(first.isDisposed());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> first.write(block(4), 0)).getMessage());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(first)).getMessage());
        GlBufferMapping second = open(buffer);
        assertSame(second, buffer.getActiveMapping());
        assertFalse(second.isDisposed());
        buffer.setSize(8L);
        assertTrue(second.isDisposed());
        assertNull(buffer.getActiveMapping());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Disposes the open mapping on new storage of the same size and of zero size
    @ParameterizedTest
    @ValueSource(longs = {0L, 16L})
    void setSizeDisposesTheMappingAtAnyValidSize(long size) {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(16L);
        GlBufferMapping mapping = open(buffer);
        buffer.setSize(size);
        assertEquals(size, buffer.getSize());
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
    }

    // Clears the mapping and stores the size before a dispose that throws
    @Test
    void setSizeClearsTheMappingBeforeAThrowingDispose() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlBufferMapping mapping = new GlBufferMapping(buffer, block(8)) {
            // Throws instead of marking the mapping disposed
            @Override
            public void dispose() {
                throw new IllegalStateException("Dispose failed");
            }
        };
        buffer.setActiveMapping(mapping);
        assertEquals("Dispose failed", assertThrows(IllegalStateException.class, () -> buffer.setSize(8L)).getMessage());
        assertEquals(8L, buffer.getSize());
        assertNull(buffer.getActiveMapping());
    }

    // Keeps the size of each of many buffers at misaligned values and deletes each name once in order
    @Test
    void manyBuffersKeepTheirOwnSizes() {
        List<GlMutableBuffer> buffers = new ArrayList<>();
        List<Integer> names = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();
        for (int i = 0; i < MANY; i++) {
            GlMutableBuffer buffer = new GlMutableBuffer();
            buffer.setSize(i * 3L + 1L);
            buffers.add(buffer);
            names.add(buffer.handle());
            assertTrue(unique.add(buffer.handle()));
        }
        for (int i = 0; i < MANY; i++) {
            GlMutableBuffer buffer = buffers.get(i);
            assertEquals(i * 3L + 1L, buffer.getSize());
            buffer.delete();
            assertEquals(0L, buffer.getSize());
        }
        ArgumentCaptor<Integer> deleted = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(gl, Mockito.times(MANY)).glDeleteBuffers(deleted.capture());
        assertEquals(names, deleted.getAllValues());
    }

    // Binds the buffer and allocates and records storage of the exact size at the int and long limits
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 7L, Integer.MAX_VALUE, 1L << 31, 1L << 32, Long.MAX_VALUE})
    void commandListRecordsTheAllocatedSize(long size) {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            int name = buffer.handle();
            list.allocateStorage(buffer, size, USAGE);
            assertEquals(size, buffer.getSize());
            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glGenBuffers();
            order.verify(gl).glBindBuffer(ARRAY, name);
            order.verify(gl).glBufferData(ARRAY, size, USAGE.getId());
            Mockito.verify(gl, Mockito.times(1)).glBufferData(anyInt(), anyLong(), anyInt());
        });
    }

    // Records the remaining bytes of an empty and a single byte and a misaligned upload
    @Test
    void commandListRecordsTheRemainingBytesOfAnUpload() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            ByteBuffer empty = block(4).limit(0);
            list.uploadData(buffer, empty, USAGE);
            assertEquals(0L, buffer.getSize());
            ByteBuffer one = block(1);
            list.uploadData(buffer, one, USAGE);
            assertEquals(1L, buffer.getSize());
            ByteBuffer misaligned = block(16).position(3).limit(10);
            list.uploadData(buffer, misaligned, USAGE);
            assertEquals(7L, buffer.getSize());
            assertEquals(3, misaligned.position());
            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glBufferData(ARRAY, empty, USAGE.getId());
            order.verify(gl).glBufferData(ARRAY, one, USAGE.getId());
            order.verify(gl).glBufferData(ARRAY, misaligned, USAGE.getId());
        });
    }

    // Records the byte count of a pointer upload at the int and long limits
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 13L, Integer.MAX_VALUE, 1L << 32, Long.MAX_VALUE})
    void commandListRecordsTheBytesOfAPointerUpload(long bytes) {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            list.uploadData(buffer, 0x1000L, bytes, USAGE);
            assertEquals(bytes, buffer.getSize());
            Mockito.verify(gl).glBufferData(ARRAY, bytes, 0x1000L, USAGE.getId());
        });
    }

    // Rejects a negative byte count from each device path and keeps the recorded size and mapping
    @ParameterizedTest
    @ValueSource(longs = {-1L, Integer.MIN_VALUE, Long.MIN_VALUE})
    void commandListRejectsANegativeSize(long bytes) {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            list.allocateStorage(buffer, 24L, USAGE);
            GlBufferMapping mapping = list.mapBuffer(buffer, 0L, 24L, WRITE);
            assertEquals(NEGATIVE, assertThrows(IllegalArgumentException.class, () -> list.allocateStorage(buffer, bytes, USAGE)).getMessage());
            assertEquals(NEGATIVE, assertThrows(IllegalArgumentException.class, () -> list.uploadData(buffer, 0x1000L, bytes, USAGE)).getMessage());
            assertEquals(24L, buffer.getSize());
            assertSame(mapping, buffer.getActiveMapping());
            assertFalse(mapping.isDisposed());
            list.unmap(mapping);
            assertTrue(mapping.isDisposed());
        });
    }

    // Disposes the mapping that a reallocation unmaps and maps the new storage afterwards
    @Test
    void commandListReallocationDisposesTheMapping() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            list.allocateStorage(buffer, 8L, USAGE);
            GlBufferMapping first = list.mapBuffer(buffer, 0L, 8L, WRITE);
            list.allocateStorage(buffer, 32L, USAGE);
            assertTrue(first.isDisposed());
            assertNull(buffer.getActiveMapping());
            assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> list.unmap(first)).getMessage());
            GlBufferMapping second = list.mapBuffer(buffer, 0L, 32L, WRITE);
            assertNotSame(first, second);
            list.uploadData(buffer, block(4), USAGE);
            assertTrue(second.isDisposed());
            assertEquals(4L, buffer.getSize());
            GlBufferMapping third = list.mapBuffer(buffer, 0L, 4L, WRITE);
            list.uploadData(buffer, 0x1000L, 2L, USAGE);
            assertTrue(third.isDisposed());
            assertNull(buffer.getActiveMapping());
            Mockito.verify(gl, Mockito.never()).glUnmapBuffer(anyInt());
        });
    }

    // Clears the size on delete and refuses later storage from each device path before any storage call
    @Test
    void commandListDeleteClearsTheSizeAndRefusesLaterStorage() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            list.allocateStorage(buffer, 96L, USAGE);
            list.deleteBuffer(buffer);
            assertEquals(0L, buffer.getSize());
            Mockito.clearInvocations(gl);
            assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> list.allocateStorage(buffer, 8L, USAGE)).getMessage());
            assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> list.uploadData(buffer, block(8), USAGE)).getMessage());
            assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> list.uploadData(buffer, 0x1000L, 8L, USAGE)).getMessage());
            assertEquals(0L, buffer.getSize());
            Mockito.verify(gl, Mockito.never()).glBufferData(anyInt(), anyLong(), anyInt());
            Mockito.verify(gl, Mockito.never()).glBufferData(anyInt(), any(ByteBuffer.class), anyInt());
            Mockito.verify(gl, Mockito.never()).glBufferData(anyInt(), anyLong(), anyLong(), anyInt());
        });
    }

    // Keeps the public class with one public constructor and the public size accessors over one private field
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        Class<GlMutableBuffer> type = GlMutableBuffer.class;
        assertEquals(Modifier.PUBLIC, type.getModifiers());
        assertSame(GlBuffer.class, type.getSuperclass());

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PUBLIC, constructors[0].getModifiers());
        assertArrayEquals(new Class<?>[0], constructors[0].getParameterTypes());

        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                fields.add(field.getName());
                assertEquals(Modifier.PRIVATE, field.getModifiers(), field.getName());
                assertSame(long.class, field.getType());
            }
        }
        assertEquals(List.of("size"), fields);

        Set<String> methods = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
                methods.add(method.getName());
            }
        }
        assertEquals(Set.of("setSize", "getSize", "sizeError", "destroyInternal"), methods);

        Method setter = type.getDeclaredMethod("setSize", long.class);
        assertEquals(Modifier.PUBLIC, setter.getModifiers());
        assertSame(void.class, setter.getReturnType());
        Method getter = type.getDeclaredMethod("getSize");
        assertEquals(Modifier.PUBLIC, getter.getModifiers());
        assertSame(long.class, getter.getReturnType());
        assertEquals(Modifier.PROTECTED, type.getDeclaredMethod("destroyInternal").getModifiers());
        assertEquals(Modifier.PRIVATE, type.getDeclaredMethod("sizeError").getModifiers());
    }
}
