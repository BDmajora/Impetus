package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Compiles every compute stage attached to one program (<name>.csh plus the <name>_a..z.csh Umbra extension); dispatch size follows Umbra's priority: indirect directive, const ivec3 workGroups, const vec2 workGroupsRender, then one invocation per pixel
public final class ComputeBuilder {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final int[] ONE_GROUP = {1, 1, 1};
    private static final float[] FULL_SCREEN = {1.0f, 1.0f};

    private final Map<String, String> defines;
    private final Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides;
    private final BitSet flippedAtLeastOnce;
    private final Map<String, ComputeDirectives.IndirectPointer> indirectPointers;
    private final ShaderStorageBufferHolder storageBuffers;
    // The first 3D custom image's size, for the shadowcomp volume fallback, or null
    private final int[] voxelVolume;

    public ComputeBuilder(Map<String, String> defines,
                          Function<TextureStage, Map<String, CustomTextureManager.Override>> overrides,
                          BitSet flippedAtLeastOnce, Map<String, ComputeDirectives.IndirectPointer> indirectPointers,
                          ShaderStorageBufferHolder storageBuffers, int[] voxelVolume) {
        this.defines = defines;
        this.overrides = overrides;
        this.flippedAtLeastOnce = flippedAtLeastOnce;
        this.indirectPointers = indirectPointers;
        this.storageBuffers = storageBuffers;
        this.voxelVolume = voxelVolume;
    }

    // Every linked compute stage of the program, in variant order; one that fails is logged and skipped
    public List<ComputePass> build(ProgramSource source, TextureStage stage) {
        String[] sources = source.getComputeSources();
        if (sources.length == 0) {
            return Collections.emptyList();
        }
        // The voxel-volume fallback is a shadowcomp-only workaround for Complementary's under-declared floodfill groups; Umbra never second-guesses a declared workGroups, and Photon's deferred4_a.csh declares (1,1,1) for a reduction that scaling would run 4096 times over
        int[] volume = stage == TextureStage.SHADOWCOMP ? this.voxelVolume : null;
        List<ComputePass> built = new ArrayList<>();
        for (int variant = 0; variant < sources.length; variant++) {
            if (sources[variant] == null) {
                continue;
            }
            String name = ProgramSource.computeVariantName(source.getName(), variant);
            try {
                built.add(compile(name, sources[variant], stage, volume));
            } catch (Exception e) {
                LOGGER.error("[Umbra] Failed to build compute pass '{}'; it will be skipped: {}", name, e.getMessage());
            }
        }
        return built;
    }

    private ComputePass compile(String name, String raw, TextureStage stage, int[] volume) {
        String csh = ShaderSourceFixes.stabilize(name, ShaderMacros.injectDefines(
                CustomTextureTransformer.transform(name, raw, stage), this.defines));
        ComputeDirectives directives = ComputeDirectives.parse(csh, this.defines);
        int[] localSize = directives.localSize();
        int[] workGroups = directives.workGroups();
        float[] renderScale = directives.renderScale();
        int[] volumeGroups = volume != null && localSize != null ? new int[]{
                ComputeDirectives.ceilDiv(volume[0], localSize[0]),
                ComputeDirectives.ceilDiv(volume[1], localSize[1]),
                ComputeDirectives.ceilDiv(volume[2], localSize[2])} : null;
        int indirectBuffer = -1;
        long indirectOffset = 0L;
        ComputeDirectives.IndirectPointer pointer = this.indirectPointers.get(name);
        if (pointer != null) {
            indirectBuffer = this.storageBuffers.getBufferId(pointer.buffer());
            indirectOffset = pointer.offset();
            if (indirectBuffer == -1) {
                LOGGER.warn("[Umbra] Compute pass '{}' requests indirect dispatch from undeclared bufferObject.{}",
                        name, pointer.buffer());
            }
        }
        if (workGroups != null && volumeGroups != null && !ComputeDirectives.coversVolume(workGroups, localSize, volume)) {
            LOGGER.warn("[Umbra] Compute pass '{}' declared dispatch {}x{}x{} does not cover custom image volume {}x{}x{} with local {}x{}x{}; using {}x{}x{}",
                    name, workGroups[0], workGroups[1], workGroups[2], volume[0], volume[1], volume[2],
                    localSize[0], localSize[1], localSize[2], volumeGroups[0], volumeGroups[1], volumeGroups[2]);
            workGroups = volumeGroups;
        }
        if (workGroups == null) {
            if (volumeGroups != null) {
                workGroups = volumeGroups;
            } else {
                // Umbra ComputeProgram.getWorkGroups' last resort: cover the screen, one invocation per pixel
                if (renderScale == null && indirectBuffer == -1) {
                    renderScale = FULL_SCREEN;
                }
                workGroups = ONE_GROUP;
            }
        }
        GlShader shader = new GlShader(ShaderType.COMPUTE, name + ".csh", csh);
        GlProgram program;
        try {
            program = ProgramBuilder.begin(name).attach(shader).link();
        } finally {
            shader.destroy();
        }
        try {
            program.bind();
            SamplerAssignment.assign(program.getGlId(), SamplerLayout.fullscreen(), this.overrides.apply(stage),
                    this.flippedAtLeastOnce);
            program.unbind();
            return new ComputePass(program, ProgramCache.uniformsFor(name, program.getGlId()),
                    ShadowSamplerKinds.detect(program.getGlId()), workGroups, renderScale, localSize,
                    indirectBuffer, indirectOffset);
        } catch (RuntimeException e) {
            // Linked but unusable: free it rather than leak it for the rest of the session
            program.destroy();
            throw e;
        }
    }
}
