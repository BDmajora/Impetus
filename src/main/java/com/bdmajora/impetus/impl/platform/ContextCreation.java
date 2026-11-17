package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterInfo;
import com.bdmajora.impetus.engine.impl.compat.probe.GraphicsAdapterProbe;
import com.bdmajora.impetus.engine.impl.compat.workarounds.ContextCreationHints;
import com.bdmajora.impetus.engine.impl.gl.debug.GLDebug;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.cleanroommc.client.sdl.SDLException;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLVideo;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Supplier;

// Creator of the window with the context hints of the adapter.
public final class ContextCreation {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    // Longest wait for the adapter probe.
    static final long ADAPTER_WAIT_MILLIS = 3_000;

    // Prevents instantiation
    private ContextCreation() {
    }

    // Creates the window and retries without hints on failure.
    public static <T> T create(Supplier<T> createWindow) {
        T window;
        if (!requestsNoError() || !SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_NO_ERROR, 1)) {
            window = createWindow.get();
        } else {
            try {
                window = createWindow.get();
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
        // Reads the impetus.enableGLDebug switch and the label and group limits of the new context
        GLDebug.reloadDebugState();
        return window;
    }

    // Returns whether a no error context is requested.
    static boolean requestsNoError() {
        boolean option;
        try {
            option = ImpetusGameOptions.load().performance.useNoErrorGLContext;
        } catch (RuntimeException e) {
            return false;
        }
        return ContextCreationHints.allowsNoErrorContext(option, ForgeEarlyConfig.OPENGL_DEBUG_CONTEXT, OsKind.current(),
                System.getenv("XDG_SESSION_TYPE"), new ProbedAdapters());
    }

    // Lazy list of the probed graphics adapters.
    private static final class ProbedAdapters extends AbstractList<GraphicsAdapterInfo> {
        // Probed adapters or null before the first read.
        private List<GraphicsAdapterInfo> adapters;

        // Returns the probed adapters and probes them on first use.
        private List<GraphicsAdapterInfo> adapters() {
            if (adapters == null) {
                adapters = GraphicsAdapterProbe.adapters(ADAPTER_WAIT_MILLIS);
            }

            return adapters;
        }

        // Returns the value.
        @Override
        public GraphicsAdapterInfo get(int index) {
            return adapters().get(index);
        }

        // Returns the size.
        @Override
        public int size() {
            return adapters().size();
        }
    }
}
