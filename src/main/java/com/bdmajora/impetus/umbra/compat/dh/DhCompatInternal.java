package com.bdmajora.impetus.umbra.compat.dh;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Distant Horizons state of one shader pipeline.
public final class DhCompatInternal {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // State used when no pack is active.
    public static final DhCompatInternal SHADERLESS = new DhCompatInternal(null, null, false);

    // Flag set when Distant Horizons is present.
    static boolean dhEnabled;

    // Handle that draws the LODs.
    private static MethodHandle clientApiRenderLods;
    // Handle that draws the deferred LODs.
    private static MethodHandle clientApiRenderDeferredLods;
    // Flag set once the handles were looked up.
    private static boolean clientApiResolved;

    // Handle that returns the block atlas.
    private static MethodHandle blockAtlasGetter;
    // Flag set once the atlas handle was looked up.
    private static boolean blockAtlasResolved;

    // Pipeline of the state.
    private final UmbraRenderingPipeline pipeline;
    // Flag set when the pack draws LOD shadows.
    private boolean shouldOverrideShadow;
    // Flag set when the pack draws LODs.
    private boolean shouldOverride;
    // Flag set when clouds are skipped.
    private boolean avoidClouds;
    // Solid program.
    private DhLodRenderProgram solidProgram;
    // Translucent program.
    private DhLodRenderProgram translucentProgram;
    // Shadow program.
    private DhLodRenderProgram shadowProgram;
    // Generic program.
    private DhGenericRenderProgram genericProgram;
    // Dh terrain framebuffer.
    private UmbraFramebuffer dhTerrainFramebuffer;
    // Dh water framebuffer.
    private UmbraFramebuffer dhWaterFramebuffer;
    // Dh generic framebuffer.
    private UmbraFramebuffer dhGenericFramebuffer;
    // Dh terrain framebuffer wrapper.
    private DhFramebufferWrapper dhTerrainFramebufferWrapper;
    // Dh shadow framebuffer wrapper.
    private DhFramebufferWrapper dhShadowFramebufferWrapper;
    // Shadow culling frustum.
    private DhShadowCullingFrustum shadowCullingFrustum;
    // Depth texture without translucents.
    private DepthTexture depthTexNoTranslucent;
    // Depth tex width.
    private int depthTexWidth;
    // Depth tex height.
    private int depthTexHeight;
    // Flag set when the translucent depth needs a copy.
    private boolean translucentDepthDirty;
    // Last depth texture attached or minus one.
    private int storedDepthTex = -1;
    // Flag set when the pack cannot draw Distant Horizons terrain.
    private boolean incompatible;

    // Creates a dh compat internal from its pipeline and pack and dh shadow enabled.
    public DhCompatInternal(UmbraRenderingPipeline pipeline, ShaderPack pack, boolean dhShadowEnabled) {
        this.pipeline = pipeline;
        if (pipeline == null || pack == null || DhApi.Delayed.configs == null
                || !DhApi.Delayed.configs.graphics().renderingEnabled().getValue()) {
            return;
        }

        Optional<ProgramSource> terrain = pack.getProgramSet().get(ProgramId.DhTerrain);
        Optional<ProgramSource> water = pack.getProgramSet().get(ProgramId.DhWater);
        if (!terrain.isPresent() && !water.isPresent()) {
            LOGGER.warn("[Umbra] No Distant Horizons program (dh_terrain) in this pack; LODs render with DH's own shaders");
            this.incompatible = true;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        try {
            createDepthTex(mc.displayWidth, mc.displayHeight);

            ProgramSource terrainSource = terrain.orElseGet(water::get);
            this.solidProgram = DhLodRenderProgram.create(pipeline, pack, terrainSource, false, false);
            this.dhTerrainFramebuffer = pipeline.createDhFramebuffer(this.solidProgram.getDrawBuffers(), false);
            this.dhTerrainFramebufferWrapper = new DhFramebufferWrapper(this.dhTerrainFramebuffer);

            Optional<ProgramSource> generic = pack.getProgramSet().get(ProgramId.DhGeneric);
            if (generic.isPresent()) {
                this.genericProgram = DhGenericRenderProgram.create(pipeline, pack, generic.get());
                this.dhGenericFramebuffer = pipeline.createDhFramebuffer(this.genericProgram.getDrawBuffers(), false);
            }

            if (water.isPresent()) {
                this.translucentProgram = DhLodRenderProgram.create(pipeline, pack, water.get(), false, true);
                this.dhWaterFramebuffer = pipeline.createDhFramebuffer(this.translucentProgram.getDrawBuffers(), true);
            }

            Optional<ProgramSource> shadow = pack.getProgramSet().get(ProgramId.DhShadow);
            UmbraShadowRenderer shadowRenderer = pipeline.getShadowRenderer();
            if (shadow.isPresent() && dhShadowEnabled && shadowRenderer != null) {
                this.shadowProgram = DhLodRenderProgram.create(pipeline, pack, shadow.get(), true, false);
                this.dhShadowFramebufferWrapper = new DhFramebufferWrapper(shadowRenderer.getFramebuffer());
                this.shadowCullingFrustum = new DhShadowCullingFrustum(shadowRenderer);
                this.shouldOverrideShadow = true;
            }
        } catch (RuntimeException e) {
            LOGGER.error("[Umbra] Failed to build this pack's Distant Horizons programs; LODs render with DH's own shaders", e);
            clear();
            this.incompatible = true;
            return;
        }

        if (this.translucentProgram == null) {
            this.translucentProgram = this.solidProgram;
        }
        Optional<String> dhClouds = pack.getProperties().getDhCloudMode();
        this.avoidClouds = dhClouds.isPresent()
                ? "off".equals(dhClouds.get())
                : "off".equals(pack.getProperties().getCloudMode().orElse(""));
        LOGGER.info("[Umbra] Distant Horizons LOD programs built: terrain '{}'{}{}{}", terrainSourceName(terrain, water),
                water.isPresent() ? ", water" : "", this.genericProgram != null ? ", generic" : "",
                this.shouldOverrideShadow ? ", shadow" : "");
        this.shouldOverride = true;
    }

    // Returns the name of the terrain source program.
    private static String terrainSourceName(Optional<ProgramSource> terrain, Optional<ProgramSource> water) {
        return terrain.isPresent() ? terrain.get().getName() : water.get().getName();
    }

    // Returns the dh block render distance.
    public static int getDhBlockRenderDistance() {
        if (DhApi.Delayed.configs == null || !dhEnabled) {
            return Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
        }
        return DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16;
    }

    // Returns the render distance.
    public static int getRenderDistance() {
        return getDhBlockRenderDistance();
    }

    // Returns the far plane.
    public static float getFarPlane() {
        if (DhApi.Delayed.configs == null) {
            return 0;
        }
        int lodChunkDist = DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue();
        int lodBlockDist = lodChunkDist * 16;
        return (float) ((lodBlockDist + 512) * Math.sqrt(2));
    }

    // Returns the near plane.
    public static float getNearPlane() {
        if (DhApi.Delayed.renderProxy == null) {
            return 0;
        }
        return DhApi.Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    // Returns whether rendering enabled.
    public static boolean isRenderingEnabled() {
        return dhEnabled;
    }

    // Returns whether LODs can draw this frame.
    public static boolean checkFrame() {
        if (DhApi.Delayed.configs == null) {
            return dhEnabled;
        }
        boolean enabled = DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
        if (dhEnabled != enabled && Umbra.isShaderPackInUse()) {
            dhEnabled = enabled;
            LOGGER.info("[Umbra] Distant Horizons rendering turned {}; reloading the shader pack", enabled ? "on" : "off");
            Umbra.loadCurrentShaderpack();
        }
        return dhEnabled;
    }

    // Draws the solid LOD shadows.
    public static void renderShadowSolid() {
        if (!dhEnabled || !resolveClientApi()) {
            return;
        }
        try {
            clientApiRenderLods.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Distant Horizons shadow pass failed", e);
        }
    }

    // Draws the deferred LODs.
    public static void renderDeferredLods() {
        if (!dhEnabled || !resolveClientApi()) {
            return;
        }
        try {
            clientApiRenderDeferredLods.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Distant Horizons deferred LOD pass failed", e);
        }
    }

    // Looks up the LOD draw handles and returns whether they exist.
    private static boolean resolveClientApi() {
        if (clientApiResolved) {
            return clientApiRenderLods != null;
        }
        clientApiResolved = true;
        try {
            Class<?> clientApi = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Object instance = lookup.findStaticGetter(clientApi, "INSTANCE", clientApi).invoke();
            clientApiRenderLods = lookup.findVirtual(clientApi, "renderLods", MethodType.methodType(void.class)).bindTo(instance);
            clientApiRenderDeferredLods = lookup.findVirtual(clientApi, "renderDeferredLodsForShaders", MethodType.methodType(void.class)).bindTo(instance);
            return true;
        } catch (Throwable e) {
            clientApiRenderLods = null;
            clientApiRenderDeferredLods = null;
            LOGGER.error("[Umbra] Could not bind Distant Horizons' render entry points; LODs will cast no shadows and LOD water will not draw", e);
            return false;
        }
    }

    // Returns the block atlas texture id.
    static int getBlockAtlasTextureId() {
        if (!blockAtlasResolved) {
            blockAtlasResolved = true;
            try {
                blockAtlasGetter = MethodHandles.lookup().findVirtual(
                        Class.forName("com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy"),
                        "getDhBlockRatioAtlasTextureGlId", MethodType.methodType(DhApiResult.class));
            } catch (Throwable e) {
                blockAtlasGetter = null;
                LOGGER.info("[Umbra] This Distant Horizons API has no block atlas getter; dh_sampleTexture reads the block atlas instead");
            }
        }
        if (blockAtlasGetter == null || DhApi.Delayed.renderProxy == null) {
            return -1;
        }
        try {
            @SuppressWarnings("unchecked")
            DhApiResult<Integer> result = (DhApiResult<Integer>) blockAtlasGetter.invoke(DhApi.Delayed.renderProxy);
            return result.success && result.payload != null ? result.payload : -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    // Returns whether the pack cannot draw LODs.
    public boolean incompatiblePack() {
        return this.incompatible;
    }

    // Returns whether override.
    public boolean shouldOverride() {
        return this.shouldOverride;
    }

    // Returns whether override shadow.
    public boolean shouldOverrideShadow() {
        return this.shouldOverrideShadow;
    }

    // Attaches a new depth texture to the LOD framebuffers.
    public void reconnectDHTextures(int depthTex, int width, int height) {
        if (width > 0 && height > 0 && (width != this.depthTexWidth || height != this.depthTexHeight)) {
            createDepthTex(width, height);
        }
        if (this.storedDepthTex != depthTex && this.dhTerrainFramebuffer != null) {
            this.storedDepthTex = depthTex;
            this.dhTerrainFramebuffer.addDepthAttachment(depthTex);
            if (this.dhWaterFramebuffer != null) {
                this.dhWaterFramebuffer.addDepthAttachment(depthTex);
            }
            if (this.dhGenericFramebuffer != null) {
                this.dhGenericFramebuffer.addDepthAttachment(depthTex);
            }
        }
    }

    // Creates the depth texture without translucents.
    private void createDepthTex(int width, int height) {
        if (this.depthTexNoTranslucent != null) {
            this.depthTexNoTranslucent.destroy();
            this.depthTexNoTranslucent = null;
        }
        this.translucentDepthDirty = true;
        this.depthTexWidth = width;
        this.depthTexHeight = height;
        this.depthTexNoTranslucent = new DepthTexture(width, height,
                GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
    }

    // Deletes the framebuffers and programs.
    public void clear() {
        if (this.solidProgram != null) {
            this.solidProgram.free();
        }
        if (this.translucentProgram != null && this.translucentProgram != this.solidProgram) {
            this.translucentProgram.free();
        }
        if (this.shadowProgram != null) {
            this.shadowProgram.free();
        }
        if (this.genericProgram != null) {
            this.genericProgram.free();
        }
        this.solidProgram = null;
        this.translucentProgram = null;
        this.shadowProgram = null;
        this.shouldOverrideShadow = false;
        this.shouldOverride = false;
        if (this.dhTerrainFramebuffer != null) {
            this.dhTerrainFramebuffer.destroy();
            this.dhTerrainFramebuffer = null;
        }
        if (this.dhWaterFramebuffer != null) {
            this.dhWaterFramebuffer.destroy();
            this.dhWaterFramebuffer = null;
        }
        if (this.dhGenericFramebuffer != null) {
            this.dhGenericFramebuffer.destroy();
            this.dhGenericFramebuffer = null;
        }
        if (this.depthTexNoTranslucent != null) {
            this.depthTexNoTranslucent.destroy();
            this.depthTexNoTranslucent = null;
        }
        this.storedDepthTex = -1;
        this.translucentDepthDirty = true;

        if (this.dhTerrainFramebufferWrapper != null) {
            DhApi.overrides.unbind(IDhApiFramebuffer.class, this.dhTerrainFramebufferWrapper);
        }
        if (this.dhShadowFramebufferWrapper != null) {
            DhApi.overrides.unbind(IDhApiFramebuffer.class, this.dhShadowFramebufferWrapper);
        }
        if (this.shadowCullingFrustum != null) {
            DhApi.overrides.unbind(IDhApiShadowCullingFrustum.class, this.shadowCullingFrustum);
        }
        if (this.genericProgram != null) {
            DhApi.overrides.unbind(IDhApiGenericObjectShaderProgram.class, this.genericProgram);
        }
        this.genericProgram = null;
        this.dhTerrainFramebufferWrapper = null;
        this.dhShadowFramebufferWrapper = null;
        this.shadowCullingFrustum = null;
    }

    // Returns the solid shader.
    public DhLodRenderProgram getSolidShader() {
        return this.solidProgram;
    }

    // Returns the translucent shader.
    public DhLodRenderProgram getTranslucentShader() {
        return this.translucentProgram == null ? this.solidProgram : this.translucentProgram;
    }

    // Returns the shadow shader.
    public DhLodRenderProgram getShadowShader() {
        return this.shadowProgram;
    }

    // Returns the generic shader.
    public IDhApiGenericObjectShaderProgram getGenericShader() {
        return this.genericProgram;
    }

    // Returns the solid fb wrapper.
    public DhFramebufferWrapper getSolidFBWrapper() {
        return this.dhTerrainFramebufferWrapper;
    }

    // Returns the shadow fb wrapper.
    public DhFramebufferWrapper getShadowFBWrapper() {
        return this.dhShadowFramebufferWrapper;
    }

    // Returns the shadow culling frustum.
    public DhShadowCullingFrustum getShadowCullingFrustum() {
        return this.shadowCullingFrustum;
    }

    // Returns the solid fb.
    public UmbraFramebuffer getSolidFB() {
        return this.dhTerrainFramebuffer;
    }

    // Returns the translucent fb.
    public UmbraFramebuffer getTranslucentFB() {
        return this.dhWaterFramebuffer;
    }

    // Returns the generic fb.
    public UmbraFramebuffer getGenericFB() {
        return this.dhGenericFramebuffer;
    }

    // Returns the pipeline.
    public UmbraRenderingPipeline getPipeline() {
        return this.pipeline;
    }

    // Returns the stored depth tex.
    public int getStoredDepthTex() {
        return this.storedDepthTex;
    }

    // Returns the depth tex no translucent.
    public int getDepthTexNoTranslucent() {
        return this.depthTexNoTranslucent == null ? 0 : this.depthTexNoTranslucent.getTextureId();
    }

    // Returns whether clouds are skipped.
    public boolean avoidRenderingClouds() {
        return this.avoidClouds;
    }

    // Copies the translucents.
    public void copyTranslucents(int width, int height) {
        if (this.dhTerrainFramebuffer == null || this.depthTexNoTranslucent == null || this.storedDepthTex == -1) {
            return;
        }
        this.translucentDepthDirty = false;
        int w = Math.min(width, this.depthTexWidth);
        int h = Math.min(height, this.depthTexHeight);
        this.dhTerrainFramebuffer.bindAsReadBuffer();
        GlTextureUnits.selectScratch(DEPTH_COPY_SCRATCH_UNIT);
        try {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.depthTexNoTranslucent.getTextureId());
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GlTextureUnits.releaseScratch();
        }
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // Texture unit used for the depth copy.
    private static final int DEPTH_COPY_SCRATCH_UNIT = 33;
}
