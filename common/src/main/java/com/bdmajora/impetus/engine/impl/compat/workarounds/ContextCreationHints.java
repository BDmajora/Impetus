package com.bdmajora.impetus.engine.impl.compat.workarounds;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsVendor;

import java.util.List;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Rules for the context flags requested before window creation and the driver mitigations applied once the context is current
public final class ContextCreationHints {
    // GL enum value of the synchronous debug output state
    static final int DEBUG_OUTPUT_SYNCHRONOUS = 0x8242;

    // Prevents instantiation
    private ContextCreationHints() {
    }

    // Returns true when a no error context is requested and neither a debug context nor a Linux Wayland session nor a legacy Intel Windows driver rules it out
    public static boolean allowsNoErrorContext(boolean requested, boolean debugContext, OsKind os, String sessionType, List<GraphicsAdapterInfo> adapters) {
        // Rejects a null operating system or adapter list
        Objects.requireNonNull(os, "Operating system must not be null");
        Objects.requireNonNull(adapters, "Adapters must not be null");

        // Refuses a context that is off by option or that would also be a debug context
        if (!requested || debugContext) {
            return false;
        }

        // Refuses a Linux Wayland session whether the window opens natively or under Xwayland
        if (os == OsKind.LINUX && "wayland".equalsIgnoreCase(sessionType)) {
            return false;
        }

        // Refuses a Windows machine with a legacy Intel driver or without adapter information
        return !Workarounds.hasLegacyIntelWindowsAdapter(os, adapters);
    }

    // Enables synchronous debug output on an NVIDIA context under Windows with GL 4.3 and returns whether it did
    public static boolean applyContextMitigations(GlContextInfo context, OsKind os) {
        // Rejects a null context or operating system
        Objects.requireNonNull(context, "Context must not be null");
        Objects.requireNonNull(os, "Operating system must not be null");

        // Returns false off Windows or for a vendor other than NVIDIA or below GL 4.3
        if (os != OsKind.WINDOWS || GraphicsVendor.fromContext(context) != GraphicsVendor.NVIDIA || !LWJGL.isOpenGLVersionSupported(4, 3)) {
            return false;
        }

        LWJGL.glEnable(DEBUG_OUTPUT_SYNCHRONOUS);
        return true;
    }
}
