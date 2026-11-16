package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.umbra.pipeline.rendering.GbufferTargets;
import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
import com.bdmajora.impetus.umbra.pipeline.rendering.SamplerAssignment;
import com.bdmajora.impetus.umbra.pipeline.rendering.ShaderSourceFixes;
import org.lwjgl.system.MemoryStack;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Base of the programs that draw Distant Horizons terrain with a shader pack.
abstract class DhRenderProgram {
    // Linked program.
    protected final GlProgram program;
    // Draw buffers of the program.
    protected final int[] drawBuffers;
    // Blend override of the program.
    protected final ProgramBlendState blendState;
    // Pack uniforms of the program.
    protected final ProgramUniforms uniforms;
    // Location of the model view matrix.
    private final int uModelView;
    // Location of the inverse model view matrix.
    private final int uModelViewInverse;
    // Location of the projection matrix.
    private final int uProjection;
    // Location of the inverse projection matrix.
    private final int uProjectionInverse;
    // Location of the normal matrix.
    private final int uNormalMatrix;
    // Scratch of the inverse model view.
    private final Matrix4f modelViewInverse = new Matrix4f();
    // Scratch of the inverse projection.
    private final Matrix4f projectionInverse = new Matrix4f();
    // Scratch of the normal matrix.
    private final Matrix3f normalMatrix = new Matrix3f();

    // Creates a program and looks up its matrix uniforms.
    protected DhRenderProgram(GlProgram program, int[] drawBuffers, ProgramBlendState blendState, ProgramUniforms uniforms) {
        this.program = program;
        this.drawBuffers = drawBuffers;
        this.blendState = blendState;
        this.uniforms = uniforms;
        this.uModelView = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW);
        this.uModelViewInverse = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW_INVERSE);
        this.uProjection = program.getUniformLocation(DhTerrainTransformer.PROJECTION);
        this.uProjectionInverse = program.getUniformLocation(DhTerrainTransformer.PROJECTION_INVERSE);
        this.uNormalMatrix = program.getUniformLocation(DhTerrainTransformer.NORMAL_MATRIX);
    }

    // Returns the draw buffers of the program.
    public int[] getDrawBuffers() {
        return this.drawBuffers;
    }

    // Returns the blend override of the program.
    public ProgramBlendState getBlendState() {
        return this.blendState;
    }

    // Uploads the matrices and the inverses the program reads.
    protected final void uploadMatrices(Matrix4fc projection, Matrix4fc modelView) {
        setUniform(this.uModelView, modelView);
        setUniform(this.uProjection, projection);
        // Inverts only the matrices the program reads
        if (this.uModelViewInverse != -1 || this.uNormalMatrix != -1) {
            this.modelViewInverse.set(modelView).invert();
            setUniform(this.uModelViewInverse, this.modelViewInverse);
            if (this.uNormalMatrix != -1) {
                setUniform(this.uNormalMatrix, this.modelViewInverse.transpose3x3(this.normalMatrix));
            }
        }
        if (this.uProjectionInverse != -1) {
            setUniform(this.uProjectionInverse, this.projectionInverse.set(projection).invert());
        }
    }

    // Uploads a float when the location exists.
    protected static void setUniform(int location, float value) {
        if (location != -1) {
            LWJGL.glUniform1f(location, value);
        }
    }

    // Uploads a 4 by 4 matrix when the location exists.
    protected static void setUniform(int location, Matrix4fc matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            LWJGL.glUniformMatrix4fv(location, false, buffer);
        }
    }

    // Uploads a 3 by 3 matrix when the location exists.
    protected static void setUniform(int location, Matrix3f matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            matrix.get(buffer);
            LWJGL.glUniformMatrix3fv(location, false, buffer);
        }
    }

    // Points every declared sampler of a name list at a unit.
    protected static void assignSamplerUnit(GlProgram program, int unit, String... names) {
        for (String name : names) {
            int location = program.getUniformLocation(name);
            if (location != -1) {
                LWJGL.glUniform1i(location, unit);
            }
        }
    }

    // Transformed sources and draw buffers of a program.
    static final class Stage {
        // Name of the program.
        final String name;
        // Vertex source.
        final String vsh;
        // Fragment source.
        final String fsh;
        // Draw buffers of the fragment stage.
        final int[] drawBuffers;

        // Creates a stage.
        private Stage(String name, String vsh, String fsh, int[] drawBuffers) {
            this.name = name;
            this.vsh = vsh;
            this.fsh = fsh;
            this.drawBuffers = drawBuffers;
        }
    }

    // Transforms the stages of a pack program for Distant Horizons and rejects a missing stage.
    static Stage transform(ShaderPack pack, ProgramSource source, boolean generic) {
        String name = source.getName();
        String vshSource = source.getVertexSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no vertex stage"));
        String fshSource = source.getFragmentSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no fragment stage"));
        vshSource = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, vshSource, TextureStage.GBUFFERS_AND_SHADOW));
        fshSource = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, fshSource, TextureStage.GBUFFERS_AND_SHADOW));

        Map<String, String> macros = ShaderMacros.forProgram(pack.getEnvironmentDefines(), name);
        int[] drawBuffers = GbufferTargets.sanitize(DrawBuffers.parseActive(fshSource, macros));
        ProgramAlphaTest packAlphaTest = ProgramAlphaTest.from(pack.getProperties(), name);
        String alphaTestSnippet = packAlphaTest.hasDirectives()
                ? packAlphaTest.toGlslDiscard("iris_FragData[0].a", "    ") : "";
        boolean modern = ModernPackTransformer.isModernSource(fshSource);
        String vsh = transformStage(name, vshSource, macros, modern,
                s -> DhTerrainTransformer.transformVertexShader(s, generic));
        String fsh = transformStage(name, fshSource, macros, modern,
                s -> DhTerrainTransformer.transformFragmentShader(s, drawBuffers, alphaTestSnippet, generic));
        return new Stage(name, vsh, fsh, drawBuffers);
    }

    // Transforms one stage before or after the macros depending on the source style.
    private static String transformStage(String name, String source, Map<String, String> macros, boolean modern,
                                         Function<String, String> bridge) {
        if (modern) {
            return bridge.apply(ShaderSourceFixes.stabilize(name,
                    ShaderMacros.injectDefines(source, macros)));
        }
        return ShaderSourceFixes.foldUncompilableConditionals(ShaderMacros.injectDefines(bridge.apply(source), macros));
    }

    // Compiles and links a program and runs setup while bound and deletes every object that fails.
    static GlProgram link(String label, Stage stage, Consumer<ProgramBuilder> attributes, Consumer<GlProgram> whileBound) {
        GlShader vertex = null;
        GlShader fragment = null;
        try {
            vertex = new GlShader(ShaderType.VERTEX, label + ".vsh", stage.vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, label + ".fsh", stage.fsh);
            GlState.drainGlError();
            ProgramBuilder builder = ProgramBuilder.begin(label)
                    .attach(vertex)
                    .attach(fragment);
            attributes.accept(builder);
            GlProgram program = builder
                    .bindFragmentDataLocation(0, "iris_FragData")
                    .link();
            // Unbinds and deletes the program when its setup fails
            try {
                GlState.reportGlError(label + " link");

                program.bind();
                try {
                    SamplerAssignment.assignToBoundProgram(program.getGlId());
                    whileBound.accept(program);
                } finally {
                    program.unbind();
                }
                return program;
            } catch (RuntimeException | Error e) {
                program.destroy();
                throw e;
            }
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    // Builds the pack uniforms of a program.
    static ProgramUniforms buildUniforms(String label, String name, GlProgram program) {
        ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program.getGlId());
        CommonUniforms.addCommonUniforms(uniforms);
        MatrixUniforms.addMatrixUniforms(uniforms);
        ActiveCustomUniforms.assignTo(uniforms);
        GlState.reportGlError(label + " uniforms");
        return uniforms.buildUniforms();
    }
}
