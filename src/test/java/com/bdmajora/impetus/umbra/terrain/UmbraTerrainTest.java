package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.vertices.UmbraChunkVertexType;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraTerrainTest {
    private static final String LEGACY_VERTEX = String.join("\n",
            "#version 120",
            "attribute vec4 mc_Entity;",
            "attribute vec2 extraAttribute;",
            "varying vec2 texcoord;",
            "uniform int worldTime;",
            "float timeScale = max(worldTime, 2);",
            "void main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; }");
    private static final String LEGACY_FRAGMENT = String.join("\n",
            "#version 120",
            "/* DRAWBUFFERS:02 */",
            "varying vec2 texcoord;",
            "uniform sampler2D texture;",
            "void main() { gl_FragData[0] = texture2D(texture, texcoord) * gl_Fog.color; gl_FragData[1] = vec4(1.0); }");
    private static final String MODERN_VERTEX = String.join("\n",
            "#version 330 compatibility",
            "in vec3 mc_Entity;",
            "in vec2 mc_midTexCoord;",
            "in float at_tangent;",
            "in ivec2 at_midBlock;",
            "out vec2 texcoord;",
            "void main() { gl_Position = ftransform(); texcoord = mc_midTexCoord; }");
    private static final String MODERN_FRAGMENT = String.join("\n",
            "#version 330 compatibility",
            "/* RENDERTARGETS: 0 */",
            "in vec2 texcoord;",
            "layout(location = 0) out vec4 color;",
            "void main() { color = vec4(texcoord, 0.0, 1.0); }");

    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<GL14> gl14;
    private int defaultTexUnit;

    @BeforeEach
    void mockGl() {
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        gl14 = Mockito.mockStatic(GL14.class);
        defaultTexUnit = OpenGlHelper.defaultTexUnit;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
    }

    @AfterEach
    void restore() {
        gl14.close();
        gl13.close();
        gl11.close();
        OpenGlHelper.defaultTexUnit = defaultTexUnit;
        UmbraTerrainProgramOverride.destroyShadowPrograms();
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Mixins.set(Umbra.class, "renderingPipelineFailed", false);
        com.bdmajora.testing.Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
    }

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    private static ChunkShaderOptions options(boolean reverse, boolean discard) {
        ChunkPrimitiveType triangles = mock(ChunkPrimitiveType.class);
        when(triangles.getGlPrimitiveType()).thenReturn(GlPrimitiveType.TRIANGLES);
        TerrainRenderPass pass = TerrainRenderPass.builder().name(reverse ? "translucent" : discard ? "cutout" : "solid")
                .useReverseOrder(reverse).fragmentDiscard(discard)
                .vertexType(UmbraChunkVertexType.INSTANCE).primitiveType(triangles).build();
        return new ChunkShaderOptions(List.of(), pass);
    }

    @Test
    void thePacksTerrainProgramsReplaceTheEnginesWhileItIsLoaded() {
        ChunkShaderOptions solid = options(false, false);
        ChunkShaderOptions cutout = options(false, true);
        ChunkShaderOptions water = options(true, false);
        // No pack: nothing to override
        assertFalse(UmbraTerrainProgramOverride.areShadersActive());
        assertNull(UmbraTerrainProgramOverride.getProgramOverride(solid));
        assertNull(UmbraTerrainProgramOverride.getShadowProgramOverride(solid));

        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, String.join("\n",
                "alphaTest.gbuffers_terrain_cutout = GREATER 0.2",
                "blend.gbuffers_water = SRC_ALPHA ONE_MINUS_SRC_ALPHA"));
        // Legacy terrain, a modern water program, and a legacy shadow program covering every shadow variant
        sources.put(path("/gbuffers_terrain.vsh"), LEGACY_VERTEX);
        sources.put(path("/gbuffers_terrain.fsh"), LEGACY_FRAGMENT);
        sources.put(path("/gbuffers_water.vsh"), MODERN_VERTEX);
        sources.put(path("/gbuffers_water.fsh"), MODERN_FRAGMENT);
        sources.put(path("/shadow.vsh"), LEGACY_VERTEX);
        sources.put(path("/shadow.fsh"), LEGACY_FRAGMENT);
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(sources));
        assertTrue(UmbraTerrainProgramOverride.areShadersActive());

        GlProgram<ChunkShaderInterface> terrain = UmbraTerrainProgramOverride.getProgramOverride(solid);
        GlProgram<ChunkShaderInterface> cutoutProgram = UmbraTerrainProgramOverride.getProgramOverride(cutout);
        GlProgram<ChunkShaderInterface> waterProgram = UmbraTerrainProgramOverride.getProgramOverride(water);
        assertNotNull(terrain);
        assertNotNull(cutoutProgram);
        assertNotNull(waterProgram);
        assertInstanceOf(UmbraTerrainShaderInterface.class, terrain.getInterface());
        // The pack's own attribute layout is bound by name in the vertex type's order
        verify(TestGl.gl(), Mockito.atLeastOnce()).glBindAttribLocation(anyInt(), eq(0), any(CharSequence.class));

        // Shadow programs are cached per variant, each falling back to the plain shadow program
        GlProgram<ChunkShaderInterface> shadowSolid = UmbraTerrainProgramOverride.getShadowProgramOverride(solid);
        assertSame(shadowSolid, UmbraTerrainProgramOverride.getShadowProgramOverride(solid));
        GlProgram<ChunkShaderInterface> shadowCutout = UmbraTerrainProgramOverride.getShadowProgramOverride(cutout);
        assertSame(shadowCutout, UmbraTerrainProgramOverride.getShadowProgramOverride(cutout));
        assertNotNull(UmbraTerrainProgramOverride.getShadowProgramOverride(water));
        UmbraTerrainProgramOverride.destroyShadowPrograms();
        verify(TestGl.gl(), Mockito.atLeast(2)).glDeleteProgram(anyInt());

        // A pack without the program, one that disables it, or a program the driver rejects, leaves the engine's own
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "")));
        assertNull(UmbraTerrainProgramOverride.getProgramOverride(solid));
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "program.gbuffers_terrain.enabled = false",
                path("/gbuffers_terrain.vsh"), LEGACY_VERTEX, path("/gbuffers_terrain.fsh"), LEGACY_FRAGMENT)));
        assertNull(UmbraTerrainProgramOverride.getProgramOverride(solid));
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(path("/gbuffers_terrain.vsh"), LEGACY_VERTEX,
                path("/gbuffers_terrain.fsh"), LEGACY_FRAGMENT)));
        when(TestGl.gl().glGetShaderi(anyInt(), eq(0x8B81))).thenReturn(0);
        assertNull(UmbraTerrainProgramOverride.getProgramOverride(solid));
        assertNotNull(Mixins.construct(UmbraTerrainProgramOverride.class));
    }

    @Test
    void theShaderInterfaceFeedsTheGbufferAndTheEnginesPerDrawState() {
        // Every uniform the prologue declares is present, and the block sampler under its second spelling
        ShaderBindingContext context = mock(ShaderBindingContext.class);
        when(context.bindUniformIfPresent(any(), any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            if (name.equals("tex")) {
                return null;
            }
            return ((java.util.function.IntFunction<?>) invocation.getArgument(1)).apply(7);
        });
        ProgramBlendState blend = ProgramBlendState.empty();
        ProgramAlphaTest alpha = ProgramAlphaTest.empty();
        UmbraTerrainShaderInterface shader = new UmbraTerrainShaderInterface(context, new int[] {0, 2}, blend, alpha);
        shader.setProjectionMatrix(new Matrix4f());
        shader.setModelViewMatrix(new Matrix4f());
        verify(TestGl.gl(), times(2)).glUniformMatrix4fv(eq(7), eq(false), any(FloatBuffer.class));
        shader.setRegionOffset(1, 2, 3);
        verify(TestGl.gl()).glUniform3f(7, 1, 2, 3);
        shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
        shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 2);
        verify(TestGl.gl()).glUniform1i(7, 0);
        verify(TestGl.gl()).glUniform1i(7, 2);

        ChunkShaderOptions water = options(true, false);
        ChunkShaderOptions solid = options(false, false);
        // Without a pipeline only the program's own uniforms upload
        ProgramUniforms uniforms = mock(ProgramUniforms.class);
        shader.setUniforms(uniforms);
        shader.setShadowSamplerKinds(null);
        shader.setupState(solid.pass());
        verify(uniforms).update();
        shader.restoreState();
        assertEquals(GlPrimitiveType.TRIANGLES, shader.getPrimitiveType());

        // With one, terrain lands in the gbuffer under the program's draw buffers, water with OptiFine's blend restored first
        UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        shader.setShadowSamplerKinds(ShadowSamplerKinds.ALL_COMPARE);
        shader.setupState(water.pass());
        verify(pipeline).onTerrainDraw(new int[] {0, 2}, blend, alpha, true);
        verify(pipeline).applyShadowSamplerKinds(ShadowSamplerKinds.ALL_COMPARE, true);
        verify(pipeline).bindCustomImages();
        shader.restoreState();
        verify(pipeline).afterTerrainDraw(2);
        shader.restoreState();
        verify(pipeline, times(1)).afterTerrainDraw(anyInt());
        // The shadow pass leaves the shadow framebuffer bound
        com.bdmajora.testing.Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        shader.setupState(solid.pass());
        verify(pipeline, times(1)).onTerrainDraw(any(), any(), any(), Mockito.anyBoolean());
        shader.restoreState();
        verify(pipeline, times(1)).afterTerrainDraw(anyInt());

        // A program with no prologue uniforms and no samplers ignores the engine's state, and defaults its draw buffers
        ShaderBindingContext bare = mock(ShaderBindingContext.class);
        UmbraTerrainShaderInterface plain = new UmbraTerrainShaderInterface(bare, null, blend, alpha);
        plain.setProjectionMatrix(new Matrix4f());
        plain.setModelViewMatrix(new Matrix4f());
        plain.setRegionOffset(0, 0, 0);
        plain.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
        verify(TestGl.gl(), never()).glUniform1i(0, 0);
    }

    @Test
    void theTransformsBridgeLegacyAndModernSourcesOntoTheTerrainFormat() {
        // The single-argument fragment overloads use the default draw buffers and alpha test
        assertTrue(ImpetusTerrainTransformer.transformFragmentShader(LEGACY_FRAGMENT).contains("void irisMain()"));
        assertTrue(ImpetusTerrainTransformer.transformFragmentShader(LEGACY_FRAGMENT, new int[] {0}).contains("iris_FragData"));
        assertTrue(ImpetusTerrainTransformer.transformFragmentShaderModern(MODERN_FRAGMENT).contains("void main()"));
        assertTrue(ImpetusTerrainTransformer.transformFragmentShaderModern(
                MODERN_FRAGMENT.replace("color = ", "gl_FragData[0] = ").replace("layout(location = 0) out vec4 color;", ""),
                new int[] {0}).contains("discard"));
        // Modern attributes are adapted to the type the pack declared them with, and their declarations removed
        String modern = ImpetusTerrainTransformer.transformVertexShaderModern(MODERN_VERTEX);
        assertTrue(modern.contains("#define mc_Entity (iris_EntityFull.xyz)"), modern);
        assertTrue(modern.contains("#define mc_midTexCoord (iris_MidTexFull.xy)"), modern);
        assertTrue(modern.contains("#define at_tangent (iris_Tangent.x)"), modern);
        assertTrue(modern.contains("#define at_midBlock ivec2(iris_MidBlock.xy)"), modern);
        assertFalse(modern.contains("in vec3 mc_Entity;"), modern);
        String ints = ImpetusTerrainTransformer.transformVertexShaderModern(
                "#version 330\nin int mc_Entity;\nin uint mc_midTexCoord;\nin vec4 at_tangent;\nvoid main() {}");
        assertTrue(ints.contains("#define mc_Entity int(iris_EntityFull.x)"), ints);
        assertTrue(ints.contains("#define mc_midTexCoord uint(max(iris_MidTexFull.x, 0.0))"), ints);
        assertTrue(ints.contains("#define at_tangent iris_Tangent\n"), ints);
        // The legacy transform turns stray attributes into zeroed globals and hoists a non-constant initialiser into main
        String legacy = ImpetusTerrainTransformer.transformVertexShader(LEGACY_VERTEX);
        assertTrue(legacy.contains("vec2 extraAttribute = vec2(0.0);"), legacy);
        assertTrue(legacy.contains("timeScale = max(worldTime, 2);"), legacy);
        assertTrue(FullscreenTransformer.transformFragmentShader(LEGACY_FRAGMENT).contains("irisMain"));
    }

    @Test
    void stagesBetweenVertexAndFragmentCarryTheGeneratedVaryings() {
        // Vertex and fragment re-declare the generated varyings as one block
        String vertex = ImpetusTerrainTransformer.blockGeneratedVaryings(ImpetusTerrainTransformer.transformVertexShaderModern(MODERN_VERTEX), true);
        assertTrue(vertex.contains("out IrisTerrainVaryings {"), vertex);
        assertFalse(vertex.contains("flat out float iris_AlphaCutoff;"), vertex);
        String fragment = ImpetusTerrainTransformer.blockGeneratedVaryings(ImpetusTerrainTransformer.transformFragmentShaderModern(MODERN_FRAGMENT), false);
        assertTrue(fragment.contains("in IrisTerrainVaryings {"), fragment);

        // A geometry stage that leaves the built-ins alone gets them copied through on every EmitVertex
        String geometry = ImpetusTerrainTransformer.transformAuxiliaryStage(
                "#version 330 compatibility\nlayout(triangles) in;\nvoid main() { EmitVertex(); }",
                com.bdmajora.impetus.engine.impl.gl.shader.ShaderType.GEOM);
        assertTrue(geometry.startsWith("#version 330 compatibility"), geometry);
        assertTrue(geometry.contains("in IrisTerrainVaryings"), geometry);
        assertTrue(geometry.contains("#define EmitVertex() { iris_passVaryings(); EmitVertex(); }"), geometry);
        assertTrue(geometry.contains("iris_TexCoordArr = iris_varyingsIn[0].iris_TexCoordArr;"), geometry);
        assertTrue(geometry.contains("void irisMain()"), geometry);
        // One that reads the old built-ins through gl_in and writes its own keeps them
        String written = ImpetusTerrainTransformer.transformAuxiliaryStage(
                "#version 400 compatibility\nvoid main() { gl_TexCoord[0] = gl_in[0].gl_TexCoord[0]; gl_FogFragCoord = gl_in[1].gl_FogFragCoord; imageStore(x, ivec2(0), vec4(0)); }",
                com.bdmajora.impetus.engine.impl.gl.shader.ShaderType.GEOM);
        assertTrue(written.contains("iris_varyingsIn[0].iris_TexCoordArr[0]"), written);
        assertTrue(written.contains("iris_varyingsIn[1].iris_FogFragCoord"), written);
        assertFalse(written.contains("iris_TexCoordArr = iris_varyingsIn[0].iris_TexCoordArr;"), written);
        assertTrue(written.startsWith("#version 430 compatibility"), written);

        String control = ImpetusTerrainTransformer.transformAuxiliaryStage("#version 400 core\nlayout(vertices = 3) out;\nvoid main() {}",
                com.bdmajora.impetus.engine.impl.gl.shader.ShaderType.TESS_CTRL);
        assertTrue(control.contains("out IrisTerrainVaryings {"), control);
        assertTrue(control.contains("iris_varyingsOut[gl_InvocationID].iris_AlphaCutoff = iris_varyingsIn[gl_InvocationID].iris_AlphaCutoff;"), control);
        String evaluation = ImpetusTerrainTransformer.transformAuxiliaryStage("#version 400 core\nlayout(triangles) in;\nvoid main() {}",
                com.bdmajora.impetus.engine.impl.gl.shader.ShaderType.TESS_EVALUATE);
        assertTrue(evaluation.contains("gl_TessCoord.x * iris_varyingsIn[0].iris_FogFragCoord"), evaluation);
        String evaluationWrites = ImpetusTerrainTransformer.transformAuxiliaryStage(
                "#version 400 core\nvoid main() { gl_TexCoord[0] = vec4(0.0); gl_FogFragCoord = 1.0; }",
                com.bdmajora.impetus.engine.impl.gl.shader.ShaderType.TESS_EVALUATE);
        assertFalse(evaluationWrites.contains("gl_TessCoord.x * iris_varyingsIn[0].iris_FogFragCoord"), evaluationWrites);

        // mc_chunkFade: live on the camera path, a constant in the shadow pass, absent when unread
        String fading = ImpetusTerrainTransformer.transformVertexShader("#version 120\nvarying float f;\nvoid main() { f = mc_chunkFade; }", false);
        assertTrue(fading.contains("uniform float iris_ChunkAgesMs[256];"), fading);
        assertTrue(fading.contains("mc_chunkFade = iris_ChunkFadeInv <= 0.0 ? 1.0"), fading);
        String shadowFade = ImpetusTerrainTransformer.transformVertexShaderModern("#version 330\nout float f;\nvoid main() { f = mc_chunkFade; }", true);
        assertTrue(shadowFade.contains("const float mc_chunkFade = -1.0;"), shadowFade);
        assertFalse(shadowFade.contains("iris_ChunkAgesMs"), shadowFade);
        assertFalse(ImpetusTerrainTransformer.transformVertexShaderModern(MODERN_VERTEX).contains("mc_chunkFade"));
    }

    @Test
    void auxiliaryStagesLinkWhenTheyCanAndFallAwayWhenTheyCannot() {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, "iris.features.optional = TESSELLATION_SHADERS");
        sources.put(path("/gbuffers_terrain.vsh"), MODERN_VERTEX);
        sources.put(path("/gbuffers_terrain.fsh"), MODERN_FRAGMENT);
        sources.put(path("/gbuffers_terrain.tcs"), "#version 400 compatibility\nlayout(vertices = 3) out;\nvoid main() {}");
        sources.put(path("/gbuffers_terrain.tes"), "#version 400 compatibility\nlayout(triangles) in;\nvoid main() {}");
        sources.put(path("/gbuffers_water.vsh"), MODERN_VERTEX);
        sources.put(path("/gbuffers_water.fsh"), MODERN_FRAGMENT);
        sources.put(path("/gbuffers_water.gsh"), "#version 330 compatibility\nlayout(triangles) in;\nvoid main() { EmitVertex(); }");
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(sources));

        // The tessellated terrain program draws patches
        GlProgram<ChunkShaderInterface> terrain = UmbraTerrainProgramOverride.getProgramOverride(options(false, false));
        assertNotNull(terrain);
        verify(TestGl.gl()).glCreateShader(0x8E88);
        // The real uniforms read the client; the draw-state switch is what matters here
        ((UmbraTerrainShaderInterface) terrain.getInterface()).setUniforms(mock(ProgramUniforms.class));
        terrain.getInterface().setupState(options(false, false).pass());
        assertEquals(GlPrimitiveType.PATCHES, terrain.getInterface().getPrimitiveType());
        verify(TestGl.gl()).glPatchParameteri(0x8E72, 3);

        // A geometry stage the link rejects costs only that stage
        when(TestGl.gl().glGetProgrami(anyInt(), eq(0x8B82))).thenReturn(0, 1);
        GlProgram<ChunkShaderInterface> water = UmbraTerrainProgramOverride.getProgramOverride(options(true, false));
        assertNotNull(water);
        ((UmbraTerrainShaderInterface) water.getInterface()).setUniforms(mock(ProgramUniforms.class));
        water.getInterface().setupState(options(true, false).pass());
        assertEquals(GlPrimitiveType.TRIANGLES, water.getInterface().getPrimitiveType());
    }

    @Test
    void sectionAgesFeedTheFadeOnlyWhenThePackReadsIt() {
        com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.chunkFadeInDuration = 0;
        try {
            ShaderBindingContext context = mock(ShaderBindingContext.class);
            when(context.bindUniformIfPresent(any(), any())).thenAnswer(invocation ->
                    ((java.util.function.IntFunction<?>) invocation.getArgument(1)).apply(4));
            UmbraTerrainShaderInterface fading = new UmbraTerrainShaderInterface(context, null, ProgramBlendState.empty(), ProgramAlphaTest.empty(), false);
            // Ages in milliseconds, capped at thirty seconds; no fade duration reads as a finished fade
            fading.setSectionAges(100_000_000_000L, new long[] {99_000_000_000L, 0L});
            verify(TestGl.gl()).glUniform1fv(eq(4), any(FloatBuffer.class));
            verify(TestGl.gl()).glUniform1f(4, 0.0f);
            com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.chunkFadeInDuration = 500;
            fading.setSectionAges(1_000_000L, new long[] {0L});
            verify(TestGl.gl()).glUniform1f(4, 0.002f);

            // A context with the ages but no fade rate still uploads the ages
            ShaderBindingContext agesOnly = mock(ShaderBindingContext.class);
            when(agesOnly.bindUniformIfPresent(any(), any())).thenAnswer(invocation -> "iris_ChunkAgesMs".equals(invocation.getArgument(0))
                    ? ((java.util.function.IntFunction<?>) invocation.getArgument(1)).apply(6) : null);
            new UmbraTerrainShaderInterface(agesOnly, null, ProgramBlendState.empty(), ProgramAlphaTest.empty(), false)
                    .setSectionAges(1_000_000L, new long[] {0L});
            verify(TestGl.gl()).glUniform1fv(eq(6), any(FloatBuffer.class));

            // No fade in the program: nothing uploads
            Mockito.clearInvocations(TestGl.gl());
            new UmbraTerrainShaderInterface(mock(ShaderBindingContext.class), null, ProgramBlendState.empty(), ProgramAlphaTest.empty())
                    .setSectionAges(1L, new long[] {0L});
            verify(TestGl.gl(), never()).glUniform1fv(anyInt(), any(FloatBuffer.class));
        } finally {
            com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.chunkFadeInDuration = 0;
        }
    }

    @Test
    void globalInitialisersNeedingUniformsMoveIntoMain() {
        GlslGlobalInitHoister.Result result = GlslGlobalInitHoister.hoist(String.join("\n",
                "#ifdef FOG",
                "float fogFactor = fogDensity * 2.0;",
                "#endif",
                "vec3 constant = vec3(1.0, 2.0,",
                "    3.0);",
                "vec3 spread = vec3(sunAngle,",
                "    0.0, 1.0);",
                "int trailing = offset; // explained",
                "int chained = offset; chained++;",
                "void f() {",
                "    float local = offset;",
                "}",
                "float runaway = offset * (2.0"));
        assertTrue(result.hoistedAssignments.contains("#ifdef FOG"), result.hoistedAssignments);
        assertTrue(result.hoistedAssignments.contains("fogFactor = fogDensity * 2.0;"), result.hoistedAssignments);
        assertTrue(result.hoistedAssignments.contains("spread = vec3(sunAngle,\n    0.0, 1.0);"), result.hoistedAssignments);
        assertTrue(result.hoistedAssignments.contains("trailing = offset;"), result.hoistedAssignments);
        assertFalse(result.hoistedAssignments.contains("constant"), result.hoistedAssignments);
        assertFalse(result.hoistedAssignments.contains("chained"), result.hoistedAssignments);
        assertFalse(result.hoistedAssignments.contains("local"), result.hoistedAssignments);
        assertFalse(result.hoistedAssignments.contains("runaway"), result.hoistedAssignments);
        assertTrue(result.body.contains("float fogFactor;"), result.body);
        assertEquals("", GlslGlobalInitHoister.hoist("const float a = b;\nfloat c = 1.0;").hoistedAssignments);
    }

    @Test
    void smallRewritesStayConservative() {
        // Integer max/min widen only when both sides are integer uniforms or literals
        String widened = GlslIntegerOverloadPolyfill.widenIntegerBuiltinCalls("x",
                "#version 120\nint a = max(worldTime, 2); float b = min(someFloat, 1); int c = max(eyeBrightness.x, frameCounter);");
        assertTrue(widened.contains("max(float(worldTime), float(2))"), widened);
        assertTrue(widened.contains("min(someFloat, 1)"), widened);
        assertTrue(widened.contains("max(float(eyeBrightness.x), float(frameCounter))"), widened);
        assertTrue(GlslIntegerOverloadPolyfill.widenIntegerBuiltinCalls("x", "max(1 + 2, 3)").contains("max(1 + 2, 3)"));
        // gl_Fog in a modern source gets the live fog uniforms declared after the preamble
        String fogged = ModernPackTransformer.transform("#version 330\n#extension GL_ARB_foo : enable\n/* a\n comment */\nvoid main() { vec4 c = gl_Fog.color; }");
        assertTrue(fogged.contains("uniform vec4 iris_FogColor;"), fogged);
        assertTrue(fogged.indexOf("#extension") < fogged.indexOf("uniform vec4 iris_FogColor;"), fogged);
        // A pack's own local named like a modern built-in is left alone
        String local = VanillaNameTransformer.transform("vec4 f(mat4 projectionMatrix) { return projectionMatrix[0]; }");
        assertTrue(local.contains("mat4 projectionMatrix)"), local);
        assertFalse(local.contains("gl_ProjectionMatrix"), local);
    }
}
