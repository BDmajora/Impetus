package com.bdmajora.impetus.lwjgl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.*;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// Every GL function the engine calls resolved for the context.
public record LWJGLService(
        VAOMode vaoMode,
        TimerQueryMode timerQueryMode,
        VertexAttribIMode vertexAttribIMode,
        BlendIMode blendIMode) {
    // Logger of the service.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/LWJGL");

    // Returns whether the context supports an OpenGL version.
    public boolean isOpenGLVersionSupported(int major, int minor) {
        GLCapabilities caps = GL.getCapabilities();
        return switch (major * 10 + minor) {
            case 11 -> caps.OpenGL11;
            case 12 -> caps.OpenGL12;
            case 13 -> caps.OpenGL13;
            case 14 -> caps.OpenGL14;
            case 15 -> caps.OpenGL15;
            case 20 -> caps.OpenGL20;
            case 21 -> caps.OpenGL21;
            case 30 -> caps.OpenGL30;
            case 31 -> caps.OpenGL31;
            case 32 -> caps.OpenGL32;
            case 33 -> caps.OpenGL33;
            case 40 -> caps.OpenGL40;
            case 41 -> caps.OpenGL41;
            case 42 -> caps.OpenGL42;
            case 43 -> caps.OpenGL43;
            case 44 -> caps.OpenGL44;
            case 45 -> caps.OpenGL45;
            case 46 -> caps.OpenGL46;
            default -> false;
        };
    }

    // Returns whether the context supports an extension.
    public boolean isExtensionSupported(GLExtension extension) {
        GLCapabilities caps = GL.getCapabilities();
        return switch (extension) {
            case ARB_buffer_storage -> caps.GL_ARB_buffer_storage;
            case ARB_clear_buffer_object -> caps.GL_ARB_clear_buffer_object;
            case ARB_multi_draw_indirect -> caps.GL_ARB_multi_draw_indirect;
            case ARB_draw_elements_base_vertex -> caps.GL_ARB_draw_elements_base_vertex;
            case ARB_direct_state_access -> caps.GL_ARB_direct_state_access;
            case ARB_shader_storage_buffer_object -> caps.GL_ARB_shader_storage_buffer_object;
            case ARB_sync -> caps.GL_ARB_sync;
            case ARB_timer_query -> caps.GL_ARB_timer_query;
            case ARB_uniform_buffer_object -> caps.GL_ARB_uniform_buffer_object;
            case ARB_vertex_array_object -> caps.GL_ARB_vertex_array_object;
            case ARB_map_buffer_range -> caps.GL_ARB_map_buffer_range;
            case ARB_pixel_buffer_object -> caps.GL_ARB_pixel_buffer_object;
            case ARB_copy_buffer -> caps.GL_ARB_copy_buffer;
            case ARB_texture_storage -> caps.GL_ARB_texture_storage;
            case ARB_base_instance -> caps.GL_ARB_base_instance;
            case ARB_compatibility -> caps.GL_ARB_compatibility;
            case NVX_gpu_memory_info -> caps.GL_NVX_gpu_memory_info;
            case KHR_debug -> caps.GL_KHR_debug;
            case NV_mesh_shader -> caps.GL_NV_mesh_shader;
            case NV_shader_buffer_load -> caps.GL_NV_shader_buffer_load;
            case NV_vertex_buffer_unified_memory -> caps.GL_NV_vertex_buffer_unified_memory;
            case NV_uniform_buffer_unified_memory -> caps.GL_NV_uniform_buffer_unified_memory;
            case NV_representative_fragment_test -> caps.GL_NV_representative_fragment_test;
            case NV_bindless_multi_draw_indirect -> caps.GL_NV_bindless_multi_draw_indirect;
            case NV_gpu_shader5 -> caps.GL_NV_gpu_shader5;
            case NV_fragment_shader_barycentric -> caps.GL_NV_fragment_shader_barycentric;
            case ARB_sparse_buffer -> caps.GL_ARB_sparse_buffer;
        };
    }

    // Returns whether blending can be set per buffer.
    public boolean supportsBufferBlending() {
        return blendIMode != BlendIMode.NONE;
    }

    // Calls the GL function glGenBuffers.
    public int glGenBuffers() {
        return GL15C.glGenBuffers();
    }

    // Calls the GL function glDeleteBuffers.
    public void glDeleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    // Calls the GL function glBindBuffer.
    public void glBindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    // Calls the GL function glBufferData.
    public void glBufferData(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    // Calls the GL function glBufferData.
    public void glBufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    // Calls the GL function glBufferData.
    public void glBufferData(int target, long size, long data, int usage) {
        GL15C.nglBufferData(target, size, data, usage);
    }

    // Calls the GL function glBufferSubData.
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    // Calls the GL function glBufferStorage.
    public void glBufferStorage(int target, long size, int flags) {
        GL44C.glBufferStorage(target, size, flags);
    }

    // Calls the GL function glClearBufferData.
    public void glClearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data) {
        GL43C.glClearBufferData(target, internalFormat, format, type, data);
    }

    // Calls the GL function glMapBufferRange.
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return GL30C.glMapBufferRange(target, offset, length, flags);
    }

    // Calls the GL function nglMapBuffer.
    public long nglMapBuffer(int target, int access) {
        return GL15C.nglMapBuffer(target, access);
    }

    // Calls the GL function glMapBuffer.
    public ByteBuffer glMapBuffer(int target, int access) {
        return GL15C.glMapBuffer(target, access, null);
    }

    // Calls the GL function glUnmapBuffer.
    public void glUnmapBuffer(int target) {
        GL15C.glUnmapBuffer(target);
    }

    // Calls the GL function glFlushMappedBufferRange.
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        GL30C.glFlushMappedBufferRange(target, offset, length);
    }

    // Calls the GL function glCopyBufferSubData.
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    // Calls the GL function glBindBufferBase.
    public void glBindBufferBase(int target, int index, int buffer) {
        GL30C.glBindBufferBase(target, index, buffer);
    }

    // Way to reach vertex array objects.
    private sealed interface VAOMode {
        // Creates a vertex array.
        int gen();
        // Deletes a vertex array.
        void delete(int array);
        // Binds a vertex array.
        void bind(int array);
    }

    // Vertex array functions bound by LWJGL.
    private enum BoundVAO implements VAOMode {
        // Core functions.
        CORE {
            @Override public int gen() { return GL30C.glGenVertexArrays(); }
            @Override public void delete(int array) { GL30C.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { GL30C.glBindVertexArray(array); }
        },
        // Extension functions.
        ARB {
            @Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
            @Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
        },
        // No vertex array support.
        NONE {
            @Override public int gen() { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void delete(int array) { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void bind(int array) { throw new UnsupportedOperationException("VAO not supported"); }
        }
    }

    // Vertex array functions of the Apple extension called by address.
    private static final class AppleVAO implements VAOMode {
        // Address of the create function.
        private final long gen;
        // Address of the delete function.
        private final long delete;
        // Address of the bind function.
        private final long bind;

        // Creates the wrapper from the function addresses.
        private AppleVAO(FunctionProvider functions, long bind) {
            this.gen = functions.getFunctionAddress("glGenVertexArraysAPPLE");
            this.delete = functions.getFunctionAddress("glDeleteVertexArraysAPPLE");
            this.bind = bind;
        }

        @Override
        public int gen() {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                IntBuffer buf = stack.callocInt(1);
                JNI.callPV(1, MemoryUtil.memAddress(buf), gen);
                return buf.get(0);
            }
        }

        @Override
        public void delete(int array) {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                JNI.callPV(1, MemoryUtil.memAddress(stack.ints(array)), delete);
            }
        }

        @Override
        public void bind(int array) {
            JNI.callV(array, bind);
        }
    }

    // Ways to reach timer queries.
    private enum TimerQueryMode { CORE, ARB, NONE }
    // Ways to reach integer attributes.
    private enum VertexAttribIMode { CORE, EXT, NONE }
    // Ways to reach per buffer blending.
    private enum BlendIMode { CORE, ARB, NONE }

    // Resolves the entry points that depend on the version and extensions.
    public static LWJGLService create() {
        GLCapabilities caps = GL.getCapabilities();

        VAOMode vaoMode;

        if (caps.OpenGL30) {
            vaoMode = BoundVAO.CORE;
        } else if (caps.GL_ARB_vertex_array_object) {
            vaoMode = BoundVAO.ARB;
        } else {
            FunctionProvider functions = GL.getFunctionProvider();
            long bind = functions.getFunctionAddress("glBindVertexArrayAPPLE");
            boolean complete = bind != MemoryUtil.NULL
                    && functions.getFunctionAddress("glGenVertexArraysAPPLE") != MemoryUtil.NULL
                    && functions.getFunctionAddress("glDeleteVertexArraysAPPLE") != MemoryUtil.NULL;
            vaoMode = complete ? new AppleVAO(functions, bind) : BoundVAO.NONE;
        }

        TimerQueryMode timerQueryMode;

        if (caps.OpenGL33) {
            timerQueryMode = TimerQueryMode.CORE;
        } else if (caps.GL_ARB_timer_query) {
            timerQueryMode = TimerQueryMode.ARB;
        } else {
            timerQueryMode = TimerQueryMode.NONE;
            LOGGER.warn("ARB_timer_query extension not available - GPU profiling will be disabled");
        }

        VertexAttribIMode vertexAttribIMode;
        if (caps.OpenGL30) {
            vertexAttribIMode = VertexAttribIMode.CORE;
        } else if (caps.GL_EXT_gpu_shader4) {
            vertexAttribIMode = VertexAttribIMode.EXT;
        } else {
            vertexAttribIMode = VertexAttribIMode.NONE;
        }

        BlendIMode blendIMode;
        if (caps.OpenGL40) {
            blendIMode = BlendIMode.CORE;
        } else if (caps.GL_ARB_draw_buffers_blend) {
            blendIMode = BlendIMode.ARB;
        } else {
            blendIMode = BlendIMode.NONE;
        }

        return new LWJGLService(vaoMode, timerQueryMode, vertexAttribIMode, blendIMode);
    }

    // Creates a vertex array through the resolved mode.
    public int glGenVertexArrays() {
        return vaoMode.gen();
    }

    // Deletes a vertex array through the resolved mode.
    public void glDeleteVertexArrays(int array) {
        vaoMode.delete(array);
    }

    // Binds a vertex array through the resolved mode.
    public void glBindVertexArray(int array) {
        vaoMode.bind(array);
    }

    // Calls the GL function glVertexAttribPointer.
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    // Sets an integer attribute pointer through the core or the extension function.
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        switch (vertexAttribIMode) {
            case CORE -> GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
            case EXT -> EXTGPUShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
            case NONE -> throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
        }
    }

    // Calls the GL function glVertexAttribDivisor.
    public void glVertexAttribDivisor(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    // Calls the GL function glEnableVertexAttribArray.
    public void glEnableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    // Calls the GL function glDisableVertexAttribArray.
    public void glDisableVertexAttribArray(int index) {
        GL20C.glDisableVertexAttribArray(index);
    }

    // Calls the GL function glGetVertexAttribi.
    public int glGetVertexAttribi(int index, int pname) {
        return GL20C.glGetVertexAttribi(index, pname);
    }

    // Calls the GL function glCreateShader.
    public int glCreateShader(int type) {
        return GL20C.glCreateShader(type);
    }

    // Calls the GL function glShaderSource.
    public void glShaderSource(int shader, CharSequence source) {
        GL20C.glShaderSource(shader, source);
    }

    // Sets the shader source as a terminated string without a length array.
    public void glShaderSourceSafe(int shader, CharSequence source) {
        ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            GL20C.nglShaderSource(shader, 1, stack.pointers(sourceBuffer).address(), MemoryUtil.NULL);
        } finally {
            MemoryUtil.memFree(sourceBuffer);
        }
    }

    // Calls the GL function glCompileShader.
    public void glCompileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    // Calls the GL function glGetShaderInfoLog.
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return GL20C.glGetShaderInfoLog(shader);
    }

    // Calls the GL function glGetShaderi.
    public int glGetShaderi(int shader, int pname) {
        return GL20C.glGetShaderi(shader, pname);
    }

    // Calls the GL function glDeleteShader.
    public void glDeleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    // Calls the GL function glCreateProgram.
    public int glCreateProgram() {
        return GL20C.glCreateProgram();
    }

    // Calls the GL function glAttachShader.
    public void glAttachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    // Calls the GL function glDetachShader.
    public void glDetachShader(int program, int shader) {
        GL20C.glDetachShader(program, shader);
    }

    // Calls the GL function glLinkProgram.
    public void glLinkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    // Calls the GL function glGetProgramInfoLog.
    public String glGetProgramInfoLog(int program, int maxLength) {
        return GL20C.glGetProgramInfoLog(program);
    }

    // Calls the GL function glGetProgrami.
    public int glGetProgrami(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    // Calls the GL function glGetActiveUniform.
    public String glGetActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            String name = GL20C.glGetActiveUniform(program, index, maxLength, size, type);
            sizeType.put(0, size.get(0));
            sizeType.put(1, type.get(0));
            return name;
        }
    }

    // Calls the GL function glUseProgram.
    public void glUseProgram(int program) {
        GL20C.glUseProgram(program);
    }

    // Calls the GL function glDeleteProgram.
    public void glDeleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    // Calls the GL function glBindAttribLocation.
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20C.glBindAttribLocation(program, index, name);
    }

    // Calls the GL function glBindFragDataLocation.
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        GL30C.glBindFragDataLocation(program, colorNumber, name);
    }

    // Calls the GL function glGetUniformLocation.
    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    // Calls the GL function glGetUniformBlockIndex.
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    // Calls the GL function glUniformBlockBinding.
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        GL31C.glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    // Calls the GL function glUniform1f.
    public void glUniform1f(int location, float v0) {
        GL20C.glUniform1f(location, v0);
    }

    // Calls the GL function glUniform1i.
    public void glUniform1i(int location, int v0) {
        GL20C.glUniform1i(location, v0);
    }

    // Calls the GL function glUniform1fv.
    public void glUniform1fv(int location, FloatBuffer value) {
        GL20C.glUniform1fv(location, value);
    }

    // Calls the GL function glUniform1fv.
    public void glUniform1fv(int location, float[] value) {
        GL20C.glUniform1fv(location, value);
    }

    // Calls the GL function glUniform2i.
    public void glUniform2i(int location, int v0, int v1) {
        GL20C.glUniform2i(location, v0, v1);
    }

    // Calls the GL function glUniform3i.
    public void glUniform3i(int location, int v0, int v1, int v2) {
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    // Calls the GL function glUniform2f.
    public void glUniform2f(int location, float v0, float v1) {
        GL20C.glUniform2f(location, v0, v1);
    }

    // Calls the GL function glUniform4f.
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20C.glUniform4f(location, v0, v1, v2, v3);
    }

    // Calls the GL function glUniform4i.
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    // Returns whether uniforms can be written to an unbound program.
    public boolean supportsProgramUniforms() {
        GLCapabilities caps = GL.getCapabilities();
        return caps.OpenGL41 || caps.GL_ARB_separate_shader_objects;
    }

    // Calls the GL function glProgramUniform1i.
    public void glProgramUniform1i(int program, int location, int v0) {
        GL41C.glProgramUniform1i(program, location, v0);
    }

    // Calls the GL function glProgramUniform1f.
    public void glProgramUniform1f(int program, int location, float v0) {
        GL41C.glProgramUniform1f(program, location, v0);
    }

    // Calls the GL function glProgramUniform4f.
    public void glProgramUniform4f(int program, int location, float v0, float v1, float v2, float v3) {
        GL41C.glProgramUniform4f(program, location, v0, v1, v2, v3);
    }

    // Calls the GL function glUniform3f.
    public void glUniform3f(int location, float v0, float v1, float v2) {
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    // Calls the GL function glUniform3fv.
    public void glUniform3fv(int location, FloatBuffer value) {
        GL20C.glUniform3fv(location, value);
    }

    // Calls the GL function glUniform3fv.
    public void glUniform3fv(int location, float[] value) {
        GL20C.glUniform3fv(location, value);
    }

    // Calls the GL function glUniform4fv.
    public void glUniform4fv(int location, FloatBuffer value) {
        GL20C.glUniform4fv(location, value);
    }

    // Calls the GL function glUniform4fv.
    public void glUniform4fv(int location, float[] value) {
        GL20C.glUniform4fv(location, value);
    }

    // Calls the GL function glUniformMatrix3fv.
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    // Calls the GL function glUniformMatrix4fv.
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    // Calls the GL function glDrawElementsBaseVertex.
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GL32C.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    // Calls the GL function glMultiDrawElementsBaseVertex.
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    // Calls the GL function glMultiDrawElementsIndirect.
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // Calls the GL function glFenceSync.
    public long glFenceSync(int condition, int flags) {
        return GL32C.glFenceSync(condition, flags);
    }

    // Calls the GL function glClientWaitSync.
    public int glClientWaitSync(long sync, int flags, long timeout) {
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    // Calls the GL function glGetSynci.
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        return GL32C.glGetSynci(sync, pname, length);
    }

    // Calls the GL function glDeleteSync.
    public void glDeleteSync(long sync) {
        GL32C.glDeleteSync(sync);
    }

    // Calls the GL function glGenQueries.
    public int glGenQueries() {
        return GL15C.glGenQueries();
    }

    // Calls the GL function glDeleteQueries.
    public void glDeleteQueries(int query) {
        GL15C.glDeleteQueries(query);
    }

    // Calls the GL function glQueryCounter.
    public void glQueryCounter(int id, int target) {
        switch (timerQueryMode) {
            case CORE -> GL33C.glQueryCounter(id, target);
            case ARB -> ARBTimerQuery.glQueryCounter(id, target);
            // Without timer queries nothing is recorded
            case NONE -> { }
        }
    }

    // Calls the GL function glGetQueryObjectui64.
    public long glGetQueryObjectui64(int id, int pname) {
        return switch (timerQueryMode) {
            case CORE -> GL33C.glGetQueryObjectui64(id, pname);
            case ARB -> ARBTimerQuery.glGetQueryObjectui64(id, pname);
            case NONE -> 0L;
        };
    }

    // Calls the GL function glGetQueryObjecti.
    public int glGetQueryObjecti(int id, int pname) {
        return GL15C.glGetQueryObjecti(id, pname);
    }

    // Calls the GL function glGenTextures.
    public int glGenTextures() {
        return GL11C.glGenTextures();
    }

    // Calls the GL function glGenTextures.
    public void glGenTextures(int[] textures) {
        GL11C.glGenTextures(textures);
    }

    // Calls the GL function glDeleteTextures.
    public void glDeleteTextures(int texture) {
        GL11C.glDeleteTextures(texture);
    }

    // Calls the GL function glDeleteTextures.
    public void glDeleteTextures(int[] textures) {
        GL11C.glDeleteTextures(textures);
    }

    // Calls the GL function glBindTexture.
    public void glBindTexture(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    // Calls the GL function glActiveTexture.
    public void glActiveTexture(int texture) {
        GL13C.glActiveTexture(texture);
    }

    // Calls the GL function glMultiTexCoord2f.
    public void glMultiTexCoord2f(int target, float s, float t) {
        GL13.glMultiTexCoord2f(target, s, t);
    }

    // Calls the GL function glGetTexLevelParameteri.
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    // Calls the GL function glCopyTexSubImage2D.
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset,
                                    int x, int y, int width, int height) {
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    // Calls the GL function glReadPixels.
    public void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    // Calls the GL function glReadPixels.
    public void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelsOffset) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixelsOffset);
    }

    // Calls the GL function glGenerateMipmap.
    public void glGenerateMipmap(int target) {
        GL30C.glGenerateMipmap(target);
    }

    // Calls the GL function glGenSamplers.
    public int glGenSamplers() {
        return GL33C.glGenSamplers();
    }

    // Calls the GL function glDeleteSamplers.
    public void glDeleteSamplers(int sampler) {
        GL33C.glDeleteSamplers(sampler);
    }

    // Calls the GL function glBindSampler.
    public void glBindSampler(int unit, int sampler) {
        GL33C.glBindSampler(unit, sampler);
    }

    // Calls the GL function glSamplerParameteri.
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GL33C.glSamplerParameteri(sampler, pname, param);
    }

    // Calls the GL function glDepthRange.
    public void glDepthRange(double zNear, double zFar) {
        GL11C.glDepthRange(zNear, zFar);
    }

    // Calls the GL function glPixelStorei.
    public void glPixelStorei(int pname, int param) {
        GL11C.glPixelStorei(pname, param);
    }

    // Calls the GL function glTexImage2D.
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    // Calls the GL function glTexImage3D.
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        GL12C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    // Calls the GL function glBindImageTexture.
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    // Calls the GL function glMemoryBarrier.
    public void glMemoryBarrier(int barriers) {
        GL42C.glMemoryBarrier(barriers);
    }

    // Calls the GL function glDispatchCompute.
    public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    // Calls the GL function glDispatchComputeIndirect.
    public void glDispatchComputeIndirect(long indirect) {
        GL43C.glDispatchComputeIndirect(indirect);
    }

    // Calls the GL function glClearTexImage.
    public void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }

    // Calls the GL function glTexParameteri.
    public void glTexParameteri(int target, int pname, int param) {
        GL11C.glTexParameteri(target, pname, param);
    }

    // Calls the GL function glTexParameteriv.
    public void glTexParameteriv(int target, int pname, int[] params) {
        GL11C.glTexParameteriv(target, pname, params);
    }

    // Calls the GL function glTexParameterf.
    public void glTexParameterf(int target, int pname, float param) {
        GL11C.glTexParameterf(target, pname, param);
    }

    // Calls the GL function glGenFramebuffers.
    public int glGenFramebuffers() {
        return GL30C.glGenFramebuffers();
    }

    // Calls the GL function glDeleteFramebuffers.
    public void glDeleteFramebuffers(int framebuffer) {
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    // Calls the GL function glBindFramebuffer.
    public void glBindFramebuffer(int target, int framebuffer) {
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    // Calls the GL function glCheckFramebufferStatus.
    public int glCheckFramebufferStatus(int target) {
        return GL30C.glCheckFramebufferStatus(target);
    }

    // Calls the GL function glFramebufferTexture2D.
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    // Calls the GL function glFramebufferTextureLayer.
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GL30C.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    // Calls the GL function glDrawBuffers.
    public void glDrawBuffers(int buf) {
        GL20C.glDrawBuffers(buf);
    }

    // Calls the GL function glDrawBuffers.
    public void glDrawBuffers(IntBuffer bufs) {
        GL20C.glDrawBuffers(bufs);
    }

    // Calls the GL function glReadBuffer.
    public void glReadBuffer(int mode) {
        GL11C.glReadBuffer(mode);
    }

    // Calls the GL function glBlitFramebuffer.
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // Calls the GL function glGenRenderbuffers.
    public int glGenRenderbuffers() {
        return GL30C.glGenRenderbuffers();
    }

    // Calls the GL function glDeleteRenderbuffers.
    public void glDeleteRenderbuffers(int renderbuffer) {
        GL30C.glDeleteRenderbuffers(renderbuffer);
    }

    // Calls the GL function glBindRenderbuffer.
    public void glBindRenderbuffer(int target, int renderbuffer) {
        GL30C.glBindRenderbuffer(target, renderbuffer);
    }

    // Calls the GL function glRenderbufferStorage.
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GL30C.glRenderbufferStorage(target, internalformat, width, height);
    }

    // Calls the GL function glFramebufferRenderbuffer.
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    // Calls the GL function glEnable.
    public void glEnable(int cap) {
        GL11C.glEnable(cap);
    }

    // Calls the GL function glDisable.
    public void glDisable(int cap) {
        GL11C.glDisable(cap);
    }

    // Calls the GL function glEnablei.
    public void glEnablei(int target, int index) {
        GL30C.glEnablei(target, index);
    }

    // Calls the GL function glDisablei.
    public void glDisablei(int target, int index) {
        GL30C.glDisablei(target, index);
    }

    // Calls the GL function glBlendFunc.
    public void glBlendFunc(int sfactor, int dfactor) {
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    // Calls the GL function glBlendFuncSeparate.
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // Calls the GL function glBlendFuncSeparatei.
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        switch (blendIMode) {
            case CORE -> GL40C.glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
            case ARB -> ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
            case NONE -> throw new UnsupportedOperationException("Per-buffer blending is not supported");
        }
    }

    // Calls the GL function glDepthFunc.
    public void glDepthFunc(int func) {
        GL11C.glDepthFunc(func);
    }

    // Calls the GL function glDepthMask.
    public void glDepthMask(boolean flag) {
        GL11C.glDepthMask(flag);
    }

    // Calls the GL function glColorMask.
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11C.glColorMask(red, green, blue, alpha);
    }

    // Calls the GL function glViewport.
    public void glViewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    // Calls the GL function glClear.
    public void glClear(int mask) {
        GL11C.glClear(mask);
    }

    // Calls the GL function glClearColor.
    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11C.glClearColor(red, green, blue, alpha);
    }

    // Calls the GL function glClearDepth.
    public void glClearDepth(double depth) {
        GL11C.glClearDepth(depth);
    }

    // Calls the GL function glCullFace.
    public void glCullFace(int mode) {
        GL11C.glCullFace(mode);
    }

    // Calls the GL function glDrawArrays.
    public void glDrawArrays(int mode, int first, int count) {
        GL11C.glDrawArrays(mode, first, count);
    }

    // Calls the GL function glPatchParameteri.
    public void glPatchParameteri(int pname, int value) {
        GL40C.glPatchParameteri(pname, value);
    }

    // Calls the GL function glGetError.
    public int glGetError() {
        return GL11C.glGetError();
    }

    // Calls the GL function glMatrixMode.
    public void glMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    // Calls the GL function glLoadMatrixf.
    public void glLoadMatrixf(FloatBuffer m) {
        GL11.glLoadMatrixf(m);
    }

    // Calls the GL function glGetInteger.
    public int glGetInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    // Calls the GL function glGetFloat.
    public float glGetFloat(int pname) {
        return GL11C.glGetFloat(pname);
    }

    // Calls the GL function glGetIntegerv.
    public void glGetIntegerv(int pname, int[] params) {
        GL11C.glGetIntegerv(pname, params);
    }

    // Calls the GL function glGetBoolean.
    public boolean glGetBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    // Calls the GL function glGetString.
    public String glGetString(int pname) {
        return GL11C.glGetString(pname);
    }

    // Calls the GL function glGetAttribLocation.
    public int glGetAttribLocation(int program, CharSequence name) {
        return GL20C.glGetAttribLocation(program, name);
    }

    // Calls the GL function glObjectLabel.
    public void glObjectLabel(int identifier, int name, CharSequence label) {
        GL43C.glObjectLabel(identifier, name, label);
    }

    // Calls the GL function glPushDebugGroup.
    public void glPushDebugGroup(int source, int id, CharSequence message) {
        GL43C.glPushDebugGroup(source, id, message);
    }

    // Calls the GL function glPopDebugGroup.
    public void glPopDebugGroup() {
        GL43C.glPopDebugGroup();
    }

    // Calls the GL function glCreateBuffers.
    public int glCreateBuffers() {
        return ARBDirectStateAccess.glCreateBuffers();
    }

    // Calls the GL function glNamedBufferStorage.
    public void glNamedBufferStorage(int buffer, long size, int flags) {
        ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }

    // Calls the GL function nglMapNamedBufferRange.
    public long nglMapNamedBufferRange(int buffer, long offset, long length, int access) {
        return ARBDirectStateAccess.nglMapNamedBufferRange(buffer, offset, length, access);
    }

    // Calls the GL function glUnmapNamedBuffer.
    public void glUnmapNamedBuffer(int buffer) {
        ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }

    // Calls the GL function glFlushMappedNamedBufferRange.
    public void glFlushMappedNamedBufferRange(int buffer, long offset, long length) {
        ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }

    // Calls the GL function glCopyNamedBufferSubData.
    public void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    // Calls the GL function glClearNamedBufferSubDataZero.
    public void glClearNamedBufferSubDataZero(int buffer, int internalFormat, long offset, long size, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferSubData(buffer, internalFormat, offset, size, format, type, MemoryUtil.NULL);
    }

    // Calls the GL function glClearNamedBufferDataZero.
    public void glClearNamedBufferDataZero(int buffer, int internalFormat, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferData(buffer, internalFormat, format, type, MemoryUtil.NULL);
    }

    // Calls the GL function glGetNamedBufferGpuAddressNV.
    public long glGetNamedBufferGpuAddressNV(int buffer) {
        long[] address = new long[1];
        NVShaderBufferLoad.glGetNamedBufferParameterui64vNV(buffer, NVShaderBufferLoad.GL_BUFFER_GPU_ADDRESS_NV, address);
        return address[0];
    }

    // Calls the GL function glMakeNamedBufferResidentNV.
    public void glMakeNamedBufferResidentNV(int buffer, int access) {
        NVShaderBufferLoad.glMakeNamedBufferResidentNV(buffer, access);
    }

    // Calls the GL function glMakeNamedBufferNonResidentNV.
    public void glMakeNamedBufferNonResidentNV(int buffer) {
        NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(buffer);
    }

    // Calls the GL function glBufferAddressRangeNV.
    public void glBufferAddressRangeNV(int pname, int index, long address, long length) {
        NVVertexBufferUnifiedMemory.glBufferAddressRangeNV(pname, index, address, length);
    }

    // Calls the GL function glEnableClientState.
    public void glEnableClientState(int cap) {
        GL11.glEnableClientState(cap);
    }

    // Calls the GL function glDisableClientState.
    public void glDisableClientState(int cap) {
        GL11.glDisableClientState(cap);
    }

    // Calls the GL function glDrawMeshTasksNV.
    public void glDrawMeshTasksNV(int first, int count) {
        NVMeshShader.glDrawMeshTasksNV(first, count);
    }

    // Calls the GL function glMultiDrawMeshTasksIndirectNV.
    public void glMultiDrawMeshTasksIndirectNV(long indirect, int drawCount, int stride) {
        NVMeshShader.glMultiDrawMeshTasksIndirectNV(indirect, drawCount, stride);
    }

    // Calls the GL function glBufferPageCommitmentARB.
    public void glBufferPageCommitmentARB(int target, long offset, long size, boolean commit) {
        ARBSparseBuffer.glBufferPageCommitmentARB(target, offset, size, commit);
    }

    // Calls the GL function glFinish.
    public void glFinish() {
        GL11.glFinish();
    }
}
