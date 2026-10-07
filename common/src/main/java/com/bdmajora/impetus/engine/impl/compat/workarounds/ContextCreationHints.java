package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;

import java.util.List;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// What Impetus decides around window creation now that Cleanroom builds the window through GLFW: whether to ask for a KHR_no_error context (Sodium's "No Error Context", unreachable through LWJGL2's Display), and the driver mitigations applied the moment the context is current
public final class ContextCreationHints {
    // GL_DEBUG_OUTPUT_SYNCHRONOUS
    static final int DEBUG_OUTPUT_SYNCHRONOUS = 0x8242;

    private ContextCreationHints() {
    }

    // The option asks for one and nothing rules it out: GLFW cannot create a context that is both debug and no-error, Wayland sessions have crashed creating one, and legacy Intel Windows drivers misbehave under it
    public static boolean allowsNoErrorContext(boolean requested, boolean debugContext, OsKind os, String sessionType, List<GraphicsAdapterInfo> adapters) {
        if (!requested || debugContext) {
            return false;
        }

        // Applies under Xwayland too, where it would be fine; telling the two apart before the window exists is not worth the risk
        if (os == OsKind.LINUX && "wayland".equalsIgnoreCase(sessionType)) {
            return false;
        }

        return !Workarounds.hasLegacyIntelWindowsAdapter(os, adapters);
    }

    // NVIDIA's Windows driver leaves its threaded optimizations off while debug output is synchronous, which is the only switch a process has once the driver is loaded; Linux needs nothing here since Cleanroom sets __GL_THREADED_OPTIMIZATIONS=0 before creating the window. True when applied
    public static boolean applyContextMitigations(GlContextInfo context, OsKind os) {
        if (os != OsKind.WINDOWS || GraphicsVendor.fromContext(context) != GraphicsVendor.NVIDIA || !LWJGL.isOpenGLVersionSupported(4, 3)) {
            return false;
        }

        LWJGL.glEnable(DEBUG_OUTPUT_SYNCHRONOUS);
        return true;
    }
}
