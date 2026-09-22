package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A pack's dh_generic (falling back to dh_terrain) program for DH's generic objects, the instanced boxes behind its beacon beams and clouds (Iris's IrisGenericRenderProgram): registered with DH as the IDhApiGenericObjectShaderProgram override, so DH calls back here to bind and fill uniforms while keeping its own instance buffers and draw calls. DH's instanced attribute layout is fixed (0 position, 1 colour, 2 scale, 3 chunk translation, 4 sub-chunk translation, 5 material), and only that path is supported: DH's non-instanced fallback (Macs, GPUs without vertexAttribDivisor) has per-box uniforms this program does not carry, the same limit Iris has
public final class DhGenericRenderProgram extends DhRenderProgram implements IDhApiGenericObjectShaderProgram {
    // DH's own VAO would carry its default program's layout; ours keeps the generic pointers DH sets in bindVertexBuffer and the instanced ones it sets per group
    private final int vertexArray;
    private final int uOffsetChunk;
    private final int uOffsetSubChunk;
    private final int uCameraPosChunk;
    private final int uCameraPosSubChunk;
    private final int uBlockLight;
    private final int uSkyLight;
    // DH hands its matrices over as row-major arrays each pass; converted into these rather than fresh objects
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();

    private DhGenericRenderProgram(GlProgram program, int[] drawBuffers, ProgramBlendState blendState,
                                   ProgramUniforms uniforms) {
        super(program, drawBuffers, blendState, uniforms);
        this.vertexArray = LWJGL.glGenVertexArrays();
        this.uOffsetChunk = program.getUniformLocation("uOffsetChunk");
        this.uOffsetSubChunk = program.getUniformLocation("uOffsetSubChunk");
        this.uCameraPosChunk = program.getUniformLocation("uCameraPosChunk");
        this.uCameraPosSubChunk = program.getUniformLocation("uCameraPosSubChunk");
        this.uBlockLight = program.getUniformLocation("uBlockLight");
        this.uSkyLight = program.getUniformLocation("uSkyLight");
    }

    // Same build as DhLodRenderProgram with the generic bridge; the attribute locations are DH's GlGenericObjectRenderer's
    static DhGenericRenderProgram create(UmbraRenderingPipeline pipeline, ShaderPack pack, ProgramSource source) {
        Stage stage = transform(pack, source, true);
        String label = "dh_" + stage.name + "_generic";
        GlProgram program = link(label, stage,
                builder -> builder
                        .bindAttributeLocation(0, "vPosition")
                        .bindAttributeLocation(1, "iris_color")
                        .bindAttributeLocation(2, "aScale")
                        .bindAttributeLocation(3, "aTranslateChunk")
                        .bindAttributeLocation(4, "aTranslateSubChunk")
                        .bindAttributeLocation(5, "aMaterial"),
                linked -> assignSamplerUnit(linked, 1, "lightmap", "texLightmap"));
        return new DhGenericRenderProgram(program, stage.drawBuffers,
                ProgramBlendState.from(pack.getProperties(), stage.name), buildUniforms(label, stage.name, program));
    }

    @Override
    public boolean overrideThisFrame() {
        return Umbra.getRenderingPipeline() != null;
    }

    @Override
    public int getId() {
        return this.program.getGlId();
    }

    @Override
    public void free() {
        LWJGL.glDeleteVertexArrays(this.vertexArray);
        this.program.destroy();
    }

    // DH calls this once per generic pass with the pass's matrices, then bindVertexBuffer and one fillIndirectUniformData per box group
    @Override
    public void bind(DhApiRenderParam renderParam) {
        LWJGL.glBindVertexArray(this.vertexArray);
        this.program.bind();
        uploadMatrices(toJoml(renderParam.dhProjectionMatrix, this.projection), toJoml(renderParam.dhModelViewMatrix, this.modelView));
        this.uniforms.update();
    }

    @Override
    public void unbind() {
        LWJGL.glBindVertexArray(0);
        LWJGL.glUseProgram(0);
    }

    // The shared unit-box vertex buffer at attribute 0, as DH's own program lays it out
    @Override
    public void bindVertexBuffer(int vbo) {
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        LWJGL.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        LWJGL.glEnableVertexAttribArray(0);
    }

    // Per box group: where the group's instances sit relative to the camera, split into chunk and sub-chunk parts for precision far from the origin, plus its light levels
    @Override
    public void fillIndirectUniformData(DhApiRenderParam renderParam, DhApiRenderableBoxGroupShading shading,
                                        IDhApiRenderableBoxGroup boxGroup, DhApiVec3d camPos) {
        bind(renderParam);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        DhApiVec3d origin = boxGroup.getOriginBlockPos();
        setUniform(this.uOffsetChunk, chunkPos(origin.x), chunkPos(origin.y), chunkPos(origin.z));
        setUniform(this.uOffsetSubChunk, subChunkPos(origin.x), subChunkPos(origin.y), subChunkPos(origin.z));
        setUniform(this.uCameraPosChunk, chunkPos(camPos.x), chunkPos(camPos.y), chunkPos(camPos.z));
        setUniform(this.uCameraPosSubChunk, subChunkPos(camPos.x), subChunkPos(camPos.y), subChunkPos(camPos.z));
        if (this.uBlockLight != -1) {
            LWJGL.glUniform1i(this.uBlockLight, boxGroup.getBlockLight());
        }
        if (this.uSkyLight != -1) {
            LWJGL.glUniform1i(this.uSkyLight, boxGroup.getSkyLight());
        }
    }

    @Override
    public void fillSharedDirectUniformData(DhApiRenderParam renderParam, DhApiRenderableBoxGroupShading shading,
                                            IDhApiRenderableBoxGroup boxGroup, DhApiVec3d camPos) {
        throw new IllegalStateException("Only instanced generic rendering is supported with shaders active.");
    }

    @Override
    public void fillDirectUniformData(DhApiRenderParam renderParam, IDhApiRenderableBoxGroup boxGroup,
                                      DhApiRenderableBox box, DhApiVec3d camPos) {
        throw new IllegalStateException("Only instanced generic rendering is supported with shaders active.");
    }

    private static int chunkPos(double value) {
        return (int) Math.floor(value / 16);
    }

    private static float subChunkPos(double value) {
        return (float) (value - Math.floor(value / 16) * 16);
    }

    // DH's matrices are row-major arrays, so a transposed set yields JOML's column-major form
    private static Matrix4f toJoml(DhApiMat4f matrix, Matrix4f into) {
        return into.setTransposed(matrix.getValuesAsArray());
    }

    private void setUniform(int location, int x, int y, int z) {
        if (location != -1) {
            LWJGL.glUniform3i(location, x, y, z);
        }
    }

    private void setUniform(int location, float x, float y, float z) {
        if (location != -1) {
            LWJGL.glUniform3f(location, x, y, z);
        }
    }
}
