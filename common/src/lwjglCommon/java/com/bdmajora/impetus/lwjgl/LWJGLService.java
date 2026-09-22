package com.bdmajora.impetus.lwjgl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// the LWJGL2/LWJGL3 abstraction: every GL entry point the engine needs, in whichever binding is present
public interface LWJGLService {

    // ===================== CAPABILITIES =====================

    boolean isOpenGLVersionSupported(int major, int minor);
    boolean isExtensionSupported(GLExtension extension);
    int getPointerSize();

    // ===================== BUFFER OPERATIONS =====================

    int glGenBuffers();
    void glDeleteBuffers(int buffer);
    void glBindBuffer(int target, int buffer);
    void glBufferData(int target, long size, int usage);
    void glBufferData(int target, ByteBuffer data, int usage);
    void glBufferData(int target, long size, long data, int usage);
    void glBufferSubData(int target, long offset, ByteBuffer data);
    void glBufferStorage(int target, long size, int flags);
    // Server-side buffer fill (GL 4.3): zeroes a buffer with no client memory or upload, so it works on immutable non-client-writable storage
    void glClearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data);
    ByteBuffer glMapBufferRange(int target, long offset, long length, int flags);
    long nglMapBuffer(int target, int access);
    ByteBuffer glMapBuffer(int target, int access);
    void glUnmapBuffer(int target);
    void glFlushMappedBufferRange(int target, long offset, long length);
    void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size);
    void glBindBufferBase(int target, int index, int buffer);

    // ===================== VAO OPERATIONS =====================

    int glGenVertexArrays();
    void glDeleteVertexArrays(int array);
    void glBindVertexArray(int array);
    void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer);
    void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer);
    void glEnableVertexAttribArray(int index);
    void glDisableVertexAttribArray(int index);
    int glGetVertexAttribi(int index, int pname);

    // ===================== SHADER OPERATIONS =====================

    int glCreateShader(int type);
    void glShaderSource(int shader, CharSequence source);

    // glShaderSource with a null length pointer so the driver relies on the null terminator; some AMD drivers misread the length (fix from Canvas, hat tip fewizz)
    void glShaderSourceSafe(int shader, CharSequence source);
    void glCompileShader(int shader);
    String glGetShaderInfoLog(int shader, int maxLength);
    int glGetShaderi(int shader, int pname);
    void glDeleteShader(int shader);

    int glCreateProgram();
    void glAttachShader(int program, int shader);
    void glDetachShader(int program, int shader);
    void glLinkProgram(int program);
    String glGetProgramInfoLog(int program, int maxLength);
    int glGetProgrami(int program, int pname);
    // Returns the active uniform's name at index; writes its size to sizeType.get(0) and GL type to sizeType.get(1).
    String glGetActiveUniform(int program, int index, int maxLength, IntBuffer sizeType);
    void glUseProgram(int program);
    void glDeleteProgram(int program);
    void glBindAttribLocation(int program, int index, CharSequence name);
    void glBindFragDataLocation(int program, int colorNumber, CharSequence name);

    // ===================== UNIFORM OPERATIONS =====================

    int glGetUniformLocation(int program, CharSequence name);
    int glGetUniformBlockIndex(int program, CharSequence name);
    void glUniformBlockBinding(int program, int blockIndex, int blockBinding);
    void glUniform1f(int location, float v0);
    void glUniform1i(int location, int v0);
    void glUniform1fv(int location, FloatBuffer value);
    void glUniform2i(int location, int v0, int v1);
    void glUniform3i(int location, int v0, int v1, int v2);
    void glUniform2f(int location, float v0, float v1);
    void glUniform4f(int location, float v0, float v1, float v2, float v3);
    void glUniform4i(int location, int v0, int v1, int v2, int v3);
    void glUniform3f(int location, float v0, float v1, float v2);
    void glUniform3fv(int location, FloatBuffer value);
    void glUniform3fv(int location, float[] value);
    void glUniform4fv(int location, FloatBuffer value);
    void glUniform4fv(int location, float[] value);
    void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value);
    void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value);

    // ===================== DRAW OPERATIONS =====================

    void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex);
    void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex);
    void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride);

    // ===================== SYNC OPERATIONS =====================

    long glFenceSync(int condition, int flags);
    int glClientWaitSync(long sync, int flags, long timeout);
    int glGetSynci(long sync, int pname, IntBuffer length);
    void glWaitSync(long sync, int flags, long timeout);
    void glDeleteSync(long sync);

    // ===================== QUERY OPERATIONS =====================

    int glGenQueries();
    void glDeleteQueries(int query);
    void glQueryCounter(int id, int target);
    long glGetQueryObjectui64(int id, int pname);
    // GL15 form, for GL_QUERY_RESULT_AVAILABLE polls; plain GL15 since availability is not a timer-extension entry point
    int glGetQueryObjecti(int id, int pname);

    // ===================== TEXTURE OPERATIONS =====================

    int glGenTextures();
    void glGenTextures(int[] textures);
    void glDeleteTextures(int texture);
    void glDeleteTextures(int[] textures);
    void glBindTexture(int target, int texture);
    void glActiveTexture(int texture);
    void glMultiTexCoord2f(int target, float s, float t);
    int glGetTexLevelParameteri(int target, int level, int pname);
    void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);
    void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels);
    // Into the bound GL_PIXEL_PACK_BUFFER at a byte offset, the asynchronous form
    void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelsOffset);
    void glGenerateMipmap(int target);
    int glGenSamplers();
    void glDeleteSamplers(int sampler);
    void glBindSampler(int unit, int sampler);
    void glSamplerParameteri(int sampler, int pname, int param);

    void glDepthRange(double zNear, double zFar);
    void glPixelStorei(int pname, int param);
    void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels);
    void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels);

    // ===================== IMAGE LOAD/STORE + COMPUTE (GL 4.2/4.3/4.4) =====================

    void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);
    void glMemoryBarrier(int barriers);
    void glDispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ);

    void glDispatchComputeIndirect(long indirect);
    void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data);
    // Null data clears to zero
    default void glClearTexImage(int texture, int level, int format, int type) {
        glClearTexImage(texture, level, format, type, (ByteBuffer) null);
    }
    void glTexParameteri(int target, int pname, int param);
    void glTexParameteriv(int target, int pname, int[] params);
    void glTexParameterf(int target, int pname, float param);

    // ===================== FRAMEBUFFER OPERATIONS =====================

    int glGenFramebuffers();
    void glDeleteFramebuffers(int framebuffer);
    void glBindFramebuffer(int target, int framebuffer);
    int glCheckFramebufferStatus(int target);
    void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    // Optional; backends without layered attachments throw
    default void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        throw new UnsupportedOperationException("Layered framebuffer attachments are not supported");
    }
    void glDrawBuffers(int buf);
    void glDrawBuffers(IntBuffer bufs);
    void glReadBuffer(int mode);
    void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    int glGenRenderbuffers();
    void glDeleteRenderbuffers(int renderbuffer);
    void glBindRenderbuffer(int target, int renderbuffer);
    void glRenderbufferStorage(int target, int internalformat, int width, int height);
    void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer);

    // ===================== STATE OPERATIONS =====================

    void glEnable(int cap);
    void glDisable(int cap);
    // Optional; false unless the backend overrides
    default boolean supportsBufferBlending() {
        return false;
    }
    // Optional; backends without indexed enable throw
    default void glEnablei(int target, int index) {
        throw new UnsupportedOperationException("Indexed GL enable is not supported");
    }
    // Optional; backends without indexed enable throw
    default void glDisablei(int target, int index) {
        throw new UnsupportedOperationException("Indexed GL disable is not supported");
    }
    void glBlendFunc(int sfactor, int dfactor);
    void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
    // Optional; backends without per-buffer blending throw
    default void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        throw new UnsupportedOperationException("Per-buffer blending is not supported");
    }
    void glDepthFunc(int func);
    void glDepthMask(boolean flag);
    void glColorMask(boolean red, boolean green, boolean blue, boolean alpha);
    void glViewport(int x, int y, int width, int height);
    void glClear(int mask);
    void glClearColor(float red, float green, float blue, float alpha);
    void glClearDepth(double depth);
    void glCullFace(int mode);
    void glDrawArrays(int mode, int first, int count);
    int glGetError();
    // Blocks until every issued command completes; only the terrain upload ring uses it, when it fills mid-frame, to avoid overwriting in-flight staging bytes
    void glFinish();

    // ===================== COMPATIBILITY PROFILE =====================

    void glMatrixMode(int mode);
    void glLoadMatrixf(FloatBuffer m);

    // ===================== MISC GL =====================

    int glGetInteger(int pname);
    float glGetFloat(int pname);
    void glGetIntegerv(int pname, int[] params);
    boolean glGetBoolean(int pname);
    String glGetString(int pname);
    int glGetAttribLocation(int program, CharSequence name);

    // ===================== MEMORY STACK =====================

    MemoryStack stackPush();

    // ===================== NATIVE MEMORY =====================

    long nmemAlloc(long size);
    long nmemCalloc(long count, long size);
    long nmemAlignedAlloc(long alignment, long size);
    long nmemRealloc(long ptr, long size);
    void nmemFree(long ptr);
    void nmemAlignedFree(long ptr);

    ByteBuffer memAlloc(int size);
    ByteBuffer memCalloc(int size);
    ByteBuffer memRealloc(ByteBuffer buffer, int size);
    void memFree(Buffer buffer);
    ByteBuffer memByteBuffer(long address, int capacity);
    long memAddress(Buffer buffer);
    long memAddress(Buffer buffer, int position);

    void memSet(long address, int value, long bytes);
    void memCopy(long src, long dst, long bytes);

    // Buffer form over the address form, copying src's remaining bytes
    default void memCopy(ByteBuffer src, ByteBuffer dst) {
        memCopy(memAddress(src), memAddress(dst), src.remaining());
    }

    void memPutByte(long address, byte value);
    void memPutShort(long address, short value);
    void memPutInt(long address, int value);
    void memPutFloat(long address, float value);
    void memPutLong(long address, long value);
    void memPutAddress(long address, long value);

    byte memGetByte(long address);
    short memGetShort(long address);
    int memGetInt(long address);
    float memGetFloat(long address);
    long memGetLong(long address);
    long memGetAddress(long address);
    ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity);

    // ===================== DIRECT STATE ACCESS BUFFERS (GL 4.5 / ARB_direct_state_access) =====================

    // LWJGL3-only entry points below; defaulted to throw so the LWJGL2 backend stays untouched, and nothing calls them without MeshShaderSupport passing first

    default int glCreateBuffers() {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default void glNamedBufferStorage(int buffer, long size, int flags) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default long nglMapNamedBufferRange(int buffer, long offset, long length, int access) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default void glUnmapNamedBuffer(int buffer) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default void glFlushMappedNamedBufferRange(int buffer, long offset, long length) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default void glCopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Fills a buffer range with zeroes; a null data pointer is spec-defined as "clear to zero", the only form this engine needs
    default void glClearNamedBufferSubDataZero(int buffer, int internalFormat, long offset, long size, int format, int type) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // Optional DSA; throws unless the backend has it
    default void glClearNamedBufferDataZero(int buffer, int internalFormat, int format, int type) {
        throw new UnsupportedOperationException("Direct state access is not supported");
    }

    // ===================== BINDLESS BUFFERS (NV_shader_buffer_load) =====================

    // The buffer's address in GPU virtual memory; only meaningful once the buffer has been made resident
    default long glGetNamedBufferGpuAddressNV(int buffer) {
        throw new UnsupportedOperationException("Bindless buffers are not supported");
    }

    // Optional NV bindless; throws unless the backend has it
    default void glMakeNamedBufferResidentNV(int buffer, int access) {
        throw new UnsupportedOperationException("Bindless buffers are not supported");
    }

    // Optional NV bindless; throws unless the backend has it
    default void glMakeNamedBufferNonResidentNV(int buffer) {
        throw new UnsupportedOperationException("Bindless buffers are not supported");
    }

    // Binds a buffer range by GPU address rather than object name; how the scene uniform block and indirect command buffer are attached
    default void glBufferAddressRangeNV(int pname, int index, long address, long length) {
        throw new UnsupportedOperationException("Bindless buffers are not supported");
    }

    // Unified-memory client states that switch the address bindings on; fixed-function calls, hence the compatibility profile requirement
    default void glEnableClientState(int cap) {
        throw new UnsupportedOperationException("Client state is not supported");
    }

    // Optional fixed-function; no-op on backends without client state
    default void glDisableClientState(int cap) {
        throw new UnsupportedOperationException("Client state is not supported");
    }

    // ===================== MESH SHADERS (NV_mesh_shader) =====================

    default void glDrawMeshTasksNV(int first, int count) {
        throw new UnsupportedOperationException("Mesh shaders are not supported");
    }

    // Draws from a GPU-written command buffer bound via glBufferAddressRangeNV; stride 0 means tightly packed uvec2 commands
    default void glMultiDrawMeshTasksIndirectNV(long indirect, int drawCount, int stride) {
        throw new UnsupportedOperationException("Mesh shaders are not supported");
    }

    // ===================== SPARSE BUFFERS (ARB_sparse_buffer) =====================

    // Commits or releases physical pages of a sparse buffer; offset and size must be multiples of GL_SPARSE_BUFFER_PAGE_SIZE_ARB
    default void glBufferPageCommitmentARB(int target, long offset, long size, boolean commit) {
        throw new UnsupportedOperationException("Sparse buffers are not supported");
    }
}
