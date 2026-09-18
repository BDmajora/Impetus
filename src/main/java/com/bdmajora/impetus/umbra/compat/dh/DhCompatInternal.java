package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL30;
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

// One pipeline's Distant Horizons state (Iris's DHCompatInternal): the pack's dh_terrain/dh_water/dh_shadow/dh_generic programs compiled onto DH's vertex formats, the framebuffers the LOD passes draw into (the gbuffer colour targets with DH's OWN depth texture, so LOD depth lands in dhDepthTex0 and never mixes with depthtex0), and the pre-translucent depth copy that is dhDepthTex1. Only ever loaded through DhCompat's method handles, since it imports the DH API
public final class DhCompatInternal {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // The stand-in while no pipeline exists (shaders off): every override flag false, so DH renders exactly as without Impetus
    public static final DhCompatInternal SHADERLESS = new DhCompatInternal(null, null, false);

    // DH's rendering toggle as of the last checkFrame; read by the macro environment, so it is also set from DH's init event before the first pack load
    static boolean dhEnabled;

    // DH's internal ClientApi render entry points, the same calls DH's own RenderGlobal mixin makes; needed because Impetus's shadow pass draws terrain straight from its world renderer, never through RenderGlobal, so DH's hook never sees the shadow pass. Resolved on first use, null (with one log line) when the internals moved
    private static MethodHandle clientApiRenderLods;
    private static MethodHandle clientApiRenderDeferredLods;
    private static boolean clientApiResolved;

    // DH's block texture atlas getter, API 7.1+; textured LODs sample it through dh_sampleTexture, and without the getter the pack's helper reads whatever is on unit 0
    private static MethodHandle blockAtlasGetter;
    private static boolean blockAtlasResolved;

    private final UmbraRenderingPipeline pipeline;
    private boolean shouldOverrideShadow;
    private boolean shouldOverride;
    private boolean avoidClouds;
    private DhLodRenderProgram solidProgram;
    private DhLodRenderProgram translucentProgram;
    private DhLodRenderProgram shadowProgram;
    private DhGenericRenderProgram genericProgram;
    private UmbraFramebuffer dhTerrainFramebuffer;
    private UmbraFramebuffer dhWaterFramebuffer;
    private UmbraFramebuffer dhGenericFramebuffer;
    private DhFramebufferWrapper dhTerrainFramebufferWrapper;
    private DhFramebufferWrapper dhShadowFramebufferWrapper;
    private DhShadowCullingFrustum shadowCullingFrustum;
    private DepthTexture depthTexNoTranslucent;
    private int depthTexWidth;
    private int depthTexHeight;
    private boolean translucentDepthDirty;
    private int storedDepthTex = -1;
    private boolean incompatible;

    // Compiles the pack's DH programs against this pipeline's gbuffer layout; a pack with neither dh_terrain nor dh_water is "incompatible" (DH keeps rendering with its own shaders), and dhShadowEnabled is the pack's dhShadow.enabled directive
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
        // Unlike Iris, a dh_* program that fails to build does not fail the pack: the DH bridge is the newest transform here, and a pack is more useful with LODs in DH's own shading than not at all
        try {
            createDepthTex(mc.displayWidth, mc.displayHeight);

            // dh_water falls back to dh_terrain in the ProgramSet, so at least one of the two resolved above
            ProgramSource terrainSource = terrain.orElseGet(water::get);
            this.solidProgram = DhLodRenderProgram.create(pipeline, pack, terrainSource, false, false);
            this.dhTerrainFramebuffer = pipeline.createDhFramebuffer(this.solidProgram.getDrawBuffers(), false);
            this.dhTerrainFramebufferWrapper = new DhFramebufferWrapper(this.dhTerrainFramebuffer);

            // dh_generic falls back to dh_terrain too, so DH's beacon beams and clouds always get a pack program once dh_terrain exists
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
                // LOD shadows draw straight into the shadow map: the shadow pass's own framebuffer, whose depth is shadowtex0
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
        // dhClouds=off, or clouds=off with dhClouds unstated: the pack draws its own sky, so DH's cloud boxes must not land in the gbuffer either (Iris's avoidRenderingClouds)
        Optional<String> dhClouds = pack.getProperties().getDhCloudMode();
        this.avoidClouds = dhClouds.isPresent()
                ? "off".equals(dhClouds.get())
                : "off".equals(pack.getProperties().getCloudMode().orElse(""));
        LOGGER.info("[Umbra] Distant Horizons LOD programs built: terrain '{}'{}{}{}", terrainSourceName(terrain, water),
                water.isPresent() ? ", water" : "", this.genericProgram != null ? ", generic" : "",
                this.shouldOverrideShadow ? ", shadow" : "");
        this.shouldOverride = true;
    }

    private static String terrainSourceName(Optional<ProgramSource> terrain, Optional<ProgramSource> water) {
        return terrain.isPresent() ? terrain.get().getName() : water.get().getName();
    }

    // ---------------------------------------------------------------- per-frame statics (read by DhCompat)

    // DH's LOD render distance in blocks; the player's render distance in blocks before DH finishes setup or while it is not rendering, like Iris
    public static int getDhBlockRenderDistance() {
        if (DhApi.Delayed.configs == null || !dhEnabled) {
            return Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
        }
        return DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16;
    }

    public static int getRenderDistance() {
        return getDhBlockRenderDistance();
    }

    // DH's far plane: the LOD distance plus a margin, times sqrt 2 so the corners of the square LOD area are not clipped (DH's RenderUtil)
    public static float getFarPlane() {
        if (DhApi.Delayed.configs == null) {
            return 0;
        }
        int lodChunkDist = DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue();
        int lodBlockDist = lodChunkDist * 16;
        return (float) ((lodBlockDist + 512) * Math.sqrt(2));
    }

    // DH's near plane, which moves with the vanilla render distance so the LODs start where the chunks end
    public static float getNearPlane() {
        if (DhApi.Delayed.renderProxy == null) {
            return 0;
        }
        return DhApi.Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    // DH's rendering toggle as last observed, without side effects
    public static boolean isRenderingEnabled() {
        return dhEnabled;
    }

    // Frame hook: when DH's rendering toggle changed since the pack loaded, reload the pack so DISTANT_HORIZONS and the LOD programs follow it (the pack's define environment is baked at load); returns whether DH renders
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

    // Draws the opaque LODs for the shadow pass through DH's own entry point; DH's render state (matrices, level, partial ticks) is whatever its RenderGlobal hook captured last, which the shadow pass never depends on since the LOD program and frustum overrides take the shadow matrices from CapturedRenderingState
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

    // Same for the deferred translucent LODs
    public static void renderShadowTranslucent() {
        if (!dhEnabled || !resolveClientApi()) {
            return;
        }
        try {
            clientApiRenderDeferredLods.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Distant Horizons shadow pass failed", e);
        }
    }

    // Binds ClientApi.INSTANCE.renderLods()/renderDeferredLodsForShaders() once; false (after one log line) when DH's internals no longer match, in which case LODs simply cast no shadows
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
            LOGGER.error("[Umbra] Could not bind Distant Horizons' render entry points; LODs will not cast shadows", e);
            return false;
        }
    }

    // DH's block texture atlas id for dh_sampleTexture, or -1 without API 7.1's getter or before DH built it
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

    // ---------------------------------------------------------------- per-pipeline state

    public boolean incompatiblePack() {
        return this.incompatible;
    }

    public boolean shouldOverride() {
        return this.shouldOverride;
    }

    public boolean shouldOverrideShadow() {
        return this.shouldOverrideShadow;
    }

    // Attaches DH's current depth texture to the LOD framebuffers, and re-sizes the pre-translucent copy to DH's texture size; called before every LOD frame since DH recreates the texture on resize (with a fresh id, deliberately)
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

    // (Re)allocates dhDepthTex1 at DH's texture size
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

    // Frees everything this pack built; pipeline teardown, render thread
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

        // The overrides are rebound every LOD frame, but a torn-down pipeline must not leave dangling ones for DH to call
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

    public DhLodRenderProgram getSolidShader() {
        return this.solidProgram;
    }

    public DhLodRenderProgram getTranslucentShader() {
        return this.translucentProgram == null ? this.solidProgram : this.translucentProgram;
    }

    public DhLodRenderProgram getShadowShader() {
        return this.shadowProgram;
    }

    public IDhApiGenericObjectShaderProgram getGenericShader() {
        return this.genericProgram;
    }

    public DhFramebufferWrapper getSolidFBWrapper() {
        return this.dhTerrainFramebufferWrapper;
    }

    public DhFramebufferWrapper getShadowFBWrapper() {
        return this.dhShadowFramebufferWrapper;
    }

    public DhShadowCullingFrustum getShadowCullingFrustum() {
        return this.shadowCullingFrustum;
    }

    public UmbraFramebuffer getSolidFB() {
        return this.dhTerrainFramebuffer;
    }

    public UmbraFramebuffer getTranslucentFB() {
        return this.dhWaterFramebuffer;
    }

    public UmbraFramebuffer getGenericFB() {
        return this.dhGenericFramebuffer;
    }

    public UmbraRenderingPipeline getPipeline() {
        return this.pipeline;
    }

    public int getStoredDepthTex() {
        return this.storedDepthTex;
    }

    public int getDepthTexNoTranslucent() {
        return this.depthTexNoTranslucent == null ? 0 : this.depthTexNoTranslucent.getTextureId();
    }

    public boolean avoidRenderingClouds() {
        return this.avoidClouds;
    }

    // Snapshots DH's depth after its opaque pass into dhDepthTex1, right before the translucent LODs draw over it
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
        // The read binding must not linger on DH's framebuffer, or the next blit reads LOD depth
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // A raw unit above every sampler allocation for the depth copy bind, the pipeline's own choice for the same job
    private static final int DEPTH_COPY_SCRATCH_UNIT = 33;
}
