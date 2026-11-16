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

// Renderer of the shadow pass of a shader pack.
public class UmbraShadowRenderer {
    // Logger of repeated pass failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Near plane used when the pack sets none.
    public static final float DEFAULT_NEAR_PLANE = -100.05f;
    // Far plane used when the pack sets none.
    public static final float DEFAULT_FAR_PLANE = 156.0f;
    // Camera snapping interval used when the pack sets none.
    public static final float DEFAULT_INTERVAL_SIZE = 2.0f;
    // Failures after which the shadow pass is disabled.
    static final int MAX_FAILURES = 3;

    // Active state of the shadow pass on the render thread.
    private static boolean shadowPassActive;

    // Depth and color targets of the shadow map.
    private final ShadowTargets targets;
    // Shadow camera.
    private final ShadowCamera camera;
    // Terrain selection and drawing of the shadow pass.
    private final ShadowTerrain terrain;
    // Programs of the shadow casters.
    private final ShadowCasterPrograms programs;
    // Entity and block entity casters.
    private final ShadowCasters casters;
    // GL state saved around the pass.
    private final ShadowPassState passState = new ShadowPassState();
    // Content settings of the pass.
    private final ShadowContentSettings content;
    // Draw buffers the shadow program writes.
    private final int[] drawBuffers;
    // Action that rebinds the pack resources.
    private final Runnable shaderPackResourceRestorer;
    // Number of failed passes.
    private int failureCount;
    // Disabled state after repeated failures.
    private boolean failed;
    // Released state of the renderer.
    private boolean destroyed;

    // Creates the shadow targets and programs and releases them when creation fails.
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
        ShadowCasterPrograms programs = null;
        try {
            this.drawBuffers = this.targets.drawMask(shadowSource.getFragmentSource()
                    .map(source -> DrawBuffers.parseActive(source, shaderDefines))
                    .orElse(null));
            programs = new ShadowCasterPrograms(shadowSource, shadowEntitiesSource, shadowBlockSource,
                    source -> GbufferPrograms.compile(source, shaderDefines, samplerUnits), shaderPackResourceRestorer);
            this.casters = new ShadowCasters(programs, content, shadowDistance);
        } catch (Throwable failure) {
            // Releases the targets and the programs of a renderer that could not be created
            try {
                this.targets.destroy();
            } finally {
                if (programs != null) {
                    programs.destroy();
                }
            }
            throw failure;
        }
        this.programs = programs;
    }

    // Returns whether the shadow pass is drawing.
    public static boolean isShadowPass() {
        return shadowPassActive;
    }

    // Returns the GL name of the shadow depth texture.
    public int getDepthTextureId() {
        return this.targets.depthTextureId();
    }

    // Returns the GL name of the shadow depth texture without translucents.
    public int getDepthTextureNoTranslucentsId() {
        return this.targets.depthNoTranslucentsTextureId();
    }

    // Returns the GL name of a shadow color texture.
    public int getColorTextureId(int index) {
        return this.targets.colorTextureId(index);
    }

    // Returns the internal format of a shadow color texture.
    public int getColorInternalFormat(int index) {
        return this.targets.colorInternalFormat(index);
    }

    // Returns the number of shadow color textures.
    public int getColorBufferCount() {
        return this.targets.colorBufferCount();
    }

    // Returns the side of the shadow map in pixels.
    public int getResolution() {
        return this.targets.resolution();
    }

    // Returns the framebuffer of the shadow map.
    public UmbraFramebuffer getFramebuffer() {
        return this.targets.framebuffer();
    }

    // Returns the frustum of the shadow terrain culling.
    public Frustum getCullingFrustum() {
        return this.terrain.cullingFrustum();
    }

    // Returns the model view matrix of the shadow camera.
    public Matrix4f getShadowModelView() {
        return this.camera.modelView();
    }

    // Returns the projection matrix of the shadow camera.
    public Matrix4f getShadowProjection() {
        return this.camera.projection();
    }

    // Draws the shadow pass and disables it after repeated failures.
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

    // Draws terrain and casters and translucents into the shadow map.
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

    // Binds the block texture atlas.
    private static void bindBlockAtlas(Minecraft mc) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    // Releases the targets and the programs once.
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
