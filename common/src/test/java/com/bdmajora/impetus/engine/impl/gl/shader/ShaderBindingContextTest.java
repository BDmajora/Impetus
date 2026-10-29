package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniform;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of the required uniform and block lookups of the shader binding context
class ShaderBindingContextTest {
    // Message of a null name
    private static final String NULL_NAME = "Name must not be null";
    // Message of a null factory
    private static final String NULL_FACTORY = "Factory must not be null";
    // Message of a negative binding point
    private static final String BAD_BINDING = "Uniform block binding must not be negative";
    // Message of a deleted program
    private static final String BAD_HANDLE = "Handle is not valid";
    // Message prefix of a missing uniform
    private static final String MISSING_UNIFORM = "No uniform exists with name: ";
    // Message prefix of a missing uniform block
    private static final String MISSING_BLOCK = "No uniform block exists with name: ";
    // Program name that the stubbed driver hands out
    private static final int PROGRAM = 17;
    // Binding point that no lookup in these tests records
    private static final int NO_BINDING = Integer.MIN_VALUE;

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Error type that no lookup code throws on its own
    private static final class Fault extends Error {
        // Creates the error with the message
        private Fault(String message) {
            super(message);
        }
    }

    // Uniform that exposes its location
    private static final class Probe extends GlUniform<Object> {
        // Creates the uniform at the location
        private Probe(int location) {
            super(location);
        }

        // Returns the location of the uniform
        private int location() {
            return this.index;
        }

        // Uploads nothing
        @Override
        public void set(Object value) {
        }
    }

    // Implementation that records each optional lookup and answers with the configured result
    private static final class Stub implements ShaderBindingContext {
        // Uniform that the optional uniform lookup returns
        private GlUniform<?> uniform;
        // Location at which the optional uniform lookup applies the factory or null to return the uniform field
        private Integer location;
        // Block that the optional block lookup returns
        private GlUniformBlock block;
        // Throwable that both optional lookups throw or null to return normally
        private Throwable failure;
        // Number of optional uniform lookups
        private int uniformCalls;
        // Number of optional block lookups
        private int blockCalls;
        // Name of the last optional lookup
        private String name;
        // Factory of the last optional uniform lookup
        private IntFunction<?> factory;
        // Binding point of the last optional block lookup
        private int bindingPoint = NO_BINDING;

        // Records the lookup and returns the configured uniform or the uniform that the factory builds
        @Override
        @SuppressWarnings("unchecked")
        public <U extends GlUniform<?>> U bindUniformIfPresent(String name, IntFunction<U> factory) {
            this.uniformCalls++;
            this.name = name;
            this.factory = factory;

            if (this.failure != null) {
                throw sneaky(this.failure);
            }

            if (this.location != null) {
                return factory.apply(this.location);
            }

            return (U) this.uniform;
        }

        // Records the lookup and returns the configured block
        @Override
        public GlUniformBlock bindUniformBlockIfPresent(String name, int bindingPoint) {
            this.blockCalls++;
            this.name = name;
            this.bindingPoint = bindingPoint;

            if (this.failure != null) {
                throw sneaky(this.failure);
            }

            return this.block;
        }
    }

    // Returns one fresh throwable of each kind that an implementation can raise
    static Stream<Throwable> throwables() {
        return Stream.of(new RuntimeException("runtime"), new IllegalStateException("state"), new NullPointerException("null"),
                new Fault("error"), new StackOverflowError("stack"), new IOException("checked"));
    }

    // Returns names of every length class and character class that a lookup can report
    static Stream<String> names() {
        return Stream.of("", "a", " ", "u_Color", "null", "two words", "Block.member[3]", String.valueOf((char) 0),
                Character.toString(0x1F600), String.valueOf((char) 0xD800), "x".repeat(1 << 20));
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

    // Returns the message of the null pointer exception that the body throws
    private static String npe(Executable body) {
        return assertThrows(NullPointerException.class, body).getMessage();
    }

    // Returns a factory that counts its calls in the counter
    private static IntFunction<Probe> counting(AtomicInteger calls) {
        return location -> {
            calls.incrementAndGet();

            return new Probe(location);
        };
    }

    // Returns a linked program over the stubbed program name with no recorded GL calls
    private GlProgram<Object> linked() {
        Mockito.doReturn(PROGRAM).when(this.gl).glCreateProgram();
        GlProgram<Object> program = GlProgram.builder("program").link(context -> null);
        Mockito.clearInvocations(this.gl);

        return program;
    }

    // Declares the two optional lookups as abstract methods and the two required lookups as default methods
    @Test
    void declaresFourLookupsAndNoFields() throws NoSuchMethodException {
        Class<ShaderBindingContext> type = ShaderBindingContext.class;
        Method uniform = type.getMethod("bindUniform", String.class, IntFunction.class);
        Method optionalUniform = type.getMethod("bindUniformIfPresent", String.class, IntFunction.class);
        Method block = type.getMethod("bindUniformBlock", String.class, int.class);
        Method optionalBlock = type.getMethod("bindUniformBlockIfPresent", String.class, int.class);

        assertTrue(type.isInterface());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(0, Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).count());
        assertEquals(4, Arrays.stream(type.getDeclaredMethods()).filter(method -> !method.isSynthetic()).count());
        assertTrue(uniform.isDefault());
        assertTrue(block.isDefault());
        assertTrue(Modifier.isAbstract(optionalUniform.getModifiers()));
        assertTrue(Modifier.isAbstract(optionalBlock.getModifiers()));
        assertEquals(GlUniform.class, uniform.getReturnType());
        assertEquals(GlUniform.class, optionalUniform.getReturnType());
        assertEquals(GlUniformBlock.class, block.getReturnType());
        assertEquals(GlUniformBlock.class, optionalBlock.getReturnType());
        assertEquals(0, uniform.getExceptionTypes().length + block.getExceptionTypes().length);
    }

    // Returns the uniform of the optional lookup and passes the same name and factory to it once
    @Test
    void uniformReturnsTheUniformOfTheOptionalLookup() {
        Stub stub = new Stub();
        GlUniformInt expected = new GlUniformInt(3);
        stub.uniform = expected;
        String name = new String("u_Color");
        IntFunction<GlUniformInt> factory = GlUniformInt::new;

        assertSame(expected, stub.bindUniform(name, factory));
        assertEquals(1, stub.uniformCalls);
        assertEquals(0, stub.blockCalls);
        assertSame(name, stub.name);
        assertSame(factory, stub.factory);
    }

    // Returns the uniform that the factory builds at every location that a uniform accepts
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void uniformReturnsTheUniformThatTheFactoryBuilds(int location) {
        Stub stub = new Stub();
        stub.location = location;
        AtomicInteger calls = new AtomicInteger();

        Probe probe = stub.bindUniform("u", counting(calls));

        assertEquals(location, probe.location());
        assertEquals(1, calls.get());
        assertEquals(1, stub.uniformCalls);
    }

    // Propagates the refusal of a location below minus one from the factory unchanged
    @ParameterizedTest
    @ValueSource(ints = {-2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void uniformPropagatesTheRefusalOfTheFactory(int location) {
        Stub stub = new Stub();
        stub.location = location;
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> stub.bindUniform("u", counting(calls)));
        assertEquals(1, calls.get());
        assertEquals(1, stub.uniformCalls);
    }

    // Leaves every factory call to the optional lookup
    @Test
    void uniformNeverAppliesTheFactoryItself() {
        Stub stub = new Stub();
        stub.uniform = new GlUniformInt(0);
        AtomicInteger calls = new AtomicInteger();

        assertSame(stub.uniform, stub.bindUniform("u", counting(calls)));
        stub.uniform = null;
        npe(() -> stub.bindUniform("u", counting(calls)));
        assertEquals(0, calls.get());
        assertEquals(2, stub.uniformCalls);
    }

    // Rejects a missing uniform with the name in the message after one optional lookup
    @ParameterizedTest(name = "{index}")
    @MethodSource("names")
    void uniformRejectsAMissingUniformWithItsName(String name) {
        Stub stub = new Stub();

        assertEquals(MISSING_UNIFORM + name, npe(() -> stub.bindUniform(name, GlUniformInt::new)));
        assertEquals(1, stub.uniformCalls);
        assertSame(name, stub.name);
    }

    // Rejects a null name before the optional lookup whether or not a uniform is present
    @Test
    void uniformRejectsANullNameBeforeTheOptionalLookup() {
        Stub stub = new Stub();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(NULL_NAME, npe(() -> stub.bindUniform(null, counting(calls))));
        stub.uniform = new GlUniformInt(1);
        assertEquals(NULL_NAME, npe(() -> stub.bindUniform(null, counting(calls))));
        stub.location = 2;
        assertEquals(NULL_NAME, npe(() -> stub.bindUniform(null, counting(calls))));
        assertEquals(0, stub.uniformCalls);
        assertEquals(0, calls.get());
    }

    // Rejects a null factory before the optional lookup whether or not a uniform is present
    @Test
    void uniformRejectsANullFactoryBeforeTheOptionalLookup() {
        Stub stub = new Stub();

        assertEquals(NULL_FACTORY, npe(() -> stub.bindUniform("u", null)));
        stub.uniform = new GlUniformInt(1);
        assertEquals(NULL_FACTORY, npe(() -> stub.bindUniform("u", null)));
        assertEquals(0, stub.uniformCalls);
    }

    // Rejects a null name before a null factory
    @Test
    void uniformRejectsANullNameBeforeANullFactory() {
        Stub stub = new Stub();
        stub.uniform = new GlUniformInt(1);

        assertEquals(NULL_NAME, npe(() -> stub.bindUniform(null, null)));
        assertEquals(0, stub.uniformCalls);
    }

    // Propagates every throwable of the optional lookup unchanged and without suppressed throwables
    @ParameterizedTest
    @MethodSource("throwables")
    void uniformPropagatesTheThrowableOfTheOptionalLookup(Throwable failure) {
        Stub stub = new Stub();
        stub.failure = failure;

        Throwable thrown = thrown(() -> stub.bindUniform("u", GlUniformInt::new));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(1, stub.uniformCalls);
    }

    // Repeats the optional lookup on every call and follows each change of its answer
    @Test
    void uniformRepeatsTheOptionalLookupOnEveryCall() {
        Stub stub = new Stub();
        GlUniformInt first = new GlUniformInt(1);
        GlUniformInt second = new GlUniformInt(2);

        stub.uniform = first;
        assertSame(first, stub.bindUniform("u", GlUniformInt::new));
        assertSame(first, stub.bindUniform("u", GlUniformInt::new));
        stub.uniform = null;
        assertEquals(MISSING_UNIFORM + "u", npe(() -> stub.bindUniform("u", GlUniformInt::new)));
        stub.uniform = second;
        assertSame(second, stub.bindUniform("v", GlUniformInt::new));
        assertEquals("v", stub.name);
        assertEquals(4, stub.uniformCalls);
    }

    // Returns the block of the optional lookup and passes the same name and binding point to it once
    @Test
    void blockReturnsTheBlockOfTheOptionalLookup() {
        Stub stub = new Stub();
        GlUniformBlock expected = new GlUniformBlock(5);
        stub.block = expected;
        String name = new String("Block");

        assertSame(expected, stub.bindUniformBlock(name, 5));
        assertEquals(1, stub.blockCalls);
        assertEquals(0, stub.uniformCalls);
        assertSame(name, stub.name);
        assertEquals(5, stub.bindingPoint);
    }

    // Passes every binding point between 0 and the int maximum to the optional lookup unchanged
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void blockPassesEveryValidBindingPointUnchanged(int binding) {
        Stub stub = new Stub();
        stub.block = new GlUniformBlock(binding);

        assertSame(stub.block, stub.bindUniformBlock("Block", binding));
        assertEquals(binding, stub.bindingPoint);
        assertEquals(1, stub.blockCalls);
    }

    // Rejects every negative binding point before the optional lookup whether or not a block is present
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void blockRejectsANegativeBindingPointBeforeTheOptionalLookup(int binding) {
        Stub stub = new Stub();

        assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> stub.bindUniformBlock("Block", binding)).getMessage());
        stub.block = new GlUniformBlock(0);
        assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> stub.bindUniformBlock("Block", binding)).getMessage());
        assertEquals(0, stub.blockCalls);
        assertEquals(NO_BINDING, stub.bindingPoint);
    }

    // Rejects a null name before the binding point and before the optional lookup
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void blockRejectsANullNameBeforeTheBindingPoint(int binding) {
        Stub stub = new Stub();

        assertEquals(NULL_NAME, npe(() -> stub.bindUniformBlock(null, binding)));
        stub.block = new GlUniformBlock(0);
        assertEquals(NULL_NAME, npe(() -> stub.bindUniformBlock(null, binding)));
        assertEquals(0, stub.blockCalls);
    }

    // Rejects a missing block with the name in the message after one optional lookup
    @ParameterizedTest(name = "{index}")
    @MethodSource("names")
    void blockRejectsAMissingBlockWithItsName(String name) {
        Stub stub = new Stub();

        assertEquals(MISSING_BLOCK + name, npe(() -> stub.bindUniformBlock(name, 0)));
        assertEquals(1, stub.blockCalls);
        assertSame(name, stub.name);
    }

    // Propagates every throwable of the optional lookup unchanged and without suppressed throwables
    @ParameterizedTest
    @MethodSource("throwables")
    void blockPropagatesTheThrowableOfTheOptionalLookup(Throwable failure) {
        Stub stub = new Stub();
        stub.failure = failure;

        Throwable thrown = thrown(() -> stub.bindUniformBlock("Block", 0));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(1, stub.blockCalls);
    }

    // Repeats the optional lookup on every call and follows each change of its answer
    @Test
    void blockRepeatsTheOptionalLookupOnEveryCall() {
        Stub stub = new Stub();
        GlUniformBlock first = new GlUniformBlock(1);
        GlUniformBlock second = new GlUniformBlock(2);

        stub.block = first;
        assertSame(first, stub.bindUniformBlock("Block", 1));
        assertSame(first, stub.bindUniformBlock("Block", 1));
        stub.block = null;
        assertEquals(MISSING_BLOCK + "Block", npe(() -> stub.bindUniformBlock("Block", 1)));
        stub.block = second;
        assertSame(second, stub.bindUniformBlock("Other", 2));
        assertEquals("Other", stub.name);
        assertEquals(2, stub.bindingPoint);
        assertEquals(4, stub.blockCalls);
    }

    // Keeps the uniform and block lookups of one context apart
    @Test
    void uniformAndBlockLookupsStayApart() {
        Stub stub = new Stub();
        stub.uniform = new GlUniformInt(0);
        stub.block = new GlUniformBlock(0);

        assertNotNull(stub.bindUniform("u", GlUniformInt::new));
        assertEquals(NO_BINDING, stub.bindingPoint);
        assertNotNull(stub.bindUniformBlock("Block", 0));
        assertEquals(1, stub.uniformCalls);
        assertEquals(1, stub.blockCalls);
    }

    // Returns the program uniform at the GL location with one query per call
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programUniformReadsOneLocationPerCall(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        ShaderBindingContext context = this.linked();

        Probe probe = context.bindUniform("u", Probe::new);

        assertEquals(location, probe.location());
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "u");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a uniform that the program lacks with one query and no factory call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE})
    void programUniformRejectsAMissingLocation(int location) {
        Mockito.doReturn(location).when(this.gl).glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u"));
        ShaderBindingContext context = this.linked();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(MISSING_UNIFORM + "u", npe(() -> context.bindUniform("u", counting(calls))));
        assertEquals(0, calls.get());
        Mockito.verify(this.gl).glGetUniformLocation(PROGRAM, "u");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Assigns the binding point of the program block with one index query and one binding call
    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void programBlockAssignsTheBindingPoint(int binding) {
        Mockito.doReturn(3).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        ShaderBindingContext context = this.linked();

        assertNotNull(context.bindUniformBlock("Block", binding));
        Mockito.verify(this.gl).glGetUniformBlockIndex(PROGRAM, "Block");
        Mockito.verify(this.gl).glUniformBlockBinding(PROGRAM, 3, binding);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Rejects a block that the program lacks with one index query and no binding call
    @Test
    void programBlockRejectsAMissingBlock() {
        Mockito.doReturn(-1).when(this.gl).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        ShaderBindingContext context = this.linked();

        assertEquals(MISSING_BLOCK + "Block", npe(() -> context.bindUniformBlock("Block", 0)));
        assertNull(context.bindUniformBlockIfPresent("Block", 0));
        Mockito.verify(this.gl, Mockito.times(2)).glGetUniformBlockIndex(PROGRAM, "Block");
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Checks the arguments before the state of a deleted program and then rejects the deleted program without a GL call
    @Test
    void programChecksTheArgumentsBeforeItsDeletedState() {
        GlProgram<Object> program = this.linked();
        program.delete();
        Mockito.clearInvocations(this.gl);
        ShaderBindingContext context = program;
        AtomicInteger calls = new AtomicInteger();

        assertEquals(NULL_NAME, npe(() -> context.bindUniform(null, counting(calls))));
        assertEquals(NULL_FACTORY, npe(() -> context.bindUniform("u", null)));
        assertEquals(NULL_NAME, npe(() -> context.bindUniformBlock(null, -1)));
        assertEquals(BAD_BINDING, assertThrows(IllegalArgumentException.class, () -> context.bindUniformBlock("Block", -1)).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> context.bindUniform("u", counting(calls))).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> context.bindUniformBlock("Block", 0)).getMessage());
        assertEquals(0, calls.get());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Lets an escaped context of a program whose factory failed report the deleted program
    @Test
    void escapedContextOfAFailedProgramReportsTheDeletedProgram() {
        Mockito.doReturn(PROGRAM).when(this.gl).glCreateProgram();
        ShaderBindingContext[] escaped = new ShaderBindingContext[1];
        GlProgram.Builder builder = GlProgram.builder("program");

        Throwable failure = thrown(() -> builder.link(context -> {
            escaped[0] = context;

            return context.bindUniform("missing", GlUniformInt::new);
        }));

        assertInstanceOf(NullPointerException.class, failure);
        assertEquals(MISSING_UNIFORM + "missing", failure.getMessage());
        assertNotNull(escaped[0]);
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> escaped[0].bindUniform("u", GlUniformInt::new)).getMessage());
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> escaped[0].bindUniformBlock("Block", 0)).getMessage());
    }
}
