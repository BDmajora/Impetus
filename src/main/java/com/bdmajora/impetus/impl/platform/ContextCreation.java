package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.workarounds.ContextCreationHints;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.cleanroommc.client.sdl.SDLException;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLVideo;

import java.util.function.Supplier;

// The SDL builder creates the context during Minecraft.init, before vanilla's createDisplay adopts it
public final class ContextCreation {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    static final long ADAPTER_WAIT_MILLIS = 3_000;

    private ContextCreation() {
    }

    public static <T> T create(Supplier<T> createWindow) {
        T window;
        if (!requestsNoError() || !SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 1)) {
            window = createWindow.get();
        } else {
            try {
                window = createWindow.get();
                // Keep the attribute for Cleanroom's shared contexts, which must match this context's flags
                LOGGER.info("[Impetus] Created a no-error OpenGL context");
            } catch (SDLException e) {
                LOGGER.warn("[Impetus] Window creation failed with a no-error context requested; retrying without one", e);
                SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 0);
                try {
                    window = createWindow.get();
                } catch (RuntimeException retry) {
                    retry.addSuppressed(e);
                    throw retry;
                }
            }
        }

        if (ContextCreationHints.applyContextMitigations(GlContextInfo.capture(), OsKind.current())) {
            LOGGER.info("[Impetus] Enabled synchronous debug output to keep NVIDIA threaded optimizations off");
        }
        return window;
    }

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
