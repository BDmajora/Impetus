package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshDrawPhase;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.UnsupportedProgramException;
import com.bdmajora.impetus.umbra.terrain.mesh.NvidiumPackPorter;
import com.bdmajora.impetus.umbra.vertices.UmbraMeshVertex;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraMeshShadingTest {
    private static final String VERTEX = String.join("\n",
            "#version 120",
            "varying vec2 texcoord;",
            "void main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; }");
    private static final String FRAGMENT = String.join("\n",
            "#version 120",
            "varying vec2 texcoord;",
            "uniform sampler2D texture;",
            "void main() { gl_FragData[0] = texture2D(texture, texcoord); }");
    private static final MeshTerrainConfig CONFIG = new MeshTerrainConfig(true, MeshTranslucencySorting.QUADS, MeshStatistics.NONE, false, 512, 0, 8);
    private static final ChunkRenderMatrices MATRICES = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f());
    private static final TerrainRenderPass SOLID = pass("solid", false);
    private static final TerrainRenderPass TRANSLUCENT = pass("translucent", true);
    // NV_mesh_shader's stage types
    private static final int TASK_SHADER = 0x955A;
    private static final int MESH_SHADER = 0x9559;

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
        when(TestGl.gl().glGetUniformLocation(anyInt(), eq("umbra_TaskMode"))).thenReturn(41);
        when(TestGl.gl().glGetUniformLocation(anyInt(), eq("umbra_CullFacing"))).thenReturn(42);
    }

    @AfterEach
    void restore() {
        gl14.close();
        gl13.close();
        gl11.close();
        OpenGlHelper.defaultTexUnit = defaultTexUnit;
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(UmbraMeshShading.class, "failedPack", null);
        Mixins.set(Umbra.class, "nvidiumPortNote", "");
    }

    private static TerrainRenderPass pass(String name, boolean translucent) {
        return TerrainRenderPass.builder().name(name).useReverseOrder(translucent).useTranslucencySorting(translucent)
                .vertexType(UmbraMeshVertex.INSTANCE).primitiveType(QuadPrimitiveType.TRIANGULATED).build();
    }

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    // A pack with terrain, water and, when asked, a shadow program, its Nvidium port added when asked, then the extra files on top
    private static Map<AbsolutePackPath, String> sources(boolean shadow, boolean ported, Map<String, String> extra) {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, "");
        String[] programs = shadow ? new String[]{"gbuffers_terrain", "gbuffers_water", "shadow"} : new String[]{"gbuffers_terrain", "gbuffers_water"};
        for (String program : programs) {
            sources.put(path("/" + program + ".vsh"), VERTEX);
            sources.put(path("/" + program + ".fsh"), FRAGMENT);
        }
        if (ported) {
            sources.putAll(NvidiumPackPorter.portSources(sources, new ArrayList<>(), new ArrayList<>()));
        }
        extra.forEach((file, text) -> sources.put(path(file), text));
        return sources;
    }

    private static ShaderPack pack(boolean shadow, boolean ported, Map<String, String> extra) {
        return new ShaderPack(sources(shadow, ported, extra));
    }

    // The real uniforms read the client; what matters here is which program binds for which phase
    private static void quietUniforms(UmbraMeshShading shading) {
        for (String field : new String[]{"opaque", "translucent", "shadowOpaque", "shadowTranslucent"}) {
            Object program = Mixins.get(shading, field);
            if (program != null) {
                GlProgram<ChunkShaderInterface> linked = Mixins.get(program, "program");
                ((UmbraTerrainShaderInterface) linked.getInterface()).setUniforms(mock(ProgramUniforms.class));
            }
        }
    }

    @Test
    void aPortsProgramsBuildOnceFromTheirShippedStagesAndBindPerPhase() {
        ShaderPack pack = pack(true, true, Map.of());
        Mixins.set(Umbra.class, "currentPack", pack);
        UmbraMeshShading shading = new UmbraMeshShading(pack, CONFIG);
        assertEquals(UmbraMeshVertex.STRIDE, shading.vertexStride());
        assertTrue(UmbraMeshShading.canRun(pack));
        assertFalse(UmbraMeshShading.canRun(null));
        assertFalse(UmbraMeshShading.canRun(pack(true, false, Map.of())));
        // The debug screen's reason: none for a port that runs, the port's own note or a default for one that cannot
        assertEquals("", UmbraMeshShading.whyNot(pack));
        assertEquals("the pack's terrain program has no Nvidium port", UmbraMeshShading.whyNot(null));
        Mixins.set(Umbra.class, "nvidiumPortNote", "gbuffers_terrain: geometry or tessellation stage");
        assertEquals("gbuffers_terrain: geometry or tessellation stage", UmbraMeshShading.whyNot(pack(true, false, Map.of())));

        // Asking for the shadow programs builds all four; each task variant compiles once and is shared
        assertTrue(shading.hasShadowPrograms());
        assertTrue(shading.hasShadowPrograms());
        assertFalse(shading.hasFailed());
        verify(TestGl.gl(), times(2)).glCreateShader(TASK_SHADER);
        verify(TestGl.gl(), times(4)).glCreateShader(MESH_SHADER);
        verify(TestGl.gl(), times(4 * 3)).glAttachShader(anyInt(), anyInt());
        quietUniforms(shading);

        // The main draw then the temporal one share the pass state and only change the task mode
        assertTrue(shading.bindOpaque(MeshDrawPhase.MAIN, SOLID, MATRICES));
        assertTrue(shading.bindOpaque(MeshDrawPhase.TEMPORAL, SOLID, MATRICES));
        verify(TestGl.gl()).glUniform1i(41, 0);
        verify(TestGl.gl()).glUniform1i(41, 1);
        verify(TestGl.gl(), times(2)).glUniform1i(eq(42), anyInt());
        shading.finish();
        shading.finish();

        // The shadow map draws every facing
        assertTrue(shading.bindOpaque(MeshDrawPhase.SHADOW, SOLID, MATRICES));
        verify(TestGl.gl()).glUniform1i(42, 0);
        assertTrue(shading.bindTranslucent(MeshDrawPhase.SHADOW, TRANSLUCENT, MATRICES));
        assertTrue(shading.bindTranslucent(MeshDrawPhase.MAIN, TRANSLUCENT, MATRICES));
        verify(TestGl.gl(), times(2)).glUniform1i(41, 2);
        // No pass means no state to apply, so nothing is bound
        assertFalse(shading.bindOpaque(MeshDrawPhase.MAIN, null, MATRICES));
        shading.finish();

        Mockito.clearInvocations(TestGl.gl());
        shading.delete();
        verify(TestGl.gl(), times(4)).glDeleteProgram(anyInt());
        shading.delete();
        verify(TestGl.gl(), times(4)).glDeleteProgram(anyInt());
    }

    @Test
    void aPortWithoutShadowsBuildsItsTerrainAlone() {
        ShaderPack pack = pack(false, true, Map.of());
        Mixins.set(Umbra.class, "currentPack", pack);
        UmbraMeshShading shading = new UmbraMeshShading(pack, CONFIG);
        assertFalse(shading.hasShadowPrograms());
        quietUniforms(shading);
        assertTrue(shading.bindOpaque(MeshDrawPhase.MAIN, SOLID, MATRICES));
        assertFalse(shading.bindOpaque(MeshDrawPhase.SHADOW, SOLID, MATRICES));
        assertFalse(shading.bindTranslucent(MeshDrawPhase.SHADOW, TRANSLUCENT, MATRICES));
        shading.finish();
        shading.delete();
    }

    @Test
    void aProgramThePortLeftOutSendsThePackBackToOpenGl() {
        // gbuffers_water had a geometry stage at port time, so only its OpenGL stages exist
        Map<AbsolutePackPath, String> sources = sources(true, true, Map.of());
        sources.remove(path("/gbuffers_water.mesh"));
        sources.remove(path("/gbuffers_water.mesh.fsh"));
        ShaderPack pack = new ShaderPack(sources);
        Mixins.set(Umbra.class, "currentPack", pack);
        UmbraMeshShading shading = new UmbraMeshShading(pack, CONFIG);
        assertTrue(UmbraMeshShading.canRun(pack));
        assertFalse(shading.bindOpaque(MeshDrawPhase.MAIN, SOLID, MATRICES));
        assertTrue(shading.hasFailed());
        assertFalse(UmbraMeshShading.canRun(pack));
        assertTrue(UmbraMeshShading.getFailureReason().contains("gbuffers_water has no Nvidium port"));
        assertEquals(UmbraMeshShading.getFailureReason(), UmbraMeshShading.whyNot(pack));
        // The terrain program that did link is freed again
        verify(TestGl.gl()).glDeleteProgram(anyInt());
        assertFalse(shading.hasShadowPrograms());
        shading.delete();
    }

    @Test
    void stalePortsOwnBlocksAndMissingProgramsAllFallBack() {
        // A port from another port format is refused before anything compiles
        Map<AbsolutePackPath, String> stale = sources(false, true, Map.of());
        stale.computeIfPresent(path("/gbuffers_terrain.mesh"), (file, text) -> text.replace("port format " + MeshShaderTranslator.PORT_FORMAT, "port format 0"));
        UmbraMeshShading old = new UmbraMeshShading(new ShaderPack(stale), CONFIG);
        assertFalse(old.bindOpaque(MeshDrawPhase.MAIN, SOLID, MATRICES));
        assertTrue(UmbraMeshShading.getFailureReason().contains("another version of Impetus"));
        verify(TestGl.gl(), never()).glCreateShader(MESH_SHADER);

        // A program declaring a uniform block of its own cannot share unified uniform memory
        when(TestGl.gl().glGetProgrami(anyInt(), eq(UmbraMeshShading.ACTIVE_UNIFORM_BLOCKS))).thenReturn(3);
        UmbraMeshShading withBlocks = new UmbraMeshShading(pack(true, true, Map.of()), CONFIG);
        assertFalse(withBlocks.bindOpaque(MeshDrawPhase.MAIN, SOLID, MATRICES));
        assertTrue(UmbraMeshShading.getFailureReason().contains("uniform blocks"));

        // A pack with no terrain program at all has nothing to draw with
        UmbraMeshShading nothing = new UmbraMeshShading(new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "")), CONFIG);
        assertFalse(nothing.hasShadowPrograms());
        assertTrue(UmbraMeshShading.getFailureReason().contains("gbuffers_terrain"));

        assertThrows(UnsupportedProgramException.class, () -> UmbraMeshShading.meshThreads("x", "// " + "port format " + MeshShaderTranslator.PORT_FORMAT));
        assertEquals(16, UmbraMeshShading.meshThreads("x", MeshShaderTranslator.HEADER + "\n#define UMBRA_MESH_THREADS 16\n"));
    }

    @Test
    void solidQuadsSkipTheCutoutTestUnlessThePackSetsOne() {
        UmbraTerrainProgramOverride.PreparedProgram plain = UmbraTerrainProgramOverride.prepareMesh(pack(false, true, Map.of()), ProgramId.TerrainCutoutMip);
        String split = UmbraMeshShading.opaqueAlphaTest(plain);
        assertTrue(split.startsWith("    if (iris_AlphaCutoff > 0.0) {\n" + plain.alphaTestSnippet()));
        assertTrue(split.contains("} else {\n    }"));
        assertTrue(plain.mesh());
        // The loader pins the bridge inputs where the port recorded them
        assertTrue(plain.fragment("").contains("layout(location = 0) in umbra_v0 { flat float iris_AlphaCutoff; };"));

        // A pack's own alphaTest directive applies to every opaque quad alike
        UmbraTerrainProgramOverride.PreparedProgram directed = UmbraTerrainProgramOverride.prepareMesh(
                pack(false, true, Map.of("/shaders.properties", "alphaTest.gbuffers_terrain = GREATER 0.2")), ProgramId.TerrainCutoutMip);
        assertEquals(directed.alphaTestSnippet(), UmbraMeshShading.opaqueAlphaTest(directed));
        assertFalse(directed.hasAuxiliaryStages());
        assertNull(UmbraTerrainProgramOverride.prepareMesh(new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "")), ProgramId.TerrainCutoutMip));
    }
}
