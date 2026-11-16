package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.umbra.pipeline.rendering.GbufferTargets;
import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
import com.bdmajora.impetus.umbra.pipeline.rendering.SamplerAssignment;
import com.bdmajora.impetus.umbra.pipeline.rendering.ShaderSourceFixes;
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
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Builds the terrain and shadow terrain programs of a shader pack for the chunk renderer.
public final class UmbraTerrainProgramOverride {
    // Logger of build failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/UmbraTerrain");

    // Prevents instantiation
    private UmbraTerrainProgramOverride() {
    }

    // Returns whether a shader pack is in use.
    public static boolean areShadersActive() {
        return Umbra.isShaderPackInUse();
    }

    // Shadow programs by options with null for a failed build.
    private static final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> SHADOW_PROGRAMS = new HashMap<>();

    // Builds the terrain program of a pass or returns null without a pack.
    public static GlProgram<ChunkShaderInterface> getProgramOverride(ChunkShaderOptions options) {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return null;
        }
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

    // Returns the cached shadow program of a pass and builds it once.
    public static GlProgram<ChunkShaderInterface> getShadowProgramOverride(ChunkShaderOptions options) {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return null;
        }
        GlProgram<ChunkShaderInterface> cached = SHADOW_PROGRAMS.get(options);
        if (cached != null || SHADOW_PROGRAMS.containsKey(options)) {
            return cached;
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

    // Deletes every shadow program and continues after a failure.
    public static void destroyShadowPrograms() {
        RuntimeException failure = null;
        for (GlProgram<ChunkShaderInterface> program : SHADOW_PROGRAMS.values()) {
            if (program == null) {
                continue;
            }
            try {
                program.delete();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else if (e != failure) {
                    failure.addSuppressed(e);
                }
            }
        }
        SHADOW_PROGRAMS.clear();
        if (failure != null) {
            throw failure;
        }
    }

    // Builds and links a terrain program and returns null and deletes it after a failure.
    private static GlProgram<ChunkShaderInterface> build(ShaderPack pack, ChunkShaderOptions options, ProgramId programId) {
        GlProgram<ChunkShaderInterface> program = null;
        try {
            PreparedProgram prepared = prepare(pack, programId);
            if (prepared == null) {
                return null;
            }
            String vsh = prepared.vertex();
            String fsh = prepared.fragment(prepared.alphaTestSnippet());
            String shaderName = prepared.shaderName();

            Map<ShaderType, String> auxiliary = prepared.modern() ? auxiliaryStages(prepared.source(), prepared.macros(), shaderName) : Map.of();
            if (!auxiliary.isEmpty()) {
                try {
                    program = link(shaderName, ImpetusTerrainTransformer.blockGeneratedVaryings(vsh, true),
                            ImpetusTerrainTransformer.blockGeneratedVaryings(fsh, false), auxiliary, options, prepared.drawBuffers(),
                            prepared.blendState(), prepared.alphaTest(), auxiliary.containsKey(ShaderType.TESS_EVALUATE));
                } catch (RuntimeException e) {
                    LOGGER.warn("[Umbra] '{}' failed to link with its geometry or tessellation stages; drawing without them: {}",
                            programId.getSourceName(), e.getMessage());
                }
            }
            if (program == null) {
                program = link(shaderName, vsh, fsh, Map.of(), options, prepared.drawBuffers(), prepared.blendState(), prepared.alphaTest(), false);
            }
            GlState.reportGlError("terrain '" + programId.getSourceName() + "' link");

            configureUniforms(program, programId.getSourceName());
            return program;
        } catch (Exception e) {
            // Deletes a linked program whose uniform setup failed
            if (program != null) {
                try {
                    program.delete();
                } catch (RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            LOGGER.error("[Umbra] Failed to build terrain override; using Impetus default", e);
            return null;
        }
    }

    // Transforms the stages of a terrain program or returns null when the pack has none.
    static PreparedProgram prepare(ShaderPack pack, ProgramId programId) {
        ProgramSource source = resolve(pack, programId);
        if (source == null || source.getVertexSource().isEmpty() || source.getFragmentSource().isEmpty()) {
            return null;
        }
        String vshSource = VanillaNameTransformer.transform(customTextures(source, source.getVertexSource().get()));
        String fshSource = VanillaNameTransformer.transform(customTextures(source, source.getFragmentSource().get()));

        boolean modern = ModernPackTransformer.isModernSource(fshSource);
        Map<String, String> macros = macros(pack, programId);
        boolean shadow = programId.getSourceName().startsWith("shadow");
        String vsh = modern
                ? ImpetusTerrainTransformer.transformVertexShaderModern(
                        ShaderSourceFixes.stabilize(programId.getSourceName(),
                                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(vshSource, macros)), shadow)
                : ShaderSourceFixes.foldUncompilableConditionals(com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(
                                ImpetusTerrainTransformer.transformVertexShader(vshSource, shadow), macros));
        return prepared(pack, programId, source, vsh, fshSource, modern, macros, false);
    }

    // Transforms the mesh stages of a ported terrain program and rejects an unported one.
    static PreparedProgram prepareMesh(ShaderPack pack, ProgramId programId) {
        ProgramSource source = resolve(pack, programId);
        if (source == null) {
            return null;
        }
        if (!source.hasMeshStages()) {
            throw new com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.UnsupportedProgramException(
                    source.getName() + " has no Nvidium port; port the pack from the shader pack screen");
        }
        String mesh = customTextures(source, source.getMeshSource().get());
        String fshSource = customTextures(source, source.getMeshFragmentSource().get());
        boolean modern = ModernPackTransformer.isModernSource(fshSource);
        Map<String, String> macros = macros(pack, programId);
        String vertex = ShaderSourceFixes.stabilize(programId.getSourceName(),
                com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(mesh, macros));
        return prepared(pack, programId, source, vertex, fshSource, modern, macros, true);
    }

    // Returns the enabled source of a program or null.
    private static ProgramSource resolve(ShaderPack pack, ProgramId programId) {
        Optional<ProgramSource> sourceOpt = pack.getProgramSet().get(programId);
        if (sourceOpt.isEmpty()) {
            LOGGER.warn("[Umbra] no {} source in pack", programId.getSourceName());
            return null;
        }
        ProgramSource source = sourceOpt.get();
        return pack.getProperties().getProgramEnabled(source.getName()).orElse(Boolean.TRUE) ? source : null;
    }

    // Applies the custom texture renames of the gbuffer stage.
    private static String customTextures(ProgramSource source, String stage) {
        return com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer.transform(
                source.getName(), stage, com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW);
    }

    // Returns the macros of a program.
    private static Map<String, String> macros(ShaderPack pack, ProgramId programId) {
        return com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.forProgram(pack.getEnvironmentDefines(), programId.getSourceName());
    }

    // Collects the transformed stages and draw state of a program.
    private static PreparedProgram prepared(ShaderPack pack, ProgramId programId, ProgramSource source, String vertex, String fshSource,
                                            boolean modern, Map<String, String> macros, boolean mesh) {
        String programName = programId.getSourceName();
        int[] drawBuffers = GbufferTargets.sanitize(DrawBuffers.parseActive(fshSource, macros));
        ProgramAlphaTest packAlphaTest = ProgramAlphaTest.from(pack.getProperties(), source.getName());
        String alphaTestSnippet;
        if (packAlphaTest.hasDirectives()) {
            alphaTestSnippet = packAlphaTest.toGlslDiscard("iris_FragData[0].a", "    ");
        } else if (programName.endsWith("_solid")) {
            alphaTestSnippet = "";
        } else if (programName.contains("cutout")) {
            alphaTestSnippet = ProgramAlphaTest.glslDiscard(
                    "iris_FragData[0].a", ">", "0.5", "    ");
        } else {
            alphaTestSnippet = ProgramAlphaTest.glslDiscard(
                    "iris_FragData[0].a", ">", "0.0001", "    ");
        }
        String shaderName = "iris_" + programName;
        ProgramBlendState blendState = ProgramBlendState.from(pack.getProperties(), source.getName());
        return new PreparedProgram(programId, source, shaderName, vertex, fshSource, modern, macros, drawBuffers, blendState,
                packAlphaTest, alphaTestSnippet, mesh);
    }

    // Transformed sources and draw state of a terrain program.
    record PreparedProgram(ProgramId id, ProgramSource source, String shaderName, String vertex, String fragmentSource, boolean modern,
                           Map<String, String> macros, int[] drawBuffers, ProgramBlendState blendState, ProgramAlphaTest alphaTest,
                           String alphaTestSnippet, boolean mesh) {
        // Returns the fragment stage with an alpha test snippet.
        String fragment(String alphaSnippet) {
            String name = this.id.getSourceName();
            String transformed = this.modern
                    ? ImpetusTerrainTransformer.transformFragmentShaderModern(
                            ShaderSourceFixes.stabilize(name,
                                    com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(this.fragmentSource, this.macros)),
                            this.drawBuffers, alphaSnippet)
                    : ShaderSourceFixes.foldUncompilableConditionals(com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(
                                    ImpetusTerrainTransformer.transformFragmentShader(this.fragmentSource, this.drawBuffers,
                                            alphaSnippet), this.macros));
            return this.mesh ? com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.pinBridgeInputs(transformed) : transformed;
        }

        // Returns whether the source has geometry or tessellation stages.
        boolean hasAuxiliaryStages() {
            return this.source.getGeometrySource().isPresent() || this.source.getTessControlSource().isPresent()
                    || this.source.getTessEvalSource().isPresent();
        }
    }

    // Assigns the samplers and builds the uniforms of a linked program.
    static void configureUniforms(GlProgram<ChunkShaderInterface> program, String programName) {
        program.bind();
        SamplerAssignment.assignToBoundProgram(program.handle());
        GlState.reportGlError("terrain '" + programName + "' sampler units");
        program.unbind();
        ProgramUniforms.Builder uniforms = ProgramUniforms.builder(programName, program.handle());
        CommonUniforms.addCommonUniforms(uniforms);
        MatrixUniforms.addMatrixUniforms(uniforms);
        com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.assignTo(uniforms);
        ((UmbraTerrainShaderInterface) program.getInterface()).setUniforms(uniforms.buildUniforms());
        ((UmbraTerrainShaderInterface) program.getInterface()).setShadowSamplerKinds(ShadowSamplerKinds.detect(program.handle()));
        GlState.reportGlError("terrain '" + programName + "' uniforms");
    }

    // Returns the transformed geometry and tessellation stages of a source.
    static Map<ShaderType, String> auxiliaryStages(ProgramSource source, Map<String, String> macros, String shaderName) {
        Map<ShaderType, String> stages = new java.util.EnumMap<>(ShaderType.class);
        source.getGeometrySource().ifPresent(geometry -> stages.put(ShaderType.GEOM, geometry));
        if (source.getTessControlSource().isPresent() && source.getTessEvalSource().isPresent()) {
            stages.put(ShaderType.TESS_CTRL, source.getTessControlSource().get());
            stages.put(ShaderType.TESS_EVALUATE, source.getTessEvalSource().get());
        }

        stages.replaceAll((type, stage) -> {
            String name = shaderName + "." + type.fileExtension;
            String rewritten = com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer.transform(
                    source.getName(), stage, com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage.GBUFFERS_AND_SHADOW);
            rewritten = VanillaNameTransformer.transform(rewritten);
            rewritten = ShaderSourceFixes.stabilize(source.getName(),
                    com.bdmajora.impetus.umbra.gl.shader.ShaderMacros.injectDefines(rewritten, macros));
            return com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor.finalizeForDriver(name,
                    ImpetusTerrainTransformer.transformAuxiliaryStage(rewritten, type));
        });
        return stages;
    }

    // Compiles and links a program and deletes every shader after linking.
    private static GlProgram<ChunkShaderInterface> link(String shaderName, String vsh, String fsh, Map<ShaderType, String> auxiliary,
                                                        ChunkShaderOptions options, int[] drawBuffers, ProgramBlendState blendState,
                                                        ProgramAlphaTest alphaTest, boolean tessellated) {
        List<GlShader> shaders = new ArrayList<>();
        try {
            shaders.add(new GlShader(ShaderType.VERTEX, shaderName + ".vsh", com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor
                    .finalizeForDriver(shaderName + ".vsh", vsh)));
            shaders.add(new GlShader(ShaderType.FRAGMENT, shaderName + ".fsh", com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor
                    .finalizeForDriver(shaderName + ".fsh", fsh)));
            for (var stage : auxiliary.entrySet()) {
                shaders.add(new GlShader(stage.getKey(), shaderName + "." + stage.getKey().fileExtension, stage.getValue()));
            }

            var builder = GlProgram.builder("impetus:iris_terrain");
            for (GlShader shader : shaders) {
                builder.attachShader(shader);
            }
            int index = 0;
            for (var attribute : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attribute.getName(), index++);
            }
            GlState.drainGlError();
            return builder.link(context -> new UmbraTerrainShaderInterface(context, drawBuffers, blendState, alphaTest, tessellated));
        } finally {
            for (GlShader shader : shaders) {
                shader.delete();
            }
        }
    }
}
