package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL20;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Tests of shader compilation and the shader name and deletion
class GlShaderTest {
    // Shader name that the stubbed driver hands out
    private static final int SHADER = 23;
    // GL type of a vertex shader
    private static final int VERTEX = ShaderType.VERTEX.id;
    // GL query of the compile status
    private static final int COMPILE_STATUS = GL20.GL_COMPILE_STATUS;
    // Maximum compile log length that the shader asks for
    private static final int INFO_LOG_LENGTH = 4096;
    // Pattern of the compile log warning
    private static final String LOG_PATTERN = "Shader compilation log for {}: {}";
    // Message of a null type
    private static final String NULL_TYPE = "Type must not be null";
    // Message of a null name
    private static final String NULL_NAME = "Name must not be null";
    // Message of a null source
    private static final String NULL_SOURCE = "Source must not be null";
    // Message of a zero shader name from the driver
    private static final String CREATE_FAILED = "Failed to create a shader";
    // Message of a failed compile
    private static final String COMPILE_FAILED = "Shader compilation failed, see log for details";
    // Message of a deleted GL object
    private static final String BAD_HANDLE = "Handle is not valid";

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();

    // Error type that no GL code throws on its own
    private static final class Fault extends Error {
        // Creates the error with the message
        private Fault(String message) {
            super(message);
        }
    }

    // Returns one fresh throwable of each kind that a GL call can raise
    static Stream<Throwable> throwables() {
        return Stream.of(new RuntimeException("runtime"), new IllegalStateException("state"), new Fault("error"), new IOException("checked"));
    }

    // Returns sources with supplementary characters and lone surrogates and NUL characters
    static Stream<String> unusualSources() {
        return Stream.of(new String(Character.toChars(0x1F600)), String.valueOf((char) 0xD800), (char) 0xDC00 + "x", "a\0b", "\0");
    }

    // Returns empty and blank and long shader names
    static Stream<String> names() {
        return Stream.of("", " ", "a.vsh", "{}", "name with spaces", "x".repeat(10_000));
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

    // Makes the driver hand out the stubbed shader name for every type
    private void stubShader() {
        Mockito.doReturn(SHADER).when(this.gl).glCreateShader(Mockito.anyInt());
    }

    // Returns a compiled shader over the stubbed shader name with no recorded GL calls
    private GlShader shader() {
        this.stubShader();
        GlShader shader = new GlShader(ShaderType.VERTEX, "shader", "");
        Mockito.clearInvocations(this.gl);

        return shader;
    }

    // Runs the body with the shader logger swapped for a mock
    private static void withLogger(Consumer<Logger> body) {
        // Runs the class initialiser before the swap
        Logger original = Statics.get(GlShader.class, "LOGGER");
        Logger mock = Mockito.mock(Logger.class);
        Statics.set(GlShader.class, "LOGGER", mock);

        try {
            body.accept(mock);
        } finally {
            Statics.set(GlShader.class, "LOGGER", original);
        }
    }

    // Rejects a null type without a GL call
    @Test
    void constructorRejectsANullTypeWithoutAGlCall() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new GlShader(null, "shader", ""));
        assertEquals(NULL_TYPE, error.getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a null name without a GL call
    @Test
    void constructorRejectsANullNameWithoutAGlCall() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new GlShader(ShaderType.VERTEX, null, ""));
        assertEquals(NULL_NAME, error.getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a null source without a GL call
    @Test
    void constructorRejectsANullSourceWithoutAGlCall() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new GlShader(ShaderType.FRAGMENT, "shader", null));
        assertEquals(NULL_SOURCE, error.getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Checks the type first and the name second and the source last
    @Test
    void constructorChecksTheTypeThenTheNameThenTheSource() {
        assertEquals(NULL_TYPE, assertThrows(NullPointerException.class, () -> new GlShader(null, null, null)).getMessage());
        assertEquals(NULL_TYPE, assertThrows(NullPointerException.class, () -> new GlShader(null, "shader", null)).getMessage());
        assertEquals(NULL_NAME, assertThrows(NullPointerException.class, () -> new GlShader(ShaderType.VERTEX, null, null)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Creates a shader of every type and uploads and compiles the source and reads the log and the status in order
    @ParameterizedTest
    @EnumSource(ShaderType.class)
    void constructorCompilesEveryStageInOrder(ShaderType type) {
        String source = "void main() {}";
        Mockito.doReturn(SHADER).when(this.gl).glCreateShader(type.id);

        GlShader shader = new GlShader(type, "stage", source);

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glCreateShader(type.id);
        order.verify(this.gl).glShaderSourceSafe(Mockito.eq(SHADER), Mockito.same(source));
        order.verify(this.gl).glCompileShader(SHADER);
        order.verify(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);
        order.verify(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(SHADER, shader.handle());
        assertEquals("stage", shader.getName());
    }

    // Stores every non zero shader name from the driver
    @ParameterizedTest
    @ValueSource(ints = {1, 2, SHADER, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void constructorStoresEveryNonZeroShaderName(int name) {
        Mockito.doReturn(name).when(this.gl).glCreateShader(VERTEX);

        GlShader shader = new GlShader(ShaderType.VERTEX, "shader", "");

        assertEquals(name, shader.handle());
        Mockito.verify(this.gl).glCompileShader(name);
        Mockito.verify(this.gl, Mockito.never()).glDeleteShader(Mockito.anyInt());
    }

    // Rejects a zero shader name from the driver without any further GL call
    @Test
    void constructorRejectsAZeroShaderNameWithoutFurtherGlCalls() {
        Mockito.doReturn(0).when(this.gl).glCreateShader(VERTEX);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new GlShader(ShaderType.VERTEX, "shader", "void main() {}"));

        assertEquals(CREATE_FAILED, error.getMessage());
        Mockito.verify(this.gl).glCreateShader(VERTEX);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Uploads empty and single character and multi line sources as the same instance
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "x", "void main() {}", "#version 120\nvoid main() {}\n", "{}", "%s"})
    void constructorUploadsTheSourceVerbatim(String source) {
        this.stubShader();

        new GlShader(ShaderType.VERTEX, "shader", source);

        Mockito.verify(this.gl).glShaderSourceSafe(Mockito.eq(SHADER), Mockito.same(source));
    }

    // Uploads sources with characters that need special UTF8 handling as the same instance
    @ParameterizedTest
    @MethodSource("unusualSources")
    void constructorUploadsUnusualCharactersVerbatim(String source) {
        this.stubShader();

        new GlShader(ShaderType.FRAGMENT, "shader", source);

        Mockito.verify(this.gl).glShaderSourceSafe(Mockito.eq(SHADER), Mockito.same(source));
    }

    // Uploads a source of one million characters as the same instance
    @Test
    void constructorUploadsALargeSourceVerbatim() {
        this.stubShader();
        String source = "a".repeat(1 << 20);

        new GlShader(ShaderType.COMPUTE, "shader", source);

        Mockito.verify(this.gl).glShaderSourceSafe(Mockito.eq(SHADER), Mockito.same(source));
    }

    // Keeps empty and blank and long names as the same instance
    @ParameterizedTest
    @MethodSource("names")
    void constructorKeepsTheNameVerbatim(String name) {
        this.stubShader();

        assertSame(name, new GlShader(ShaderType.VERTEX, name, "").getName());
    }

    // Throws and deletes the shader after the status query for every status but GL_TRUE
    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void constructorFailsAndDeletesTheShaderForEveryStatusButTrue(int status) {
        this.stubShader();
        Mockito.doReturn(status).when(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);

        Throwable error = thrown(() -> new GlShader(ShaderType.VERTEX, "shader", ""));

        assertEquals(RuntimeException.class, error.getClass());
        assertEquals(COMPILE_FAILED, error.getMessage());
        assertEquals(0, error.getSuppressed().length);
        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);
        order.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verify(this.gl).glDeleteShader(SHADER);
    }

    // Deletes the shader and rethrows the same throwable when the source upload throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheShaderWhenTheSourceUploadThrows(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glShaderSourceSafe(Mockito.anyInt(), Mockito.any());

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glCreateShader(VERTEX);
        order.verify(this.gl).glShaderSourceSafe(SHADER, "");
        order.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(0, error.getSuppressed().length);
    }

    // Deletes the shader and rethrows the same throwable when the compile call throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheShaderWhenTheCompileThrows(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glCompileShader(SHADER);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glCreateShader(VERTEX);
        order.verify(this.gl).glShaderSourceSafe(SHADER, "");
        order.verify(this.gl).glCompileShader(SHADER);
        order.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Deletes the shader and rethrows the same throwable when the log query throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheShaderWhenTheLogQueryThrows(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glCompileShader(SHADER);
        order.verify(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);
        order.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verify(this.gl, Mockito.never()).glGetShaderi(Mockito.anyInt(), Mockito.anyInt());
    }

    // Deletes the shader and rethrows the same throwable when the status query throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheShaderWhenTheStatusQueryThrows(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        InOrder order = Mockito.inOrder(this.gl);
        order.verify(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);
        order.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verify(this.gl).glDeleteShader(SHADER);
    }

    // Deletes the shader and throws a null pointer error when the driver returns a null log
    @Test
    void constructorDeletesTheShaderWhenTheLogIsNull() {
        this.stubShader();
        Mockito.doReturn(null).when(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);

        assertThrows(NullPointerException.class, () -> new GlShader(ShaderType.VERTEX, "shader", ""));

        Mockito.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verify(this.gl, Mockito.never()).glGetShaderi(Mockito.anyInt(), Mockito.anyInt());
    }

    // Deletes the shader and rethrows the same throwable when the warning throws
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorDeletesTheShaderWhenTheWarningThrows(Throwable error) {
        this.stubShader();
        Mockito.doReturn("log text").when(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);

        withLogger(logger -> {
            Mockito.doAnswer(invocation -> {
                throw error;
            }).when(logger).warn(LOG_PATTERN, (Object) "shader", (Object) "log text");

            assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));
        });

        Mockito.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verify(this.gl, Mockito.never()).glGetShaderi(Mockito.anyInt(), Mockito.anyInt());
    }

    // Keeps the compile failure and attaches the failed delete to it
    @Test
    void constructorKeepsTheCompileFailureWhenTheDeleteThrows() {
        this.stubShader();
        Mockito.doReturn(0).when(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);
        IllegalStateException deleteFailure = new IllegalStateException("delete");
        Mockito.doThrow(deleteFailure).when(this.gl).glDeleteShader(SHADER);

        Throwable error = thrown(() -> new GlShader(ShaderType.VERTEX, "shader", ""));

        assertEquals(RuntimeException.class, error.getClass());
        assertEquals(COMPILE_FAILED, error.getMessage());
        assertArrayEquals(new Throwable[] {deleteFailure}, error.getSuppressed());
        Mockito.verify(this.gl).glDeleteShader(SHADER);
    }

    // Keeps every GL failure and attaches the failed delete to it
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorKeepsTheGlFailureWhenTheDeleteThrows(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glCompileShader(SHADER);
        Fault deleteFailure = new Fault("delete");
        Mockito.doThrow(deleteFailure).when(this.gl).glDeleteShader(SHADER);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        assertArrayEquals(new Throwable[] {deleteFailure}, error.getSuppressed());
    }

    // Rethrows the failure once without suppressing it into itself when the delete throws the same instance
    @ParameterizedTest
    @MethodSource("throwables")
    void constructorRethrowsOnceWhenTheDeleteThrowsTheSameFailure(Throwable error) {
        this.stubShader();
        Mockito.doAnswer(invocation -> {
            throw error;
        }).when(this.gl).glCompileShader(SHADER);
        Mockito.doAnswer(invocation -> {
            throw sneaky(error);
        }).when(this.gl).glDeleteShader(SHADER);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        assertEquals(0, error.getSuppressed().length);
        Mockito.verify(this.gl).glDeleteShader(SHADER);
    }

    // Keeps a failure that disables suppression when the delete throws
    @Test
    void constructorKeepsAFailureWithoutSuppressionWhenTheDeleteThrows() {
        this.stubShader();
        RuntimeException error = new RuntimeException("plain", null, false, false) {
        };
        Mockito.doThrow(error).when(this.gl).glCompileShader(SHADER);
        Mockito.doThrow(new IllegalStateException("delete")).when(this.gl).glDeleteShader(SHADER);

        assertSame(error, thrown(() -> new GlShader(ShaderType.VERTEX, "shader", "")));

        assertEquals(0, error.getSuppressed().length);
    }

    // Creates a separate shader on every call with the same arguments
    @Test
    void constructorCreatesANewShaderOnEveryCall() {
        GlShader first = new GlShader(ShaderType.VERTEX, "same", "void main() {}");
        GlShader second = new GlShader(ShaderType.VERTEX, "same", "void main() {}");

        assertNotEquals(first.handle(), second.handle());
        int kept = second.handle();
        first.delete();
        assertEquals(kept, second.handle());
        Mockito.verify(this.gl, Mockito.times(2)).glCreateShader(VERTEX);
        Mockito.verify(this.gl, Mockito.times(2)).glCompileShader(Mockito.anyInt());
        Mockito.verify(this.gl).glDeleteShader(Mockito.anyInt());
    }

    // Warns once with the name and the log for a non empty log
    @Test
    void constructorWarnsWithTheNameAndTheLog() {
        this.stubShader();
        Mockito.doReturn("warning text").when(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);

        withLogger(logger -> {
            new GlShader(ShaderType.VERTEX, "named", "");

            Mockito.verify(logger).warn(LOG_PATTERN, "named", "warning text");
            Mockito.verifyNoMoreInteractions(logger);
        });
    }

    // Warns once per compile with the same log
    @Test
    void constructorWarnsOnEveryCompile() {
        Mockito.doReturn("warning text").when(this.gl).glGetShaderInfoLog(Mockito.anyInt(), Mockito.eq(INFO_LOG_LENGTH));

        withLogger(logger -> {
            new GlShader(ShaderType.VERTEX, "named", "");
            new GlShader(ShaderType.VERTEX, "named", "");

            Mockito.verify(logger, Mockito.times(2)).warn(LOG_PATTERN, "named", "warning text");
            Mockito.verifyNoMoreInteractions(logger);
        });
    }

    // Writes no warning for an empty log
    @Test
    void constructorWritesNoWarningForAnEmptyLog() {
        withLogger(logger -> {
            new GlShader(ShaderType.VERTEX, "named", "");

            Mockito.verifyNoInteractions(logger);
        });
    }

    // Warns with the log before a failed compile throws
    @Test
    void constructorWarnsBeforeAFailedCompile() {
        this.stubShader();
        Mockito.doReturn("error text").when(this.gl).glGetShaderInfoLog(SHADER, INFO_LOG_LENGTH);
        Mockito.doReturn(0).when(this.gl).glGetShaderi(SHADER, COMPILE_STATUS);

        withLogger(logger -> {
            assertEquals(COMPILE_FAILED, assertThrows(RuntimeException.class, () -> new GlShader(ShaderType.FRAGMENT, "broken", "")).getMessage());

            Mockito.verify(logger).warn(LOG_PATTERN, "broken", "error text");
            Mockito.verifyNoMoreInteractions(logger);
        });

        Mockito.verify(this.gl).glDeleteShader(SHADER);
    }

    // Formats the warning to the name and the log text verbatim for logs and names that hold format markers
    @ParameterizedTest
    @ValueSource(strings = {"{}", "\\{}", "a {} b {} c", "%s %d %n", "$1 \\1", "first line\nsecond line", " ", "{", "}"})
    void constructorWarningKeepsTheNameAndLogVerbatim(String text) {
        Mockito.doReturn(text).when(this.gl).glGetShaderInfoLog(Mockito.anyInt(), Mockito.eq(INFO_LOG_LENGTH));
        String name = "name {} " + text;

        withLogger(logger -> {
            new GlShader(ShaderType.VERTEX, name, "");

            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> first = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Object> second = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(pattern.capture(), first.capture(), second.capture());
            String formatted = new ParameterizedMessage(pattern.getValue(), first.getValue(), second.getValue()).getFormattedMessage();
            assertEquals("Shader compilation log for " + name + ": " + text, formatted);
        });
    }

    // Returns the same name before and after delete
    @Test
    void getNameReturnsTheSameNameBeforeAndAfterDelete() {
        this.stubShader();
        String name = "kept.fsh";
        GlShader shader = new GlShader(ShaderType.FRAGMENT, name, "");

        assertSame(name, shader.getName());
        shader.delete();
        assertSame(name, shader.getName());
    }

    // Frees the shader once and rejects every later use of the handle
    @Test
    void deleteFreesTheShaderOnce() {
        GlShader shader = this.shader();

        shader.delete();
        Mockito.verify(this.gl).glDeleteShader(SHADER);
        shader.delete();

        Mockito.verifyNoMoreInteractions(this.gl);
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, shader::handle).getMessage());
    }

    // Frees every non zero shader name
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE})
    void deleteFreesEveryShaderName(int name) {
        Mockito.doReturn(name).when(this.gl).glCreateShader(VERTEX);
        GlShader shader = new GlShader(ShaderType.VERTEX, "shader", "");

        shader.delete();

        Mockito.verify(this.gl).glDeleteShader(name);
    }

    // Frees the shader once through the deprecated alias
    @Test
    @SuppressWarnings("deprecation")
    void destroyFreesTheShaderOnce() {
        GlShader shader = this.shader();

        shader.destroy();
        shader.destroy();
        shader.delete();

        Mockito.verify(this.gl).glDeleteShader(SHADER);
        Mockito.verifyNoMoreInteractions(this.gl);
    }

    // Keeps the shader alive and rethrows when the delete call throws and frees it on the next delete
    @Test
    void deleteKeepsTheShaderWhenGlThrows() {
        GlShader shader = this.shader();
        IllegalStateException failure = new IllegalStateException("delete");
        Mockito.doThrow(failure).doNothing().when(this.gl).glDeleteShader(SHADER);

        assertSame(failure, thrown(shader::delete));
        assertEquals(SHADER, shader.handle());
        shader.delete();

        Mockito.verify(this.gl, Mockito.times(2)).glDeleteShader(SHADER);
        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, shader::handle).getMessage());
    }

    // Deletes the shader name on a direct call and leaves the handle in place
    @Test
    void destroyInternalDeletesWithoutClearingTheHandle() {
        GlShader shader = this.shader();

        shader.destroyInternal();

        Mockito.verify(this.gl).glDeleteShader(SHADER);
        assertEquals(SHADER, shader.handle());
    }

    // Rejects a direct call on a deleted shader without a GL call
    @Test
    void destroyInternalRejectsADeletedShaderWithoutAGlCall() {
        GlShader shader = this.shader();
        shader.delete();
        Mockito.clearInvocations(this.gl);

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, shader::destroyInternal).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }

    // Rejects a deleted shader in a program builder without a GL call
    @Test
    void deletedShaderIsRejectedByAProgramBuilder() {
        GlShader shader = this.shader();
        GlProgram.Builder builder = GlProgram.builder("program");
        shader.delete();
        Mockito.clearInvocations(this.gl);

        assertEquals(BAD_HANDLE, assertThrows(IllegalStateException.class, () -> builder.attachShader(shader)).getMessage());
        Mockito.verifyNoInteractions(this.gl);
    }
}
