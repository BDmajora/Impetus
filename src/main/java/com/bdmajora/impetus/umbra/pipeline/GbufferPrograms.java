package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.pipeline.rendering.GbufferTargets;
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
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Gbuffer programs of a pack by phase with programs shared between phases that use one source.
public class GbufferPrograms {
    // Logger of compile failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Phases that draw with a gbuffer program.
    private static final ProgramId[] PHASES = {
            ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.Entities, ProgramId.EntitiesTrans,
            ProgramId.SpiderEyes, ProgramId.DamagedBlock,
            ProgramId.Particles, ProgramId.Weather, ProgramId.Clouds, ProgramId.Hand,
            ProgramId.HandWater, ProgramId.Line, ProgramId.ArmorGlint
    };

    // Compiled gbuffer program with its uniforms and draw state.
    public static final class Entry {
        // Compiled program.
        final UmbraProgram program;
        // Uniforms of the program.
        final ProgramUniforms uniforms;
        // Draw buffers of the program.
        final int[] drawBuffers;
        // Blend override of the program.
        final ProgramBlendState blendState;
        // Alpha test override of the program.
        final ProgramAlphaTest alphaTest;
        // Location of the hand lightmap uniform or minus one.
        final int handLightmapLocation;
        // Compare or raw kind of each shadow sampler.
        final ShadowSamplerKinds shadowSamplerKinds;

        // Creates an entry with a copy of the draw buffers.
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

        // Returns the kind of each shadow sampler.
        public ShadowSamplerKinds getShadowSamplerKinds() {
            return this.shadowSamplerKinds;
        }

        // Returns the compiled program.
        public UmbraProgram getProgram() {
            return this.program;
        }

        // Returns the uniforms of the program.
        public ProgramUniforms getUniforms() {
            return this.uniforms;
        }

        // Returns a copy of the draw buffers.
        public int[] getDrawBuffers() {
            return this.drawBuffers.clone();
        }

        // Returns the draw buffers without a copy.
        public int[] drawBuffersReadOnly() {
            return this.drawBuffers;
        }

        // Returns the blend override.
        public ProgramBlendState getBlendState() {
            return this.blendState;
        }

        // Returns the alpha test override.
        public ProgramAlphaTest getAlphaTest() {
            return this.alphaTest;
        }

        // Uploads the hand lightmap when the program reads it.
        public void setHandLightmap(float blockLight, float skyLight) {
            if (this.handLightmapLocation != -1) {
                LWJGL.glUniform2f(this.handLightmapLocation, blockLight, skyLight);
            }
        }
    }

    // Programs by phase.
    private final Map<ProgramId, Entry> byPhase = new EnumMap<>(ProgramId.class);
    // Programs to delete.
    private final List<Entry> ownedEntries = new ArrayList<>();
    // Phases with their own program.
    private final Set<ProgramId> directPhases = EnumSet.noneOf(ProgramId.class);

    // Compiles the gbuffer program of every enabled phase once per source.
    public GbufferPrograms(ShaderPack pack, Map<String, Integer> samplerUnits, Map<String, Integer> samplerOverrides) {
        Map<String, String> defines = pack.getEnvironmentDefines();
        Map<String, Integer> gbufferSamplers = new HashMap<>(samplerUnits);
        gbufferSamplers.put("texture", 0);
        gbufferSamplers.put("gtexture", 0);
        gbufferSamplers.put("lightmap", 1);
        gbufferSamplers.put("normals", 2);
        gbufferSamplers.put("specular", 3);
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
                entry = bySourceName.get(sourceName);
            } else {
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

    // Compiles a program without blend or alpha test overrides.
    static Entry compile(ProgramSource source, Map<String, String> defines, Map<String, Integer> samplerUnits) {
        return compile(source, defines, samplerUnits, ProgramBlendState.empty(), ProgramAlphaTest.empty());
    }

    // Compiles a program and assigns its samplers and uniforms or returns null and deletes it after a failure.
    static Entry compile(ProgramSource source, Map<String, String> defines, Map<String, Integer> samplerUnits,
                         ProgramBlendState blendState, ProgramAlphaTest alphaTest) {
        UmbraProgram program = null;
        try {
            program = ShaderProgramCompiler.compile(source.getName(), source, defines);

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
            ActiveCustomUniforms.assignTo(builder);
            int[] drawBuffers = GbufferTargets.sanitize(program.getDrawBuffers());
            return new Entry(program, builder.buildUniforms(), drawBuffers, blendState, alphaTest, handLightmapLocation,
                    ShadowSamplerKinds.detect(glProgram.getGlId()));
        } catch (Exception e) {
            // Deletes a program that compiled before a later step failed
            if (program != null) {
                try {
                    program.destroy();
                } catch (RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            LOGGER.error("[Umbra] Failed to compile gbuffer program '{}'; its phases render vanilla style: {}",
                    source.getName(), e.getMessage());
            return null;
        }
    }

    // Returns the program of a phase or null.
    public Entry get(ProgramId phase) {
        return this.byPhase.get(phase);
    }

    // Returns whether a phase has its own program.
    public boolean hasDirect(ProgramId phase) {
        return this.directPhases.contains(phase);
    }

    // Returns the programs to delete.
    public List<Entry> entries() {
        return this.ownedEntries;
    }

    // Deletes every program and forgets the phases.
    public void destroy() {
        for (Entry entry : this.ownedEntries) {
            entry.program.destroy();
        }
        this.ownedEntries.clear();
        this.byPhase.clear();
    }
}
