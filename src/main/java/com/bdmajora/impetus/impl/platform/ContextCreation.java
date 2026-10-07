package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.workarounds.ContextCreationHints;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.common.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Wraps vanilla's createDisplay: Cleanroom's Display reads its GLFW hints from ForgeEarlyConfig, so the no-error hint goes in there for the one create and comes back out afterwards, keeping forge_early.cfg at the user's own value
public final class ContextCreation {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    // How long window creation waits on the adapter probe the coremod started; instant on Linux, normally long finished on Windows
    static final long ADAPTER_WAIT_MILLIS = 3_000;

    private ContextCreation() {
    }

    // Creates the window, with a no-error context when allowed and once more without one if the driver refuses it, then applies the context mitigations
    public static void create(Runnable createDisplay) {
        // A no-error context the user already asked Cleanroom for is theirs; Impetus only adds one, and only takes back its own
        if (ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR || !requestsNoError()) {
            createDisplay.run();
        } else {
            ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = true;
            try {
                createDisplay.run();
                LOGGER.info("[Impetus] Created a no-error OpenGL context");
            } catch (IllegalStateException e) {
                LOGGER.warn("[Impetus] Window creation failed with a no-error context requested; retrying without one", e);
                ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = false;
                createDisplay.run();
            } finally {
                // Cleanroom saves every early option along with the GL version it settled on; this rewrites the file with the hint back off
                ForgeEarlyConfig.OPENGL_CONTEXT_NO_ERROR = false;
                ConfigManager.sync(ForgeEarlyConfig.class);
            }
        }

        if (ContextCreationHints.applyContextMitigations(GlContextInfo.capture(), OsKind.current())) {
            LOGGER.info("[Impetus] Enabled synchronous debug output to keep NVIDIA threaded optimizations off");
        }
    }

    // The No Error Context option, read from the options file directly since the mod (and its loaded copy) does not exist yet, then checked against what this machine can take
    static boolean requestsNoError() {
        boolean option;
        try {
            option = ImpetusGameOptions.load().performance.useNoErrorGLContext;
        } catch (RuntimeException e) {
            return false;
        }

        return ContextCreationHints.allowsNoErrorContext(option, ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT, OsKind.current(),
                System.getenv("XDG_SESSION_TYPE"), GraphicsAdapterProbe.adapters(ADAPTER_WAIT_MILLIS));
    }
}
