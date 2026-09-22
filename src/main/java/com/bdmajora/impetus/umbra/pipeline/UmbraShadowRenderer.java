package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;
import com.bdmajora.impetus.lwjgl.GL14;
import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.lwjgl.GL30;
import com.bdmajora.impetus.lwjgl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Second render of the world from the sun/moon's POV before the gbuffer pass samples it, in Umbra's ShadowRenderer order: solid+cutout terrain, fixed-function entities/block entities, depth copied to shadowtex1, then translucent terrain into shadowcolor0/1 and shadowtex0; the ortho shadow camera is snapped to shadowIntervalSize so texels do not swim
public class UmbraShadowRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    public static final float DEFAULT_NEAR_PLANE = -100.05f;
    public static final float DEFAULT_FAR_PLANE = 156.0f;
    public static final float DEFAULT_INTERVAL_SIZE = 2.0f;

    // Fixed-function matrix modes (GlStateManager.matrixMode takes the raw GL enum).
    private static final int GL_MODELVIEW_MODE = 0x1700;
    private static final int GL_PROJECTION_MODE = 0x1701;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_DEPTH_FUNC = 0x0B74;

    // True while the shadow pass is drawing; consulted by the matrix, program and draw-buffer seams, which answer differently for the shadow and camera passes
    private static boolean shadowPassActive;

    private final int resolution;
    private final float halfPlaneLength;
    private final float nearPlane;
    private final float farPlane;
    private final float sunPathRotation;
    private final float intervalSize;
    private final Float shadowMapFov;

    // shadowtex0: every shadow caster, translucents included
    private final DepthTexture depthTexture;
    // shadowtex1: a copy of shadowtex0 taken just BEFORE translucent shadow geometry draws, so the difference is exactly "occluded only by glass and water", which is how packs tint light shafts
    private final DepthTexture depthTextureNoTranslucents;
    // What the pack permits this pass to draw: shadowTerrain, shadowEntities, shadowBlockEntities and friends
    private final ShadowContentSettings content;
    // The pack's voxelDistance, the safe-zone radius under shadow.culling = reversed; 0 when undeclared, degenerating that mode to plain advanced culling
    private final float voxelDistance;
    // Whether the pack voxelizes during the shadow pass, inferred like Iris from a geometry stage or custom images rather than a declaration
    private final boolean packVoxelizes;
    // shadowDistance * shadowDistanceRenderMul, the distance the pass culls against, deliberately not the distance the projection covers
    private final float cullDistance;
    private final int colorTexture0;
    private final int colorTexture1;
    private final UmbraFramebuffer framebuffer;
    private final boolean[] hardwareFiltering;
    private final boolean[] mipmapDepth;
    private final boolean[] nearestDepth;
    private final boolean separateHardwareSamplers;
    // Both shadowcolor attachments, for the frame-start clear — only 0 and 1 exist on this version
    private static final int[] CLEAR_MASK = {0, 1};
    // Texture unit for one-off raw work (creating shadow textures, mipmaps, depth copies), above CACHED_UNITS and clear of every pipeline unit so it never rewrites a tracked slot; always pair selectScratch with releaseScratch
    private static final int TEXTURE_SETUP_UNIT = 31;
    // The pack's shadow DRAWBUFFERS mask for the geometry draws, sanitised down to shadowcolor0 and 1, the only two that exist
    private final int[] shadowDrawBuffers;
    private final Runnable shaderPackResourceRestorer;
    // The FIXED-FUNCTION flavour of the pack's shadow program for entities and block entities, a separate compile since they draw immediate-mode and the Impetus-format terrain program cannot consume that; null when it failed, and entity shadows are then skipped
    private final GbufferPrograms.Entry entityShadowProgram;

    // shadow_block's flavour for the block-entity loop (Iris gives block entities their own shadow program); when the pack ships neither, both resolve onto the same `shadow` source and this holds the same Entry, which blockEntityProgramShared detects so teardown does not free it twice
    private final GbufferPrograms.Entry blockEntityShadowProgram;

    // Whether the two program fields hold the SAME object, which teardown checks before destroying either
    private final boolean blockEntityProgramShared;

    private final FloatBuffer matrixBuffer =
            ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    private int failureCount;
    private boolean failed;
    // Monotonic frame tag for the shadow pass's own render-list graph updates, independent of the camera pass's counter so neither satisfies the other's dirty check
    private int shadowListFrame;
    private boolean destroyed;

    private final Matrix4f shadowModelView = new Matrix4f();
    private final Matrix4f shadowProjection = new Matrix4f();
    // The section filter built for the current pass, kept for the DH compat's LOD culling
    private com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum cullingFrustum;

    // shadowSource compiles into the fixed-function entity flavour; samplerUnits already carries the gbuffers-stage overrides since the shadow pass belongs to that stage; hardwareFiltering, mipmapDepth and nearestDepth are PER TEXTURE (0 = shadowtex0, 1 = shadowtex1), and separateHardwareSamplers moves compare onto the *HW aliases
    public UmbraShadowRenderer(int resolution, float shadowDistance, float nearPlane, float farPlane,
                              float intervalSize, Float shadowMapFov, float sunPathRotation,
                              ProgramSource shadowSource,
                              ProgramSource shadowEntitiesSource, ProgramSource shadowBlockSource,
                              Map<String, Integer> samplerUnits,
                              Map<String, String> shaderDefines, boolean[] hardwareFiltering,
                              boolean[] mipmapDepth, boolean[] nearestDepth, boolean separateHardwareSamplers,
                              Runnable shaderPackResourceRestorer, ShadowContentSettings content,
                              float voxelDistance, float cullDistance, boolean packVoxelizes) {
        this.resolution = resolution;
        this.halfPlaneLength = shadowDistance;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.intervalSize = intervalSize;
        this.shadowMapFov = shadowMapFov;
        this.sunPathRotation = sunPathRotation;
        this.shaderPackResourceRestorer = shaderPackResourceRestorer;
        this.content = content;
        this.voxelDistance = voxelDistance;
        this.cullDistance = cullDistance;
        this.packVoxelizes = packVoxelizes;
        this.hardwareFiltering = hardwareFiltering.clone();
        this.mipmapDepth = mipmapDepth.clone();
        this.nearestDepth = nearestDepth.clone();
        this.separateHardwareSamplers = separateHardwareSamplers;

        this.depthTexture = createShadowDepthTexture(resolution, this.hardwareFiltering[0],
                this.mipmapDepth[0], this.nearestDepth[0], this.separateHardwareSamplers);
        this.depthTextureNoTranslucents = createShadowDepthTexture(resolution, this.hardwareFiltering[1],
                this.mipmapDepth[1], this.nearestDepth[1], this.separateHardwareSamplers);

        // shadowcolor0/1: the shadow program's color outputs (white where nothing draws = untinted shadows).
        this.colorTexture0 = createShadowColorTexture(resolution);
        this.colorTexture1 = createShadowColorTexture(resolution);

        this.framebuffer = new UmbraFramebuffer();
        this.framebuffer.addColorAttachment(0, this.colorTexture0);
        this.framebuffer.addColorAttachment(1, this.colorTexture1);
        this.framebuffer.addDepthAttachment(this.depthTexture.getTextureId());
        this.framebuffer.drawBuffers(CLEAR_MASK);

        // The shadow program's DRAWBUFFERS (0, or 01 when it also writes shadowcolor1); indices past the two shadowcolor attachments would reference missing images, so they are dropped
        this.shadowDrawBuffers = shadowSource.getFragmentSource()
                .map(source -> DrawBuffers.parseActive(source, shaderDefines))
                .map(buffers -> DrawBuffers.sanitize(buffers, 2))
                .orElse(new int[]{0});

        // shadow_entities / shadow_block, both falling back to plain `shadow` through the ProgramSet chain; for a pack shipping only `shadow` these are the identical ProgramSource and the second compile is skipped
        ProgramSource entitiesSource = shadowEntitiesSource != null ? shadowEntitiesSource : shadowSource;
        ProgramSource blockSource = shadowBlockSource != null ? shadowBlockSource : shadowSource;

        this.entityShadowProgram = GbufferPrograms.compile(entitiesSource, shaderDefines, samplerUnits);
        if (this.entityShadowProgram == null) {
            LOGGER.warn("[Umbra] Fixed-function shadow program failed to compile; entity shadows disabled");
        }
        this.blockEntityProgramShared = blockSource == entitiesSource;
        this.blockEntityShadowProgram = this.blockEntityProgramShared
                ? this.entityShadowProgram
                : GbufferPrograms.compile(blockSource, shaderDefines, samplerUnits);
    }

    private static DepthTexture createShadowDepthTexture(int resolution, boolean hardwareFiltering,
                                                         boolean mipmap, boolean nearest,
                                                         boolean separateHardwareSamplers) {
        DepthTexture texture = new DepthTexture(resolution, resolution,
                GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        // Configure on a scratch unit: a raw bind on the selected unit 0 changes the real binding without updating GlStateManager's slot record, and the trailing unbind leaves the cache asserting a texture no longer there (see GlTextureUnits)
        GlTextureUnits.selectScratch(TEXTURE_SETUP_UNIT);
        try {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
            if (hardwareFiltering && !separateHardwareSamplers) {
                LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
            }
            LWJGL.glTexParameteriv(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_RGBA,
                    new int[]{GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE});
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, shadowMinFilter(mipmap, nearest));
            LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                    nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GlTextureUnits.releaseScratch();
        }
        return texture;
    }

    // GL filter constant from the pack's mipmap and nearest flags
    private static int shadowMinFilter(boolean mipmap, boolean nearest) {
        if (mipmap) {
            return nearest ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
        }
        return nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    // The internal format of shadowcolor0 and 1, which glBindImageTexture needs to expose them as shadowcolorimgN
    public static final int SHADOW_COLOR_INTERNAL_FORMAT = GL11.GL_RGBA8;

    // One shadowcolor texture at the shadow resolution
    private static int createShadowColorTexture(int resolution) {
        int texture = LWJGL.glGenTextures();
        // Scratch unit, for the same reason as createShadowDepthTexture.
        GlTextureUnits.selectScratch(TEXTURE_SETUP_UNIT);
        try {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            TextureParameters.setFilter2D(GL11.GL_LINEAR);
            LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, resolution, resolution, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GlTextureUnits.releaseScratch();
        }
        return texture;
    }

    // Whether the shadow pass is currently rendering, checked by mixins that must skip work
    public static boolean isShadowPass() {
        return shadowPassActive;
    }

    // shadowtex0
    public int getDepthTextureId() {
        return this.depthTexture.getTextureId();
    }

    // shadowtex1
    public int getDepthTextureNoTranslucentsId() {
        return this.depthTextureNoTranslucents.getTextureId();
    }

    // shadowcolor0
    public int getColorTextureId() {
        return this.colorTexture0;
    }

    // shadowcolor1
    public int getColorTexture1Id() {
        return this.colorTexture1;
    }

    // Shadow map size from the pack
    public int getResolution() {
        return this.resolution;
    }

    // The shadow framebuffer (shadowcolor0/1 + shadowtex0), for the Distant Horizons compat to draw LODs into during the pass
    public UmbraFramebuffer getFramebuffer() {
        return this.framebuffer;
    }

    // The section filter of the current (or last) shadow pass, camera-relative; the DH compat culls its LOD columns with the same one
    public com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum getCullingFrustum() {
        return this.cullingFrustum;
    }

    // Whether the active pack draws Distant Horizons LODs into the shadow map this pass
    private static boolean dhShadowsActive() {
        UmbraRenderingPipeline pipeline = com.bdmajora.impetus.umbra.Umbra.getRenderingPipeline();
        return pipeline != null && pipeline.getDhCompat() != null && pipeline.getDhCompat().shouldRenderShadows()
                && DhCompat.hasRenderingEnabled();
    }

    // Draws DH's LODs into the shadow map through DH's own renderer, restoring the pass's GL state it changes: DH enables face culling and (for water) blending, binds its VAO/buffers and a program, and rebinds texture units 0 and 1
    private void renderDhShadows(boolean translucent) {
        if (!dhShadowsActive()) {
            return;
        }
        try {
            if (translucent) {
                DhCompat.renderShadowTranslucent();
            } else {
                DhCompat.renderShadowSolid();
            }
        } finally {
            LWJGL.glUseProgram(0);
            LWJGL.glBindVertexArray(0);
            LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            this.framebuffer.bind();
            LWJGL.glViewport(0, 0, this.resolution, this.resolution);
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlTextureUnits.resetToUnit0();
        }
    }

    // Renders the shadow map, called right AFTER the camera matrices are captured and BEFORE any gbuffer geometry so the deferred chain samples a finished map; leaves the shadow framebuffer and viewport bound for the caller to rebind
    public void render() {
        if (this.failed || this.destroyed) {
            return;
        }
        ImpetusWorldRenderer worldRenderer = ImpetusWorldRenderer.instanceNullable();
        if (worldRenderer == null) {
            return;
        }

        computeMatrices();
        CapturedRenderingState.INSTANCE.setShadowModelView(this.shadowModelView);
        CapturedRenderingState.INSTANCE.setShadowProjection(this.shadowProjection);

        boolean cullWasEnabled = false;
        int previousDepthFunc = GL11.GL_LEQUAL;
        boolean restoreGlState = false;

        try {
            shadowPassActive = true;
            Minecraft mc = Minecraft.getMinecraft();
            this.framebuffer.bind();
            cullWasEnabled = LWJGL.glGetBoolean(GL_CULL_FACE);
            previousDepthFunc = LWJGL.glGetInteger(GL_DEPTH_FUNC);
            restoreGlState = true;
            LWJGL.glViewport(0, 0, this.resolution, this.resolution);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.clearDepth(1.0D);
            GlStateManager.disableCull();
            // shadowcolor clears to white (no tint); GlStateManager keeps the vanilla clear-color cache coherent.
            this.framebuffer.drawBuffers(CLEAR_MASK);
            GlStateManager.clearColor(1.0f, 1.0f, 1.0f, 1.0f);
            LWJGL.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            this.framebuffer.drawBuffers(this.shadowDrawBuffers);
            // Put the clear colour back immediately: it is GLOBAL, cached by GlStateManager, and vanilla sets it once per frame BEFORE the "frustum" section this hooks, so leaked white survived to the next frame's clear of the DEFAULT framebuffer and flashed whenever the blit was delayed (screenshots); the mixin sets the fog colour just before calling, alpha 0 like vanilla
            org.joml.Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
            GlStateManager.clearColor(fog.x, fog.y, fog.z, 0.0f);

            GlStateManager.disableBlend();
            // The shadow program alpha-tests foliage against the block atlas; make sure unit 0 holds it, since this runs before vanilla's own "prepareterrain" bind
            bindBlockAtlas(mc);

            Vector3d camera = CapturedRenderingState.INSTANCE.getCameraPosition();

            // Umbra `shadow.culling = reversed` parity: build the DEDICATED shadow render list of every built section in range with no frustum or occlusion culling (isInShadowPass() routes updates and draws onto the shadow RenderListManager); reusing the culled main lists made cave sections blink in the pack's voxelization and the floodfill strobed forever
            RenderDevice.enterManagedCode();
            try {
                // `shadow.culling`: `off` keeps every loaded section, otherwise a box of the pack's shadowDistance (Umbra BoxCuller), position-only so the section set stays frame-stable; the advanced/safe-zone frustums derive from THIS frame's matrices and are rebuilt every pass. With DH LODs in the map the view frustum takes DH's far plane (Iris does the same), or the LODs past the vanilla far plane would never cast
                boolean dhShadows = dhShadowsActive();
                this.cullingFrustum = com.bdmajora.impetus.umbra.pipeline.shadow.ShadowFrustums.create(
                        this.content.getCulling(), this.cullDistance, this.voxelDistance,
                        this.packVoxelizes,
                        Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16,
                        this.sunPathRotation,
                        dhShadows ? DhCompat.getProjection() : CapturedRenderingState.INSTANCE.getGbufferProjection());
                worldRenderer.setupTerrain(
                        new com.bdmajora.impetus.engine.impl.render.viewport.Viewport(
                                this.cullingFrustum,
                                new Vector3d(camera.x, camera.y, camera.z)),
                        ImpetusWorldRenderer.captureCameraState(mc.getRenderPartialTicks()),
                        ++this.shadowListFrame, false, false);
            } finally {
                RenderDevice.exitManagedCode();
            }

            // 1) Solid + cutout terrain (Umbra order). Impetus requires draws inside its managed-device scope.
            if (this.content.shouldRenderTerrain()) {
                RenderDevice.enterManagedCode();
                try {
                    worldRenderer.drawChunkLayer(BlockRenderLayer.SOLID, camera.x, camera.y, camera.z);
                    worldRenderer.drawChunkLayer(BlockRenderLayer.CUTOUT_MIPPED, camera.x, camera.y, camera.z);
                    worldRenderer.drawChunkLayer(BlockRenderLayer.CUTOUT, camera.x, camera.y, camera.z);
                } finally {
                    RenderDevice.exitManagedCode();
                }
                // 1b) Distant Horizons' opaque LODs, where Iris's shadow pass reaches them through vanilla's chunk-layer hook
                renderDhShadows(false);
            }

            // 2) Entities + block entities, fixed-function under the shadow matrices.
            renderEntityShadows(mc, camera);

            // 3) shadowtex1 = depth without translucents (Umbra copyPreTranslucentDepth).
            copyDepthTo(this.depthTextureNoTranslucents);

            // 4) Translucent terrain writes the shadow tint into shadowcolor0/1 and depth into shadowtex0, UNBLENDED like OptiFine (disableBlend before the draw) and Umbra: blending mixed every texel toward the white clear weighted by Complementary's alpha, which is its light-shaft HEIGHT not opacity, and pow2(rgb * 4.0) then went ~16x overbright wherever glass shadowed ground (8.4% of the map), the "sunlight leaking through terrain"
            if (this.content.shouldRenderTranslucent() && this.content.shouldRenderTerrain()) {
                bindBlockAtlas(mc);
                GlStateManager.disableBlend();
                RenderDevice.enterManagedCode();
                try {
                    worldRenderer.drawChunkLayer(BlockRenderLayer.TRANSLUCENT, camera.x, camera.y, camera.z);
                } finally {
                    RenderDevice.exitManagedCode();
                }
                GlStateManager.disableBlend();
                // 4b) DH's deferred translucent LODs (water) into shadowtex0/shadowcolor, unblended like the terrain above
                renderDhShadows(true);
            }
            generateMipmaps();
        } catch (Throwable t) {
            // The first frames can race renderer setup (no viewport or render lists yet), so only give up for good after repeated failures
            if (++this.failureCount >= 3) {
                this.failed = true;
                LOGGER.error("[Umbra] Shadow pass failed repeatedly; disabling shadows for this pack", t);
            }
        } finally {
            if (restoreGlState) {
                if (cullWasEnabled) {
                    GlStateManager.enableCull();
                } else {
                    GlStateManager.disableCull();
                }
                GlStateManager.depthFunc(previousDepthFunc);
                GlStateManager.depthMask(true);
            }
            shadowPassActive = false;
        }
    }

    // Terrain in the shadow pass still samples the atlas for cutouts
    private static void bindBlockAtlas(Minecraft mc) {
        mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    // Mips on the depth textures the pack asked for
    private void generateMipmaps() {
        generateDepthMipmap(this.depthTexture, this.mipmapDepth[0], this.nearestDepth[0]);
        generateDepthMipmap(this.depthTextureNoTranslucents, this.mipmapDepth[1], this.nearestDepth[1]);
        GlTextureUnits.resetToUnit0();
        this.shaderPackResourceRestorer.run();
    }

    // One depth texture's mip chain and filter
    private static void generateDepthMipmap(DepthTexture texture, boolean mipmap, boolean nearest) {
        if (!mipmap) {
            return;
        }
        GlTextureUnits.selectScratch(TEXTURE_SETUP_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
        LWJGL.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, shadowMinFilter(true, nearest));
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Binds one fixed-function shadow program and refreshes its uniforms; the resource restorer runs on BOTH sides since binding re-points the sampler units and the entity renderers in between rebind textures, and a null entry falls back to the entity program rather than leaving an unrelated one bound
    private void bindShadowGeometryProgram(GbufferPrograms.Entry program) {
        GbufferPrograms.Entry target = program != null ? program : this.entityShadowProgram;
        if (target == null) {
            return;
        }
        this.shaderPackResourceRestorer.run();
        target.getProgram().getProgram().bind();
        this.shaderPackResourceRestorer.run();
        target.getUniforms().update();
    }

    // Renders entities and block entities into the shadow map like OptiFine: fixed-function geometry under the untransformed shadow program, shadow matrices on the fixed-function stack, render origin at the camera to match the snapped model-view; culling is a plain horizontal box like OptiFine's
    private void renderEntityShadows(Minecraft mc, Vector3d camera) {
        if (this.entityShadowProgram == null) {
            return;
        }
        if (!this.content.shouldRenderEntities() && !this.content.shouldRenderPlayer()
                && !this.content.shouldRenderAnyBlockEntities()) {
            return;
        }
        World world = mc.world;
        Entity viewEntity = mc.getRenderViewEntity();
        if (world == null || viewEntity == null) {
            return;
        }
        float partialTicks = CapturedRenderingState.INSTANCE.getTickDelta();
        double cullRange = this.halfPlaneLength + 16.0;

        // FF matrix stack <- shadow matrices (entities/TESRs draw through gl_ModelViewProjectionMatrix).
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.pushMatrix();
        loadMatrix(this.shadowProjection);
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.pushMatrix();
        loadMatrix(this.shadowModelView);

        try {
            // Anchor the render origin at the interpolated camera position the shadow model-view was built around; both dispatchers cache it, and vanilla re-caches for the main pass right after
            mc.getRenderManager().cacheActiveRenderInfo(world, mc.fontRenderer, viewEntity, mc.pointedEntity,
                    mc.gameSettings, partialTicks);
            mc.getRenderManager().setRenderPosition(camera.x, camera.y, camera.z);
            TileEntityRendererDispatcher.instance.prepare(world, mc.getTextureManager(), mc.fontRenderer,
                    viewEntity, mc.objectMouseOver, partialTicks);

            bindShadowGeometryProgram(this.entityShadowProgram);

            // Entities and TESRs draw fixed-function straight after shadow terrain rendered from its own VAO; hand the vertex pipeline back clean (see resetVanillaVertexArrayState), especially since the player model's display lists first compile here and bake the state for the session
            UmbraRenderingPipeline.resetVanillaVertexArrayState();

            if (this.content.shouldRenderEntities() || this.content.shouldRenderPlayer()) {
                bindShadowGeometryProgram(this.entityShadowProgram);
                for (Entity entity : world.loadedEntityList) {
                    if (entity.isDead
                            || Math.abs(entity.posX - camera.x) > cullRange
                            || Math.abs(entity.posZ - camera.z) > cullRange) {
                        continue;
                    }
                    // shadowPlayer and shadowEntities are independent switches, so the player is filtered separately.
                    boolean isPlayer = entity instanceof net.minecraft.entity.player.EntityPlayer;
                    if (isPlayer ? !this.content.shouldRenderPlayer() : !this.content.shouldRenderEntities()) {
                        continue;
                    }
                    mc.getRenderManager().renderEntityStatic(entity, partialTicks, false);
                }
            }

            if (this.content.shouldRenderAnyBlockEntities()) {
                // Umbra draws block entities in the shadow pass under shadow_block, not the entity program; with neither shipped this is the same object and the rebind is a no-op
                bindShadowGeometryProgram(this.blockEntityShadowProgram);
                boolean lightOnly = this.content.shouldRenderLightBlockEntitiesOnly();
                for (TileEntity tileEntity : world.loadedTileEntityList) {
                    if (TileEntityRendererDispatcher.instance.getRenderer(tileEntity) == null) {
                        continue;
                    }
                    BlockPos pos = tileEntity.getPos();
                    if (Math.abs(pos.getX() - camera.x) > cullRange || Math.abs(pos.getZ() - camera.z) > cullRange) {
                        continue;
                    }
                    // shadowLightBlockEntities without shadowBlockEntities: only emitters, so a voxel-lighting pack sees light sources without paying for every chest and sign
                    if (lightOnly && tileEntity.getBlockType().getLightValue(
                            world.getBlockState(pos), world, pos) <= 0) {
                        continue;
                    }
                    TileEntityRendererDispatcher.instance.render(tileEntity, partialTicks, -1);
                }
            }
        } finally {
            LWJGL.glUseProgram(0);
            GlStateManager.matrixMode(GL_PROJECTION_MODE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL_MODELVIEW_MODE);
            GlStateManager.popMatrix();
            // Entity/TESR rendering rebinds textures and toggles state; restore what the terrain layers assume.
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }
    }

    // Uploads a JOML matrix into the fixed-function stack
    private void loadMatrix(Matrix4f matrix) {
        matrix.get(this.matrixBuffer);
        GlStateManager.loadIdentity();
        GlStateManager.multMatrix(this.matrixBuffer);
    }

    // Copies the shadow framebuffer's depth into destination; requires the shadow FBO bound as this pass's framebuffer, making it a framebuffer read rather than a texture blit
    private void copyDepthTo(DepthTexture destination) {
        GlTextureUnits.selectScratch(TEXTURE_SETUP_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, destination.getTextureId());
        LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, this.resolution, this.resolution);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
        // Unit 31 can belong to a custom texture or image sampler on 32-unit drivers; Umbra rebinds per program use, so restore before translucent shadow terrain continues
        this.shaderPackResourceRestorer.run();
    }

    // The shadow camera, a verbatim port of Iris's ShadowMatrices (baseline model-view, snapModelViewToGrid, ortho); the GRID SNAP is what matters, since the previous hand-rolled snap omitted the half-cell centring and had the sign backwards, which made shadows flicker
    private void computeMatrices() {
        if (this.shadowMapFov != null) {
            // ShadowMatrices.createPerspectiveMatrix(fov).
            float yScale = (float) (1.0f / Math.tan(Math.toRadians(this.shadowMapFov) * 0.5f));
            this.shadowProjection.set(
                    yScale, 0.0f, 0.0f, 0.0f,
                    0.0f, yScale, 0.0f, 0.0f,
                    0.0f, 0.0f, (this.farPlane + this.nearPlane) / (this.nearPlane - this.farPlane), -1.0f,
                    0.0f, 0.0f, 2.0f * this.farPlane * this.nearPlane / (this.nearPlane - this.farPlane), 1.0f);
        } else {
            // ShadowMatrices.createOrthoMatrix(halfPlaneLength, nearPlane, farPlane).
            this.shadowProjection.identity().setOrtho(
                    -this.halfPlaneLength, this.halfPlaneLength,
                    -this.halfPlaneLength, this.halfPlaneLength,
                    this.nearPlane, this.farPlane);
        }

        // ---- createBaselineModelViewMatrix(target, shadowAngle, sunPathRotation) ----
        float shadowAngle = CelestialUniforms.getShadowAngle();
        float skyAngle;
        if (shadowAngle < 0.25f) {
            skyAngle = shadowAngle + 0.75f;
        } else {
            skyAngle = shadowAngle - 0.25f;
        }

        this.shadowModelView.identity()
                .rotateX((float) Math.toRadians(90.0f))
                .rotateZ((float) Math.toRadians(skyAngle * -360.0f))
                .rotateX((float) Math.toRadians(this.sunPathRotation));

        // ---- snapModelViewToGrid(target, intervalSize, cameraX, cameraY, cameraZ) ----
        Vector3d camera = CapturedRenderingState.INSTANCE.getCameraPosition();
        if (Math.abs(this.intervalSize) != 0.0f) {
            float halfIntervalSize = this.intervalSize / 2.0f;
            float offsetX = (float) camera.x % this.intervalSize - halfIntervalSize;
            float offsetY = (float) camera.y % this.intervalSize - halfIntervalSize;
            float offsetZ = (float) camera.z % this.intervalSize - halfIntervalSize;
            this.shadowModelView.translate(offsetX, offsetY, offsetZ);
        }
    }

    // For the shadow matrix uniforms
    public Matrix4f getShadowModelView() {
        return this.shadowModelView;
    }

    // For the shadow matrix uniforms
    public Matrix4f getShadowProjection() {
        return this.shadowProjection;
    }

    // Frees the FBO and textures
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        this.framebuffer.destroy();
        this.depthTexture.destroy();
        this.depthTextureNoTranslucents.destroy();
        LWJGL.glDeleteTextures(this.colorTexture0);
        LWJGL.glDeleteTextures(this.colorTexture1);
        // Only when it is genuinely a second program: with no shadow_block this is the same object as entityShadowProgram, and destroying it would double-free
        if (!this.blockEntityProgramShared && this.blockEntityShadowProgram != null) {
            this.blockEntityShadowProgram.getProgram().destroy();
        }
        if (this.entityShadowProgram != null) {
            this.entityShadowProgram.getProgram().destroy();
        }
    }
}
