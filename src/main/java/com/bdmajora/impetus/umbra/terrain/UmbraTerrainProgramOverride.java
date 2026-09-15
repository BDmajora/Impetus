package com.bdmajora.impetus.umbra.terrain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderOptions;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Builds the pack's gbuffers_terrain and gbuffers_water on Impetus's vertex format, null on failure so the engine's default takes over; not cached here since ShaderChunkRenderer caches per options and deletes on reload
public final class UmbraTerrainProgramOverride {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/UmbraTerrain");

    private UmbraTerrainProgramOverride() {
    }

    // Whether a pack is loaded and its pipeline is live
    public static boolean areShadersActive() {
        return Umbra.isShaderPackInUse();
    }

    // The pack's shadow programs per options variant, cached here unlike the gbuffer overrides because they are OURS to free (handed out during the shadow pass, never stored in the engine's map, destroyed at pipeline teardown); a failed build caches null so it is retried once, not per frame
    private static final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> SHADOW_PROGRAMS = new HashMap<>();

    // The transformed terrain or water program for these options; null falls back to the engine's default
    public static GlProgram<ChunkShaderInterface> getProgramOverride(ChunkShaderOptions options) {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return null;
        }
        // One program per chunk pass as OptiFine (programs 12/9/8) and Umbra (TERRAIN_TRANSLUCENT/CUTOUT/SOLID) split them: translucent is gbuffers_water, solid needs no discard (what gbuffers_terrain_solid lets a pack compile away); all fall back to gbuffers_terrain, TerrainCutoutMip also to TerrainCutout so both conventions resolve
        ProgramId programId;
        if (options.pass().isReverseOrder()) {
            programId = ProgramId.Water;
        } else if (options.pass().supportsFragmentDiscard()) {
            programId = ProgramId.TerrainCutoutMip;
        } else {
            programId = ProgramId.TerrainSolid;
        }
        return build(pack, options, programId);
    }

    // The pack's shadow terrain program for the shadow-map pass, or null meaning nothing is drawn; split by chunk pass like Iris's ShadowWater/Cutout/Solid and OptiFine's shadow_solid/shadow_cutout, since shadow_solid tells the compiler to drop the alpha test, and every variant falls back to plain `shadow`
    public static GlProgram<ChunkShaderInterface> getShadowProgramOverride(ChunkShaderOptions options) {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return null;
        }
        if (SHADOW_PROGRAMS.containsKey(options)) {
            return SHADOW_PROGRAMS.get(options);
        }
        ProgramId programId;
        if (options.pass().isReverseOrder()) {
            programId = ProgramId.ShadowWater;
        } else if (options.pass().supportsFragmentDiscard()) {
            programId = ProgramId.ShadowCutout;
        } else {
            programId = ProgramId.ShadowSolid;
        }
        GlProgram<ChunkShaderInterface> program = build(pack, options, programId);
        SHADOW_PROGRAMS.put(options, program);
        return program;
    }

    // Frees the cached shadow programs; pipeline teardown only, on the render thread, since an off-thread GL delete has no context and leaks
    public static void destroyShadowPrograms() {
        for (GlProgram<ChunkShaderInterface> program : SHADOW_PROGRAMS.values()) {
            if (program != null) {
                program.delete();
            }
        }
        SHADOW_PROGRAMS.clear();
    }

    // Transforms and compiles one pack program; any failure is logged and yields null
    private static GlProgram<ChunkShaderInterface> build(ShaderPack pack, ChunkShaderOptions options, ProgramId programId) {
        GlShader vertexShader = null;
        GlShader fragmentShader = null;
        try {
            Optional<ProgramSource> sourceOpt = pack.getProgramSet().get(programId);
            if (!sourceOpt.isPresent()) {
                LOGGER.warn("[Umbra] no {} source in pack", programId.getSourceName());
                return null;
            }
            ProgramSource source = sourceOpt.get();
            if (!pack.getProperties().getProgramEnabled(source.getName()).orElse(Boolean.TRUE)) {
                return null;
            }
            String vshSource = source.getVertexSource().orElse(null);
            String fshSource = source.getFragmentSource().orElse(null);
            if (vshSource == null || fshSource == null) {
                return null;
            }
            // Raw texture.gbuffers.<sampler> directives: type-checked rename to the minted customtexN sampler.
            vshSource = com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer.transform(
                    source.getName(), vshSource,
                    com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW);
            fshSource = com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer.transform(
                    source.getName(), fshSource,
                    com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW);
            // Modern (1.17+) attribute/matrix names -> fixed-function built-ins.
            vshSource = VanillaNameTransformer.transform(vshSource);
            fshSource = VanillaNameTransformer.transform(fshSource);

            // Modern (#version 130+) dual-stage packs (Complementary) use the compatibility stage normalizer; the GLSL-120 Chocapic family (LIGHT) keeps the full rewrite
            boolean modern = ModernPackTransformer.isModernSource(fshSource);
            // Scoped per program: a program in `impetus.umbra.legacyPrograms` compiles without IS_IRIS, and the same map feeds parseActive and injectDefines so the DRAWBUFFERS layout and the compiled branch agree (mismatching corrupted colortex1 on water)
            Map<String, String> macros = com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(
                    pack.getEnvironmentDefines(), programId.getSourceName());
            int[] drawBuffers = UmbraRenderingPipeline.sanitizeDrawBuffers(
                    programId.getSourceName(), DrawBuffers.parseActive(fshSource, macros));
            // The GLSL-120 terrain path needs the same MC_*/IS_IRIS macro environment as the other paths, or gbuffers_water compiles its pre-Umbra branch writing gl_FragData[2] against a DRAWBUFFERS:41 layout and corrupts colortex1; the macros inject after the 330 rewrite, so the legacy conditional fold runs at the END where it sees what the driver sees (Pastel's malformed `#if (in(biome, ...)` otherwise failed the water pass to the Impetus default)
            String vsh = modern
                    ? ImpetusTerrainTransformer.transformVertexShaderModern(
                            UmbraRenderingPipeline.stabilizeShaderSource(programId.getSourceName(),
                                    com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(vshSource, macros)))
                    : UmbraRenderingPipeline.foldUncompilableConditionals(programId.getSourceName(),
                            com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(
                                    ImpetusTerrainTransformer.transformVertexShader(vshSource), macros));
            // Umbra SodiumPrograms exactly: the pack's alphaTest.<program> wins, else TRANSLUCENT -> NON_ZERO_ALPHA, CUTOUT/SHADOW_CUTOUT -> HALF_ALPHA, else ALWAYS, which emits no discard at all
            String programName = programId.getSourceName();
            ProgramAlphaTest packAlphaTest = ProgramAlphaTest.from(pack.getProperties(), source.getName());
            String alphaTestSnippet;
            if (packAlphaTest.hasDirectives()) {
                alphaTestSnippet = packAlphaTest.toGlslDiscard("iris_FragData[0].a", "    ");
            } else if (programName.endsWith("_solid")) {
                alphaTestSnippet = "";                                    // AlphaTest.ALWAYS
            } else if (programName.contains("cutout")) {
                alphaTestSnippet = ProgramAlphaTest.glslDiscard(          // AlphaTests.HALF_ALPHA
                        "iris_FragData[0].a", ">", "0.5", "    ");
            } else {
                alphaTestSnippet = ProgramAlphaTest.glslDiscard(          // AlphaTests.NON_ZERO_ALPHA
                        "iris_FragData[0].a", ">", "0.0001", "    ");
            }
            String fsh = modern
                    ? ImpetusTerrainTransformer.transformFragmentShaderModern(
                            UmbraRenderingPipeline.stabilizeShaderSource(programId.getSourceName(),
                                    com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(fshSource, macros)),
                            drawBuffers, alphaTestSnippet)
                    : UmbraRenderingPipeline.foldUncompilableConditionals(programId.getSourceName(),
                            com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(
                                    ImpetusTerrainTransformer.transformFragmentShader(fshSource, drawBuffers,
                                            alphaTestSnippet), macros));
            // Name the shader after the program it actually is: this builds every terrain-family pass, and the old hardcoded "iris_gbuffers_terrain" reported water-pass failures as gbuffers_terrain errors beside logs saying gbuffers_terrain built fine; the dump filenames already use getSourceName()
            String shaderName = "iris_" + programId.getSourceName();
            // This path builds the ENGINE's GlShader, not umbra.gl.shader.GlShader, so it does not inherit the strict-driver rewrites and must ask for them; skipping this is why Mesa kept rejecting `#extension` mid-shader and texture2D(usampler2D) in exactly the terrain and shadow programs
            vsh = com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor
                    .finalizeForDriver(shaderName + ".vsh", vsh);
            fsh = com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor
                    .finalizeForDriver(shaderName + ".fsh", fsh);
            vertexShader = new GlShader(ShaderType.VERTEX, shaderName + ".vsh", vsh);
            fragmentShader = new GlShader(ShaderType.FRAGMENT, shaderName + ".fsh", fsh);

            var builder = GlProgram.builder("impetus:iris_terrain");
            builder.attachShader(vertexShader);
            builder.attachShader(fragmentShader);
            int index = 0;
            for (var attribute : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attribute.getName(), index++);
            }
            ProgramBlendState blendState = ProgramBlendState.from(pack.getProperties(), source.getName());
            ProgramAlphaTest alphaTest = packAlphaTest;
            UmbraRenderingPipeline.drainGlError();
            GlProgram<ChunkShaderInterface> program =
                    builder.link(context -> new UmbraTerrainShaderInterface(context, drawBuffers, blendState, alphaTest));
            UmbraRenderingPipeline.reportGlError("terrain '" + programId.getSourceName() + "' link");

            // The pack program needs the full OptiFine uniform set (LIGHT round-trips positions through gbufferModelView(Inverse), and zeros collapse every vertex to the origin); sampler units get the standard mapping, the block/lightmap samplers stay with the interface
            program.bind();
            UmbraRenderingPipeline.assignSamplerUnitsToBoundProgram(program.handle());
            UmbraRenderingPipeline.reportGlError("terrain '" + programId.getSourceName() + "' sampler-units");
            program.unbind();
            ProgramUniforms.Builder uniforms = ProgramUniforms.builder(programId.getSourceName(), program.handle());
            CommonUniforms.addCommonUniforms(uniforms);
            MatrixUniforms.addMatrixUniforms(uniforms);
            com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.assignTo(uniforms);
            ((UmbraTerrainShaderInterface) program.getInterface()).setUniforms(uniforms.buildUniforms());
            // Read off the linked program like the uniforms: gbuffers_terrain/water may sample shadowtex0 as a plain sampler2D, and the bind path then needs the raw-depth sampler on that unit
            ((UmbraTerrainShaderInterface) program.getInterface())
                    .setShadowSamplerKinds(ShadowSamplerKinds.detect(program.handle()));
            UmbraRenderingPipeline.reportGlError("terrain '" + programId.getSourceName() + "' uniforms");

            return program;
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to build terrain override; using Impetus default", e);
            return null;
        } finally {
            if (vertexShader != null) {
                vertexShader.delete();
            }
            if (fragmentShader != null) {
                fragmentShader.delete();
            }
        }
    }
}
