package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.pipeline.shadow.DhShadowLods;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowCamera;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowCasterPrograms;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowCasters;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowPassState;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowTargets;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowTerrain;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;

// Renders the shadow map from the sun or moon before the gbuffer pass samples it
public class UmbraShadowRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Near plane of the shadow projection in blocks when the pack declares none
    public static final float DEFAULT_NEAR_PLANE = -100.05f;
    // Far plane of the shadow projection in blocks when the pack declares none
    public static final float DEFAULT_FAR_PLANE = 156.0f;
    // Grid step in blocks that the shadow camera snaps to when the pack declares none
    public static final float DEFAULT_INTERVAL_SIZE = 2.0f;
    // Number of failed passes after which shadows stay off for the pack
    static final int MAX_FAILURES = 3;

    // Flag set while the shadow pass is drawing
    private static boolean shadowPassActive;

    // Shadow textures and framebuffer
    private final ShadowTargets targets;
    // Sun or moon camera matrices
    private final ShadowCamera camera;
    // Section selection and terrain layer draws
    private final ShadowTerrain terrain;
    // Fixed function programs of entities and block entities
    private final ShadowCasterPrograms programs;
    // Entity and block entity draws
    private final ShadowCasters casters;
    // Culling and depth state saved around each pass
    private final ShadowPassState passState = new ShadowPassState();
    // Switches for what the pass draws
    private final ShadowContentSettings content;
    // Draw buffer mask of the shadow program limited to allocated colour buffers
    private final int[] drawBuffers;
    // Action that rebinds the pack textures and images to their units
    private final Runnable shaderPackResourceRestorer;
    // Number of passes that threw
    private int failureCount;
    // Flag set once the pass gives up after repeated failures
    private boolean failed;
    // Flag set once destroy has run
    private boolean destroyed;

    // Allocates the shadow targets and compiles the fixed function caster programs and frees everything again when any step throws
    public UmbraShadowRenderer(int resolution, float shadowDistance, float nearPlane, float farPlane,
                               float intervalSize, Float shadowMapFov, float sunPathRotation,
                               ProgramSource shadowSource,
                               ProgramSource shadowEntitiesSource, ProgramSource shadowBlockSource,
                               Map<String, Integer> samplerUnits,
                               Map<String, String> shaderDefines, boolean[] hardwareFiltering,
                               boolean[] mipmapDepth, boolean[] nearestDepth, boolean separateHardwareSamplers,
                               Runnable shaderPackResourceRestorer, ShadowContentSettings content,
                               float voxelDistance, float cullDistance, boolean packVoxelizes,
                               ShadowColorSettings colorSettings, BitSet usedColorBuffers) {
        Objects.requireNonNull(shadowSource, "shadowSource");
        Objects.requireNonNull(samplerUnits, "samplerUnits");
        this.shaderPackResourceRestorer = Objects.requireNonNull(shaderPackResourceRestorer, "shaderPackResourceRestorer");
        this.content = Objects.requireNonNull(content, "content");
        this.camera = new ShadowCamera(shadowDistance, nearPlane, farPlane, intervalSize, shadowMapFov, sunPathRotation);
        this.terrain = new ShadowTerrain(content, cullDistance, voxelDistance, packVoxelizes, sunPathRotation);
        this.targets = new ShadowTargets(resolution, hardwareFiltering, mipmapDepth, nearestDepth,
                separateHardwareSamplers, colorSettings, usedColorBuffers);
        try {
            this.drawBuffers = this.targets.drawMask(shadowSource.getFragmentSource()
                    .map(source -> DrawBuffers.parseActive(source, shaderDefines))
                    .orElse(null));
            this.programs = new ShadowCasterPrograms(shadowSource, shadowEntitiesSource, shadowBlockSource,
                    source -> GbufferPrograms.compile(source, shaderDefines, samplerUnits), shaderPackResourceRestorer);
        } catch (Throwable failure) {
            this.targets.destroy();
            throw failure;
        }
        this.casters = new ShadowCasters(this.programs, content, shadowDistance);
    }

    // Returns whether the shadow pass is drawing
    public static boolean isShadowPass() {
        return shadowPassActive;
    }

    // Returns the shadowtex0 texture name and throws once destroyed
    public int getDepthTextureId() {
        return this.targets.depthTextureId();
    }

    // Returns the shadowtex1 texture name and throws once destroyed
    public int getDepthTextureNoTranslucentsId() {
        return this.targets.depthNoTranslucentsTextureId();
    }

    // Returns the texture name of a shadow colour buffer or 0 for a buffer that does not exist and throws once destroyed
    public int getColorTextureId(int index) {
        return this.targets.colorTextureId(index);
    }

    // Returns the internal format of a shadow colour buffer or 0 for an index outside the addressable buffers
    public int getColorInternalFormat(int index) {
        return this.targets.colorInternalFormat(index);
    }

    // Returns how many shadow colour buffers the pack may address
    public int getColorBufferCount() {
        return this.targets.colorBufferCount();
    }

    // Returns the width and height of the shadow map in pixels
    public int getResolution() {
        return this.targets.resolution();
    }

    // Returns the framebuffer with every shadow colour buffer and shadowtex0 attached
    public UmbraFramebuffer getFramebuffer() {
        return this.targets.framebuffer();
    }

    // Returns the camera relative section filter of the latest pass or null before the first one
    public Frustum getCullingFrustum() {
        return this.terrain.cullingFrustum();
    }

    // Returns the live shadow model view matrix
    public Matrix4f getShadowModelView() {
        return this.camera.modelView();
    }

    // Returns the live shadow projection matrix
    public Matrix4f getShadowProjection() {
        return this.camera.projection();
    }

    // Draws terrain and entities and translucent terrain into the shadow map and leaves the shadow framebuffer bound
    public void render() {
        if (this.failed || this.destroyed) {
            return;
        }
        ImpetusWorldRenderer worldRenderer = ImpetusWorldRenderer.instanceNullable();
        if (worldRenderer == null) {
            return;
        }
        shadowPassActive = true;
        try {
            drawPass(worldRenderer);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            if (++this.failureCount >= MAX_FAILURES) {
                this.failed = true;
                LOGGER.error("[Umbra] Shadow pass failed repeatedly; disabling shadows for this pack", failure);
            }
        } finally {
            this.passState.restore();
            shadowPassActive = false;
        }
    }

    // Runs every stage of one pass in order
    private void drawPass(ImpetusWorldRenderer worldRenderer) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        Vector3d cameraPosition = state.getCameraPosition();
        this.camera.update(CelestialUniforms.getShadowAngle(), cameraPosition);
        state.setShadowModelView(this.camera.modelView());
        state.setShadowProjection(this.camera.projection());
        Minecraft mc = Minecraft.getMinecraft();

        this.targets.bind();
        this.passState.save();
        ShadowPassState.prepare();
        try {
            this.targets.clear(this.drawBuffers);
        } finally {
            // Puts back the frame clear colour with alpha 0
            Vector3f fog = state.getFogColor();
            GlStateManager.clearColor(fog.x, fog.y, fog.z, 0.0f);
        }
        GlStateManager.disableBlend();
        bindBlockAtlas(mc);

        boolean terrainEnabled = this.content.shouldRenderTerrain();
        boolean dhShadows = DhShadowLods.active();
        RenderDevice.enterManagedCode();
        try {
            this.terrain.select(worldRenderer, mc, cameraPosition,
                    dhShadows ? DhCompat.getProjection() : state.getGbufferProjection());
            if (terrainEnabled) {
                this.terrain.drawOpaque(worldRenderer, cameraPosition);
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
        if (terrainEnabled && dhShadows) {
            DhShadowLods.render(false, this.targets);
        }

        this.casters.render(mc, cameraPosition, this.camera.projection(), this.camera.modelView(), state.getTickDelta());

        this.targets.copyDepthWithoutTranslucents();
        this.shaderPackResourceRestorer.run();

        // Translucent casters write tint and depth without blending
        if (terrainEnabled && this.content.shouldRenderTranslucent()) {
            bindBlockAtlas(mc);
            GlStateManager.disableBlend();
            RenderDevice.enterManagedCode();
            try {
                this.terrain.drawTranslucent(worldRenderer, cameraPosition);
            } finally {
                RenderDevice.exitManagedCode();
            }
            GlStateManager.disableBlend();
            if (dhShadows) {
                DhShadowLods.render(true, this.targets);
            }
        }

        this.targets.generateMipmaps();
        this.shaderPackResourceRestorer.run();
    }

    // Binds the block atlas on the active unit
    private static void bindBlockAtlas(Minecraft mc) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    // Frees the shadow targets and the caster programs once
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        try {
            this.targets.destroy();
        } finally {
            this.programs.destroy();
        }
    }
}
