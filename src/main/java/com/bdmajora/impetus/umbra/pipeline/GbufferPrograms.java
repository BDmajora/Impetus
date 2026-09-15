package com.bdmajora.impetus.umbra.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.ShaderProgramCompiler;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The pack's fixed-function gbuffer programs (sky, entities, damage, particles, weather, clouds, hand), still immediate-mode so gl_Vertex remains the contract; OptiFine's fallback chain is honoured and phases with the same source share one program
public class GbufferPrograms {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // The phases driven from the vanilla render-loop anchors in EntityRendererMixin; this list IS the set of bind points, so a phase absent here is never selected however the pack declares it
    private static final ProgramId[] PHASES = {
            ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.Entities, ProgramId.EntitiesTrans,
            ProgramId.SpiderEyes, ProgramId.DamagedBlock,
            ProgramId.Particles, ProgramId.Weather, ProgramId.Clouds, ProgramId.Hand,
            ProgramId.HandWater, ProgramId.Line, ProgramId.ArmorGlint
    };

    // One compiled gbuffer program with everything needed to bind it: uniform driver, sanitised DRAWBUFFERS mask, alpha-test override
    public static final class Entry {
        final UmbraProgram program;
        final ProgramUniforms uniforms;
        final int[] drawBuffers;
        final ProgramBlendState blendState;
        final ProgramAlphaTest alphaTest;
        final int handLightmapLocation;
        // How the program declared shadowtex0/1, so the bind path can put a raw-depth sampler on a unit it reads through a plain sampler2D
        final ShadowSamplerKinds shadowSamplerKinds;

        Entry(UmbraProgram program, ProgramUniforms uniforms, int[] drawBuffers, ProgramBlendState blendState,
              ProgramAlphaTest alphaTest, int handLightmapLocation, ShadowSamplerKinds shadowSamplerKinds) {
            this.program = program;
            this.uniforms = uniforms;
            this.drawBuffers = drawBuffers == null ? DrawBuffers.DEFAULT.clone() : drawBuffers.clone();
            this.blendState = blendState;
            this.alphaTest = alphaTest;
            this.handLightmapLocation = handLightmapLocation;
            this.shadowSamplerKinds = shadowSamplerKinds;
        }

        // Which shadow depth units this program samples as raw depth rather than a comparison
        public ShadowSamplerKinds getShadowSamplerKinds() {
            return this.shadowSamplerKinds;
        }

        // The linked program
        public UmbraProgram getProgram() {
            return this.program;
        }

        // Its uniform set
        public ProgramUniforms getUniforms() {
            return this.uniforms;
        }

        // Its declared targets
        public int[] getDrawBuffers() {
            return this.drawBuffers.clone();
        }

        // Its blend directives
        public ProgramBlendState getBlendState() {
            return this.blendState;
        }

        // The pack's alphaTest.<program> override, or an empty one when undeclared, never null so the bind path applies and restores unconditionally
        public ProgramAlphaTest getAlphaTest() {
            return this.alphaTest;
        }

        // Overrides the lightmap coordinates for the hand programs
        public void setHandLightmap(float blockLight, float skyLight) {
            if (this.handLightmapLocation != -1) {
                LWJGL.glUniform2f(this.handLightmapLocation, blockLight, skyLight);
            }
        }
    }

    private final Map<ProgramId, Entry> byPhase = new EnumMap<>(ProgramId.class);
    private final List<Entry> ownedEntries = new ArrayList<>();
    // Phases the pack ships an actual file for, as opposed to ones resolved through the fallback chain; matters where a caller needs to know whether the pack MEANT to handle a phase
    private final java.util.Set<ProgramId> directPhases = java.util.EnumSet.noneOf(ProgramId.class);

    // samplerOverrides holds the gbuffers-stage custom-texture units as sampler name -> dedicated unit over the standard table, so `texture.gbuffers.gaux4` redirects one sampler while every other keeps its unit
    GbufferPrograms(ShaderPack pack, Map<String, Integer> samplerUnits, Map<String, Integer> samplerOverrides) {
        Map<String, String> defines = pack.getEnvironmentDefines();
        // Fixed-function stages sample the bound atlas/lightmap on the vanilla units, plus OptiFine's aux slots.
        Map<String, Integer> gbufferSamplers = new HashMap<>(samplerUnits);
        gbufferSamplers.put("texture", 0);
        gbufferSamplers.put("gtexture", 0);
        gbufferSamplers.put("lightmap", 1);
        gbufferSamplers.put("normals", 2);
        gbufferSamplers.put("specular", 3);
        // Custom-texture overrides win over everything, including the vanilla-unit additions above.
        gbufferSamplers.putAll(samplerOverrides);

        Map<String, Entry> bySourceName = new HashMap<>();
        for (ProgramId phase : PHASES) {
            Optional<ProgramSource> direct = pack.getProgramSet().getDirect(phase);
            Optional<ProgramSource> source = phase == ProgramId.EntitiesTrans
                    ? direct
                    : pack.getProgramSet().get(phase);
            if (!source.isPresent()) {
                continue;
            }
            String sourceName = source.get().getName();
            if (!pack.getProperties().getProgramEnabled(sourceName).orElse(Boolean.TRUE)) {
                bySourceName.put(sourceName, null);
                continue;
            }
            Entry entry;
            if (bySourceName.containsKey(sourceName)) {
                entry = bySourceName.get(sourceName); // may be null: a failed compile is not retried
            } else {
                // A ProgramId's default blend mode belongs to its own file; a phase resolved through the fallback chain uses some other program's source with its own directives (Umbra attaches the default at the direct read), and direct-only also means a phase with a default never shares an Entry
                BlendMode defaultBlend = direct.isPresent() ? phase.getDefaultBlendMode() : null;
                entry = compile(source.get(), defines, gbufferSamplers,
                        ProgramBlendState.from(pack.getProperties(), sourceName, defaultBlend),
                        ProgramAlphaTest.from(pack.getProperties(), sourceName));
                bySourceName.put(sourceName, entry);
                if (entry != null) {
                    this.ownedEntries.add(entry);
                }
            }
            if (entry != null) {
                this.byPhase.put(phase, entry);
                if (direct.isPresent()) {
                    this.directPhases.add(phase);
                }
            }
        }
    }

    // Package-visible because UmbraShadowRenderer compiles the fixed-function flavour of the shadow program through the same path, since entity and block-entity shadows go through vanilla's renderers
    static Entry compile(ProgramSource source, Map<String, String> defines, Map<String, Integer> samplerUnits) {
        return compile(source, defines, samplerUnits, ProgramBlendState.empty(), ProgramAlphaTest.empty());
    }

    static Entry compile(ProgramSource source, Map<String, String> defines, Map<String, Integer> samplerUnits,
                         ProgramBlendState blendState, ProgramAlphaTest alphaTest) {
        try {
            UmbraProgram program = ShaderProgramCompiler.compile(source.getName(), source, defines);

            GlProgram glProgram = program.getProgram();
            glProgram.bind();
            for (Map.Entry<String, Integer> sampler : samplerUnits.entrySet()) {
                int location = glProgram.getUniformLocation(sampler.getKey());
                if (location != -1) {
                    LWJGL.glUniform1i(location, sampler.getValue());
                }
            }
            int handLightmapLocation = glProgram.getUniformLocation(ShaderProgramCompiler.HAND_LIGHTMAP_UNIFORM);
            glProgram.unbind();

            ProgramUniforms.Builder builder = ProgramUniforms.builder(source.getName(), glProgram.getGlId());
            CommonUniforms.addCommonUniforms(builder);
            MatrixUniforms.addMatrixUniforms(builder);
            com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms.assignTo(builder);
            int[] drawBuffers = UmbraRenderingPipeline.sanitizeDrawBuffers(source.getName(), program.getDrawBuffers());
            return new Entry(program, builder.buildUniforms(), drawBuffers, blendState, alphaTest, handLightmapLocation,
                    ShadowSamplerKinds.detect(glProgram.getGlId()));
        } catch (Exception e) {
            LOGGER.error("[Umbra] Failed to compile gbuffer program '{}'; its phases render vanilla-style: {}",
                    source.getName(), e.getMessage());
            return null;
        }
    }

    // The compiled program for a phase, or null meaning "let vanilla's fixed-function path draw it", not an error
    public Entry get(ProgramId phase) {
        return this.byPhase.get(phase);
    }

    // Whether the pack ships a file for this phase ITSELF rather than resolving through the fallback chain onto another program's source
    public boolean hasDirect(ProgramId phase) {
        return this.directPhases.contains(phase);
    }

    // Every DISTINCT compiled entry, since several phases share one program and the draw-buffer union and teardown must visit each once
    public List<Entry> entries() {
        return this.ownedEntries;
    }

    // Frees every gbuffer program
    public void destroy() {
        for (Entry entry : this.ownedEntries) {
            entry.program.destroy();
        }
        this.ownedEntries.clear();
        this.byPhase.clear();
    }
}
