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

// Every GL entry point the engine calls, on LWJGL3, with the version-dependent ones resolved once at creation; a single instance, which unit tests swap for a mock
public record LWJGLService(
        VAOMode vaoMode,
        TimerQueryMode timerQueryMode,
        VertexAttribIMode vertexAttribIMode,
        BlendIMode blendIMode) {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/LWJGL");

    // ===================== CAPABILITIES =====================

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

    // Maps the extension enum onto LWJGL3's capability flags
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

    // GL 4.0 core or ARB_draw_buffers_blend, as resolved in create()
    public boolean supportsBufferBlending() {
        return blendIMode != BlendIMode.NONE;
    }

    // ===================== BUFFER OPERATIONS =====================

    public int glGenBuffers() {
        return GL15C.glGenBuffers();
    }

    public void glDeleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    public void glBindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    public void glBufferData(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    public void glBufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    public void glBufferData(int target, long size, long data, int usage) {
        GL15C.nglBufferData(target, size, data, usage);
    }

    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    public void glBufferStorage(int target, long size, int flags) {
        GL44C.glBufferStorage(target, size, flags);
    }

    public void glClearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data) {
        GL43C.glClearBufferData(target, internalFormat, format, type, data);
    }

    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return GL30C.glMapBufferRange(target, offset, length, flags);
    }

    public long nglMapBuffer(int target, int access) {
        return GL15C.nglMapBuffer(target, access);
    }

    public ByteBuffer glMapBuffer(int target, int access) {
        return GL15C.glMapBuffer(target, access, null);
    }

    public void glUnmapBuffer(int target) {
        GL15C.glUnmapBuffer(target);
    }

    public void glFlushMappedBufferRange(int target, long offset, long length) {
        GL30C.glFlushMappedBufferRange(target, offset, length);
    }

    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    public void glBindBufferBase(int target, int index, int buffer) {
        GL30C.glBindBufferBase(target, index, buffer);
    }

    // How vertex array objects are reached; resolved once in create()
    private sealed interface VAOMode {
        int gen();
        void delete(int array);
        void bind(int array);
    }

    // The entry points LWJGL3 binds: GL30 core, ARB_vertex_array_object, or none at all
    private enum BoundVAO implements VAOMode {
        CORE {
            @Override public int gen() { return GL30C.glGenVertexArrays(); }
            @Override public void delete(int array) { GL30C.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { GL30C.glBindVertexArray(array); }
        },
        ARB {
            @Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
            @Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
        },
        NONE {
            @Override public int gen() { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void delete(int array) { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void bind(int array) { throw new UnsupportedOperationException("VAO not supported"); }
        }
    }

    // APPLE_vertex_array_object (macOS legacy contexts) has neither an LWJGL3 binding nor a capability flag, so its entry points are looked up and called directly
    private static final class AppleVAO implements VAOMode {
        private final long gen;
        private final long delete;
        private final long bind;

        private AppleVAO(FunctionProvider functions, long bind) {
            this.gen = functions.getFunctionAddress("glGenVertexArraysAPPLE");
            this.delete = functions.getFunctionAddress("glDeleteVertexArraysAPPLE");
            this.bind = bind;
        }

            public int gen() {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                IntBuffer buf = stack.callocInt(1);
                JNI.callPV(1, MemoryUtil.memAddress(buf), gen);
                return buf.get(0);
            }
        }

            public void delete(int array) {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                JNI.callPV(1, MemoryUtil.memAddress(stack.ints(array)), delete);
            }
        }

            public void bind(int array) {
            JNI.callV(array, bind);
        }
    }

    private enum TimerQueryMode { CORE, ARB, NONE }
    private enum VertexAttribIMode { CORE, EXT, NONE }
    // GL40's glBlendFuncSeparatei and the ARB one are separate entry points, and calling one the driver did not load jumps through a null pointer
    private enum BlendIMode { CORE, ARB, NONE }

    // Resolves VAO, timer-query, vertex-attrib and per-buffer blend entry points once from the context capabilities
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
            vaoMode = bind != MemoryUtil.NULL ? new AppleVAO(functions, bind) : BoundVAO.NONE;
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

    // Through the resolved VAO mode: core, ARB, APPLE or unsupported
    public int glGenVertexArrays() {
        return vaoMode.gen();
    }

    // Through the resolved VAO mode
    public void glDeleteVertexArrays(int array) {
        vaoMode.delete(array);
    }

    // Through the resolved VAO mode
    public void glBindVertexArray(int array) {
        vaoMode.bind(array);
    }

    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    // Core or EXT_gpu_shader4 depending on what the driver has
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        switch (vertexAttribIMode) {
            case CORE -> GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
            case EXT -> EXTGPUShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
            case NONE -> throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
        }
    }

    public void glVertexAttribDivisor(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    public void glEnableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    public void glDisableVertexAttribArray(int index) {
        GL20C.glDisableVertexAttribArray(index);
    }

    public int glGetVertexAttribi(int index, int pname) {
        return GL20C.glGetVertexAttribi(index, pname);
    }

    // ===================== SHADER OPERATIONS =====================

    public int glCreateShader(int type) {
        return GL20C.glCreateShader(type);
    }

    public void glShaderSource(int shader, CharSequence source) {
        GL20C.glShaderSource(shader, source);
    }

    // AMD workaround: a null length array makes the driver read up to the terminator instead of misreading a length; sources outgrow the stack, so the string is heap-allocated
    public void glShaderSourceSafe(int shader, CharSequence source) {
        ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            GL20C.nglShaderSource(shader, 1, stack.pointers(sourceBuffer).address(), MemoryUtil.NULL);
        } finally {
            MemoryUtil.memFree(sourceBuffer);
        }
    }

    public void glCompileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    // Reads the info log into a String; LWJGL3's overload handles the length itself
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return GL20C.glGetShaderInfoLog(shader);
    }

    public int glGetShaderi(int shader, int pname) {
        return GL20C.glGetShaderi(shader, pname);
    }

    public void glDeleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    public int glCreateProgram() {
        return GL20C.glCreateProgram();
    }

    public void glAttachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    public void glDetachShader(int program, int shader) {
        GL20C.glDetachShader(program, shader);
    }

    public void glLinkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    public String glGetProgramInfoLog(int program, int maxLength) {
        return GL20C.glGetProgramInfoLog(program);
    }

    public int glGetProgrami(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    // LWJGL3 splits size and type into separate buffers, so they are packed back into the caller's one
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

    public void glUseProgram(int program) {
        GL20C.glUseProgram(program);
    }

    public void glDeleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20C.glBindAttribLocation(program, index, name);
    }

    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        GL30C.glBindFragDataLocation(program, colorNumber, name);
    }

    // ===================== UNIFORM OPERATIONS =====================

    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        GL31C.glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    public void glUniform1f(int location, float v0) {
        GL20C.glUniform1f(location, v0);
    }

    public void glUniform1i(int location, int v0) {
        GL20C.glUniform1i(location, v0);
    }

    public void glUniform1fv(int location, FloatBuffer value) {
        GL20C.glUniform1fv(location, value);
    }

    public void glUniform2i(int location, int v0, int v1) {
        GL20C.glUniform2i(location, v0, v1);
    }

    public void glUniform3i(int location, int v0, int v1, int v2) {
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    public void glUniform2f(int location, float v0, float v1) {
        GL20C.glUniform2f(location, v0, v1);
    }

    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20C.glUniform4f(location, v0, v1, v2, v3);
    }

    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    public void glUniform3f(int location, float v0, float v1, float v2) {
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    public void glUniform3fv(int location, FloatBuffer value) {
        GL20C.glUniform3fv(location, value);
    }

    public void glUniform3fv(int location, float[] value) {
        GL20C.glUniform3fv(location, value);
    }

    public void glUniform4fv(int location, FloatBuffer value) {
        GL20C.glUniform4fv(location, value);
    }

    public void glUniform4fv(int location, float[] value) {
        GL20C.glUniform4fv(location, value);
    }

    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    // ===================== DRAW OPERATIONS =====================

    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GL32C.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // ===================== SYNC OPERATIONS =====================

    public long glFenceSync(int condition, int flags) {
        return GL32C.glFenceSync(condition, flags);
    }

    public int glClientWaitSync(long sync, int flags, long timeout) {
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    public int glGetSynci(long sync, int pname, IntBuffer length) {
        return GL32C.glGetSynci(sync, pname, length);
    }

    public void glDeleteSync(long sync) {
        GL32C.glDeleteSync(sync);
    }

    // ===================== QUERY OPERATIONS =====================

    public int glGenQueries() {
        return GL15C.glGenQueries();
    }

    public void glDeleteQueries(int query) {
        GL15C.glDeleteQueries(query);
    }

    // Through the resolved timer-query mode: GL33, ARB or no-op
    public void glQueryCounter(int id, int target) {
        switch (timerQueryMode) {
            case CORE -> GL33C.glQueryCounter(id, target);
            case ARB -> ARBTimerQuery.glQueryCounter(id, target);
            case NONE -> { /* no-op */ }
        }
    }

    // Through the resolved timer-query mode
    public long glGetQueryObjectui64(int id, int pname) {
        return switch (timerQueryMode) {
            case CORE -> GL33C.glGetQueryObjectui64(id, pname);
            case ARB -> ARBTimerQuery.glGetQueryObjectui64(id, pname);
            case NONE -> 0L;
        };
    }

    public int glGetQueryObjecti(int id, int pname) {
        return GL15C.glGetQueryObjecti(id, pname);
    }

    // ===================== TEXTURE OPERATIONS =====================

    public int glGenTextures() {
        return GL11C.glGenTextures();
    }

    public void glGenTextures(int[] textures) {
        GL11C.glGenTextures(textures);
    }

    public void glDeleteTextures(int texture) {
        GL11C.glDeleteTextures(texture);
    }

    public void glDeleteTextures(int[] textures) {
        GL11C.glDeleteTextures(textures);
    }

    public void glBindTexture(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    public void glActiveTexture(int texture) {
        GL13C.glActiveTexture(texture);
    }

    public void glMultiTexCoord2f(int target, float s, float t) {
        GL13.glMultiTexCoord2f(target, s, t);
    }

    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset,
                                    int x, int y, int width, int height) {
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    public void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelsOffset) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixelsOffset);
    }

    public void glGenerateMipmap(int target) {
        GL30C.glGenerateMipmap(target);
    }

    public int glGenSamplers() {
        return GL33C.glGenSamplers();
    }

    public void glDeleteSamplers(int sampler) {
        GL33C.glDeleteSamplers(sampler);
    }

    public void glBindSampler(int unit, int sampler) {
        GL33C.glBindSampler(unit, sampler);
    }

    public void glSamplerParameteri(int sampler, int pname, int param) {
        GL33C.glSamplerParameteri(sampler, pname, param);
    }

    public void glDepthRange(double zNear, double zFar) {
        GL11C.glDepthRange(zNear, zFar);
    }

    public void glPixelStorei(int pname, int param) {
        GL11C.glPixelStorei(pname, param);
    }

    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        GL12C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    public void glMemoryBarrier(int barriers) {
        GL42C.glMemoryBarrier(barriers);
    }

    public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    public void glDispatchComputeIndirect(long indirect) {
        GL43C.glDispatchComputeIndirect(indirect);
    }

    public void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }

    public void glTexParameteri(int target, int pname, int param) {
        GL11C.glTexParameteri(target, pname, param);
    }

    public void glTexParameteriv(int target, int pname, int[] params) {
        GL11C.glTexParameteriv(target, pname, params);
    }

    public void glTexParameterf(int target, int pname, float param) {
        GL11C.glTexParameterf(target, pname, param);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================

    public int glGenFramebuffers() {
        return GL30C.glGenFramebuffers();
    }

    public void glDeleteFramebuffers(int framebuffer) {
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    public void glBindFramebuffer(int target, int framebuffer) {
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    public int glCheckFramebufferStatus(int target) {
        return GL30C.glCheckFramebufferStatus(target);
    }

    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GL30C.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    public void glDrawBuffers(int buf) {
        GL20C.glDrawBuffers(buf);
    }

    public void glDrawBuffers(IntBuffer bufs) {
        GL20C.glDrawBuffers(bufs);
    }

    public void glReadBuffer(int mode) {
        GL11C.glReadBuffer(mode);
    }

    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public int glGenRenderbuffers() {
        return GL30C.glGenRenderbuffers();
    }

    public void glDeleteRenderbuffers(int renderbuffer) {
        GL30C.glDeleteRenderbuffers(renderbuffer);
    }

    public void glBindRenderbuffer(int target, int renderbuffer) {
        GL30C.glBindRenderbuffer(target, renderbuffer);
    }

    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GL30C.glRenderbufferStorage(target, internalformat, width, height);
    }

    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    // ===================== STATE OPERATIONS =====================

    public void glEnable(int cap) {
        GL11C.glEnable(cap);
    }

    public void glDisable(int cap) {
        GL11C.glDisable(cap);
    }

    public void glEnablei(int target, int index) {
        GL30C.glEnablei(target, index);
    }

    public void glDisablei(int target, int index) {
        GL30C.glDisablei(target, index);
    }

    public void glBlendFunc(int sfactor, int dfactor) {
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // GL40C, else ARBDrawBuffersBlend; unsupported throws instead of calling a null entry point
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        switch (blendIMode) {
            case CORE -> GL40C.glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
            case ARB -> ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
            case NONE -> throw new UnsupportedOperationException("Per-buffer blending is not supported");
        }
    }

    public void glDepthFunc(int func) {
        GL11C.glDepthFunc(func);
    }

    public void glDepthMask(boolean flag) {
        GL11C.glDepthMask(flag);
    }

    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11C.glColorMask(red, green, blue, alpha);
    }

    public void glViewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    public void glClear(int mask) {
        GL11C.glClear(mask);
    }

    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11C.glClearColor(red, green, blue, alpha);
    }

    public void glClearDepth(double depth) {
        GL11C.glClearDepth(depth);
    }

    public void glCullFace(int mode) {
        GL11C.glCullFace(mode);
    }

    public void glDrawArrays(int mode, int first, int count) {
        GL11C.glDrawArrays(mode, first, count);
    }

    public void glPatchParameteri(int pname, int value) {
        GL40C.glPatchParameteri(pname, value);
    }

    public int glGetError() {
        return GL11C.glGetError();
    }

    // ===================== COMPATIBILITY PROFILE (GL1.x) =====================

    public void glMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    public void glLoadMatrixf(FloatBuffer m) {
        GL11.glLoadMatrixf(m);
    }

    // ===================== MISC GL =====================

    public int glGetInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    public float glGetFloat(int pname) {
        return GL11C.glGetFloat(pname);
    }

    public void glGetIntegerv(int pname, int[] params) {
        GL11C.glGetIntegerv(pname, params);
    }

    public boolean glGetBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    public String glGetString(int pname) {
        return GL11C.glGetString(pname);
    }

    public int glGetAttribLocation(int program, CharSequence name) {
        return GL20C.glGetAttribLocation(program, name);
    }

    // ===================== DEBUG LABELS (KHR_debug / GL 4.3) =====================

    public void glObjectLabel(int identifier, int name, CharSequence label) {
        GL43C.glObjectLabel(identifier, name, label);
    }

    public void glPushDebugGroup(int source, int id, CharSequence message) {
        GL43C.glPushDebugGroup(source, id, message);
    }

    public void glPopDebugGroup() {
        GL43C.glPopDebugGroup();
    }

    // ===================== DIRECT STATE ACCESS BUFFERS =====================

    public int glCreateBuffers() {
        return ARBDirectStateAccess.glCreateBuffers();
    }

    public void glNamedBufferStorage(int buffer, long size, int flags) {
        ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }

    public long nglMapNamedBufferRange(int buffer, long offset, long length, int access) {
        return ARBDirectStateAccess.nglMapNamedBufferRange(buffer, offset, length, access);
    }

    public void glUnmapNamedBuffer(int buffer) {
        ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }

    public void glFlushMappedNamedBufferRange(int buffer, long offset, long length) {
        ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }

    public void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    public void glClearNamedBufferSubDataZero(int buffer, int internalFormat, long offset, long size, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferSubData(buffer, internalFormat, offset, size, format, type, MemoryUtil.NULL);
    }

    public void glClearNamedBufferDataZero(int buffer, int internalFormat, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferData(buffer, internalFormat, format, type, MemoryUtil.NULL);
    }

    // ===================== BINDLESS BUFFERS =====================

    public long glGetNamedBufferGpuAddressNV(int buffer) {
        // The extension only offers the array form of the getter, so a one-element scratch array it is
        long[] address = new long[1];
        NVShaderBufferLoad.glGetNamedBufferParameterui64vNV(buffer, NVShaderBufferLoad.GL_BUFFER_GPU_ADDRESS_NV, address);
        return address[0];
    }

    public void glMakeNamedBufferResidentNV(int buffer, int access) {
        NVShaderBufferLoad.glMakeNamedBufferResidentNV(buffer, access);
    }

    public void glMakeNamedBufferNonResidentNV(int buffer) {
        NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(buffer);
    }

    public void glBufferAddressRangeNV(int pname, int index, long address, long length) {
        NVVertexBufferUnifiedMemory.glBufferAddressRangeNV(pname, index, address, length);
    }

    public void glEnableClientState(int cap) {
        GL11.glEnableClientState(cap);
    }

    public void glDisableClientState(int cap) {
        GL11.glDisableClientState(cap);
    }

    // ===================== MESH SHADERS =====================

    public void glDrawMeshTasksNV(int first, int count) {
        NVMeshShader.glDrawMeshTasksNV(first, count);
    }

    public void glMultiDrawMeshTasksIndirectNV(long indirect, int drawCount, int stride) {
        NVMeshShader.glMultiDrawMeshTasksIndirectNV(indirect, drawCount, stride);
    }

    // ===================== SPARSE BUFFERS =====================

    public void glBufferPageCommitmentARB(int target, long offset, long size, boolean commit) {
        ARBSparseBuffer.glBufferPageCommitmentARB(target, offset, size, commit);
    }

    public void glFinish() {
        GL11.glFinish();
    }
}
