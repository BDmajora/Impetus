package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

// Tests of the immutable buffer alone and behind a real command list
class GlImmutableBufferTest {
    // Message of a null flag set
    private static final String NO_FLAGS = "Flags must not be null";
    // Message of a zero name from the driver
    private static final String NO_NAME = "Failed to generate a buffer";
    // Message of a handle read after delete
    private static final String INVALID = "Handle is not valid";
    // Message of a write through a disposed mapping
    private static final String DISPOSED = "Buffer mapping is already disposed";
    // Message of a persistent map request on storage without the persistent flag
    private static final String NOT_PERSISTENT = "Tried to map non-persistent buffer as persistent";
    // Message of a write map request on storage without the map write flag
    private static final String NOT_WRITABLE = "Tried to map non-writable buffer as writable";
    // Message of a read map request on storage without the map read flag
    private static final String NOT_READABLE = "Tried to map non-readable buffer as readable";
    // Every storage flag in declaration order
    private static final GlBufferStorageFlags[] ALL = GlBufferStorageFlags.values();
    // Number of buffers in the bulk test
    private static final int MANY = 1000;
    // Target that the device binds for storage and mapping
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // Flag set of a persistent write buffer
    private static final EnumBitField<GlBufferStorageFlags> PERSISTENT_WRITE = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT);

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

    // Returns every non empty subset mask of the storage flags
    static IntStream masks() {
        return IntStream.range(1, 1 << ALL.length);
    }

    // Returns the flag set of every storage flag whose bit is set in the mask
    private static EnumBitField<GlBufferStorageFlags> subset(int mask) {
        List<GlBufferStorageFlags> picked = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) {
            if ((mask >>> i & 1) != 0) {
                picked.add(ALL[i]);
            }
        }
        return EnumBitField.of(picked.toArray(GlBufferStorageFlags[]::new));
    }

    // Returns the map flags that the read and write and persistent bits of the request select
    private static EnumBitField<GlBufferMapFlags> request(int bits) {
        List<GlBufferMapFlags> picked = new ArrayList<>();
        if ((bits & 1) != 0) {
            picked.add(GlBufferMapFlags.READ);
        }
        if ((bits & 2) != 0) {
            picked.add(GlBufferMapFlags.WRITE);
        }
        if ((bits & 4) != 0) {
            picked.add(GlBufferMapFlags.PERSISTENT);
        }
        return EnumBitField.of(picked.toArray(GlBufferMapFlags[]::new));
    }

    // Creates an immutable buffer whose generated name is the given value
    private GlImmutableBuffer named(int name, EnumBitField<GlBufferStorageFlags> flags) {
        Mockito.doReturn(name).when(gl).glGenBuffers();
        return new GlImmutableBuffer(flags);
    }

    // Allocates a zeroed native block that the test frees afterwards
    private ByteBuffer block(int bytes) {
        ByteBuffer block = MemoryUtil.memCalloc(bytes);
        blocks.add(block);
        return block;
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

    // Generates exactly one name and stores the flags and makes no other GL call
    @Test
    void constructorGeneratesOneNameAndStoresTheFlags() {
        EnumBitField<GlBufferStorageFlags> flags = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE);
        GlImmutableBuffer buffer = new GlImmutableBuffer(flags);
        assertEquals(1, buffer.handle());
        assertSame(flags, buffer.getFlags());
        assertNull(buffer.getActiveMapping());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Rejects null flags on every attempt before any GL call and leaves the next name unused
    @Test
    void constructorRejectsNullFlagsBeforeAnyGlCall() {
        for (int i = 0; i < 3; i++) {
            assertEquals(NO_FLAGS, assertThrows(NullPointerException.class, () -> new GlImmutableBuffer(null)).getMessage());
        }
        Mockito.verifyNoInteractions(gl);
        assertEquals(1, new GlImmutableBuffer(EnumBitField.of(GlBufferStorageFlags.MAP_READ)).handle());
    }

    // Rejects null flags ahead of a driver that returns a zero name or throws
    @Test
    void constructorRejectsNullFlagsAheadOfADriverFailure() {
        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals(NO_FLAGS, assertThrows(NullPointerException.class, () -> new GlImmutableBuffer(null)).getMessage());
        Mockito.doThrow(new IllegalStateException("No context")).when(gl).glGenBuffers();
        assertEquals(NO_FLAGS, assertThrows(NullPointerException.class, () -> new GlImmutableBuffer(null)).getMessage());
        Mockito.verify(gl, Mockito.never()).glGenBuffers();
    }

    // Rejects an empty flag set while the argument is built and generates no name
    @Test
    void constructorNeverSeesAnEmptyFlagSet() {
        assertThrows(IllegalArgumentException.class, () -> new GlImmutableBuffer(EnumBitField.of()));
        Mockito.verifyNoInteractions(gl);
    }

    // Rejects a zero name from the driver and deletes nothing
    @Test
    void constructorRejectsAZeroName() {
        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals(NO_NAME, assertThrows(IllegalStateException.class, () -> new GlImmutableBuffer(PERSISTENT_WRITE)).getMessage());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Passes a driver failure through and creates a working buffer on the next attempt
    @Test
    void constructorRecoversAfterADriverFailure() {
        Mockito.doThrow(new IllegalStateException("No context")).doReturn(4).when(gl).glGenBuffers();
        assertEquals("No context", assertThrows(IllegalStateException.class, () -> new GlImmutableBuffer(PERSISTENT_WRITE)).getMessage());
        GlImmutableBuffer buffer = new GlImmutableBuffer(PERSISTENT_WRITE);
        assertEquals(4, buffer.handle());
        assertSame(PERSISTENT_WRITE, buffer.getFlags());
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(anyInt());
    }

    // Keeps every non zero name from the driver and deletes it by the same value
    @ParameterizedTest
    @ValueSource(ints = {1, 2, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1})
    void constructorKeepsEveryNonZeroName(int name) {
        GlImmutableBuffer buffer = named(name, PERSISTENT_WRITE);
        assertEquals(name, buffer.handle());
        buffer.delete();
        Mockito.verify(gl).glDeleteBuffers(name);
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::handle).getMessage());
        assertSame(PERSISTENT_WRITE, buffer.getFlags());
    }

    // Returns every storage flag subset with its members and bits unchanged
    @ParameterizedTest
    @MethodSource("masks")
    void getFlagsReturnsEverySubsetUnchanged(int mask) {
        EnumBitField<GlBufferStorageFlags> flags = subset(mask);
        GlImmutableBuffer buffer = new GlImmutableBuffer(flags);
        assertSame(flags, buffer.getFlags());
        int bits = 0;
        for (int i = 0; i < ALL.length; i++) {
            boolean member = (mask >>> i & 1) != 0;
            assertEquals(member, buffer.getFlags().contains(ALL[i]), ALL[i].name());
            if (member) {
                bits |= ALL[i].getBits();
            }
        }
        assertEquals(bits, buffer.getFlags().getBitField());
    }

    // Returns the stored instance on every read without any GL call
    @Test
    void getFlagsRepeatsWithoutGlCalls() {
        GlImmutableBuffer buffer = new GlImmutableBuffer(PERSISTENT_WRITE);
        for (int i = 0; i < MANY; i++) {
            assertSame(PERSISTENT_WRITE, buffer.getFlags());
        }
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Keeps the exact instance of each of two equal flag sets and shares one instance between two buffers
    @Test
    void getFlagsKeepsTheGivenInstance() {
        EnumBitField<GlBufferStorageFlags> first = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT);
        EnumBitField<GlBufferStorageFlags> second = EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.MAP_WRITE);
        GlImmutableBuffer a = new GlImmutableBuffer(first);
        GlImmutableBuffer b = new GlImmutableBuffer(second);
        GlImmutableBuffer c = new GlImmutableBuffer(first);
        assertSame(first, a.getFlags());
        assertSame(second, b.getFlags());
        assertSame(first, c.getFlags());
        assertNotSame(a.getFlags(), b.getFlags());
        assertEquals(a.getFlags().getBitField(), b.getFlags().getBitField());
    }

    // Returns the flags after delete and destroy while the handle refuses use after delete
    @Test
    @SuppressWarnings("deprecation")
    void getFlagsSurvivesDelete() {
        GlImmutableBuffer buffer = named(9, PERSISTENT_WRITE);
        buffer.delete();
        assertSame(PERSISTENT_WRITE, buffer.getFlags());
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::handle).getMessage());
        buffer.delete();
        buffer.destroy();
        assertSame(PERSISTENT_WRITE, buffer.getFlags());
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(9);
    }

    // Disposes the open mapping on delete and refuses a write through it and a new mapping afterwards
    @Test
    void deleteDisposesTheOpenMapping() {
        GlImmutableBuffer buffer = named(11, PERSISTENT_WRITE);
        GlBufferMapping mapping = new GlBufferMapping(buffer, block(16));
        buffer.setActiveMapping(mapping);
        buffer.delete();
        Mockito.verify(gl).glDeleteBuffers(11);
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(block(1), 0)).getMessage());
        GlBufferMapping late = new GlBufferMapping(buffer, block(16));
        assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(late)).getMessage());
        assertNull(buffer.getActiveMapping());
        assertSame(PERSISTENT_WRITE, buffer.getFlags());
    }

    // Keeps the name and flags of each of many buffers and deletes each name once in order
    @Test
    void manyBuffersKeepTheirOwnFlagsAndNames() {
        List<GlImmutableBuffer> buffers = new ArrayList<>();
        List<EnumBitField<GlBufferStorageFlags>> flags = new ArrayList<>();
        List<Integer> names = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();
        for (int i = 0; i < MANY; i++) {
            EnumBitField<GlBufferStorageFlags> set = subset(1 + i % ((1 << ALL.length) - 1));
            GlImmutableBuffer buffer = new GlImmutableBuffer(set);
            buffers.add(buffer);
            flags.add(set);
            names.add(buffer.handle());
            assertTrue(unique.add(buffer.handle()));
        }
        for (int i = 0; i < MANY; i++) {
            assertSame(flags.get(i), buffers.get(i).getFlags());
            buffers.get(i).delete();
        }
        ArgumentCaptor<Integer> deleted = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(gl, Mockito.times(MANY)).glDeleteBuffers(deleted.capture());
        assertEquals(names, deleted.getAllValues());
    }

    // Rejects null flags on the command list before any name or bind or storage call
    @Test
    void commandListRejectsNullFlagsBeforeAnyGlCall() {
        onDevice(list -> {
            assertEquals(NO_FLAGS, assertThrows(NullPointerException.class, () -> list.createImmutableBuffer(64L, null)).getMessage());
            Mockito.verifyNoInteractions(gl);
        });
    }

    // Binds the new name and allocates storage with the exact size and flag bits at the int and long limits
    @ParameterizedTest
    @ValueSource(longs = {1L, 8L, Integer.MAX_VALUE, 1L << 31, 1L << 32, Long.MAX_VALUE})
    void commandListStoresTheSizeAndFlagBitsUnchanged(long size) {
        EnumBitField<GlBufferStorageFlags> flags = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE, GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.COHERENT);
        onDevice(list -> {
            GlImmutableBuffer buffer = list.createImmutableBuffer(size, flags);
            int name = buffer.handle();
            assertSame(flags, buffer.getFlags());
            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glGenBuffers();
            order.verify(gl).glBindBuffer(ARRAY, name);
            order.verify(gl).glBufferStorage(ARRAY, size, flags.getBitField());
            Mockito.verify(gl, Mockito.times(1)).glBufferStorage(anyInt(), anyLong(), anyInt());
        });
    }

    // Maps each read and write and persistent request that the storage flags allow and refuses each missing flag in order
    @ParameterizedTest
    @MethodSource("masks")
    void commandListChecksMapRequestsAgainstTheFlags(int mask) {
        EnumBitField<GlBufferStorageFlags> flags = subset(mask);
        onDevice(list -> {
            GlImmutableBuffer buffer = list.createImmutableBuffer(8L, flags);
            for (int bits = 1; bits < 8; bits++) {
                EnumBitField<GlBufferMapFlags> map = request(bits);
                if ((bits & 4) != 0 && !flags.contains(GlBufferStorageFlags.PERSISTENT)) {
                    assertEquals(NOT_PERSISTENT, assertThrows(IllegalArgumentException.class, () -> list.mapBuffer(buffer, 0L, 8L, map)).getMessage());
                } else if ((bits & 2) != 0 && !flags.contains(GlBufferStorageFlags.MAP_WRITE)) {
                    assertEquals(NOT_WRITABLE, assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0L, 8L, map)).getMessage());
                } else if ((bits & 1) != 0 && !flags.contains(GlBufferStorageFlags.MAP_READ)) {
                    assertEquals(NOT_READABLE, assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0L, 8L, map)).getMessage());
                } else {
                    GlBufferMapping mapping = list.mapBuffer(buffer, 0L, 8L, map);
                    assertSame(mapping, buffer.getActiveMapping());
                    list.unmap(mapping);
                    assertTrue(mapping.isDisposed());
                }
                assertNull(buffer.getActiveMapping());
            }
            list.deleteBuffer(buffer);
            assertSame(flags, buffer.getFlags());
        });
    }

    // Keeps the public class with one public constructor and one public getter over one private final field
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        Class<GlImmutableBuffer> type = GlImmutableBuffer.class;
        assertEquals(Modifier.PUBLIC, type.getModifiers());
        assertSame(GlBuffer.class, type.getSuperclass());

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PUBLIC, constructors[0].getModifiers());
        assertArrayEquals(new Class<?>[] {EnumBitField.class}, constructors[0].getParameterTypes());

        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                fields.add(field.getName());
                assertEquals(Modifier.PRIVATE | Modifier.FINAL, field.getModifiers(), field.getName());
            }
        }
        assertEquals(List.of("flags"), fields);

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
                methods.add(method.getName());
            }
        }
        assertEquals(List.of("getFlags"), methods);

        Method getter = type.getDeclaredMethod("getFlags");
        assertEquals(Modifier.PUBLIC, getter.getModifiers());
        assertSame(EnumBitField.class, getter.getReturnType());
        ParameterizedType returned = (ParameterizedType) getter.getGenericReturnType();
        assertArrayEquals(new Object[] {GlBufferStorageFlags.class}, returned.getActualTypeArguments());
    }
}
