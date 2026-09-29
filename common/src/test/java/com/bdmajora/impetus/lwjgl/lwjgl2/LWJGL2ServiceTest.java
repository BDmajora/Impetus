package com.bdmajora.impetus.lwjgl.lwjgl2;

import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryUtilities;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.APPLEVertexArrayObject;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTGpuShader4;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;
import org.mockito.MockedStatic;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LWJGL2ServiceTest {
    private static String name(Object mode) {
        return ((Enum<?>) mode).name();
    }

    @Test
    void createResolvesEntryPointsFromTheCapabilities() {
        ContextCapabilities caps = GLContext.getCapabilities();
        LWJGL2Service core = LWJGL2Service.create();
        assertEquals("CORE", name(core.vaoMode()));
        assertEquals("CORE", name(core.timerQueryMode()));
        assertEquals("CORE", name(core.vertexAttribIMode()));
        assertTrue(core.syncObjects().isEmpty());
        try (MockedStatic<GL30> gl30 = mockStatic(GL30.class); MockedStatic<GL33> gl33 = mockStatic(GL33.class)) {
            gl30.when(GL30::glGenVertexArrays).thenReturn(4);
            gl33.when(() -> GL33.glGetQueryObjectui64(1, 2)).thenReturn(99L);
            assertEquals(4, core.glGenVertexArrays());
            core.glDeleteVertexArrays(4);
            core.glBindVertexArray(4);
            gl30.verify(() -> GL30.glDeleteVertexArrays(4));
            gl30.verify(() -> GL30.glBindVertexArray(4));
            core.glQueryCounter(1, 2);
            gl33.verify(() -> GL33.glQueryCounter(1, 2));
            assertEquals(99L, core.glGetQueryObjectui64(1, 2));
            core.glVertexAttribIPointer(1, 2, 3, 4, 5L);
            gl30.verify(() -> GL30.glVertexAttribIPointer(1, 2, 3, 4, 5L));
        }

        caps.OpenGL30 = false;
        caps.OpenGL33 = false;
        caps.GL_ARB_vertex_array_object = true;
        caps.GL_ARB_timer_query = true;
        caps.GL_EXT_gpu_shader4 = true;
        LWJGL2Service arb = LWJGL2Service.create();
        assertEquals("ARB", name(arb.vaoMode()));
        assertEquals("ARB", name(arb.timerQueryMode()));
        assertEquals("EXT", name(arb.vertexAttribIMode()));
        try (MockedStatic<ARBVertexArrayObject> vao = mockStatic(ARBVertexArrayObject.class);
             MockedStatic<ARBTimerQuery> timer = mockStatic(ARBTimerQuery.class);
             MockedStatic<EXTGpuShader4> ext = mockStatic(EXTGpuShader4.class)) {
            vao.when(ARBVertexArrayObject::glGenVertexArrays).thenReturn(5);
            timer.when(() -> ARBTimerQuery.glGetQueryObjectui64(1, 2)).thenReturn(98L);
            assertEquals(5, arb.glGenVertexArrays());
            arb.glDeleteVertexArrays(5);
            arb.glBindVertexArray(5);
            vao.verify(() -> ARBVertexArrayObject.glDeleteVertexArrays(5));
            vao.verify(() -> ARBVertexArrayObject.glBindVertexArray(5));
            arb.glQueryCounter(1, 2);
            timer.verify(() -> ARBTimerQuery.glQueryCounter(1, 2));
            assertEquals(98L, arb.glGetQueryObjectui64(1, 2));
            arb.glVertexAttribIPointer(1, 2, 3, 4, 5L);
            ext.verify(() -> EXTGpuShader4.glVertexAttribIPointerEXT(1, 2, 3, 4, 5L));
        }

        caps.GL_ARB_vertex_array_object = false;
        caps.GL_APPLE_vertex_array_object = true;
        LWJGL2Service apple = LWJGL2Service.create();
        assertEquals("APPLE", name(apple.vaoMode()));
        try (MockedStatic<APPLEVertexArrayObject> vao = mockStatic(APPLEVertexArrayObject.class)) {
            vao.when(APPLEVertexArrayObject::glGenVertexArraysAPPLE).thenReturn(6);
            assertEquals(6, apple.glGenVertexArrays());
            apple.glDeleteVertexArrays(6);
            apple.glBindVertexArray(6);
            vao.verify(() -> APPLEVertexArrayObject.glDeleteVertexArraysAPPLE(6));
            vao.verify(() -> APPLEVertexArrayObject.glBindVertexArrayAPPLE(6));
        }

        caps.none();
        LWJGL2Service none = LWJGL2Service.create();
        assertEquals("NONE", name(none.vaoMode()));
        assertEquals("NONE", name(none.timerQueryMode()));
        assertEquals("NONE", name(none.vertexAttribIMode()));
        assertThrows(UnsupportedOperationException.class, none::glGenVertexArrays);
        assertThrows(UnsupportedOperationException.class, () -> none.glDeleteVertexArrays(1));
        assertThrows(UnsupportedOperationException.class, () -> none.glBindVertexArray(1));
        assertThrows(UnsupportedOperationException.class, () -> none.glVertexAttribIPointer(1, 2, 3, 4, 5L));
        none.glQueryCounter(1, 2);
        assertEquals(0L, none.glGetQueryObjectui64(1, 2));
    }

    @Test
    void capabilityQueriesReadTheContextFlags() {
        ContextCapabilities caps = GLContext.getCapabilities();
        LWJGL2Service service = LWJGL2Service.create();
        int[][] versions = {{1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {3, 2}, {3, 3},
                {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}, {4, 5}};
        for (int[] v : versions) {
            assertTrue(service.isOpenGLVersionSupported(v[0], v[1]), v[0] + "." + v[1]);
        }
        assertFalse(service.isOpenGLVersionSupported(4, 6));
        assertFalse(service.isOpenGLVersionSupported(1, 0));
        for (GLExtension extension : GLExtension.values()) {
            boolean expected = switch (extension) {
                case NVX_gpu_memory_info, NV_mesh_shader, NV_shader_buffer_load, NV_vertex_buffer_unified_memory,
                     NV_uniform_buffer_unified_memory, NV_representative_fragment_test,
                     NV_bindless_multi_draw_indirect, NV_gpu_shader5, NV_fragment_shader_barycentric,
                     ARB_sparse_buffer -> false;
                default -> true;
            };
            assertEquals(expected, service.isExtensionSupported(extension), extension.name());
        }
        caps.GL_NVX_gpu_memory_info = true;
        assertTrue(service.isExtensionSupported(GLExtension.NVX_gpu_memory_info));
        assertTrue(service.supportsBufferBlending());
        caps.OpenGL40 = false;
        assertTrue(service.supportsBufferBlending());
        caps.GL_ARB_draw_buffers_blend = false;
        assertFalse(service.supportsBufferBlending());
        assertEquals(8, service.getPointerSize());
        caps.none();
        for (int[] v : versions) {
            assertFalse(service.isOpenGLVersionSupported(v[0], v[1]));
        }
        for (GLExtension extension : GLExtension.values()) {
            assertFalse(service.isExtensionSupported(extension));
        }
    }

    @Test
    void bufferCallsForwardToTheirGlClasses() {
        LWJGL2Service service = LWJGL2Service.create();
        ByteBuffer data = ByteBuffer.allocateDirect(16);
        try (MockedStatic<GL15> gl15 = mockStatic(GL15.class); MockedStatic<GL30> gl30 = mockStatic(GL30.class);
             MockedStatic<GL31> gl31 = mockStatic(GL31.class); MockedStatic<GL43> gl43 = mockStatic(GL43.class);
             MockedStatic<ARBBufferStorage> storage = mockStatic(ARBBufferStorage.class)) {
            gl15.when(GL15::glGenBuffers).thenReturn(3);
            assertEquals(3, service.glGenBuffers());
            service.glDeleteBuffers(3);
            gl15.verify(() -> GL15.glDeleteBuffers(3));
            service.glBindBuffer(1, 3);
            gl15.verify(() -> GL15.glBindBuffer(1, 3));
            service.glBufferData(1, 64L, 2);
            gl15.verify(() -> GL15.glBufferData(1, 64L, 2));
            service.glBufferData(1, data, 2);
            gl15.verify(() -> GL15.glBufferData(1, data, 2));
            // A zero pointer allocates uninitialised, a real one is wrapped as a buffer of that size
            service.glBufferData(1, 32L, 0L, 2);
            gl15.verify(() -> GL15.glBufferData(1, 32L, 2));
            long address = MemoryUtilities.memAddress(data);
            service.glBufferData(1, 16L, address, 2);
            gl15.verify(() -> GL15.glBufferData(eq(1), argThat((ByteBuffer b) -> b != data && b.capacity() == 16
                    && MemoryUtilities.memAddress(b) == address), eq(2)));
            service.glBufferSubData(1, 8L, data);
            gl15.verify(() -> GL15.glBufferSubData(1, 8L, data));
            service.glBufferStorage(1, 64L, 5);
            storage.verify(() -> ARBBufferStorage.glBufferStorage(1, 64L, 5));
            service.glClearBufferData(1, 2, 3, 4, data);
            gl43.verify(() -> GL43.glClearBufferData(1, 2, 3, 4, data));
            gl30.when(() -> GL30.glMapBufferRange(1, 0L, 16L, 7, null)).thenReturn(data);
            assertSame(data, service.glMapBufferRange(1, 0L, 16L, 7));
            assertEquals(0L, service.nglMapBuffer(1, 2));
            gl15.when(() -> GL15.glMapBuffer(1, 2, null)).thenReturn(data);
            assertEquals(address, service.nglMapBuffer(1, 2));
            assertSame(data, service.glMapBuffer(1, 2));
            service.glUnmapBuffer(1);
            gl15.verify(() -> GL15.glUnmapBuffer(1));
            service.glFlushMappedBufferRange(1, 2L, 3L);
            gl30.verify(() -> GL30.glFlushMappedBufferRange(1, 2L, 3L));
            service.glCopyBufferSubData(1, 2, 3L, 4L, 5L);
            gl31.verify(() -> GL31.glCopyBufferSubData(1, 2, 3L, 4L, 5L));
            service.glBindBufferBase(1, 2, 3);
            gl30.verify(() -> GL30.glBindBufferBase(1, 2, 3));
        }
    }

    @Test
    void vertexShaderAndUniformCallsForward() {
        LWJGL2Service service = LWJGL2Service.create();
        FloatBuffer floats = ByteBuffer.allocateDirect(64).asFloatBuffer();
        IntBuffer ints = ByteBuffer.allocateDirect(16).asIntBuffer();
        try (MockedStatic<GL20> gl20 = mockStatic(GL20.class); MockedStatic<GL30> gl30 = mockStatic(GL30.class);
             MockedStatic<GL31> gl31 = mockStatic(GL31.class)) {
            service.glVertexAttribPointer(1, 2, 3, true, 4, 5L);
            gl20.verify(() -> GL20.glVertexAttribPointer(1, 2, 3, true, 4, 5L));
            service.glEnableVertexAttribArray(1);
            gl20.verify(() -> GL20.glEnableVertexAttribArray(1));
            service.glDisableVertexAttribArray(1);
            gl20.verify(() -> GL20.glDisableVertexAttribArray(1));
            gl20.when(() -> GL20.glGetVertexAttrib(eq(1), eq(2), any(IntBuffer.class))).thenAnswer(inv -> {
                inv.<IntBuffer>getArgument(2).put(0, 9);
                return null;
            });
            assertEquals(9, service.glGetVertexAttribi(1, 2));

            gl20.when(() -> GL20.glCreateShader(1)).thenReturn(7);
            assertEquals(7, service.glCreateShader(1));
            service.glShaderSource(7, "void main() {}");
            gl20.verify(() -> GL20.glShaderSource(7, (CharSequence) "void main() {}"));
            service.glShaderSourceSafe(7, "abc");
            gl20.verify(() -> GL20.glShaderSource(eq(7), argThat((ByteBuffer b) -> b.capacity() == 4
                    && MemoryUtilities.memASCII(b, 3).equals("abc"))));
            service.glCompileShader(7);
            gl20.verify(() -> GL20.glCompileShader(7));
            gl20.when(() -> GL20.glGetShaderInfoLog(7, 100)).thenReturn("log");
            assertEquals("log", service.glGetShaderInfoLog(7, 100));
            gl20.when(() -> GL20.glGetShaderi(7, 1)).thenReturn(1);
            assertEquals(1, service.glGetShaderi(7, 1));
            service.glDeleteShader(7);
            gl20.verify(() -> GL20.glDeleteShader(7));
            gl20.when(GL20::glCreateProgram).thenReturn(8);
            assertEquals(8, service.glCreateProgram());
            service.glAttachShader(8, 7);
            gl20.verify(() -> GL20.glAttachShader(8, 7));
            service.glDetachShader(8, 7);
            gl20.verify(() -> GL20.glDetachShader(8, 7));
            service.glLinkProgram(8);
            gl20.verify(() -> GL20.glLinkProgram(8));
            gl20.when(() -> GL20.glGetProgramInfoLog(8, 100)).thenReturn("plog");
            assertEquals("plog", service.glGetProgramInfoLog(8, 100));
            gl20.when(() -> GL20.glGetProgrami(8, 1)).thenReturn(1);
            assertEquals(1, service.glGetProgrami(8, 1));
            gl20.when(() -> GL20.glGetActiveUniform(8, 0, 32, ints)).thenReturn("u_Name");
            assertEquals("u_Name", service.glGetActiveUniform(8, 0, 32, ints));
            service.glUseProgram(8);
            gl20.verify(() -> GL20.glUseProgram(8));
            service.glDeleteProgram(8);
            gl20.verify(() -> GL20.glDeleteProgram(8));
            service.glBindAttribLocation(8, 0, "a_Pos");
            gl20.verify(() -> GL20.glBindAttribLocation(8, 0, "a_Pos"));
            service.glBindFragDataLocation(8, 0, "fragColor");
            gl30.verify(() -> GL30.glBindFragDataLocation(8, 0, "fragColor"));
            gl20.when(() -> GL20.glGetUniformLocation(8, "u")).thenReturn(4);
            assertEquals(4, service.glGetUniformLocation(8, "u"));
            gl31.when(() -> GL31.glGetUniformBlockIndex(8, "b")).thenReturn(2);
            assertEquals(2, service.glGetUniformBlockIndex(8, "b"));
            service.glUniformBlockBinding(8, 2, 1);
            gl31.verify(() -> GL31.glUniformBlockBinding(8, 2, 1));
            gl20.when(() -> GL20.glGetAttribLocation(8, "a")).thenReturn(3);
            assertEquals(3, service.glGetAttribLocation(8, "a"));

            service.glUniform1f(1, 2f);
            gl20.verify(() -> GL20.glUniform1f(1, 2f));
            service.glUniform1i(1, 2);
            gl20.verify(() -> GL20.glUniform1i(1, 2));
            service.glUniform1fv(1, floats);
            gl20.verify(() -> GL20.glUniform1(1, floats));
            service.glUniform2i(1, 2, 3);
            gl20.verify(() -> GL20.glUniform2i(1, 2, 3));
            service.glUniform3i(1, 2, 3, 4);
            gl20.verify(() -> GL20.glUniform3i(1, 2, 3, 4));
            service.glUniform2f(1, 2f, 3f);
            gl20.verify(() -> GL20.glUniform2f(1, 2f, 3f));
            service.glUniform4f(1, 2f, 3f, 4f, 5f);
            gl20.verify(() -> GL20.glUniform4f(1, 2f, 3f, 4f, 5f));
            service.glUniform4i(1, 2, 3, 4, 5);
            gl20.verify(() -> GL20.glUniform4i(1, 2, 3, 4, 5));
            service.glUniform3f(1, 2f, 3f, 4f);
            gl20.verify(() -> GL20.glUniform3f(1, 2f, 3f, 4f));
            service.glUniform3fv(1, floats);
            gl20.verify(() -> GL20.glUniform3(1, floats));
            service.glUniform4fv(1, floats);
            gl20.verify(() -> GL20.glUniform4(1, floats));
            service.glUniformMatrix3fv(1, true, floats);
            gl20.verify(() -> GL20.glUniformMatrix3(1, true, floats));
            service.glUniformMatrix4fv(1, false, floats);
            gl20.verify(() -> GL20.glUniformMatrix4(1, false, floats));
            // Array uniforms use the scalar entry point for a single vector and a stack buffer for arrays
            service.glUniform3fv(2, new float[] {1f, 2f, 3f});
            gl20.verify(() -> GL20.glUniform3f(2, 1f, 2f, 3f));
            service.glUniform3fv(2, new float[] {1f, 2f, 3f, 4f, 5f, 6f});
            gl20.verify(() -> GL20.glUniform3(eq(2), argThat((FloatBuffer f) -> f.remaining() == 6 && f.get(5) == 6f)));
            assertThrows(IllegalArgumentException.class, () -> service.glUniform3fv(2, new float[4]));
            service.glUniform4fv(2, new float[] {1f, 2f, 3f, 4f});
            gl20.verify(() -> GL20.glUniform4f(2, 1f, 2f, 3f, 4f));
            service.glUniform4fv(2, new float[8]);
            gl20.verify(() -> GL20.glUniform4(eq(2), argThat((FloatBuffer f) -> f.remaining() == 8)));
            assertThrows(IllegalArgumentException.class, () -> service.glUniform4fv(2, new float[5]));
        }
    }

    @Test
    void drawSyncAndQueryCallsForward() {
        LWJGL2Service service = LWJGL2Service.create();
        try (MockedStatic<GL32> gl32 = mockStatic(GL32.class); MockedStatic<GL43> gl43 = mockStatic(GL43.class);
             MockedStatic<GL15> gl15 = mockStatic(GL15.class)) {
            service.glDrawElementsBaseVertex(4, 6, 2, 8L, 1);
            gl32.verify(() -> GL32.glDrawElementsBaseVertex(4, 6, 2, 8L, 1));
            // Multi-draw is emulated one draw at a time, skipping empty entries
            long counts = MemoryUtilities.nmemCalloc(3, 4);
            long indices = MemoryUtilities.nmemCalloc(3, 8);
            long bases = MemoryUtilities.nmemCalloc(3, 4);
            MemoryUtilities.memPutInt(counts, 3);
            MemoryUtilities.memPutInt(counts + 8, 2);
            MemoryUtilities.memPutAddress(indices, 100L);
            MemoryUtilities.memPutAddress(indices + 16, 300L);
            MemoryUtilities.memPutInt(bases, 1);
            MemoryUtilities.memPutInt(bases + 8, 3);
            service.glMultiDrawElementsBaseVertex(4, counts, 2, indices, 3, bases);
            gl32.verify(() -> GL32.glDrawElementsBaseVertex(4, 3, 2, 100L, 1));
            gl32.verify(() -> GL32.glDrawElementsBaseVertex(4, 2, 2, 300L, 3));
            gl32.verify(() -> GL32.glDrawElementsBaseVertex(anyInt(), anyInt(), anyInt(), anyLong(), anyInt()), times(3));
            MemoryUtilities.nmemFree(counts);
            MemoryUtilities.nmemFree(indices);
            MemoryUtilities.nmemFree(bases);
            service.glMultiDrawElementsIndirect(4, 2, 0L, 5, 20);
            gl43.verify(() -> GL43.glMultiDrawElementsIndirect(4, 2, 0L, 5, 20));

            GLSync sync = mock(GLSync.class);
            when(sync.getPointer()).thenReturn(77L);
            gl32.when(() -> GL32.glFenceSync(1, 0)).thenReturn(sync);
            assertEquals(77L, service.glFenceSync(1, 0));
            assertSame(sync, service.syncObjects().get(77L));
            gl32.when(() -> GL32.glClientWaitSync(sync, 1, 5L)).thenReturn(0x911A);
            assertEquals(0x911A, service.glClientWaitSync(77L, 1, 5L));
            gl32.when(() -> GL32.glGetSynci(sync, 0x9114)).thenReturn(0x9119);
            assertEquals(0x9119, service.glGetSynci(77L, 0x9114, null));
            IntBuffer length = ByteBuffer.allocateDirect(4).asIntBuffer();
            assertEquals(0x9119, service.glGetSynci(77L, 0x9114, length));
            assertEquals(1, length.get(0));
            service.glWaitSync(77L, 0, 5L);
            gl32.verify(() -> GL32.glWaitSync(sync, 0, 5L));
            service.glDeleteSync(12345L);
            gl32.verify(() -> GL32.glDeleteSync(any()), never());
            service.glDeleteSync(77L);
            gl32.verify(() -> GL32.glDeleteSync(sync));
            assertTrue(service.syncObjects().isEmpty());

            gl15.when(GL15::glGenQueries).thenReturn(9);
            assertEquals(9, service.glGenQueries());
            service.glDeleteQueries(9);
            gl15.verify(() -> GL15.glDeleteQueries(9));
            gl15.when(() -> GL15.glGetQueryObjecti(9, 1)).thenReturn(1);
            assertEquals(1, service.glGetQueryObjecti(9, 1));
        }
    }

    @Test
    void textureAndComputeCallsForward() {
        LWJGL2Service service = LWJGL2Service.create();
        ByteBuffer pixels = ByteBuffer.allocateDirect(16);
        try (MockedStatic<GL11> gl11 = mockStatic(GL11.class); MockedStatic<GL12> gl12 = mockStatic(GL12.class);
             MockedStatic<GL13> gl13 = mockStatic(GL13.class); MockedStatic<GL30> gl30 = mockStatic(GL30.class);
             MockedStatic<GL33> gl33 = mockStatic(GL33.class); MockedStatic<GL42> gl42 = mockStatic(GL42.class);
             MockedStatic<GL43> gl43 = mockStatic(GL43.class);
             MockedStatic<ARBClearTexture> clear = mockStatic(ARBClearTexture.class)) {
            gl11.when(GL11::glGenTextures).thenReturn(2);
            assertEquals(2, service.glGenTextures());
            gl11.when(() -> GL11.glGenTextures(any(IntBuffer.class))).thenAnswer(inv -> {
                IntBuffer buf = inv.getArgument(0);
                for (int i = 0; i < buf.remaining(); i++) {
                    buf.put(i, 10 + i);
                }
                return null;
            });
            int[] textures = new int[3];
            service.glGenTextures(textures);
            assertArrayEquals(new int[] {10, 11, 12}, textures);
            service.glDeleteTextures(2);
            gl11.verify(() -> GL11.glDeleteTextures(2));
            service.glDeleteTextures(textures);
            gl11.verify(() -> GL11.glDeleteTextures(argThat((IntBuffer b) -> b.remaining() == 3 && b.get(2) == 12)));
            service.glBindTexture(1, 2);
            gl11.verify(() -> GL11.glBindTexture(1, 2));
            service.glActiveTexture(3);
            gl13.verify(() -> GL13.glActiveTexture(3));
            service.glMultiTexCoord2f(3, 0.5f, 0.25f);
            gl13.verify(() -> GL13.glMultiTexCoord2f(3, 0.5f, 0.25f));
            gl11.when(() -> GL11.glGetTexLevelParameteri(1, 0, 2)).thenReturn(64);
            assertEquals(64, service.glGetTexLevelParameteri(1, 0, 2));
            service.glCopyTexSubImage2D(1, 0, 2, 3, 4, 5, 6, 7);
            gl11.verify(() -> GL11.glCopyTexSubImage2D(1, 0, 2, 3, 4, 5, 6, 7));
            service.glReadPixels(1, 2, 3, 4, 5, 6, pixels);
            gl11.verify(() -> GL11.glReadPixels(1, 2, 3, 4, 5, 6, pixels));
            service.glReadPixels(1, 2, 3, 4, 5, 6, 8L);
            gl11.verify(() -> GL11.glReadPixels(1, 2, 3, 4, 5, 6, 8L));
            service.glGenerateMipmap(1);
            gl30.verify(() -> GL30.glGenerateMipmap(1));
            gl33.when(GL33::glGenSamplers).thenReturn(4);
            assertEquals(4, service.glGenSamplers());
            service.glDeleteSamplers(4);
            gl33.verify(() -> GL33.glDeleteSamplers(4));
            service.glBindSampler(0, 4);
            gl33.verify(() -> GL33.glBindSampler(0, 4));
            service.glSamplerParameteri(4, 1, 2);
            gl33.verify(() -> GL33.glSamplerParameteri(4, 1, 2));
            service.glDepthRange(0.1, 0.9);
            gl11.verify(() -> GL11.glDepthRange(0.1, 0.9));
            service.glPixelStorei(1, 2);
            gl11.verify(() -> GL11.glPixelStorei(1, 2));
            service.glTexImage2D(1, 0, 2, 3, 4, 0, 5, 6, pixels);
            gl11.verify(() -> GL11.glTexImage2D(1, 0, 2, 3, 4, 0, 5, 6, pixels));
            service.glTexImage3D(1, 0, 2, 3, 4, 5, 0, 6, 7, pixels);
            gl12.verify(() -> GL12.glTexImage3D(1, 0, 2, 3, 4, 5, 0, 6, 7, pixels));
            service.glBindImageTexture(0, 2, 0, false, 0, 1, 3);
            gl42.verify(() -> GL42.glBindImageTexture(0, 2, 0, false, 0, 1, 3));
            service.glMemoryBarrier(7);
            gl42.verify(() -> GL42.glMemoryBarrier(7));
            service.glDispatchCompute(1, 2, 3);
            gl43.verify(() -> GL43.glDispatchCompute(1, 2, 3));
            service.glDispatchComputeIndirect(16L);
            gl43.verify(() -> GL43.glDispatchComputeIndirect(16L));
            service.glClearTexImage(2, 0, 1, 3, pixels);
            clear.verify(() -> ARBClearTexture.glClearTexImage(2, 0, 1, 3, pixels));
            service.glTexParameteri(1, 2, 3);
            gl11.verify(() -> GL11.glTexParameteri(1, 2, 3));
            service.glTexParameteriv(1, 2, new int[] {5, 6});
            gl11.verify(() -> GL11.glTexParameter(eq(1), eq(2), argThat((IntBuffer b) -> b.remaining() == 2 && b.get(1) == 6)));
            service.glTexParameterf(1, 2, 0.5f);
            gl11.verify(() -> GL11.glTexParameterf(1, 2, 0.5f));
        }
    }

    @Test
    void framebufferAndStateCallsForward() {
        LWJGL2Service service = LWJGL2Service.create();
        IntBuffer bufs = ByteBuffer.allocateDirect(8).asIntBuffer();
        FloatBuffer matrix = ByteBuffer.allocateDirect(64).asFloatBuffer();
        try (MockedStatic<GL11> gl11 = mockStatic(GL11.class); MockedStatic<GL14> gl14 = mockStatic(GL14.class);
             MockedStatic<GL20> gl20 = mockStatic(GL20.class); MockedStatic<GL30> gl30 = mockStatic(GL30.class);
             MockedStatic<ARBDrawBuffersBlend> blend = mockStatic(ARBDrawBuffersBlend.class)) {
            gl30.when(GL30::glGenFramebuffers).thenReturn(5);
            assertEquals(5, service.glGenFramebuffers());
            service.glDeleteFramebuffers(5);
            gl30.verify(() -> GL30.glDeleteFramebuffers(5));
            service.glBindFramebuffer(1, 5);
            gl30.verify(() -> GL30.glBindFramebuffer(1, 5));
            gl30.when(() -> GL30.glCheckFramebufferStatus(1)).thenReturn(0x8CD5);
            assertEquals(0x8CD5, service.glCheckFramebufferStatus(1));
            service.glFramebufferTexture2D(1, 2, 3, 4, 0);
            gl30.verify(() -> GL30.glFramebufferTexture2D(1, 2, 3, 4, 0));
            service.glFramebufferTextureLayer(1, 2, 3, 0, 4);
            gl30.verify(() -> GL30.glFramebufferTextureLayer(1, 2, 3, 0, 4));
            service.glDrawBuffers(1);
            gl20.verify(() -> GL20.glDrawBuffers(1));
            service.glDrawBuffers(bufs);
            gl20.verify(() -> GL20.glDrawBuffers(bufs));
            service.glReadBuffer(2);
            gl11.verify(() -> GL11.glReadBuffer(2));
            service.glBlitFramebuffer(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
            gl30.verify(() -> GL30.glBlitFramebuffer(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
            gl30.when(GL30::glGenRenderbuffers).thenReturn(6);
            assertEquals(6, service.glGenRenderbuffers());
            service.glDeleteRenderbuffers(6);
            gl30.verify(() -> GL30.glDeleteRenderbuffers(6));
            service.glBindRenderbuffer(1, 6);
            gl30.verify(() -> GL30.glBindRenderbuffer(1, 6));
            service.glRenderbufferStorage(1, 2, 3, 4);
            gl30.verify(() -> GL30.glRenderbufferStorage(1, 2, 3, 4));
            service.glFramebufferRenderbuffer(1, 2, 3, 6);
            gl30.verify(() -> GL30.glFramebufferRenderbuffer(1, 2, 3, 6));

            service.glEnable(1);
            gl11.verify(() -> GL11.glEnable(1));
            service.glDisable(1);
            gl11.verify(() -> GL11.glDisable(1));
            service.glEnablei(1, 2);
            gl30.verify(() -> GL30.glEnablei(1, 2));
            service.glDisablei(1, 2);
            gl30.verify(() -> GL30.glDisablei(1, 2));
            service.glBlendFunc(1, 2);
            gl11.verify(() -> GL11.glBlendFunc(1, 2));
            service.glBlendFuncSeparate(1, 2, 3, 4);
            gl14.verify(() -> GL14.glBlendFuncSeparate(1, 2, 3, 4));
            service.glBlendFuncSeparatei(0, 1, 2, 3, 4);
            blend.verify(() -> ARBDrawBuffersBlend.glBlendFuncSeparateiARB(0, 1, 2, 3, 4));
            service.glDepthFunc(1);
            gl11.verify(() -> GL11.glDepthFunc(1));
            service.glDepthMask(true);
            gl11.verify(() -> GL11.glDepthMask(true));
            service.glColorMask(true, false, true, false);
            gl11.verify(() -> GL11.glColorMask(true, false, true, false));
            service.glViewport(0, 0, 10, 20);
            gl11.verify(() -> GL11.glViewport(0, 0, 10, 20));
            service.glClear(3);
            gl11.verify(() -> GL11.glClear(3));
            service.glClearColor(0f, 0.5f, 1f, 1f);
            gl11.verify(() -> GL11.glClearColor(0f, 0.5f, 1f, 1f));
            service.glClearDepth(1.0);
            gl11.verify(() -> GL11.glClearDepth(1.0));
            service.glCullFace(1);
            gl11.verify(() -> GL11.glCullFace(1));
            service.glDrawArrays(4, 0, 3);
            gl11.verify(() -> GL11.glDrawArrays(4, 0, 3));
            gl11.when(GL11::glGetError).thenReturn(0x500);
            assertEquals(0x500, service.glGetError());
            service.glMatrixMode(1);
            gl11.verify(() -> GL11.glMatrixMode(1));
            service.glLoadMatrixf(matrix);
            gl11.verify(() -> GL11.glLoadMatrix(matrix));
            gl11.when(() -> GL11.glGetInteger(1)).thenReturn(42);
            assertEquals(42, service.glGetInteger(1));
            gl11.when(() -> GL11.glGetFloat(1)).thenReturn(0.5f);
            assertEquals(0.5f, service.glGetFloat(1));
            gl11.when(() -> GL11.glGetInteger(eq(2), any(IntBuffer.class))).thenAnswer(inv -> {
                inv.<IntBuffer>getArgument(1).put(0, 7).put(1, 8);
                return null;
            });
            int[] values = new int[2];
            service.glGetIntegerv(2, values);
            assertArrayEquals(new int[] {7, 8}, values);
            gl11.when(() -> GL11.glGetBoolean(1)).thenReturn(true);
            assertTrue(service.glGetBoolean(1));
            gl11.when(() -> GL11.glGetString(1)).thenReturn("vendor");
            assertEquals("vendor", service.glGetString(1));
            service.glFinish();
            gl11.verify(GL11::glFinish);
        }
    }

    @Test
    void memoryCallsWrapTheVendoredUtilities() {
        LWJGL2Service service = LWJGL2Service.create();
        try (com.bdmajora.impetus.lwjgl.MemoryStack stack = service.stackPush()) {
            assertInstanceOf(LWJGL2MemoryStack.class, stack);
            assertEquals(4, stack.mallocInt(4).capacity());
        }
        long raw = service.nmemAlloc(16);
        assertNotEquals(0L, raw);
        raw = service.nmemRealloc(raw, 32);
        service.nmemFree(raw);
        long zeroed = service.nmemCalloc(2, 8);
        assertEquals(0L, service.memGetLong(zeroed + 8));
        service.nmemFree(zeroed);
        // Aligned allocations stash the real block address just below the returned pointer
        long aligned = service.nmemAlignedAlloc(64, 100);
        assertEquals(0, aligned % 64);
        long real = service.memGetLong(aligned - 8);
        assertTrue(real < aligned && aligned - real <= 64 + 8 + 64);
        service.nmemAlignedFree(aligned);
        service.nmemAlignedFree(0L);
        long small = service.nmemAlignedAlloc(1, 8);
        assertEquals(0, small % 8);
        service.nmemAlignedFree(small);

        ByteBuffer buffer = service.memAlloc(16);
        assertEquals(16, buffer.capacity());
        buffer = service.memRealloc(buffer, 32);
        assertEquals(32, buffer.capacity());
        long address = service.memAddress(buffer);
        assertEquals(MemoryUtilities.memAddress(buffer), address);
        assertEquals(address + 4, service.memAddress(buffer, 4));
        assertEquals(4L, service.memAddress(null, 4));
        assertEquals(32, service.memByteBuffer(address, 32).capacity());
        ByteBuffer slice = service.memSlice(buffer, 8, 8);
        assertEquals(address + 8, service.memAddress(slice));
        assertEquals(8, slice.capacity());
        service.memSet(address, 0x5A, 32);
        assertEquals((byte) 0x5A, service.memGetByte(address + 31));
        ByteBuffer zero = service.memCalloc(32);
        long zeroAddress = service.memAddress(zero);
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
        service.memFree(null);
    }
}
