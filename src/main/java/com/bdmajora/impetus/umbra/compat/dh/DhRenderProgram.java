package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.MemoryStack;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// What the LOD and generic DH programs share: the pack-side transform and link of one DH stage, the five matrix uniforms every generated prologue declares, and the uniform upload helpers
abstract class DhRenderProgram {
    protected final GlProgram program;
    protected final int[] drawBuffers;
    protected final ProgramBlendState blendState;
    protected final ProgramUniforms uniforms;
    private final int uModelView;
    private final int uModelViewInverse;
    private final int uProjection;
    private final int uProjectionInverse;
    private final int uNormalMatrix;
    // Reused per pass rather than allocated: the inverses and the normal matrix are derived each bind
    private final Matrix4f modelViewInverse = new Matrix4f();
    private final Matrix4f projectionInverse = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();

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

    public int[] getDrawBuffers() {
        return this.drawBuffers;
    }

    public ProgramBlendState getBlendState() {
        return this.blendState;
    }

    // The pass matrices and everything derived from them, to the program currently bound
    protected final void uploadMatrices(Matrix4fc projection, Matrix4fc modelView) {
        this.modelViewInverse.set(modelView).invert();
        setUniform(this.uModelView, modelView);
        setUniform(this.uModelViewInverse, this.modelViewInverse);
        setUniform(this.uProjection, projection);
        setUniform(this.uProjectionInverse, this.projectionInverse.set(projection).invert());
        setUniform(this.uNormalMatrix, this.modelViewInverse.transpose3x3(this.normalMatrix));
    }

    protected static void setUniform(int location, float value) {
        if (location != -1) {
            LWJGL.glUniform1f(location, value);
        }
    }

    protected static void setUniform(int location, Matrix4fc matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = LWJGL.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            LWJGL.glUniformMatrix4fv(location, false, buffer);
        }
    }

    protected static void setUniform(int location, Matrix3f matrix) {
        if (location == -1) {
            return;
        }
        try (MemoryStack stack = LWJGL.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            matrix.get(buffer);
            LWJGL.glUniformMatrix3fv(location, false, buffer);
        }
    }

    // Points every sampler named in names that the program declares at one texture unit; call with the program bound
    protected static void assignSamplerUnit(GlProgram program, int unit, String... names) {
        for (String name : names) {
            int location = program.getUniformLocation(name);
            if (location != -1) {
                LWJGL.glUniform1i(location, unit);
            }
        }
    }

    // One DH stage after the pack-side transforms: custom texture renames, modern name rewrite, the pack's macro environment, then the DH bridge
    static final class Stage {
        final String name;
        final String vsh;
        final String fsh;
        final int[] drawBuffers;

        private Stage(String name, String vsh, String fsh, int[] drawBuffers) {
            this.name = name;
            this.vsh = vsh;
            this.fsh = fsh;
            this.drawBuffers = drawBuffers;
        }
    }

    // Transforms a program's sources the way UmbraTerrainProgramOverride builds the terrain programs; generic selects the instanced-box prologue over the LOD one
    static Stage transform(ShaderPack pack, ProgramSource source, boolean generic) {
        String name = source.getName();
        String vshSource = source.getVertexSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no vertex stage"));
        String fshSource = source.getFragmentSource().orElseThrow(() ->
                new IllegalStateException("DH program " + name + " has no fragment stage"));
        vshSource = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, vshSource, TextureStage.GBUFFERS_AND_SHADOW));
        fshSource = VanillaNameTransformer.transform(CustomTextureTransformer.transform(name, fshSource, TextureStage.GBUFFERS_AND_SHADOW));

        Map<String, String> macros = ShaderMacros.forProgram(pack.getEnvironmentDefines(), name);
        int[] drawBuffers = UmbraRenderingPipeline.sanitizeDrawBuffers(name, DrawBuffers.parseActive(fshSource, macros));
        // Iris injects no default alpha test into DH programs; only the pack's own alphaTest.<program> directive discards
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

    // The macro injection and stabilisation the terrain override applies around a stage transform, ordered per source style: modern sources get the defines stabilised BEFORE the bridge, legacy ones fold their conditionals AFTER it
    private static String transformStage(String name, String source, Map<String, String> macros, boolean modern,
                                         Function<String, String> bridge) {
        if (modern) {
            return bridge.apply(UmbraRenderingPipeline.stabilizeShaderSource(name,
                    ShaderMacros.injectDefines(source, macros)));
        }
        return UmbraRenderingPipeline.foldUncompilableConditionals(name,
                ShaderMacros.injectDefines(bridge.apply(source), macros));
    }

    // Compiles and links a transformed stage under label (the GL object name): attributes binds the locations DH's VAO expects, whileBound runs sampler setup with the program bound; a compile failure propagates as a ShaderCompileException so the pack fails loudly rather than drawing with a half-built program
    static GlProgram link(String label, Stage stage, Consumer<ProgramBuilder> attributes, Consumer<GlProgram> whileBound) {
        GlShader vertex = null;
        GlShader fragment = null;
        try {
            vertex = new GlShader(ShaderType.VERTEX, label + ".vsh", stage.vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, label + ".fsh", stage.fsh);
            UmbraRenderingPipeline.drainGlError();
            ProgramBuilder builder = ProgramBuilder.begin(label)
                    .attach(vertex)
                    .attach(fragment);
            attributes.accept(builder);
            GlProgram program = builder
                    .bindFragmentDataLocation(0, "iris_FragData")
                    .link();
            UmbraRenderingPipeline.reportGlError(label + " link");

            program.bind();
            UmbraRenderingPipeline.assignSamplerUnitsToBoundProgram(program.getGlId());
            whileBound.accept(program);
            program.unbind();
            return program;
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    // The pack's whole uniform set for a linked program: the common and matrix uniforms plus the pack's custom ones
    static ProgramUniforms buildUniforms(String label, String name, GlProgram program) {
        ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program.getGlId());
        CommonUniforms.addCommonUniforms(uniforms);
        MatrixUniforms.addMatrixUniforms(uniforms);
        ActiveCustomUniforms.assignTo(uniforms);
        UmbraRenderingPipeline.reportGlError(label + " uniforms");
        return uniforms.buildUniforms();
    }
}
