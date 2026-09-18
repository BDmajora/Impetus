package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.MemoryStack;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
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
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A pack's dh_terrain, dh_water or dh_shadow program built on DH's LOD vertex format (Iris's IrisLodRenderProgram): DH keeps its own VAO and draw calls, this program is bound over DH's from the DhApiBeforeRenderPassEvent/DhApiBeforeBufferRenderEvent handlers, so its attribute locations must be DH's (0 vPosition, 1 colour, 2 extra) and it takes DH's per-pass matrices and per-buffer model offset the way DH's own shader would
public final class DhLodRenderProgram {
    private final GlProgram program;
    private final String name;
    private final int[] drawBuffers;
    private final ProgramBlendState blendState;
    private final ProgramUniforms uniforms;
    private final ShadowSamplerKinds shadowSamplerKinds;
    private final boolean translucent;
    // Per-pass and per-buffer uniforms of the generated prologue
    private final int uModelOffset;
    private final int uWorldYOffset;
    private final int uMircoOffset;
    private final int uModelView;
    private final int uModelViewInverse;
    private final int uProjection;
    private final int uProjectionInverse;
    private final int uNormalMatrix;
    private final int uClipDistance;

    private DhLodRenderProgram(GlProgram program, String name, int[] drawBuffers, ProgramBlendState blendState,
                               ProgramUniforms uniforms, ShadowSamplerKinds shadowSamplerKinds, boolean translucent) {
        this.program = program;
        this.name = name;
        this.drawBuffers = drawBuffers;
        this.blendState = blendState;
        this.uniforms = uniforms;
        this.shadowSamplerKinds = shadowSamplerKinds;
        this.translucent = translucent;
        this.uModelOffset = program.getUniformLocation("modelOffset");
        this.uWorldYOffset = program.getUniformLocation("worldYOffset");
        this.uMircoOffset = program.getUniformLocation("mircoOffset");
        this.uModelView = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW);
        this.uModelViewInverse = program.getUniformLocation(DhTerrainTransformer.MODEL_VIEW_INVERSE);
        this.uProjection = program.getUniformLocation(DhTerrainTransformer.PROJECTION);
        this.uProjectionInverse = program.getUniformLocation(DhTerrainTransformer.PROJECTION_INVERSE);
        this.uNormalMatrix = program.getUniformLocation(DhTerrainTransformer.NORMAL_MATRIX);
        this.uClipDistance = program.getUniformLocation("clipDistance");
    }

    // Transforms and links one DH program the way UmbraTerrainProgramOverride builds the terrain programs: custom texture renames, modern name rewrite, the pack's macro environment, then the DH bridge; a compile failure propagates as a ShaderCompileException so the pack fails loudly rather than drawing LODs with a half-built program
    static DhLodRenderProgram create(UmbraRenderingPipeline pipeline, ShaderPack pack, ProgramSource source,
                                     boolean shadowPass, boolean translucent) {
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
        // Iris injects no default alpha test into DH programs; only the pack's own alphaTest.<program> directive discards
        ProgramAlphaTest packAlphaTest = ProgramAlphaTest.from(pack.getProperties(), name);
        String alphaTestSnippet = packAlphaTest.hasDirectives()
                ? packAlphaTest.toGlslDiscard("iris_FragData[0].a", "    ") : "";
        boolean modern = ModernPackTransformer.isModernSource(fshSource);
        String vsh = transformStage(name, vshSource, macros, modern,
                s -> DhTerrainTransformer.transformVertexShader(s, false));
        String fsh = transformStage(name, fshSource, macros, modern,
                s -> DhTerrainTransformer.transformFragmentShader(s, drawBuffers, alphaTestSnippet, false));

        GlShader vertex = null;
        GlShader fragment = null;
        try {
            vertex = new GlShader(ShaderType.VERTEX, "dh_" + name + ".vsh", vsh);
            fragment = new GlShader(ShaderType.FRAGMENT, "dh_" + name + ".fsh", fsh);
            UmbraRenderingPipeline.drainGlError();
            GlProgram program = ProgramBuilder.begin("dh_" + name)
                    .attach(vertex)
                    .attach(fragment)
                    // DH's VAO layout (GlDhTerrainShaderProgram): uvec4 position+meta, normalized colour, uvec4 material/normal/tile
                    .bindAttributeLocation(0, "vPosition")
                    .bindAttributeLocation(1, "iris_color")
                    .bindAttributeLocation(2, "irisExtra")
                    .bindFragmentDataLocation(0, "iris_FragData")
                    .link();
            UmbraRenderingPipeline.reportGlError("dh '" + name + "' link");

            program.bind();
            UmbraRenderingPipeline.assignSamplerUnitsToBoundProgram(program.getGlId());
            assignFixedSamplers(program);
            program.unbind();
            ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program.getGlId());
            CommonUniforms.addCommonUniforms(uniforms);
            MatrixUniforms.addMatrixUniforms(uniforms);
            ActiveCustomUniforms.assignTo(uniforms);
            UmbraRenderingPipeline.reportGlError("dh '" + name + "' uniforms");
            return new DhLodRenderProgram(program, name, drawBuffers,
                    ProgramBlendState.from(pack.getProperties(), name), uniforms.buildUniforms(),
                    ShadowSamplerKinds.detect(program.getGlId()), translucent);
        } finally {
            if (vertex != null) {
                vertex.destroy();
            }
            if (fragment != null) {
                fragment.destroy();
            }
        }
    }

    // The macro injection and stabilisation the terrain override applies around a stage transform, ordered per source style: modern sources get the defines stabilised BEFORE the bridge, legacy ones fold their conditionals AFTER it
    private static String transformStage(String name, String source, Map<String, String> macros, boolean modern,
                                         java.util.function.Function<String, String> bridge) {
        if (modern) {
            return bridge.apply(UmbraRenderingPipeline.stabilizeShaderSource(name,
                    ShaderMacros.injectDefines(source, macros)));
        }
        return UmbraRenderingPipeline.foldUncompilableConditionals(name,
                ShaderMacros.injectDefines(bridge.apply(source), macros));
    }

    // The samplers with a fixed unit for a LOD draw: DH's block atlas (and the block sampler aliases, which DH programs never read) on unit 0, the lightmap on unit 1 like every gbuffer program; set once, fillUniformData binds the textures
    private static void assignFixedSamplers(GlProgram program) {
        for (String atlasName : new String[]{"dhBlockAtlas", "texture", "gtexture", "tex", "gcolor"}) {
            int location = program.getUniformLocation(atlasName);
            if (location != -1) {
                LWJGL.glUniform1i(location, 0);
            }
        }
        for (String lightmapName : new String[]{"lightmap", "texLightmap"}) {
            int location = program.getUniformLocation(lightmapName);
            if (location != -1) {
                LWJGL.glUniform1i(location, 1);
            }
        }
    }

    public int[] getDrawBuffers() {
        return this.drawBuffers;
    }

    public ProgramBlendState getBlendState() {
        return this.blendState;
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

        Matrix4f modelViewInverse = new Matrix4f(modelView).invert();
        setUniform(this.uModelView, modelView);
        setUniform(this.uModelViewInverse, modelViewInverse);
        setUniform(this.uProjection, projection);
        setUniform(this.uProjectionInverse, new Matrix4f(projection).invert());
        setUniform(this.uNormalMatrix, modelViewInverse.transpose3x3(new Matrix3f()));
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

    private static void setUniform(int location, float value) {
        if (location != -1) {
            LWJGL.glUniform1f(location, value);
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
