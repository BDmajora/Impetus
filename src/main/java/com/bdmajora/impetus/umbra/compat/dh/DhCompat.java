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

// Bridge to the Distant Horizons support classes that only load when the mod is present.
public final class DhCompat {
    // Logger of load failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Class of the internal bridge.
    private static final String INTERNAL = "com.bdmajora.impetus.umbra.compat.dh.DhCompatInternal";
    // Class of the render event handlers.
    private static final String EVENTS = "com.bdmajora.impetus.umbra.compat.dh.LodRendererEvents";

    // Loaded state of the bridge.
    private static boolean dhPresent;
    // Incompatible state of the last loaded pack.
    private static boolean lastIncompatible;
    // Constructor of the internal bridge.
    private static MethodHandle newInternal;
    // Handle that releases the bridge.
    private static MethodHandle clearPipeline;
    // Handle of the pack compatibility check.
    private static MethodHandle incompatible;
    // Handle of the stored depth texture.
    private static MethodHandle getDepthTex;
    // Handle of the depth texture without translucents.
    private static MethodHandle getDepthTexNoTranslucent;
    // Handle of the far plane.
    private static MethodHandle getFarPlane;
    // Handle of the near plane.
    private static MethodHandle getNearPlane;
    // Handle of the LOD render distance.
    private static MethodHandle getRenderDistance;
    // Handle of the frame check.
    private static MethodHandle checkFrame;
    // Handle of the rendering switch.
    private static MethodHandle isRenderingEnabled;
    // Handle of the solid shadow LOD draw.
    private static MethodHandle renderShadowSolid;
    // Handle of the deferred LOD draw.
    private static MethodHandle renderDeferredLods;
    // Handle of the shadow override check.
    private static MethodHandle shadowsOverridden;

    // Internal bridge of a pipeline or null.
    private final Object internal;

    // Creates the bridge of a pipeline and releases it when the load fails.
    public DhCompat(UmbraRenderingPipeline pipeline, ShaderPack pack, boolean renderDhShadow) {
        Object instance = null;
        if (dhPresent) {
            try {
                instance = newInternal.invoke(pipeline, pack, renderDhShadow);
                lastIncompatible = (boolean) incompatible.invoke(instance);
            } catch (Throwable e) {
                lastIncompatible = false;
                // Releases a bridge that was created before the failure
                if (instance != null) {
                    try {
                        clearPipeline.invoke(instance);
                    } catch (Throwable suppressed) {
                        e.addSuppressed(suppressed);
                    }
                }
                Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                if (cause instanceof ShaderCompileException) {
                    throw (ShaderCompileException) cause;
                }
                throw new RuntimeException("Unknown error loading Distant Horizons compatibility.", cause);
            }
        }
        this.internal = instance;
    }

    // Looks up the bridge methods when the mod is present and installs the event handlers.
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
            renderDeferredLods = lookup.findStatic(internalClass, "renderDeferredLods", MethodType.methodType(void.class));
            MethodHandle setupEventHandlers = lookup.findStatic(eventsClass, "setupEventHandlers", MethodType.methodType(void.class));
            dhPresent = true;
            setupEventHandlers.invoke();
            LOGGER.info("[Umbra] Distant Horizons found; LOD shader support armed.");
        } catch (Throwable e) {
            dhPresent = false;
            if (e instanceof ExceptionInInitializerError) {
                LOGGER.error("[Umbra] Failure loading Distant Horizons compat; LOD shader support disabled", e.getCause());
            } else {
                LOGGER.error("[Umbra] Distant Horizons found, but one or more API methods are missing; Impetus needs DH API 7.0.0 or newer. LOD shader support disabled", e);
            }
        }
    }

    // Returns whether the bridge loaded.
    public static boolean isPresent() {
        return dhPresent;
    }

    // Returns whether the last pack is incompatible while LODs render.
    public static boolean lastPackIncompatible() {
        return dhPresent && hasRenderingEnabled() && lastIncompatible;
    }

    // Returns the LOD far plane or a small default.
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

    // Returns the LOD near plane or a small default.
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

    // Returns the LOD render distance or the vanilla one in blocks.
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

    // Returns whether the frame draws LODs.
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

    // Returns whether LOD rendering is on.
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

    // Returns the LOD projection matrix.
    public static Matrix4f getProjection() {
        Matrix4f projection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
        if (!dhPresent) {
            return projection;
        }
        return new Matrix4f().setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(),
                getNearPlane(), getFarPlane());
    }

    // Draws the solid LODs into the shadow map.
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

    // Draws the LODs that wait for the deferred passes.
    public static void renderDeferredLods() {
        if (!dhPresent) {
            return;
        }
        try {
            renderDeferredLods.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Returns whether the pack draws LOD shadows.
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

    // Releases the bridge of the pipeline.
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

    // Returns the stored LOD depth texture.
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

    // Returns the LOD depth texture without translucents.
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

    // Returns the internal bridge.
    public Object getInstance() {
        return this.internal;
    }
}
