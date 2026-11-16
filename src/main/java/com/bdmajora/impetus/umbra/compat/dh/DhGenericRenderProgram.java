package com.bdmajora.impetus.umbra.compat.dh;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
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

// Distant Horizons program of instanced generic boxes with a shader pack.
public final class DhGenericRenderProgram extends DhRenderProgram implements IDhApiGenericObjectShaderProgram {
    // Vertex array of the boxes.
    private final int vertexArray;
    // Location of the chunk part of the group origin.
    private final int uOffsetChunk;
    // Location of the block part of the group origin.
    private final int uOffsetSubChunk;
    // Location of the chunk part of the camera.
    private final int uCameraPosChunk;
    // Location of the block part of the camera.
    private final int uCameraPosSubChunk;
    // Location of the block light.
    private final int uBlockLight;
    // Location of the sky light.
    private final int uSkyLight;
    // Scratch of the model view matrix.
    private final Matrix4f modelView = new Matrix4f();
    // Scratch of the projection matrix.
    private final Matrix4f projection = new Matrix4f();

    // Creates a program with its vertex array and uniform locations.
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

    // Transforms and links the generic program of a pack and deletes it when its setup fails.
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
        // Deletes the program when its uniforms or vertex array cannot be created
        try {
            return new DhGenericRenderProgram(program, stage.drawBuffers,
                    ProgramBlendState.from(pack.getProperties(), stage.name), buildUniforms(label, stage.name, program));
        } catch (RuntimeException | Error e) {
            program.destroy();
            throw e;
        }
    }

    // Returns whether a shader pack is active.
    @Override
    public boolean overrideThisFrame() {
        return Umbra.getRenderingPipeline() != null;
    }

    // Returns the GL name of the program.
    @Override
    public int getId() {
        return this.program.getGlId();
    }

    // Deletes the vertex array and the program.
    @Override
    public void free() {
        try {
            LWJGL.glDeleteVertexArrays(this.vertexArray);
        } finally {
            this.program.destroy();
        }
    }

    // Binds the vertex array and the program and uploads the matrices and uniforms.
    @Override
    public void bind(DhApiRenderParam renderParam) {
        LWJGL.glBindVertexArray(this.vertexArray);
        this.program.bind();
        uploadMatrices(toJoml(renderParam.dhProjectionMatrix, this.projection), toJoml(renderParam.dhModelViewMatrix, this.modelView));
        this.uniforms.update();
    }

    // Unbinds the vertex array and the program.
    @Override
    public void unbind() {
        LWJGL.glBindVertexArray(0);
        LWJGL.glUseProgram(0);
    }

    // Binds a vertex buffer as the position attribute.
    @Override
    public void bindVertexBuffer(int vbo) {
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        LWJGL.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        LWJGL.glEnableVertexAttribArray(0);
    }

    // Uploads the uniforms of an instanced box group.
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

    // Rejects direct rendering.
    @Override
    public void fillSharedDirectUniformData(DhApiRenderParam renderParam, DhApiRenderableBoxGroupShading shading,
                                            IDhApiRenderableBoxGroup boxGroup, DhApiVec3d camPos) {
        throw new IllegalStateException("Only instanced generic rendering is supported with shaders active.");
    }

    // Rejects direct rendering.
    @Override
    public void fillDirectUniformData(DhApiRenderParam renderParam, IDhApiRenderableBoxGroup boxGroup,
                                      DhApiRenderableBox box, DhApiVec3d camPos) {
        throw new IllegalStateException("Only instanced generic rendering is supported with shaders active.");
    }

    // Returns the chunk of a coordinate.
    private static int chunkPos(double value) {
        return (int) Math.floor(value / 16);
    }

    // Returns the offset of a coordinate inside its chunk.
    private static float subChunkPos(double value) {
        return (float) (value - Math.floor(value / 16) * 16);
    }

    // Writes a Distant Horizons matrix into a JOML matrix.
    private static Matrix4f toJoml(DhApiMat4f matrix, Matrix4f into) {
        return into.setTransposed(matrix.getValuesAsArray());
    }

    // Uploads three ints when the location exists.
    private void setUniform(int location, int x, int y, int z) {
        if (location != -1) {
            LWJGL.glUniform3i(location, x, y, z);
        }
    }

    // Uploads three floats when the location exists.
    private void setUniform(int location, float x, float y, float z) {
        if (location != -1) {
            LWJGL.glUniform3f(location, x, y, z);
        }
    }
}
