package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

// Tests of every member of the buffer base class alone and behind a real command list
class GlBufferTest {
    // Message of a handle read after delete
    private static final String INVALID = "Handle is not valid";
    // Message of a zero name from the driver
    private static final String NO_NAME = "Failed to generate a buffer";
    // Message of a mapping that names another buffer
    private static final String FOREIGN = "Mapping belongs to another buffer";
    // Message of a mapping that is already unmapped
    private static final String DISPOSED = "Buffer mapping is already disposed";
    // Message of a mapping stored over another open mapping
    private static final String MAPPED = "Buffer is already mapped";
    // Number of buffers in the bulk tests
    private static final int MANY = 1000;
    // Target that the device binds for map and unmap
    private static final int ARRAY = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // Map flags of a plain write mapping
    private static final EnumBitField<GlBufferMapFlags> WRITE = EnumBitField.of(GlBufferMapFlags.WRITE);

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();
    // Native blocks that the test frees after it ends
    private final List<ByteBuffer> blocks = new ArrayList<>();

    // Concrete buffer that adds nothing to the base class
    private static final class Plain extends GlBuffer {
    }

    // Frees every native block the test allocated
    @AfterEach
    void freeBlocks() {
        for (ByteBuffer block : blocks) {
            MemoryUtil.memFree(block);
        }
        blocks.clear();
    }

    // Creates a buffer whose generated name is the given value
    private GlBuffer named(int name) {
        Mockito.doReturn(name).when(gl).glGenBuffers();
        return new Plain();
    }

    // Allocates a zeroed native block that the test frees afterwards
    private ByteBuffer block(int bytes) {
        ByteBuffer block = MemoryUtil.memCalloc(bytes);
        blocks.add(block);
        return block;
    }

    // Creates an open mapping of the buffer over a fresh native block
    private GlBufferMapping mapping(GlBuffer buffer) {
        return new GlBufferMapping(buffer, block(16));
    }

    // Creates a mapping of the buffer that is already unmapped
    private GlBufferMapping disposed(GlBuffer buffer) {
        GlBufferMapping mapping = mapping(buffer);
        mapping.dispose();
        return mapping;
    }

    // Runs the body with a command list of an active render device
    private static void onDevice(Consumer<CommandList> body) {
        GLRenderDevice device = new GLRenderDevice();
        device.makeActive();
        try {
            body.accept(device.createCommandList());
        } finally {
            device.makeInactive();
        }
    }

    // Creates a buffer from exactly one generated name and makes no other GL call
    @Test
    void constructorGeneratesExactlyOneName() {
        GlBuffer buffer = new Plain();
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
        assertEquals(1, buffer.handle());
        assertNull(buffer.getActiveMapping());
    }

    // Keeps every nonzero name the driver returns including the unsigned names above the int limit
    @ParameterizedTest
    @ValueSource(ints = {1, 2, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -2, -1})
    void handleReturnsTheGeneratedName(int name) {
        assertEquals(name, named(name).handle());
    }

    // Rejects a zero name from the driver and frees nothing
    @Test
    void constructorRejectsAZeroName() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> named(0));
        assertEquals(NO_NAME, error.getMessage());
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Creates a working buffer after the driver once returned a zero name
    @Test
    void constructorRecoversAfterAZeroName() {
        Mockito.doReturn(0).doReturn(9).when(gl).glGenBuffers();
        assertThrows(IllegalStateException.class, Plain::new);
        GlBuffer buffer = new Plain();
        assertEquals(9, buffer.handle());
        buffer.delete();
        Mockito.verify(gl).glDeleteBuffers(9);
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(0);
    }

    // Passes on the exact failure of name generation and frees nothing
    @Test
    void constructorPropagatesAGenerationFailure() {
        UnsupportedOperationException failure = new UnsupportedOperationException("No buffer objects");
        Mockito.doThrow(failure).when(gl).glGenBuffers();
        assertSame(failure, assertThrows(UnsupportedOperationException.class, Plain::new));
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Creates a working buffer after one failed generation
    @Test
    void constructorRecoversAfterAGenerationFailure() {
        Mockito.doThrow(new IllegalStateException("No context")).doReturn(4).when(gl).glGenBuffers();
        assertThrows(IllegalStateException.class, Plain::new);
        assertEquals(4, new Plain().handle());
    }

    // Generates one name for each subclass and rejects a zero name for both
    @Test
    void subclassesGenerateThroughTheBaseConstructor() {
        EnumBitField<GlBufferStorageFlags> flags = EnumBitField.of(GlBufferStorageFlags.MAP_WRITE);
        GlMutableBuffer mutable = new GlMutableBuffer();
        GlImmutableBuffer immutable = new GlImmutableBuffer(flags);
        assertEquals(1, mutable.handle());
        assertEquals(2, immutable.handle());
        assertNull(mutable.getActiveMapping());
        assertNull(immutable.getActiveMapping());
        Mockito.verify(gl, Mockito.times(2)).glGenBuffers();

        Mockito.doReturn(0).when(gl).glGenBuffers();
        assertEquals(NO_NAME, assertThrows(IllegalStateException.class, GlMutableBuffer::new).getMessage());
        assertEquals(NO_NAME, assertThrows(IllegalStateException.class, () -> new GlImmutableBuffer(flags)).getMessage());
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(anyInt());
    }

    // Returns null on a new buffer on every read without any GL call
    @Test
    void getActiveMappingStartsNullAndRepeatsWithoutGlCalls() {
        GlBuffer buffer = named(12);
        for (int i = 0; i < 3; i++) {
            assertNull(buffer.getActiveMapping());
        }
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Stores and clears a mapping of the buffer without any GL call and leaves the mapping and its memory alone
    @Test
    void setStoresAndClearsAMappingOfThisBuffer() {
        GlBuffer buffer = named(3);
        ByteBuffer memory = block(8);
        memory.put(0, (byte) 0x5A);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        buffer.setActiveMapping(mapping);
        assertSame(mapping, buffer.getActiveMapping());
        assertSame(mapping, buffer.getActiveMapping());
        buffer.setActiveMapping(null);
        assertNull(buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        assertSame(memory, mapping.getMemoryBuffer());
        assertEquals((byte) 0x5A, memory.get(0));
        Mockito.verify(gl).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Accepts null on an unmapped buffer on every call
    @Test
    void setNullOnAnUnmappedBufferIsAccepted() {
        GlBuffer buffer = named(5);
        for (int i = 0; i < 3; i++) {
            buffer.setActiveMapping(null);
            assertNull(buffer.getActiveMapping());
        }
    }

    // Keeps the open mapping when the same mapping is stored again
    @Test
    void setTheSameMappingAgainKeepsIt() {
        GlBuffer buffer = named(6);
        GlBufferMapping mapping = mapping(buffer);
        for (int i = 0; i < 3; i++) {
            buffer.setActiveMapping(mapping);
            assertSame(mapping, buffer.getActiveMapping());
        }
        assertFalse(mapping.isDisposed());
    }

    // Keeps a mapping that was disposed while open when it is stored again and clears it on delete
    @Test
    void setTheSameMappingAfterItsDisposalKeepsIt() {
        GlBuffer buffer = named(18);
        GlBufferMapping mapping = mapping(buffer);
        buffer.setActiveMapping(mapping);
        mapping.dispose();
        buffer.setActiveMapping(mapping);
        assertSame(mapping, buffer.getActiveMapping());
        assertEquals(MAPPED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(mapping(buffer))).getMessage());
        buffer.delete();
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
    }

    // Stores a mapping and disposes it on delete without reading any memory
    @Test
    void setAcceptsAMappingWhoseMemoryIsNeverRead() {
        GlBuffer buffer = named(19);
        GlBufferMapping sealed = new GlBufferMapping(buffer, block(4)) {
            // Fails the test on any read of the memory buffer
            @Override
            public ByteBuffer getMemoryBuffer() {
                throw new AssertionError("Memory buffer was read");
            }
        };
        buffer.setActiveMapping(sealed);
        assertSame(sealed, buffer.getActiveMapping());
        buffer.delete();
        assertNull(buffer.getActiveMapping());
        assertTrue(sealed.isDisposed());
    }

    // Accepts a new mapping after the previous one was cleared
    @Test
    void setAcceptsANewMappingAfterClearing() {
        GlBuffer buffer = named(7);
        GlBufferMapping first = mapping(buffer);
        GlBufferMapping second = mapping(buffer);
        buffer.setActiveMapping(first);
        buffer.setActiveMapping(null);
        buffer.setActiveMapping(second);
        assertSame(second, buffer.getActiveMapping());
        assertFalse(first.isDisposed());
    }

    // Rejects a mapping of another buffer and keeps the stored state
    @Test
    void setRejectsAMappingOfAnotherBuffer() {
        GlBuffer buffer = named(8);
        GlBuffer other = named(9);
        GlBufferMapping foreign = mapping(other);
        assertEquals(FOREIGN, assertThrows(IllegalArgumentException.class, () -> buffer.setActiveMapping(foreign)).getMessage());
        assertNull(buffer.getActiveMapping());
        assertNull(other.getActiveMapping());

        GlBufferMapping own = mapping(buffer);
        buffer.setActiveMapping(own);
        assertEquals(FOREIGN, assertThrows(IllegalArgumentException.class, () -> buffer.setActiveMapping(foreign)).getMessage());
        assertSame(own, buffer.getActiveMapping());
        assertFalse(foreign.isDisposed());
    }

    // Rejects a mapping that names no buffer
    @Test
    void setRejectsAMappingOfNoBuffer() {
        GlBuffer buffer = named(10);
        GlBufferMapping orphan = new GlBufferMapping(buffer, block(4)) {
            // Reports no buffer
            @Override
            public GlBuffer getBufferObject() {
                return null;
            }
        };
        assertEquals(FOREIGN, assertThrows(IllegalArgumentException.class, () -> buffer.setActiveMapping(orphan)).getMessage());
        assertNull(buffer.getActiveMapping());
    }

    // Rejects a mapping that is already unmapped and keeps the buffer unmapped
    @Test
    void setRejectsADisposedMapping() {
        GlBuffer buffer = named(11);
        GlBufferMapping stale = disposed(buffer);
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(stale)).getMessage());
        assertNull(buffer.getActiveMapping());
    }

    // Rejects a second mapping over an open one and keeps the first
    @Test
    void setRejectsASecondMapping() {
        GlBuffer buffer = named(12);
        GlBufferMapping first = mapping(buffer);
        GlBufferMapping second = mapping(buffer);
        buffer.setActiveMapping(first);
        assertEquals(MAPPED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(second)).getMessage());
        assertSame(first, buffer.getActiveMapping());
        assertFalse(first.isDisposed());
        assertFalse(second.isDisposed());
    }

    // Rejects a mapping after delete and still accepts null
    @Test
    void setRejectsAMappingAfterDelete() {
        GlBuffer buffer = named(13);
        GlBufferMapping late = mapping(buffer);
        buffer.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(late)).getMessage());
        assertNull(buffer.getActiveMapping());
        assertFalse(late.isDisposed());
        buffer.setActiveMapping(null);
        assertNull(buffer.getActiveMapping());
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(13);
    }

    // Reports a deleted buffer first and then a foreign mapping and then a disposed one and then a second mapping
    @Test
    void setRefusalsComeInAFixedOrder() {
        GlBuffer buffer = named(14);
        GlBuffer other = named(15);
        GlBufferMapping open = mapping(buffer);
        buffer.setActiveMapping(open);

        assertEquals(FOREIGN, assertThrows(IllegalArgumentException.class, () -> buffer.setActiveMapping(disposed(other))).getMessage());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(disposed(buffer))).getMessage());
        assertEquals(MAPPED, assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(mapping(buffer))).getMessage());
        assertSame(open, buffer.getActiveMapping());

        GlBufferMapping foreignStale = disposed(other);
        other.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> other.setActiveMapping(foreignStale)).getMessage());
        assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> other.setActiveMapping(mapping(buffer))).getMessage());
    }

    // Makes no GL call on any refused mapping
    @Test
    void setRefusalsMakeNoGlCalls() {
        GlBuffer buffer = named(16);
        GlBuffer other = named(17);
        assertThrows(IllegalArgumentException.class, () -> buffer.setActiveMapping(mapping(other)));
        assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(disposed(buffer)));
        buffer.setActiveMapping(mapping(buffer));
        assertThrows(IllegalStateException.class, () -> buffer.setActiveMapping(mapping(buffer)));
        Mockito.verify(gl, Mockito.times(2)).glGenBuffers();
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Deletes the generated name once and makes no other GL call
    @Test
    void deleteDeletesTheGeneratedNameOnce() {
        GlBuffer buffer = named(5);
        buffer.delete();
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glGenBuffers();
        order.verify(gl).glDeleteBuffers(5);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Deletes names at the int limits without changing them
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, Integer.MIN_VALUE, -1})
    void deletePassesLimitNamesUnchanged(int name) {
        named(name).delete();
        Mockito.verify(gl).glDeleteBuffers(name);
    }

    // Rejects a handle read after delete
    @Test
    void handleRejectsUseAfterDelete() {
        GlBuffer buffer = named(3);
        buffer.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::handle).getMessage());
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::handle).getMessage());
    }

    // Ignores every delete after the first
    @Test
    void repeatedDeleteFreesTheNameOnce() {
        GlBuffer buffer = named(6);
        for (int i = 0; i < 3; i++) {
            buffer.delete();
        }
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(6);
        assertNull(buffer.getActiveMapping());
    }

    // Deletes through the alias exactly like delete and ignores every later call of either
    @Test
    @SuppressWarnings("deprecation")
    void destroyDeletesLikeDelete() {
        GlBuffer buffer = named(8);
        GlBufferMapping mapping = mapping(buffer);
        buffer.setActiveMapping(mapping);
        buffer.destroy();
        buffer.destroy();
        buffer.delete();
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(8);
        assertThrows(IllegalStateException.class, buffer::handle);
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());

        GlBuffer other = named(10);
        other.delete();
        other.destroy();
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(10);
    }

    // Keeps the buffer live and mapped after a failed delete and deletes and unmaps it on the retry
    @Test
    void failedDeleteKeepsTheBufferLiveAndMapped() {
        GlBuffer buffer = named(11);
        GlBufferMapping mapping = mapping(buffer);
        buffer.setActiveMapping(mapping);
        IllegalStateException failure = new IllegalStateException("Context lost");
        Mockito.doThrow(failure).doNothing().when(gl).glDeleteBuffers(11);
        assertSame(failure, assertThrows(IllegalStateException.class, buffer::delete));
        assertEquals(11, buffer.handle());
        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());

        buffer.delete();
        assertThrows(IllegalStateException.class, buffer::handle);
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        buffer.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteBuffers(11);
    }

    // Disposes and clears the open mapping on delete and leaves its native memory untouched
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4096})
    void deleteWhileMappedDisposesTheMappingAndKeepsItsMemory(int offset) {
        GlBuffer buffer = named(20);
        ByteBuffer whole = block(offset + 8);
        for (int i = 0; i < whole.capacity(); i++) {
            whole.put(i, (byte) (i * 7 + 1));
        }
        ByteBuffer memory = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(whole) + offset, 8);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        buffer.setActiveMapping(mapping);
        buffer.delete();
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
        assertSame(buffer, mapping.getBufferObject());
        assertSame(memory, mapping.getMemoryBuffer());
        for (int i = 0; i < whole.capacity(); i++) {
            assertEquals((byte) (i * 7 + 1), whole.get(i));
        }
        Mockito.verify(gl).glDeleteBuffers(20);
    }

    // Disposes a mapping over a zero byte block on delete
    @Test
    void deleteDisposesAnEmptyMapping() {
        GlBuffer buffer = named(21);
        GlBufferMapping mapping = new GlBufferMapping(buffer, ByteBuffer.allocateDirect(0));
        buffer.setActiveMapping(mapping);
        buffer.delete();
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
    }

    // Disposes only the open mapping and leaves a cleared one alone
    @Test
    void deleteDisposesOnlyTheOpenMapping() {
        GlBuffer buffer = named(22);
        GlBufferMapping cleared = mapping(buffer);
        buffer.setActiveMapping(cleared);
        buffer.setActiveMapping(null);
        buffer.delete();
        assertFalse(cleared.isDisposed());

        GlBuffer unmapped = named(23);
        GlBufferMapping never = mapping(unmapped);
        unmapped.delete();
        assertFalse(never.isDisposed());
    }

    // Keeps other buffers mapped when one mapped buffer is deleted
    @Test
    void deleteUnmapsOnlyItsOwnMapping() {
        GlBuffer first = named(24);
        GlBuffer second = named(25);
        GlBufferMapping firstMapping = mapping(first);
        GlBufferMapping secondMapping = mapping(second);
        first.setActiveMapping(firstMapping);
        second.setActiveMapping(secondMapping);
        first.delete();
        assertTrue(firstMapping.isDisposed());
        assertSame(secondMapping, second.getActiveMapping());
        assertFalse(secondMapping.isDisposed());
        assertEquals(25, second.handle());
    }

    // Deletes the name and unmaps from the template hook and leaves the handle to the base class
    @Test
    void destroyInternalDeletesAndUnmapsButKeepsTheHandle() {
        GlBuffer buffer = named(13);
        GlBufferMapping mapping = mapping(buffer);
        buffer.setActiveMapping(mapping);
        buffer.destroyInternal();
        Mockito.verify(gl).glDeleteBuffers(13);
        assertEquals(13, buffer.handle());
        assertNull(buffer.getActiveMapping());
        assertTrue(mapping.isDisposed());
    }

    // Rejects the template hook on a deleted buffer before any GL call
    @Test
    void destroyInternalRejectsADeletedBuffer() {
        GlBuffer buffer = named(14);
        buffer.delete();
        assertEquals(INVALID, assertThrows(IllegalStateException.class, buffer::destroyInternal).getMessage());
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(14);
    }

    // Keeps distinct names apart and leaves one buffer live after the other is deleted
    @Test
    void deleteLeavesOtherBuffersLive() {
        GlBuffer first = new Plain();
        GlBuffer second = new Plain();
        assertNotEquals(first.handle(), second.handle());
        int kept = second.handle();
        first.delete();
        assertEquals(kept, second.handle());
        Mockito.verify(gl, Mockito.never()).glDeleteBuffers(kept);
    }

    // Deletes each of many mapped buffers by its own name exactly once and disposes each mapping
    @Test
    void manyBuffersDeleteTheirOwnNames() {
        List<GlBuffer> buffers = new ArrayList<>();
        List<GlBufferMapping> mappings = new ArrayList<>();
        List<Integer> names = new ArrayList<>();
        Set<Integer> unique = new HashSet<>();
        ByteBuffer shared = block(1);
        for (int i = 0; i < MANY; i++) {
            GlBuffer buffer = new Plain();
            GlBufferMapping mapping = new GlBufferMapping(buffer, shared);
            buffer.setActiveMapping(mapping);
            buffers.add(buffer);
            mappings.add(mapping);
            names.add(buffer.handle());
            assertTrue(unique.add(buffer.handle()));
        }
        for (GlBuffer buffer : buffers) {
            buffer.delete();
            buffer.delete();
        }
        ArgumentCaptor<Integer> deleted = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(gl, Mockito.times(MANY)).glDeleteBuffers(deleted.capture());
        assertEquals(names, deleted.getAllValues());
        for (int i = 0; i < MANY; i++) {
            assertNull(buffers.get(i).getActiveMapping());
            assertTrue(mappings.get(i).isDisposed());
        }
    }

    // Lets two buffers share one name the driver reused and frees it once for each
    @Test
    void reusedNameBelongsToEachBufferInTurn() {
        GlBuffer first = named(7);
        first.delete();
        GlBuffer second = named(7);
        GlBufferMapping mapping = mapping(second);
        second.setActiveMapping(mapping);
        assertEquals(7, second.handle());
        first.delete();
        Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(7);
        assertSame(mapping, second.getActiveMapping());
        assertFalse(mapping.isDisposed());
        assertThrows(IllegalStateException.class, () -> first.setActiveMapping(mapping));
        second.delete();
        Mockito.verify(gl, Mockito.times(2)).glDeleteBuffers(7);
    }

    // Maps and unmaps and remaps through a real command list and unmaps before the delete
    @Test
    void commandListMapsUnmapsRemapsAndDeletes() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            int name = buffer.handle();
            GlBufferMapping first = list.mapBuffer(buffer, 0L, 8L, WRITE);
            assertSame(first, buffer.getActiveMapping());
            assertSame(buffer, first.getBufferObject());
            assertEquals(MAPPED, assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0L, 8L, WRITE)).getMessage());
            list.unmap(first);
            assertNull(buffer.getActiveMapping());
            assertTrue(first.isDisposed());

            GlBufferMapping second = list.mapBuffer(buffer, 0L, 8L, WRITE);
            assertNotSame(first, second);
            assertSame(second, buffer.getActiveMapping());
            list.deleteBuffer(buffer);
            assertNull(buffer.getActiveMapping());
            assertTrue(second.isDisposed());

            InOrder order = Mockito.inOrder(gl);
            order.verify(gl).glUnmapBuffer(ARRAY);
            order.verify(gl).glUnmapBuffer(ARRAY);
            order.verify(gl).glDeleteBuffers(name);
            Mockito.verify(gl, Mockito.times(2)).glUnmapBuffer(ARRAY);
            Mockito.verify(gl, Mockito.times(1)).glDeleteBuffers(name);
        });
    }

    // Refuses the stale mapping of a buffer deleted while mapped before any GL call
    @Test
    void directDeleteWhileMappedRefusesTheStaleMapping() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            GlBufferMapping mapping = list.mapBuffer(buffer, 0L, 8L, WRITE);
            buffer.delete();
            assertNull(buffer.getActiveMapping());
            assertTrue(mapping.isDisposed());
            assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> list.unmap(mapping)).getMessage());
            assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> list.flushMappedRange(mapping, 0, 4)).getMessage());
            assertEquals(INVALID, assertThrows(IllegalStateException.class, () -> list.mapBuffer(buffer, 0L, 8L, WRITE)).getMessage());
            Mockito.verify(gl, Mockito.never()).glUnmapBuffer(anyInt());
            Mockito.verify(gl, Mockito.never()).glFlushMappedBufferRange(anyInt(), anyLong(), anyLong());
            Mockito.verify(gl, Mockito.times(1)).glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt());
        });
    }

    // Keeps the public abstract class with one protected constructor and its public accessors and protected hook
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBuffer> type = GlBuffer.class;
        assertEquals(Modifier.PUBLIC | Modifier.ABSTRACT, type.getModifiers());
        assertSame(GlObject.class, type.getSuperclass());

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PROTECTED, constructors[0].getModifiers());
        assertEquals(0, constructors[0].getParameterCount());

        for (Field field : type.getDeclaredFields()) {
            assertTrue(field.isSynthetic() || Modifier.isPrivate(field.getModifiers()), field.getName());
        }

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        methods.sort(null);
        assertEquals(List.of("destroyInternal", "getActiveMapping", "setActiveMapping"), methods);

        Method getter = type.getDeclaredMethod("getActiveMapping");
        assertEquals(Modifier.PUBLIC, getter.getModifiers());
        assertSame(GlBufferMapping.class, getter.getReturnType());
        Method setter = type.getDeclaredMethod("setActiveMapping", GlBufferMapping.class);
        assertEquals(Modifier.PUBLIC, setter.getModifiers());
        assertSame(void.class, setter.getReturnType());
        Method hook = type.getDeclaredMethod("destroyInternal");
        assertEquals(Modifier.PROTECTED, hook.getModifiers());
        assertSame(void.class, hook.getReturnType());
    }
}
