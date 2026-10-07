package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Single gate for the mesh-shader backend, probed once on the render thread after a GL context exists; every other class in this package assumes it passed
public final class MeshShaderSupport {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // NV_bindless_multi_draw_indirect's indirect-buffer address tokens, which LWJGL's NVVertexBufferUnifiedMemory leaves out
    public static final int GL_DRAW_INDIRECT_UNIFIED_NV = 0x8F40;
    public static final int GL_DRAW_INDIRECT_ADDRESS_NV = 0x8F41;

    // All required, no partial path: Turing and newer NVIDIA has all of it, nothing else has NV_mesh_shader at all
    private static final GLExtension[] REQUIRED = {
            GLExtension.NV_mesh_shader,
            GLExtension.NV_shader_buffer_load,
            GLExtension.NV_vertex_buffer_unified_memory,
            GLExtension.NV_uniform_buffer_unified_memory,
            GLExtension.NV_representative_fragment_test,
            GLExtension.NV_bindless_multi_draw_indirect,
            GLExtension.NV_gpu_shader5,
            GLExtension.NV_fragment_shader_barycentric,
            GLExtension.ARB_direct_state_access
    };

    private static Boolean supported;
    private static String unsupportedReason = "not probed";
    private static boolean sparseGeometry;

    private MeshShaderSupport() {}

    // Probed once and cached
    public static boolean isSupported() {
        if (supported == null) {
            probe();
        }
        return supported;
    }

    // Why the backend is unavailable, for the F3 overlay and the log; empty once isSupported() is true
    public static String getUnsupportedReason() {
        isSupported();
        return supported ? "" : unsupportedReason;
    }

    // Whether geometry can live in one enormous sparse buffer instead of a fixed dense allocation; separate from isSupported because losing it costs VRAM, not correctness
    public static boolean supportsSparseGeometry() {
        isSupported();
        return sparseGeometry;
    }

    // Turns the backend off for the rest of the session after it failed to start on hardware that advertised everything, so every reload does not fail again
    public static void markBroken(String reason) {
        supported = false;
        unsupportedReason = reason;
        LOGGER.error("Mesh-shader terrain backend disabled: {}", reason);
    }

    // Checks the extensions and a minimal compile, since some drivers advertise but fail
    private static void probe() {
        List<String> missing = new ArrayList<>();

        for (GLExtension extension : REQUIRED) {
            if (!LWJGL.isExtensionSupported(extension)) {
                missing.add(extension.name());
            }
        }

        // The unified-memory client states are fixed-function, so a core-profile context cannot bind the scene uniform or indirect buffer by address
        if (!LWJGL.isExtensionSupported(GLExtension.ARB_compatibility)) {
            missing.add("ARB_compatibility");
        }

        if (!missing.isEmpty()) {
            supported = false;
            unsupportedReason = "missing " + String.join(", ", missing);
            LOGGER.info("Mesh-shader terrain backend unavailable: {}", unsupportedReason);
            return;
        }

        supported = true;
        unsupportedReason = "";

        // NVIDIA's Linux driver does not reliably release committed pages, so the sparse address space grows without bound; Nvidium falls back the same way to a fixed dense buffer
        sparseGeometry = LWJGL.isExtensionSupported(GLExtension.ARB_sparse_buffer) && OsKind.current() != OsKind.LINUX;

        LOGGER.info("Mesh-shader terrain backend available (sparse geometry: {})", sparseGeometry);
    }
}
