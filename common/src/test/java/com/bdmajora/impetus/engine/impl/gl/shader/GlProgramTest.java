package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttribute;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL20;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Tests of the program constructor and interface and uniform lookups and every builder step
class GlProgramTest {
    // Program name that the stubbed driver hands out
    private static final int PROGRAM = 41;
    // GL query of the link status
    private static final int LINK_STATUS = GL20.GL_LINK_STATUS;
    // GL target of a uniform buffer binding
    private static final int UNIFORM_BUFFER = 0x8A11;
    // Maximum link log length that the builder asks for
    private static final int INFO_LOG_LENGTH = 4096;
    // Pattern of the link log warning
    private static final String LOG_PATTERN = "Program link log for {}: {}";
    // Message of a null name
    private static final String NULL_NAME = "Name must not be null";
    // Message of a null factory
    private static final String NULL_FACTORY = "Factory must not be null";
    // Message of a null shader
    private static final String NULL_SHADER = "Shader must not be null";
    // Message of a null vertex format
    private static final String NULL_FORMAT = "Format must not be null";
    // Message of a negative attribute location
    private static final String NEGATIVE_ATTRIBUTE = "Attribute index must not be negative";
    // Message of a last attribute location past the int range
    private static final String LAST_ATTRIBUTE = "Attribute index must not exceed 2147483647";
    // Message of a negative fragment output
    private static final String NEGATIVE_FRAGMENT = "Fragment output index must not be negative";
    // Message of a builder that already linked
    private static final String LINKED = "Program builder is already linked";
    // Message of a zero program name from the driver
    private static final String CREATE_FAILED = "Failed to create a program";
    // Message of a failed link
    private static final String LINK_FAILED = "Shader program linking failed, see log for details";
    // Message of a deleted GL object
    private static final String BAD_HANDLE = "Handle is not valid";
    // Message of a negative uniform block binding point
    private static final String BAD_BINDING = "Uniform block binding must not be negative";

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Error type that no GL or factory code throws on its own
    private static final class Fault extends Error {
        // Creates the error with the message
        private Fault(String message) {
            super(message);
        }
    }

    // Returns one fresh throwable of each kind that a factory or GL call can raise
    static Stream<Throwable> throwables() {
        return Stream.of(new RuntimeException("runtime"), new IllegalStateException("state"), new Fault("error"), new IOException("checked"));
    }

    // Throws the throwable without declaring it
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable throwable) throws E {
        throw (E) throwable;
    }

    // Returns what the body throws and fails when it completes
    private static Throwable thrown(Executable body) {
        try {
            body.execute();
        } catch (Throwable t) {
            return t;
        }

        throw new AssertionError("Expected a throwable");
    }

    // Returns a builder over the stubbed program name with no recorded GL calls
    private GlProgram.Builder builder() {
        Mockito.doReturn(PROGRAM).when(this.gl).glCreateProgram();
        GlProgram.Builder builder = GlProgram.builder("program");
        Mockito.clearInvocations(this.gl);

        return builder;
    }

    // Returns a linked program over the stubbed program name with no recorded GL calls
    private GlProgram<Object> linked() {
        GlProgram<Object> program = this.builder().link(context -> null);
        Mockito.clearInvocations(this.gl);

        return program;
    }

    // Returns a compiled shader with no recorded GL calls
    private GlShader shader() {
        GlShader shader = new GlShader(ShaderType.VERTEX, "shader", "");
        Mockito.clearInvocations(this.gl);

        return shader;
    }

    // Returns a format of single float attributes named a0 upward
    private static GlVertexFormat format(int count) {
        if (count == 0) {
            return new GlVertexFormat(new Reference2ReferenceLinkedOpenHashMap<>(), 4);
        }

        GlVertexFormat.Builder builder = GlVertexFormat.builder(4 * count);

        for (int i = 0; i < count; i++) {
            builder.addElement("a" + i, 4 * i, GlVertexAttributeFormat.FLOAT, 1, false, false);
        }

        return builder.build();
    }

    // Runs the body with the program logger swapped for a mock
    private static void withLogger(Consumer<Logger> body) {
        // Runs the class initialiser before the swap
        Logger original = Statics.get(GlProgram.class, "LOGGER");
        Logger mock = Mockito.mock(Logger.class);
        Statics.set(GlProgram.class, "LOGGER", mock);

        try {
            body.accept(mock);
        } finally {
            Statics.set(GlProgram.class, "LOGGER", original);
        }
    }

    // Rejects a null factory without a GL call
    @Test
    void constructorRejectsANullFactoryWithoutAGlCall() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new GlProgram<Object>(5, null));
        assertEquals(NULL_FACTORY, error.getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Stores every non zero program name and passes the program itself to the factory once
    @ParameterizedTest
    @ValueSource(ints = {1, 2, PROGRAM, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void constructorStoresTheProgramAndPassesItselfToTheFactory(int name) {
        AtomicReference<ShaderBindingContext> context = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        Object iface = new Object();

        GlProgram<Object> program = new GlProgram<>(name, c -> {
            calls.incrementAndGet();
            context.set(c);

            return iface;
        });

        assertSame(program, context.get());
        assertSame(iface, program.getInterface());
        assertEquals(name, program.handle());
        assertEquals(1, calls.get());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Builds a program over the zero name that reads as deleted from the start
    @Test
    void constructorOverTheZeroNameReadsAsDeleted() {
        GlProgram<String> program = new GlProgram<>(0, c -> "iface");

        assertEquals("iface", program.getInterface());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, program::handle).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, program::bind).getMessage());
        program.delete();
        Mockito.verifyNoInteractions(this.gl);
    }

    // Deletes the program and rethrows the same throwable when the factory throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheProgramWhenTheFactoryThrows(Throwable error) {
        AtomicReference<ShaderBindingContext> escaped = new AtomicReference<>();

        Throwable thrown = thrown(() -> new GlProgram<>(9, c -> {
            escaped.set(c);
            throw sneaky(error);
        }));

        assertSame(error, thrown);
        Mockito.verify(this.gl).glDeleteProgram(9);
        Mockito.verifyNoMoreInteractions(this.gl);
        GlProgram<?> program = (GlProgram<?>) escaped.get();
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, program::bind).getMessage());
        program.delete();
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Deletes the program once when the factory deletes it and then throws
    @Test
    void constructorDeletesOnceWhenTheFactoryDeletesThenThrows() {
        IllegalStateException error = new IllegalStateException("factory");

        assertSame(error, thrown(() -> new GlProgram<>(9, c -> {
            ((GlProgram<?>) c).delete();
            throw error;
        })));

        Mockito.verify(this.gl).glDeleteProgram(9);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Keeps a null interface and reads a null interface while the factory runs
    @Test
    void constructorKeepsANullInterfaceAndReadsNullDuringTheFactory() {
        AtomicReference<Object> during = new AtomicReference<>("unset");

        GlProgram<Object> program = new GlProgram<>(3, c -> {
            during.set(((GlProgram<?>) c).getInterface());

            return null;
        });

        assertNull(during.get());
        assertNull(program.getInterface());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Returns the same interface on every call and after delete without another GL call
    @Test
    void getInterfaceReturnsTheSameInstanceBeforeAndAfterDelete() {
        Object iface = new Object();
        GlProgram<Object> program = this.builder().link(c -> iface);
        Mockito.clearInvocations(this.gl);

        for (int i = 0; i < 3; i++) {
            assertSame(iface, program.getInterface());
        }

        Mockito.verifyNoInteractions(this.gl);
        program.delete();
        assertSame(iface, program.getInterface());
        Mockito.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null name in both builder entry points without a GL call
    @Test
    void builderRejectsANullNameWithoutAGlCall() {
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> GlProgram.builder(null)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> new GlProgram.Builder(null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a zero program name from the driver in both builder entry points
    @Test
    void builderRejectsAZeroProgramNameFromTheDriver() {
        Mockito.doReturn(0).when(this.gl).glCreateProgram();

        assertEquals(CREATE_FAILED, assertThrows(IllegalStateException.class, () -> GlProgram.builder("program")).getMessage());
        assertEquals(CREATE_FAILED, assertThrows(IllegalStateException.class, () -> new GlProgram.Builder("program")).getMessage());
        Mockito.verify(this.gl, Mockito.times(2)).glCreateProgram();
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Accepts every non zero program name from the driver
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void builderAcceptsEveryNonZeroProgramName(int name) {
        Mockito.doReturn(name).when(this.gl).glCreateProgram();

        GlProgram<Object> program = new GlProgram.Builder("").link(c -> null);

        assertEquals(name, program.handle());
        Mockito.verify(this.gl).glLinkProgram(name);
    }

    // Creates one program for each builder
    @Test
    void builderCreatesOneProgramPerBuilder() {
        GlProgram<Object> first = GlProgram.builder("first").link(c -> null);
        GlProgram<Object> second = GlProgram.builder("second").link(c -> null);

        assertNotSame(first, second);
        Mockito.verify(this.gl, Mockito.times(2)).glCreateProgram();
        assertEquals(1, Math.abs(first.handle() - second.handle()));
    }

    // Makes the program current on every call
    @Test
    void bindMakesTheProgramCurrentOnEveryCall() {
        GlProgram<Object> program = this.linked();

        program.bind();
        program.bind();
        program.bind();

        Mockito.verify(this.gl, Mockito.times(3)).glUseProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a deleted program without a GL call
    @Test
    void bindRejectsADeletedProgramWithoutAGlCall() {
        GlProgram<Object> program = this.linked();
        program.delete();
        Mockito.clearInvocations(this.gl);

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, program::bind).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Makes no program current on every call before and after delete
    @Test
    void unbindMakesNoProgramCurrentBeforeAndAfterDelete() {
        GlProgram<Object> program = this.linked();

        program.unbind();
        program.unbind();
        Mockito.verify(this.gl, Mockito.times(2)).glUseProgram(0);
        program.delete();
        program.unbind();

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glDeleteProgram(PROGRAM);
        order.verify(this.gl).glUseProgram(0);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Deletes the program once over repeated delete and destroy calls
    @Test
    @SuppressWarnings("deprecation")
    void deleteFreesTheProgramOnce() {
        GlProgram<Object> program = this.linked();

        program.delete();
        program.delete();
        program.destroy();

        Mockito.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, program::handle).getMessage());
    }

    // Deletes every non zero program name
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void deleteFreesEveryProgramName(int name) {
        new GlProgram<>(name, c -> null).delete();

        Mockito.verify(this.gl).glDeleteProgram(name);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null uniform name before the factory and without a GL call
    @Test
    void uniformRejectsANullNameWithoutAGlCall() {
        GlProgram<Object> program = this.linked();

        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> program.bindUniformIfPresent(null, GlUniformInt::new)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> program.bindUniformIfPresent(null, null)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> program.bindUniform(null, GlUniformInt::new)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a null factory for a present or missing uniform without a GL call
    @Test
    void uniformRejectsANullFactoryWithoutAGlCall() {
        Mockito.doReturn(3).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("present"));
        GlProgram<Object> program = this.linked();

        for (String name : new String[]{"present", "missing"}) {
            assertEquals(NULL_FACTORY, assertThrows(NullPointerException.class, () -> program.bindUniformIfPresent(name, null)).getMessage());
            assertEquals(NULL_FACTORY, assertThrows(NullPointerException.class, () -> program.bindUniform(name, null)).getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Returns null for every negative location without calling the factory
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void uniformReturnsNullForEveryNegativeLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        GlProgram<Object> program = this.linked();
        AtomicInteger calls = new AtomicInteger();

        assertNull(program.bindUniformIfPresent("u", index -> {
            calls.incrementAndGet();

            return new GlUniformInt(index);
        }));

        assertEquals(0, calls.get());
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "u");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Passes every other location to the factory and returns its result
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1023, Integer.MAX_VALUE})
    void uniformPassesEveryOtherLocationToTheFactory(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        GlProgram<Object> program = this.linked();
        GlUniformInt created = new GlUniformInt(0);
        List<Integer> seen = new ArrayList<>();

        assertSame(created, program.bindUniformIfPresent("u", index -> {
            seen.add(index);

            return created;
        }));

        assertEquals(List.of(location), seen);
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "u");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Returns null for a present uniform whose factory returns null and the required lookup rejects it
    @Test
    void uniformReturnsNullWhenTheFactoryReturnsNull() {
        Mockito.doReturn(2).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        GlProgram<Object> program = this.linked();
        IntFunction<GlUniformInt> factory = index -> null;

        assertNull(program.bindUniformIfPresent("u", factory));
        assertEquals("No uniform exists with name: u", assertThrows(NullPointerException.class, () -> program.bindUniform("u", factory)).getMessage());
    }

    // Rethrows the same throwable that the uniform factory throws
    @Test
    void uniformRethrowsWhatTheFactoryThrows() {
        Mockito.doReturn(2).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        GlProgram<Object> program = this.linked();
        IllegalArgumentException error = new IllegalArgumentException("factory");

        assertSame(error, assertThrows(IllegalArgumentException.class, () -> program.bindUniformIfPresent("u", index -> {
            throw error;
        })));
    }

    // Rejects a deleted program without a GL call or a factory call
    @Test
    void uniformRejectsADeletedProgramWithoutAGlCall() {
        GlProgram<Object> program = this.linked();
        program.delete();
        Mockito.clearInvocations(this.gl);
        AtomicInteger calls = new AtomicInteger();
        IntFunction<GlUniformInt> factory = index -> {
            calls.incrementAndGet();

            return new GlUniformInt(index);
        };

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> program.bindUniformIfPresent("u", factory)).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> program.bindUniform("u", factory)).getMessage());
        assertEquals(0, calls.get());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Queries GL on every call and returns a new uniform each time
    @Test
    void uniformQueriesGlOnEveryCall() {
        Mockito.doReturn(4).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        GlProgram<Object> program = this.linked();

        GlUniformInt first = program.bindUniform("u", GlUniformInt::new);
        GlUniformInt second = program.bindUniform("u", GlUniformInt::new);
        GlUniformInt third = program.bindUniformIfPresent("u", GlUniformInt::new);

        assertNotSame(first, second);
        assertNotSame(second, third);
        Mockito.verify(this.gl, Mockito.times(3)).glGetUniformLocation(PROGRAM, "u");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a missing uniform in the required lookup and passes an empty name to GL
    @Test
    void requiredUniformRejectsAMissingName() {
        GlProgram<Object> program = this.linked();

        assertEquals("No uniform exists with name: missing", assertThrows(NullPointerException.class, () -> program.bindUniform("missing", GlUniformInt::new)).getMessage());
        assertNull(program.bindUniformIfPresent("", GlUniformInt::new));
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "missing");
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null block name before the binding point and without a GL call
    @Test
    void blockRejectsANullNameBeforeTheBindingPoint() {
        GlProgram<Object> program = this.linked();

        for (int binding : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> program.bindUniformBlockIfPresent(null, binding)).getMessage());
            assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> program.bindUniformBlock(null, binding)).getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every negative binding point for a present or missing block without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void blockRejectsEveryNegativeBindingPointWithoutAGlCall(int binding) {
        Mockito.doReturn(1).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlProgram<Object> program = this.linked();

        for (String name : new String[]{"Block", "Missing"}) {
            assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> program.bindUniformBlockIfPresent(name, binding)).getMessage());
            assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> program.bindUniformBlock(name, binding)).getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Returns null for every negative block index without assigning a binding point
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void blockReturnsNullForEveryNegativeIndex(int index) {
        Mockito.doReturn(index).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlProgram<Object> program = this.linked();

        assertNull(program.bindUniformBlockIfPresent("Block", 2));
        assertEquals("No uniform block exists with name: Block", assertThrows(NullPointerException.class, () -> program.bindUniformBlock("Block", 2)).getMessage());
        Mockito.verify(this.gl, Mockito.times(2)).glGetUniformBlockIndex(PROGRAM, "Block");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Assigns the binding point for every other block index and returns a block on that binding point
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 35", "7, 0", "2147483647, 2147483647", "0, 2147483647"})
    void blockAssignsTheBindingPointForEveryOtherIndex(int index, int binding) {
        Mockito.doReturn(index).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlProgram<Object> program = this.linked();

        GlUniformBlock block = program.bindUniformBlock("Block", binding);
        block.bindBuffer(buffer);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glGetUniformBlockIndex(PROGRAM, "Block");
        order.verify(this.gl).glUniformBlockBinding(PROGRAM, index, binding);
        order.verify(this.gl).glBindBufferBase(UNIFORM_BUFFER, binding, buffer.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Returns a new block on every call and assigns the binding point each time
    @Test
    void blockReturnsANewBlockOnEveryCall() {
        Mockito.doReturn(3).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlProgram<Object> program = this.linked();

        assertNotSame(program.bindUniformBlockIfPresent("Block", 1), program.bindUniformBlockIfPresent("Block", 1));
        Mockito.verify(this.gl, Mockito.times(2)).glGetUniformBlockIndex(PROGRAM, "Block");
        Mockito.verify(this.gl, Mockito.times(2)).glUniformBlockBinding(PROGRAM, 3, 1);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a deleted program after the binding point check and without a GL call
    @Test
    void blockRejectsADeletedProgramWithoutAGlCall() {
        GlProgram<Object> program = this.linked();
        program.delete();
        Mockito.clearInvocations(this.gl);

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> program.bindUniformBlockIfPresent("Block", 0)).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> program.bindUniformBlock("Block", 0)).getMessage());
        assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> program.bindUniformBlockIfPresent("Block", -1)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a null shader without a GL call
    @Test
    void attachRejectsANullShaderWithoutAGlCall() {
        GlProgram.Builder builder = this.builder();

        assertEquals(NULL_SHADER, assertThrows(NullPointerException.class, () -> builder.attachShader(null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a deleted shader without a GL call
    @Test
    void attachRejectsADeletedShaderWithoutAGlCall() {
        GlShader shader = this.shader();
        shader.delete();
        GlProgram.Builder builder = this.builder();

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> builder.attachShader(shader)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Attaches the shader on every call and returns the same builder
    @Test
    void attachAttachesTheShaderAndReturnsTheBuilder() {
        GlShader shader = this.shader();
        GlProgram.Builder builder = this.builder();

        assertSame(builder, builder.attachShader(shader));
        assertSame(builder, builder.attachShader(shader));

        Mockito.verify(this.gl, Mockito.times(2)).glAttachShader(PROGRAM, shader.handle());
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null factory without a GL call and keeps the builder open
    @Test
    void linkRejectsANullFactoryAndKeepsTheBuilderOpen() {
        GlProgram.Builder builder = this.builder();

        assertEquals(NULL_FACTORY, assertThrows(NullPointerException.class, () -> builder.link(null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
        assertEquals(PROGRAM, builder.link(c -> null).handle());
    }

    // Links then reads the log then checks the status and passes the returned program to the factory
    @Test
    void linkLinksReadsTheLogAndChecksTheStatusInOrder() {
        GlProgram.Builder builder = this.builder();
        AtomicReference<ShaderBindingContext> context = new AtomicReference<>();

        GlProgram<String> program = builder.link(c -> {
            context.set(c);

            return "iface";
        });

        assertSame(program, context.get());
        assertEquals("iface", program.getInterface());
        assertEquals(PROGRAM, program.handle());
        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glLinkProgram(PROGRAM);
        order.verify(this.gl).glGetProgramInfoLog(PROGRAM, INFO_LOG_LENGTH);
        order.verify(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Deletes the program and closes the builder for every status but true
    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void linkFailsAndDeletesTheProgramForEveryStatusButTrue(int status) {
        GlProgram.Builder builder = this.builder();
        Mockito.doReturn(status).when(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
        AtomicInteger calls = new AtomicInteger();

        RuntimeException error = assertThrows(RuntimeException.class, () -> builder.link(c -> calls.incrementAndGet()));

        assertEquals(RuntimeException.class, error.getClass());
        assertEquals(LINK_FAILED, error.getMessage());
        assertEquals(0, calls.get());
        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glLinkProgram(PROGRAM);
        order.verify(this.gl).glGetProgramInfoLog(PROGRAM, INFO_LOG_LENGTH);
        order.verify(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
        order.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(LINKED, assertThrows(IllegalStateException.class, () -> builder.bindAttribute("a", 0)).getMessage());
    }

    // Deletes the program and rethrows the same throwable when the link call throws
    @ParameterizedTest
    @MethodSource("throwables")
    void linkDeletesTheProgramWhenGlThrows(Throwable error) {
        GlProgram.Builder builder = this.builder();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glLinkProgram(PROGRAM);

        assertSame(error, thrown(() -> builder.link(c -> null)));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glLinkProgram(PROGRAM);
        order.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(LINKED, assertThrows(IllegalStateException.class, () -> builder.link(c -> null)).getMessage());
    }

    // Deletes the program once and rethrows the same throwable when the factory throws
    @ParameterizedTest
    @MethodSource("throwables")
    void linkDeletesTheProgramWhenTheFactoryThrows(Throwable error) {
        GlProgram.Builder builder = this.builder();

        assertSame(error, thrown(() -> builder.link(c -> {
            throw sneaky(error);
        })));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glLinkProgram(PROGRAM);
        order.verify(this.gl).glGetProgramInfoLog(PROGRAM, INFO_LOG_LENGTH);
        order.verify(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
        order.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(LINKED, assertThrows(IllegalStateException.class, () -> builder.link(c -> null)).getMessage());
    }

    // Deletes the program when a required uniform is missing during the factory
    @Test
    void linkDeletesTheProgramWhenARequiredUniformIsMissing() {
        GlProgram.Builder builder = this.builder();

        NullPointerException error = assertThrows(NullPointerException.class, () -> builder.link(c -> c.bindUniform("missing", GlUniformInt::new)));

        assertEquals("No uniform exists with name: missing", error.getMessage());
        Mockito.verify(this.gl).glDeleteProgram(PROGRAM);
    }

    // Rejects a second link without a GL call and leaves the first program intact
    @Test
    void linkRejectsASecondLinkWithoutAGlCall() {
        GlProgram.Builder builder = this.builder();
        GlProgram<Object> program = builder.link(c -> null);
        Mockito.clearInvocations(this.gl);

        assertEquals(LINKED, assertThrows(IllegalStateException.class, () -> builder.link(c -> null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
        assertEquals(PROGRAM, program.handle());
        program.delete();
        Mockito.verify(this.gl).glDeleteProgram(PROGRAM);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Warns once with the name and the log for a non empty log
    @Test
    void linkWarnsWithTheNameAndTheLog() {
        Mockito.doReturn("warning text").when(this.gl).glGetProgramInfoLog(Mockito.anyInt(), Mockito.anyInt());

        withLogger(logger -> {
            GlProgram.builder("named").link(c -> null);

            Mockito.verify(logger).warn(LOG_PATTERN, "named", "warning text");
            Mockito.verifyNoMoreInteractions(logger);
        });
    }

    // Writes no warning for an empty log
    @Test
    void linkWritesNoWarningForAnEmptyLog() {
        withLogger(logger -> {
            GlProgram.builder("named").link(c -> null);

            Mockito.verifyNoInteractions(logger);
        });
    }

    // Warns with the log before a failed link throws
    @Test
    void linkWarnsBeforeAFailedLink() {
        Mockito.doReturn("error text").when(this.gl).glGetProgramInfoLog(Mockito.anyInt(), Mockito.anyInt());
        Mockito.doReturn(0).when(this.gl).glGetProgrami(Mockito.anyInt(), Mockito.eq(LINK_STATUS));

        withLogger(logger -> {
            assertEquals(LINK_FAILED, assertThrows(RuntimeException.class, () -> GlProgram.builder("broken").link(c -> null)).getMessage());

            Mockito.verify(logger).warn(LOG_PATTERN, "broken", "error text");
            Mockito.verifyNoMoreInteractions(logger);
        });
    }

    // Formats the warning to the name and log text verbatim for logs and names that hold format markers
    @ParameterizedTest
    @ValueSource(strings = {"{}", "\\{}", "a {} b {} c", "%s %d %n", "$1 \\1", "first line\nsecond line", " ", "{", "}"})
    void linkWarningKeepsTheNameAndLogVerbatim(String text) {
        Mockito.doReturn(text).when(this.gl).glGetProgramInfoLog(Mockito.anyInt(), Mockito.anyInt());
        String name = "name {} " + text;

        withLogger(logger -> {
            GlProgram.builder(name).link(c -> null);

            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> first = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Object> second = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(pattern.capture(), first.capture(), second.capture());
            String formatted = new ParameterizedMessage(pattern.getValue(), first.getValue(), second.getValue()).getFormattedMessage();
            assertEquals("Program link log for " + name + ": " + text, formatted);
        });
    }

    // Rejects a null attribute name before the location and without a GL call
    @Test
    void attributeRejectsANullNameWithoutAGlCall() {
        GlProgram.Builder builder = this.builder();

        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> builder.bindAttribute(null, 0)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> builder.bindAttribute(null, -1)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every negative attribute location without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void attributeRejectsEveryNegativeLocationWithoutAGlCall(int index) {
        GlProgram.Builder builder = this.builder();

        assertEquals(NEGATIVE_ATTRIBUTE, assertThrows(IllegalArgumentException.class, () -> builder.bindAttribute("a", index)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Binds every other attribute location and returns the same builder
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 15, 16, Integer.MAX_VALUE})
    void attributeBindsEveryOtherLocation(int index) {
        GlProgram.Builder builder = this.builder();

        assertSame(builder, builder.bindAttribute("a_Pos", index));

        Mockito.verify(this.gl).glBindAttribLocation(PROGRAM, index, "a_Pos");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Passes repeated and empty attribute names to GL on every call
    @Test
    void attributePassesRepeatedAndEmptyNamesToGl() {
        GlProgram.Builder builder = this.builder();

        builder.bindAttribute("a", 0).bindAttribute("a", 0).bindAttribute("", 1);

        Mockito.verify(this.gl, Mockito.times(2)).glBindAttribLocation(PROGRAM, 0, "a");
        Mockito.verify(this.gl).glBindAttribLocation(PROGRAM, 1, "");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a null format before the first location and without a GL call
    @Test
    void attributesRejectANullFormatWithoutAGlCall() {
        GlProgram.Builder builder = this.builder();

        assertEquals(NULL_FORMAT, assertThrows(NullPointerException.class, () -> builder.bindAttributes(null, 0)).getMessage());
        assertEquals(NULL_FORMAT, assertThrows(NullPointerException.class, () -> builder.bindAttributes(null, -1)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every negative first location for empty and filled formats without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void attributesRejectEveryNegativeFirstLocationWithoutAGlCall(int first) {
        GlProgram.Builder builder = this.builder();

        for (int count : new int[]{0, 1, 3}) {
            GlVertexFormat format = format(count);
            Mockito.clearInvocations(this.gl);
            assertEquals(NEGATIVE_ATTRIBUTE, assertThrows(IllegalArgumentException.class, () -> builder.bindAttributes(format, first)).getMessage());
            Mockito.verifyNoInteractions(this.gl);
        }
    }

    // Binds consecutive locations in declaration order and returns the same builder
    @Test
    void attributesBindConsecutiveLocationsInDeclarationOrder() {
        GlVertexFormat format = format(4);
        GlProgram.Builder builder = this.builder();

        assertSame(builder, builder.bindAttributes(format, 3));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 3, "a0");
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 4, "a1");
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 5, "a2");
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 6, "a3");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Binds nothing for an empty format at every non negative first location
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void attributesBindNothingForAnEmptyFormat(int first) {
        GlVertexFormat format = format(0);
        GlProgram.Builder builder = this.builder();

        assertSame(builder, builder.bindAttributes(format, first));
        Mockito.verifyNoInteractions(this.gl);
    }

    // Binds a last location equal to the largest int
    @ParameterizedTest
    @CsvSource({"1, 2147483647", "2, 2147483646", "3, 2147483645"})
    void attributesReachTheLargestIntLocation(int count, int first) {
        GlVertexFormat format = format(count);
        GlProgram.Builder builder = this.builder();

        builder.bindAttributes(format, first);

        for (int i = 0; i < count; i++) {
            Mockito.verify(this.gl).glBindAttribLocation(PROGRAM, first + i, "a" + i);
        }

        Mockito.verify(this.gl).glBindAttribLocation(PROGRAM, Integer.MAX_VALUE, "a" + (count - 1));
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a last location past the int range without a GL call
    @ParameterizedTest
    @CsvSource({"2, 2147483647", "3, 2147483646", "3, 2147483647", "4, 2147483645"})
    void attributesRejectALastLocationPastTheIntRange(int count, int first) {
        GlVertexFormat format = format(count);
        GlProgram.Builder builder = this.builder();

        assertEquals(LAST_ATTRIBUTE, assertThrows(IllegalArgumentException.class, () -> builder.bindAttributes(format, first)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Stops at a null attribute after binding the attributes before it and keeps the builder open
    @Test
    void attributesStopAtANullAttribute() {
        Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributes = new Reference2ReferenceLinkedOpenHashMap<>();
        attributes.put("a0", new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, "a0", 1, false, 0, 4, false));
        attributes.put("a1", null);
        GlVertexFormat format = new GlVertexFormat(attributes, 4);
        GlProgram.Builder builder = this.builder();

        assertThrows(NullPointerException.class, () -> builder.bindAttributes(format, 0));

        Mockito.verify(this.gl).glBindAttribLocation(PROGRAM, 0, "a0");
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(PROGRAM, builder.link(c -> null).handle());
    }

    // Rejects a null fragment output name before the color number and without a GL call
    @Test
    void fragmentRejectsANullNameWithoutAGlCall() {
        GlProgram.Builder builder = this.builder();

        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> builder.bindFragmentData(null, 0)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> builder.bindFragmentData(null, -1)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects every negative color number without a GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void fragmentRejectsEveryNegativeColorNumberWithoutAGlCall(int index) {
        GlProgram.Builder builder = this.builder();

        assertEquals(NEGATIVE_FRAGMENT, assertThrows(IllegalArgumentException.class, () -> builder.bindFragmentData("fragColor", index)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Binds every other color number and returns the same builder
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, Integer.MAX_VALUE})
    void fragmentBindsEveryOtherColorNumber(int index) {
        GlProgram.Builder builder = this.builder();

        assertSame(builder, builder.bindFragmentData("fragColor", index));
        assertSame(builder, builder.bindFragmentData("", index));

        Mockito.verify(this.gl).glBindFragDataLocation(PROGRAM, index, "fragColor");
        Mockito.verify(this.gl).glBindFragDataLocation(PROGRAM, index, "");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects every builder step after a successful or failed link without a GL call
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void builderRejectsEveryStepAfterLinkWithoutAGlCall(boolean linkSucceeds) {
        GlShader shader = this.shader();
        GlVertexFormat filled = format(2);
        GlVertexFormat empty = format(0);
        GlProgram.Builder builder = this.builder();

        if (linkSucceeds) {
            builder.link(c -> null);
        } else {
            Mockito.doReturn(0).when(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
            assertThrows(RuntimeException.class, () -> builder.link(c -> null));
        }

        Mockito.clearInvocations(this.gl);
        List<Executable> steps = List.of(
                () -> builder.attachShader(shader),
                () -> builder.bindAttribute("a", 0),
                () -> builder.bindAttributes(filled, 0),
                () -> builder.bindAttributes(empty, 0),
                () -> builder.bindFragmentData("fragColor", 0),
                () -> builder.link(c -> null));

        for (Executable step : steps) {
            assertEquals(LINKED, assertThrows(IllegalStateException.class, step).getMessage());
        }

        Mockito.verifyNoInteractions(this.gl);
    }

    // Checks arguments before the linked state of the builder
    @Test
    void builderChecksArgumentsBeforeTheLinkedState() {
        GlProgram.Builder builder = this.builder();
        builder.link(c -> null);
        Mockito.clearInvocations(this.gl);

        assertInstanceOf(NullPointerException.class, thrown(() -> builder.attachShader(null)));
        assertInstanceOf(NullPointerException.class, thrown(() -> builder.link(null)));
        assertInstanceOf(NullPointerException.class, thrown(() -> builder.bindAttributes(null, 0)));
        assertInstanceOf(IllegalArgumentException.class, thrown(() -> builder.bindAttribute("a", -1)));
        assertInstanceOf(IllegalArgumentException.class, thrown(() -> builder.bindAttributes(format(2), Integer.MAX_VALUE)));
        assertInstanceOf(IllegalArgumentException.class, thrown(() -> builder.bindFragmentData("fragColor", -1)));
        Mockito.verifyNoInteractions(this.gl);
    }

    // Sends every builder step to GL in call order
    @Test
    void builderChainReachesGlInCallOrder() {
        GlShader shader = this.shader();
        GlVertexFormat format = format(2);
        GlProgram.Builder builder = this.builder();

        GlProgram<Object> program = builder.attachShader(shader)
                .bindAttribute("a_Extra", 9)
                .bindAttributes(format, 0)
                .bindFragmentData("fragColor", 0)
                .link(c -> null);

        assertEquals(PROGRAM, program.handle());
        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glAttachShader(PROGRAM, shader.handle());
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 9, "a_Extra");
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 0, "a0");
        order.verify(this.gl).glBindAttribLocation(PROGRAM, 1, "a1");
        order.verify(this.gl).glBindFragDataLocation(PROGRAM, 0, "fragColor");
        order.verify(this.gl).glLinkProgram(PROGRAM);
        order.verify(this.gl).glGetProgramInfoLog(PROGRAM, INFO_LOG_LENGTH);
        order.verify(this.gl).glGetProgrami(PROGRAM, LINK_STATUS);
        Mockito.verifyNoMoreInteractions(this.gl);
    }
}
