package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.FullscreenQuadRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.terrain.FullscreenTransformer;
import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.terrain.VanillaNameTransformer;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Cache of the linked programs of a pack.
public final class ProgramCache {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Data of the compiled.
    public record Compiled(UmbraProgram program, ProgramUniforms uniforms, ShadowSamplerKinds shadowSamplerKinds,
                           boolean modern) {
    }

    private final Map<String, Compiled> compiled = new HashMap<>();
    private final Map<String, String> defines;
    private final Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides;
    // Render targets flipped at least once.
    private final BitSet flippedAtLeastOnce;

    // Creates a program cache from its defines.
    public ProgramCache(Map<String, String> defines,
                        Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides,
                        BitSet flippedAtLeastOnce) {
        this.defines = defines;
        this.overrides = overrides;
        this.flippedAtLeastOnce = flippedAtLeastOnce;
    }

    // Returns the value.
    public Compiled get(ProgramSource source) {
        String name = source.getName();
        if (this.compiled.containsKey(name)) {
            return this.compiled.get(name);
        }
        Compiled result = null;
        try {
            result = compile(source, stageOf(name));
        } finally {
            this.compiled.put(name, result);
        }
        return result;
    }

    // Stages the of.
    static TextureStage stageOf(String name) {
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

    // Compiles a program for a texture stage.
    private Compiled compile(ProgramSource source, TextureStage stage) {
        String name = source.getName();
        String vshRaw = source.getVertexSource().orElse(null);
        String fshRaw = source.getFragmentSource().orElse(null);
        if (vshRaw == null || fshRaw == null) {
            LOGGER.warn("[Umbra] Program '{}' is missing a vertex or fragment stage; skipping", name);
            return null;
        }
        vshRaw = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, vshRaw, stage));
        fshRaw = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, fshRaw, stage));
        GlShader vertex = null;
        GlShader fragment = null;
        GlProgram program = null;
        try {
            boolean modern = ModernPackTransformer.isModernSource(fshRaw);
            Map<String, String> macros = ShaderMacros.forProgram(this.defines, name);
            int[] drawBuffers = DrawBuffers.sanitize(DrawBuffers.parseActive(fshRaw, macros), UmbraRenderTargets.MAX_COLOR_BUFFERS);
            String vsh;
            String fsh;
            if (modern) {
                vsh = ModernPackTransformer.bindFullscreenTexCoord(ModernPackTransformer.transform(
                        ShaderSourceFixes.stabilize(name, ShaderMacros.injectDefines(vshRaw, macros))));
                fsh = DrawBuffers.rewriteFragmentOutputs(ModernPackTransformer.transform(
                        ShaderSourceFixes.stabilize(name, ShaderMacros.injectDefines(fshRaw, macros))), drawBuffers);
            } else {
                vsh = FullscreenTransformer.transformVertexShader(
                        ShaderSourceFixes.foldUncompilableConditionals(ShaderMacros.injectDefines(vshRaw, macros)));
                fsh = FullscreenTransformer.transformFragmentShader(
                        ShaderSourceFixes.foldUncompilableConditionals(ShaderMacros.injectDefines(fshRaw, macros)), drawBuffers);
            }
            vertex = new GlShader(ShaderType.VERTEX, name + ".vsh", vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fsh);
            ProgramBuilder builder = ProgramBuilder.begin(name)
                    .attach(vertex)
                    .attach(fragment)
                    .bindAttributeLocation(FullscreenQuadRenderer.POSITION_SLOT, "a_Position")
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT, "a_TexCoord")
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT, ModernPackTransformer.FULLSCREEN_TEXCOORD_ATTRIBUTE);
            if (!modern) {
                builder.bindFragmentDataLocation(0, "iris_FragData");
            }
            program = builder.link();
            program.bind();
            SamplerAssignment.assign(program.getGlId(), SamplerLayout.forStage(stage), this.overrides.apply(stage),
                    this.flippedAtLeastOnce);
            program.unbind();
            return new Compiled(new UmbraProgram(program, drawBuffers), uniformsFor(name, program.getGlId()),
                    ShadowSamplerKinds.detect(program.getGlId()), modern);
        } catch (RuntimeException e) {
            if (program != null) {
                program.destroy();
            }
            throw e;
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    // Returns the uniforms of a linked program.
    static ProgramUniforms uniformsFor(String name, int programId) {
        ProgramUniforms.Builder builder = ProgramUniforms.builder(name, programId);
        CommonUniforms.addCommonUniforms(builder);
        MatrixUniforms.addMatrixUniforms(builder);
        ActiveCustomUniforms.assignTo(builder);
        return builder.buildUniforms();
    }

    // Deletes every cached program.
    public void destroy() {
        for (Compiled entry : this.compiled.values()) {
            if (entry != null) {
                entry.program().destroy();
            }
        }
        this.compiled.clear();
    }
}
