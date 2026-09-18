package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL15;
import com.bdmajora.impetus.lwjgl.MemoryStack;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.terrain.DhTerrainTransformer;
import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.terrain.VanillaNameTransformer;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A pack's dh_generic (falling back to dh_terrain) program for DH's generic objects, the instanced boxes behind its beacon beams and clouds (Iris's IrisGenericRenderProgram): registered with DH as the IDhApiGenericObjectShaderProgram override, so DH calls back here to bind and fill uniforms while keeping its own instance buffers and draw calls. DH's instanced attribute layout is fixed (0 position, 1 colour, 2 scale, 3 chunk translation, 4 sub-chunk translation, 5 material), and only that path is supported: DH's non-instanced fallback (Macs, GPUs without vertexAttribDivisor) has per-box uniforms this program does not carry, the same limit Iris has
public final class DhGenericRenderProgram implements IDhApiGenericObjectShaderProgram {
    private final GlProgram program;
    private final int[] drawBuffers;
    private final ProgramBlendState blendState;
    private final ProgramUniforms uniforms;
    // DH's own VAO would carry its default program's layout; ours keeps the generic pointers DH sets in bindVertexBuffer and the instanced ones it sets per group
    private final int vertexArray;
    private final int uModelView;
    private final int uModelViewInverse;
    private final int uProjection;
    private final int uProjectionInverse;
    private final int uNormalMatrix;
    private final int uOffsetChunk;
    private final int uOffsetSubChunk;
    private final int uCameraPosChunk;
    private final int uCameraPosSubChunk;
    private final int uBlockLight;
    private final int uSkyLight;

    private DhGenericRenderProgram(GlProgram program, int[] drawBuffers, ProgramBlendState blendState,
                                   ProgramUniforms uniforms) {
        this.program = program;
        this.drawBuffers = drawBuffers;
        this.blendState = blendState;
        this.uniforms = uniforms;
        this.vertexArray = LWJGL.glGenVertexArrays();
        this.uModelView = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW);
        this.uModelViewInverse = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW_INVERSE);
        this.uProjection = program.getUniformLocation(DhTerrainTransformer.PROJECTION);
        this.uProjectionInverse = program.getUniformLocation(DhTerrainTransformer.PROJECTION_INVERSE);
        this.uNormalMatrix = program.getUniformLocation(DhTerrainTransformer.NORMAL_MATRIX);
        this.uOffsetChunk = program.getUniformLocation("uOffsetChunk");
        this.uOffsetSubChunk = program.getUniformLocation("uOffsetSubChunk");
        this.uCameraPosChunk = program.getUniformLocation("uCameraPosChunk");
        this.uCameraPosSubChunk = program.getUniformLocation("uCameraPosSubChunk");
        this.uBlockLight = program.getUniformLocation("uBlockLight");
        this.uSkyLight = program.getUniformLocation("uSkyLight");
    }

    // Same build as DhLodRenderProgram with the generic bridge; the attribute locations are DH's GlGenericObjectRenderer's
    static DhGenericRenderProgram create(UmbraRenderingPipeline pipeline, ShaderPack pack, ProgramSource source) {
        String name = source.getName();
        String vshSource = source.getVertexSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no vertex stage"));
        String fshSource = source.getFragmentSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no fragment stage"));
        vshSource = CustomTextureTransformer.transform(name, vshSource, TextureStage.GBUFFERS_AND_SHADOW);
        fshSource = CustomTextureTransformer.transform(name, fshSource, TextureStage.GBUFFERS_AND_SHADOW);
        vshSource = VanillaNameTransformer.transform(vshSource);
        fshSource = VanillaNameTransformer.transform(fshSource);

        Map<String, String> macros = ShaderMacros.forProgram(pack.getEnvironmentDefines(), name);
        int[] drawBuffers = UmbraRenderingPipeline.sanitizeDrawBuffers(name, DrawBuffers.parseActive(fshSource, macros));
        ProgramAlphaTest packAlphaTest = ProgramAlphaTest.from(pack.getProperties(), name);
        String alphaTestSnippet = packAlphaTest.hasDirectives()
                ? packAlphaTest.toGlslDiscard("iris_FragData[0].a", "    ") : "";
        boolean modern = ModernPackTransformer.isModernSource(fshSource);
        String vsh;
        String fsh;
        if (modern) {
            vsh = DhTerrainTransformer.transformVertexShader(UmbraRenderingPipeline.stabilizeShaderSource(name,
                    ShaderMacros.injectDefines(vshSource, macros)), true);
            fsh = DhTerrainTransformer.transformFragmentShader(UmbraRenderingPipeline.stabilizeShaderSource(name,
                    ShaderMacros.injectDefines(fshSource, macros)), drawBuffers, alphaTestSnippet, true);
        } else {
            vsh = UmbraRenderingPipeline.foldUncompilableConditionals(name, ShaderMacros.injectDefines(
                    DhTerrainTransformer.transformVertexShader(vshSource, true), macros));
            fsh = UmbraRenderingPipeline.foldUncompilableConditionals(name, ShaderMacros.injectDefines(
                    DhTerrainTransformer.transformFragmentShader(fshSource, drawBuffers, alphaTestSnippet, true), macros));
        }

        GlShader vertex = null;
        GlShader fragment = null;
        try {
            vertex = new GlShader(ShaderType.VERTEX, "dh_" + name + "_generic.vsh", vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, "dh_" + name + "_generic.fsh", fsh);
            UmbraRenderingPipeline.drainGlError();
            GlProgram program = ProgramBuilder.begin("dh_" + name + "_generic")
                    .attach(vertex)
                    .attach(fragment)
                    .bindAttributeLocation(0, "vPosition")
                    .bindAttributeLocation(1, "iris_color")
                    .bindAttributeLocation(2, "aScale")
                    .bindAttributeLocation(3, "aTranslateChunk")
                    .bindAttributeLocation(4, "aTranslateSubChunk")
                    .bindAttributeLocation(5, "aMaterial")
                    .bindFragmentDataLocation(0, "iris_FragData")
                    .link();
            UmbraRenderingPipeline.reportGlError("dh generic '" + name + "' link");

            program.bind();
            UmbraRenderingPipeline.assignSamplerUnitsToBoundProgram(program.getGlId());
            for (String lightmapName : new String[]{"lightmap", "texLightmap"}) {
                int location = program.getUniformLocation(lightmapName);
                if (location != -1) {
                    LWJGL.glUniform1i(location, 1);
                }
            }
            program.unbind();
            ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program.getGlId());
            CommonUniforms.addCommonUniforms(uniforms);
            MatrixUniforms.addMatrixUniforms(uniforms);
            ActiveCustomUniforms.assignTo(uniforms);
            UmbraRenderingPipeline.reportGlError("dh generic '" + name + "' uniforms");
            return new DhGenericRenderProgram(program, drawBuffers,
                    ProgramBlendState.from(pack.getProperties(), name), uniforms.buildUniforms());
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    public int[] getDrawBuffers() {
        return this.drawBuffers;
    }

    public ProgramBlendState getBlendState() {
        return this.blendState;
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
        Matrix4f modelView = toJoml(renderParam.dhModelViewMatrix);
        Matrix4f projection = toJoml(renderParam.dhProjectionMatrix);
        Matrix4f modelViewInverse = new Matrix4f(modelView).invert();
        setUniform(this.uModelView, modelView);
        setUniform(this.uModelViewInverse, modelViewInverse);
        setUniform(this.uProjection, projection);
        setUniform(this.uProjectionInverse, new Matrix4f(projection).invert());
        setUniform(this.uNormalMatrix, modelViewInverse.transpose3x3(new Matrix3f()));
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
    private static Matrix4f toJoml(DhApiMat4f matrix) {
        return new Matrix4f().setTransposed(matrix.getValuesAsArray());
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

    private static void setUniform(int location, Matrix4fc matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = LWJGL.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            LWJGL.glUniformMatrix4fv(location, false, buffer);
        }
    }

    private static void setUniform(int location, Matrix3f matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = LWJGL.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            matrix.get(buffer);
            LWJGL.glUniformMatrix3fv(location, false, buffer);
        }
    }
}
