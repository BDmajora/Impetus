package com.bdmajora.impetus.lwjgl.lwjgl2;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryStack;
import com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryUtilities;
import com.bdmajora.impetus.lwjgl.lwjgl2.memory.Pointer;
import com.bdmajora.impetus.lwjgl.GLExtension;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.APPLEVertexArrayObject;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTGpuShader4;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// The LWJGL2 backend of LWJGLService, selected by LWJGLServiceProvider when the game is running on LWJGL 2
// A record because the capability decisions — which VAO, timer-query and vertex-attrib entry points this driver
// actually has — are resolved once at creation and never change afterwards
public record LWJGL2Service(
        VAOMode vaoMode,
        TimerQueryMode timerQueryMode,
        VertexAttribIMode vertexAttribIMode,
        Long2ObjectOpenHashMap<GLSync> syncObjects) implements LWJGLService {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/LWJGL2Service");

    private enum VAOMode {
        CORE {
            @Override public int gen() { return GL30.glGenVertexArrays(); }
            @Override public void delete(int array) { GL30.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { GL30.glBindVertexArray(array); }
        },
        ARB {
            @Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
            @Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
        },
        APPLE {
            @Override public int gen() { return APPLEVertexArrayObject.glGenVertexArraysAPPLE(); }
            @Override public void delete(int array) { APPLEVertexArrayObject.glDeleteVertexArraysAPPLE(array); }
            @Override public void bind(int array) { APPLEVertexArrayObject.glBindVertexArrayAPPLE(array); }
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

    private enum TimerQueryMode {
        CORE {
            @Override public void queryCounter(int id, int target) { GL33.glQueryCounter(id, target); }
            @Override public long getQueryObjectui64(int id, int pname) { return GL33.glGetQueryObjectui64(id, pname); }
        },
        ARB {
            @Override public void queryCounter(int id, int target) { ARBTimerQuery.glQueryCounter(id, target); }
            @Override public long getQueryObjectui64(int id, int pname) { return ARBTimerQuery.glGetQueryObjectui64(id, pname); }
        },
        NONE {
            @Override public void queryCounter(int id, int target) { /* no-op */ }
            @Override public long getQueryObjectui64(int id, int pname) { return 0L; }
        };

        public abstract void queryCounter(int id, int target);
        public abstract long getQueryObjectui64(int id, int pname);
    }

    private enum VertexAttribIMode {
        CORE {
            @Override public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
                GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
            }
        },
        EXT {
            @Override public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
                EXTGpuShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
            }
        },
        NONE {
            @Override public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
                throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
            }
        };

        public abstract void vertexAttribIPointer(int index, int size, int type, int stride, long pointer);
    }

    // Resolves VAO, timer-query and vertex-attrib entry points once from the context capabilities
    public static LWJGL2Service create() {
        ContextCapabilities caps = GLContext.getCapabilities();

        VAOMode vaoMode;
        if (caps.OpenGL30) {
            vaoMode = VAOMode.CORE;
        } else if (caps.GL_ARB_vertex_array_object) {
            vaoMode = VAOMode.ARB;
        } else if (caps.GL_APPLE_vertex_array_object) {
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

        return new LWJGL2Service(vaoMode, timerQueryMode, vertexAttribIMode, new Long2ObjectOpenHashMap<>());
    }

    // ===================== CAPABILITIES =====================

    @Override
    public boolean isOpenGLVersionSupported(int major, int minor) {
        ContextCapabilities caps = GLContext.getCapabilities();
        switch (major * 10 + minor) {
            case 11: return caps.OpenGL11;
            case 12: return caps.OpenGL12;
            case 13: return caps.OpenGL13;
            case 14: return caps.OpenGL14;
            case 15: return caps.OpenGL15;
            case 20: return caps.OpenGL20;
            case 21: return caps.OpenGL21;
            case 30: return caps.OpenGL30;
            case 31: return caps.OpenGL31;
            case 32: return caps.OpenGL32;
            case 33: return caps.OpenGL33;
            case 40: return caps.OpenGL40;
            case 41: return caps.OpenGL41;
            case 42: return caps.OpenGL42;
            case 43: return caps.OpenGL43;
            case 44: return caps.OpenGL44;
            case 45: return caps.OpenGL45;
            default: return false;
        }
    }

    // Maps the abstraction's extension enum onto LWJGL2's capability flags
    @Override
    public boolean isExtensionSupported(GLExtension extension) {
        ContextCapabilities caps = GLContext.getCapabilities();
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
            // LWJGL 2.9.3 has no bindings for any of these, so the mesh-shader backend can never run on this
            // path regardless of what the driver actually supports
            case NV_mesh_shader,
                 NV_shader_buffer_load,
                 NV_vertex_buffer_unified_memory,
                 NV_uniform_buffer_unified_memory,
                 NV_representative_fragment_test,
                 NV_bindless_multi_draw_indirect,
                 NV_gpu_shader5,
                 NV_fragment_shader_barycentric,
                 ARB_sparse_buffer -> false;
        };
    }

    // GL 4.0 core or ARB_draw_buffers_blend
    @Override
    public boolean supportsBufferBlending() {
        ContextCapabilities caps = GLContext.getCapabilities();
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
        return GL15.glGenBuffers();
    }

    // GL15
    @Override
    public void glDeleteBuffers(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    // GL15
    @Override
    public void glBindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    // GL15
    @Override
    public void glBufferData(int target, long size, int usage) {
        GL15.glBufferData(target, size, usage);
    }

    // GL15
    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    // LWJGL2's nglBufferData differs in signature; a zero pointer allocates uninitialised, otherwise wraps the address
    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        // LWJGL2 nglBufferData has different signature - wrap the pointer
        if (data == 0) {
            GL15.glBufferData(target, size, usage);
        } else {
            ByteBuffer buf = MemoryUtilities.memByteBuffer(data, (int) size);
            GL15.glBufferData(target, buf, usage);
        }
    }

    // GL15
    @Override
    public void glBufferSubData(int target, long offset, ByteBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    // ARBBufferStorage
    @Override
    public void glBufferStorage(int target, long size, int flags) {
        ARBBufferStorage.glBufferStorage(target, size, flags);
    }

    // GL43
    @Override
    public void glClearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data) {
        GL43.glClearBufferData(target, internalFormat, format, type, data);
    }

    // GL30
    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return GL30.glMapBufferRange(target, offset, length, flags, null);
    }

    // LWJGL2 returns a ByteBuffer, so the address is extracted from it
    @Override
    public long nglMapBuffer(int target, int access) {
        ByteBuffer buf = GL15.glMapBuffer(target, access, null);
        return buf != null ? MemoryUtilities.memAddress(buf) : 0L;
    }

    // GL15
    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        return GL15.glMapBuffer(target, access, null);
    }

    // GL15
    @Override
    public void glUnmapBuffer(int target) {
        GL15.glUnmapBuffer(target);
    }

    // GL30
    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        GL30.glFlushMappedBufferRange(target, offset, length);
    }

    // GL31
    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    // GL30
    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        GL30.glBindBufferBase(target, index, buffer);
    }

    // ===================== VAO OPERATIONS =====================

    @Override
    public int glGenVertexArrays() {
        return vaoMode.gen();
    }

    // Through the resolved VAO mode: core, APPLE or unsupported
    @Override
    public void glDeleteVertexArrays(int array) {
        vaoMode.delete(array);
    }

    // Through the resolved VAO mode
    @Override
    public void glBindVertexArray(int array) {
        vaoMode.bind(array);
    }

    // GL20
    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    // Through the resolved mode: GL30 or EXT_gpu_shader4
    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        vertexAttribIMode.vertexAttribIPointer(index, size, type, stride, pointer);
    }

    // GL20
    @Override
    public void glEnableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    // GL20
    @Override
    public void glDisableVertexAttribArray(int index) {
        GL20.glDisableVertexAttribArray(index);
    }

    // LWJGL2 only exposes the buffer form, so a scratch IntBuffer is used
    @Override
    public int glGetVertexAttribi(int index, int pname) {
        // LWJGL2's GL20 only exposes the buffer form of glGetVertexAttrib; LWJGL2 insists on room for four values
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer params = stack.mallocInt(4);
            GL20.glGetVertexAttrib(index, pname, params);
            return params.get(0);
        }
    }

    // ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return GL20.glCreateShader(type);
    }

    // GL20
    @Override
    public void glShaderSource(int shader, CharSequence source) {
        GL20.glShaderSource(shader, source);
    }

    // AMD workaround: null length forces null-terminator reliance, avoiding a driver read past the string
    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        // AMD driver workaround: pass null for string length to force null-terminator reliance.
        // Some AMD drivers don't receive or interpret the length correctly, resulting in an
        // access violation when the driver tries to read past the string memory.
        // In LWJGL2, we use the normal method which should be null-terminated aware.
        ByteBuffer sourceBuffer = MemoryUtilities.memUTF8(source, true);
        GL20.glShaderSource(shader, sourceBuffer);
    }

    // GL20
    @Override
    public void glCompileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    // GL20
    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }

    // GL20
    @Override
    public int glGetShaderi(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }

    // GL20
    @Override
    public void glDeleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    // GL20
    @Override
    public int glCreateProgram() {
        return GL20.glCreateProgram();
    }

    // GL20
    @Override
    public void glAttachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    // GL20
    @Override
    public void glDetachShader(int program, int shader) {
        GL20.glDetachShader(program, shader);
    }

    // GL20
    @Override
    public void glLinkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    // GL20
    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        return GL20.glGetProgramInfoLog(program, maxLength);
    }

    // GL20
    @Override
    public int glGetProgrami(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }

    // GL20
    @Override
    public String glGetActiveUniform(int program, int index, int maxLength, java.nio.IntBuffer sizeType) {
        return GL20.glGetActiveUniform(program, index, maxLength, sizeType);
    }

    // GL20
    @Override
    public void glUseProgram(int program) {
        GL20.glUseProgram(program);
    }

    // GL20
    @Override
    public void glDeleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    // GL20
    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }

    // GL30
    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        GL30.glBindFragDataLocation(program, colorNumber, name);
    }

    // ===================== UNIFORM OPERATIONS =====================

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }

    // GL31
    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return GL31.glGetUniformBlockIndex(program, name);
    }

    // GL31
    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        GL31.glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    // GL20
    @Override
    public void glUniform1f(int location, float v0) {
        GL20.glUniform1f(location, v0);
    }

    // GL20
    @Override
    public void glUniform1i(int location, int v0) {
        GL20.glUniform1i(location, v0);
    }

    // GL20
    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        GL20.glUniform1(location, value);
    }

    // GL20
    @Override
    public void glUniform2i(int location, int v0, int v1) {
        GL20.glUniform2i(location, v0, v1);
    }

    // GL20
    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        GL20.glUniform3i(location, v0, v1, v2);
    }

    // GL20
    @Override
    public void glUniform2f(int location, float v0, float v1) {
        GL20.glUniform2f(location, v0, v1);
    }

    // GL20
    @Override
    public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20.glUniform4f(location, v0, v1, v2, v3);
    }

    // GL20
    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20.glUniform4i(location, v0, v1, v2, v3);
    }

    // GL20
    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        GL20.glUniform3f(location, v0, v1, v2);
    }

    // GL20
    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        GL20.glUniform3(location, value);
    }

    // Single vec3 takes the scalar fast path; arrays go through a buffer
    @Override
    public void glUniform3fv(int location, float[] value) {
        if (value.length == 3) {
            // fast path: single vec3
            GL20.glUniform3f(location, value[0], value[1], value[2]);
            return;
        }

        if (value.length % 3 != 0)
            throw new IllegalArgumentException("Array length must be multiple of 3");

        // general path: multiple vec3s, staged on the stack rather than a fresh direct buffer per call
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(value.length);
            buffer.put(value).flip();
            GL20.glUniform3(location, buffer);
        }
    }

    // GL20
    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        GL20.glUniform4(location, value);
    }

    // Single vec4 takes the scalar fast path; arrays go through a buffer
    @Override
    public void glUniform4fv(int location, float[] value) {
        if (value.length == 4) {
            // fast path: single vec4
            GL20.glUniform4f(location, value[0], value[1], value[2], value[3]);
            return;
        }

        if (value.length % 4 != 0)
            throw new IllegalArgumentException("Array length must be multiple of 4");

        // general path: multiple vec4s, staged on the stack rather than a fresh direct buffer per call
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(value.length);
            buffer.put(value).flip();
            GL20.glUniform4(location, buffer);
        }
    }

    // GL20
    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix3(location, transpose, value);
    }

    // GL20
    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix4(location, transpose, value);
    }

    // ===================== DRAW OPERATIONS =====================

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        GL32.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    // Emulated with a loop; LWJGL2 cannot call arbitrary GL functions by name
    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        // Must emulate using a loop on LWJGL2. Sad! But there is no better way until we start writing our own
        // native code, because LWJGL2 doesn't allow us to call arbitrary functions by name like LWJGL3 does.
        for (int i = 0; i < drawcount; i++) {
            int count = MemoryUtilities.memGetInt(pCount + (long) i * 4);
            if (count > 0) {
                long indices = MemoryUtilities.memGetAddress(pIndices + (long) i * Pointer.POINTER_SIZE);
                int baseVertex = MemoryUtilities.memGetInt(pBaseVertex + (long) i * 4);
                GL32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
            }
        }
    }

    // GL43
    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        GL43.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // ===================== SYNC OPERATIONS =====================

    @Override
    public long glFenceSync(int condition, int flags) {
        GLSync sync = GL32.glFenceSync(condition, flags);
        long pointer = sync.getPointer();
        syncObjects.put(pointer, sync);
        return pointer;
    }

    // GL32
    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        return GL32.glClientWaitSync(syncObjects.get(sync), flags, timeout);
    }

    // LWJGL2's form returns the value directly, so the length buffer is filled by hand
    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        // LWJGL2 glGetSynci doesn't take length buffer - it returns single value directly
        int result = GL32.glGetSynci(syncObjects.get(sync), pname);
        if (length != null) {
            length.put(0, 1); // Always returns single value
        }
        return result;
    }

    // GL32
    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
        GL32.glWaitSync(syncObjects.get(sync), flags, timeout);
    }

    // Sync objects are tracked by handle since LWJGL2 uses GLSync objects, not longs
    @Override
    public void glDeleteSync(long sync) {
        GLSync obj = syncObjects.remove(sync);
        if (obj == null) return;
        GL32.glDeleteSync(obj);
    }

    // ===================== QUERY OPERATIONS =====================

    @Override
    public int glGenQueries() {
        return GL15.glGenQueries();
    }

    // GL15
    @Override
    public void glDeleteQueries(int query) {
        GL15.glDeleteQueries(query);
    }

    // Through the resolved timer-query mode: GL33, ARB or no-op
    @Override
    public void glQueryCounter(int id, int target) {
        timerQueryMode.queryCounter(id, target);
    }

    // Through the resolved timer-query mode
    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        return timerQueryMode.getQueryObjectui64(id, pname);
    }

    @Override
    public int glGetQueryObjecti(int id, int pname) {
        return GL15.glGetQueryObjecti(id, pname);
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return GL11.glGenTextures();
    }

    // Array form via a scratch IntBuffer
    @Override
    public void glGenTextures(int[] textures) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buf = stack.mallocInt(textures.length);
            GL11.glGenTextures(buf);
            buf.get(textures);
        }
    }

    // GL11
    @Override
    public void glDeleteTextures(int texture) {
        GL11.glDeleteTextures(texture);
    }

    // Array form via a scratch IntBuffer
    @Override
    public void glDeleteTextures(int[] textures) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buf = stack.mallocInt(textures.length);
            buf.put(textures).flip();
            GL11.glDeleteTextures(buf);
        }
    }

    // GL11
    @Override
    public void glBindTexture(int target, int texture) {
        GL11.glBindTexture(target, texture);
    }

    // GL13
    @Override
    public void glActiveTexture(int texture) {
        GL13.glActiveTexture(texture);
    }

    // GL13
    @Override
    public void glMultiTexCoord2f(int target, float s, float t) {
        GL13.glMultiTexCoord2f(target, s, t);
    }

    // GL11
    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return GL11.glGetTexLevelParameteri(target, level, pname);
    }

    // GL11
    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    // GL11
    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelsOffset) {
        GL11.glReadPixels(x, y, width, height, format, type, pixelsOffset);
    }

    // GL30
    @Override
    public void glGenerateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }

    // GL33
    @Override
    public int glGenSamplers() {
        return GL33.glGenSamplers();
    }

    // GL33
    @Override
    public void glDeleteSamplers(int sampler) {
        GL33.glDeleteSamplers(sampler);
    }

    // GL33
    @Override
    public void glBindSampler(int unit, int sampler) {
        GL33.glBindSampler(unit, sampler);
    }

    // GL33
    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GL33.glSamplerParameteri(sampler, pname, param);
    }

    // GL11
    @Override
    public void glDepthRange(double zNear, double zFar) {
        GL11.glDepthRange(zNear, zFar);
    }

    // GL11
    @Override
    public void glPixelStorei(int pname, int param) {
        GL11.glPixelStorei(pname, param);
    }

    // GL11
    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    // GL12
    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        org.lwjgl.opengl.GL12.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    // GL42
    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        org.lwjgl.opengl.GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    // GL42
    @Override
    public void glMemoryBarrier(int barriers) {
        org.lwjgl.opengl.GL42.glMemoryBarrier(barriers);
    }

    // GL43
    @Override
    public void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    // GL43
    @Override
    public void glDispatchComputeIndirect(long indirect) {
        GL43.glDispatchComputeIndirect(indirect);
    }

    // ARBClearTexture
    @Override
    public void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        ARBClearTexture.glClearTexImage(texture, level, format, type, data);
    }

    // GL11
    @Override
    public void glTexParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    // Array form via a scratch IntBuffer
    @Override
    public void glTexParameteriv(int target, int pname, int[] params) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buf = stack.mallocInt(params.length);
            buf.put(params).flip();
            GL11.glTexParameter(target, pname, buf);
        }
    }

    // GL11
    @Override
    public void glTexParameterf(int target, int pname, float param) {
        GL11.glTexParameterf(target, pname, param);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================

    @Override
    public int glGenFramebuffers() {
        return GL30.glGenFramebuffers();
    }

    // GL30
    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    // GL30
    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        GL30.glBindFramebuffer(target, framebuffer);
    }

    // GL30
    @Override
    public int glCheckFramebufferStatus(int target) {
        return GL30.glCheckFramebufferStatus(target);
    }

    // GL30
    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    // GL30
    @Override
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GL30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    // GL20
    @Override
    public void glDrawBuffers(int buf) {
        GL20.glDrawBuffers(buf);
    }

    // GL20
    @Override
    public void glDrawBuffers(IntBuffer bufs) {
        GL20.glDrawBuffers(bufs);
    }

    // GL11
    @Override
    public void glReadBuffer(int mode) {
        GL11.glReadBuffer(mode);
    }

    // GL30
    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // GL30
    @Override
    public int glGenRenderbuffers() {
        return GL30.glGenRenderbuffers();
    }

    // GL30
    @Override
    public void glDeleteRenderbuffers(int renderbuffer) {
        GL30.glDeleteRenderbuffers(renderbuffer);
    }

    // GL30
    @Override
    public void glBindRenderbuffer(int target, int renderbuffer) {
        GL30.glBindRenderbuffer(target, renderbuffer);
    }

    // GL30
    @Override
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GL30.glRenderbufferStorage(target, internalformat, width, height);
    }

    // GL30
    @Override
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GL30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    // ===================== STATE OPERATIONS =====================

    @Override
    public void glEnable(int cap) {
        GL11.glEnable(cap);
    }

    // GL11
    @Override
    public void glDisable(int cap) {
        GL11.glDisable(cap);
    }

    // GL30
    @Override
    public void glEnablei(int target, int index) {
        GL30.glEnablei(target, index);
    }

    // GL30
    @Override
    public void glDisablei(int target, int index) {
        GL30.glDisablei(target, index);
    }

    // GL11
    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        GL11.glBlendFunc(sfactor, dfactor);
    }

    // GL14
    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // ARBDrawBuffersBlend
    @Override
    public void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    // GL11
    @Override
    public void glDepthFunc(int func) {
        GL11.glDepthFunc(func);
    }

    // GL11
    @Override
    public void glDepthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    // GL11
    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    // GL11
    @Override
    public void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    // GL11
    @Override
    public void glClear(int mask) {
        GL11.glClear(mask);
    }

    // GL11
    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    // GL11
    @Override
    public void glClearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    // GL11
    @Override
    public void glCullFace(int mode) {
        GL11.glCullFace(mode);
    }

    // GL11
    @Override
    public void glDrawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }

    // GL11
    @Override
    public int glGetError() {
        return GL11.glGetError();
    }

    // ===================== COMPATIBILITY PROFILE (GL1.x) =====================

    @Override
    public void glMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    // GL11
    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        GL11.glLoadMatrix(m);
    }

    // ===================== MISC GL =====================

    @Override
    public int glGetInteger(int pname) {
        return GL11.glGetInteger(pname);
    }

    // GL11
    @Override
    public float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    // Array form via a scratch IntBuffer
    @Override
    public void glGetIntegerv(int pname, int[] params) {
        // LWJGL2 checks for at least 16 ints of room on the generic glGetInteger
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buf = stack.mallocInt(Math.max(16, params.length));
            GL11.glGetInteger(pname, buf);
            buf.get(params, 0, params.length);
        }
    }

    // GL11
    @Override
    public boolean glGetBoolean(int pname) {
        return GL11.glGetBoolean(pname);
    }

    // GL11
    @Override
    public String glGetString(int pname) {
        return GL11.glGetString(pname);
    }

    // GL20
    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return GL20.glGetAttribLocation(program, name);
    }

    // ===================== MEMORY STACK OPERATIONS =====================

    @Override
    public com.bdmajora.impetus.lwjgl.MemoryStack stackPush() {
        return new LWJGL2MemoryStack(MemoryStack.stackPush());
    }

    // ===================== NATIVE MEMORY OPERATIONS =====================

    @Override
    public long nmemAlloc(long size) {
        return MemoryUtilities.nmemAlloc(size);
    }

    // MemoryUtilities
    @Override
    public long nmemCalloc(long count, long size) {
        return MemoryUtilities.nmemCalloc(count, size);
    }

    // Over-allocates and stores the real address just before the aligned one; ported from FalsePattern's LegacyMemoryAdapter
    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        // Ported from FalsePattern's lwjgl2-impetus LegacyMemoryAdapter
        int required = 8;
        alignment = Math.max(alignment, 8);
        long prefixLength = Math.max(alignment, required) + required;
        long capacity = size + prefixLength;
        long addr = MemoryUtilities.nmemAlloc(capacity);
        if (addr == 0) return 0;
        long shiftBy = alignment - (addr % alignment);
        if (shiftBy < required) {
            shiftBy += alignment;
        }
        long finalAddr = addr + shiftBy;
        MemoryUtilities.memPutLong(finalAddr - 8, addr);
        return finalAddr;
    }

    // MemoryUtilities
    @Override
    public long nmemRealloc(long ptr, long size) {
        return MemoryUtilities.nmemRealloc(ptr, size);
    }

    // MemoryUtilities
    @Override
    public void nmemFree(long ptr) {
        MemoryUtilities.nmemFree(ptr);
    }

    // Reads the real address back from the word before the aligned pointer
    @Override
    public void nmemAlignedFree(long ptr) {
        if (ptr == 0) return;
        long realAddr = MemoryUtilities.memGetLong(ptr - 8);
        MemoryUtilities.nmemFree(realAddr);
    }

    // MemoryUtilities
    @Override
    public ByteBuffer memAlloc(int size) {
        return MemoryUtilities.memAlloc(size);
    }

    // MemoryUtilities
    @Override
    public ByteBuffer memCalloc(int size) {
        return MemoryUtilities.memCalloc(size);
    }

    // MemoryUtilities
    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return MemoryUtilities.memRealloc(buffer, size);
    }

    // MemoryUtilities
    @Override
    public void memFree(Buffer buffer) {
        MemoryUtilities.memFree(buffer);
    }

    // MemoryUtilities
    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtilities.memByteBuffer(address, capacity);
    }

    // MemoryUtilities
    @Override
    public long memAddress(Buffer buffer) {
        return MemoryUtilities.memAddress(buffer);
    }

    // Address of a buffer element; null buffer treats position as an absolute address
    @Override
    public long memAddress(Buffer buffer, int position) {
        if (buffer == null) {
            return position;
        }
        return MemoryUtilities.memAddress0(buffer) + position;
    }

    // MemoryUtilities
    @Override
    public void memSet(long address, int value, long bytes) {
        MemoryUtilities.memSet(address, value, bytes);
    }

    // MemoryUtilities
    @Override
    public void memCopy(long src, long dst, long bytes) {
        MemoryUtilities.memCopy(src, dst, bytes);
    }

    // MemoryUtilities
    @Override
    public void memPutByte(long address, byte value) {
        MemoryUtilities.memPutByte(address, value);
    }

    // MemoryUtilities
    @Override
    public void memPutShort(long address, short value) {
        MemoryUtilities.memPutShort(address, value);
    }

    // MemoryUtilities
    @Override
    public void memPutInt(long address, int value) {
        MemoryUtilities.memPutInt(address, value);
    }

    // MemoryUtilities
    @Override
    public void memPutFloat(long address, float value) {
        MemoryUtilities.memPutFloat(address, value);
    }

    // MemoryUtilities
    @Override
    public void memPutLong(long address, long value) {
        MemoryUtilities.memPutLong(address, value);
    }

    // MemoryUtilities
    @Override
    public void memPutAddress(long address, long value) {
        MemoryUtilities.memPutAddress(address, value);
    }

    // MemoryUtilities
    @Override
    public byte memGetByte(long address) {
        return MemoryUtilities.memGetByte(address);
    }

    // MemoryUtilities
    @Override
    public short memGetShort(long address) {
        return MemoryUtilities.memGetShort(address);
    }

    // MemoryUtilities
    @Override
    public int memGetInt(long address) {
        return MemoryUtilities.memGetInt(address);
    }

    // MemoryUtilities
    @Override
    public float memGetFloat(long address) {
        return MemoryUtilities.memGetFloat(address);
    }

    // MemoryUtilities
    @Override
    public long memGetLong(long address) {
        return MemoryUtilities.memGetLong(address);
    }

    // MemoryUtilities
    @Override
    public long memGetAddress(long address) {
        return MemoryUtilities.memGetAddress(address);
    }

    // A view over part of a buffer without copying
    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        long address = MemoryUtilities.memAddress(buffer) + offset;
        return MemoryUtilities.memByteBuffer(address, capacity);
    }

    // GL11
    @Override
    public void glFinish() {
        GL11.glFinish();
    }
}
