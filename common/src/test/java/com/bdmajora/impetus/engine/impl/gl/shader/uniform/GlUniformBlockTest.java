package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the uniform block binding point and the buffer bind call
class GlUniformBlockTest {
    // GL target of the uniform buffer binding points
    private static final int GL_UNIFORM_BUFFER = 0x8A11;
    // Message of a negative binding point
    private static final String BAD_BINDING = "Uniform block binding must not be negative";
    // Message of a deleted buffer
    private static final String BAD_HANDLE = "Handle is not valid";
    // Number of random binding points in the sampled test
    private static final int SAMPLES = 100_000;
    // Seed of the sampled test
    private static final long SEED = 0x5EED_B10CL;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Creates a buffer with the next generated name and clears the recorded calls
    private GlMutableBuffer buffer() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        Mockito.clearInvocations(this.gl);

        return buffer;
    }

    // Creates a buffer with the given name and clears the recorded calls
    private GlMutableBuffer bufferNamed(int name) {
        Mockito.doReturn(name).when(this.gl).glGenBuffers();

        return this.buffer();
    }

    // Returns the binding point that the block stores
    private static int bindingOf(GlUniformBlock block) throws ReflectiveOperationException {
        Field field = GlUniformBlock.class.getDeclaredField("binding");
        field.setAccessible(true);

        return field.getInt(block);
    }

    // Stores every accepted binding point exactly and forwards it on bind
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 23, 24, 35, 36, 83, 84, 1023, 1024, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void constructorStoresEveryAcceptedBindingPoint(int binding) throws ReflectiveOperationException {
        GlUniformBlock block = new GlUniformBlock(binding);
        GlMutableBuffer buffer = this.buffer();

        assertEquals(binding, bindingOf(block));
        block.bindBuffer(buffer);

        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects every negative binding point with the same message and issues no GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, -24, -36, -84, -65536, Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void constructorRejectsEveryNegativeBindingPoint(int binding) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new GlUniformBlock(binding));

        assertEquals(BAD_BINDING, error.getMessage());
        assertNull(error.getCause());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Accepts a binding point exactly when it is not negative over random samples of the whole int range
    @Test
    void constructorAcceptsExactlyTheNonNegativeBindingPoints() throws ReflectiveOperationException {
        SplittableRandom random = new SplittableRandom(SEED);

        for (int i = 0; i < SAMPLES; i++) {
            int binding = random.nextInt();

            if (binding >= 0) {
                assertEquals(binding, bindingOf(new GlUniformBlock(binding)));
            } else {
                assertThrows(IllegalArgumentException.class, () -> new GlUniformBlock(binding));
            }
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Issues no GL call on construction
    @Test
    void constructorIssuesNoGlCall() {
        new GlUniformBlock(0);
        new GlUniformBlock(5);
        new GlUniformBlock(Integer.MAX_VALUE);

        Mockito.verifyNoInteractions(this.gl);
    }

    // Keeps the binding point of each block apart from every other block
    @Test
    void constructorKeepsEachBlockApart() {
        GlUniformBlock first = new GlUniformBlock(1);
        GlUniformBlock second = new GlUniformBlock(7);
        GlMutableBuffer buffer = this.buffer();

        second.bindBuffer(buffer);
        first.bindBuffer(buffer);
        second.bindBuffer(buffer);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 7, buffer.handle());
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 1, buffer.handle());
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 7, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Issues exactly one bind call at the uniform buffer target
    @Test
    void bindBufferIssuesExactlyOneBindCall() {
        GlUniformBlock block = new GlUniformBlock(3);
        GlMutableBuffer buffer = this.buffer();

        block.bindBuffer(buffer);

        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 3, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null buffer before any GL call and stays usable
    @Test
    void bindBufferRejectsANullBuffer() {
        GlUniformBlock block = new GlUniformBlock(4);

        assertThrows(NullPointerException.class, () -> block.bindBuffer(null));
        Mockito.verifyNoInteractions(this.gl);

        GlMutableBuffer buffer = this.buffer();
        block.bindBuffer(buffer);
        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 4, buffer.handle());
    }

    // Rejects a deleted buffer before any GL call
    @Test
    void bindBufferRejectsADeletedBuffer() {
        GlUniformBlock block = new GlUniformBlock(2);
        GlMutableBuffer buffer = this.buffer();
        buffer.delete();
        Mockito.clearInvocations(this.gl);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> block.bindBuffer(buffer));

        assertEquals(BAD_HANDLE, error.getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a buffer deleted after a bind and keeps the earlier bind call alone
    @Test
    void bindBufferRejectsABufferDeletedAfterABind() {
        GlUniformBlock block = new GlUniformBlock(6);
        GlMutableBuffer buffer = this.buffer();
        int name = buffer.handle();

        block.bindBuffer(buffer);
        buffer.delete();

        assertThrows(IllegalStateException.class, () -> block.bindBuffer(buffer));
        assertThrows(IllegalStateException.class, () -> block.bindBuffer(buffer));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 6, name);
        order.verify(this.gl).glDeleteBuffers(name);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards every buffer name unchanged including names above the signed int range
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, -1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void bindBufferForwardsEveryBufferNameUnchanged(int name) {
        GlUniformBlock block = new GlUniformBlock(Integer.MAX_VALUE);
        GlMutableBuffer buffer = this.bufferNamed(name);

        block.bindBuffer(buffer);

        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, Integer.MAX_VALUE, name);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Repeats the bind call on every invocation with the same buffer
    @Test
    void bindBufferRepeatsTheCallOnEveryInvocation() {
        GlUniformBlock block = new GlUniformBlock(0);
        GlMutableBuffer buffer = this.buffer();

        block.bindBuffer(buffer);
        block.bindBuffer(buffer);
        block.bindBuffer(buffer);

        Mockito.verify(this.gl, Mockito.times(3)).glBindBufferBase(GL_UNIFORM_BUFFER, 0, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Forwards each buffer in call order when buffers alternate
    @Test
    void bindBufferForwardsBuffersInCallOrder() {
        GlUniformBlock block = new GlUniformBlock(9);
        GlMutableBuffer first = this.bufferNamed(11);
        GlMutableBuffer second = this.bufferNamed(12);

        block.bindBuffer(first);
        block.bindBuffer(second);
        block.bindBuffer(first);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 9, 11);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 9, 12);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 9, 11);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Binds one buffer at the lowest and highest binding points and one between them
    @Test
    void bindBufferBindsOneBufferAtSeveralBindingPoints() {
        GlMutableBuffer buffer = this.bufferNamed(21);

        new GlUniformBlock(0).bindBuffer(buffer);
        new GlUniformBlock(1).bindBuffer(buffer);
        new GlUniformBlock(Integer.MAX_VALUE).bindBuffer(buffer);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 0, 21);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 1, 21);
        order.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, Integer.MAX_VALUE, 21);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Binds a buffer without storage and a buffer that records the largest long size and leaves both sizes unchanged
    @Test
    void bindBufferAcceptsEveryStorageSize() {
        GlUniformBlock block = new GlUniformBlock(5);
        GlMutableBuffer empty = this.bufferNamed(31);
        GlMutableBuffer huge = this.bufferNamed(32);
        huge.setSize(Long.MAX_VALUE);

        block.bindBuffer(empty);
        block.bindBuffer(huge);

        assertEquals(0L, empty.getSize());
        assertEquals(Long.MAX_VALUE, huge.getSize());
        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 5, 31);
        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 5, 32);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Binds a mapped buffer and leaves the mapping open and its native memory untouched
    @Test
    void bindBufferLeavesTheMappingOpen() {
        GlUniformBlock block = new GlUniformBlock(8);
        GlMutableBuffer buffer = this.buffer();
        ByteBuffer memory = ByteBuffer.allocateDirect(16);
        byte[] pattern = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        memory.put(0, pattern);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        buffer.setActiveMapping(mapping);

        block.bindBuffer(buffer);

        assertSame(mapping, buffer.getActiveMapping());
        assertFalse(mapping.isDisposed());
        byte[] read = new byte[16];
        memory.get(0, read);
        assertArrayEquals(pattern, read);
        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 8, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Binds an anonymous buffer subclass through the base type
    @Test
    void bindBufferAcceptsAnyBufferSubclass() {
        GlUniformBlock block = new GlUniformBlock(10);
        Mockito.doReturn(41).when(this.gl).glGenBuffers();
        // Creates an anonymous buffer subclass with the stubbed name
        GlBuffer buffer = new GlBuffer() {};
        Mockito.clearInvocations(this.gl);

        block.bindBuffer(buffer);

        Mockito.verify(this.gl).glBindBufferBase(GL_UNIFORM_BUFFER, 10, 41);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Propagates the exact GL failure and binds again on the next call
    @Test
    void bindBufferPropagatesAGlFailureAndStaysUsable() {
        GlUniformBlock block = new GlUniformBlock(12);
        GlMutableBuffer buffer = this.bufferNamed(51);
        IllegalStateException failure = new IllegalStateException("driver failure");
        Mockito.doThrow(failure).doNothing().when(this.gl).glBindBufferBase(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());

        assertSame(failure, assertThrows(IllegalStateException.class, () -> block.bindBuffer(buffer)));
        block.bindBuffer(buffer);

        Mockito.verify(this.gl, Mockito.times(2)).glBindBufferBase(GL_UNIFORM_BUFFER, 12, 51);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Keeps the public shape of one int constructor and one bind method over one immutable field
    @Test
    void publicShapeStaysFixed() throws ReflectiveOperationException {
        Class<GlUniformBlock> type = GlUniformBlock.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertFalse(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isAbstract(type.getModifiers()));

        Constructor<?>[] constructors = type.getConstructors();
        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[]{int.class}, constructors[0].getParameterTypes());

        Method[] methods = Arrays.stream(type.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toArray(Method[]::new);
        assertEquals(1, methods.length);
        assertEquals(type.getMethod("bindBuffer", GlBuffer.class), methods[0]);
        assertTrue(Modifier.isPublic(methods[0].getModifiers()));
        assertEquals(void.class, methods[0].getReturnType());

        Field[] fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).toArray(Field[]::new);
        assertEquals(1, fields.length);
        assertEquals(int.class, fields[0].getType());
        assertTrue(Modifier.isPrivate(fields[0].getModifiers()));
        assertTrue(Modifier.isFinal(fields[0].getModifiers()));
        assertFalse(Modifier.isStatic(fields[0].getModifiers()));
    }
}
