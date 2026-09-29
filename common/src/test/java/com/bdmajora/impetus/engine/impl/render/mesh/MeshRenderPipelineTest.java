package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshStoresTest;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class MeshRenderPipelineTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
        Statics.set(MeshShaderSupport.class, "supported", null);
    }

    @AfterEach
    void forget() {
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
    }

    @Test
    void framesDrawLastFramesCommandsAndCullForTheNext() {
        MeshRenderPipeline pipeline = new MeshRenderPipeline(1 << 20, 1 << 20);
        assertNotNull(pipeline.getSections());
        assertNotNull(pipeline.getUploadStream());
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f().perspective(1f, 1f, 0.1f, 1000f), new Matrix4f());
        pipeline.renderFrame(Sections.viewport(8, 8, 8), matrices, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.never()).glDrawMeshTasksNV(Mockito.anyInt(), Mockito.anyInt());

        pipeline.getSections().upload(0, 0, 0, SectionGeometry.from(MeshStoresTest.mesh(2, ModelQuadFacing.POS_Y)));
        pipeline.getSections().upload(20, 0, 0, SectionGeometry.from(MeshStoresTest.mesh(2, ModelQuadFacing.POS_Y)));
        pipeline.renderFrame(new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), matrices, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.never()).glDrawMeshTasksNV(Mockito.anyInt(), Mockito.anyInt());
        pipeline.renderFrame(Sections.viewport(8.5, 8.5, 8.5), matrices, 8.5, 8.5, 8.5, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDrawMeshTasksNV(0, 2);
        Mockito.verify(TestGl.gl(), Mockito.never()).glMultiDrawMeshTasksIndirectNV(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        pipeline.renderFrame(Sections.viewport(8, 8, 8), matrices, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl()).glMultiDrawMeshTasksIndirectNV(0L, 2, 0);
        pipeline.renderFrame(new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), matrices, 8, 8, 8, 800, 600);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glClearNamedBufferSubDataZero(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        pipeline.delete();
    }
}
