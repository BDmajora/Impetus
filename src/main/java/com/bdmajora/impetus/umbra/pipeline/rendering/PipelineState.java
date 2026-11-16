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

// Every resource of an active shader pack pipeline from its creation to its release.
public final class PipelineState {
    // Logger of release failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // GL enum of the texture image unit limit.
    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    // Draw buffer count used when the driver reports none.
    private static final int MIN_DRAW_BUFFERS = 8;

    // Color and depth targets of the pack.
    final UmbraRenderTargets targets;
    // Macros passed to every program.
    final Map<String, String> defines;
    // Notifier of per frame uniform updates.
    final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();
    // Sampler of the depth at the screen center.
    final CenterDepthSampler centerDepth = new CenterDepthSampler();
    // Converter of the final color space.
    final ColorSpaceConverter colorSpace = new ColorSpaceConverter();
    // Passes in draw order.
    final PassSchedule schedule = new PassSchedule();
    // Buffer swaps and clears.
    final FrameCopies copies = new FrameCopies();
    // Image units of the pack.
    final ImageUnits images = new ImageUnits();
    // Targets flipped by at least one pass.
    private final BitSet flippedAtLeastOnce = new BitSet();
    // Sampler overrides by texture stage.
    private final Map<TextureStage, Map<String, CustomTextureManager.Override>> stageOverrides = new EnumMap<>(TextureStage.class);
    // Number of draw buffers the driver allows.
    final int maxDrawBuffers;
    // Renderer of the fullscreen quad.
    final FullscreenQuadRenderer quad;
    // Samplers of the shadow maps.
    final ShadowSamplers shadowSamplers;
    // Scalar directives of the pack.
    final ScalarDirectives scalars;
    // Noise texture of the pack.
    final NoiseTexture noise;
    // Custom textures of the pack.
    final CustomTextureManager customTextures;
    // Custom images of the pack.
    final CustomImageManager customImages;
    // Shader storage buffers of the pack.
    final ShaderStorageBufferHolder storageBuffers;
    // Feature switches of the pack.
    final PackSwitches switches;
    // Programs of the gbuffer passes.
    final GbufferPrograms gbufferPrograms;
    // Gbuffer target sets.
    final GbufferTargets gbuffers;
    // Feedback copies of the gbuffers.
    final GbufferFeedback feedback;
    // Binder of the pack textures.
    final TextureBinder binder;
    // Renderer of the shadow pass.
    final UmbraShadowRenderer shadowRenderer;
    // Cache of the composite programs.
    final ProgramCache programs;
    // Distant Horizons bridge or null.
    DhCompat dhCompat;
    // Active state of the pipeline.
    boolean active;
    // Released state of the pipeline.
    boolean destroyed;
    // Full clear state of the next frame.
    boolean fullClearRequired = true;
    // Dispatched state of the setup programs.
    boolean setupDispatched;
    // Gbuffer flips of the current frame.
    BitSet activeGbufferFlips = new BitSet();

    // Creates every resource of a pack and releases them all when one fails.
    public PipelineState(UmbraRenderingPipeline owner, ShaderPack pack) {
        Minecraft mc = Minecraft.getMinecraft();
        this.targets = new UmbraRenderTargets(mc.displayWidth, mc.displayHeight);
        this.defines = pack.getEnvironmentDefines();
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
            this.noise = new NoiseTexture(this.scalars.noiseResolution());
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
            int firstCustomUnit = this.shadowSamplers.separateHardwareSamplers()
                    ? SamplerLayout.SHADOW_TEX_1_HW_UNIT + 1 : SamplerLayout.CUSTOM_TEX_FIRST_UNIT;
            int lastCustomUnit = lastProgrammableUnit();
            this.customTextures = new CustomTextureManager(pack, SamplerLayout.byStage(), SamplerLayout.colorTargetsByName(),
                    firstCustomUnit, lastCustomUnit);
            this.customImages = new CustomImageManager(properties.getUmbraCustomImages(),
                    this.customTextures.getNextAvailableUnit(), lastCustomUnit, mc.displayWidth, mc.displayHeight);
            this.images.allocateRenderTargets(sources.renderTargetImages(), this.customImages, this.targets);
            SamplerAssignment.publish(this, overrides(TextureStage.GBUFFERS_AND_SHADOW));
            ActiveCustomUniforms.set(properties.getCustomUniforms().build());
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
            this.stageOverrides.clear();
            SamplerAssignment.publish(this, overrides(TextureStage.GBUFFERS_AND_SHADOW));
            this.programs = new ProgramCache(this.defines, this::overrides, this.flippedAtLeastOnce);
            ComputeBuilder computes = new ComputeBuilder(this.defines, this::overrides, this.flippedAtLeastOnce,
                    ComputeDirectives.parseIndirectPointers(properties.getRaw()), this.storageBuffers,
                    this.customImages.getFirst3DImageSize());
            PassFactory factory = new PassFactory(properties, sources, this.programs, this.targets, this.shadowRenderer,
                    this.flippedAtLeastOnce);
            this.schedule.build(pack.getProgramSet(), properties, factory, computes, this.gbuffers, this.targets.getBufferFlipper());
            this.copies.build(this.targets, targetDirectives, this.targets.getBufferFlipper(),
                    Math.min(this.maxDrawBuffers, LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)));
            this.dhCompat = new DhCompat(owner, pack, properties.getDhShadowEnabled().orElse(Boolean.TRUE));
            LWJGL.glUseProgram(0);
            GlState.bindMainRenderTarget(mc);
            GlState.restoreMainDrawReadBuffers();
            this.binder.restoreUnits();
        } catch (RuntimeException | Error e) {
            destroy();
            throw e;
        }
    }

    // Returns the active stages of every program source.
    private static List<String> activeStages(PackSources sources) {
        List<String> stages = new ArrayList<>();
        for (ProgramSource source : sources.all()) {
            stages.addAll(sources.activeStages(source));
        }
        return stages;
    }

    // Returns the last texture unit custom textures may use.
    private static int lastProgrammableUnit() {
        int driver = LWJGL.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) - 1;
        return Math.min(SamplerLayout.CUSTOM_TEX_LAST_UNIT, Math.max(SamplerLayout.CUSTOM_TEX_FIRST_UNIT - 1, driver));
    }

    // Returns the cached sampler overrides of a stage.
    Map<String, CustomTextureManager.Override> overrides(TextureStage stage) {
        return this.stageOverrides.computeIfAbsent(stage, s -> {
            Map<String, CustomTextureManager.Override> merged = new LinkedHashMap<>(this.customTextures.getOverrides(s));
            this.customImages.getUniformOverrides().forEach((name, unit) ->
                    merged.put(name, new CustomTextureManager.Override(unit, -1)));
            this.images.addOverrides(merged);
            return Collections.unmodifiableMap(merged);
        });
    }

    // Returns the units of the custom textures and images of the gbuffer passes.
    private Map<String, Integer> gbufferOverrideUnits() {
        Map<String, Integer> units = new LinkedHashMap<>();
        this.customTextures.getOverrides(TextureStage.GBUFFERS_AND_SHADOW).forEach((name, override) -> units.put(name, override.unit));
        units.putAll(this.customImages.getUniformOverrides());
        return units;
    }

    // Returns whether the translucent gbuffer comes after the deferred passes.
    boolean isPostDeferred() {
        return !this.schedule.deferred.isEmpty() && this.gbuffers.current == this.schedule.translucentGbuffer;
    }

    // Releases every resource once and rethrows the first failure after the rest were released.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        this.active = false;
        // First failure of a release step
        Throwable[] failure = new Throwable[1];
        release(failure, this.centerDepth::destroy);
        release(failure, this.colorSpace::destroy);
        if (this.storageBuffers != null) {
            release(failure, this.storageBuffers::destroy);
        }
        release(failure, ShaderStorageBufferHolder::forceDeleteBuffers);
        release(failure, ActiveCustomUniforms::clear);
        release(failure, UmbraTerrainProgramOverride::destroyShadowPrograms);
        if (this.shadowRenderer != null) {
            release(failure, this.shadowRenderer::destroy);
        }
        if (this.gbufferPrograms != null) {
            release(failure, this.gbufferPrograms::destroy);
        }
        release(failure, () -> SamplerAssignment.withdraw(this));
        release(failure, PackSwitches::resetWorldSettings);
        release(failure, ScalarDirectives::reset);
        release(failure, this.schedule::destroy);
        release(failure, this.copies::destroy);
        if (this.programs != null) {
            release(failure, this.programs::destroy);
        }
        if (this.customTextures != null) {
            release(failure, this.customTextures::destroy);
        }
        if (this.customImages != null) {
            release(failure, this.customImages::destroy);
        }
        if (this.dhCompat != null) {
            try {
                this.dhCompat.clearPipeline();
            } catch (RuntimeException e) {
                LOGGER.error("[Umbra] Distant Horizons compat failed to release its pipeline", e);
            }
            this.dhCompat = null;
        }
        if (this.feedback != null) {
            release(failure, this.feedback::destroy);
        }
        if (this.binder != null) {
            release(failure, this.binder::destroy);
        }
        if (this.quad != null) {
            release(failure, this.quad::destroy);
        }
        if (this.noise != null) {
            release(failure, this.noise::destroy);
        }
        release(failure, ImmediateTessellation::reset);
        if (this.shadowSamplers != null) {
            release(failure, this.shadowSamplers::destroy);
        }
        release(failure, this.targets::destroy);
        if (failure[0] instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure[0] instanceof Error error) {
            throw error;
        }
    }

    // Runs one release step and keeps its failure when it is the first.
    private static void release(Throwable[] failure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error e) {
            if (failure[0] == null) {
                failure[0] = e;
            } else if (e != failure[0]) {
                failure[0].addSuppressed(e);
            }
        }
    }
}
