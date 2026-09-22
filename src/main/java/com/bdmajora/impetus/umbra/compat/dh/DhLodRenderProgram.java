package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import org.joml.Matrix4fc;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A pack's dh_terrain, dh_water or dh_shadow program built on DH's LOD vertex format (Iris's IrisLodRenderProgram): DH keeps its own VAO and draw calls, this program is bound over DH's from the DhApiBeforeRenderPassEvent/DhApiBeforeBufferRenderEvent handlers, so its attribute locations must be DH's (0 vPosition, 1 colour, 2 extra) and it takes DH's per-pass matrices and per-buffer model offset the way DH's own shader would
public final class DhLodRenderProgram extends DhRenderProgram {
    private final String name;
    private final ShadowSamplerKinds shadowSamplerKinds;
    private final boolean translucent;
    // Per-pass and per-buffer uniforms of the generated prologue
    private final int uModelOffset;
    private final int uWorldYOffset;
    private final int uMircoOffset;
    private final int uClipDistance;

    private DhLodRenderProgram(GlProgram program, String name, int[] drawBuffers, ProgramBlendState blendState,
                               ProgramUniforms uniforms, ShadowSamplerKinds shadowSamplerKinds, boolean translucent) {
        super(program, drawBuffers, blendState, uniforms);
        this.name = name;
        this.shadowSamplerKinds = shadowSamplerKinds;
        this.translucent = translucent;
        this.uModelOffset = program.getUniformLocation("modelOffset");
        this.uWorldYOffset = program.getUniformLocation("worldYOffset");
        this.uMircoOffset = program.getUniformLocation("mircoOffset");
        this.uClipDistance = program.getUniformLocation("clipDistance");
    }

    // Transforms and links one DH program on DH's VAO layout (GlDhTerrainShaderProgram): uvec4 position+meta, normalized colour, uvec4 material/normal/tile
    static DhLodRenderProgram create(UmbraRenderingPipeline pipeline, ShaderPack pack, ProgramSource source,
                                     boolean shadowPass, boolean translucent) {
        Stage stage = transform(pack, source, false);
        String label = "dh_" + stage.name;
        GlProgram program = link(label, stage,
                builder -> builder
                        .bindAttributeLocation(0, "vPosition")
                        .bindAttributeLocation(1, "iris_color")
                        .bindAttributeLocation(2, "irisExtra"),
                DhLodRenderProgram::assignFixedSamplers);
        return new DhLodRenderProgram(program, stage.name, stage.drawBuffers,
                ProgramBlendState.from(pack.getProperties(), stage.name), buildUniforms(label, stage.name, program),
                ShadowSamplerKinds.detect(program.getGlId()), translucent);
    }

    // The samplers with a fixed unit for a LOD draw: DH's block atlas (and the block sampler aliases, which DH programs never read) on unit 0, the lightmap on unit 1 like every gbuffer program; set once, fillUniformData binds the textures
    private static void assignFixedSamplers(GlProgram program) {
        assignSamplerUnit(program, 0, "dhBlockAtlas", "texture", "gtexture", "tex", "gcolor");
        assignSamplerUnit(program, 1, "lightmap", "texLightmap");
    }

    public ShadowSamplerKinds getShadowSamplerKinds() {
        return this.shadowSamplerKinds;
    }

    public boolean isTranslucent() {
        return this.translucent;
    }

    public String getName() {
        return this.name;
    }

    public void bind() {
        this.program.bind();
    }

    public void unbind() {
        LWJGL.glUseProgram(0);
    }

    public void free() {
        this.program.destroy();
    }

    // Per pass: DH's matrices for this pass (the LOD projection or the shadow matrices), the lightmap and atlas on the units the samplers point at, DH's near clip, and the pack's whole uniform set
    public void fillUniformData(Matrix4fc projection, Matrix4fc modelView, int worldYOffset, float partialTicks) {
        this.program.bind();

        // DH binds these the other way round (lightmap 0, atlas 1) in its own setup, and its atlas bind is raw, so a cached bind here would be skipped against a stale cache: force both
        GlTextureUnits.forceBindTexture2D(1, CustomTextureManager.getLightmapTextureId());
        int atlas = DhCompatInternal.getBlockAtlasTextureId();
        if (atlas > 0) {
            GlTextureUnits.forceBindTexture2D(0, atlas);
        }

        uploadMatrices(projection, modelView);
        // DH's own values: a 0.01 block micro offset separating coplanar LOD faces, and the near clip distance its fragment fade reads
        setUniform(this.uMircoOffset, 0.01f);
        setUniform(this.uWorldYOffset, (float) worldYOffset);
        if (this.uClipDistance != -1 && DhApi.Delayed.renderProxy != null) {
            setUniform(this.uClipDistance, DhApi.Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(partialTicks));
        }

        this.uniforms.update();
    }

    // Per LOD buffer: the buffer's corner relative to the camera, which the prologue adds to every vertex
    public void setModelPos(DhApiVec3f modelPos) {
        if (this.uModelOffset != -1) {
            LWJGL.glUniform3f(this.uModelOffset, modelPos.x, modelPos.y, modelPos.z);
        }
    }
}
