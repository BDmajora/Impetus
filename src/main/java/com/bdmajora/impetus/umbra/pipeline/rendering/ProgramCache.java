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

// Fullscreen programs (begin/prepare/deferred/composite/final/shadowcomp) compiled once with their uniforms and cached by source name; a failure is cached as absent so it is never retried, and this cache owns the GL programs
public final class ProgramCache {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // A linked program with everything a pass needs from it
    public record Compiled(UmbraProgram program, ProgramUniforms uniforms, ShadowSamplerKinds shadowSamplerKinds,
                           boolean modern) {
    }

    private final Map<String, Compiled> compiled = new HashMap<>();
    private final Map<String, String> defines;
    private final Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides;
    // Colour targets an earlier pass already wrote; mutated while the schedule is built, read at each compile
    private final BitSet flippedAtLeastOnce;

    public ProgramCache(Map<String, String> defines,
                        Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides,
                        BitSet flippedAtLeastOnce) {
        this.defines = defines;
        this.overrides = overrides;
        this.flippedAtLeastOnce = flippedAtLeastOnce;
    }

    // The compiled program, or null when it cannot be built; a throw is cached as null too, then rethrown for the caller to report
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

    // The custom-texture stage a fullscreen program belongs to, from its name: beginN, prepareN, deferredN, shadowcompN, else composite (final included)
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

    private Compiled compile(ProgramSource source, TextureStage stage) {
        String name = source.getName();
        String vshRaw = source.getVertexSource().orElse(null);
        String fshRaw = source.getFragmentSource().orElse(null);
        if (vshRaw == null || fshRaw == null) {
            LOGGER.warn("[Umbra] Program '{}' is missing a vertex or fragment stage; skipping", name);
            return null;
        }
        // texture.<stage>.<sampler> redirects first, so later transforms and the DRAWBUFFERS parse see the final identifiers; then modern (1.17+) names onto fixed-function built-ins, drawn under the quad's ortho
        vshRaw = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, vshRaw, stage));
        fshRaw = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, fshRaw, stage));
        GlShader vertex = null;
        GlShader fragment = null;
        GlProgram program = null;
        try {
            // Modern single-source packs (Complementary) take the compatibility normalizer, the GLSL-120 Chocapic family (LIGHT) the full 330-core rewrite; detected off the fragment source
            boolean modern = ModernPackTransformer.isModernSource(fshRaw);
            // Scoped per pass: a pass in impetus.umbra.legacyPrograms compiles without IS_IRIS, and one map drives both the DRAWBUFFERS parse and the injected prologue
            Map<String, String> macros = ShaderMacros.forProgram(this.defines, name);
            int[] drawBuffers = DrawBuffers.sanitize(DrawBuffers.parseActive(fshRaw, macros), UmbraRenderTargets.MAX_COLOR_BUFFERS);
            String vsh;
            String fsh;
            if (modern) {
                // Modern sources rely on the driver preprocessor for their #if trees, so the MC_*/IRIS_FEATURE_* environment must be present; gl_MultiTexCoord0 is fed from a real attribute rather than NVIDIA's aliasing
                vsh = ModernPackTransformer.bindFullscreenTexCoord(ModernPackTransformer.transform(
                        ShaderSourceFixes.stabilize(name, ShaderMacros.injectDefines(vshRaw, macros))));
                fsh = DrawBuffers.rewriteFragmentOutputs(ModernPackTransformer.transform(
                        ShaderSourceFixes.stabilize(name, ShaderMacros.injectDefines(fshRaw, macros))), drawBuffers);
            } else {
                // The legacy branch needs the macro environment too, or `#if MC_VERSION < 10800` takes the pre-1.8 path (Pastel's `MC_RENDER_QUALITY * 0.0625` failed); injected before the transform so the defines follow its `#version 330 core`
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
                    // Modern (compatibility-profile) sources read the quad's texcoord through this, since gl_MultiTexCoord0 cannot be fed portably via generic attribute 8
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT, ModernPackTransformer.FULLSCREEN_TEXCOORD_ATTRIBUTE);
            if (!modern) {
                // The 330-core rewrite writes an explicit out array whose indices are the dense draw-buffer slots, matching Umbra's packed attachments
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
            // A program that linked but could not be set up would otherwise leak until the context dies
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

    // Registers every uniform the pipeline can supply against one program
    static ProgramUniforms uniformsFor(String name, int programId) {
        ProgramUniforms.Builder builder = ProgramUniforms.builder(name, programId);
        CommonUniforms.addCommonUniforms(builder);
        MatrixUniforms.addMatrixUniforms(builder);
        ActiveCustomUniforms.assignTo(builder);
        return builder.buildUniforms();
    }

    // Frees every compiled program once, however many passes used it
    public void destroy() {
        for (Compiled entry : this.compiled.values()) {
            if (entry != null) {
                entry.program().destroy();
            }
        }
        this.compiled.clear();
    }
}
