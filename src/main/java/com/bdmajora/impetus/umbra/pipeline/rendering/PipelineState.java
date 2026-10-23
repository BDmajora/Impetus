package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder;
import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.pipeline.CenterDepthSampler;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.umbra.pipeline.CustomImageManager;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.FullscreenQuadRenderer;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;
import com.bdmajora.impetus.umbra.uniforms.CameraUniforms;
import com.bdmajora.impetus.umbra.uniforms.FrameUpdateNotifier;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Everything one pack's pipeline owns, built in the order the pieces depend on each other and freed together; every program compiles up front and a failure skips its pass, render thread only
public final class PipelineState {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    // The GL 3.3 floor, for a driver whose GL_MAX_DRAW_BUFFERS query fails
    private static final int MIN_DRAW_BUFFERS = 8;

    final UmbraRenderTargets targets;
    final Map<String, String> defines;
    final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();
    final CenterDepthSampler centerDepth = new CenterDepthSampler();
    // Optional wide-gamut output conversion, user-configured, sRGB = off
    final ColorSpaceConverter colorSpace = new ColorSpaceConverter();
    final PassSchedule schedule = new PassSchedule();
    final FrameCopies copies = new FrameCopies();
    final ImageUnits images = new ImageUnits();
    // Colour targets flipped by at least one pass while the schedule is built, for custom-texture override deactivation
    private final BitSet flippedAtLeastOnce = new BitSet();
    // The merged sampler overrides per stage, built once instead of per compiled program
    private final Map<TextureStage, Map<String, CustomTextureManager.Override>> stageOverrides = new EnumMap<>(TextureStage.class);
    final int maxDrawBuffers;
    final FullscreenQuadRenderer quad;
    final ShadowSamplers shadowSamplers;
    final ScalarDirectives scalars;
    final NoiseTexture noise;
    final CustomTextureManager customTextures;
    final CustomImageManager customImages;
    final ShaderStorageBufferHolder storageBuffers;
    final PackSwitches switches;
    final GbufferPrograms gbufferPrograms;
    final GbufferTargets gbuffers;
    final GbufferFeedback feedback;
    final TextureBinder binder;
    final UmbraShadowRenderer shadowRenderer;
    final ProgramCache programs;
    // Distant Horizons: the pack's dh_* programs and the LOD framebuffers over this gbuffer
    DhCompat dhCompat;
    boolean active;
    boolean destroyed;
    boolean fullClearRequired = true;
    boolean setupDispatched;
    // The flip snapshot colortex4..7 are bound with for gbuffer programs, before or after the deferred chain
    BitSet activeGbufferFlips = new BitSet();

    public PipelineState(UmbraRenderingPipeline owner, ShaderPack pack) {
        Minecraft mc = Minecraft.getMinecraft();
        this.targets = new UmbraRenderTargets(mc.displayWidth, mc.displayHeight);
        this.defines = pack.getEnvironmentDefines();
        // The GPU identity macros need a live context, so they join here rather than the pack's baked environment; without them every hardware-workaround gate took its "unknown vendor" branch (Clarity's `immut` expanded to nothing)
        ShaderMacros.withGpuIdentity(this.defines, LWJGL.glGetString(GL11.GL_VENDOR), LWJGL.glGetString(GL11.GL_RENDERER));
        CameraUniforms.attach(this.frameUpdateNotifier);
        try {
            int reportedDrawBuffers = LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS);
            this.maxDrawBuffers = reportedDrawBuffers > 0 ? reportedDrawBuffers : MIN_DRAW_BUFFERS;
            this.quad = new FullscreenQuadRenderer();
            this.shadowSamplers = new ShadowSamplers(pack.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
            ShaderProperties properties = pack.getProperties();
            PackSources sources = new PackSources(pack.getProgramSet(), this.defines);
            TargetDirectives targetDirectives = new TargetDirectives();
            targetDirectives.apply(sources, this.targets);
            this.scalars = new ScalarDirectives(new ConstDirectives(activeStages(sources)));
            this.scalars.publish();
            // After the scan: `const int noiseTextureResolution` sizes this, and guessing 256 gives a pack sampling at its own resolution the wrong frequency
            this.noise = new NoiseTexture(this.scalars.noiseResolution());
            // size.buffer.colortexN lands before any target materialises; Photon's sky map is authored against a 192x108 colortex4 indexed by absolute texel
            properties.getBufferSizes().forEach((index, size) -> {
                if (index >= 0 && index < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                    this.targets.setColorSize(index, size[0], size[1], properties.getBufferSizeRelative(index));
                }
            });
            BitSet sampled = sources.sampledColorTargets();
            for (int index = sampled.nextSetBit(0); index >= 0; index = sampled.nextSetBit(index + 1)) {
                this.targets.getOrCreate(index);
            }
            PackSwitches.publishWorldSettings(pack, mc.gameSettings.renderDistanceChunks);
            // Custom textures and images exist before any program compiles, since sampler assignment consults their overrides
            int firstCustomUnit = this.shadowSamplers.separateHardwareSamplers()
                    ? SamplerLayout.SHADOW_TEX_1_HW_UNIT + 1 : SamplerLayout.CUSTOM_TEX_FIRST_UNIT;
            int lastCustomUnit = lastProgrammableUnit();
            this.customTextures = new CustomTextureManager(pack, SamplerLayout.byStage(), SamplerLayout.colorTargetsByName(),
                    firstCustomUnit, lastCustomUnit);
            this.customImages = new CustomImageManager(properties.getUmbraCustomImages(),
                    this.customTextures.getNextAvailableUnit(), lastCustomUnit, mc.displayWidth, mc.displayHeight);
            this.images.allocateRenderTargets(sources.renderTargetImages(), this.customImages, this.targets);
            SamplerAssignment.publish(this, overrides(TextureStage.GBUFFERS_AND_SHADOW));
            // Custom uniforms exist before any program compiles, so every compile path can register them
            ActiveCustomUniforms.set(properties.getCustomUniforms().build());
            // Pack-declared SSBOs (bufferObject.<index>), zero-filled and bound at fixed indices
            this.storageBuffers = new ShaderStorageBufferHolder(
                    ShaderStorageBufferHolder.parseDefinitions(properties.getRaw()), mc.displayWidth, mc.displayHeight);
            this.switches = new PackSwitches(pack);
            this.gbufferPrograms = new GbufferPrograms(pack, SamplerLayout.gbuffer(), gbufferOverrideUnits());
            this.gbuffers = new GbufferTargets(pack, this.gbufferPrograms, this.defines, this.targets);
            this.feedback = new GbufferFeedback(this.targets);
            this.binder = new TextureBinder(this.targets, this.noise, this.customTextures, this.customImages,
                    this.shadowSamplers, () -> this.dhCompat);
            this.shadowRenderer = ShadowSettings.createRenderer(pack, sources, this.shadowSamplers, this.defines,
                    gbufferOverrideUnits(), this.binder::bindPackResources);
            this.binder.setShadowRenderer(this.shadowRenderer);
            this.images.allocateShadowColors(sources.shadowColorImages(), this.customImages, this.shadowRenderer);
            // The shadowcolorimg units join the overrides, so the terrain and DH programs built from here on see them too
            this.stageOverrides.clear();
            SamplerAssignment.publish(this, overrides(TextureStage.GBUFFERS_AND_SHADOW));
            this.programs = new ProgramCache(this.defines, this::overrides, this.flippedAtLeastOnce);
            ComputeBuilder computes = new ComputeBuilder(this.defines, this::overrides, this.flippedAtLeastOnce,
                    ComputeDirectives.parseIndirectPointers(properties.getRaw()), this.storageBuffers,
                    this.customImages.getFirst3DImageSize());
            PassFactory factory = new PassFactory(properties, sources, this.programs, this.targets, this.shadowRenderer,
                    this.flippedAtLeastOnce);
            // One schedule from the reset flip state, then the copy-back for whatever it leaves odd-flipped, so next frame's baked FBOs and snapshots hold without per-frame parity
            this.schedule.build(pack.getProgramSet(), properties, factory, computes, this.gbuffers, this.targets.getBufferFlipper());
            // A clear FBO groups at most what the driver draws and attaches; the copies treat a failed query as one at a time
            this.copies.build(this.targets, targetDirectives, this.targets.getBufferFlipper(),
                    Math.min(this.maxDrawBuffers, LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)));
            // After the schedule, since the LOD framebuffers attach the gbuffer's baked front textures; dhShadow.enabled defaults on like Iris
            this.dhCompat = new DhCompat(owner, pack, properties.getDhShadowEnabled().orElse(Boolean.TRUE));
            // Building creates and checks FBOs as a side effect; give Minecraft its main target back before vanilla's next GL check
            LWJGL.glUseProgram(0);
            GlState.bindMainRenderTarget(mc);
            GlState.restoreMainDrawReadBuffers();
            this.binder.restoreUnits();
        } catch (RuntimeException | Error e) {
            destroy();
            throw e;
        }
    }

    // Every program's resolved vertex and fragment stage in scan order, for the pack-wide scalar consts
    private static List<String> activeStages(PackSources sources) {
        List<String> stages = new ArrayList<>();
        for (ProgramSource source : sources.all()) {
            stages.addAll(sources.activeStages(source));
        }
        return stages;
    }

    // The highest unit a pack texture may take: the driver's limit, below the pipeline's own fixed high units
    private static int lastProgrammableUnit() {
        int driver = LWJGL.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) - 1;
        return Math.min(SamplerLayout.CUSTOM_TEX_LAST_UNIT, Math.max(SamplerLayout.CUSTOM_TEX_FIRST_UNIT - 1, driver));
    }

    // The stage's custom-texture overrides plus the stage-independent image assignments, which never deactivate (colorTarget -1); read-only, shared by every compile of the stage
    Map<String, CustomTextureManager.Override> overrides(TextureStage stage) {
        return this.stageOverrides.computeIfAbsent(stage, s -> {
            Map<String, CustomTextureManager.Override> merged = new LinkedHashMap<>(this.customTextures.getOverrides(s));
            this.customImages.getUniformOverrides().forEach((name, unit) ->
                    merged.put(name, new CustomTextureManager.Override(unit, -1)));
            this.images.addOverrides(merged);
            return Collections.unmodifiableMap(merged);
        });
    }

    // The gbuffers-stage overrides flattened to name -> unit, for the gbuffer and shadow programs' sampler tables
    private Map<String, Integer> gbufferOverrideUnits() {
        Map<String, Integer> units = new LinkedHashMap<>();
        this.customTextures.getOverrides(TextureStage.GBUFFERS_AND_SHADOW).forEach((name, override) -> units.put(name, override.unit));
        units.putAll(this.customImages.getUniformOverrides());
        return units;
    }

    // Whether the frame has run the deferred chain and renders into its post-deferred gbuffer
    boolean isPostDeferred() {
        return !this.schedule.deferred.isEmpty() && this.gbuffers.current == this.schedule.translucentGbuffer;
    }

    // Frees every GL resource and resets what was published for the chunk meshers; render thread only, safe twice and on a half-built state
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        this.active = false;
        this.centerDepth.destroy();
        this.colorSpace.destroy();
        if (this.storageBuffers != null) {
            this.storageBuffers.destroy();
        }
        // Umbra sweeps the same registry: a holder replaced without destroy() strands hundreds of megabytes of VRAM per reload
        ShaderStorageBufferHolder.forceDeleteBuffers();
        ActiveCustomUniforms.clear();
        UmbraTerrainProgramOverride.destroyShadowPrograms();
        if (this.shadowRenderer != null) {
            this.shadowRenderer.destroy();
        }
        if (this.gbufferPrograms != null) {
            this.gbufferPrograms.destroy();
        }
        SamplerAssignment.withdraw(this);
        PackSwitches.resetWorldSettings();
        ScalarDirectives.reset();
        this.schedule.destroy();
        this.copies.destroy();
        if (this.programs != null) {
            this.programs.destroy();
        }
        if (this.customTextures != null) {
            this.customTextures.destroy();
        }
        if (this.customImages != null) {
            this.customImages.destroy();
        }
        if (this.dhCompat != null) {
            try {
                this.dhCompat.clearPipeline();
            } catch (RuntimeException e) {
                // A broken DH bridge must not stop the rest of the teardown and leak the pipeline's VRAM
                LOGGER.error("[Umbra] Distant Horizons compat failed to release its pipeline", e);
            }
            this.dhCompat = null;
        }
        if (this.feedback != null) {
            this.feedback.destroy();
        }
        if (this.binder != null) {
            this.binder.destroy();
        }
        if (this.quad != null) {
            this.quad.destroy();
        }
        if (this.noise != null) {
            this.noise.destroy();
        }
        ImmediateTessellation.reset();
        if (this.shadowSamplers != null) {
            this.shadowSamplers.destroy();
        }
        this.targets.destroy();
    }
}
