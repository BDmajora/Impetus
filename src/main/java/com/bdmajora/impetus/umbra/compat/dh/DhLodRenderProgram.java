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

// Distant Horizons program of LOD terrain with a shader pack.
public final class DhLodRenderProgram extends DhRenderProgram {
    // Name of the program.
    private final String name;
    // Compare or raw kind of each shadow sampler.
    private final ShadowSamplerKinds shadowSamplerKinds;
    // Translucent state of the program.
    private final boolean translucent;
    // Location of the model offset.
    private final int uModelOffset;
    // Location of the world height offset.
    private final int uWorldYOffset;
    // Location of the micro offset.
    private final int uMircoOffset;
    // Location of the near clip distance.
    private final int uClipDistance;

    // Creates a program with its uniform locations.
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

    // Transforms and links the LOD program of a pack and deletes it when its setup fails.
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
        // Deletes the program when its uniforms or sampler kinds cannot be read
        try {
            return new DhLodRenderProgram(program, stage.name, stage.drawBuffers,
                    ProgramBlendState.from(pack.getProperties(), stage.name), buildUniforms(label, stage.name, program),
                    ShadowSamplerKinds.detect(program.getGlId()), translucent);
        } catch (RuntimeException | Error e) {
            program.destroy();
            throw e;
        }
    }

    // Points the atlas and lightmap samplers at their units.
    private static void assignFixedSamplers(GlProgram program) {
        assignSamplerUnit(program, 0, "dhBlockAtlas", "texture", "gtexture", "tex", "gcolor");
        assignSamplerUnit(program, 1, "lightmap", "texLightmap");
    }

    // Returns the kind of each shadow sampler.
    public ShadowSamplerKinds getShadowSamplerKinds() {
        return this.shadowSamplerKinds;
    }

    // Returns whether the program draws translucent terrain.
    public boolean isTranslucent() {
        return this.translucent;
    }

    // Returns the name of the program.
    public String getName() {
        return this.name;
    }

    // Binds the program.
    public void bind() {
        this.program.bind();
    }

    // Unbinds every program.
    public void unbind() {
        LWJGL.glUseProgram(0);
    }

    // Deletes the program.
    public void free() {
        this.program.destroy();
    }

    // Binds the program and its textures and uploads the matrices and uniforms of a frame.
    public void fillUniformData(Matrix4fc projection, Matrix4fc modelView, int worldYOffset, float partialTicks) {
        this.program.bind();

        GlTextureUnits.forceBindTexture2D(1, CustomTextureManager.getLightmapTextureId());
        int atlas = DhCompatInternal.getBlockAtlasTextureId();
        if (atlas > 0) {
            GlTextureUnits.forceBindTexture2D(0, atlas);
        }

        uploadMatrices(projection, modelView);
        setUniform(this.uMircoOffset, 0.01f);
        setUniform(this.uWorldYOffset, (float) worldYOffset);
        if (this.uClipDistance != -1 && DhApi.Delayed.renderProxy != null) {
            setUniform(this.uClipDistance, DhApi.Delayed.renderProxy.getNearClipPlaneDistanceInBlocks(partialTicks));
        }

        this.uniforms.update();
    }

    // Uploads the offset of the drawn model.
    public void setModelPos(DhApiVec3f modelPos) {
        if (this.uModelOffset != -1) {
            LWJGL.glUniform3f(this.uModelOffset, modelPos.x, modelPos.y, modelPos.z);
        }
    }
}
