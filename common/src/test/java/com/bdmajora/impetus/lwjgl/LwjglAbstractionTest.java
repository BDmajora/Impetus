package com.bdmajora.impetus.lwjgl;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class LwjglAbstractionTest {
    @Test
    void providerExposesTheSelectedBackend() {
        assertEquals(8, LWJGLServiceProvider.POINTER_SIZE);
        assertEquals(0L, LWJGLServiceProvider.NULL);
        assertSame(TestGl.gl(), LWJGLServiceProvider.LWJGL);
    }

    @Test
    void serviceDefaultsThrowForOptionalEntryPoints() {
        LWJGLService gl = TestGl.gl();
        gl.glClearTexImage(1, 0, 0, 0);
        Mockito.verify(gl).glClearTexImage(1, 0, 0, 0, (ByteBuffer) null);
        assertThrows(UnsupportedOperationException.class, () -> gl.glFramebufferTextureLayer(0, 0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glEnablei(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glDisablei(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glBlendFuncSeparatei(0, 0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, gl::glCreateBuffers);
        assertThrows(UnsupportedOperationException.class, () -> gl.glNamedBufferStorage(0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.nglMapNamedBufferRange(0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glUnmapNamedBuffer(0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glFlushMappedNamedBufferRange(0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glCopyNamedBufferSubData(0, 0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glClearNamedBufferSubDataZero(0, 0, 0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glClearNamedBufferDataZero(0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glGetNamedBufferGpuAddressNV(0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glMakeNamedBufferResidentNV(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glMakeNamedBufferNonResidentNV(0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glBufferAddressRangeNV(0, 0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glEnableClientState(0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glDisableClientState(0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glDrawMeshTasksNV(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glMultiDrawMeshTasksIndirectNV(0, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> gl.glBufferPageCommitmentARB(0, 0, 0, true));
        // Default methods run for real on the mock, and the interface default is off
        assertFalse(gl.supportsBufferBlending());
        ByteBuffer src = gl.memCalloc(8);
        ByteBuffer dst = gl.memCalloc(8);
        src.putInt(0, 42);
        gl.memCopy(src, dst);
        assertEquals(42, dst.getInt(0));
        gl.memFree(src);
        gl.memFree(dst);
    }

    @Test
    void memoryStackFillsPrimitiveBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer one = stack.ints(7);
            assertEquals(7, one.get(0));
            IntBuffer many = stack.ints(1, 2, 3);
            assertEquals(3, many.remaining());
            assertEquals(3, many.get(2));
            FloatBuffer f = stack.floats(1.5f);
            assertEquals(1.5f, f.get(0));
            FloatBuffer fs = stack.floats(1f, 2f);
            assertEquals(2f, fs.get(1));
            assertTrue(stack.getSize() > 0);
            assertTrue(stack.getAddress() != 0);
            stack.setPointer(stack.getPointer());
        }
    }
}
