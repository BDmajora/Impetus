package com.bdmajora.impetus.lwjgl.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.GLNv;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// LWJGL3 backend of LWJGLService (lwjgl3ify / RetroFuturaBootstrap on 1.12.2); capability decisions resolved once at creation
public record LWJGL3Service(
        VAOMode vaoMode,
        TimerQueryMode timerQueryMode,
        VertexAttribIMode vertexAttribIMode) implements LWJGLService {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/LWJGL3Service");

    // ===================== CAPABILITIES =====================


    @Override
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

    // Maps the abstraction's extension enum onto LWJGL3's capability flags
    @Override
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
            case ARB_copy_buffer -> caps.GL_ARB_copy_buffer;
            case ARB_texture_storage -> caps.GL_ARB_texture_storage;
            case ARB_base_instance -> caps.GL_ARB_base_instance;
            case ARB_compatibility -> caps.GL_ARB_compatibility;
            case NVX_gpu_memory_info -> caps.GL_NVX_gpu_memory_info;
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

    // GL 4.0 core or ARB_draw_buffers_blend
    @Override
    public boolean supportsBufferBlending() {
        GLCapabilities caps = GL.getCapabilities();
        return caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
    }

    // Native pointer width, for buffer stride arithmetic
    @Override
    public int getPointerSize() {
        return Pointer.POINTER_SIZE;
    }

    // ===================== BUFFER OPERATIONS =====================

    @Override
    public int glGenBuffers() {
        return GL15C.glGenBuffers();
    }

    // GL15C
    @Override
    public void glDeleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    // GL15C
    @Override
    public void glBindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    // GL15C
    @Override
    public void glBufferData(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    // GL15C
    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

    // GL15C
    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        GL15C.nglBufferData(target, size, data, usage);
    }

    // GL15C
    @Override
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        GL15C.glBufferSubData(target, offset, data);
    }

    // GL44C
    @Override
    public void glBufferStorage(int target, long size, int flags) {
        GL44C.glBufferStorage(target, size, flags);
    }

    // GL43C
    @Override
    public void glClearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data) {
        GL43C.glClearBufferData(target, internalFormat, format, type, data);
    }

    // GL30C
    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return GL30C.glMapBufferRange(target, offset, length, flags);
    }

    // GL15C
    @Override
    public long nglMapBuffer(int target, int access) {
        return GL15C.nglMapBuffer(target, access);
    }

    // GL15C
    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        return GL15C.glMapBuffer(target, access, null);
    }

    // GL15C
    @Override
    public void glUnmapBuffer(int target) {
        GL15C.glUnmapBuffer(target);
    }

    // GL30C
    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        GL30C.glFlushMappedBufferRange(target, offset, length);
    }

    // GL31C
    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    // GL30C
    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        GL30C.glBindBufferBase(target, index, buffer);
    }

    private enum VAOMode {
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
        APPLE {
            @Override public int gen() {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    java.nio.IntBuffer buf = stack.callocInt(1);
                    org.lwjgl.system.JNI.callPV(1, MemoryUtil.memAddress(buf), glGenVertexArraysAPPLE);
                    return buf.get(0);
                }
            }
            @Override public void delete(int array) {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    java.nio.IntBuffer buf = stack.ints(array);
                    org.lwjgl.system.JNI.callPV(1, MemoryUtil.memAddress(buf), glDeleteVertexArraysAPPLE);
                }
            }
            @Override public void bind(int array) { org.lwjgl.system.JNI.callV(array, glBindVertexArrayAPPLE); }
        },
        NONE {
            @Override public int gen() { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void delete(int array) { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void bind(int array) { throw new UnsupportedOperationException("VAO not supported"); }
        };

        public abstract int gen();
        public abstract void delete(int array);
        public abstract void bind(int array);
    }
    private enum TimerQueryMode { CORE, ARB, NONE }
    private enum VertexAttribIMode { CORE, EXT, NONE }


    // Cached function addresses for APPLE VAO extensions
    private static final long glGenVertexArraysAPPLE = GL.getFunctionProvider().getFunctionAddress("glGenVertexArraysAPPLE");
    private static final long glDeleteVertexArraysAPPLE = GL.getFunctionProvider().getFunctionAddress("glDeleteVertexArraysAPPLE");
    private static final long glBindVertexArrayAPPLE = GL.getFunctionProvider().getFunctionAddress("glBindVertexArrayAPPLE");

    // Resolves VAO, timer-query and vertex-attrib entry points once from the context capabilities
    public static LWJGL3Service create() {
        GLCapabilities caps = GL.getCapabilities();

        VAOMode vaoMode;

        if (caps.OpenGL30) {
            vaoMode = VAOMode.CORE;
        } else if (caps.GL_ARB_vertex_array_object) {
            vaoMode = VAOMode.ARB;
        } else if (glBindVertexArrayAPPLE != 0) {
            vaoMode = VAOMode.APPLE;
        } else {
            vaoMode = VAOMode.NONE;
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

        return new LWJGL3Service(vaoMode, timerQueryMode, vertexAttribIMode);
    }

    // Through the resolved VAO mode: core, APPLE or unsupported
    @Override
    public int glGenVertexArrays() {
        return vaoMode.gen();
    }

    // Through the resolved VAO mode
    @Override
    public void glDeleteVertexArrays(int array) {
        vaoMode.delete(array);
    }

    // Through the resolved VAO mode
    @Override
    public void glBindVertexArray(int array) {
        vaoMode.bind(array);
    }

    // GL20C
    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    // Core or EXT_gpu_shader4 depending on what the driver has
    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        switch (vertexAttribIMode) {
            case CORE -> GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
            case EXT -> EXTGPUShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
            case NONE -> throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
        }
    }

    // GL20C
    @Override
    public void glEnableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    // GL20C
    @Override
    public void glDisableVertexAttribArray(int index) {
        GL20C.glDisableVertexAttribArray(index);
    }

    // GL20C
    @Override
    public int glGetVertexAttribi(int index, int pname) {
        return GL20C.glGetVertexAttribi(index, pname);
    }

    // ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return GL20C.glCreateShader(type);
    }

    // GL20C
    @Override
    public void glShaderSource(int shader, CharSequence source) {
        GL20C.glShaderSource(shader, source);
    }

    // AMD workaround: null length forces null-terminator reliance, avoiding a driver read past the string
    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        // AMD workaround: pass null for the string length so the driver relies on the null terminator instead of misreading the length
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
            org.lwjgl.PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);
            GL20C.nglShaderSource(shader, 1, pointers.address0(), 0);
            org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
        }
    }

    // GL20C
    @Override
    public void glCompileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    // Reads the info log into a String; LWJGL3's overload handles the length itself
    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        // LWJGL3 doesn't need maxLength, but we accept it for API compatibility
        return GL20C.glGetShaderInfoLog(shader);
    }

    // GL20C
    @Override
    public int glGetShaderi(int shader, int pname) {
        return GL20C.glGetShaderi(shader, pname);
    }

    // GL20C
    @Override
    public void glDeleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    // GL20C
    @Override
    public int glCreateProgram() {
        return GL20C.glCreateProgram();
    }

    // GL20C
    @Override
    public void glAttachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    // GL20C
    @Override
    public void glDetachShader(int program, int shader) {
        GL20C.glDetachShader(program, shader);
    }

    // GL20C
    @Override
    public void glLinkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    // GL20C
    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        return GL20C.glGetProgramInfoLog(program);
    }

    // GL20C
    @Override
    public int glGetProgrami(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    // LWJGL3 splits size and type into separate buffers, so they are packed back into the caller's one
    @Override
    public String glGetActiveUniform(int program, int index, int maxLength, java.nio.IntBuffer sizeType) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer size = stack.mallocInt(1);
            java.nio.IntBuffer type = stack.mallocInt(1);
            String name = GL20C.glGetActiveUniform(program, index, maxLength, size, type);
            sizeType.put(0, size.get(0));
            sizeType.put(1, type.get(0));
            return name;
        }
    }

    // GL20C
    @Override
    public void glUseProgram(int program) {
        GL20C.glUseProgram(program);
    }

    // GL20C
    @Override
    public void glDeleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    // GL20C
    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20C.glBindAttribLocation(program, index, name);
    }

    // GL30C
    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        GL30C.glBindFragDataLocation(program, colorNumber, name);
    }

    // ===================== UNIFORM OPERATIONS =====================

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    // GL31C
    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    // GL31C
    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        GL31C.glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    // GL20C
    @Override
    public void glUniform1f(int location, float v0) {
        GL20C.glUniform1f(location, v0);
    }

    // GL20C
    @Override
    public void glUniform1i(int location, int v0) {
        GL20C.glUniform1i(location, v0);
    }

    // GL20C
    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        GL20C.glUniform1fv(location, value);
    }

    // GL20C
    @Override
    public void glUniform2i(int location, int v0, int v1) {
        GL20C.glUniform2i(location, v0, v1);
    }

    // GL20C
    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    // GL20C
    @Override
    public void glUniform2f(int location, float v0, float v1) {
        GL20C.glUniform2f(location, v0, v1);
    }

    // GL20C
    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20C.glUniform4f(location, v0, v1, v2, v3);
    }

    // GL20C
    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    // GL20C
    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    // GL20C
    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        GL20C.glUniform3fv(location, value);
    }

    // GL20C
    @Override
    public void glUniform3fv(int location, float[] value) {
        GL20C.glUniform3fv(location, value);
    }

    // GL20C
    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        GL20C.glUniform4fv(location, value);
    }

    // GL20C
    @Override
    public void glUniform4fv(int location, float[] value) {
        GL20C.glUniform4fv(location, value);
    }

    // GL20C
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    // GL20C
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    // ===================== DRAW OPERATIONS =====================

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GL32C.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    // GL32C
    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    // GL43C
    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // ===================== SYNC OPERATIONS =====================

    @Override
    public long glFenceSync(int condition, int flags) {
        return GL32C.glFenceSync(condition, flags);
    }

    // GL32C
    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    // GL32C
    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        return GL32C.glGetSynci(sync, pname, length);
    }

    // GL32C
    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
        GL32C.glWaitSync(sync, flags, timeout);
    }

    // GL32C
    @Override
    public void glDeleteSync(long sync) {
        GL32C.glDeleteSync(sync);
    }

    // ===================== QUERY OPERATIONS =====================

    @Override
    public int glGenQueries() {
        return GL15C.glGenQueries();
    }

    // GL15C
    @Override
    public void glDeleteQueries(int query) {
        GL15C.glDeleteQueries(query);
    }

    // Through the resolved timer-query mode: GL33, ARB or no-op
    @Override
    public void glQueryCounter(int id, int target) {
        switch (timerQueryMode) {
            case CORE -> GL33C.glQueryCounter(id, target);
            case ARB -> ARBTimerQuery.glQueryCounter(id, target);
            case NONE -> { /* no-op */ }
        }
    }

    // Through the resolved timer-query mode
    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        return switch (timerQueryMode) {
            case CORE -> GL33C.glGetQueryObjectui64(id, pname);
            case ARB -> ARBTimerQuery.glGetQueryObjectui64(id, pname);
            case NONE -> 0L;
        };
    }

    @Override
    public int glGetQueryObjecti(int id, int pname) {
        return GL15C.glGetQueryObjecti(id, pname);
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return GL11C.glGenTextures();
    }

    // GL11C
    @Override
    public void glGenTextures(int[] textures) {
        GL11C.glGenTextures(textures);
    }

    // GL11C
    @Override
    public void glDeleteTextures(int texture) {
        GL11C.glDeleteTextures(texture);
    }

    // GL11C
    @Override
    public void glDeleteTextures(int[] textures) {
        GL11C.glDeleteTextures(textures);
    }

    // GL11C
    @Override
    public void glBindTexture(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    // GL13C
    @Override
    public void glActiveTexture(int texture) {
        GL13C.glActiveTexture(texture);
    }

    // GL13
    @Override
    public void glMultiTexCoord2f(int target, float s, float t) {
        GL13.glMultiTexCoord2f(target, s, t);
    }

    // GL11C
    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset,
                                    int x, int y, int width, int height) {
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    // GL11C
    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelsOffset) {
        GL11C.glReadPixels(x, y, width, height, format, type, pixelsOffset);
    }

    // GL30C
    @Override
    public void glGenerateMipmap(int target) {
        GL30C.glGenerateMipmap(target);
    }

    // GL33C
    @Override
    public int glGenSamplers() {
        return GL33C.glGenSamplers();
    }

    // GL33C
    @Override
    public void glDeleteSamplers(int sampler) {
        GL33C.glDeleteSamplers(sampler);
    }

    // GL33C
    @Override
    public void glBindSampler(int unit, int sampler) {
        GL33C.glBindSampler(unit, sampler);
    }

    // GL33C
    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GL33C.glSamplerParameteri(sampler, pname, param);
    }

    // GL11C
    @Override
    public void glDepthRange(double zNear, double zFar) {
        GL11C.glDepthRange(zNear, zFar);
    }

    // GL11C
    @Override
    public void glPixelStorei(int pname, int param) {
        GL11C.glPixelStorei(pname, param);
    }

    // GL11C
    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    // GL12C
    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        GL12C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    // GL42C
    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    // GL42C
    @Override
    public void glMemoryBarrier(int barriers) {
        GL42C.glMemoryBarrier(barriers);
    }

    // GL43C
    @Override
    public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    // GL43C
    @Override
    public void glDispatchComputeIndirect(long indirect) {
        GL43C.glDispatchComputeIndirect(indirect);
    }

    // ARBClearTexture
    @Override
    public void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }

    // GL11C
    @Override
    public void glTexParameteri(int target, int pname, int param) {
        GL11C.glTexParameteri(target, pname, param);
    }

    // GL11C
    @Override
    public void glTexParameteriv(int target, int pname, int[] params) {
        GL11C.glTexParameteriv(target, pname, params);
    }

    // GL11C
    @Override
    public void glTexParameterf(int target, int pname, float param) {
        GL11C.glTexParameterf(target, pname, param);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================

    @Override
    public int glGenFramebuffers() {
        return GL30C.glGenFramebuffers();
    }

    // GL30C
    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    // GL30C
    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    // GL30C
    @Override
    public int glCheckFramebufferStatus(int target) {
        return GL30C.glCheckFramebufferStatus(target);
    }

    // GL30C
    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    // GL30C
    @Override
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GL30C.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    // GL20C
    @Override
    public void glDrawBuffers(int buf) {
        GL20C.glDrawBuffers(buf);
    }

    // GL20C
    @Override
    public void glDrawBuffers(IntBuffer bufs) {
        GL20C.glDrawBuffers(bufs);
    }

    // GL11C
    @Override
    public void glReadBuffer(int mode) {
        GL11C.glReadBuffer(mode);
    }

    // GL30C
    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // GL30C
    @Override
    public int glGenRenderbuffers() {
        return GL30C.glGenRenderbuffers();
    }

    // GL30C
    @Override
    public void glDeleteRenderbuffers(int renderbuffer) {
        GL30C.glDeleteRenderbuffers(renderbuffer);
    }

    // GL30C
    @Override
    public void glBindRenderbuffer(int target, int renderbuffer) {
        GL30C.glBindRenderbuffer(target, renderbuffer);
    }

    // GL30C
    @Override
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GL30C.glRenderbufferStorage(target, internalformat, width, height);
    }

    // GL30C
    @Override
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    // ===================== STATE OPERATIONS =====================

    @Override
    public void glEnable(int cap) {
        GL11C.glEnable(cap);
    }

    // GL11C
    @Override
    public void glDisable(int cap) {
        GL11C.glDisable(cap);
    }

    // GL30C
    @Override
    public void glEnablei(int target, int index) {
        GL30C.glEnablei(target, index);
    }

    // GL30C
    @Override
    public void glDisablei(int target, int index) {
        GL30C.glDisablei(target, index);
    }

    // GL11C
    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    // GL14C
    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // ARBDrawBuffersBlend
    @Override
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // GL11C
    @Override
    public void glDepthFunc(int func) {
        GL11C.glDepthFunc(func);
    }

    // GL11C
    @Override
    public void glDepthMask(boolean flag) {
        GL11C.glDepthMask(flag);
    }

    // GL11C
    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11C.glColorMask(red, green, blue, alpha);
    }

    // GL11C
    @Override
    public void glViewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    // GL11C
    @Override
    public void glClear(int mask) {
        GL11C.glClear(mask);
    }

    // GL11C
    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11C.glClearColor(red, green, blue, alpha);
    }

    // GL11C
    @Override
    public void glClearDepth(double depth) {
        GL11C.glClearDepth(depth);
    }

    // GL11C
    @Override
    public void glCullFace(int mode) {
        GL11C.glCullFace(mode);
    }

    // GL11C
    @Override
    public void glDrawArrays(int mode, int first, int count) {
        GL11C.glDrawArrays(mode, first, count);
    }

    // GL11C
    @Override
    public int glGetError() {
        return GL11C.glGetError();
    }

    // ===================== COMPATIBILITY PROFILE (GL1.x) =====================

    @Override
    public void glMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    // GL11
    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        GL11.glLoadMatrixf(m);
    }

    // ===================== MISC GL =====================

    @Override
    public int glGetInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    // GL11C
    @Override
    public float glGetFloat(int pname) {
        return GL11C.glGetFloat(pname);
    }

    // GL11C
    @Override
    public void glGetIntegerv(int pname, int[] params) {
        GL11C.glGetIntegerv(pname, params);
    }

    // GL11C
    @Override
    public boolean glGetBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    // GL11C
    @Override
    public String glGetString(int pname) {
        return GL11C.glGetString(pname);
    }

    // GL20C
    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return GL20C.glGetAttribLocation(program, name);
    }

    // ===================== MEMORY STACK OPERATIONS =====================

    @Override
    public MemoryStack stackPush() {
        return new LWJGL3MemoryStack(org.lwjgl.system.MemoryStack.stackPush());
    }

    // ===================== NATIVE MEMORY OPERATIONS =====================

    @Override
    public long nmemAlloc(long size) {
        return MemoryUtil.nmemAlloc(size);
    }

    // MemoryUtil
    @Override
    public long nmemCalloc(long count, long size) {
        return MemoryUtil.nmemCalloc(count, size);
    }

    // MemoryUtil
    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        return MemoryUtil.nmemAlignedAlloc(alignment, size);
    }

    // MemoryUtil
    @Override
    public long nmemRealloc(long ptr, long size) {
        return MemoryUtil.nmemRealloc(ptr, size);
    }

    // MemoryUtil
    @Override
    public void nmemFree(long ptr) {
        MemoryUtil.nmemFree(ptr);
    }

    // MemoryUtil
    @Override
    public void nmemAlignedFree(long ptr) {
        MemoryUtil.nmemAlignedFree(ptr);
    }

    // MemoryUtil
    @Override
    public ByteBuffer memAlloc(int size) {
        return MemoryUtil.memAlloc(size);
    }

    // MemoryUtil
    @Override
    public ByteBuffer memCalloc(int size) {
        return MemoryUtil.memCalloc(size);
    }

    // MemoryUtil
    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return MemoryUtil.memRealloc(buffer, size);
    }

    // MemoryUtil
    @Override
    public void memFree(Buffer buffer) {
        MemoryUtil.memFree(buffer);
    }

    // MemoryUtil
    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    // MemoryUtil
    @Override
    public long memAddress(Buffer buffer) {
        return MemoryUtil.memAddress(buffer);
    }

    // LWJGL3 has no positioned memAddress for a generic Buffer, so the offset is computed from the element size
    @Override
    public long memAddress(Buffer buffer, int position) {
        // Generic Buffer has no positioned memAddress in LWJGL3, so add the position offset (scaled by element size) to the base address
        long base = MemoryUtil.memAddress(buffer);
        int elementSize;
        if (buffer instanceof java.nio.ByteBuffer) {
            elementSize = 1;
        } else if (buffer instanceof java.nio.ShortBuffer || buffer instanceof java.nio.CharBuffer) {
            elementSize = 2;
        } else if (buffer instanceof java.nio.IntBuffer || buffer instanceof java.nio.FloatBuffer) {
            elementSize = 4;
        } else if (buffer instanceof java.nio.LongBuffer || buffer instanceof java.nio.DoubleBuffer) {
            elementSize = 8;
        } else {
            throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass());
        }
        return base + ((long) position * elementSize);
    }

    // MemoryUtil
    @Override
    public void memSet(long address, int value, long bytes) {
        MemoryUtil.memSet(address, value, bytes);
    }

    // MemoryUtil
    @Override
    public void memCopy(long src, long dst, long bytes) {
        MemoryUtil.memCopy(src, dst, bytes);
    }

    // MemoryUtil
    @Override
    public void memPutByte(long address, byte value) {
        MemoryUtil.memPutByte(address, value);
    }

    // MemoryUtil
    @Override
    public void memPutShort(long address, short value) {
        MemoryUtil.memPutShort(address, value);
    }

    // MemoryUtil
    @Override
    public void memPutInt(long address, int value) {
        MemoryUtil.memPutInt(address, value);
    }

    // MemoryUtil
    @Override
    public void memPutFloat(long address, float value) {
        MemoryUtil.memPutFloat(address, value);
    }

    // MemoryUtil
    @Override
    public void memPutLong(long address, long value) {
        MemoryUtil.memPutLong(address, value);
    }

    // MemoryUtil
    @Override
    public void memPutAddress(long address, long value) {
        MemoryUtil.memPutAddress(address, value);
    }

    // MemoryUtil
    @Override
    public byte memGetByte(long address) {
        return MemoryUtil.memGetByte(address);
    }

    // MemoryUtil
    @Override
    public short memGetShort(long address) {
        return MemoryUtil.memGetShort(address);
    }

    // MemoryUtil
    @Override
    public int memGetInt(long address) {
        return MemoryUtil.memGetInt(address);
    }

    // MemoryUtil
    @Override
    public float memGetFloat(long address) {
        return MemoryUtil.memGetFloat(address);
    }

    // MemoryUtil
    @Override
    public long memGetLong(long address) {
        return MemoryUtil.memGetLong(address);
    }

    // MemoryUtil
    @Override
    public long memGetAddress(long address) {
        return MemoryUtil.memGetAddress(address);
    }

    // MemoryUtil
    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return MemoryUtil.memSlice(buffer, offset, capacity);
    }

    // ===================== DIRECT STATE ACCESS BUFFERS =====================

    @Override
    public int glCreateBuffers() {
        return ARBDirectStateAccess.glCreateBuffers();
    }

    // ARBDirectStateAccess
    @Override
    public void glNamedBufferStorage(int buffer, long size, int flags) {
        ARBDirectStateAccess.glNamedBufferStorage(buffer, size, flags);
    }

    // ARBDirectStateAccess
    @Override
    public long nglMapNamedBufferRange(int buffer, long offset, long length, int access) {
        return ARBDirectStateAccess.nglMapNamedBufferRange(buffer, offset, length, access);
    }

    // ARBDirectStateAccess
    @Override
    public void glUnmapNamedBuffer(int buffer) {
        ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
    }

    // ARBDirectStateAccess
    @Override
    public void glFlushMappedNamedBufferRange(int buffer, long offset, long length) {
        ARBDirectStateAccess.glFlushMappedNamedBufferRange(buffer, offset, length);
    }

    // ARBDirectStateAccess
    @Override
    public void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        ARBDirectStateAccess.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    // ARBDirectStateAccess
    @Override
    public void glClearNamedBufferSubDataZero(int buffer, int internalFormat, long offset, long size, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferSubData(buffer, internalFormat, offset, size, format, type, MemoryUtil.NULL);
    }

    // ARBDirectStateAccess
    @Override
    public void glClearNamedBufferDataZero(int buffer, int internalFormat, int format, int type) {
        ARBDirectStateAccess.nglClearNamedBufferData(buffer, internalFormat, format, type, MemoryUtil.NULL);
    }

    // ===================== BINDLESS BUFFERS =====================

    @Override
    public long glGetNamedBufferGpuAddressNV(int buffer) {
        // The extension only offers the array form of the getter, so a one-element scratch array it is
        long[] address = new long[1];
        NVShaderBufferLoad.glGetNamedBufferParameterui64vNV(buffer, GLNv.GL_BUFFER_GPU_ADDRESS_NV, address);
        return address[0];
    }

    // NVShaderBufferLoad
    @Override
    public void glMakeNamedBufferResidentNV(int buffer, int access) {
        NVShaderBufferLoad.glMakeNamedBufferResidentNV(buffer, access);
    }

    // NVShaderBufferLoad
    @Override
    public void glMakeNamedBufferNonResidentNV(int buffer) {
        NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(buffer);
    }

    // NVVertexBufferUnifiedMemory
    @Override
    public void glBufferAddressRangeNV(int pname, int index, long address, long length) {
        NVVertexBufferUnifiedMemory.glBufferAddressRangeNV(pname, index, address, length);
    }

    // GL11
    @Override
    public void glEnableClientState(int cap) {
        GL11.glEnableClientState(cap);
    }

    // GL11
    @Override
    public void glDisableClientState(int cap) {
        GL11.glDisableClientState(cap);
    }

    // ===================== MESH SHADERS =====================

    @Override
    public void glDrawMeshTasksNV(int first, int count) {
        NVMeshShader.glDrawMeshTasksNV(first, count);
    }

    // NVMeshShader
    @Override
    public void glMultiDrawMeshTasksIndirectNV(long indirect, int drawCount, int stride) {
        NVMeshShader.glMultiDrawMeshTasksIndirectNV(indirect, drawCount, stride);
    }

    // ===================== SPARSE BUFFERS =====================

    @Override
    public void glBufferPageCommitmentARB(int target, long offset, long size, boolean commit) {
        ARBSparseBuffer.glBufferPageCommitmentARB(target, offset, size, commit);
    }

    // GL11
    @Override
    public void glFinish() {
        GL11.glFinish();
    }
}
