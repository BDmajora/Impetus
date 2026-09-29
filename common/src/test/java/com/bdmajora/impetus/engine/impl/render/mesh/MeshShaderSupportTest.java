package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class MeshShaderSupportTest {
    @AfterEach
    void forget() {
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
    }

    @Test
    void probeReportsMissingExtensionsOrSparseSupport() {
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(false);
        assertFalse(MeshShaderSupport.isSupported());
        assertTrue(MeshShaderSupport.getUnsupportedReason().contains("NV_mesh_shader"));
        assertTrue(MeshShaderSupport.getUnsupportedReason().contains("ARB_compatibility"));
        assertFalse(MeshShaderSupport.supportsSparseGeometry());

        Statics.set(MeshShaderSupport.class, "supported", null);
        Mockito.when(TestGl.gl().isExtensionSupported(Mockito.any())).thenReturn(true);
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        assertTrue(MeshShaderSupport.isSupported());
        assertEquals("", MeshShaderSupport.getUnsupportedReason());
        assertTrue(MeshShaderSupport.supportsSparseGeometry());

        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
        assertTrue(MeshShaderSupport.isSupported());
        assertFalse(MeshShaderSupport.supportsSparseGeometry());
        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_sparse_buffer)).thenReturn(false);
        Statics.set(MeshShaderSupport.class, "supported", null);
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        assertFalse(MeshShaderSupport.supportsSparseGeometry());
    }
}
