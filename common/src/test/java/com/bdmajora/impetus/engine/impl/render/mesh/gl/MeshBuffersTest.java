package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class MeshBuffersTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    @Test
    void bindlessBuffersAreResidentUntilDeleted() {
        BindlessBuffer buffer = new BindlessBuffer(64);
        assertEquals(64, buffer.getSize());
        assertTrue(buffer.getId() > 0);
        assertEquals(0x1000L * buffer.getId(), buffer.getDeviceAddress());
        buffer.clear();
        buffer.clearRange(8, 16);
        Mockito.verify(TestGl.gl()).glClearNamedBufferSubDataZero(Mockito.eq(buffer.getId()), Mockito.anyInt(), Mockito.eq(8L), Mockito.eq(16L), Mockito.anyInt(), Mockito.anyInt());
        buffer.delete();
        buffer.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glMakeNamedBufferNonResidentNV(buffer.getId());
        Mockito.doReturn(0L).when(TestGl.gl()).glGetNamedBufferGpuAddressNV(Mockito.anyInt());
        assertThrows(IllegalStateException.class, () -> new BindlessBuffer(8));
        assertThrows(IllegalStateException.class, () -> new SparseBindlessBuffer(8));
    }

    @Test
    void sparseBuffersCommitPagesByReferenceCount() {
        SparseBindlessBuffer buffer = new SparseBindlessBuffer(SparseBindlessBuffer.PAGE_SIZE * 3 + 1);
        assertEquals(SparseBindlessBuffer.PAGE_SIZE * 4, buffer.getSize());
        assertEquals(SparseBindlessBuffer.alignUp(10, 4), 12);
        assertEquals(8, SparseBindlessBuffer.alignUp(8, 4));
        assertTrue(buffer.getId() > 0);
        assertTrue(buffer.getDeviceAddress() != 0);
        assertEquals(0, buffer.getCommittedPages());
        buffer.ensureCommitted(0, SparseBindlessBuffer.PAGE_SIZE + 1);
        assertEquals(2, buffer.getCommittedPages());
        buffer.ensureCommitted(SparseBindlessBuffer.PAGE_SIZE, 10);
        assertEquals(2, buffer.getCommittedPages());
        assertEquals(2 * SparseBindlessBuffer.PAGE_SIZE, buffer.getCommittedBytes());
        buffer.release(0, SparseBindlessBuffer.PAGE_SIZE + 1);
        assertEquals(1, buffer.getCommittedPages());
        buffer.release(SparseBindlessBuffer.PAGE_SIZE, 10);
        assertEquals(0, buffer.getCommittedPages());
        buffer.ensureCommitted(5, 0);
        buffer.delete();
        buffer.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteBuffers(buffer.getId());
    }

    @Test
    void mappedUploadBuffersExposeTheirMapping() {
        MappedUploadBuffer buffer = new MappedUploadBuffer(128);
        assertEquals(128, buffer.getSize());
        assertTrue(buffer.getId() > 0);
        assertTrue(buffer.getClientAddress() != 0);
        TestGl.gl().memPutInt(buffer.getClientAddress(), 7);
        buffer.flush(0, 4);
        Mockito.verify(TestGl.gl()).glFlushMappedNamedBufferRange(buffer.getId(), 0, 4);
        buffer.delete();
        buffer.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glUnmapNamedBuffer(buffer.getId());
        Mockito.doReturn(0L).when(TestGl.gl()).nglMapNamedBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        assertThrows(IllegalStateException.class, () -> new MappedUploadBuffer(8));
    }

    @Test
    void meshProgramsLinkTheirStages() {
        MeshProgram program = MeshProgram.builder("test")
                .constants(ShaderConstants.builder().add("X").build())
                .stage(ShaderType.MESH, "impetus:mesh/region_raster.mesh")
                .stage(ShaderType.FRAGMENT, "impetus:mesh/region_raster.frag")
                .link();
        assertEquals("test", program.getName());
        program.bind();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDetachShader(Mockito.anyInt(), Mockito.anyInt());
        program.delete();
        program.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteProgram(Mockito.anyInt());
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> MeshProgram.builder("broken").link());
    }
}
