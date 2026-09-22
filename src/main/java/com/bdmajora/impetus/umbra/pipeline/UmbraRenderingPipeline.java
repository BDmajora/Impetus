package com.bdmajora.impetus.umbra.pipeline;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.gl.texture.PlainTexture;
import com.bdmajora.impetus.umbra.gl.texture.StubShadowMap;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.targets.NoiseTexture;
import com.bdmajora.impetus.umbra.terrain.FullscreenTransformer;
import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.terrain.VanillaNameTransformer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CameraUniforms;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import com.bdmajora.impetus.umbra.uniforms.FrameUpdateNotifier;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import com.bdmajora.impetus.umbra.features.FeatureFlags;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;
import com.bdmajora.impetus.lwjgl.GL13;
import com.bdmajora.impetus.lwjgl.GL14;
import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.lwjgl.GL30;
import com.bdmajora.impetus.mixin.core.terrain.ActiveRenderInfoAccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The frame pipeline: beginWorldRendering binds the gbuffer, captureRenderingState copies camera matrices, finishWorldRendering runs each pass ping-ponging targets like OptiFine then final into vanilla's framebuffer; every pass compiles up front and a failure skips it, render thread only
public class UmbraRenderingPipeline {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Texture units for this fixed-unit 1.12 bridge: fullscreen programs keep colortexN -> unit N, gbuffers programs use OptiFine's 1.12 aux slots for gaux1..4/colortex4..7
    private static final int DEPTH_TEX_0_UNIT = 16;
    private static final int DEPTH_TEX_1_UNIT = 17;
    private static final int DEPTH_TEX_2_UNIT = 18;
    private static final int SHADOW_TEX_0_UNIT = 19;
    private static final int SHADOW_TEX_1_UNIT = 20;
    private static final int SHADOW_COLOR_0_UNIT = 21;
    private static final int SHADOW_COLOR_1_UNIT = 22;
    private static final int NOISE_TEX_UNIT = 23;
    private static final int SHADOW_TEX_0_HW_UNIT = 24;
    private static final int SHADOW_TEX_1_HW_UNIT = 25;
    // Distant Horizons' LOD depth (dhDepthTex0/1), above every fixed and pack-allocated unit and shared by both layouts; a sampler may address any unit below GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS (48 minimum on 3.3), the per-stage limit only caps how many a stage samples. Without DH they alias depthtex0/1 as before
    private static final int DH_DEPTH_TEX_0_UNIT = 35;
    private static final int DH_DEPTH_TEX_1_UNIT = 36;
    // OptiFine 1.12 gbuffers-stage units from Shaders.useProgram(): texture/lightmap/normals/specular 0..3, shadow maps 4/5, depthtex0 6, gaux1..4 7..10, depthtex1 12, shadowcolor0/1 13/14, noisetex 15
    private static final int GBUFFER_DEPTH_TEX_0_UNIT = 6;
    private static final int GBUFFER_DEPTH_TEX_1_UNIT = 12;
    private static final int GBUFFER_SHADOW_TEX_0_UNIT = 4;
    private static final int GBUFFER_SHADOW_TEX_1_UNIT = 5;
    private static final int GBUFFER_SHADOW_COLOR_0_UNIT = 13;
    private static final int GBUFFER_SHADOW_COLOR_1_UNIT = 14;
    private static final int GBUFFER_NOISE_TEX_UNIT = 15;
    // iris_overlay, Umbra's entity hurt overlay sampler; 1.12.2 draws that flash as a fixed-function pass, so a fully transparent 1x1 is bound (Umbra's own "no overlay" fallback) on unit 11, the one gap in OptiFine's 1.12 layout
    private static final int GBUFFER_OVERLAY_UNIT = 11;
    // The fullscreen-stage bind of the same 1x1 iris_overlay dummy, ABOVE the sampleable range on purpose since no composite/deferred/final program declares it and a low unit only starved the pack's samplers
    private static final int OVERLAY_TEX_UNIT = 34;
    // Highest logical colortex index shader-pack gbuffer stages may address. FBO attachment points are packed.
    private static final int GBUFFER_ATTACHMENT_LIMIT = UmbraRenderTargets.MAX_COLOR_BUFFERS;
    // High texture unit used transiently for depth-copy binds so no vanilla-tracked unit is disturbed.
    private static final int DEPTH_COPY_SCRATCH_UNIT = 33;
    // Scratch unit for mipmap generation and reset, ABOVE every sampler allocation; raw binds on unit 0 desync GlStateManager's cache so bindColorSamplers' "already bound" checks skip the real rebind (BSL deferred1 read the black colortex6 and the screen went black)
    private static final int MIPMAP_SCRATCH_UNIT = 32;

    // Lowest unit the pack's custom textures and images may use; the shadowtex*HW units are only real under SEPARATE_HARDWARE_SAMPLERS, otherwise they are handed to the pack (Complementary needs seven custom units and lost two at 26), and the first is shared with transient depth-copy helpers that rebind afterwards
    private static final int CUSTOM_TEX_FIRST_UNIT = SHADOW_TEX_0_HW_UNIT;
    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    private static final int GL_BACK_BUFFER = 0x0405;
    private static final int SHADER_PACK_RESOURCE_BARRIERS = 0x00000020 | 0x00000008 | 0x00002000;
    private static final int FULL_BRIGHT_LIGHTMAP = 0x00F000F0;
    // Both halves of #FULL_BRIGHT_LIGHTMAP as the raw texcoord the lightmap texture matrix expects.
    private static final float FULL_BRIGHT_LIGHTMAP_COORD = 240.0f;
    private static final float LIGHTMAP_TEXTURE_SCALE = 1.0f / 256.0f;
    private static final float LIGHTMAP_TEXTURE_OFFSET = 8.0f / 256.0f;
    // Draw-buffer mask for fixed-function content with no pack program: plain color into colortex0 only.
    private static final int[] FIXED_FUNCTION_MASK = {0};
    private static final String[] LEGACY_COLOR_TARGETS = UmbraRenderTargets.LEGACY_COLOR_TARGETS;
    private static final Pattern MIPMAP_DIRECTIVE =
            Pattern.compile("const\\s+bool\\s+(\\w+?)MipmapEnabled\\s*=\\s*(true|false)\\s*;");
    // Umbra's PackDirectives defaults. The half-lives are in deciseconds (1/10 s = 2 ticks).
    private static final float DEFAULT_CENTER_DEPTH_HALF_LIFE = 1.0f;
    private static final float DEFAULT_WETNESS_HALF_LIFE = 600.0f;
    private static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0f;
    private static final float DEFAULT_EYE_BRIGHTNESS_HALF_LIFE = 10.0f;
    // Sampler name -> logical colortex index, independent from the texture unit chosen for a stage.
    private static final Map<String, Integer> COLOR_TARGETS_BY_NAME = new LinkedHashMap<>();
    // Sampler name -> texture unit for deferred/composite/final programs.
    private static final Map<String, Integer> FULLSCREEN_SAMPLER_UNITS = new LinkedHashMap<>();
    // Sampler name -> texture unit for gbuffers/shadow-stage programs.
    private static final Map<String, Integer> GBUFFER_SAMPLER_UNITS = new LinkedHashMap<>();
    private static final int[] GBUFFER_COLOR_TEXTURE_UNITS = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];

    static {
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            COLOR_TARGETS_BY_NAME.put("colortex" + i, i);
            FULLSCREEN_SAMPLER_UNITS.put("colortex" + i, i);
            if (i < LEGACY_COLOR_TARGETS.length) {
                COLOR_TARGETS_BY_NAME.put(LEGACY_COLOR_TARGETS[i], i);
                FULLSCREEN_SAMPLER_UNITS.put(LEGACY_COLOR_TARGETS[i], i);
            }
            GBUFFER_COLOR_TEXTURE_UNITS[i] = -1;
        }
        // OptiFine 1.12 gbuffers stage: gaux1..4 live on units 7..10, and the colortex4..7 aliases share them
        for (int i = 4; i <= 7; i++) {
            GBUFFER_COLOR_TEXTURE_UNITS[i] = i + 3;
        }
        for (int i = 4; i <= 7; i++) {
            GBUFFER_SAMPLER_UNITS.put("colortex" + i, GBUFFER_COLOR_TEXTURE_UNITS[i]);
            if (i < LEGACY_COLOR_TARGETS.length) {
                GBUFFER_SAMPLER_UNITS.put(LEGACY_COLOR_TARGETS[i], GBUFFER_COLOR_TEXTURE_UNITS[i]);
            }
        }
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "depthtex0", DEPTH_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "gdepthtex", DEPTH_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "dhDepthTex", DH_DEPTH_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "dhDepthTex0", DH_DEPTH_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "depthtex1", DEPTH_TEX_1_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "dhDepthTex1", DH_DEPTH_TEX_1_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "depthtex2", DEPTH_TEX_2_UNIT);
        // Shadow samplers are parked on unused units so a shadow-reading pack samples nothing instead of colortex0 (sampler uniforms default to unit 0); the shadow pass is a later phase
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowcolor1", SHADOW_COLOR_1_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowcolor0", SHADOW_COLOR_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowcolor", SHADOW_COLOR_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex0", SHADOW_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex0DH", SHADOW_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadow", SHADOW_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "watershadow", SHADOW_TEX_0_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex1", SHADOW_TEX_1_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex1DH", SHADOW_TEX_1_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex0HW", SHADOW_TEX_0_HW_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "shadowtex1HW", SHADOW_TEX_1_HW_UNIT);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "noisetex", NOISE_TEX_UNIT);
        // OptiFine gbuffer-stage PBR samplers; during fullscreen passes these units are also colortex2/3, so the mapping stays correct for packs leaving the aliases in shared includes
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "normals", 2);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "texNorm", 2);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "specular", 3);
        putSharedSampler(FULLSCREEN_SAMPLER_UNITS, "texSpecular", 3);

        putGbufferSampler("depthtex0", GBUFFER_DEPTH_TEX_0_UNIT);
        putGbufferSampler("gdepthtex", GBUFFER_DEPTH_TEX_0_UNIT);
        putGbufferSampler("dhDepthTex", DH_DEPTH_TEX_0_UNIT);
        putGbufferSampler("dhDepthTex0", DH_DEPTH_TEX_0_UNIT);
        putGbufferSampler("depthtex1", GBUFFER_DEPTH_TEX_1_UNIT);
        putGbufferSampler("dhDepthTex1", DH_DEPTH_TEX_1_UNIT);
        putGbufferSampler("shadowcolor1", GBUFFER_SHADOW_COLOR_1_UNIT);
        putGbufferSampler("shadowcolor0", GBUFFER_SHADOW_COLOR_0_UNIT);
        putGbufferSampler("shadowcolor", GBUFFER_SHADOW_COLOR_0_UNIT);
        putGbufferSampler("iris_overlay", GBUFFER_OVERLAY_UNIT);
        putGbufferSampler("shadowtex0", GBUFFER_SHADOW_TEX_0_UNIT);
        putGbufferSampler("shadowtex0DH", GBUFFER_SHADOW_TEX_0_UNIT);
        putGbufferSampler("shadow", GBUFFER_SHADOW_TEX_0_UNIT);
        putGbufferSampler("watershadow", GBUFFER_SHADOW_TEX_0_UNIT);
        putGbufferSampler("shadowtex1", GBUFFER_SHADOW_TEX_1_UNIT);
        putGbufferSampler("shadowtex1DH", GBUFFER_SHADOW_TEX_1_UNIT);
        putGbufferSampler("noisetex", GBUFFER_NOISE_TEX_UNIT);
    }

    // Registers a sampler name in the fullscreen layout
    private static void putSharedSampler(Map<String, Integer> fullscreen, String name, int unit) {
        fullscreen.put(name, unit);
        GBUFFER_SAMPLER_UNITS.put(name, unit);
    }

    // Registers a sampler name in the gbuffer layout
    private static void putGbufferSampler(String name, int unit) {
        GBUFFER_SAMPLER_UNITS.put(name, unit);
    }

    // One entry of a numbered pass family: a fullscreen draw into its own framebuffer, the final pass (framebuffer == null), or compute-only (program == null) for a slot with only .csh files; computes (<name>.csh, <name>_a..z.csh) dispatch before the pass's own draw under the same flip state, like Umbra
    private static final class FullscreenPass {
        final String name;
        final UmbraProgram program;
        final ProgramUniforms uniforms;
        final UmbraFramebuffer framebuffer;
        // Per color buffer, the texture to bind as colortexN when this pass runs (0 = target not in use).
        final int[] colorSamplers;
        // Logical render targets in shader output-slot order, used for per-target blend directives.
        final int[] drawBuffers;
        final ProgramBlendState blendState;
        final BitSet flipsBefore;
        final BitSet flipsAfter;
        final BitSet mipmappedBuffers;
        // Compute stages dispatched before this pass draws; never null, usually empty.
        final List<ComputePass> computes;
        // Viewport for this pass from its draw buffers' size (size.buffer.colortexN); zero means the current render size
        int viewportWidth;
        int viewportHeight;
        // scale.<program> (Umbra's ViewportData): unlike viewportWidth, this shrinks the rasterised rectangle inside whatever size the pass has; default scale 1, no offset
        float viewportScale = 1.0f;
        float viewportOffsetX;
        float viewportOffsetY;
        // How this pass's program declared shadowtex0/1, applied to the shadow units after it binds (see applyShadowSamplerKinds)
        ShadowSamplerKinds shadowSamplerKinds = ShadowSamplerKinds.ALL_COMPARE;

        FullscreenPass(String name, UmbraProgram program, ProgramUniforms uniforms, UmbraFramebuffer framebuffer,
                       int[] colorSamplers, int[] drawBuffers, ProgramBlendState blendState,
                       BitSet flipsBefore, BitSet flipsAfter, BitSet mipmappedBuffers, List<ComputePass> computes) {
            this.name = name;
            this.program = program;
            this.uniforms = uniforms;
            this.framebuffer = framebuffer;
            this.colorSamplers = colorSamplers;
            this.drawBuffers = drawBuffers;
            this.blendState = blendState;
            this.flipsBefore = flipsBefore;
            this.flipsAfter = flipsAfter;
            this.mipmappedBuffers = mipmappedBuffers;
            this.computes = computes;
        }

        // Umbra's ComputeOnlyPass: a family slot with computes but no vertex/fragment pair; draws and flips nothing, but still needs the flip state and colortex snapshot of its position in the chain
        static FullscreenPass computeOnly(String name, int[] colorSamplers, BitSet flips, List<ComputePass> computes) {
            return new FullscreenPass(name, null, null, null, colorSamplers, DrawBuffers.DEFAULT.clone(), null,
                    flips, (BitSet) flips.clone(), new BitSet(), computes);
        }
    }

    private final UmbraRenderTargets renderTargets;
    private final FullscreenQuadRenderer quadRenderer;
    private final NoiseTexture noiseTexture;
    // Fallback PBR inputs for the gbuffer stage: flat up-normal and black specular (OptiFine's defaults).
    private final PlainTexture defaultNormals;
    private final PlainTexture defaultSpecular;
    // The "no overlay active" stand-in bound on the iris_overlay units.
    private final PlainTexture noOverlayTexture;
    // "Always lit" 1×1 shadow map on the shadowtex units until the real shadow pass exists.
    private final StubShadowMap stubShadowMap;
    // Whether the pack DECLARED SEPARATE_HARDWARE_SAMPLERS (Umbra reads hasFeature for this), not whether the port could provide it; keying off isUsable() made it always true, suppressed GL_TEXTURE_COMPARE_MODE on the shadow depth textures, and left sampler2DShadow reading compare-mode NONE, "fully lit" on NVIDIA
    private boolean separateHardwareSamplers;
    private final boolean[] shadowHardwareFiltering = new boolean[2];
    private final boolean[] shadowMipmap = new boolean[2];
    private final boolean[] shadowNearest = new boolean[2];
    private final int shadowLinearHwSampler;
    private final int shadowNearestHwSampler;
    private final int shadowMippedLinearHwSampler;
    private final int shadowMippedNearestHwSampler;
    // The same four flavours WITHOUT depth comparison, bound on a shadow depth unit only for programs that declared that sampler as a plain sampler2D (see ShadowSamplerKinds); with the comparison sampler there Complementary's texelFetch light-shaft march read "lit" everywhere on this context
    private final int shadowLinearRawSampler;
    private final int shadowNearestRawSampler;
    private final int shadowMippedLinearRawSampler;
    private final int shadowMippedNearestRawSampler;
    // ONE baked frame schedule like Umbra: buffers the chain flips an odd number of times (Complementary's colortex2 TAA history) are copied alt->main at frame end (SwapPass), so every frame starts from "main = latest" and all baked FBOs and sampler snapshots stay valid; TAA works since a history pass reads main and writes alt
    private UmbraFramebuffer gbufferFramebuffer;
    private UmbraFramebuffer translucentGbufferFramebuffer;
    // The setup family: compute-only, dispatched *once* after the pipeline is built (Umbra runs it on dimension change, Photon seeds its LPV volumes with it)
    private final List<FullscreenPass> setupPasses = new ArrayList<>();
    private boolean setupDispatched;
    // prepareBeforeShadow: run the prepare family before the shadow map instead of after, for packs whose shadow pass samples what prepare produces
    private boolean prepareBeforeShadow;
    // allowConcurrentCompute: skip the full memory barrier between consecutive compute dispatches, only safe when the pack states they are independent
    private boolean allowConcurrentCompute;
    // rain.depth — whether rain/snow writes into the depth buffer (Umbra shouldWriteRainAndSnowToDepthBuffer).
    private boolean rainDepth;
    // beacon.beam.depth — whether the beacon beam writes into the depth buffer.
    private boolean beaconBeamDepth;
    // frustum.culling / occlusion.culling — vanilla culling switches; both default on.
    private boolean frustumCulling = true;
    private boolean occlusionCulling = true;
    // skipAllRendering — draw no world geometry at all, leaving only the composite chain.
    private boolean skipAllRendering;
    // separateEntityDraws — entities render in their own pass after the deferred chain.
    private boolean separateEntityDraws;
    // particles.ordering = mixed | after | before, relative to the deferred chain.
    private String particleOrdering = "mixed";
    // backFace.solid|cutout|cutoutMipped|translucent, indexed by BlockRenderLayer#ordinal(); vanilla culls every layer, and a pack shading both sides or reading geometry from the light's side asks to keep back faces
    private final boolean[] backFaceCulling = {true, true, true, true};
    // The begin family: runs at the very start of world rendering, before anything is drawn.
    private final List<FullscreenPass> beginPasses = new ArrayList<>();
    // The prepare family, after the shadow map and before the gbuffers; Photon builds its cloud shadow and cumulus coverage maps into colortex8 here
    private final List<FullscreenPass> preparePasses = new ArrayList<>();
    private final List<FullscreenPass> deferredPasses = new ArrayList<>();
    private final List<FullscreenPass> passes = new ArrayList<>();
    private UmbraFramebuffer blitSourceFramebuffer;
    private final List<SwapPass> swapPasses = new ArrayList<>();
    // Per-render-target clear directives parsed from the pack sources. Umbra defaults every colortex to clear=true.
    private final boolean[] colorBufferClears = new boolean[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Explicit colortexNClearColor values; null means Umbra's default color for that buffer.
    private final float[][] colorBufferClearColors = new float[UmbraRenderTargets.MAX_COLOR_BUFFERS][];
    // Regular per-frame clears: only buffers whose colortexNClear directive is true, both main and alt sides.
    private final List<ClearPass> clearPasses = new ArrayList<>();
    // First-frame / resized-storage clears: every materialized buffer, both main and alt sides.
    private final List<ClearPass> fullClearPasses = new ArrayList<>();
    private boolean fullClearRequired = true;
    // Fullscreen programs plus their uniforms, compiled once and cached by name (null means it failed); owns the GL programs, destroyed here not per-pass
    private final java.util.Map<String, UmbraProgram> compiledPrograms = new java.util.HashMap<>();
    private final java.util.Map<String, ProgramUniforms> compiledUniforms = new java.util.HashMap<>();
    // Per compiled fullscreen program, how it declared the shadow depth samplers; read once from the linked program like the uniforms
    private final java.util.Map<String, ShadowSamplerKinds> compiledShadowSamplerKinds = new java.util.HashMap<>();
    // The pack's fixed-function gbuffer programs (sky/entities/particles/weather/clouds/hand), phase-switched.
    private final GbufferPrograms gbufferPrograms;
    // The pack's custom textures (texture.*/customTexture.* directives) and their unit overrides.
    private final CustomTextureManager customTextureManager;
    // The pack's writable custom images (image.* directives — Complementary's colored-lighting volumes).
    private final CustomImageManager customImageManager;
    // The shadowcomp family, dispatched right after the shadow map renders; compute stages only, since nothing schedules shadowcomp raster stages into shadowcolor0/1 yet (Umbra's ShadowCompositeRenderer)
    private final List<FullscreenPass> shadowCompPasses = new ArrayList<>();
    // Render targets a program writes via the image API (colorimgN) mapped to their image unit (Umbra's addRenderTargetImages); Photon's deferred4_a.csh imageStores its skylight SH this way, and the bound texture follows the pass's flips like Umbra
    private final Map<Integer, Integer> renderTargetImageUnits = new LinkedHashMap<>();
    // shadowcolorimg0/1, the shadow colour attachments via the image API (Umbra's addShadowColorImages); index -> image unit, empty when unreferenced or there is no shadow pass to own them
    private final Map<Integer, Integer> shadowColorImageUnits = new LinkedHashMap<>();
    // Active shader macro environment: built-in MC/UMBRA macros plus the pack's resolved option values.
    private final Map<String, String> shaderDefines;

    // One compute dispatch: the linked program, its uniforms, and the work-group counts.
    private static final class ComputePass {
        final String name;
        final GlProgram program;
        final ProgramUniforms uniforms;
        final int groupsX;
        final int groupsY;
        final int groupsZ;
        // Screen-relative dispatch (const vec2 workGroupsRender); NaN = fixed dispatch.
        final float renderScaleX;
        final float renderScaleY;
        final int localSizeX;
        final int localSizeY;
        // Indirect dispatch (indirect.<pass> directive): GL buffer id, or -1 for direct dispatch.
        final int indirectBuffer;
        final long indirectOffset;
        // Sampler objects also govern a compute stage's texture fetches, so a compute reading raw shadow depth needs the same per-program choice
        ShadowSamplerKinds shadowSamplerKinds = ShadowSamplerKinds.ALL_COMPARE;

        ComputePass(String name, GlProgram program, ProgramUniforms uniforms, int groupsX, int groupsY, int groupsZ,
                    float renderScaleX, float renderScaleY, int localSizeX, int localSizeY,
                    int indirectBuffer, long indirectOffset) {
            this.name = name;
            this.program = program;
            this.uniforms = uniforms;
            this.groupsX = groupsX;
            this.groupsY = groupsY;
            this.groupsZ = groupsZ;
            this.renderScaleX = renderScaleX;
            this.renderScaleY = renderScaleY;
            this.localSizeX = localSizeX;
            this.localSizeY = localSizeY;
            this.indirectBuffer = indirectBuffer;
            this.indirectOffset = indirectOffset;
        }
    }
    // The gbuffers/shadow-stage sampler overrides of the *active* pipeline, consulted by the static assignSamplerUnitsToBoundProgram the Impetus terrain and shadow overrides call for their lazily built programs; set on construction, cleared on destroy
    private static volatile Map<String, CustomTextureManager.Override> activeGbufferSamplerOverrides =
            java.util.Collections.emptyMap();
    private static volatile Map<String, Integer> activeGbufferSamplerUnits = GBUFFER_SAMPLER_UNITS;
    // Colour targets flipped by at least one earlier pass while the chain is being built; Umbra parity, a custom-texture override on a colortex deactivates once a pass has written it, and only mutated during construction
    private final TreeSet<Integer> flippedAtLeastOnce = new TreeSet<>();
    // Every color index attached to the gbuffer FBOs: the union of all gbuffer-stage DRAWBUFFERS masks, sorted.
    private final int[] gbufferAttachments;
    // Logical colortex index -> physical gbuffer attachment point.
    private final Map<Integer, Integer> gbufferAttachmentPoints = new LinkedHashMap<>();
    // The same mapping as a flat table (-1 for unattached) plus a reusable output array, since drawGbufferBuffers runs on every phase switch
    private final int[] gbufferAttachmentPointByIndex = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    private int[] physicalDrawBufferScratch = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // The gbuffer FBO the world is currently rendering into (switches after the deferred chain runs).
    private UmbraFramebuffer currentGbuffer;
    // Scratch read FBO used to snapshot a gbuffer color target before a program reads and writes it.
    private UmbraFramebuffer gbufferFeedbackCopyFramebuffer;
    // Per linked program, which of colortex4..7 it declares a sampler for (see prepareGbufferFeedbackSamplers); resolved once per program since glGetUniformLocation is a driver call
    private final Int2ObjectOpenHashMap<BitSet> gauxSamplersByProgram = new Int2ObjectOpenHashMap<>();
    // Umbra-style colortex flip snapshot used by opaque gbuffers programs, before the deferred chain runs.
    private BitSet preTranslucentGbufferSamplerFlips = new BitSet();
    // Umbra-style colortex flip snapshot used by translucent gbuffers programs, after the deferred chain runs.
    private BitSet translucentGbufferSamplerFlips = new BitSet();
    // The flip snapshot currently used to bind colortex4..7 for gbuffers programs.
    private BitSet activeGbufferSamplerFlips = new BitSet();
    // The shadow-map pass, or null when the pack declares no shadow program.
    private final UmbraShadowRenderer shadowRenderer;
    // Distant Horizons: the pack's dh_* programs and the LOD framebuffers over this pipeline's gbuffer, null only if construction failed before it was built
    private DhCompat dhCompat;
    private final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();

    // centerDepthSmooth producer + its pack-configurable smoothing half-life (seconds).
    private final CenterDepthSampler centerDepthSampler = new CenterDepthSampler();
    private float centerDepthHalfLife = DEFAULT_CENTER_DEPTH_HALF_LIFE;

    // The pack-wide scalar const directives with Umbra's defaults; the three half-lives are in *deciseconds*, SmoothedFloat's unit
    private int noiseTextureResolution = NoiseTexture.DEFAULT_RESOLUTION;
    private float ambientOcclusionLevel = 1.0f;
    private float wetnessHalfLife = DEFAULT_WETNESS_HALF_LIFE;
    private float drynessHalfLife = DEFAULT_DRYNESS_HALF_LIFE;
    private float eyeBrightnessHalfLife = DEFAULT_EYE_BRIGHTNESS_HALF_LIFE;

    // Optional final-presentation wide-gamut conversion (user-configured, defaults to sRGB = off).
    private final ColorSpaceConverter colorSpaceConverter = new ColorSpaceConverter();

    // Pack-declared shader storage buffers; null until construction.
    private com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder shaderStorageBuffers;

    // indirect.<pass> directives: pass name → {bufferObject index, byte offset}.
    private Map<String, long[]> indirectDispatchPointers = java.util.Collections.emptyMap();

    // GL43 dispatch-indirect binding target (kept as a literal to avoid a hard generated-constant dependency).
    private static final int GL_DISPATCH_INDIRECT_BUFFER = 0x90EE;

    // End-of-frame alt->main copy-back for a buffer the chain left odd-flipped (Umbra's FinalPassRenderer.SwapPass); "from" is a read framebuffer over the ALT texture, the target is MAIN
    private static final class SwapPass {
        final UmbraFramebuffer from;
        final int targetTexture;
        final int index;
        // The target's own dimensions — a size.buffer-sized buffer must not be copied at the screen size.
        final int width;
        final int height;

        SwapPass(int index, UmbraFramebuffer from, int targetTexture, int width, int height) {
            this.index = index;
            this.from = from;
            this.targetTexture = targetTexture;
            this.width = width;
            this.height = height;
        }
    }

    private static final class ClearPass {
        final UmbraFramebuffer framebuffer;
        final float[] color;
        // Clear viewport; 0 means the current render size. Explicitly-sized buffers need their own.
        final int width;
        final int height;

        ClearPass(UmbraFramebuffer framebuffer, float[] color, int width, int height) {
            this.framebuffer = framebuffer;
            this.color = color;
            this.width = width;
            this.height = height;
        }
    }

    private boolean worldRenderingActive;
    private boolean destroyed;
    // True when the pack's fullscreen shaders are modern (#version 130+), which position the quad via ftransform()/gl_TextureMatrix[0] so the chain must run with identity matrices (see runPass)
    private boolean modernPack;
    public UmbraRenderingPipeline(ShaderPack pack) {
        Minecraft mc = Minecraft.getMinecraft();
        this.renderTargets = new UmbraRenderTargets(mc.displayWidth, mc.displayHeight);
        this.shaderDefines = pack.getEnvironmentDefines();
        // The GPU identity macros need a live GL context, so they are added here rather than baked into the pack's environment defines; without them every hardware-workaround gate took its "unknown vendor" branch (Clarity's `immut` expanded to nothing)
        com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.withGpuIdentity(this.shaderDefines,
                LWJGL.glGetString(GL11.GL_VENDOR), LWJGL.glGetString(GL11.GL_RENDERER));
        this.separateHardwareSamplers = pack.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS);
        java.util.Arrays.fill(this.colorBufferClears, true);
        CameraUniforms.attach(this.frameUpdateNotifier);

        boolean initialized = false;
        try {
            this.quadRenderer = new FullscreenQuadRenderer();
            this.defaultNormals = new PlainTexture(127, 127, 255, 255);
            this.defaultSpecular = new PlainTexture(0, 0, 0, 0);
            // Transparent, so a pack doing `mix(color, overlay.rgb, overlay.a)` gets its colour back unchanged.
            this.noOverlayTexture = new PlainTexture(0, 0, 0, 0);
            this.stubShadowMap = new StubShadowMap();
            this.shadowLinearHwSampler = createShadowSampler(true, false, true);
            this.shadowNearestHwSampler = createShadowSampler(false, false, true);
            this.shadowMippedLinearHwSampler = createShadowSampler(true, true, true);
            this.shadowMippedNearestHwSampler = createShadowSampler(false, true, true);
            this.shadowLinearRawSampler = createShadowSampler(true, false, false);
            this.shadowNearestRawSampler = createShadowSampler(false, false, false);
            this.shadowMippedLinearRawSampler = createShadowSampler(true, true, false);
            this.shadowMippedNearestRawSampler = createShadowSampler(false, true, false);

            List<ProgramSource> fullscreenSources = collectFullscreenSources(pack);
            // colortexNFormat / clear directives may live in ANY program stage, and Umbra/OptiFine scan every one; Sildur declares its HDR formats in gbuffers_textured.fsh, and a fullscreen-only scan left every target RGBA8 and clamped its lighting
            applyPackFormatDirectives(collectAllProgramSources(pack));
            // After the directive scan: `const int noiseTextureResolution` sizes this, and a pack sampling noisetex at an assumed resolution gets the wrong spatial frequency if we guess 256
            this.noiseTexture = new NoiseTexture(this.noiseTextureResolution);
            // size.buffer.colortexN must land before any target materialises: Photon's sky map is authored against a 192x108 colortex4 indexed by absolute texel
            pack.getProperties().getBufferSizes().forEach((index, size) -> {
                if (index < 0 || index >= UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                    return;
                }
                boolean[] relative = pack.getProperties().getBufferSizeRelative(index);
                this.renderTargets.setColorSize(index, size[0], size[1], relative);
            });
            materializeSampledTargets(fullscreenSources);

            // Publish the pack's block.properties mapping for the chunk meshers (null keeps raw 1.12.2 IDs); done here since registry resolution needs the game fully initialized
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setBlockStateIds(
                    com.bdmajora.impetus.umbra.material.BlockMaterialMapping.createBlockStateIdTable(pack.getIdMap()));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setBlockRenderLayers(
                    com.bdmajora.impetus.umbra.material.BlockMaterialMapping.createBlockRenderLayerTable(pack.getIdMap()));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setItemIds(pack.getIdMap().getItemIdMap());
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setEntityIds(pack.getIdMap().getEntityIdMap());
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setVoxelRenderDistanceChunks(
                    mc.gameSettings.renderDistanceChunks);

            // Custom images/textures must exist before any program compiles, since sampler-unit assignment consults the overrides (image uniforms are plain glUniform1i assignments like samplers)
            int firstCustomUnit = this.separateHardwareSamplers
                    ? SHADOW_TEX_1_HW_UNIT + 1 : CUSTOM_TEX_FIRST_UNIT;
            this.customTextureManager = new CustomTextureManager(pack, samplerUnitsByStage(), COLOR_TARGETS_BY_NAME,
                    firstCustomUnit, maxProgrammableTextureUnit());
            this.customImageManager = new CustomImageManager(pack.getProperties().getUmbraCustomImages(),
                    this.customTextureManager.getNextAvailableUnit(), maxProgrammableTextureUnit(),
                    mc.displayWidth, mc.displayHeight);
            allocateRenderTargetImageUnits(collectAllProgramSources(pack));
            activeGbufferSamplerUnits = GBUFFER_SAMPLER_UNITS;
            activeGbufferSamplerOverrides = mergedStageOverrides(TextureStage.GBUFFERS_AND_SHADOW);

            // Custom uniforms must exist before any program compiles, so every compile path can register them.
            com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.set(
                    pack.getProperties().getCustomUniforms().build());

            // Pack-declared SSBOs (bufferObject.<index> directives), zero-filled and bound at fixed indices.
            this.shaderStorageBuffers = new com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder(
                    com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder.parseDefinitions(
                            pack.getProperties().getRaw()),
                    mc.displayWidth, mc.displayHeight);
            this.indirectDispatchPointers = parseIndirectPointers(pack.getProperties().getRaw());

            this.prepareBeforeShadow = pack.getProperties().getPrepareBeforeShadow().orElse(Boolean.FALSE);
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setDynamicHandLight(
                    pack.getProperties().getDynamicHandLight().orElse(Boolean.TRUE));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setSeparateAo(
                    pack.getProperties().getSeparateAo().orElse(Boolean.FALSE));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setOldLighting(
                    pack.getProperties().getOldLighting().orElse(Boolean.TRUE));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setOldHandLight(
                    pack.getProperties().getOldHandLight().orElse(Boolean.TRUE));
            com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setVoxelizeLightBlocks(
                    pack.getProperties().getVoxelizeLightBlocks().orElse(Boolean.FALSE));
            this.allowConcurrentCompute = pack.getProperties().getAllowConcurrentCompute().orElse(Boolean.FALSE);
            this.rainDepth = pack.getProperties().getRainDepth().orElse(Boolean.FALSE);
            this.beaconBeamDepth = pack.getProperties().getBeaconBeamDepth().orElse(Boolean.FALSE);
            this.frustumCulling = pack.getProperties().getFrustumCulling().orElse(Boolean.TRUE);
            this.occlusionCulling = pack.getProperties().getOcclusionCulling().orElse(Boolean.TRUE);
            this.skipAllRendering = pack.getProperties().getSkipAllRendering().orElse(Boolean.FALSE);
            this.separateEntityDraws = pack.getProperties().getSeparateEntityDraws().orElse(Boolean.FALSE);
            // Umbra's resolution order: an explicit directive wins, else a pack with a deferred chain not using separate entity draws gets `after` (particles not lit by deferred), else `mixed`
            this.particleOrdering = pack.getProperties().getParticleOrdering().orElseGet(() -> {
                boolean hasDeferred = pack.getProgramSet().get(ProgramArrayId.Deferred, 0).isPresent();
                return hasDeferred && !this.separateEntityDraws ? "after" : "mixed";
            });
            // BlockRenderLayer order on 1.12.2 is SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT.
            String[] backFaceKeys = {"solid", "cutoutMipped", "cutout", "translucent"};
            for (int i = 0; i < backFaceKeys.length; i++) {
                this.backFaceCulling[i] = pack.getProperties()
                        .getBackFaceCulling(backFaceKeys[i]).orElse(Boolean.TRUE);
            }
            this.gbufferPrograms = new GbufferPrograms(pack, GBUFFER_SAMPLER_UNITS, gbufferSamplerOverrideUnits());
            this.gbufferAttachments = computeGbufferAttachments(pack, terrainDrawBuffers(pack));
            Arrays.fill(this.gbufferAttachmentPointByIndex, -1);
            for (int i = 0; i < this.gbufferAttachments.length; i++) {
                this.gbufferAttachmentPoints.put(this.gbufferAttachments[i], i);
                this.gbufferAttachmentPointByIndex[this.gbufferAttachments[i]] = i;
                // A gbuffer FBO mixing attachment sizes renders into their intersection, silently shrinking the world pass; packs size reflection/bloom buffers this way but never gbuffer outputs, so surface it as a pack bug
                if (this.renderTargets.hasCustomSize(this.gbufferAttachments[i])) {
                    LOGGER.warn("[Umbra] colortex{} declares its own size but is also a gbuffer output; the world pass "
                                    + "would be clipped to {}x{}", this.gbufferAttachments[i],
                            this.renderTargets.getWidth(this.gbufferAttachments[i]),
                            this.renderTargets.getHeight(this.gbufferAttachments[i]));
                }
            }
            this.shadowRenderer = createShadowRenderer(pack);
            allocateShadowColorImageUnits(collectAllProgramSources(pack));
            BufferFlipper flipper = this.renderTargets.getBufferFlipper();

            // Bake the single schedule from the reset flip state, then record which buffers the chain leaves odd-flipped for the end-of-frame alt->main copy-back, so next frame's baked FBOs and snapshots are valid without per-frame parity
            buildSchedule(pack, flipper);
            buildSwapPasses(flipper);
            buildClearPasses();

            // After the schedule: the LOD framebuffers attach the gbuffer's front textures as baked above; dhShadow.enabled defaults on like Iris
            this.dhCompat = new DhCompat(this, pack, pack.getProperties().getDhShadowEnabled().orElse(Boolean.TRUE));

            // Pipeline setup creates and checks Umbra FBOs as a side effect; give Minecraft's main target back before vanilla's next post-render GL check
            LWJGL.glUseProgram(0);
            bindMainRenderTarget(mc);
            restoreMainDrawReadBuffers(mc);
            restoreTextureUnits();
            initialized = true;
        } finally {
            if (!initialized) {
                destroy();
            }
        }
    }

    // Highest unit a program can address, from the driver
    private static int maxProgrammableTextureUnit() {
        return Math.max(CUSTOM_TEX_FIRST_UNIT - 1, LWJGL.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) - 1);
    }

    // A sampler object for a shadow depth unit: depth-compare on for the shadow2D flavour, off for the raw-depth flavour a plain sampler2D declaration needs; a bound sampler object replaces the texture's own parameters wholesale, so the raw flavour reads stored depth even though the texture itself carries the compare mode
    private static int createShadowSampler(boolean linear, boolean mipmapped, boolean compare) {
        int sampler = LWJGL.glGenSamplers();
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, mipmapped
                ? (linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST)
                : (linear ? GL11.GL_LINEAR : GL11.GL_NEAREST));
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_MODE,
                compare ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE);
        return sampler;
    }

    // ------------------------------------------------------------------ construction

    // The numbered families that render full-screen quads into the color targets, in the order they run.
    private static final ProgramArrayId[] FULLSCREEN_FAMILIES = {
            ProgramArrayId.Begin, ProgramArrayId.Prepare, ProgramArrayId.Deferred, ProgramArrayId.Composite
    };
    // NB: Setup is deliberately absent — it is compute-only, so it contributes no sampled targets or draw buffers.

    private static void collectFamily(ShaderPack pack, ProgramArrayId id, List<ProgramSource> sources) {
        for (int i = 0; i < id.getNumPrograms(); i++) {
            pack.getProgramSet().get(id, i).ifPresent(sources::add);
        }
    }

    // Every composite, deferred and final program the pack ships, in pass order
    private List<ProgramSource> collectFullscreenSources(ShaderPack pack) {
        List<ProgramSource> sources = new ArrayList<>();
        for (ProgramArrayId id : FULLSCREEN_FAMILIES) {
            collectFamily(pack, id, sources);
        }
        pack.getProgramSet().get(ProgramId.Final).ifPresent(sources::add);
        return sources;
    }

    // Every present program source (all gbuffer families, shadow, deferred/composite arrays, final) for directive scanning only; format and clear directives sit in block comments wherever the author chose and Umbra/OptiFine scan the whole pack, and duplicates are idempotent
    private List<ProgramSource> collectAllProgramSources(ShaderPack pack) {
        List<ProgramSource> sources = new ArrayList<>();
        for (ProgramId id : ProgramId.VALUES) {
            pack.getProgramSet().get(id).ifPresent(sources::add);
        }
        for (ProgramArrayId id : ProgramArrayId.values()) {
            collectFamily(pack, id, sources);
        }
        return sources;
    }

    private static final Pattern RENDER_TARGET_IMAGE = Pattern.compile("\\bcolorimg(\\d{1,2})\\b");

    // Reserves an image unit for every colorimgN the pack references, above the image.<name> units; Umbra assigns per program, but with fixed units one global unit per target is equivalent since the binding is re-established per pass (see bindRenderTargetImages)
    private void allocateRenderTargetImageUnits(List<ProgramSource> sources) {
        TreeSet<Integer> referenced = new TreeSet<>();
        for (ProgramSource source : sources) {
            collectRenderTargetImages(source.getVertexSource().orElse(null), referenced);
            collectRenderTargetImages(source.getFragmentSource().orElse(null), referenced);
            collectRenderTargetImages(source.getGeometrySource().orElse(null), referenced);
            for (String compute : source.getComputeSources()) {
                collectRenderTargetImages(compute, referenced);
            }
        }
        if (referenced.isEmpty()) {
            return;
        }

        int unit = this.customImageManager.getNextAvailableImageUnit();
        int limit = this.customImageManager.getHardwareImageUnits();
        for (Integer index : referenced) {
            if (unit >= limit) {
                LOGGER.error("[Umbra] Out of image units for colorimg{} (max {}); ignoring it", index, limit);
                continue;
            }
            // Umbra createIfUnsure()s the target: a buffer nothing samples but a compute writes still has to exist.
            this.renderTargets.getOrCreate(index);
            this.renderTargetImageUnits.put(index, unit);
            unit++;
        }
    }

    // Reserves image units for shadowcolorimg0/1, after the render-target images so they share one ascending allocation, and only when a shadow renderer exists to own the textures
    private void allocateShadowColorImageUnits(List<ProgramSource> sources) {
        if (this.shadowRenderer == null) {
            return;
        }
        TreeSet<Integer> referenced = new TreeSet<>();
        Pattern pattern = Pattern.compile("\\bshadowcolorimg([01])\\b");
        for (ProgramSource source : sources) {
            for (String stage : new String[]{source.getVertexSource().orElse(null),
                    source.getFragmentSource().orElse(null), source.getGeometrySource().orElse(null)}) {
                if (stage == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(stage);
                while (matcher.find()) {
                    referenced.add(Integer.parseInt(matcher.group(1)));
                }
            }
            for (String compute : source.getComputeSources()) {
                if (compute == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(compute);
                while (matcher.find()) {
                    referenced.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        if (referenced.isEmpty()) {
            return;
        }

        int unit = this.customImageManager.getNextAvailableImageUnit() + this.renderTargetImageUnits.size();
        int limit = this.customImageManager.getHardwareImageUnits();
        for (Integer index : referenced) {
            if (unit >= limit) {
                LOGGER.error("[Umbra] Out of image units for shadowcolorimg{} (max {}); ignoring it", index, limit);
                continue;
            }
            this.shadowColorImageUnits.put(index, unit);
            unit++;
        }
    }

    // Finds colorimgN references so those targets get image bindings
    private static void collectRenderTargetImages(String source, TreeSet<Integer> out) {
        if (source == null) {
            return;
        }
        Matcher matcher = RENDER_TARGET_IMAGE.matcher(source);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                out.add(index);
            }
        }
    }

    // OptiFine's pre-colortexNFormat way of upgrading gaux4/colortex7 as a block comment (GAUX4FORMAT:RGB32F); only OptiFine's three formats are honoured, matching Umbra's PackRenderTargetDirectives
    private void applyLegacyGaux4Format(List<ProgramSource> sources) {
        Pattern directive = Pattern.compile("/\\*\\s*GAUX4FORMAT\\s*:\\s*(\\w+)\\s*\\*/");
        for (ProgramSource source : sources) {
            for (String stage : activeDirectiveStages(source)) {
                Matcher matcher = directive.matcher(stage);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    if (!name.equals("RGBA32F") && !name.equals("RGB32F") && !name.equals("RGB16")) {
                        LOGGER.warn("[Umbra] Ignoring GAUX4FORMAT:{} in '{}' — only RGBA32F, RGB32F and RGB16 are valid; "
                                + "use `const int colortex7Format = {};` instead", name, source.getName(), name);
                        continue;
                    }
                    InternalTextureFormat.fromString(name).ifPresent(format -> {
                        this.renderTargets.setColorFormat(7, format);
                    });
                }
            }
        }
    }

    // The stages a directive may be declared in, each with the pack's conditionals resolved against its macro set; raw scans take the LAST textual match so a directive under a disabled #if wins (Body Camera's colortex0Format/colortex5Clear/colortex0MipmapEnabled all resolved to the dead branch and whited the screen). Options are applied as real #define lines, not macro-map entries; vertex first, fragment last since Umbra trusts the fragment stage
    private List<String> activeDirectiveStages(ProgramSource source) {
        Map<String, String> macros =
                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(this.shaderDefines, source.getName());
        List<String> stages = new ArrayList<>(2);
        for (Optional<String> stage : java.util.Arrays.asList(source.getVertexSource(), source.getFragmentSource())) {
            if (stage.isPresent()) {
                stages.add(GlslPreprocessor.resolveConditionals(stage.get(), macros));
            }
        }
        return stages;
    }

    // Applies the pack's render-target format directives (const int colortex0Format = RGBA16, including legacy gcolorFormat names) declared as consts anywhere in the fullscreen sources; must precede target materialisation or HDR packs clamp and blow out
    private void applyPackFormatDirectives(List<ProgramSource> sources) {
        Pattern formatDirective = Pattern.compile("const\\s+int\\s+(\\w+?)Format\\s*=\\s*(\\w+)\\s*;");
        Pattern clearDirective = Pattern.compile("const\\s+bool\\s+(\\w+?)Clear\\s*=\\s*(true|false)\\s*;");
        Pattern clearColorDirective = Pattern.compile("const\\s+vec4\\s+(\\w+?)ClearColor\\s*=\\s*vec4\\s*\\(([^)]*)\\)\\s*;");
        applyLegacyGaux4Format(sources);
        StringBuilder scalarText = new StringBuilder();
        for (ProgramSource source : sources) {
            for (String stage : activeDirectiveStages(source)) {
                // The pack-wide scalar directives are name-keyed rather than target-keyed, so one concatenation serves; terminate each stage so an unterminated construct cannot run into the next
                scalarText.append(stage).append('\n');
                Matcher matcher = formatDirective.matcher(stage);
                while (matcher.find()) {
                    Integer index = COLOR_TARGETS_BY_NAME.get(matcher.group(1));
                    if (index == null || index >= UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                        continue; // not a color-target name (e.g. shadowcolor0Format — shadow pass comes later)
                    }
                    Optional<InternalTextureFormat> format = InternalTextureFormat.fromString(matcher.group(2));
                    if (!format.isPresent()) {
                        LOGGER.warn("[Umbra] '{}' requests unknown format {} for colortex{}; keeping RGBA8",
                                source.getName(), matcher.group(2), index);
                        continue;
                    }
                    try {
                        this.renderTargets.setColorFormat(index, format.get());
                    } catch (IllegalStateException e) {
                        LOGGER.warn("[Umbra] Format directive for colortex{} came after the target was created", index);
                    }
                }
                Matcher clearMatcher = clearDirective.matcher(stage);
                while (clearMatcher.find()) {
                    Integer index = COLOR_TARGETS_BY_NAME.get(clearMatcher.group(1));
                    if (index != null && index < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                        this.colorBufferClears[index] = Boolean.parseBoolean(clearMatcher.group(2));
                    }
                }
                Matcher clearColorMatcher = clearColorDirective.matcher(stage);
                while (clearColorMatcher.find()) {
                    Integer index = COLOR_TARGETS_BY_NAME.get(clearColorMatcher.group(1));
                    if (index != null && index < UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                        float[] color = parseVec4(clearColorMatcher.group(2));
                        if (color != null) {
                            this.colorBufferClearColors[index] = color;
                        }
                    }
                }
            }
        }
        applyPackScalarDirectives(new ConstDirectives(scalarText.toString()));
    }

    // The pack-wide scalar const directives Umbra collects into PackDirectives; sunPathRotation and the shadow directives are read on the shadow path, and the half-lives are in *deciseconds* with ambientOcclusionLevel clamped to 0..1
    private void applyPackScalarDirectives(ConstDirectives consts) {
        this.centerDepthHalfLife = consts.getFloat("centerDepthHalflife", DEFAULT_CENTER_DEPTH_HALF_LIFE);

        // noisetex size; a pack sampling `texture2D(noisetex, uv * 32)` against a 256x256 texture gets the wrong spatial frequency (Body Camera's water normals)
        int noiseResolution = consts.getInt("noiseTextureResolution", NoiseTexture.DEFAULT_RESOLUTION);
        if (noiseResolution > 0) {
            // NoiseTexture allocates resolution^2 * 4 bytes twice, so a pack typo like 65536 would OOM the client; 4096 is far past anything real
            if (noiseResolution > 4096) {
                LOGGER.warn("[Umbra] Pack requests noiseTextureResolution={}; clamping to 4096", noiseResolution);
                noiseResolution = 4096;
            }
            this.noiseTextureResolution = noiseResolution;
        }

        // Vanilla's baked AO strength, pushed into WorldRenderingSettings for the block-model AO computation; 1.0 is vanilla, 0.0 disables it so the pack does its own
        float aoLevel = consts.getFloat("ambientOcclusionLevel", 1.0f);
        this.ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, aoLevel));
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings
                .setAmbientOcclusionLevel(this.ambientOcclusionLevel);

        // `wetness` and `eyeBrightnessSmooth` smoothing rates.
        this.wetnessHalfLife = consts.getFloat("wetnessHalflife", DEFAULT_WETNESS_HALF_LIFE);
        this.drynessHalfLife = consts.getFloat("drynessHalflife", DEFAULT_DRYNESS_HALF_LIFE);
        this.eyeBrightnessHalfLife =
                consts.getFloat("eyeBrightnessHalflife", DEFAULT_EYE_BRIGHTNESS_HALF_LIFE);
        EyeBrightnessTracker.setHalfLives(this.wetnessHalfLife, this.drynessHalfLife, this.eyeBrightnessHalfLife);
    }

    // Four comma-separated floats
    private static float[] parseVec4(String value) {
        String[] parts = value.split(",");
        try {
            if (parts.length == 1) {
                float scalar = parseFloatLiteral(parts[0]);
                return new float[]{scalar, scalar, scalar, scalar};
            }
            if (parts.length != 4) {
                return null;
            }
            return new float[]{
                    parseFloatLiteral(parts[0]),
                    parseFloatLiteral(parts[1]),
                    parseFloatLiteral(parts[2]),
                    parseFloatLiteral(parts[3])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Tolerates a trailing f
    private static float parseFloatLiteral(String value) {
        String cleaned = value.trim();
        if (cleaned.endsWith("f") || cleaned.endsWith("F")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return Float.parseFloat(cleaned);
    }

    // Creates every colour target the composite/final programs declare a sampler for, so per-pass sampler snapshots can bind them even when the writing pass comes later in the chain
    private void materializeSampledTargets(List<ProgramSource> sources) {
        StringBuilder allSource = new StringBuilder();
        for (ProgramSource source : sources) {
            source.getVertexSource().ifPresent(allSource::append);
            source.getFragmentSource().ifPresent(allSource::append);
        }
        String text = allSource.toString();
        for (Map.Entry<String, Integer> entry : COLOR_TARGETS_BY_NAME.entrySet()) {
            int target = entry.getValue();
            if (Pattern.compile("\\bsampler2D\\s+" + entry.getKey() + "\\b").matcher(text).find()) {
                this.renderTargets.getOrCreate(target);
            }
        }
    }

    // Builds the shadow renderer when the pack declares a shadow program, with OptiFine's shadow projection directives parsed from anywhere in the sources
    private UmbraShadowRenderer createShadowRenderer(ShaderPack pack) {
        // Const directives may live in ANY source (packs put them in includes flattened into every program), so scan the gbuffer programs and the whole fullscreen chain
        StringBuilder allSources = new StringBuilder();
        StringBuilder activeSources = new StringBuilder();
        for (ProgramId id : new ProgramId[]{ProgramId.Shadow, ProgramId.Terrain, ProgramId.Water, ProgramId.Final}) {
            pack.getProgramSet().get(id).ifPresent(source -> {
                appendDirectiveSource(allSources, activeSources, source, source.getVertexSource());
                appendDirectiveSource(allSources, activeSources, source, source.getFragmentSource());
            });
        }
        for (ProgramSource source : collectFullscreenSources(pack)) {
            appendDirectiveSource(allSources, activeSources, source, source.getFragmentSource());
        }
        // `text` keeps the raw sources for the `#define`-form directives, which conditional resolution consumes.
        String text = allSources.toString();
        ConstDirectives consts = new ConstDirectives(activeSources.toString());

        // Tilt of the sun/moon's daily arc, needed by the celestial-position uniforms whether or not the pack draws shadows, so set before the no-shadow early-out
        float sunPathRotation = consts.getFloat("sunPathRotation", 0.0f);
        CelestialUniforms.setSunPathRotation(sunPathRotation);

        Optional<ProgramSource> shadowSource = pack.getProgramSet().get(ProgramId.Shadow);
        if (!shadowSource.isPresent()) {
            return null;
        }
        // OptiFine's pre-const spelling of the same settings (`#define SHADOWRES 2048`); Umbra accepts both, and the shaders.properties keys override either
        int resolution = consts.getInt("shadowMapResolution", parseDefineInt(text, "SHADOWRES", 1024));
        if (resolution <= 0) {
            // A pack error, but the literal grammar admits a sign and a non-positive texture size would fail allocation; E-LITE declares 10 in its shadows-off branch, which conditional resolution hides, so this only backstops the value reaching GL
            LOGGER.warn("[Umbra] Pack declares shadowMapResolution={}; falling back to 1024", resolution);
            resolution = 1024;
        }
        float distance = consts.getFloat("shadowDistance", parseDefineFloat(text, "SHADOWHPL", 160.0f));
        // With Distant Horizons LODs casting into the map and no declared planes, the ortho depth range spans the LOD distance like Iris's -1 "auto" planes do, or a mountain LOD a few hundred blocks along the light is clipped out of the map; OptiFine's defaults otherwise
        boolean dhShadows = DhCompat.hasRenderingEnabled()
                && pack.getProgramSet().get(ProgramId.DhShadow).isPresent()
                && pack.getProperties().getDhShadowEnabled().orElse(Boolean.TRUE);
        float nearPlane = consts.getFloat("shadowNearPlane",
                dhShadows ? -DhCompat.getRenderDistance() : UmbraShadowRenderer.DEFAULT_NEAR_PLANE);
        float farPlane = consts.getFloat("shadowFarPlane",
                dhShadows ? DhCompat.getRenderDistance() : UmbraShadowRenderer.DEFAULT_FAR_PLANE);
        float intervalSize = consts.getFloat("shadowIntervalSize", UmbraShadowRenderer.DEFAULT_INTERVAL_SIZE);
        Float shadowMapFov = consts.getFloat("shadowMapFov");
        if (shadowMapFov == null) {
            Matcher legacyFov = Pattern.compile("(?m)^\\s*#define\\s+SHADOWFOV\\s+([0-9.]+)").matcher(text);
            if (legacyFov.find()) {
                try {
                    shadowMapFov = Float.parseFloat(legacyFov.group(1));
                } catch (NumberFormatException ignored) {
                    // keep the const-derived value (null = no FOV override)
                }
            }
        }
        // shaders.properties wins over anything declared in GLSL, matching Umbra's directive precedence.
        resolution = pack.getProperties().getShadowMapResolution().orElse(resolution);
        distance = pack.getProperties().getShadowDistance().isPresent()
                ? pack.getProperties().getShadowDistance().getAsInt() : distance;
        // `const float voxelDistance` overrides the shadow distance for voxelization only, since colored-lighting packs want a tighter radius than their shadow map (Umbra PackShadowDirectives)
        float voxelDistance = consts.getFloat("voxelDistance", 0.0f);
        // `shadowDistanceRenderMul` scales the shadow pass's CULLING distance, not the projection; Umbra's -1 sentinel falls back to a user setting that does not exist here, so unset or negative means no scaling
        float shadowDistanceRenderMul = consts.getFloat("shadowDistanceRenderMul", -1.0f);
        float cullDistance = shadowDistanceRenderMul >= 0.0f ? distance * shadowDistanceRenderMul : distance;
        float voxelRadius = voxelDistance > 0.0f ? voxelDistance : distance;
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setVoxelRenderDistanceChunks(
                Math.max(1, Math.round(voxelRadius / 16.0f)));
        parseShadowDepthSamplingSettings(consts);
        // The FF shadow program (entities/block entities) belongs to the gbuffers custom-texture stage.
        Map<String, Integer> shadowSamplerUnits = new LinkedHashMap<>(GBUFFER_SAMPLER_UNITS);
        shadowSamplerUnits.putAll(gbufferSamplerOverrideUnits());
        try {
            ShadowContentSettings content = ShadowContentSettings.from(pack.getProperties());
            // Umbra's voxelization detection: a shadow geometry stage, or the pack declaring custom images.
            boolean packVoxelizes = shadowSource.get().getGeometrySource().isPresent()
                    || !pack.getProperties().getUmbraCustomImages().isEmpty();
            return new UmbraShadowRenderer(resolution, distance, nearPlane, farPlane, intervalSize, shadowMapFov,
                    sunPathRotation,
                    shadowSource.get(),
                    // Both resolve through the ProgramSet fallback chain, so a pack shipping only `shadow` hands the renderer the same ProgramSource for all three and it compiles once
                    pack.getProgramSet().get(ProgramId.ShadowEntities).orElse(shadowSource.get()),
                    pack.getProgramSet().get(ProgramId.ShadowBlock).orElse(shadowSource.get()),
                    shadowSamplerUnits, this.shaderDefines,
                    this.shadowHardwareFiltering, this.shadowMipmap, this.shadowNearest,
                    this.separateHardwareSamplers, this::bindShaderPackResources, content,
                    voxelDistance, cullDistance, packVoxelizes);
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to create the shadow renderer; shadows disabled", e);
            return null;
        }
    }

    // Appends one stage to both scans, raw as authored and active with conditionals resolved; consts must be read from active, since Complementary declares shadowMapResolution 4096 under one branch and 2048 under #else, and the wrong map size sent every texelFetch-based light shaft into one quadrant of cleared depth, shining through terrain
    private void appendDirectiveSource(StringBuilder raw, StringBuilder active, ProgramSource source,
                                       Optional<String> stage) {
        if (!stage.isPresent()) {
            return;
        }
        raw.append(stage.get());
        active.append(GlslPreprocessor.resolveConditionals(stage.get(),
                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(this.shaderDefines, source.getName())));
        // Stages are resolved individually, so terminate the appended text so an unterminated construct cannot run into the next file
        active.append('\n');
    }

    // OptiFine's legacy #define <NAME> <value> spelling of a shadow directive.
    private static int parseDefineInt(String text, String name, int fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*#define\\s+" + name + "\\s+(\\d+)").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    // #define NAME value in shader text
    private static float parseDefineFloat(String text, String name, float fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*#define\\s+" + name + "\\s+([0-9.]+)").matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Float.parseFloat(matcher.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Reads the shadowHardwareFiltering and shadowtex filtering constants
    private void parseShadowDepthSamplingSettings(ConstDirectives consts) {
        Arrays.fill(this.shadowHardwareFiltering, false);
        Arrays.fill(this.shadowMipmap, false);
        Arrays.fill(this.shadowNearest, false);

        applyBothThenIndexed(consts, "shadowHardwareFiltering", "shadowHardwareFiltering", "", this.shadowHardwareFiltering);
        applyBothThenIndexed(consts, "generateShadowMipmap", "shadowtex", "Mipmap", this.shadowMipmap);
        consts.getOptionalBool("shadowtexMipmap").ifPresent(value -> this.shadowMipmap[0] = value);
        applyBothThenIndexed(consts, null, "shadowtex", "Nearest", this.shadowNearest);
        consts.getOptionalBool("shadowtexNearest").ifPresent(value -> this.shadowNearest[0] = value);
        for (int i = 0; i < this.shadowNearest.length; i++) {
            final int index = i;
            consts.getOptionalBool("shadow" + i + "MinMagNearest")
                    .ifPresent(value -> this.shadowNearest[index] = value);
        }
    }

    // A shared constant sets both entries, then per-index constants (<prefix><i><suffix>) override
    private static void applyBothThenIndexed(ConstDirectives consts, String bothName, String indexedPrefix,
                                             String indexedSuffix, boolean[] values) {
        if (bothName != null) {
            consts.getOptionalBool(bothName).ifPresent(value -> Arrays.fill(values, value));
        }
        for (int i = 0; i < values.length; i++) {
            final int index = i;
            consts.getOptionalBool(indexedPrefix + i + indexedSuffix)
                    .ifPresent(value -> values[index] = value);
        }
    }

    // The color buffers the terrain program writes, per its DRAWBUFFERS directive.
    private int[] terrainDrawBuffers(ShaderPack pack) {
        return programDrawBuffers(pack, ProgramId.Terrain, "gbuffers_terrain");
    }

    // One gbuffer program's DRAWBUFFERS mask resolved against the macros it actually compiles with; a legacy-branch program declares a different mask (Sildur's gbuffers_water is 41 with IS_IRIS, 412 without), and a buffer missing from the attachment set reroutes to colortex0
    private int[] programDrawBuffers(ShaderPack pack, ProgramId id, String fallbackName) {
        ProgramSource source = pack.getProgramSet().get(id).orElse(null);
        String fragment = source == null ? null : source.getFragmentSource().orElse(null);
        String name = source == null ? fallbackName : source.getName();
        if (fragment == null) {
            return DrawBuffers.DEFAULT.clone();
        }
        return sanitizeDrawBuffers(name, DrawBuffers.parseActive(fragment,
                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(this.shaderDefines, name)));
    }

    // The union of every gbuffer-stage program's DRAWBUFFERS mask plus colortex0, all attached up front since a mask naming an attachment without an image makes the FBO incomplete
    private int[] computeGbufferAttachments(ShaderPack pack, int[] terrainDrawBuffers) {
        TreeSet<Integer> attachments = new TreeSet<>();
        attachments.add(0); // colortex0 must exist — it is what final/blit shows
        for (int buffer : terrainDrawBuffers) {
            attachments.add(buffer);
        }
        for (int buffer : programDrawBuffers(pack, ProgramId.Water, "gbuffers_water")) {
            attachments.add(buffer);
        }
        for (GbufferPrograms.Entry entry : this.gbufferPrograms.entries()) {
            for (int buffer : entry.getDrawBuffers()) {
                attachments.add(buffer);
            }
        }
        int[] result = new int[attachments.size()];
        int i = 0;
        for (int buffer : attachments) {
            result[i++] = buffer;
        }
        return result;
    }

    // A gbuffer FBO world rendering is redirected into: every gbuffer-stage target's current "front" side plus the shared depth texture; the mask starts colour-only and setPhase and the terrain override switch it per program, OptiFine-style
    private UmbraFramebuffer createGbufferFramebuffer(BufferFlipper flipper) {
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        for (Map.Entry<Integer, Integer> entry : this.gbufferAttachmentPoints.entrySet()) {
            int logicalIndex = entry.getKey();
            int attachmentPoint = entry.getValue();
            framebuffer.addColorAttachment(logicalIndex, attachmentPoint, frontTexture(flipper, logicalIndex));
        }
        framebuffer.addDepthAttachment(this.renderTargets.getDepthTexture().getTextureId());
        drawGbufferBuffers(framebuffer, FIXED_FUNCTION_MASK);
        checkFramebufferComplete(framebuffer, "gbuffer", this.gbufferAttachments);
        return framebuffer;
    }

    // Fails loudly with the purpose and attachments named
    private static void checkFramebufferComplete(UmbraFramebuffer framebuffer, String purpose, int[] buffers) {
        int status = framebuffer.getStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Umbra " + purpose + " framebuffer for buffers "
                    + Arrays.toString(buffers) + ": status=" + status);
        }
    }

    // Sets the FBO's draw buffers from a program's logical colortex list
    private void drawGbufferBuffers(UmbraFramebuffer framebuffer, int[] logicalDrawBuffers) {
        int[] physicalDrawBuffers = this.physicalDrawBufferScratch;
        if (physicalDrawBuffers.length < logicalDrawBuffers.length) {
            physicalDrawBuffers = this.physicalDrawBufferScratch = new int[logicalDrawBuffers.length];
        }
        int written = 0;
        for (int i = 0; i < logicalDrawBuffers.length; i++) {
            int logical = logicalDrawBuffers[i];
            int attachmentPoint = logical >= 0 && logical < this.gbufferAttachmentPointByIndex.length ? this.gbufferAttachmentPointByIndex[logical] : -1;
            if (attachmentPoint < 0) {
                LOGGER.warn("[Umbra] Gbuffer draw buffer colortex{} is not attached; routing output slot {} to colortex0",
                        logical, i);
                attachmentPoint = Math.max(0, this.gbufferAttachmentPointByIndex[0]);
            } else {
                written |= 1 << logical;
            }
            physicalDrawBuffers[i] = attachmentPoint;
        }
        // Umbra parity: a gbuffer FBO holds ONLY the buffers the current program writes, so a program sampling a colortex it does not write (gbuffers_terrain reading gaux4 for fog) reads a detached texture instead of a feedback loop's garbage
        framebuffer.retainColorAttachments(written);
        framebuffer.drawBuffers(physicalDrawBuffers, logicalDrawBuffers.length);
    }

    // Bakes one frame's ping-pong schedule from the flipper's current state, advancing it as it goes; family order matches Umbra: begin, prepare, (gbuffers), deferred, (translucents), composite, final
    private void buildSchedule(ShaderPack pack, BufferFlipper flipper) {
        buildFamily(pack, ProgramArrayId.Setup, TextureStage.SETUP, flipper, this.setupPasses);
        buildFamily(pack, ProgramArrayId.Begin, TextureStage.BEGIN, flipper, this.beginPasses);
        buildComputePasses(pack, flipper);
        buildFamily(pack, ProgramArrayId.Prepare, TextureStage.PREPARE, flipper, this.preparePasses);

        this.gbufferFramebuffer = createGbufferFramebuffer(flipper);
        this.preTranslucentGbufferSamplerFlips = flipper.snapshot();

        applyExplicitPreFlips(pack.getProperties().getExplicitFlips("deferred_pre"), flipper, "deferred_pre");
        buildFamily(pack, ProgramArrayId.Deferred, TextureStage.DEFERRED, flipper, this.deferredPasses);

        this.translucentGbufferSamplerFlips = flipper.snapshot();
        this.translucentGbufferFramebuffer =
                this.deferredPasses.isEmpty() ? this.gbufferFramebuffer : createGbufferFramebuffer(flipper);

        applyExplicitPreFlips(pack.getProperties().getExplicitFlips("composite_pre"), flipper, "composite_pre");
        buildFamily(pack, ProgramArrayId.Composite, TextureStage.COMPOSITE_AND_FINAL, flipper, this.passes);

        FullscreenPass finalPass = buildFinalPass(pack, flipper);
        if (finalPass != null) {
            this.passes.add(finalPass);
        } else {
            // No (working) final program: show the post-composite colortex0 by blitting it to the screen.
            this.blitSourceFramebuffer = new UmbraFramebuffer();
            this.blitSourceFramebuffer.addColorAttachment(0, frontTexture(flipper, 0));
            this.blitSourceFramebuffer.readBuffer(0);
            checkFramebufferComplete(this.blitSourceFramebuffer, "fallback blit", new int[]{0});
        }
    }

    // Schedules one numbered family in index order: a vertex+fragment pair becomes a drawing pass, a .csh-only entry a compute-only pass (how Photon's deferred4_a.csh runs at all), and computes dispatch before the entry's draw under the same flip state
    private void buildFamily(ShaderPack pack, ProgramArrayId id, TextureStage stage, BufferFlipper flipper,
                             List<FullscreenPass> target) {
        for (int i = 0; i < id.getNumPrograms(); i++) {
            Optional<ProgramSource> source = pack.getProgramSet().get(id, i);
            if (!source.isPresent()) {
                continue;
            }
            String name = source.get().getName();
            if (!isProgramEnabled(pack, name)) {
                continue;
            }
            List<ComputePass> computes = buildFamilyComputePasses(pack, source.get(), stage);
            if (!source.get().hasRasterStages()) {
                if (!computes.isEmpty()) {
                    target.add(FullscreenPass.computeOnly(name, snapshotFrontTextures(flipper), flipper.snapshot(),
                            computes));
                }
                continue;
            }
            FullscreenPass pass = buildCompositePass(pack, source.get(), flipper, computes);
            if (pass != null) {
                target.add(pass);
            } else if (!computes.isEmpty()) {
                // The draw failed to compile but the computes linked: still run them, as Umbra would.
                target.add(FullscreenPass.computeOnly(name, snapshotFrontTextures(flipper), flipper.snapshot(),
                        computes));
            }
        }
    }

    // Gives a pass the viewport of the buffers it writes; Umbra throws on mixed sizes, here the mismatch is logged and the first size wins rather than losing a whole stage of the chain
    private void applyPassViewport(FullscreenPass pass, int[] drawBuffers) {
        for (int buffer : drawBuffers) {
            if (buffer < 0 || buffer >= UmbraRenderTargets.MAX_COLOR_BUFFERS
                    || !this.renderTargets.hasCustomSize(buffer)) {
                continue;
            }
            int width = this.renderTargets.getWidth(buffer);
            int height = this.renderTargets.getHeight(buffer);
            if (pass.viewportWidth == 0) {
                pass.viewportWidth = width;
                pass.viewportHeight = height;
            } else if (pass.viewportWidth != width || pass.viewportHeight != height) {
                LOGGER.warn("[Umbra] Pass '{}' writes buffers of different sizes ({}x{} vs colortex{} at {}x{}); "
                                + "using the first", pass.name, pass.viewportWidth, pass.viewportHeight,
                        buffer, width, height);
            }
        }
    }

    // Applies scale.<program>, separate from applyPassViewport since one reads the written buffers' size and the other shrinks the rasterised rectangle within it; a pass can have both
    private void applyPassViewportScale(FullscreenPass pass, ShaderPack pack) {
        float[] scale = pack.getProperties().getViewportScale(pass.name);
        if (scale == null) {
            return;
        }
        pass.viewportScale = scale[0];
        pass.viewportOffsetX = scale[1];
        pass.viewportOffsetY = scale[2];
    }

    // Honours a pass's flip.<pass>.<buffer> directives before it runs
    private void applyExplicitPreFlips(Map<Integer, Boolean> explicitFlips, BufferFlipper flipper, String name) {
        for (Map.Entry<Integer, Boolean> entry : explicitFlips.entrySet()) {
            if (entry.getValue()) {
                flipper.flip(entry.getKey());
            }
        }
    }

    // Umbra's SwapPass: every buffer the chain leaves odd-flipped has its latest content on ALT, so copy alt->main after final; buffers cleared at frame start are skipped, and gbuffer attachments are not special-cased since Complementary marks some clear=false for temporal contents
    private void buildSwapPasses(BufferFlipper flipper) {
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            if (!flipper.isFlipped(i) || this.renderTargets.get(i) == null || this.colorBufferClears[i]) {
                continue;
            }
            UmbraRenderTarget target = this.renderTargets.get(i);
            UmbraFramebuffer from = new UmbraFramebuffer();
            from.addColorAttachment(0, target.getAltTexture());
            from.readBuffer(0);
            checkFramebufferComplete(from, "swap colortex" + i, new int[]{i});
            this.swapPasses.add(new SwapPass(i, from, target.getMainTexture(),
                    this.renderTargets.getWidth(i), this.renderTargets.getHeight(i)));
        }
    }

    // Schedules the per-frame clears each target needs, with its declared clear colour
    private void buildClearPasses() {
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            if (this.renderTargets.get(i) == null) {
                continue;
            }
            float[] color = defaultClearColor(i);
            int clearWidth = this.renderTargets.hasCustomSize(i) ? this.renderTargets.getWidth(i) : 0;
            int clearHeight = this.renderTargets.hasCustomSize(i) ? this.renderTargets.getHeight(i) : 0;
            this.fullClearPasses.add(new ClearPass(
                    this.renderTargets.createClearFramebuffer(false, new int[]{i}), color, clearWidth, clearHeight));
            this.fullClearPasses.add(new ClearPass(
                    this.renderTargets.createClearFramebuffer(true, new int[]{i}), color, clearWidth, clearHeight));
            if (this.colorBufferClears[i]) {
                this.clearPasses.add(new ClearPass(
                        this.renderTargets.createClearFramebuffer(false, new int[]{i}), color, clearWidth, clearHeight));
                this.clearPasses.add(new ClearPass(
                        this.renderTargets.createClearFramebuffer(true, new int[]{i}), color, clearWidth, clearHeight));
            }
        }
    }

    // colortex0 clears to fog colour, the rest to transparent black, unless overridden
    private float[] defaultClearColor(int index) {
        if (this.colorBufferClearColors[index] != null) {
            return this.colorBufferClearColors[index];
        }
        if (index == 0) {
            return null; // Umbra clears colortex0 to the current fog color by default.
        }
        if (index == 1) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    // The custom-texture stage a fullscreen program belongs to, from its name: beginN -> begin, prepareN -> prepare, deferredN -> deferred, compositeN and final -> composite
    private static TextureStage fullscreenTextureStage(String name) {
        if (name.startsWith(ProgramArrayId.Begin.getBaseName())) {
            return TextureStage.BEGIN;
        }
        if (name.startsWith(ProgramArrayId.Prepare.getBaseName())) {
            return TextureStage.PREPARE;
        }
        if (name.startsWith(ProgramArrayId.Deferred.getBaseName())) {
            return TextureStage.DEFERRED;
        }
        if (name.startsWith(ProgramArrayId.ShadowComposite.getBaseName())) {
            return TextureStage.SHADOWCOMP;
        }
        return TextureStage.COMPOSITE_AND_FINAL;
    }

    // Compiles a fullscreen program and its uniforms once, cached by source name; null if it failed, cached as absent so we do not retry
    private UmbraProgram cachedProgram(ProgramSource source) {
        String name = source.getName();
        if (this.compiledPrograms.containsKey(name)) {
            return this.compiledPrograms.get(name);
        }
        UmbraProgram program = compileFullscreenProgram(source, fullscreenTextureStage(name));
        this.compiledPrograms.put(name, program);
        if (program != null) {
            this.compiledUniforms.put(name, buildUniforms(name, program));
            this.compiledShadowSamplerKinds.put(name, ShadowSamplerKinds.detect(program.getProgram().getGlId()));
        }
        return program;
    }

    // The kinds recorded for a compiled fullscreen program, comparison-only when it never compiled
    private ShadowSamplerKinds shadowSamplerKindsFor(String name) {
        ShadowSamplerKinds kinds = this.compiledShadowSamplerKinds.get(name);
        return kinds != null ? kinds : ShadowSamplerKinds.ALL_COMPARE;
    }

    private FullscreenPass buildCompositePass(ShaderPack pack, ProgramSource source, BufferFlipper flipper,
                                              List<ComputePass> computes) {
        String name = source.getName();
        try {
            UmbraProgram program = cachedProgram(source);
            if (program == null) {
                return null;
            }
            int[] drawBuffers = sanitizeCompositeDrawBuffers(name, program.getDrawBuffers());
            Map<Integer, Boolean> explicitFlips = pack.getProperties().getExplicitFlips(name);
            BitSet flipsBefore = flipper.snapshot();
            BitSet mipmappedBuffers = parseMipmappedBuffers(source);

            // Reads see the current "front" side; the FBO writes the back side; then the written buffers flip.
            int[] colorSamplers = snapshotFrontTextures(flipper);
            UmbraFramebuffer framebuffer = this.renderTargets.createColorFramebuffer(drawBuffers);
            for (int buffer : drawBuffers) {
                if (explicitFlips.get(buffer) == Boolean.FALSE) {
                    continue;
                }
                flipper.flip(buffer);
                // Later passes' colortex custom-texture overrides deactivate for buffers a pass has written.
                this.flippedAtLeastOnce.add(buffer);
            }
            for (Map.Entry<Integer, Boolean> entry : explicitFlips.entrySet()) {
                if (entry.getValue()) {
                    flipper.flip(entry.getKey());
                    this.flippedAtLeastOnce.add(entry.getKey());
                }
            }
            BitSet flipsAfter = flipper.snapshot();

            FullscreenPass pass = new FullscreenPass(name, program, this.compiledUniforms.get(name), framebuffer,
                    colorSamplers, drawBuffers, ProgramBlendState.from(pack.getProperties(), name),
                    flipsBefore, flipsAfter, mipmappedBuffers, computes);
            pass.shadowSamplerKinds = shadowSamplerKindsFor(name);
            applyPassViewport(pass, drawBuffers);
            applyPassViewportScale(pass, pack);
            return pass;
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build pass '{}'; it will be skipped: {}", name, e.getMessage());
            return null;
        }
    }

    // The final program, which writes the main framebuffer rather than a colortex
    private FullscreenPass buildFinalPass(ShaderPack pack, BufferFlipper flipper) {
        Optional<ProgramSource> source = pack.getProgramSet().get(ProgramId.Final);
        if (!source.isPresent()) {
            return null;
        }
        String name = source.get().getName();
        if (!isProgramEnabled(pack, name)) {
            return null;
        }
        try {
            UmbraProgram program = cachedProgram(source.get());
            if (program == null) {
                return null;
            }
            BitSet flips = flipper.snapshot();
            BitSet mipmappedBuffers = parseMipmappedBuffers(source.get());
            int[] colorSamplers = snapshotFrontTextures(flipper);
            FullscreenPass pass = new FullscreenPass(name, program, this.compiledUniforms.get(name), null,
                    colorSamplers, DrawBuffers.DEFAULT.clone(),
                    ProgramBlendState.from(pack.getProperties(), name), flips, (BitSet) flips.clone(),
                    mipmappedBuffers, java.util.Collections.<ComputePass>emptyList());
            pass.shadowSamplerKinds = shadowSamplerKindsFor(name);
            return pass;
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build final pass; falling back to colortex0 blit: {}", e.getMessage());
            return null;
        }
    }

    // The colortexNMipmapEnabled set for one pass, per program like Umbra's ProgramDirectives, read from the fragment stage with conditionals resolved (Body Camera declares it true and false in the two branches of one #if and the dead branch broke its auto-exposure)
    private BitSet parseMipmappedBuffers(ProgramSource source) {
        BitSet mipmappedBuffers = new BitSet(UmbraRenderTargets.MAX_COLOR_BUFFERS);
        Optional<String> fragmentSource = source.getFragmentSource();
        if (!fragmentSource.isPresent()) {
            return mipmappedBuffers;
        }

        Matcher matcher = MIPMAP_DIRECTIVE.matcher(GlslPreprocessor.resolveConditionals(fragmentSource.get(),
                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(this.shaderDefines, source.getName())));
        while (matcher.find()) {
            Integer index = UmbraRenderTargets.colorTargetIndex(matcher.group(1));
            if (index == null || index >= UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                continue;
            }
            if (Boolean.parseBoolean(matcher.group(2))) {
                mipmappedBuffers.set(index);
            } else {
                mipmappedBuffers.clear(index);
            }
        }
        return mipmappedBuffers;
    }

    // Transforms and compiles one composite-family program
    private UmbraProgram compileFullscreenProgram(ProgramSource source, TextureStage stage) {
        String vshRaw = source.getVertexSource().orElse(null);
        String fshRaw = source.getFragmentSource().orElse(null);
        if (vshRaw == null || fshRaw == null) {
            LOGGER.warn("[Umbra] Program '{}' is missing a vertex or fragment stage; skipping", source.getName());
            return null;
        }
        // Raw texture.<stage>.<sampler> directives redirect the identifier to its minted customtexN name where the sampler type matches; must precede everything else so later transforms and the DRAWBUFFERS parse see the final identifiers
        vshRaw = CustomTextureTransformer.transform(source.getName(), vshRaw, stage);
        fshRaw = CustomTextureTransformer.transform(source.getName(), fshRaw, stage);
        // Modern (1.17+) attribute/matrix names -> fixed-function built-ins; the quad is drawn under runPass's ortho, so gl_ProjectionMatrix is exactly the (0,1)->(-1,1) matrix Umbra substitutes for `projectionMatrix`
        vshRaw = VanillaNameTransformer.transform(vshRaw);
        fshRaw = VanillaNameTransformer.transform(fshRaw);

        GlShader vertex = null;
        GlShader fragment = null;
        try {
            // Modern single-source packs (Complementary) use the compatibility stage normalizer, the GLSL-120 Chocapic family (LIGHT) keeps the full 330-core rewrite; detected off the fragment source
            boolean modern = ModernPackTransformer.isModernSource(fshRaw);
            this.modernPack |= modern;
            // Scoped per pass like the terrain path: a pass in `impetus.umbra.legacyPrograms` compiles without IS_IRIS, and the one map drives both the DRAWBUFFERS parse and the injected prologue
            Map<String, String> macros = com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(
                    this.shaderDefines, source.getName());
            int[] drawBuffers = sanitizeCompositeDrawBuffers(source.getName(), DrawBuffers.parseActive(fshRaw, macros));
            String vsh;
            String fsh;
            if (modern) {
                // Modern sources rely on the driver preprocessor for their #if trees, so the MC_*/IRIS_FEATURE_* environment must be present; the quad uses generic attributes, so gl_MultiTexCoord0 is fed from a real one rather than NVIDIA's aliasing (see ModernPackTransformer#bindFullscreenTexCoord)
                vsh = ModernPackTransformer.bindFullscreenTexCoord(
                        ModernPackTransformer.transform(stabilizeShaderSource(source.getName(),
                                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(vshRaw, macros))));
                fsh = DrawBuffers.rewriteFragmentOutputs(ModernPackTransformer.transform(
                        stabilizeShaderSource(source.getName(),
                                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(fshRaw, macros))),
                        drawBuffers);
            } else {
                // The macro environment must be injected on the legacy branch too, or a pack's post chain disagrees with its gbuffer programs about MC_VERSION etc: `#if MC_VERSION < 10800` silently takes the pre-1.8 path, and Pastel's `MC_RENDER_QUALITY * 0.0625` failed to compile; injecting before the transform lands the defines right after the generated `#version 330 core`
                vsh = FullscreenTransformer.transformVertexShader(foldUncompilableConditionals(source.getName(),
                        com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(vshRaw, macros)));
                fsh = FullscreenTransformer.transformFragmentShader(foldUncompilableConditionals(source.getName(),
                        com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(fshRaw, macros)), drawBuffers);
            }
            vertex = new GlShader(ShaderType.VERTEX, source.getName() + ".vsh", vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, source.getName() + ".fsh", fsh);

            ProgramBuilder builder = ProgramBuilder.begin(source.getName())
                    .attach(vertex)
                    .attach(fragment)
                    .bindAttributeLocation(FullscreenQuadRenderer.POSITION_SLOT, "a_Position")
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT, "a_TexCoord")
                    // Modern (compatibility-profile) sources read the quad's texcoord through this, since gl_MultiTexCoord0 cannot be fed portably via generic attribute 8
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT,
                            ModernPackTransformer.FULLSCREEN_TEXCOORD_ATTRIBUTE);
            if (!modern) {
                // The generated 330-core path writes an explicit out array whose indices are the dense draw-buffer slots, matching Umbra's packed attachments
                builder.bindFragmentDataLocation(0, "iris_FragData");
            }
            GlProgram program = builder.link();

            assignSamplerUnits(program, stage);
            return new UmbraProgram(program, drawBuffers);
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    // Points every sampler uniform the program declares at its fixed unit (OptiFine's setProgramUniform1i) with the stage's custom-texture overrides; a colortex override is skipped once an earlier pass has flipped that buffer, matching Umbra
    private void assignSamplerUnits(GlProgram program, TextureStage stage) {
        program.bind();
        assignSamplerUnits(program.getGlId(), samplerUnitsForStage(stage), mergedStageOverrides(stage),
                this.flippedAtLeastOnce);
        program.unbind();
    }

    // Assigns the standard sampler-unit mapping on the *currently bound* raw GL program with the active pipeline's gbuffers/shadow-stage overrides; used by the Impetus terrain and shadow overrides, whose programs are Impetus's
    public static void assignSamplerUnitsToBoundProgram(int programId) {
        assignSamplerUnits(programId, activeGbufferSamplerUnits, activeGbufferSamplerOverrides,
                java.util.Collections.<Integer>emptySet());
    }

    private static void assignSamplerUnits(int programId, Map<String, Integer> samplerUnits,
                                           Map<String, CustomTextureManager.Override> overrides,
                                           java.util.Set<Integer> flippedAtLeastOnce) {
        boolean waterShadowEnabled = LWJGL.glGetUniformLocation(programId, "watershadow") != -1;
        for (Map.Entry<String, Integer> entry : samplerUnits.entrySet()) {
            int location = LWJGL.glGetUniformLocation(programId, entry.getKey());
            if (location == -1) {
                continue;
            }
            int unit = entry.getValue();
            if (waterShadowEnabled && "shadow".equals(entry.getKey())) {
                // UmbraSamplers.addShadowSamplers parity: with watershadow present, the legacy shadow alias reads the pre-translucent depth (shadowtex1) while watershadow reads shadowtex0
                unit = isGbufferSamplerLayout(samplerUnits) ? GBUFFER_SHADOW_TEX_1_UNIT : SHADOW_TEX_1_UNIT;
            }
            CustomTextureManager.Override override = overrides.get(entry.getKey());
            if (override != null && (override.colorTarget < 0 || !flippedAtLeastOnce.contains(override.colorTarget))) {
                unit = override.unit;
            }
            LWJGL.glUniform1i(location, unit);
        }
        // Pack-declared sampler names with no standard unit (customTexture.<name> directives).
        for (Map.Entry<String, CustomTextureManager.Override> entry : overrides.entrySet()) {
            if (samplerUnits.containsKey(entry.getKey())) {
                continue;
            }
            int location = LWJGL.glGetUniformLocation(programId, entry.getKey());
            if (location != -1) {
                LWJGL.glUniform1i(location, entry.getValue().unit);
            }
        }
    }

    // Distinguishes the two fixed layouts by a marker name
    private static boolean isGbufferSamplerLayout(Map<String, Integer> samplerUnits) {
        return samplerUnits.getOrDefault("depthtex0", -1) == GBUFFER_DEPTH_TEX_0_UNIT;
    }

    // The gbuffers-stage overrides flattened to name → unit, for GbufferPrograms' sampler table.
    private Map<String, Integer> gbufferSamplerOverrideUnits() {
        Map<String, Integer> units = new LinkedHashMap<>();
        for (Map.Entry<String, CustomTextureManager.Override> entry
                : this.customTextureManager.getOverrides(TextureStage.GBUFFERS_AND_SHADOW).entrySet()) {
            units.put(entry.getKey(), entry.getValue().unit);
        }
        units.putAll(this.customImageManager.getUniformOverrides());
        return units;
    }

    // Layout per stage; composite and final share, gbuffer differs
    private static Map<TextureStage, Map<String, Integer>> samplerUnitsByStage() {
        Map<TextureStage, Map<String, Integer>> byStage = new java.util.EnumMap<>(TextureStage.class);
        for (TextureStage stage : TextureStage.values()) {
            byStage.put(stage, samplerUnitsForStage(stage));
        }
        return byStage;
    }

    // Layout for one stage
    private static Map<String, Integer> samplerUnitsForStage(TextureStage stage) {
        return stage == TextureStage.GBUFFERS_AND_SHADOW ? GBUFFER_SAMPLER_UNITS : FULLSCREEN_SAMPLER_UNITS;
    }

    // The stage's custom-texture overrides plus the stage-independent custom-image uniform assignments, in the Override form assignSamplerUnits consumes; image entries never deactivate (colorTarget -1)
    private Map<String, CustomTextureManager.Override> mergedStageOverrides(TextureStage stage) {
        Map<String, CustomTextureManager.Override> merged =
                new LinkedHashMap<>(this.customTextureManager.getOverrides(stage));
        this.customImageManager.getUniformOverrides().forEach((name, unit) ->
                merged.put(name, new CustomTextureManager.Override(unit, -1)));
        this.renderTargetImageUnits.forEach((index, unit) ->
                merged.put("colorimg" + index, new CustomTextureManager.Override(unit, -1)));
        this.shadowColorImageUnits.forEach((index, unit) ->
                merged.put("shadowcolorimg" + index, new CustomTextureManager.Override(unit, -1)));
        return merged;
    }

    // Registers every uniform the pipeline can supply against one program
    private static ProgramUniforms buildUniforms(String name, UmbraProgram program) {
        ProgramUniforms.Builder builder = ProgramUniforms.builder(name, program.getProgram().getGlId());
        CommonUniforms.addCommonUniforms(builder);
        MatrixUniforms.addMatrixUniforms(builder);
        com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.assignTo(builder);
        return builder.buildUniforms();
    }

    // The texture currently readable ("front") for each existing color target under the given flip state.
    private int[] snapshotFrontTextures(BufferFlipper flipper) {
        int[] samplers = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
        for (int i = 0; i < samplers.length; i++) {
            samplers[i] = this.renderTargets.get(i) == null ? 0 : frontTexture(flipper, i);
        }
        return samplers;
    }

    // The texture a pass reads for a colortex
    private int frontTexture(BufferFlipper flipper, int index) {
        UmbraRenderTarget target = this.renderTargets.getOrCreate(index);
        return flipper.isFlipped(index) ? target.getAltTexture() : target.getMainTexture();
    }

    // Same, from a snapshotted flip state
    private int frontTexture(BitSet flips, int index) {
        UmbraRenderTarget target = this.renderTargets.getOrCreate(index);
        return flips.get(index) ? target.getAltTexture() : target.getMainTexture();
    }

    // The texture a pass writes for a colortex
    private int backTexture(BitSet flips, int index) {
        UmbraRenderTarget target = this.renderTargets.getOrCreate(index);
        return flips.get(index) ? target.getMainTexture() : target.getAltTexture();
    }

    // Gbuffer-program draw buffers: packs address logical colortex indices, the shared gbuffer FBO maps them onto dense attachments; called by the terrain override and the phase compiler
    public static int[] sanitizeDrawBuffers(String name, int[] drawBuffers) {
        return sanitizeDrawBuffers(name, drawBuffers, GBUFFER_ATTACHMENT_LIMIT);
    }

    // Composite/deferred passes pack attachments densely, so any colortex0..15 index is fine.
    private static int[] sanitizeCompositeDrawBuffers(String name, int[] drawBuffers) {
        return sanitizeDrawBuffers(name, drawBuffers, UmbraRenderTargets.MAX_COLOR_BUFFERS);
    }

    // Drops out-of-range targets, logging each with the program name
    private static int[] sanitizeDrawBuffers(String name, int[] drawBuffers, int maxExclusive) {
        // An out-of-range index is normally an inactive #ifdef path in the pack, so it is dropped silently; the consumer stays because sanitize requires one
        return DrawBuffers.sanitize(drawBuffers, maxExclusive, buffer -> { });
    }

    // ------------------------------------------------------------------ per-frame hooks

    private void runClearPasses(List<ClearPass> passes) {
        for (ClearPass pass : passes) {
            pass.framebuffer.bind();
            LWJGL.glViewport(0, 0,
                    pass.width > 0 ? pass.width : this.renderTargets.getWidth(),
                    pass.height > 0 ? pass.height : this.renderTargets.getHeight());
            float[] color = pass.color;
            if (color == null) {
                org.joml.Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
                GlStateManager.clearColor(fog.x, fog.y, fog.z, 1.0f);
            } else {
                GlStateManager.clearColor(color[0], color[1], color[2], color[3]);
            }
            LWJGL.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }

    // renderWorld HEAD: redirect the frame into the gbuffer, so vanilla's fog-coloured clear clears our attachments and every world draw lands in the render targets
    public void beginWorldRendering(float partialTicks) {
        if (this.destroyed) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth != this.renderTargets.getWidth() || mc.displayHeight != this.renderTargets.getHeight()) {
            this.renderTargets.resize(mc.displayWidth, mc.displayHeight);
            this.fullClearRequired = true;
            if (this.shaderStorageBuffers != null) {
                this.shaderStorageBuffers.onResize(mc.displayWidth, mc.displayHeight);
            }
            this.customImageManager.onResize(mc.displayWidth, mc.displayHeight);
        }

        if (this.shaderStorageBuffers != null && !this.shaderStorageBuffers.isEmpty()) {
            this.shaderStorageBuffers.bindAll();
        }

        SystemTimeUniforms.COUNTER.beginFrame(System.nanoTime());
        EyeBrightnessTracker.update();
        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        captureAtlasSize(mc);

        Entity camera = mc.getRenderViewEntity();
        if (camera != null) {
            double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
            double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
            double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
            CapturedRenderingState.INSTANCE.setCameraPosition(x, y, z);
        }
        this.frameUpdateNotifier.onNewFrame();
        CommonUniforms.beginFrame();
        com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.update();

        // noisetex and the stub shadow maps ride along the whole frame on fixed units vanilla never binds and GlStateManager cannot address; a pack-supplied texture.noise replaces the generated noise (Umbra parity)
        bindNoiseTexture();
        bindOverlayTexture();
        GlTextureUnits.selectScratch(SHADOW_TEX_0_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.stubShadowMap.getTextureId());
        GlTextureUnits.selectScratch(SHADOW_TEX_1_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.stubShadowMap.getTextureId());
        GlTextureUnits.resetToUnit0();

        // Custom images: honor the pack-declared clears, then bind image units + paired samplers.
        this.customImageManager.clearAll();
        bindShaderPackResources();

        this.activeGbufferSamplerFlips = this.preTranslucentGbufferSamplerFlips;
        bindGbufferPbrSamplers();
        // colortex4..7 (gaux1..4) must be readable by gbuffer programs (MakeUp's terrain fog samples gaux4); bind before world geometry or distant terrain fogs toward garbage
        bindGbufferColorSamplers();

        UmbraFramebuffer gbuffer = this.gbufferFramebuffer;
        this.currentGbuffer = gbuffer;
        runClearPasses(this.fullClearRequired ? this.fullClearPasses : this.clearPasses);
        this.fullClearRequired = false;
        // Umbra beginLevelRendering: the setup computes run once on the first frame, then the `begin` family runs every frame on the freshly cleared targets before any geometry
        if (!this.setupDispatched) {
            this.setupDispatched = true;
            if (!this.setupPasses.isEmpty()) {
                bindShaderPackResources();
                for (FullscreenPass pass : this.setupPasses) {
                    bindRenderTargetImages(pass);
                    dispatchComputes(pass.computes);
                }
                LWJGL.glUseProgram(0);
            }
        }
        runFullscreenFamily(this.beginPasses, mc);
        gbuffer.bind();
        drawGbufferBuffers(gbuffer, FIXED_FUNCTION_MASK);
        GlStateManager.disableBlend();
        disableIndexedBlend(GBUFFER_ATTACHMENT_LIMIT);
        if (this.skyAtFarPlane) {
            LWJGL.glDepthRange(0.0, 1.0);
            this.skyAtFarPlane = false;
        }
        this.worldRenderingActive = true;
    }

    // Sky phases render at the far plane: packs like LIGHT write BLACK from gbuffers_skybasic and repaint the sky in composite, which only works if the vanilla dome (real geometry ~16 blocks up) never reaches depthtex0; glDepthRange(1,1) emulates OptiFine and survives vanilla's depthMask toggling
    private boolean skyAtFarPlane;

    // The alphaTest.<program> override currently forced on the GL state, so it can be undone.
    private com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest activeAlphaTest;

    // True when the pack supplies a program for this phase, so a draw lands in its DRAWBUFFERS rather than fixed-function output; callers suppressing vanilla GL state (blending into a packed gbuffer) use this to leave the fixed-function path alone
    public boolean hasGbufferProgram(ProgramId phase) {
        return this.worldRenderingActive && this.gbufferPrograms != null && this.gbufferPrograms.get(phase) != null;
    }

    // Whether the pack ships this phase's program itself, rather than resolving through OptiFine's fallback chain onto a program never written for this geometry
    public boolean hasDirectGbufferProgram(ProgramId phase) {
        return this.worldRenderingActive && this.gbufferPrograms != null && this.gbufferPrograms.hasDirect(phase);
    }

    // Whether the frame is between the deferred and composite stages
    public boolean isRenderingPostDeferredTranslucents() {
        return this.worldRenderingActive && !this.deferredPasses.isEmpty()
                && this.currentGbuffer == this.translucentGbufferFramebuffer;
    }

    // Umbra's chain is gbuffers_entities_translucent -> gbuffers_entities -> gbuffers_textured_lit; skipping the middle link drew translucent entities with the generic program for most packs and lost entityColor, normals and diffuse lighting
    public ProgramId getTranslucentEntityPhase() {
        return hasGbufferProgram(ProgramId.EntitiesTrans) ? ProgramId.EntitiesTrans : ProgramId.Entities;
    }

    // the gbuffer phase currently bound, or null when none is
    public ProgramId getCurrentPhase() {
        return this.currentPhase;
    }

    // Whether the camera pass is drawing right now; narrower than isWorldRenderingActive(), since the shadow pass renders entities through the SAME vanilla renderers and runs first (48 of 48 probe samples came back shadow=true)
    public boolean isCameraPassActive() {
        return this.worldRenderingActive && !UmbraShadowRenderer.isShadowPass();
    }

    // Switches the active gbuffer program for a fixed-function phase (sky, entities, particles, weather, clouds, hand) anchored on vanilla's profiler sections, pointing the draw-buffer mask at its DRAWBUFFERS (colortex0 only with no program); also rebinds the gbuffer to heal any mid-frame framebuffer rebind
    public void setPhase(ProgramId phase) {
        setPhase(phase, defaultRenderStage(phase));
    }

    // Umbra's WorldRenderingPhase ordinals for identifiable phases, published as renderStage; before this every sky/cloud/weather/entity draw reported NONE and Clarity's stars (gated on MC_RENDER_STAGE_STARS) never appeared. Ambiguous phases still report NONE
    private static int defaultRenderStage(ProgramId phase) {
        if (phase == null) {
            return 0; // MC_RENDER_STAGE_NONE
        }
        switch (phase) {
            case SkyBasic: return 1;    // MC_RENDER_STAGE_SKY
            case SkyTextured: return 4; // MC_RENDER_STAGE_SUN (vanilla draws sun then moon under one anchor)
            // Umbra has no distinct phase for the eyes overlay; it draws inside the entity pass.
            case Entities: case SpiderEyes: return 11; // MC_RENDER_STAGE_ENTITIES
            case DamagedBlock: return 13; // MC_RENDER_STAGE_DESTROY
            case Line: return 14;       // MC_RENDER_STAGE_OUTLINE
            case Particles: return 19;  // MC_RENDER_STAGE_PARTICLES
            case Clouds: return 20;     // MC_RENDER_STAGE_CLOUDS
            case Weather: return 21;    // MC_RENDER_STAGE_RAIN_SNOW
            default: return 0;
        }
    }

    // Mirrors OptiFine's enableLightmap()/disableLightmap(), which swap gbuffers_textured and gbuffers_textured_lit when vanilla toggles unit 1; otherwise texture2D(lightmap) on a disabled unit reads white and items (no lightmap element in ITEM format, RenderItemFrame never touches the unit) render fullbright. Only the Textured/TexturedLit pair moves, as in OptiFine
    public void setLightmapEnabled(boolean enabled) {
        if (!this.worldRenderingActive) {
            return;
        }
        ProgramId target;
        if (enabled) {
            target = this.currentPhase == ProgramId.Textured ? ProgramId.TexturedLit : null;
        } else {
            target = this.currentPhase == ProgramId.TexturedLit ? ProgramId.Textured : null;
        }
        if (target == null) {
            return;
        }
        // Carry the current render stage across rather than recompute the default: OptiFine's useProgram() leaves stage tracking alone, and gbuffers_textured_lit's stage (particles vs translucent entities) can only come from the call site
        setPhase(target, CapturedRenderingState.INSTANCE.getRenderStage());
    }

    // Overload for callers that know a finer phase than ProgramId can express (sky basic covers sky/stars/void).
    public void setPhase(ProgramId phase, int renderStage) {
        // The shadow pass owns its GL state (the `shadow` program, its draw buffers, its blend) and never calls this; selecting a gbuffer phase during it re-points the draw buffers at the gbuffer and corrupts the shadow map world-wide. Reachable since it renders entities through the same vanilla renderers (measured: per-entity setPhase shredded water), so the guard lives here and in beginEyes/endEyes/armor glint
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.currentPhase = phase;
        CapturedRenderingState.INSTANCE.setRenderStage(renderStage);
        // Every phase this switches to is fixed-function geometry submitted through client arrays aliasing generic slots; see resetVanillaVertexArrayState, a leftover generic array wins and flattens the attribute
        resetVanillaVertexArrayState();
        boolean sky = phase == ProgramId.SkyBasic || phase == ProgramId.SkyTextured;
        if (sky != this.skyAtFarPlane) {
            LWJGL.glDepthRange(sky ? 1.0 : 0.0, 1.0);
            this.skyAtFarPlane = sky;
        }
        GbufferPrograms.Entry entry = phase == null ? null : this.gbufferPrograms.get(phase);
        // The previous phase may have overridden the alpha test; put vanilla's back before deciding this phase's.
        if (this.activeAlphaTest != null) {
            this.activeAlphaTest.restore();
            this.activeAlphaTest = null;
        }
        if (entry == null) {
            LWJGL.glUseProgram(0);
            drawGbufferBuffers(this.currentGbuffer, FIXED_FUNCTION_MASK);
        } else {
            entry.getProgram().bind();
            bindShaderPackResources();
            int[] drawBuffers = entry.drawBuffersReadOnly();
            // Re-assert colortex4..7 read bindings, since sky/entity/hand phases sample gaux buffers and the prior phase may have disturbed the units; if this program also writes a samplable target, bind a copied scratch side so it never reads the active render target
            bindGbufferColorSamplers(prepareGbufferFeedbackSamplers(drawBuffers));
            entry.getUniforms().update();
            applyShadowSamplerKinds(entry.getShadowSamplerKinds(), true);
            drawGbufferBuffers(this.currentGbuffer, drawBuffers);
            entry.getBlendState().apply(drawBuffers);
            // alphaTest.<program>: packs doing their own discard turn the fixed-function test off (Photon), others tighten it to GREATER 0.0001 (Complementary); held until the next setPhase
            if (entry.getAlphaTest().hasDirectives()) {
                entry.getAlphaTest().apply();
                this.activeAlphaTest = entry.getAlphaTest();
            }
        }
    }

    // GL_CURRENT_PROGRAM, spelled as a literal because the generated GL constant classes do not carry it
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;

    // Re-uploads only the current phase's per-object uniforms (entityColor, entityId, blockEntityId, currentRenderedItemId) between draws of a batch; Umbra gets per-draw ids from a vertex attribute 1.12's formats cannot carry, and a latched id makes a whole batch emissive (Complementary dispatches on currentRenderedItemId). Goes through updatePerObject() only, since the DYNAMIC matrix uniforms each stall on glGetFloat; no-op in the shadow pass
    public void refreshDynamicUniforms() {
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass() || this.currentPhase == null
                || this.gbufferPrograms == null) {
            return;
        }
        GbufferPrograms.Entry entry = this.gbufferPrograms.get(this.currentPhase);
        if (entry == null) {
            return;
        }
        // glUniform* writes into whatever program is bound RIGHT NOW with per-program locations, and the per-object hooks fire from vanilla renderers unaware of the phase (a held item reaches RenderItem under the hand pass); ask GL rather than trust the bookkeeping
        if (LWJGL.glGetInteger(GL_CURRENT_PROGRAM) != entry.getProgram().getProgram().getGlId()) {
            return;
        }
        entry.getUniforms().updatePerObject();
    }

    // The "eyes" overlay layers (spider, enderman, dragon) for gbuffers_spidereyes, bracketed like OptiFine's beginSpiderEyes/endSpiderEyes; 1.12 signals full-bright with the raw 61680 sentinel, which arithmetically is 240.97 and overflows HDR targets into a bloom halo, so substitute vec4(240, 240, 0, 1) like Umbra's VanillaTransformer. No-op in the shadow pass
    public void beginEyes() {
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.phaseBeforeEyes = this.currentPhase;
        this.phaseBeforeEyesStage = CapturedRenderingState.INSTANCE.getRenderStage();
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT_LIGHTMAP_COORD, FULL_BRIGHT_LIGHTMAP_COORD);
        setPhase(ProgramId.SpiderEyes);
    }

    // Back to the entity program like OptiFine's endSpiderEyes, restoring what was actually bound since the eyes layers also run in the translucent-entity batch; vanilla restores the lightmap coordinate itself
    public void endEyes() {
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        setPhase(this.phaseBeforeEyes, this.phaseBeforeEyesStage);
    }

    // Routes the enchantment glint through gbuffers_armor_glint like OptiFine's renderEnchantedGlintBegin, otherwise it inherits the entity or hand program and loses the pack's additive treatment; gated on worldRenderingActive (skips GUI items) and the shadow pass, without needing OptiFine's renderItemGui flag
    public void beginArmorGlint() {
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        this.phaseBeforeArmorGlint = this.currentPhase;
        this.phaseBeforeArmorGlintStage = CapturedRenderingState.INSTANCE.getRenderStage();
        this.armorGlintActive = true;
        setPhase(ProgramId.ArmorGlint);
    }

    // Restores whatever was bound before the glint like endEyes(), since the glint runs in both the entity and first-person hand batches and assuming gbuffers_entities would strand the hand
    public void endArmorGlint() {
        if (!this.armorGlintActive) {
            return;
        }
        this.armorGlintActive = false;
        setPhase(this.phaseBeforeArmorGlint, this.phaseBeforeArmorGlintStage);
    }

    // The phase #setPhase(ProgramId, int) last selected, so #endEyes() can put it back.
    private ProgramId currentPhase;
    private ProgramId phaseBeforeEyes;
    private int phaseBeforeEyesStage;
    private ProgramId phaseBeforeArmorGlint;
    private int phaseBeforeArmorGlintStage;
    // Guards #endArmorGlint() so a begin that bailed out (GUI, shadow pass) cannot restore a stale phase.
    private boolean armorGlintActive;

    // Port of OptiFine's Shaders.drawHorizon: an octagonal ring at the render-distance edge from ground level to y = 16 through gbuffers_skybasic, right before the sky disc; vanilla leaves a band at the horizon uncovered, which kept last frame's non-cleared colortex1 and self-perpetuated into a blown ~50 band
    public void drawSkyHorizon() {
        if (!this.worldRenderingActive || this.skyHorizonActive) {
            return;
        }
        this.skyHorizonActive = true;
        try {
            // Clamp the radius (Umbra HorizonRenderer parity): at high render distances the ring reaches the far plane and clips, letting the band return; 256 blocks is beyond the terrain fog yet inside the sky projection
            float f = Math.min(Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0f, 256.0f);
            double d0 = f * 0.9238D;
            double d1 = f * 0.3826D;
            double d2 = -d1;
            double d3 = -d0;
            double top = 16.0D;
            double bottom = -CapturedRenderingState.INSTANCE.getCameraPosition().y;
            org.joml.Vector3f fog = CapturedRenderingState.INSTANCE.getFogColor();
            GlStateManager.color(fog.x, fog.y, fog.z);
            BufferBuilder bb = Tessellator.getInstance().getBuffer();
            bb.begin(7, DefaultVertexFormats.POSITION);
            bb.pos(d2, bottom, d3).endVertex();
            bb.pos(d2, top, d3).endVertex();
            bb.pos(d3, top, d2).endVertex();
            bb.pos(d3, bottom, d2).endVertex();
            bb.pos(d3, bottom, d2).endVertex();
            bb.pos(d3, top, d2).endVertex();
            bb.pos(d3, top, d1).endVertex();
            bb.pos(d3, bottom, d1).endVertex();
            bb.pos(d3, bottom, d1).endVertex();
            bb.pos(d3, top, d1).endVertex();
            bb.pos(d2, top, d1).endVertex();
            bb.pos(d2, bottom, d1).endVertex();
            bb.pos(d2, bottom, d1).endVertex();
            bb.pos(d2, top, d1).endVertex();
            bb.pos(d1, top, d0).endVertex();
            bb.pos(d1, bottom, d0).endVertex();
            bb.pos(d1, bottom, d0).endVertex();
            bb.pos(d1, top, d0).endVertex();
            bb.pos(d0, top, d1).endVertex();
            bb.pos(d0, bottom, d1).endVertex();
            bb.pos(d0, bottom, d1).endVertex();
            bb.pos(d0, top, d1).endVertex();
            bb.pos(d0, top, d2).endVertex();
            bb.pos(d0, bottom, d2).endVertex();
            bb.pos(d0, bottom, d2).endVertex();
            bb.pos(d0, top, d2).endVertex();
            bb.pos(d1, top, d3).endVertex();
            bb.pos(d1, bottom, d3).endVertex();
            bb.pos(d1, bottom, d3).endVertex();
            bb.pos(d1, top, d3).endVertex();
            bb.pos(d2, top, d3).endVertex();
            bb.pos(d2, bottom, d3).endVertex();
            Tessellator.getInstance().draw();
        } catch (Throwable t) {
            LOGGER.warn("[Umbra] drawSkyHorizon failed: {}", t.toString());
        } finally {
            this.skyHorizonActive = false;
        }
    }

    private boolean skyHorizonActive;

    // Called when the Impetus terrain override program binds (UmbraTerrainShaderInterface.setupState): points the gbuffer's draw-buffer mask at the terrain/water program's DRAWBUFFERS
    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState) {
        onTerrainDraw(drawBuffers, blendState,
                com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest.empty(), false);
    }

    public void onTerrainDraw(int[] drawBuffers, ProgramBlendState blendState,
                              com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest alphaTest,
                              boolean translucentPass) {
        if (!this.worldRenderingActive) {
            return;
        }
        int[] sanitizedDrawBuffers = DrawBuffers.sanitize(drawBuffers, GBUFFER_ATTACHMENT_LIMIT);
        // The chunk renderer owns atlas/lightmap setup, but pack samplers are Umbra-owned dynamic bindings re-asserted at program use, so gbuffers_water sees the current depthtex1/shadow/noise and a feedback-safe colortex4 snapshot
        bindDepthSamplers();
        bindShadowSamplers();
        bindNoiseTexture();
        bindGbufferPbrSamplers();
        bindGbufferColorSamplers(prepareGbufferFeedbackSamplers(sanitizedDrawBuffers));
        drawGbufferBuffers(this.currentGbuffer, sanitizedDrawBuffers);
        if (translucentPass) {
            restoreGbufferTranslucentBlend(sanitizedDrawBuffers);
        } else {
            restoreGbufferOpaqueBlend(sanitizedDrawBuffers);
        }
        blendState.apply(sanitizedDrawBuffers);
        // The terrain passes set their own alpha test (vanilla's GREATER 0.1 for cutout); a pack override replaces it for this draw, recorded so the next setPhase restores vanilla's
        if (alphaTest.hasDirectives()) {
            alphaTest.apply();
            this.activeAlphaTest = alphaTest;
        }
    }

    // Blend state for the opaque terrain pass on every attachment
    private static void restoreGbufferOpaqueBlend(int[] drawBuffers) {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        disableIndexedBlend(drawBuffers.length);
    }

    // Blend state for the translucent terrain pass
    private static void restoreGbufferTranslucentBlend(int[] drawBuffers) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);

        if (!LWJGL.supportsBufferBlending()) {
            return;
        }
        for (int slot = 0; slot < drawBuffers.length; slot++) {
            LWJGL.glEnablei(GL11.GL_BLEND, slot);
            LWJGL.glBlendFuncSeparatei(slot, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    // Turns blending off per attachment after a pass that set it per attachment
    private static void disableIndexedBlend(int drawBufferSlots) {
        if (!LWJGL.supportsBufferBlending()) {
            return;
        }
        int maxSlots = Math.min(drawBufferSlots, LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS));
        for (int slot = 0; slot < maxSlots; slot++) {
            LWJGL.glDisablei(GL11.GL_BLEND, slot);
        }
    }

    // Hook for the chunk renderer after a terrain pass; resets indexed blend
    public void afterTerrainDraw(int drawBufferSlots) {
        if (!this.worldRenderingActive || UmbraShadowRenderer.isShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getFramebuffer().bindFramebuffer(true);
        restoreMainDrawReadBuffers(mc);
        disableIndexedBlend(drawBufferSlots);
        GlTextureUnits.resetToUnit0();
    }

    // The Distant Horizons compat for this pipeline
    public DhCompat getDhCompat() {
        return this.dhCompat;
    }

    // The shadow renderer, or null when the pack draws no shadow map
    public UmbraShadowRenderer getShadowRenderer() {
        return this.shadowRenderer;
    }

    // A framebuffer for a Distant Horizons LOD pass: the gbuffer colour targets the DH program writes, at the front side baked for the phase it runs in (before or after the deferred chain), attached densely in draw-buffer order; DH's own depth texture is attached by the compat, so LOD depth lands in dhDepthTex0 rather than depthtex0. Owned by the caller
    public UmbraFramebuffer createDhFramebuffer(int[] drawBuffers, boolean translucent) {
        int[] sanitized = DrawBuffers.sanitize(drawBuffers, GBUFFER_ATTACHMENT_LIMIT);
        BitSet flips = translucent ? this.translucentGbufferSamplerFlips : this.preTranslucentGbufferSamplerFlips;
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        int[] dense = new int[sanitized.length];
        for (int slot = 0; slot < sanitized.length; slot++) {
            int logicalIndex = sanitized[slot];
            if (logicalIndex < 0) {
                dense[slot] = -1;
                continue;
            }
            framebuffer.addColorAttachment(logicalIndex, slot, frontTexture(flips, logicalIndex));
            dense[slot] = slot;
        }
        framebuffer.drawBuffers(dense);
        return framebuffer;
    }

    // Called by the DH compat right before DH draws a LOD pass (Iris's LodRendererEvents binds its own framebuffer here): the same sampler and blend setup a terrain draw gets, on the LOD framebuffer instead of the shared gbuffer; the framebuffer's draw buffers were fixed at creation
    public void onDhLodDraw(UmbraFramebuffer framebuffer, int[] drawBuffers, ProgramBlendState blendState,
                            boolean translucentPass) {
        if (!this.worldRenderingActive) {
            return;
        }
        int[] sanitized = DrawBuffers.sanitize(drawBuffers, GBUFFER_ATTACHMENT_LIMIT);
        bindDepthSamplers();
        bindShadowSamplers();
        bindNoiseTexture();
        bindGbufferPbrSamplers();
        bindGbufferColorSamplers(prepareGbufferFeedbackSamplers(sanitized));
        framebuffer.bind();
        if (translucentPass) {
            restoreGbufferTranslucentBlend(sanitized);
        } else {
            restoreGbufferOpaqueBlend(sanitized);
        }
        blendState.apply(sanitized);
        bindCustomImages();
        // Iris runs DH programs with no fixed-function alpha test (core profile); here the compatibility context would still cull a pack's low-alpha dithered fade, so it goes off for the pass and back afterwards. Recorded once per pass: DH's generic objects re-enter here mid-pass and must not overwrite what the pass found
        if (!this.dhAlphaTestRecorded) {
            this.dhAlphaTestRecorded = true;
            this.dhAlphaTestWasEnabled = LWJGL.glGetBoolean(GL11.GL_ALPHA_TEST);
        }
        GlStateManager.disableAlpha();
        LWJGL.glDisable(GL11.GL_ALPHA_TEST);
    }

    // After a LOD pass: indexed blend back off and the alpha test as it was; the gbuffer itself is rebound by whatever draws next, as after a terrain draw
    public void afterDhLodDraw(int drawBufferSlots) {
        if (!this.worldRenderingActive) {
            return;
        }
        disableIndexedBlend(drawBufferSlots);
        if (this.dhAlphaTestRecorded && this.dhAlphaTestWasEnabled) {
            GlStateManager.enableAlpha();
            LWJGL.glEnable(GL11.GL_ALPHA_TEST);
        }
        this.dhAlphaTestRecorded = false;
        GlTextureUnits.resetToUnit0();
    }

    // The alpha-test state a DH LOD pass found, put back after it
    private boolean dhAlphaTestWasEnabled;
    private boolean dhAlphaTestRecorded;

    // Called right after setupCameraTransform / updateRenderInfo, when vanilla has just read the camera matrices into ActiveRenderInfo's buffers, so copy them for the uniform providers
    public void captureRenderingState() {
        if (!this.worldRenderingActive) {
            return;
        }
        CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f(ActiveRenderInfoAccessor.getModelViewMatrix()));
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f(ActiveRenderInfoAccessor.getProjectionMatrix()));
    }

    // Renders the shadow map right after captureRenderingState, before anything draws into the gbuffer, then re-points GL at the gbuffer and replaces the always-lit stubs on shadowtex0/1; the prepare family runs here too like Umbra's renderShadows, so Photon's cloud shadow map lands before any gbuffer geometry
    public void renderShadowMap() {
        if (!this.worldRenderingActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.prepareBeforeShadow) {
            runFullscreenFamily(this.preparePasses, mc);
        }

        if (this.shadowRenderer != null) {
            this.shadowRenderer.render();
            // Iris binds the finished shadow map to every shadowcomp program at use; here the units still held the frame-start 1x1 stubs until after the dispatch, so a shadowcomp stage sampling shadowtex0/shadowcolor0 read the stub
            bindShadowSamplers();
            dispatchComputePasses();

            this.currentGbuffer.bind();
            LWJGL.glViewport(0, 0, this.renderTargets.getWidth(), this.renderTargets.getHeight());

            bindShadowSamplers();
        }

        if (!this.prepareBeforeShadow) {
            runFullscreenFamily(this.preparePasses, mc);
        }
    }

    // Called at the "translucent" anchor after all opaque content: snapshots pre-translucent depth (depthtex1), runs the deferred chain, then points world rendering at the post-deferred gbuffer
    public void beginTranslucents() {
        if (!this.worldRenderingActive) {
            return;
        }
        copyDepthTexture(this.renderTargets.getDepthTextureNoTranslucents());

        Minecraft mc = Minecraft.getMinecraft();

        if (!this.deferredPasses.isEmpty()) {
            GlStateManager.disableBlend();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableAlpha();

            bindDepthSamplers();
            // Re-establish the custom image/sampler bindings (Umbra binds images at every program use); the shadow pass's out-of-band rendering may have disturbed the high units
            bindShaderPackResources();
            for (FullscreenPass pass : this.deferredPasses) {
                runPass(pass, mc);
            }
            LWJGL.glUseProgram(0);
            restoreTextureUnits();
            this.activeGbufferSamplerFlips = this.translucentGbufferSamplerFlips;
            bindDepthSamplers();
            bindShadowSamplers();
            bindNoiseTexture();
            bindShaderPackResources();
            bindGbufferPbrSamplers();
            bindGbufferColorSamplers();
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();

            // The rest of the world (translucents, hand) renders into the post-deferred front textures.
            this.currentGbuffer = this.translucentGbufferFramebuffer;
            this.translucentGbufferFramebuffer.bind();
            LWJGL.glViewport(0, 0, this.renderTargets.getWidth(), this.renderTargets.getHeight());
        }

        // OptiFine's Shaders.beginWater() contract: translucent terrain draws with BLENDING ON and DEPTH WRITES ON, since composites find water by comparing depthtex0 against the copied depthtex1; blend must be re-enabled because the deferred chain runs blend-off and 1.12 has no RenderType setup to restore it
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);
    }

    // Called right before the solid hand draws: snapshot the pre-hand depth (depthtex2).
    public void beginHand() {
        if (!this.worldRenderingActive) {
            return;
        }
        copyDepthTexture(this.renderTargets.getDepthTextureNoHand());
    }

    // Starts the first-person hand pass into the *pre-deferred* gbuffer like OptiFine's renderHand0 (Umbra's renderSolid), since deferred packs light the hand in deferred* and a later hand stays unlit raw gbuffer data; with no hand program it still renders fixed-function into colortex0. True means call endHandRendering()
    public boolean beginHandRendering() {
        return beginHandRendering(ProgramId.Hand, 16); // MC_RENDER_STAGE_HAND_SOLID
    }

    // Starts OptiFine's late hand pass (renderHand1), after translucent geometry but before the composite chain consumes the gbuffer, so nearby translucent panes are not blended over the hand until it vanishes
    public boolean beginHandTranslucentRendering() {
        return beginHandRendering(ProgramId.HandWater, 23); // MC_RENDER_STAGE_HAND_TRANSLUCENT
    }

    // Binds the hand program and depth setup; false when the pack has none
    private boolean beginHandRendering(ProgramId programId, int renderStage) {
        if (this.destroyed || !this.worldRenderingActive) {
            return false;
        }
        this.currentGbuffer.bind();
        LWJGL.glViewport(0, 0, this.renderTargets.getWidth(), this.renderTargets.getHeight());
        LWJGL.glDepthRange(0.0, 1.0);
        this.skyAtFarPlane = false;

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        // The hand draws into an already-populated depth buffer, and with the camera pressed into collision geometry even the squeezed projection can fail LEQUAL; force it to win for this pass, restored in endHandRendering
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        resyncTextureUnitZero();
        resetVanillaVertexArrayState();

        GbufferPrograms.Entry entry = this.gbufferPrograms != null ? this.gbufferPrograms.get(programId) : null;
        // Keep the phase honest: this binds a gbuffer program without setPhase, and the held item draws through RenderItem underneath, so refreshDynamicUniforms must find the hand program or its GL_CURRENT_PROGRAM guard skips the item's currentRenderedItemId
        this.currentPhase = programId;
        int packedLight = getHandPackedLight();
        setupHandLightmap(packedLight);
        bindGbufferPbrSamplers();
        CapturedRenderingState.INSTANCE.setRenderStage(renderStage);
        if (entry == null) {
            LWJGL.glUseProgram(0);
            drawGbufferBuffers(this.currentGbuffer, FIXED_FUNCTION_MASK);
            resyncTextureUnitZero();
            return true;
        }
        entry.getProgram().bind();
        int[] drawBuffers = entry.drawBuffersReadOnly();
        drawGbufferBuffers(this.currentGbuffer, drawBuffers);
        entry.getBlendState().apply(drawBuffers);
        entry.setHandLightmap(getBlockLightmapCoord(packedLight), getSkyLightmapCoord(packedLight));
        bindShaderPackResources();
        entry.getUniforms().update();
        applyShadowSamplerKinds(entry.getShadowSamplerKinds(), true);
        resyncTextureUnitZero();
        return true;
    }

    // Hands the fixed-function vertex pipeline back to vanilla before the first-person arm draws; vanilla submits through client arrays aliasing generic slots (0 gl_Vertex, 2 gl_Normal, 3 gl_Color, 8..15 texcoords), and a leftover generic array wins and flattens the attribute (measured: constant gl_MultiTexCoord0 gave a flat green arm), baked permanently into ModelRenderer's display list on first compile
    public static void resetVanillaVertexArrayState() {
        LWJGL.glBindVertexArray(0);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        for (int slot = 0; slot < VANILLA_ALIASED_ATTRIBUTE_SLOTS; slot++) {
            LWJGL.glDisableVertexAttribArray(slot);
        }
    }

    // gl_Vertex/gl_Normal/gl_Color/gl_MultiTexCoord0..7 all alias generic slots below this.
    private static final int VANILLA_ALIASED_ATTRIBUTE_SLOTS = 16;

    // Hands unit 0 back to vanilla in a cache-trustworthy state before the arm draws; the pipeline's raw scratch binds desync GlStateManager's active-unit and texture-name caches, after which the skin bind no-ops and the arm samples the block atlas (the lime arm in an orange box); bouncing through another unit and clearing the binding defeats both caches
    private static void resyncTextureUnitZero() {
        // resetToUnit0 already does the step-through-unit-1 dance and the cached unbind leaves real GL and the cache both on 0, so the trailing raw glBindTexture was redundant
        GlTextureUnits.resetToUnit0();
        GlStateManager.bindTexture(0);
    }

    // Restores state after the hand
    public void endHandRendering() {
        LWJGL.glUseProgram(0);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        CapturedRenderingState.INSTANCE.setRenderStage(0); // MC_RENDER_STAGE_NONE
    }

    // Feeds the first-person hand its lightmap coordinate: BSL's gbuffers_hand derives all brightness from gl_TextureMatrix[1] * gl_MultiTexCoord1, and Sodium can leave the fixed-function coord stale, so set both the legacy texcoord and the bridge uniform to the player's combined light (Umbra's getPackedLightCoords)
    private void setupHandLightmap(int packedLight) {
        float blockLight = getBlockLightmapCoord(packedLight);
        float skyLight = getSkyLightmapCoord(packedLight);
        LWJGL.glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        setupLightmapTextureMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // Light at the player's eye, for the hand's lightmap
    private static int getHandPackedLight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return FULL_BRIGHT_LIGHTMAP;
        }
        BlockPos eyePos = new BlockPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
        return mc.world.getCombinedLight(eyePos, 0);
    }

    // Block light half of packed light as a lightmap coordinate
    private static float getBlockLightmapCoord(int packedLight) {
        return packedLight & 0xFFFF;
    }

    // Sky light half
    private static float getSkyLightmapCoord(int packedLight) {
        return (packedLight >>> 16) & 0xFFFF;
    }

    // Vanilla's lightmap texture matrix, which packs expect on unit 1
    private static void setupLightmapTextureMatrix() {
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.loadIdentity();
        GlStateManager.translate(LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET, LIGHTMAP_TEXTURE_OFFSET);
        GlStateManager.scale(LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE, LIGHTMAP_TEXTURE_SCALE);
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // renderWorld RETURN: run the composite chain and final pass, then hand a clean GL state back to vanilla.
    public void finishWorldRendering() {
        if (!this.worldRenderingActive) {
            return;
        }
        this.worldRenderingActive = false;
        // An alphaTest.<program> override must not survive into the composite chain or vanilla's GUI pass.
        if (this.activeAlphaTest != null) {
            this.activeAlphaTest.restore();
            this.activeAlphaTest = null;
        }
        Minecraft mc = Minecraft.getMinecraft();

        // Full-screen passes draw with depth/blend/alpha-test off; going through GlStateManager keeps its cache coherent so vanilla's later toggles are not skipped
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();

        bindDepthSamplers();

        // centerDepthSmooth: sample depthtex0 at the screen centre now that all geometry including translucents is in it, before any composite consumes the uniform (OptiFine's readCenterDepth in renderHand1)
        this.centerDepthSampler.sample(this.renderTargets.getDepthTexture().getTextureId(),
                this.renderTargets.getWidth(), this.renderTargets.getHeight(),
                SystemTimeUniforms.COUNTER.getLastFrameTime(), this.centerDepthHalfLife);

        for (FullscreenPass pass : this.passes) {
            runPass(pass, mc);
        }

        if (this.blitSourceFramebuffer != null) {
            this.blitSourceFramebuffer.bindAsReadBuffer();
            int target = OpenGlHelper.isFramebufferEnabled() ? mc.getFramebuffer().framebufferObject : 0;
            LWJGL.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            LWJGL.glBlitFramebuffer(0, 0, this.renderTargets.getWidth(), this.renderTargets.getHeight(),
                    0, 0, mc.displayWidth, mc.displayHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }

        // Optional wide-gamut output conversion in place on the presentation target as the last colour op; zero cost when the colorspace is sRGB
        if (this.colorSpaceConverter.isActive()) {
            bindMainRenderTarget(mc);
            restoreMainDrawReadBuffers(mc);
            this.colorSpaceConverter.run(mc.displayWidth, mc.displayHeight, this.quadRenderer);
        }

        resetRenderTargetMipmaps();

        // Umbra's SwapPass: odd-flipped buffers carry this frame's data on ALT, copy back to MAIN so next frame's baked FBOs read fresh data; glCopyTexSubImage2D reads the GL_READ_BUFFER of GL_FRAMEBUFFER (bind(), not bindAsReadBuffer(), which broke TAA on many drivers for Umbra)
        if (!this.swapPasses.isEmpty()) {
            // glCopyTexSubImage2D needs the destination bound to a unit; binding on the selected unit 0 would rewrite a cached slot behind GlStateManager's back and its next cached bind would no-op, so copy on a scratch unit no cached slot describes
            GlTextureUnits.selectScratch(DEPTH_COPY_SCRATCH_UNIT);
            try {
                for (SwapPass swap : this.swapPasses) {
                    swap.from.bind();
                    LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, swap.targetTexture);
                    LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, swap.width, swap.height);
                }
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            } finally {
                GlTextureUnits.releaseScratch();
            }
        }
        // Hand control back to vanilla: its framebuffer bound, no program/VAO, texture units cleaned up.
        LWJGL.glUseProgram(0);
        bindMainRenderTarget(mc);
        restoreMainDrawReadBuffers(mc);
        restoreTextureUnits();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();

    }

    // Whether the pipeline is between beginWorldRendering and its end
    public boolean isWorldRenderingActive() {
        return this.worldRenderingActive;
    }

    // rain.depth — true when the pack wants rain and snow to write depth.
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return this.rainDepth;
    }

    // beacon.beam.depth — true when the pack wants the beacon beam in depthtex.
    public boolean shouldWriteBeaconBeamToDepthBuffer() {
        return this.beaconBeamDepth;
    }

    // frustum.culling = false — the pack needs off-screen geometry drawn (Umbra shouldDisableFrustumCulling).
    public boolean shouldDisableFrustumCulling() {
        return !this.frustumCulling;
    }

    // occlusion.culling = false — the pack needs occluded geometry drawn.
    public boolean shouldDisableOcclusionCulling() {
        return !this.occlusionCulling;
    }

    // skipAllRendering — suppress all world geometry; the composite chain still runs.
    public boolean skipAllRendering() {
        return this.skipAllRendering;
    }

    // separateEntityDraws — entities are drawn in their own pass after the deferred chain.
    public boolean shouldSeparateEntityDraws() {
        return this.separateEntityDraws;
    }

    // particles.ordering — where particles fall relative to the deferred chain.
    public String getParticleOrdering() {
        return this.particleOrdering;
    }

    // backFace.<layer>: false when the pack wants that terrain layer's back faces drawn; layerOrdinal is BlockRenderLayer#ordinal()
    public boolean shouldCullBackFaces(int layerOrdinal) {
        return layerOrdinal < 0 || layerOrdinal >= this.backFaceCulling.length
                || this.backFaceCulling[layerOrdinal];
    }

    // Packs with a shadow pass replace vanilla's blob shadows
    public boolean shouldDisableVanillaEntityShadows() {
        return this.shadowRenderer != null;
    }

    // Compiles the pack's shadowcomp compute passes (.csh, an Umbra extension used by Complementary's floodfill); each index is a compute-only pass carrying the flip snapshot of its place in the chain so its reads and writes hit the same sides as the rest of the frame
    private void buildComputePasses(ShaderPack pack, BufferFlipper flipper) {
        for (int i = 0; i < ProgramArrayId.ShadowComposite.getNumPrograms(); i++) {
            Optional<ProgramSource> source = pack.getProgramSet().get(ProgramArrayId.ShadowComposite, i);
            if (!source.isPresent()) {
                continue;
            }
            List<ComputePass> computes = buildFamilyComputePasses(pack, source.get(), TextureStage.SHADOWCOMP);
            // A shadowcomp entry with a vertex+fragment pair draws a full-screen quad into shadowcolor0/1 (Umbra ShadowCompositeRenderer); no pack in circulation ships one, but it is scheduled so one that does is not dropped
            FullscreenPass raster = source.get().hasRasterStages()
                    ? buildShadowCompositePass(pack, source.get(), computes)
                    : null;
            if (raster != null) {
                this.shadowCompPasses.add(raster);
            } else if (!computes.isEmpty()) {
                this.shadowCompPasses.add(FullscreenPass.computeOnly(source.get().getName(),
                        snapshotFrontTextures(flipper), flipper.snapshot(), computes));
            }
        }
    }

    // Builds a raster shadowcomp pass: a full-screen quad into the shadow pass's own colour attachments at shadow-map resolution; these targets do not ping-pong, so no flip state
    private FullscreenPass buildShadowCompositePass(ShaderPack pack, ProgramSource source, List<ComputePass> computes) {
        if (this.shadowRenderer == null) {
            LOGGER.warn("[Umbra] '{}' draws into shadowcolor but the pack declares no shadow program; skipping",
                    source.getName());
            return null;
        }
        String name = source.getName();
        try {
            UmbraProgram program = cachedProgram(source);
            if (program == null) {
                return null;
            }
            int[] drawBuffers = sanitizeShadowCompositeDrawBuffers(name, program.getDrawBuffers());
            UmbraFramebuffer framebuffer = new UmbraFramebuffer();
            for (int i = 0; i < drawBuffers.length; i++) {
                framebuffer.addColorAttachment(drawBuffers[i], i, drawBuffers[i] == 0
                        ? this.shadowRenderer.getColorTextureId()
                        : this.shadowRenderer.getColorTexture1Id());
            }
            checkFramebufferComplete(framebuffer, "shadowcomp", drawBuffers);

            FullscreenPass pass = new FullscreenPass(name, program, this.compiledUniforms.get(name), framebuffer,
                    snapshotFrontTextures(this.renderTargets.getBufferFlipper()), drawBuffers,
                    ProgramBlendState.from(pack.getProperties(), name),
                    new BitSet(), new BitSet(), new BitSet(), computes);
            pass.shadowSamplerKinds = shadowSamplerKindsFor(name);
            pass.viewportWidth = this.shadowRenderer.getResolution();
            pass.viewportHeight = this.shadowRenderer.getResolution();
            return pass;
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build shadowcomp pass '{}'; it will be skipped: {}", name, e.getMessage());
            return null;
        }
    }

    // Only shadowcolor0/1 exist, so any higher index a shadowcomp DRAWBUFFERS names has nothing to attach to.
    private static int[] sanitizeShadowCompositeDrawBuffers(String name, int[] drawBuffers) {
        int[] sanitized = new int[drawBuffers.length];
        int count = 0;
        for (int buffer : drawBuffers) {
            if (buffer > 1) {
                LOGGER.warn("[Umbra] '{}' writes shadowcolor{}, but only shadowcolor0/1 exist; dropping it",
                        name, buffer);
                continue;
            }
            sanitized[count++] = buffer;
        }
        return count == 0 ? new int[]{0} : Arrays.copyOf(sanitized, count);
    }

    // Compiles every compute stage attached to one program (<name>.csh plus the <name>_a..z.csh Umbra extension); dispatch size follows Umbra's priority: indirect directive, const ivec3 workGroups, const vec2 workGroupsRender, then one invocation per pixel
    private List<ComputePass> buildFamilyComputePasses(ShaderPack pack, ProgramSource source, TextureStage stage) {
        String[] computeSources = source.getComputeSources();
        if (computeSources.length == 0) {
            return java.util.Collections.emptyList();
        }
        if (!isProgramEnabled(pack, source.getName())) {
            return java.util.Collections.emptyList();
        }

        // The voxel-volume dispatch fallback is a shadowcomp-only workaround for Complementary's under-declared floodfill groups; Umbra never second-guesses a declared `workGroups`, and Photon's deferred4_a.csh declares (1,1,1) for a parallel reduction that scaling would run 4096 times over
        int[] volume = stage == TextureStage.SHADOWCOMP ? this.customImageManager.getFirst3DImageSize() : null;
        List<ComputePass> built = new ArrayList<>();
        for (int variant = 0; variant < computeSources.length; variant++) {
            if (computeSources[variant] == null) {
                continue;
            }
            String name = ProgramSource.computeVariantName(source.getName(), variant);
            try {
                String csh = com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(
                        CustomTextureTransformer.transform(name, computeSources[variant], stage),
                        this.shaderDefines);
                csh = stabilizeShaderSource(name, csh);
                int[] localSize = parseLocalSize(csh);
                int[] fallbackWorkGroups = (volume != null && localSize != null)
                        ? new int[]{
                                ceilDiv(volume[0], localSize[0]),
                                ceilDiv(volume[1], localSize[1]),
                                ceilDiv(volume[2], localSize[2])
                        }
                        : null;
                float[] renderScale = parseWorkGroupsRender(csh, this.shaderDefines);
                long[] indirectPointer = this.indirectDispatchPointers.get(name);
                int indirectBuffer = -1;
                long indirectOffset = 0L;
                if (indirectPointer != null && this.shaderStorageBuffers != null) {
                    indirectBuffer = this.shaderStorageBuffers.getBufferId((int) indirectPointer[0]);
                    indirectOffset = indirectPointer[1];
                    if (indirectBuffer == -1) {
                        LOGGER.warn("[Umbra] Compute pass '{}' requests indirect dispatch from undeclared bufferObject.{}",
                                name, indirectPointer[0]);
                    }
                }

                int[] workGroups = parseWorkGroups(csh, this.shaderDefines);
                if (workGroups != null && fallbackWorkGroups != null
                        && !coversVolume(workGroups, localSize, volume)) {
                    LOGGER.warn("[Umbra] Compute pass '{}' declared dispatch {}x{}x{} does not cover custom image volume {}x{}x{} with local {}x{}x{}; using {}x{}x{}",
                            name,
                            workGroups[0], workGroups[1], workGroups[2],
                            volume[0], volume[1], volume[2],
                            localSize[0], localSize[1], localSize[2],
                            fallbackWorkGroups[0], fallbackWorkGroups[1], fallbackWorkGroups[2]);
                    workGroups = fallbackWorkGroups;
                }
                if (workGroups == null) {
                    if (fallbackWorkGroups != null) {
                        workGroups = fallbackWorkGroups;
                    } else if (renderScale == null && indirectBuffer == -1) {
                        // Umbra ComputeProgram.getWorkGroups' last resort: cover the screen, one invocation per pixel.
                        renderScale = new float[]{1.0f, 1.0f};
                        workGroups = new int[]{1, 1, 1};
                    } else {
                        workGroups = new int[]{1, 1, 1};
                    }
                }
                GlShader shader = new GlShader(ShaderType.COMPUTE, name + ".csh", csh);
                GlProgram program;
                try {
                    program = ProgramBuilder.begin(name).attach(shader).link();
                } finally {
                    shader.destroy();
                }
                program.bind();
                assignSamplerUnits(program.getGlId(), FULLSCREEN_SAMPLER_UNITS,
                        mergedStageOverrides(stage), this.flippedAtLeastOnce);
                program.unbind();
                ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program.getGlId());
                CommonUniforms.addCommonUniforms(uniforms);
                MatrixUniforms.addMatrixUniforms(uniforms);
                com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.assignTo(uniforms);
                ComputePass compute = new ComputePass(name, program, uniforms.buildUniforms(),
                        workGroups[0], workGroups[1], workGroups[2],
                        renderScale != null ? renderScale[0] : Float.NaN,
                        renderScale != null ? renderScale[1] : Float.NaN,
                        localSize != null ? localSize[0] : 1,
                        localSize != null ? localSize[1] : 1,
                        indirectBuffer, indirectOffset);
                compute.shadowSamplerKinds = ShadowSamplerKinds.detect(program.getGlId());
                built.add(compute);
            } catch (Exception e) {
                LOGGER.error("[Umbra] Failed to build compute pass '{}'; it will be skipped: {}", name, e.getMessage());
            }
        }
        return built;
    }

    // Honours program.<name>.enabled
    private static boolean isProgramEnabled(ShaderPack pack, String programName) {
        return pack.getProperties().getProgramEnabled(programName).orElse(Boolean.TRUE);
    }

    private static final Pattern UNINITIALIZED_LIGHT_VOLUME =
            Pattern.compile("(?m)^([\\t ]*)vec4\\s+lightVolume\\s*;[\\t ]*$");
    // `#version <number>`, first one wins like the driver; a companion rewrite that flattened Complementary's per-frame dither reroll (chasing block-edge shimmer whose real cause was the zeroed at_midBlock) is gone, since freezing it turned its ~15-sample light shafts into static slabs of fog, and Umbra never rewrites pack source this way
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");

    // The ARB_shader_texture_lod entry points, only legal in #version 130+ when the pack enabled the extension; renamed to the core textureGrad family like Umbra's CommonTransformer, since Just Colored Lighting and Sildur's call texture2DGradARB from 430 sources and NVIDIA rejects it (C7531)
    private static final Map<String, String> ARB_TEXTURE_LOD_FUNCTIONS;

    static {
        Map<String, String> arb = new LinkedHashMap<>();
        arb.put("texture2DGradARB", "textureGrad");
        arb.put("texture3DGradARB", "textureGrad");
        arb.put("textureCubeGradARB", "textureGrad");
        arb.put("texture2DLodARB", "textureLod");
        arb.put("texture2DProjGradARB", "textureProjGrad");
        arb.put("shadow2DGradARB", "textureGrad");
        ARB_TEXTURE_LOD_FUNCTIONS = java.util.Collections.unmodifiableMap(arb);
    }

    // Rewrites the *ARB texture-lookup entry points to their core equivalents only for #version 130+ sources; a GLSL 120 pack enables the extension itself and textureGrad does not exist there
    private static String normalizeArbTextureLookups(String name, String source) {
        Matcher version = VERSION_DIRECTIVE.matcher(source);
        if (!version.find()) {
            return source;
        }
        int versionNumber;
        try {
            versionNumber = Integer.parseInt(version.group(1));
        } catch (NumberFormatException e) {
            return source;
        }
        if (versionNumber < 130) {
            return source;
        }

        String result = source;
        for (Map.Entry<String, String> entry : ARB_TEXTURE_LOD_FUNCTIONS.entrySet()) {
            if (!result.contains(entry.getKey())) {
                continue;
            }
            result = result.replaceAll("(?<![A-Za-z0-9_])" + Pattern.quote(entry.getKey()) + "(?=\\s*\\()",
                    Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }

    // Resolves the #if directives the driver's preprocessor cannot accept (float comparisons, malformed expressions), leaving the rest to the driver; split from stabilizeShaderSource for the two legacy paths, and callers must inline the macro environment as #define lines first (three callers: gbuffer, legacy fullscreen, UmbraTerrainProgramOverride)
    public static String foldUncompilableConditionals(String name, String source) {
        return GlslPreprocessor.foldFloatConditionals(source, java.util.Collections.emptyMap());
    }

    // Applies the fixes every source needs before any transform
    public static String stabilizeShaderSource(String name, String source) {
        source = foldUncompilableConditionals(name, source);
        source = normalizeArbTextureLookups(name, source);
        source = com.bdmajora.impetus.umbra.terrain.GlslIntegerOverloadPolyfill.widenIntegerBuiltinCalls(name, source);
        Matcher declaration = UNINITIALIZED_LIGHT_VOLUME.matcher(source);
        if (declaration.find()) {
            source = declaration.replaceAll("$1vec4 lightVolume = vec4(0.0);");
        }
        return source;
    }

    // Compute work group counts, honouring #ifdef gates
    private static int[] parseWorkGroups(String source, Map<String, String> defines) {
        String active = preprocessActiveShaderSource(source, defines);
        int[] workGroups = parseWorkGroupsDirect(active);
        return workGroups != null ? workGroups : parseWorkGroupsDirect(source);
    }

    // the `const vec2 workGroupsRender` scale factors, or null when not declared
    private static float[] parseWorkGroupsRender(String source, Map<String, String> defines) {
        String active = preprocessActiveShaderSource(source, defines);
        float[] scale = parseWorkGroupsRenderDirect(active);
        return scale != null ? scale : parseWorkGroupsRenderDirect(source);
    }

    // workGroupsRender directive, screen-relative
    private static float[] parseWorkGroupsRenderDirect(String source) {
        Matcher matcher = Pattern.compile(
                "const\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(([^)]*)\\)")
                .matcher(stripGlslComments(source));
        if (!matcher.find()) {
            return null;
        }
        try {
            String[] values = matcher.group(1).split(",");
            float x = Float.parseFloat(values[0].trim().replace("f", ""));
            float y = values.length > 1 ? Float.parseFloat(values[1].trim().replace("f", "")) : x;
            return new float[]{x, y};
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Parses every indirect.<pass> = <bufferObjectIndex> <offsetBytes> directive (Umbra syntax).
    private static Map<String, long[]> parseIndirectPointers(Map<String, String> rawProperties) {
        Map<String, long[]> pointers = new LinkedHashMap<>();
        rawProperties.forEach((key, value) -> {
            if (!key.startsWith("indirect.")) {
                return;
            }
            try {
                String[] parts = value.trim().split("\\s+");
                pointers.put(key.substring("indirect.".length()),
                        new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])});
            } catch (RuntimeException e) {
                LOGGER.warn("[Umbra] Malformed indirect directive '{} = {}'", key, value);
            }
        });
        return pointers;
    }

    // Delegates to GlslPreprocessor#resolveConditionals, which falls back to raw source when resolution yields nothing (an unterminated #if would otherwise swallow the file); a named seam because the compute-directive callers pass their own define maps
    private static String preprocessActiveShaderSource(String source, Map<String, String> defines) {
        return GlslPreprocessor.resolveConditionals(source, defines);
    }

    // workGroups directive, absolute
    private static int[] parseWorkGroupsDirect(String source) {
        String stripped = stripGlslComments(source);
        Matcher vector = Pattern.compile(
                "const\\s+(?:u?ivec|vec)([234])\\s+workGroups\\s*=\\s*(?:u?ivec|vec)\\d\\s*\\(([^)]*)\\)")
                .matcher(stripped);
        if (vector.find()) {
            int dimensions = Integer.parseInt(vector.group(1));
            String[] values = vector.group(2).split(",");
            if (values.length == dimensions || values.length == 1) {
                int[] groups = {1, 1, 1};
                for (int i = 0; i < Math.min(3, dimensions); i++) {
                    Integer value = parsePositiveInt(values.length == 1 ? values[0] : values[i]);
                    if (value == null) {
                        return null;
                    }
                    groups[i] = value;
                }
                return groups;
            }
        }

        int x = parseNamedWorkGroup(stripped, "X");
        int y = parseNamedWorkGroup(stripped, "Y");
        int z = parseNamedWorkGroup(stripped, "Z");
        if (x > 0 || y > 0 || z > 0) {
            return new int[]{Math.max(1, x), Math.max(1, y), Math.max(1, z)};
        }
        return null;
    }

    // Whether the dispatch reaches every element of the target volume
    private static boolean coversVolume(int[] workGroups, int[] localSize, int[] volume) {
        return workGroups[0] * localSize[0] >= volume[0]
                && workGroups[1] * localSize[1] >= volume[1]
                && workGroups[2] * localSize[2] >= volume[2];
    }

    // layout(local_size_x = ...) values
    private static int[] parseLocalSize(String source) {
        Matcher matcher = Pattern.compile(
                "local_size_x\\s*=\\s*(\\d+)(?:\\s*,\\s*local_size_y\\s*=\\s*(\\d+))?(?:\\s*,\\s*local_size_z\\s*=\\s*(\\d+))?")
                .matcher(stripGlslComments(source));
        if (!matcher.find()) {
            return null;
        }
        int x = Integer.parseInt(matcher.group(1));
        int y = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
        int z = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 1;
        return new int[]{x, y, z};
    }

    // One axis of a workGroups directive
    private static int parseNamedWorkGroup(String source, String axis) {
        Matcher matcher = Pattern.compile("const\\s+int\\s+workGroups" + axis + "\\s*=\\s*(\\d+)\\s*;")
                .matcher(source);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    // Null for anything not a positive integer
    private static Integer parsePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Integer ceiling division
    private static int ceilDiv(int value, int divisor) {
        return Math.max(1, (value + divisor - 1) / divisor);
    }

    // Removes line and block comments before directive parsing
    private static String stripGlslComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // Runs the shadowcomp compute chain: a full barrier makes the shadow pass's voxelization visible, each pass dispatches, and a closing barrier publishes to every later sampler read
    private void dispatchComputePasses() {
        if (this.shadowCompPasses.isEmpty()) {
            return;
        }
        // Umbra rebinds all images + paired samplers at every compute use; the shadow-terrain draw that just voxelized runs through managed code that can reset units, so re-establish the voxel/floodfill bindings here rather than trust the frame-start bindAll
        bindShaderPackResources();
        LWJGL.glMemoryBarrier(com.bdmajora.impetus.lwjgl.GL42.GL_ALL_BARRIER_BITS);
        boolean drewRaster = false;
        for (FullscreenPass pass : this.shadowCompPasses) {
            // Only the images, not bindColorSamplers(pass), for compute-only entries: this runs between the shadow map and the gbuffers, and colortex0..7 would land on units 0..7, the atlas and lightmap units the world is about to render with; image units are a separate namespace
            if (pass.program == null) {
                bindRenderTargetImages(pass);
                dispatchComputes(pass.computes);
            } else {
                // A raster shadowcomp draws into shadowcolor; runPass dispatches its own computes first, so they must not run again here, and the gbuffer bindings are restored after the loop since it needs the colortex samplers
                runPass(pass, Minecraft.getMinecraft());
                drewRaster = true;
            }
        }
        LWJGL.glUseProgram(0);
        if (drewRaster) {
            restoreTextureUnits();
            bindDepthSamplers();
            bindNoiseTexture();
            bindShaderPackResources();
            bindGbufferPbrSamplers();
            bindGbufferColorSamplers();
        }
    }

    // Dispatches compute programs in order with a full barrier between each, since Complementary's floodfill iterations read the previous output and a family pass's fragment stage reads its computes' output
    private void dispatchComputes(List<ComputePass> computes) {
        for (ComputePass pass : computes) {
            pass.program.bind();
            bindShaderPackResources();
            pass.uniforms.update();
            applyShadowSamplerKinds(pass.shadowSamplerKinds, false);
            if (pass.indirectBuffer != -1) {
                // Indirect dispatch: group counts read from the pack-declared SSBO at the given offset.
                LWJGL.glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, pass.indirectBuffer);
                LWJGL.glDispatchComputeIndirect(pass.indirectOffset);
                LWJGL.glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
            } else if (!Float.isNaN(pass.renderScaleX)) {
                // Screen-relative dispatch (`const vec2 workGroupsRender`), recomputed every frame from the render size and the shader's local_size
                int groupsX = Math.max(1, ceilDiv((int) Math.ceil(this.renderTargets.getWidth() * pass.renderScaleX), pass.localSizeX));
                int groupsY = Math.max(1, ceilDiv((int) Math.ceil(this.renderTargets.getHeight() * pass.renderScaleY), pass.localSizeY));
                LWJGL.glDispatchCompute(groupsX, groupsY, 1);
            } else {
                LWJGL.glDispatchCompute(pass.groupsX, pass.groupsY, pass.groupsZ);
            }
            // Each floodfill iteration reads the previous one's writes, so every dispatch is fenced by default; `allowConcurrentCompute` is the pack asserting independence
            if (!this.allowConcurrentCompute) {
                LWJGL.glMemoryBarrier(com.bdmajora.impetus.lwjgl.GL42.GL_ALL_BARRIER_BITS);
            }
        }
        if (this.allowConcurrentCompute) {
            // Still publish the whole group's writes before anything samples them.
            LWJGL.glMemoryBarrier(com.bdmajora.impetus.lwjgl.GL42.GL_ALL_BARRIER_BITS);
        }
    }

    // Frees the compute programs attached to a pass family
    private static void destroyFamilyComputes(List<FullscreenPass> family) {
        for (FullscreenPass pass : family) {
            for (ComputePass compute : pass.computes) {
                compute.program.destroy();
            }
        }
    }

    // Captures the block-atlas dimensions for the atlasSize/terrainTextureSize uniforms.
    private static void captureAtlasSize(Minecraft mc) {
        net.minecraft.client.renderer.texture.ITextureObject atlas =
                mc.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (atlas == null) {
            return;
        }
        int previousTexture = bindScratchTexture2D(atlas.getGlTextureId());
        try {
            int width = LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            CapturedRenderingState.INSTANCE.setAtlasSize(width, height);
        } finally {
            restoreScratchTexture2D(previousTexture);
        }
    }

    // ------------------------------------------------------------------ state helpers

    // Runs a numbered family that executes mid world rendering (begin, prepare): quads draw with depth, blend and alpha test off, then the gbuffer state is put back; composite and deferred do this inline since they also switch flip snapshots
    private void runFullscreenFamily(List<FullscreenPass> family, Minecraft mc) {
        if (family.isEmpty()) {
            return;
        }
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();

        bindDepthSamplers();
        bindShaderPackResources();
        for (FullscreenPass pass : family) {
            runPass(pass, mc);
        }

        LWJGL.glUseProgram(0);
        restoreTextureUnits();
        bindDepthSamplers();
        bindShadowSamplers();
        bindNoiseTexture();
        bindShaderPackResources();
        bindGbufferPbrSamplers();
        bindGbufferColorSamplers();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);

        if (this.currentGbuffer != null) {
            this.currentGbuffer.bind();
            LWJGL.glViewport(0, 0, this.renderTargets.getWidth(), this.renderTargets.getHeight());
        }
    }

    // Runs one full-screen pass into its framebuffer (or Minecraft's framebuffer for the final pass).
    private void runPass(FullscreenPass pass, Minecraft mc) {
        // Umbra CompositeRenderer.renderAll: the pass's computes dispatch first under its flip state, then a barrier publishes their writes to the draw (deferred4 texelFetches the SH deferred4_a imageStored); samplers and images must be established before dispatching
        if (!pass.computes.isEmpty()) {
            bindColorSamplers(pass);
            bindRenderTargetImages(pass);
            dispatchComputes(pass.computes);
            LWJGL.glUseProgram(0);
        }
        if (pass.program == null) {
            return;
        }
        if (pass.framebuffer != null) {
            pass.framebuffer.bind();
            // A pass writing an explicitly-sized buffer draws at that buffer's resolution, not the screen's.
            int passWidth = pass.viewportWidth > 0 ? pass.viewportWidth : this.renderTargets.getWidth();
            int passHeight = pass.viewportHeight > 0 ? pass.viewportHeight : this.renderTargets.getHeight();
            // `scale.<program>` on top, arithmetic per Umbra: the offsets are fractions of the pass size, not texels, and the scaled extent is truncated rather than rounded
            LWJGL.glViewport(
                    (int) (passWidth * pass.viewportOffsetX),
                    (int) (passHeight * pass.viewportOffsetY),
                    (int) (passWidth * pass.viewportScale),
                    (int) (passHeight * pass.viewportScale));
        } else {
            bindMainRenderTarget(mc);
            restoreMainDrawReadBuffers(mc);
        }
        GlStateManager.disableBlend();
        disableIndexedBlend(pass.drawBuffers.length);
        pass.blendState.apply(pass.drawBuffers);
        setupMipmappedBuffers(pass);
        bindColorSamplers(pass);
        pass.program.bind();
        bindShaderPackResources();
        pass.uniforms.update();
        // Last, since nothing above touches the shadow units but the previous pass may have left the other flavour there
        applyShadowSamplerKinds(pass.shadowSamplerKinds, false);
        if (this.modernPack) {
            // The [0,1] quad maps to NDC via an ortho projection with modelview/texture identity, so ftransform() = ortho*[0,1] = NDC and gl_TextureMatrix[0]*gl_MultiTexCoord0 passes through; saved and restored so the hand and GUI after the chain are unaffected
            pushFullscreenFixedFunctionMatrices();
            this.quadRenderer.draw();
            popFixedFunctionMatrices();
        } else {
            this.quadRenderer.draw();
        }
    }

    // Generates mips on targets the pass declared via mipmapEnabled
    private void setupMipmappedBuffers(FullscreenPass pass) {
        if (pass.mipmappedBuffers.isEmpty()) {
            return;
        }
        GlTextureUnits.selectScratch(MIPMAP_SCRATCH_UNIT);
        for (int index = pass.mipmappedBuffers.nextSetBit(0); index >= 0;
             index = pass.mipmappedBuffers.nextSetBit(index + 1)) {
            UmbraRenderTarget target = this.renderTargets.get(index);
            if (target != null) {
                target.generateMipmaps(pass.flipsBefore.get(index));
            }
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Returns every target to the non-mipmapped filter at frame end
    private void resetRenderTargetMipmaps() {
        GlTextureUnits.selectScratch(MIPMAP_SCRATCH_UNIT);
        for (int i = 0; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            UmbraRenderTarget target = this.renderTargets.get(i);
            if (target != null) {
                target.resetMipmaps();
            }
        }
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Clears any pending GL error so the next check is attributable
    public static void drainGlError() {
        while (LWJGL.glGetError() != 0) {
            // discard
        }
    }

    // Logs a pending GL error with the call site
    public static void reportGlError(String where) {
        int error = LWJGL.glGetError();
        if (error != 0) {
            LOGGER.warn("[Umbra] GL error 0x{} ({}) at: {}",
                    Integer.toHexString(error), error, where);
        }
    }

    // Fixed-function matrix modes (GL_MODELVIEW/PROJECTION/TEXTURE); not in the core GL wrapper we use elsewhere.
    private static final int GL_MODELVIEW_MODE = 0x1700;
    private static final int GL_PROJECTION_MODE = 0x1701;
    private static final int GL_TEXTURE_MODE = 0x1702;

    // Identity matrices for fullscreen passes that still use fixed-function transforms
    private static void pushFullscreenFixedFunctionMatrices() {
        // Projection is the ortho mapping the [0,1] quad to NDC (matching Umbra's composite gl_ProjectionMatrix), so `gl_Position = ftransform()` (Complementary/BSL) resolves correctly; modelview and texture stay identity
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
    }

    // Restores the matrices
    private static void popFixedFunctionMatrices() {
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.popMatrix();
    }

    // Copies the active gbuffer framebuffer's depth into destination (how OptiFine snapshots depthtex1/2), on a scratch unit so no vanilla-tracked binding is disturbed
    private void copyDepthTexture(DepthTexture destination) {
        // Umbra/OptiFine copy from the shader framebuffer's depth attachment; do not rely on whatever a previous hook, hand render or post pass left bound
        if (this.currentGbuffer != null) {
            this.currentGbuffer.bind();
        }
        int previousTexture = bindScratchTexture2D(destination.getTextureId());
        try {
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    this.renderTargets.getWidth(), this.renderTargets.getHeight());
        } finally {
            restoreScratchTexture2D(previousTexture);
        }
        bindShaderPackResources();
    }

    // Binds and returns the previous binding for restore
    private static int bindScratchTexture2D(int texture) {
        GlTextureUnits.selectScratch(DEPTH_COPY_SCRATCH_UNIT);
        int previousTexture = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        return previousTexture;
    }

    // Rebinds the saved texture
    private static void restoreScratchTexture2D(int texture) {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlTextureUnits.resetToUnit0();
    }

    // Back to vanilla's framebuffer
    private static void bindMainRenderTarget(Minecraft mc) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            LWJGL.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            LWJGL.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    // Resets draw and read buffers after an FBO with custom ones
    private static void restoreMainDrawReadBuffers(Minecraft mc) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            LWJGL.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            LWJGL.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        } else {
            LWJGL.glDrawBuffers(GL_BACK_BUFFER);
            LWJGL.glReadBuffer(GL_BACK_BUFFER);
        }
    }

    // Binds each colortex the pass samples, respecting flips
    private void bindColorSamplers(FullscreenPass pass) {
        for (int i = UmbraRenderTargets.MAX_COLOR_BUFFERS - 1; i >= 0; i--) {
            if (pass.colorSamplers[i] != 0) {
                LWJGL.glBindSampler(i, 0);
                if (i < 8) {
                    // Units 0..7 go through GlStateManager so vanilla's texture-unit cache stays coherent.
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
                    GlStateManager.bindTexture(pass.colorSamplers[i]);
                } else {
                    // colortex8..15 live beyond GlStateManager's cache (indexing throws) and vanilla never touches these units, so a raw bind is correct; the tail hands the selector back
                    GlTextureUnits.selectScratch(i);
                    LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, pass.colorSamplers[i]);
                }
            }
        }
        // Deliberately not a bare setActiveTexture(GL_TEXTURE0): after the raw branch real GL is on a high unit while the cache reads 0, so the cached call would no-op and strand the selector; resetToUnit0 steps through unit 1
        GlTextureUnits.resetToUnit0();
    }

    // Binds the colorimgN images for one pass, flip-aware like Umbra so a compute writing colorimg4 hits the exact texture later programs sample as colortex4
    private void bindRenderTargetImages(FullscreenPass pass) {
        if (this.renderTargetImageUnits.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Integer> entry : this.renderTargetImageUnits.entrySet()) {
            UmbraRenderTarget target = this.renderTargets.get(entry.getKey());
            if (target == null) {
                continue;
            }
            int texture = pass.flipsBefore.get(entry.getKey()) ? target.getAltTexture() : target.getMainTexture();
            LWJGL.glBindImageTexture(entry.getValue(), texture, 0, false, 0, GL15.GL_READ_WRITE,
                    target.getInternalFormat().getInternalFormat());
        }
        bindShadowColorImages();
    }

    // Binds shadowcolorimg0/1 over the shadow pass's colour attachments; they do not ping-pong, so no flip state to follow
    private void bindShadowColorImages() {
        if (this.shadowColorImageUnits.isEmpty() || this.shadowRenderer == null) {
            return;
        }
        for (Map.Entry<Integer, Integer> entry : this.shadowColorImageUnits.entrySet()) {
            int texture = entry.getKey() == 0
                    ? this.shadowRenderer.getColorTextureId()
                    : this.shadowRenderer.getColorTexture1Id();
            if (texture == 0) {
                continue;
            }
            LWJGL.glBindImageTexture(entry.getValue(), texture, 0, false, 0, GL15.GL_READ_WRITE,
                    UmbraShadowRenderer.SHADOW_COLOR_INTERNAL_FORMAT);
        }
    }

    // depthtex0, 1 and 2
    private void bindDepthSamplers() {
        // Bind both the high fullscreen units and the OptiFine 1.12 gbuffers units (6/12).
        bindDepthSampler(DEPTH_TEX_0_UNIT, this.renderTargets.getDepthTexture());
        bindDepthSampler(DEPTH_TEX_1_UNIT, this.renderTargets.getDepthTextureNoTranslucents());
        bindDepthSampler(DEPTH_TEX_2_UNIT, this.renderTargets.getDepthTextureNoHand());
        bindDepthSampler(GBUFFER_DEPTH_TEX_0_UNIT, this.renderTargets.getDepthTexture());
        bindDepthSampler(GBUFFER_DEPTH_TEX_1_UNIT, this.renderTargets.getDepthTextureNoTranslucents());
        bindDhDepthSamplers();
        GlTextureUnits.resetToUnit0();
    }

    // dhDepthTex0/1: DH's LOD depth and its pre-translucent copy once DH has rendered a frame, else the terrain depth textures so a DH-aware pack sampling them without DH reads sane depth
    private void bindDhDepthSamplers() {
        int lodDepth = this.dhCompat == null ? -1 : this.dhCompat.getDepthTex();
        int lodDepthNoTranslucent = this.dhCompat == null ? -1 : this.dhCompat.getDepthTexNoTranslucent();
        LWJGL.glBindSampler(DH_DEPTH_TEX_0_UNIT, 0);
        LWJGL.glBindSampler(DH_DEPTH_TEX_1_UNIT, 0);
        bindTextureUnit(DH_DEPTH_TEX_0_UNIT, lodDepth > 0 ? lodDepth : this.renderTargets.getDepthTexture().getTextureId());
        bindTextureUnit(DH_DEPTH_TEX_1_UNIT, lodDepthNoTranslucent > 0 ? lodDepthNoTranslucent
                : this.renderTargets.getDepthTextureNoTranslucents().getTextureId());
    }

    // normals and specular atlases, or the neutral fallbacks
    private void bindGbufferPbrSamplers() {
        // PBR maps on the gbuffer-stage normals/specular units (2/3, through GlStateManager); fullscreen passes overwrite these with colortex2/3, so rebind before water and hand sample the atlas again
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 2);
        GlStateManager.bindTexture(com.bdmajora.impetus.umbra.pbr.PBRAtlasManager.getNormalsAtlas(
                this.defaultNormals.getTextureId()));
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 3);
        GlStateManager.bindTexture(com.bdmajora.impetus.umbra.pbr.PBRAtlasManager.getSpecularAtlas(
                this.defaultSpecular.getTextureId()));
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // One depth texture to one unit
    private static void bindDepthSampler(int unit, DepthTexture texture) {
        LWJGL.glBindSampler(unit, 0);
        bindTextureUnit(unit, texture.getTextureId());
    }

    // iris_overlay is constant for the whole frame; bound alongside the other frame-long samplers.
    private void bindOverlayTexture() {
        bindTextureUnit(OVERLAY_TEX_UNIT, this.noOverlayTexture.getTextureId());
        bindTextureUnit(GBUFFER_OVERLAY_UNIT, this.noOverlayTexture.getTextureId());
        GlTextureUnits.resetToUnit0();
    }

    // noisetex
    private void bindNoiseTexture() {
        int customNoise = this.customTextureManager.getNoiseTextureId();
        int texture = customNoise != -1 ? customNoise : this.noiseTexture.getTextureId();
        bindTextureUnit(NOISE_TEX_UNIT, texture);
        bindTextureUnit(GBUFFER_NOISE_TEX_UNIT, texture);
        GlTextureUnits.resetToUnit0();
    }

    // shadowtex and shadowcolor, or the stub when there is no shadow pass
    private void bindShadowSamplers() {
        if (this.shadowRenderer == null && this.stubShadowMap == null) {
            return;
        }
        int depth0;
        int depth1;
        int color0 = 0;
        int color1 = 0;
        if (this.shadowRenderer == null) {
            depth0 = this.stubShadowMap.getTextureId();
            depth1 = this.stubShadowMap.getTextureId();
        } else {
            depth0 = this.shadowRenderer.getDepthTextureId();
            depth1 = this.shadowRenderer.getDepthTextureNoTranslucentsId();
            color0 = this.shadowRenderer.getColorTextureId();
            color1 = this.shadowRenderer.getColorTexture1Id();
        }
        int sampler0 = shadowHardwareSamplerFor(0);
        int sampler1 = shadowHardwareSamplerFor(1);
        // OptiFine 1.12 packs declare sampler2DShadow shadowtex0/1 directly under shadowHardwareFiltering, so the plain names default to the comparison sampler; only SEPARATE_HARDWARE_SAMPLERS packs expect raw depth there with comparison on the *HW aliases (Umbra UmbraSamplers). A program that declares one of them as a plain sampler2D instead (Complementary's composite1 texelFetches shadowtex0 for its light shafts) gets the raw flavour swapped in at bind time by applyShadowSamplerKinds
        int plain0 = this.separateHardwareSamplers ? 0 : sampler0;
        int plain1 = this.separateHardwareSamplers ? 0 : sampler1;
        bindShadowDepthUnit(SHADOW_TEX_0_UNIT, depth0, plain0);
        bindShadowDepthUnit(SHADOW_TEX_1_UNIT, depth1, plain1);
        // The *HW aliases exist only for SEPARATE_HARDWARE_SAMPLERS packs; holding two units hostage for everyone else starved Complementary's wsr_sampler/wsr_lod_sampler onto unit 0, tracing an empty voxel volume
        if (this.separateHardwareSamplers) {
            bindShadowDepthUnit(SHADOW_TEX_0_HW_UNIT, depth0, sampler0);
            bindShadowDepthUnit(SHADOW_TEX_1_HW_UNIT, depth1, sampler1);
        }
        bindShadowDepthUnit(GBUFFER_SHADOW_TEX_0_UNIT, depth0, plain0);
        bindShadowDepthUnit(GBUFFER_SHADOW_TEX_1_UNIT, depth1, plain1);
        bindTextureUnit(SHADOW_COLOR_0_UNIT, color0);
        bindTextureUnit(SHADOW_COLOR_1_UNIT, color1);
        bindTextureUnit(GBUFFER_SHADOW_COLOR_0_UNIT, color0);
        bindTextureUnit(GBUFFER_SHADOW_COLOR_1_UNIT, color1);
        GlTextureUnits.resetToUnit0();
    }

    // One shadow depth texture with its compare sampler object
    private void bindShadowDepthUnit(int unit, int texture, int sampler) {
        LWJGL.glBindSampler(unit, sampler);
        bindTextureUnit(unit, texture);
    }

    // Binds one sampler unit leaving the selector *on that unit*; callers batch several and reset once via resetToUnit0(), which is load-bearing for units at or above CACHED_UNITS taking the raw branch
    private static void bindTextureUnit(int unit, int texture) {
        if (unit < GlTextureUnits.CACHED_UNITS) {
            // Low OptiFine 1.12 sampler units overlap Minecraft's cached slots; keep the cache coherent or later GlStateManager binds are skipped while the real unit holds depth/shadow data
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager.bindTexture(texture);
        } else {
            GlTextureUnits.selectScratch(unit);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    // The comparison sampler for a shadow depth texture, bound only to the *HW units that sampler2DShadow programs are pointed at (undefined without comparison), so comparison is unconditional and shadowHardwareFiltering only picks the filtering flavour
    private int shadowHardwareSamplerFor(int index) {
        if (this.shadowMipmap[index]) {
            return this.shadowNearest[index] ? this.shadowMippedNearestHwSampler : this.shadowMippedLinearHwSampler;
        }
        return this.shadowNearest[index] ? this.shadowNearestHwSampler : this.shadowLinearHwSampler;
    }

    // The raw-depth sampler with the same filtering flavour, for a unit a program samples through a plain sampler2D
    private int shadowRawSamplerFor(int index) {
        if (this.shadowMipmap[index]) {
            return this.shadowNearest[index] ? this.shadowMippedNearestRawSampler : this.shadowMippedLinearRawSampler;
        }
        return this.shadowNearest[index] ? this.shadowNearestRawSampler : this.shadowLinearRawSampler;
    }

    // Re-points the two shadow depth units at the sampler object matching how the program about to draw DECLARED each of them: raw depth for a plain sampler2D, comparison for sampler2DShadow; called after every program bind, since bindShadowSamplers restores the comparison default on both units in between. Gbuffer-stage programs read the OptiFine units 4/5, everything else 19/20, and under SEPARATE_HARDWARE_SAMPLERS the plain units already carry no sampler object (the pack asked for raw depth there itself)
    public void applyShadowSamplerKinds(ShadowSamplerKinds kinds, boolean gbufferStage) {
        if (this.destroyed || this.separateHardwareSamplers || kinds == null) {
            return;
        }
        LWJGL.glBindSampler(gbufferStage ? GBUFFER_SHADOW_TEX_0_UNIT : SHADOW_TEX_0_UNIT,
                kinds.isRaw(0) ? shadowRawSamplerFor(0) : shadowHardwareSamplerFor(0));
        LWJGL.glBindSampler(gbufferStage ? GBUFFER_SHADOW_TEX_1_UNIT : SHADOW_TEX_1_UNIT,
                kinds.isRaw(1) ? shadowRawSamplerFor(1) : shadowHardwareSamplerFor(1));
    }

    // Binds colortex4..7 (gaux1..4, aux units 7..10) for the gbuffer phase; MakeUp reads gaux4 in gbuffers_terrain as the fog colour, and a stale unit 7 blew the horizon out and dragged auto-exposure down. Units 0..3 (atlas, lightmap, PBR) are left alone, custom overrides use their own high units, re-asserted on every phase switch
    private void bindGbufferColorSamplers() {
        bindGbufferColorSamplers(this.activeGbufferSamplerFlips);
    }

    // colortex samplers for a gbuffer program, using the feedback copies where needed
    private void bindGbufferColorSamplers(BitSet samplerFlips) {
        for (int i = 4; i < UmbraRenderTargets.MAX_COLOR_BUFFERS; i++) {
            if (this.renderTargets.get(i) == null) {
                continue;
            }
            UmbraRenderTarget target = this.renderTargets.get(i);
            int texture = samplerFlips.get(i) ? target.getAltTexture() : target.getMainTexture();
            int unit = GBUFFER_COLOR_TEXTURE_UNITS[i];
            if (unit < 0) {
                continue;
            }
            if (unit < GlTextureUnits.CACHED_UNITS) {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager.bindTexture(texture);
            } else {
                GlTextureUnits.selectScratch(unit);
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            }
        }
        // See bindColorSamplers: a bare setActiveTexture here can be swallowed after the raw branch.
        GlTextureUnits.resetToUnit0();
    }

    // Copies targets a gbuffer program both reads and writes, since that is undefined otherwise
    // Only for targets the BOUND program both writes (drawBuffers) and declares a sampler for: the copy is a full-resolution glCopyTexSubImage2D, and it used to run for every gaux target of every phase switch, terrain program and eye/glint bracket whether or not the program ever read it, which on a pack that writes colortex6 from every gbuffer program was dozens of full-screen copies a frame
    private BitSet prepareGbufferFeedbackSamplers(int[] drawBuffers) {
        BitSet samplerFlips = null;
        BitSet sampled = sampledGauxTargets();
        for (int logicalIndex : drawBuffers) {
            if (!isGbufferFeedbackSampler(logicalIndex) || this.renderTargets.get(logicalIndex) == null) {
                continue;
            }
            if (sampled != null && !sampled.get(logicalIndex)) {
                continue;
            }
            copyGbufferFrontToBack(logicalIndex);
            if (samplerFlips == null) {
                samplerFlips = (BitSet) this.activeGbufferSamplerFlips.clone();
            }
            samplerFlips.flip(logicalIndex);
        }
        return samplerFlips == null ? this.activeGbufferSamplerFlips : samplerFlips;
    }

    // Which of colortex4..7 (by either name) the program bound right now samples; null when no program is bound, which keeps the old copy-everything behaviour for that caller. Program ids are per pipeline, and the map dies with it
    private BitSet sampledGauxTargets() {
        int program = LWJGL.glGetInteger(GL_CURRENT_PROGRAM);
        if (program == 0) {
            return null;
        }
        BitSet sampled = this.gauxSamplersByProgram.get(program);
        if (sampled == null) {
            sampled = new BitSet();
            for (int i = 4; i <= 7; i++) {
                if (LWJGL.glGetUniformLocation(program, "colortex" + i) != -1
                        || LWJGL.glGetUniformLocation(program, LEGACY_COLOR_TARGETS[i]) != -1) {
                    sampled.set(i);
                }
            }
            this.gauxSamplersByProgram.put(program, sampled);
        }
        return sampled;
    }

    // Targets packs commonly read back during the gbuffer stage
    private static boolean isGbufferFeedbackSampler(int logicalIndex) {
        return logicalIndex >= 0
                && logicalIndex < GBUFFER_COLOR_TEXTURE_UNITS.length
                && GBUFFER_COLOR_TEXTURE_UNITS[logicalIndex] >= 0;
    }

    // Blit so the program reads a stable copy
    private void copyGbufferFrontToBack(int logicalIndex) {
        if (this.gbufferFeedbackCopyFramebuffer == null) {
            this.gbufferFeedbackCopyFramebuffer = new UmbraFramebuffer();
        }
        int source = frontTexture(this.activeGbufferSamplerFlips, logicalIndex);
        int destination = backTexture(this.activeGbufferSamplerFlips, logicalIndex);
        this.gbufferFeedbackCopyFramebuffer.addColorAttachment(0, 0, source);
        this.gbufferFeedbackCopyFramebuffer.bindAsReadBuffer();
        LWJGL.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int previousTexture = bindScratchTexture2D(destination);
        try {
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    this.renderTargets.getWidth(), this.renderTargets.getHeight());
        } finally {
            restoreScratchTexture2D(previousTexture);
        }
    }

    // Returns to unit 0 with the block atlas, as vanilla expects
    private void restoreTextureUnits() {
        this.customTextureManager.unbindAll();
        this.customImageManager.unbindAll();
        for (int unit = DEPTH_TEX_0_UNIT; unit <= DEPTH_TEX_2_UNIT; unit++) {
            LWJGL.glBindSampler(unit, 0);
            bindTextureUnit(unit, 0);
        }
        for (int unit : new int[]{SHADOW_COLOR_0_UNIT, SHADOW_COLOR_1_UNIT, SHADOW_TEX_0_UNIT, SHADOW_TEX_1_UNIT,
                SHADOW_TEX_0_HW_UNIT, SHADOW_TEX_1_HW_UNIT, NOISE_TEX_UNIT, GBUFFER_DEPTH_TEX_0_UNIT,
                GBUFFER_DEPTH_TEX_1_UNIT, GBUFFER_SHADOW_COLOR_0_UNIT, GBUFFER_SHADOW_COLOR_1_UNIT,
                GBUFFER_SHADOW_TEX_0_UNIT, GBUFFER_SHADOW_TEX_1_UNIT, GBUFFER_NOISE_TEX_UNIT,
                DH_DEPTH_TEX_0_UNIT, DH_DEPTH_TEX_1_UNIT}) {
            LWJGL.glBindSampler(unit, 0);
            bindTextureUnit(unit, 0);
        }
        // colortex8..15 sit beyond GlStateManager's 8-slot cache, so unbind those raw (indexing the cache there throws); units 0..7 go through GlStateManager
        for (int i = UmbraRenderTargets.MAX_COLOR_BUFFERS - 1; i >= 8; i--) {
            LWJGL.glBindSampler(i, 0);
            GlTextureUnits.selectScratch(i);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        // Force GlStateManager's active-unit cache to agree with real GL before touching units 0..7 by stepping through unit 1 first; a bare glActiveTexture desyncs the cache, after which the screenshot path's cached bind lands on the wrong unit and bindFramebufferTexture()'s identical cached bind no-ops forever, a white screen until reload (see MIPMAP_SCRATCH_UNIT; raw binds stay off units 0..7)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        for (int i = 7; i >= 0; i--) {
            LWJGL.glBindSampler(i, 0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // Custom textures and images the pack declared
    private void bindShaderPackResources() {
        if (this.destroyed) {
            return;
        }
        if (this.customImageManager != null && !this.customImageManager.isEmpty()) {
            LWJGL.glMemoryBarrier(SHADER_PACK_RESOURCE_BARRIERS);
        }
        if (this.customTextureManager != null) {
            this.customTextureManager.bindAll();
        }
        if (this.customImageManager != null) {
            this.customImageManager.bindAll();
        }
    }

    // Image bindings for the current program
    public void bindCustomImages() {
        bindShaderPackResources();
    }

    // ------------------------------------------------------------------ teardown

    // Frees all GL resources. Must run on the render thread. Safe to call more than once.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        this.worldRenderingActive = false;
        this.centerDepthSampler.destroy();
        this.colorSpaceConverter.destroy();
        if (this.shaderStorageBuffers != null) {
            this.shaderStorageBuffers.destroy();
            this.shaderStorageBuffers = null;
        }
        // Umbra sweeps the same registry on teardown: a holder replaced without destroy() strands hundreds of megabytes of VRAM per reload
        com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder.forceDeleteBuffers();
        com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.clear();
        com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride.destroyShadowPrograms();
        if (this.shadowRenderer != null) {
            this.shadowRenderer.destroy();
        }
        if (this.gbufferPrograms != null) {
            this.gbufferPrograms.destroy();
        }
        activeGbufferSamplerUnits = GBUFFER_SAMPLER_UNITS;
        activeGbufferSamplerOverrides = java.util.Collections.emptyMap();
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setBlockStateIds(null);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setBlockRenderLayers(null);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setItemIds(null);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setEntityIds(null);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setVoxelRenderDistanceChunks(0);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setDynamicHandLight(true);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setSeparateAo(false);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setOldLighting(false);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setOldHandLight(true);
        com.bdmajora.impetus.umbra.material.WorldRenderingSettings.setVoxelizeLightBlocks(false);
        // Every compute program is owned by the family pass it is attached to.
        destroyFamilyComputes(this.setupPasses);
        destroyFamilyComputes(this.beginPasses);
        destroyFamilyComputes(this.shadowCompPasses);
        destroyFamilyComputes(this.preparePasses);
        destroyFamilyComputes(this.deferredPasses);
        destroyFamilyComputes(this.passes);
        if (this.customTextureManager != null) {
            this.customTextureManager.destroy();
        }
        if (this.customImageManager != null) {
            this.customImageManager.destroy();
        }
        // Programs/uniforms are cached by name — destroy them once here, not per-pass.
        for (UmbraProgram program : this.compiledPrograms.values()) {
            if (program != null) {
                program.destroy();
            }
        }
        this.compiledPrograms.clear();
        this.compiledUniforms.clear();
        this.compiledShadowSamplerKinds.clear();
        this.setupPasses.clear();
        this.beginPasses.clear();
        this.shadowCompPasses.clear();
        this.preparePasses.clear();
        this.deferredPasses.clear();
        this.passes.clear();
        if (this.blitSourceFramebuffer != null) {
            this.blitSourceFramebuffer.destroy();
            this.blitSourceFramebuffer = null;
        }
        for (SwapPass swap : this.swapPasses) {
            swap.from.destroy();
        }
        this.swapPasses.clear();
        if (this.dhCompat != null) {
            this.dhCompat.clearPipeline();
            this.dhCompat = null;
        }
        if (this.translucentGbufferFramebuffer != null && this.translucentGbufferFramebuffer != this.gbufferFramebuffer) {
            this.translucentGbufferFramebuffer.destroy();
        }
        this.translucentGbufferFramebuffer = null;
        this.gauxSamplersByProgram.clear();
        if (this.gbufferFeedbackCopyFramebuffer != null) {
            this.gbufferFeedbackCopyFramebuffer.destroy();
            this.gbufferFeedbackCopyFramebuffer = null;
        }
        if (this.gbufferFramebuffer != null) {
            this.gbufferFramebuffer.destroy();
            this.gbufferFramebuffer = null;
        }
        if (this.quadRenderer != null) {
            this.quadRenderer.destroy();
        }
        if (this.noiseTexture != null) {
            this.noiseTexture.destroy();
        }
        if (this.defaultNormals != null) {
            this.defaultNormals.destroy();
        }
        if (this.defaultSpecular != null) {
            this.defaultSpecular.destroy();
        }
        if (this.noOverlayTexture != null) {
            this.noOverlayTexture.destroy();
        }
        if (this.stubShadowMap != null) {
            this.stubShadowMap.destroy();
        }
        LWJGL.glDeleteSamplers(this.shadowLinearHwSampler);
        LWJGL.glDeleteSamplers(this.shadowNearestHwSampler);
        LWJGL.glDeleteSamplers(this.shadowMippedLinearHwSampler);
        LWJGL.glDeleteSamplers(this.shadowMippedNearestHwSampler);
        LWJGL.glDeleteSamplers(this.shadowLinearRawSampler);
        LWJGL.glDeleteSamplers(this.shadowNearestRawSampler);
        LWJGL.glDeleteSamplers(this.shadowMippedLinearRawSampler);
        LWJGL.glDeleteSamplers(this.shadowMippedNearestRawSampler);
        this.renderTargets.destroy();
    }
}
