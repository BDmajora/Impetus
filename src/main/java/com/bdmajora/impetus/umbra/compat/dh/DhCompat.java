package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.umbra.gl.shader.ShaderCompileException;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

// The DH-free face of the Distant Horizons compat (Iris's DHCompat): presence detection, the per-frame statics the uniform providers and shadow pass read, and each pipeline's handle to its DhCompatInternal. Every DH-typed class is reached through method handles resolved in run(), so this class (and everything that references it) loads with the DH API absent from the classpath; the API is compileOnly
public final class DhCompat {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final String INTERNAL = "com.bdmajora.impetus.umbra.compat.dh.DhCompatInternal";
    private static final String EVENTS = "com.bdmajora.impetus.umbra.compat.dh.LodRendererEvents";

    // False until run() finds DH's API and mod id, and again after any failure binding to it
    private static boolean dhPresent;
    private static boolean lastIncompatible;
    private static MethodHandle newInternal;
    private static MethodHandle clearPipeline;
    private static MethodHandle incompatible;
    private static MethodHandle getDepthTex;
    private static MethodHandle getDepthTexNoTranslucent;
    private static MethodHandle getFarPlane;
    private static MethodHandle getNearPlane;
    private static MethodHandle getRenderDistance;
    private static MethodHandle checkFrame;
    private static MethodHandle isRenderingEnabled;
    private static MethodHandle renderShadowSolid;
    private static MethodHandle renderShadowTranslucent;
    private static MethodHandle shadowsOverridden;

    // The DhCompatInternal for one pipeline, or null without DH or when the pack has no dh_* programs
    private final Object internal;

    // Built with the pipeline once its gbuffer schedule exists, since the LOD framebuffers attach the same colour targets; a shader compile failure in a dh_* program propagates like any other program's so the pack fails as a whole
    public DhCompat(UmbraRenderingPipeline pipeline, ShaderPack pack, boolean renderDhShadow) {
        Object instance = null;
        if (dhPresent) {
            try {
                instance = newInternal.invoke(pipeline, pack, renderDhShadow);
                lastIncompatible = (boolean) incompatible.invoke(instance);
            } catch (Throwable e) {
                lastIncompatible = false;
                Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                if (cause instanceof ShaderCompileException) {
                    throw (ShaderCompileException) cause;
                }
                throw new RuntimeException("Unknown error loading Distant Horizons compatibility.", cause);
            }
        }
        this.internal = instance;
    }

    // Mod-init hook: binds to DH when its mod id and API are present, and queues the DH event handlers; any failure logs and leaves DH treated as absent
    public static void run() {
        boolean modLoaded = Loader.isModLoaded("distanthorizons");
        try {
            if (!modLoaded) {
                dhPresent = false;
                LOGGER.info("[Umbra] Distant Horizons not found; LOD shader support inactive.");
                return;
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> internalClass = Class.forName(INTERNAL);
            Class<?> eventsClass = Class.forName(EVENTS);
            newInternal = lookup.findConstructor(internalClass,
                    MethodType.methodType(void.class, UmbraRenderingPipeline.class, ShaderPack.class, boolean.class));
            clearPipeline = lookup.findVirtual(internalClass, "clear", MethodType.methodType(void.class));
            incompatible = lookup.findVirtual(internalClass, "incompatiblePack", MethodType.methodType(boolean.class));
            getDepthTex = lookup.findVirtual(internalClass, "getStoredDepthTex", MethodType.methodType(int.class));
            getDepthTexNoTranslucent = lookup.findVirtual(internalClass, "getDepthTexNoTranslucent", MethodType.methodType(int.class));
            shadowsOverridden = lookup.findVirtual(internalClass, "shouldOverrideShadow", MethodType.methodType(boolean.class));
            getFarPlane = lookup.findStatic(internalClass, "getFarPlane", MethodType.methodType(float.class));
            getNearPlane = lookup.findStatic(internalClass, "getNearPlane", MethodType.methodType(float.class));
            getRenderDistance = lookup.findStatic(internalClass, "getRenderDistance", MethodType.methodType(int.class));
            checkFrame = lookup.findStatic(internalClass, "checkFrame", MethodType.methodType(boolean.class));
            isRenderingEnabled = lookup.findStatic(internalClass, "isRenderingEnabled", MethodType.methodType(boolean.class));
            renderShadowSolid = lookup.findStatic(internalClass, "renderShadowSolid", MethodType.methodType(void.class));
            renderShadowTranslucent = lookup.findStatic(internalClass, "renderShadowTranslucent", MethodType.methodType(void.class));
            MethodHandle setupEventHandlers = lookup.findStatic(eventsClass, "setupEventHandlers", MethodType.methodType(void.class));
            // Set before the handlers bind, since their DhApiAfterDhInitEvent reads it
            dhPresent = true;
            setupEventHandlers.invoke();
            LOGGER.info("[Umbra] Distant Horizons found; LOD shader support armed.");
        } catch (Throwable e) {
            dhPresent = false;
            if (e instanceof ExceptionInInitializerError) {
                LOGGER.error("[Umbra] Failure loading Distant Horizons compat; LOD shader support disabled", e.getCause());
            } else {
                // A missing API member means an older DH than the API this was built against (7.0.0)
                LOGGER.error("[Umbra] Distant Horizons found, but one or more API methods are missing; Impetus needs DH API 7.0.0 or newer. LOD shader support disabled", e);
            }
        }
    }

    // Whether DH is installed with a usable API
    public static boolean isPresent() {
        return dhPresent;
    }

    // Whether the active pack compiled without any dh_* program while DH was rendering; the pack still runs, LODs just draw with DH's own shaders into the gbuffer
    public static boolean lastPackIncompatible() {
        return dhPresent && hasRenderingEnabled() && lastIncompatible;
    }

    // DH's far clip plane in blocks (the dhFarPlane uniform)
    public static float getFarPlane() {
        if (!dhPresent) {
            return 0.01f;
        }
        try {
            return (float) getFarPlane.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // DH's near clip plane in blocks (the dhNearPlane uniform)
    public static float getNearPlane() {
        if (!dhPresent) {
            return 0.01f;
        }
        try {
            return (float) getNearPlane.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // DH's render distance in BLOCKS (the dhRenderDistance uniform); the player's render distance in blocks without DH, like Iris
    public static int getRenderDistance() {
        if (!dhPresent) {
            return Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
        }
        try {
            return (int) getRenderDistance.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Per-frame: tracks DH's rendering toggle and asks for a pipeline rebuild when it flips, since the DISTANT_HORIZONS define and the LOD programs are baked at pack load; returns whether DH is rendering
    public static boolean checkFrame() {
        if (!dhPresent) {
            return false;
        }
        try {
            return (boolean) checkFrame.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Whether DH is installed and currently rendering LODs, as of the last checkFrame: gates the DISTANT_HORIZONS define and every DH-dependent uniform, and is a plain read so the macro environment can ask mid pack-load without triggering the reload checkFrame does
    public static boolean hasRenderingEnabled() {
        if (!dhPresent) {
            return false;
        }
        try {
            return (boolean) isRenderingEnabled.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // The LOD projection: the camera's field of view and aspect with DH's near and far planes, exactly how Iris builds dhProjection, so a pack's position reconstruction from dhDepthTex0 matches what dh_terrain rendered with
    public static Matrix4f getProjection() {
        Matrix4f projection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
        if (!dhPresent) {
            return projection;
        }
        return new Matrix4f().setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(),
                getNearPlane(), getFarPlane());
    }

    // Draws DH's opaque LODs into the shadow map; called by the shadow pass after its terrain, since Impetus's shadow pass never goes through RenderGlobal where DH's own hook lives
    public static void renderShadowSolid() {
        if (!dhPresent) {
            return;
        }
        try {
            renderShadowSolid.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Same for the deferred translucent LODs (water), after the shadow pass's translucent terrain
    public static void renderShadowTranslucent() {
        if (!dhPresent) {
            return;
        }
        try {
            renderShadowTranslucent.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Whether this pipeline's pack shades LOD shadows (a dh_shadow program with dhShadow.enabled not false), so the shadow pass knows to draw them and to size its frustum for the LOD distance
    public boolean shouldRenderShadows() {
        if (this.internal == null) {
            return false;
        }
        try {
            return (boolean) shadowsOverridden.invoke(this.internal);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Frees the LOD programs and framebuffers; pipeline teardown
    public void clearPipeline() {
        if (this.internal == null) {
            return;
        }
        try {
            clearPipeline.invoke(this.internal);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // DH's depth texture (dhDepthTex0), the depth the LOD passes wrote this frame; -1 before the first LOD frame
    public int getDepthTex() {
        if (this.internal == null) {
            return -1;
        }
        try {
            return (int) getDepthTex.invoke(this.internal);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // DH's depth before its translucent pass (dhDepthTex1); 0 before the first LOD frame
    public int getDepthTexNoTranslucent() {
        if (this.internal == null) {
            return -1;
        }
        try {
            return (int) getDepthTexNoTranslucent.invoke(this.internal);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // The DhCompatInternal, typed as Object for the DH-free callers; the event handlers cast it
    public Object getInstance() {
        return this.internal;
    }
}
