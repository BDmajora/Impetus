package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Checks whether the mesh shader backend can run.
public final class MeshShaderSupport {
    // Logger of the backend.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // Token of the unified indirect buffer.
    public static final int GL_DRAW_INDIRECT_UNIFIED_NV = 0x8F40;
    // Token of the indirect buffer address.
    public static final int GL_DRAW_INDIRECT_ADDRESS_NV = 0x8F41;

    // Extensions that the backend needs.
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

    // Whether the backend is supported or null before the probe.
    private static volatile Boolean supported;
    // Reason the backend is unavailable.
    private static volatile String unsupportedReason = "not probed";
    // Whether sparse geometry buffers can be used.
    private static volatile boolean sparseGeometry;

    // Prevents instantiation
    private MeshShaderSupport() {}

    // Returns whether the backend is supported and probes once.
    public static boolean isSupported() {
        if (supported == null) {
            probe();
        }
        return supported;
    }

    // Returns the reason the backend is unavailable or an empty text.
    public static String getUnsupportedReason() {
        isSupported();
        return supported ? "" : unsupportedReason;
    }

    // Returns whether sparse geometry buffers can be used.
    public static boolean supportsSparseGeometry() {
        isSupported();
        return sparseGeometry;
    }

    // Disables the backend for the session and rejects a null reason.
    public static void markBroken(String reason) {
        java.util.Objects.requireNonNull(reason, "reason");
        supported = false;
        unsupportedReason = reason;
        LOGGER.error("Mesh-shader terrain backend disabled: {}", reason);
    }

    // Checks the extensions and stores the result.
    private static void probe() {
        List<String> missing = new ArrayList<>();

        for (GLExtension extension : REQUIRED) {
            if (!LWJGL.isExtensionSupported(extension)) {
                missing.add(extension.name());
            }
        }

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

        sparseGeometry = LWJGL.isExtensionSupported(GLExtension.ARB_sparse_buffer) && OsKind.current() != OsKind.LINUX;

        LOGGER.info("Mesh-shader terrain backend available (sparse geometry: {})", sparseGeometry);
    }
}
