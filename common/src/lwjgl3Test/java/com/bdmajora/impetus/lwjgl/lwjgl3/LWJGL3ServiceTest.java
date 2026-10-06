package com.bdmajora.impetus.lwjgl.lwjgl3;

import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.GLNv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBSparseBuffer;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.EXTGPUShader4;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVMeshShader;
import org.lwjgl.opengl.NVShaderBufferLoad;
import org.lwjgl.opengl.NVVertexBufferUnifiedMemory;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.mockito.MockedStatic;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Every org.lwjgl.opengl native is a plain throwing method in the test LWJGL jar, so each GL class is stubbed with mockStatic, and GL.getCapabilities answers whatever caps holds
class LWJGL3ServiceTest {
    private MockedStatic<GL> gl;
    private GLCapabilities caps;

    @BeforeEach
    void installCapabilities() {
        caps = capabilities(true);
        gl = mockStatic(GL.class);
        gl.when(GL::getCapabilities).thenAnswer(inv -> caps);
    }

    @AfterEach
    void closeCapabilities() {
        gl.close();
    }

    // GLCapabilities' constructor resolves function pointers, so one is allocated bare and its final flags written reflectively
    private static GLCapabilities capabilities(boolean value) {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            GLCapabilities caps = (GLCapabilities) ((Unsafe) theUnsafe.get(null)).allocateInstance(GLCapabilities.class);
            for (Field field : GLCapabilities.class.getFields()) {
                if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.setBoolean(caps, value);
                }
            }
            return caps;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static GLCapabilities with(GLCapabilities caps, boolean value, String... flags) {
        try {
            for (String flag : flags) {
                Field field = GLCapabilities.class.getField(flag);
                field.setAccessible(true);
                field.setBoolean(caps, value);
            }
            return caps;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // The mode types are private, so they are named through Object
    private static String name(Object mode) {
        return mode instanceof Enum<?> e ? e.name() : mode.getClass().getSimpleName();
    }

    @Test
    void createResolvesEntryPointsFromTheCapabilities() throws Exception {
        LWJGL3Service core = LWJGL3Service.create();
        assertEquals("CORE", name(core.vaoMode()));
        assertEquals("CORE", name(core.timerQueryMode()));
        assertEquals("CORE", name(core.vertexAttribIMode()));
        assertEquals("CORE", name(core.blendIMode()));
        assertTrue(core.supportsBufferBlending());
        try (MockedStatic<GL30C> gl30 = mockStatic(GL30C.class); MockedStatic<GL33C> gl33 = mockStatic(GL33C.class);
             MockedStatic<GL40C> gl40 = mockStatic(GL40C.class)) {
            gl30.when(GL30C::glGenVertexArrays).thenReturn(4);
            gl33.when(() -> GL33C.glGetQueryObjectui64(1, 2)).thenReturn(99L);
            assertEquals(4, core.glGenVertexArrays());
            core.glDeleteVertexArrays(4);
            gl30.verify(() -> GL30C.glDeleteVertexArrays(4));
            core.glBindVertexArray(4);
            gl30.verify(() -> GL30C.glBindVertexArray(4));
            core.glQueryCounter(1, 2);
            gl33.verify(() -> GL33C.glQueryCounter(1, 2));
            assertEquals(99L, core.glGetQueryObjectui64(1, 2));
            core.glVertexAttribIPointer(1, 2, 3, 4, 5L);
            gl30.verify(() -> GL30C.glVertexAttribIPointer(1, 2, 3, 4, 5L));
            core.glBlendFuncSeparatei(0, 1, 2, 3, 4);
            gl40.verify(() -> GL40C.glBlendFuncSeparatei(0, 1, 2, 3, 4));
        }

        // Below GL 3.0/3.3/4.0 the extension entry points take over
        with(caps, false, "OpenGL30", "OpenGL33", "OpenGL40");
        LWJGL3Service arb = LWJGL3Service.create();
        assertEquals("ARB", name(arb.vaoMode()));
        assertEquals("ARB", name(arb.timerQueryMode()));
        assertEquals("EXT", name(arb.vertexAttribIMode()));
        assertEquals("ARB", name(arb.blendIMode()));
        assertTrue(arb.supportsBufferBlending());
        try (MockedStatic<ARBVertexArrayObject> vao = mockStatic(ARBVertexArrayObject.class);
             MockedStatic<ARBTimerQuery> timer = mockStatic(ARBTimerQuery.class);
             MockedStatic<EXTGPUShader4> ext = mockStatic(EXTGPUShader4.class);
             MockedStatic<ARBDrawBuffersBlend> blend = mockStatic(ARBDrawBuffersBlend.class)) {
            vao.when(ARBVertexArrayObject::glGenVertexArrays).thenReturn(5);
            timer.when(() -> ARBTimerQuery.glGetQueryObjectui64(1, 2)).thenReturn(98L);
            assertEquals(5, arb.glGenVertexArrays());
            arb.glDeleteVertexArrays(5);
            vao.verify(() -> ARBVertexArrayObject.glDeleteVertexArrays(5));
            arb.glBindVertexArray(5);
            vao.verify(() -> ARBVertexArrayObject.glBindVertexArray(5));
            arb.glQueryCounter(1, 2);
            timer.verify(() -> ARBTimerQuery.glQueryCounter(1, 2));
            assertEquals(98L, arb.glGetQueryObjectui64(1, 2));
            arb.glVertexAttribIPointer(1, 2, 3, 4, 5L);
            ext.verify(() -> EXTGPUShader4.glVertexAttribIPointerEXT(1, 2, 3, 4, 5L));
            arb.glBlendFuncSeparatei(0, 1, 2, 3, 4);
            blend.verify(() -> ARBDrawBuffersBlend.glBlendFuncSeparateiARB(0, 1, 2, 3, 4));
        }

        // No ARB VAO but the driver exports the APPLE entry points (macOS legacy contexts)
        with(caps, false, "GL_ARB_vertex_array_object");
        FunctionProvider functions = mock(FunctionProvider.class);
        when(functions.getFunctionAddress("glGenVertexArraysAPPLE")).thenReturn(0x10L);
        when(functions.getFunctionAddress("glDeleteVertexArraysAPPLE")).thenReturn(0x20L);
        when(functions.getFunctionAddress("glBindVertexArrayAPPLE")).thenReturn(0x30L);
        gl.when(GL::getFunctionProvider).thenReturn(functions);
        LWJGL3Service apple = LWJGL3Service.create();
        assertEquals("AppleVAO", name(apple.vaoMode()));
        // LWJGL 3.4's Java 25 JNI is FFM-backed plain Java, which is what lets Mockito stand in for the call; a native one would jump to 0x10
        assertFalse(Modifier.isNative(JNI.class.getMethod("callPV", int.class, long.class, long.class).getModifiers()));
        assertFalse(Modifier.isNative(JNI.class.getMethod("callV", int.class, long.class).getModifiers()));
        try (MockedStatic<JNI> jni = mockStatic(JNI.class)) {
            int[] deleted = new int[1];
            jni.when(() -> JNI.callPV(eq(1), anyLong(), eq(0x10L))).thenAnswer(inv -> {
                MemoryUtil.memPutInt(inv.<Long>getArgument(1), 6);
                return null;
            });
            jni.when(() -> JNI.callPV(eq(1), anyLong(), eq(0x20L))).thenAnswer(inv -> {
                deleted[0] = MemoryUtil.memGetInt(inv.<Long>getArgument(1));
                return null;
            });
            assertEquals(6, apple.glGenVertexArrays());
            apple.glDeleteVertexArrays(6);
            assertEquals(6, deleted[0]);
            apple.glBindVertexArray(6);
            jni.verify(() -> JNI.callV(6, 0x30L));
        }

        // Nothing at all: the optional paths throw or do nothing instead of calling a null entry point
        caps = capabilities(false);
        when(functions.getFunctionAddress("glBindVertexArrayAPPLE")).thenReturn(0L);
        LWJGL3Service none = LWJGL3Service.create();
        assertEquals("NONE", name(none.vaoMode()));
        assertEquals("NONE", name(none.timerQueryMode()));
        assertEquals("NONE", name(none.vertexAttribIMode()));
        assertEquals("NONE", name(none.blendIMode()));
        assertFalse(none.supportsBufferBlending());
        assertThrows(UnsupportedOperationException.class, none::glGenVertexArrays);
        assertThrows(UnsupportedOperationException.class, () -> none.glDeleteVertexArrays(1));
        assertThrows(UnsupportedOperationException.class, () -> none.glBindVertexArray(1));
        assertThrows(UnsupportedOperationException.class, () -> none.glVertexAttribIPointer(1, 2, 3, 4, 5L));
        assertThrows(UnsupportedOperationException.class, () -> none.glBlendFuncSeparatei(0, 1, 2, 3, 4));
        none.glQueryCounter(1, 2);
        assertEquals(0L, none.glGetQueryObjectui64(1, 2));
    }

    @Test
    void capabilityQueriesReadTheContextFlags() {
        LWJGL3Service service = LWJGL3Service.create();
        int[][] versions = {{1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {3, 2}, {3, 3},
                {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}, {4, 5}, {4, 6}};
        for (int[] v : versions) {
            assertTrue(service.isOpenGLVersionSupported(v[0], v[1]), v[0] + "." + v[1]);
        }
        assertFalse(service.isOpenGLVersionSupported(1, 0));
        assertFalse(service.isOpenGLVersionSupported(5, 0));
        for (GLExtension extension : GLExtension.values()) {
            assertTrue(service.isExtensionSupported(extension), extension.name());
        }
        assertEquals(Pointer.POINTER_SIZE, service.getPointerSize());

        caps = capabilities(false);
        for (int[] v : versions) {
            assertFalse(service.isOpenGLVersionSupported(v[0], v[1]), v[0] + "." + v[1]);
        }
        for (GLExtension extension : GLExtension.values()) {
            assertFalse(service.isExtensionSupported(extension), extension.name());
        }
    }

    @Test
    void bufferCallsForwardToTheirGlClasses() {
        LWJGL3Service service = LWJGL3Service.create();
        ByteBuffer data = ByteBuffer.allocateDirect(16);
        try (MockedStatic<GL15C> gl15 = mockStatic(GL15C.class); MockedStatic<GL30C> gl30 = mockStatic(GL30C.class);
             MockedStatic<GL31C> gl31 = mockStatic(GL31C.class); MockedStatic<GL43C> gl43 = mockStatic(GL43C.class);
             MockedStatic<GL44C> gl44 = mockStatic(GL44C.class)) {
            gl15.when(GL15C::glGenBuffers).thenReturn(3);
            assertEquals(3, service.glGenBuffers());
            service.glDeleteBuffers(3);
            gl15.verify(() -> GL15C.glDeleteBuffers(3));
            service.glBindBuffer(1, 3);
            gl15.verify(() -> GL15C.glBindBuffer(1, 3));
            service.glBufferData(1, 64L, 2);
            gl15.verify(() -> GL15C.glBufferData(1, 64L, 2));
            service.glBufferData(1, data, 2);
            gl15.verify(() -> GL15C.glBufferData(1, data, 2));
            // The address form goes straight to the unchecked entry point
            service.glBufferData(1, 32L, 8L, 2);
            gl15.verify(() -> GL15C.nglBufferData(1, 32L, 8L, 2));
            service.glBufferSubData(1, 8L, data);
            gl15.verify(() -> GL15C.glBufferSubData(1, 8L, data));
            service.glBufferStorage(1, 64L, 5);
            gl44.verify(() -> GL44C.glBufferStorage(1, 64L, 5));
            service.glClearBufferData(1, 2, 3, 4, data);
            gl43.verify(() -> GL43C.glClearBufferData(1, 2, 3, 4, data));
            gl30.when(() -> GL30C.glMapBufferRange(1, 0L, 16L, 7)).thenReturn(data);
            assertSame(data, service.glMapBufferRange(1, 0L, 16L, 7));
            gl15.when(() -> GL15C.nglMapBuffer(1, 2)).thenReturn(77L);
            assertEquals(77L, service.nglMapBuffer(1, 2));
            gl15.when(() -> GL15C.glMapBuffer(1, 2, null)).thenReturn(data);
            assertSame(data, service.glMapBuffer(1, 2));
            service.glUnmapBuffer(1);
            gl15.verify(() -> GL15C.glUnmapBuffer(1));
            service.glFlushMappedBufferRange(1, 2L, 3L);
            gl30.verify(() -> GL30C.glFlushMappedBufferRange(1, 2L, 3L));
            service.glCopyBufferSubData(1, 2, 3L, 4L, 5L);
            gl31.verify(() -> GL31C.glCopyBufferSubData(1, 2, 3L, 4L, 5L));
            service.glBindBufferBase(1, 2, 3);
            gl30.verify(() -> GL30C.glBindBufferBase(1, 2, 3));
        }
    }

    @Test
    void vertexShaderAndUniformCallsForward() {
        LWJGL3Service service = LWJGL3Service.create();
        FloatBuffer floats = ByteBuffer.allocateDirect(64).asFloatBuffer();
        try (MockedStatic<GL20C> gl20 = mockStatic(GL20C.class); MockedStatic<GL30C> gl30 = mockStatic(GL30C.class);
             MockedStatic<GL31C> gl31 = mockStatic(GL31C.class)) {
            service.glVertexAttribPointer(1, 2, 3, true, 4, 5L);
            gl20.verify(() -> GL20C.glVertexAttribPointer(1, 2, 3, true, 4, 5L));
            service.glEnableVertexAttribArray(1);
            gl20.verify(() -> GL20C.glEnableVertexAttribArray(1));
            service.glDisableVertexAttribArray(1);
            gl20.verify(() -> GL20C.glDisableVertexAttribArray(1));
            gl20.when(() -> GL20C.glGetVertexAttribi(1, 2)).thenReturn(9);
            assertEquals(9, service.glGetVertexAttribi(1, 2));

            gl20.when(() -> GL20C.glCreateShader(1)).thenReturn(7);
            assertEquals(7, service.glCreateShader(1));
            service.glShaderSource(7, "void main() {}");
            gl20.verify(() -> GL20C.glShaderSource(7, (CharSequence) "void main() {}"));
            // The safe form hands GL one terminated UTF-8 string and no length array, then frees the string
            String[] sent = new String[1];
            gl20.when(() -> GL20C.nglShaderSource(eq(7), eq(1), anyLong(), eq(0L))).thenAnswer(inv -> {
                sent[0] = MemoryUtil.memUTF8(MemoryUtil.memGetAddress(inv.<Long>getArgument(2)));
                return null;
            });
            service.glShaderSourceSafe(7, "abc");
            assertEquals("abc", sent[0]);
            service.glCompileShader(7);
            gl20.verify(() -> GL20C.glCompileShader(7));
            gl20.when(() -> GL20C.glGetShaderInfoLog(7)).thenReturn("log");
            assertEquals("log", service.glGetShaderInfoLog(7, 100));
            gl20.when(() -> GL20C.glGetShaderi(7, 1)).thenReturn(1);
            assertEquals(1, service.glGetShaderi(7, 1));
            service.glDeleteShader(7);
            gl20.verify(() -> GL20C.glDeleteShader(7));
            gl20.when(GL20C::glCreateProgram).thenReturn(8);
            assertEquals(8, service.glCreateProgram());
            service.glAttachShader(8, 7);
            gl20.verify(() -> GL20C.glAttachShader(8, 7));
            service.glDetachShader(8, 7);
            gl20.verify(() -> GL20C.glDetachShader(8, 7));
            service.glLinkProgram(8);
            gl20.verify(() -> GL20C.glLinkProgram(8));
            gl20.when(() -> GL20C.glGetProgramInfoLog(8)).thenReturn("plog");
            assertEquals("plog", service.glGetProgramInfoLog(8, 100));
            gl20.when(() -> GL20C.glGetProgrami(8, 1)).thenReturn(1);
            assertEquals(1, service.glGetProgrami(8, 1));
            // LWJGL3 reports size and type separately; the service packs them into the caller's one buffer
            gl20.when(() -> GL20C.glGetActiveUniform(eq(8), eq(0), eq(32), any(IntBuffer.class), any(IntBuffer.class))).thenAnswer(inv -> {
                inv.<IntBuffer>getArgument(3).put(0, 4);
                inv.<IntBuffer>getArgument(4).put(0, 0x8B51);
                return "u_Name";
            });
            IntBuffer sizeType = IntBuffer.allocate(2);
            assertEquals("u_Name", service.glGetActiveUniform(8, 0, 32, sizeType));
            assertEquals(4, sizeType.get(0));
            assertEquals(0x8B51, sizeType.get(1));
            service.glUseProgram(8);
            gl20.verify(() -> GL20C.glUseProgram(8));
            service.glDeleteProgram(8);
            gl20.verify(() -> GL20C.glDeleteProgram(8));
            service.glBindAttribLocation(8, 0, "a_Pos");
            gl20.verify(() -> GL20C.glBindAttribLocation(8, 0, "a_Pos"));
            service.glBindFragDataLocation(8, 0, "fragColor");
            gl30.verify(() -> GL30C.glBindFragDataLocation(8, 0, "fragColor"));
            gl20.when(() -> GL20C.glGetUniformLocation(8, "u")).thenReturn(4);
            assertEquals(4, service.glGetUniformLocation(8, "u"));
            gl31.when(() -> GL31C.glGetUniformBlockIndex(8, "b")).thenReturn(2);
            assertEquals(2, service.glGetUniformBlockIndex(8, "b"));
            service.glUniformBlockBinding(8, 2, 1);
            gl31.verify(() -> GL31C.glUniformBlockBinding(8, 2, 1));
            gl20.when(() -> GL20C.glGetAttribLocation(8, "a")).thenReturn(3);
            assertEquals(3, service.glGetAttribLocation(8, "a"));

            service.glUniform1f(1, 2f);
            gl20.verify(() -> GL20C.glUniform1f(1, 2f));
            service.glUniform1i(1, 2);
            gl20.verify(() -> GL20C.glUniform1i(1, 2));
            service.glUniform1fv(1, floats);
            gl20.verify(() -> GL20C.glUniform1fv(1, floats));
            service.glUniform2i(1, 2, 3);
            gl20.verify(() -> GL20C.glUniform2i(1, 2, 3));
            service.glUniform3i(1, 2, 3, 4);
            gl20.verify(() -> GL20C.glUniform3i(1, 2, 3, 4));
            service.glUniform2f(1, 2f, 3f);
            gl20.verify(() -> GL20C.glUniform2f(1, 2f, 3f));
            service.glUniform4f(1, 2f, 3f, 4f, 5f);
            gl20.verify(() -> GL20C.glUniform4f(1, 2f, 3f, 4f, 5f));
            service.glUniform4i(1, 2, 3, 4, 5);
            gl20.verify(() -> GL20C.glUniform4i(1, 2, 3, 4, 5));
            service.glUniform3f(1, 2f, 3f, 4f);
            gl20.verify(() -> GL20C.glUniform3f(1, 2f, 3f, 4f));
            service.glUniform3fv(1, floats);
            gl20.verify(() -> GL20C.glUniform3fv(1, floats));
            float[] vec3 = {1f, 2f, 3f};
            service.glUniform3fv(2, vec3);
            gl20.verify(() -> GL20C.glUniform3fv(2, vec3));
            service.glUniform4fv(1, floats);
            gl20.verify(() -> GL20C.glUniform4fv(1, floats));
            float[] vec4 = {1f, 2f, 3f, 4f};
            service.glUniform4fv(2, vec4);
            gl20.verify(() -> GL20C.glUniform4fv(2, vec4));
            service.glUniformMatrix3fv(1, true, floats);
            gl20.verify(() -> GL20C.glUniformMatrix3fv(1, true, floats));
            service.glUniformMatrix4fv(1, false, floats);
            gl20.verify(() -> GL20C.glUniformMatrix4fv(1, false, floats));
        }
    }

    @Test
    void drawSyncAndQueryCallsForward() {
        LWJGL3Service service = LWJGL3Service.create();
        IntBuffer length = IntBuffer.allocate(1);
        try (MockedStatic<GL32C> gl32 = mockStatic(GL32C.class); MockedStatic<GL43C> gl43 = mockStatic(GL43C.class);
             MockedStatic<GL15C> gl15 = mockStatic(GL15C.class)) {
            service.glDrawElementsBaseVertex(4, 6, 2, 8L, 1);
            gl32.verify(() -> GL32C.glDrawElementsBaseVertex(4, 6, 2, 8L, 1));
            // LWJGL3 takes the count/index/base-vertex arrays as addresses, so the multi-draw is one call, not an emulation
            service.glMultiDrawElementsBaseVertex(4, 100L, 2, 200L, 3, 300L);
            gl32.verify(() -> GL32C.nglMultiDrawElementsBaseVertex(4, 100L, 2, 200L, 3, 300L));
            service.glMultiDrawElementsIndirect(4, 2, 0L, 5, 20);
            gl43.verify(() -> GL43C.glMultiDrawElementsIndirect(4, 2, 0L, 5, 20));

            // Sync objects are plain handles on LWJGL3, no GLSync table to keep
            gl32.when(() -> GL32C.glFenceSync(1, 0)).thenReturn(77L);
            assertEquals(77L, service.glFenceSync(1, 0));
            gl32.when(() -> GL32C.glClientWaitSync(77L, 1, 5L)).thenReturn(0x911A);
            assertEquals(0x911A, service.glClientWaitSync(77L, 1, 5L));
            gl32.when(() -> GL32C.glGetSynci(77L, 0x9114, length)).thenReturn(0x9119);
            assertEquals(0x9119, service.glGetSynci(77L, 0x9114, length));
            service.glWaitSync(77L, 0, 5L);
            gl32.verify(() -> GL32C.glWaitSync(77L, 0, 5L));
            service.glDeleteSync(77L);
            gl32.verify(() -> GL32C.glDeleteSync(77L));

            gl15.when(GL15C::glGenQueries).thenReturn(9);
            assertEquals(9, service.glGenQueries());
            service.glDeleteQueries(9);
            gl15.verify(() -> GL15C.glDeleteQueries(9));
            gl15.when(() -> GL15C.glGetQueryObjecti(9, 1)).thenReturn(1);
            assertEquals(1, service.glGetQueryObjecti(9, 1));
        }
    }

    @Test
    void textureAndComputeCallsForward() {
        LWJGL3Service service = LWJGL3Service.create();
        ByteBuffer pixels = ByteBuffer.allocateDirect(16);
        try (MockedStatic<GL11C> gl11 = mockStatic(GL11C.class); MockedStatic<GL12C> gl12 = mockStatic(GL12C.class);
             MockedStatic<GL13C> gl13c = mockStatic(GL13C.class); MockedStatic<GL13> gl13 = mockStatic(GL13.class);
             MockedStatic<GL30C> gl30 = mockStatic(GL30C.class); MockedStatic<GL33C> gl33 = mockStatic(GL33C.class);
             MockedStatic<GL42C> gl42 = mockStatic(GL42C.class); MockedStatic<GL43C> gl43 = mockStatic(GL43C.class);
             MockedStatic<ARBClearTexture> clear = mockStatic(ARBClearTexture.class)) {
            gl11.when(GL11C::glGenTextures).thenReturn(2);
            assertEquals(2, service.glGenTextures());
            int[] textures = new int[3];
            service.glGenTextures(textures);
            gl11.verify(() -> GL11C.glGenTextures(textures));
            service.glDeleteTextures(2);
            gl11.verify(() -> GL11C.glDeleteTextures(2));
            service.glDeleteTextures(textures);
            gl11.verify(() -> GL11C.glDeleteTextures(textures));
            service.glBindTexture(1, 2);
            gl11.verify(() -> GL11C.glBindTexture(1, 2));
            service.glActiveTexture(3);
            gl13c.verify(() -> GL13C.glActiveTexture(3));
            service.glMultiTexCoord2f(3, 0.5f, 0.25f);
            gl13.verify(() -> GL13.glMultiTexCoord2f(3, 0.5f, 0.25f));
            gl11.when(() -> GL11C.glGetTexLevelParameteri(1, 0, 2)).thenReturn(64);
            assertEquals(64, service.glGetTexLevelParameteri(1, 0, 2));
            service.glCopyTexSubImage2D(1, 0, 2, 3, 4, 5, 6, 7);
            gl11.verify(() -> GL11C.glCopyTexSubImage2D(1, 0, 2, 3, 4, 5, 6, 7));
            service.glReadPixels(1, 2, 3, 4, 5, 6, pixels);
            gl11.verify(() -> GL11C.glReadPixels(1, 2, 3, 4, 5, 6, pixels));
            service.glReadPixels(1, 2, 3, 4, 5, 6, 8L);
            gl11.verify(() -> GL11C.glReadPixels(1, 2, 3, 4, 5, 6, 8L));
            service.glGenerateMipmap(1);
            gl30.verify(() -> GL30C.glGenerateMipmap(1));
            gl33.when(GL33C::glGenSamplers).thenReturn(4);
            assertEquals(4, service.glGenSamplers());
            service.glDeleteSamplers(4);
            gl33.verify(() -> GL33C.glDeleteSamplers(4));
            service.glBindSampler(0, 4);
            gl33.verify(() -> GL33C.glBindSampler(0, 4));
            service.glSamplerParameteri(4, 1, 2);
            gl33.verify(() -> GL33C.glSamplerParameteri(4, 1, 2));
            service.glDepthRange(0.1, 0.9);
            gl11.verify(() -> GL11C.glDepthRange(0.1, 0.9));
            service.glPixelStorei(1, 2);
            gl11.verify(() -> GL11C.glPixelStorei(1, 2));
            service.glTexImage2D(1, 0, 2, 3, 4, 0, 5, 6, pixels);
            gl11.verify(() -> GL11C.glTexImage2D(1, 0, 2, 3, 4, 0, 5, 6, pixels));
            service.glTexImage3D(1, 0, 2, 3, 4, 5, 0, 6, 7, pixels);
            gl12.verify(() -> GL12C.glTexImage3D(1, 0, 2, 3, 4, 5, 0, 6, 7, pixels));
            service.glBindImageTexture(0, 2, 0, false, 0, 1, 3);
            gl42.verify(() -> GL42C.glBindImageTexture(0, 2, 0, false, 0, 1, 3));
            service.glMemoryBarrier(7);
            gl42.verify(() -> GL42C.glMemoryBarrier(7));
            service.glDispatchCompute(1, 2, 3);
            gl43.verify(() -> GL43C.glDispatchCompute(1, 2, 3));
            service.glDispatchComputeIndirect(16L);
            gl43.verify(() -> GL43C.glDispatchComputeIndirect(16L));
            service.glClearTexImage(2, 0, 1, 3, pixels);
            clear.verify(() -> ARBClearTexture.glClearTexImage(2, 0, 1, 3, pixels));
            service.glTexParameteri(1, 2, 3);
            gl11.verify(() -> GL11C.glTexParameteri(1, 2, 3));
            int[] params = {5, 6};
            service.glTexParameteriv(1, 2, params);
            gl11.verify(() -> GL11C.glTexParameteriv(1, 2, params));
            service.glTexParameterf(1, 2, 0.5f);
            gl11.verify(() -> GL11C.glTexParameterf(1, 2, 0.5f));
        }
    }

    @Test
    void framebufferAndStateCallsForward() {
        LWJGL3Service service = LWJGL3Service.create();
        IntBuffer bufs = IntBuffer.allocate(2);
        FloatBuffer matrix = ByteBuffer.allocateDirect(64).asFloatBuffer();
        try (MockedStatic<GL11C> gl11c = mockStatic(GL11C.class); MockedStatic<GL11> gl11 = mockStatic(GL11.class);
             MockedStatic<GL14C> gl14 = mockStatic(GL14C.class); MockedStatic<GL20C> gl20 = mockStatic(GL20C.class);
             MockedStatic<GL30C> gl30 = mockStatic(GL30C.class)) {
            gl30.when(GL30C::glGenFramebuffers).thenReturn(5);
            assertEquals(5, service.glGenFramebuffers());
            service.glDeleteFramebuffers(5);
            gl30.verify(() -> GL30C.glDeleteFramebuffers(5));
            service.glBindFramebuffer(1, 5);
            gl30.verify(() -> GL30C.glBindFramebuffer(1, 5));
            gl30.when(() -> GL30C.glCheckFramebufferStatus(1)).thenReturn(0x8CD5);
            assertEquals(0x8CD5, service.glCheckFramebufferStatus(1));
            service.glFramebufferTexture2D(1, 2, 3, 4, 0);
            gl30.verify(() -> GL30C.glFramebufferTexture2D(1, 2, 3, 4, 0));
            service.glFramebufferTextureLayer(1, 2, 3, 0, 4);
            gl30.verify(() -> GL30C.glFramebufferTextureLayer(1, 2, 3, 0, 4));
            service.glDrawBuffers(1);
            gl20.verify(() -> GL20C.glDrawBuffers(1));
            service.glDrawBuffers(bufs);
            gl20.verify(() -> GL20C.glDrawBuffers(bufs));
            service.glReadBuffer(2);
            gl11c.verify(() -> GL11C.glReadBuffer(2));
            service.glBlitFramebuffer(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
            gl30.verify(() -> GL30C.glBlitFramebuffer(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
            gl30.when(GL30C::glGenRenderbuffers).thenReturn(6);
            assertEquals(6, service.glGenRenderbuffers());
            service.glDeleteRenderbuffers(6);
            gl30.verify(() -> GL30C.glDeleteRenderbuffers(6));
            service.glBindRenderbuffer(1, 6);
            gl30.verify(() -> GL30C.glBindRenderbuffer(1, 6));
            service.glRenderbufferStorage(1, 2, 3, 4);
            gl30.verify(() -> GL30C.glRenderbufferStorage(1, 2, 3, 4));
            service.glFramebufferRenderbuffer(1, 2, 3, 6);
            gl30.verify(() -> GL30C.glFramebufferRenderbuffer(1, 2, 3, 6));

            service.glEnable(1);
            gl11c.verify(() -> GL11C.glEnable(1));
            service.glDisable(1);
            gl11c.verify(() -> GL11C.glDisable(1));
            service.glEnablei(1, 2);
            gl30.verify(() -> GL30C.glEnablei(1, 2));
            service.glDisablei(1, 2);
            gl30.verify(() -> GL30C.glDisablei(1, 2));
            service.glBlendFunc(1, 2);
            gl11c.verify(() -> GL11C.glBlendFunc(1, 2));
            service.glBlendFuncSeparate(1, 2, 3, 4);
            gl14.verify(() -> GL14C.glBlendFuncSeparate(1, 2, 3, 4));
            service.glDepthFunc(1);
            gl11c.verify(() -> GL11C.glDepthFunc(1));
            service.glDepthMask(true);
            gl11c.verify(() -> GL11C.glDepthMask(true));
            service.glColorMask(true, false, true, false);
            gl11c.verify(() -> GL11C.glColorMask(true, false, true, false));
            service.glViewport(0, 0, 10, 20);
            gl11c.verify(() -> GL11C.glViewport(0, 0, 10, 20));
            service.glClear(3);
            gl11c.verify(() -> GL11C.glClear(3));
            service.glClearColor(0f, 0.5f, 1f, 1f);
            gl11c.verify(() -> GL11C.glClearColor(0f, 0.5f, 1f, 1f));
            service.glClearDepth(1.0);
            gl11c.verify(() -> GL11C.glClearDepth(1.0));
            service.glCullFace(1);
            gl11c.verify(() -> GL11C.glCullFace(1));
            service.glDrawArrays(4, 0, 3);
            gl11c.verify(() -> GL11C.glDrawArrays(4, 0, 3));
            gl11c.when(GL11C::glGetError).thenReturn(0x500);
            assertEquals(0x500, service.glGetError());
            service.glMatrixMode(1);
            gl11.verify(() -> GL11.glMatrixMode(1));
            service.glLoadMatrixf(matrix);
            gl11.verify(() -> GL11.glLoadMatrixf(matrix));
            gl11c.when(() -> GL11C.glGetInteger(1)).thenReturn(42);
            assertEquals(42, service.glGetInteger(1));
            gl11c.when(() -> GL11C.glGetFloat(1)).thenReturn(0.5f);
            assertEquals(0.5f, service.glGetFloat(1));
            int[] values = new int[2];
            service.glGetIntegerv(2, values);
            gl11c.verify(() -> GL11C.glGetIntegerv(2, values));
            gl11c.when(() -> GL11C.glGetBoolean(1)).thenReturn(true);
            assertTrue(service.glGetBoolean(1));
            gl11c.when(() -> GL11C.glGetString(1)).thenReturn("vendor");
            assertEquals("vendor", service.glGetString(1));
            service.glFinish();
            gl11.verify(GL11::glFinish);
        }
    }

    @Test
    void directStateAccessBindlessMeshAndSparseCallsForward() {
        LWJGL3Service service = LWJGL3Service.create();
        try (MockedStatic<ARBDirectStateAccess> dsa = mockStatic(ARBDirectStateAccess.class);
             MockedStatic<NVShaderBufferLoad> load = mockStatic(NVShaderBufferLoad.class);
             MockedStatic<NVVertexBufferUnifiedMemory> unified = mockStatic(NVVertexBufferUnifiedMemory.class);
             MockedStatic<NVMeshShader> mesh = mockStatic(NVMeshShader.class);
             MockedStatic<ARBSparseBuffer> sparse = mockStatic(ARBSparseBuffer.class);
             MockedStatic<GL11> gl11 = mockStatic(GL11.class)) {
            dsa.when(ARBDirectStateAccess::glCreateBuffers).thenReturn(11);
            assertEquals(11, service.glCreateBuffers());
            service.glNamedBufferStorage(11, 64L, 3);
            dsa.verify(() -> ARBDirectStateAccess.glNamedBufferStorage(11, 64L, 3));
            dsa.when(() -> ARBDirectStateAccess.nglMapNamedBufferRange(11, 0L, 64L, 2)).thenReturn(4096L);
            assertEquals(4096L, service.nglMapNamedBufferRange(11, 0L, 64L, 2));
            service.glUnmapNamedBuffer(11);
            dsa.verify(() -> ARBDirectStateAccess.glUnmapNamedBuffer(11));
            service.glFlushMappedNamedBufferRange(11, 8L, 16L);
            dsa.verify(() -> ARBDirectStateAccess.glFlushMappedNamedBufferRange(11, 8L, 16L));
            service.glCopyNamedBufferSubData(11, 12, 1L, 2L, 3L);
            dsa.verify(() -> ARBDirectStateAccess.glCopyNamedBufferSubData(11, 12, 1L, 2L, 3L));
            // A null data pointer is GL's "fill with zero"
            service.glClearNamedBufferSubDataZero(11, 1, 8L, 16L, 2, 3);
            dsa.verify(() -> ARBDirectStateAccess.nglClearNamedBufferSubData(11, 1, 8L, 16L, 2, 3, 0L));
            service.glClearNamedBufferDataZero(11, 1, 2, 3);
            dsa.verify(() -> ARBDirectStateAccess.nglClearNamedBufferData(11, 1, 2, 3, 0L));

            load.when(() -> NVShaderBufferLoad.glGetNamedBufferParameterui64vNV(eq(11), eq(GLNv.GL_BUFFER_GPU_ADDRESS_NV), any(long[].class)))
                    .thenAnswer(inv -> {
                        inv.<long[]>getArgument(2)[0] = 0xABCDL;
                        return null;
                    });
            assertEquals(0xABCDL, service.glGetNamedBufferGpuAddressNV(11));
            service.glMakeNamedBufferResidentNV(11, 1);
            load.verify(() -> NVShaderBufferLoad.glMakeNamedBufferResidentNV(11, 1));
            service.glMakeNamedBufferNonResidentNV(11);
            load.verify(() -> NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(11));
            service.glBufferAddressRangeNV(1, 2, 0xABCDL, 64L);
            unified.verify(() -> NVVertexBufferUnifiedMemory.glBufferAddressRangeNV(1, 2, 0xABCDL, 64L));
            service.glEnableClientState(3);
            gl11.verify(() -> GL11.glEnableClientState(3));
            service.glDisableClientState(3);
            gl11.verify(() -> GL11.glDisableClientState(3));

            service.glDrawMeshTasksNV(0, 4);
            mesh.verify(() -> NVMeshShader.glDrawMeshTasksNV(0, 4));
            service.glMultiDrawMeshTasksIndirectNV(32L, 2, 8);
            mesh.verify(() -> NVMeshShader.glMultiDrawMeshTasksIndirectNV(32L, 2, 8));
            service.glBufferPageCommitmentARB(1, 0L, 65536L, true);
            sparse.verify(() -> ARBSparseBuffer.glBufferPageCommitmentARB(1, 0L, 65536L, true));
        }
    }

    @Test
    void memoryCallsUseLwjglsAllocator() {
        LWJGL3Service service = LWJGL3Service.create();
        try (com.bdmajora.impetus.lwjgl.MemoryStack stack = service.stackPush()) {
            assertInstanceOf(LWJGL3MemoryStack.class, stack);
            assertEquals(4, stack.mallocInt(4).capacity());
        }
        long raw = service.nmemAlloc(16);
        assertNotEquals(0L, raw);
        raw = service.nmemRealloc(raw, 32);
        assertNotEquals(0L, raw);
        service.nmemFree(raw);
        long zeroed = service.nmemCalloc(2, 8);
        assertEquals(0L, service.memGetLong(zeroed + 8));
        service.nmemFree(zeroed);
        long aligned = service.nmemAlignedAlloc(64, 100);
        assertEquals(0L, aligned % 64);
        service.nmemAlignedFree(aligned);

        ByteBuffer buffer = service.memAlloc(16);
        assertEquals(16, buffer.capacity());
        buffer = service.memRealloc(buffer, 32);
        assertEquals(32, buffer.capacity());
        long address = service.memAddress(buffer);
        assertEquals(MemoryUtil.memAddress(buffer), address);
        // The plain form follows the buffer's position; the positioned one counts elements from the base regardless
        buffer.position(8);
        assertEquals(address + 8, service.memAddress(buffer));
        assertEquals(address + 4, service.memAddress(buffer, 4));
        buffer.position(0);
        assertEquals(address + 2, service.memAddress(buffer.asShortBuffer(), 1));
        assertEquals(address + 2, service.memAddress(buffer.asCharBuffer(), 1));
        assertEquals(address + 4, service.memAddress(buffer.asIntBuffer(), 1));
        assertEquals(address + 4, service.memAddress(buffer.asFloatBuffer(), 1));
        assertEquals(address + 8, service.memAddress(buffer.asLongBuffer(), 1));
        assertEquals(address + 8, service.memAddress(buffer.asDoubleBuffer(), 1));
        assertEquals(4L, service.memAddress(null, 4));
        assertEquals(32, service.memByteBuffer(address, 32).capacity());
        ByteBuffer slice = service.memSlice(buffer, 8, 8);
        assertEquals(address + 8, service.memAddress(slice));
        assertEquals(8, slice.capacity());

        service.memSet(address, 0x5A, 32);
        assertEquals((byte) 0x5A, service.memGetByte(address + 31));
        ByteBuffer zero = service.memCalloc(32);
        long zeroAddress = service.memAddress(zero);
        assertEquals(0, service.memGetByte(zeroAddress + 31));
        service.memCopy(address, zeroAddress, 16);
        assertEquals((byte) 0x5A, service.memGetByte(zeroAddress + 15));
        assertEquals(0, service.memGetByte(zeroAddress + 16));
        service.memPutByte(zeroAddress, (byte) 1);
        assertEquals(1, service.memGetByte(zeroAddress));
        service.memPutShort(zeroAddress, (short) 2);
        assertEquals(2, service.memGetShort(zeroAddress));
        service.memPutInt(zeroAddress, 3);
        assertEquals(3, service.memGetInt(zeroAddress));
        service.memPutFloat(zeroAddress, 4f);
        assertEquals(4f, service.memGetFloat(zeroAddress));
        service.memPutLong(zeroAddress, 5L);
        assertEquals(5L, service.memGetLong(zeroAddress));
        service.memPutAddress(zeroAddress, address);
        assertEquals(address, service.memGetAddress(zeroAddress));
        service.memFree(buffer);
        service.memFree(zero);
    }
}
