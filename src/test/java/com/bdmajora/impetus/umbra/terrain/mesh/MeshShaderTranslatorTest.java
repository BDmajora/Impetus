package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.terrain.ImpetusTerrainTransformer;
import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declaration;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.Port;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.UnsupportedProgramException;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.Varying;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeshShaderTranslatorTest {
    private static final String MODERN_VERTEX = String.join("\n",
            "#version 330 compatibility",
            "in vec4 mc_Entity;",
            "out vec2 texcoord;",
            "out vec2 lmcoord;",
            "flat out int material;",
            "#ifdef WAVES",
            "out vec4 glcolor;",
            "#endif",
            "uniform sampler2D noisetex;",
            "vec3 wave(in vec3 position, out float strength) { strength = 1.0; return position; }",
            "void main() {",
            "    float strength;",
            "    vec4 position = gl_Vertex;",
            "    position.xyz = wave(position.xyz, strength);",
            "    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * position);",
            "    texcoord = gl_MultiTexCoord0.xy;",
            "    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;",
            "    material = int(mc_Entity.x);",
            "#ifdef WAVES",
            "    glcolor = gl_Color;",
            "#endif",
            "    gl_FrontColor = gl_Color;",
            "}");

    private static final String MODERN_FRAGMENT = String.join("\n",
            "#version 330 compatibility",
            "/* DRAWBUFFERS:0 */",
            "in vec2 texcoord;",
            "layout(location = 5) in vec2 lmcoord;",
            "flat in int material;",
            "#ifdef WAVES",
            "in vec4 glcolor;",
            "#else",
            "in vec4 glcolor;",
            "#endif",
            "in mat3 tbn;",
            "in vec3 vectors[2];",
            "uniform sampler2D gtexture;",
            "void main() { gl_FragData[0] = texture(gtexture, texcoord) * gl_Color; }");

    private static final String LEGACY_VERTEX = String.join("\n",
            "#version 120",
            "varying vec2 texcoord;",
            "varying vec4 sample;",
            "void main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; sample = gl_Color; gl_TexCoord[0] = gl_MultiTexCoord0; }");

    private static final String LEGACY_FRAGMENT = String.join("\n",
            "#version 120",
            "varying vec2 texcoord;",
            "varying vec4 sample;",
            "void main() { gl_FragColor = sample * gl_TexCoord[0] * gl_FogFragCoord; }");

    private static String modernVertex() {
        return ImpetusTerrainTransformer.transformVertexShaderModern(MODERN_VERTEX);
    }

    private static Varying find(List<Varying> varyings, String name) {
        return varyings.stream().filter(varying -> varying.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void locationsComeFromTheFragmentDeclarationsInEveryBranch() {
        List<Varying> varyings = MeshShaderTranslator.assignLocations(MODERN_FRAGMENT);
        // The bridge's alpha cutoff always leads; fog and texture coordinates only when the fragment stage reads them
        assertEquals(new Varying("iris_AlphaCutoff", "float", "", 0, 1, "flat "), varyings.get(0));
        assertTrue(varyings.stream().noneMatch(varying -> varying.name().equals("iris_FogFragCoord")));
        assertTrue(varyings.stream().noneMatch(varying -> varying.name().equals("iris_TexCoordArr")));
        assertEquals(new Varying("gl_Color", "vec4", "", 1, 1, ""), varyings.get(1));
        assertEquals(new Varying("texcoord", "vec2", "", 2, 1, ""), varyings.get(2));
        assertEquals(3, find(varyings, "lmcoord").location());
        assertEquals("flat ", find(varyings, "material").interpolation());
        // A name declared in two branches takes one location
        assertEquals(1, varyings.stream().filter(varying -> varying.name().equals("glcolor")).count());
        assertEquals(new Varying("tbn", "mat3", "", 6, 3, ""), find(varyings, "tbn"));
        assertEquals(new Varying("vectors", "vec3", "[2]", 9, 2, ""), find(varyings, "vectors"));
        // Sampling the atlas adds the quad's mipmap flag after everything else
        assertEquals(new Varying("umbra_Mipped", "float", "", 11, 1, "flat "), varyings.getLast());

        List<Varying> legacy = MeshShaderTranslator.assignLocations(LEGACY_FRAGMENT);
        assertEquals(List.of("iris_AlphaCutoff", "iris_FogFragCoord", "iris_TexCoordArr", "texcoord", "sample"),
                legacy.stream().map(Varying::name).toList());
        assertEquals(new Varying("iris_TexCoordArr", "vec4", "[4]", 2, 4, ""), legacy.get(2));
        assertEquals(6, legacy.get(3).location());
    }

    @Test
    void shapesAPortCannotPinAreRefused() {
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.assignLocations("in Block { vec2 uv; } fs_in;"));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.assignLocations("in Light light;"));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.assignLocations("in vec4 samples[COUNT];"));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.assignLocations("in vec4 samples[];"));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.assignLocations("in vec4 everything[40];"));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.meshShader("void main() {}", List.of(), 32));
        assertThrows(UnsupportedProgramException.class, () -> MeshShaderTranslator.vertexGlobal(
                GlslDeclarations.parse("in Block { vec3 a; } b;"), Map.of()));
        assertEquals(6, MeshShaderTranslator.arrayLength("x", "[2][3]"));
        assertEquals(1, MeshShaderTranslator.arrayLength("x", ""));
    }

    @Test
    void heavyVaryingsHalveTheMeshlet() {
        assertEquals(MeshShaderTranslator.THREADS, MeshShaderTranslator.threadsFor(MeshShaderTranslator.assignLocations(MODERN_FRAGMENT)));
        assertEquals(MeshShaderTranslator.LIGHT_THREADS, MeshShaderTranslator.threadsFor(
                MeshShaderTranslator.assignLocations("in vec4 big[30];")));
    }

    @Test
    void thePortedMeshStageStandsAloneAndFeedsWhatItsBranchDeclares() {
        Port port = MeshShaderTranslator.port(modernVertex(), MODERN_FRAGMENT);
        String mesh = port.mesh();
        assertEquals(32, port.threads());

        assertTrue(mesh.startsWith("#version 460 compatibility\n" + MeshShaderTranslator.HEADER + "\n#extension GL_NV_mesh_shader : require\n"));
        assertEquals(1, mesh.split("#version").length - 1);
        assertTrue(mesh.contains("#define UMBRA_MESH_THREADS 32\nlayout(local_size_x = UMBRA_MESH_THREADS) in;\n"
                + "layout(triangles, max_vertices = UMBRA_MESH_THREADS, max_primitives = UMBRA_MESH_THREADS / 2) out;\n"));
        // Declared once, ahead of every output, since the per-vertex arrays take their size from it
        assertEquals(1, mesh.split("max_vertices").length - 1);
        assertTrue(mesh.contains("#ifdef UMBRA_TRANSLUCENT\n#define UMBRA_WRITABLE_TERRAIN 1\n#endif"));
        // Every template is inlined, both mesh mains behind the translucency switch
        assertFalse(mesh.contains("#import"));
        assertTrue(mesh.contains("uniform UmbraSceneData"));
        assertTrue(mesh.contains("void umbra_fetchVertex("));
        assertTrue(mesh.contains("void umbra_emitQuad("));
        assertTrue(mesh.contains("void umbra_swapWithNext("));
        assertTrue(mesh.contains("uint umbra_resolveQuad("));

        // The bridge's wrapper is a function now, and the pack's own main stays irisMain
        assertTrue(mesh.contains("void umbra_vertexMain()"));
        assertTrue(mesh.contains("void irisMain()"));

        // The only stage outputs left are the per-vertex blocks, each declared beside the global it copies, inside that global's own branch
        for (Declaration declaration : GlslDeclarations.collect(mesh)) {
            if (declaration.storage().equals("out") && !declaration.declarators().isEmpty()) {
                assertTrue(declaration.declarators().getFirst().name().startsWith("umbra_o"), declaration.text());
                assertTrue(declaration.declarators().getFirst().arraySuffix().startsWith("[]"), declaration.text());
            }
            assertNotEquals("varying", declaration.storage(), declaration.text());
        }
        assertTrue(mesh.contains("vec3 a_PosId = vec3(0);"));
        assertTrue(mesh.contains("#ifdef WAVES\nvec4 glcolor;\n#define UMBRA_FED_5 1\nlayout(location = 5) out umbra_v5 { vec4 glcolor; } umbra_o5[];\n"));
        assertTrue(mesh.contains("int material;\n#define UMBRA_FED_4 1\nlayout(location = 4) out umbra_v4 { flat int material; } umbra_o4[];"));
        assertTrue(mesh.contains("float iris_AlphaCutoff;\n#define UMBRA_FED_0 1\nlayout(location = 0) out umbra_v0 { flat float iris_AlphaCutoff; } umbra_o0[];"));
        assertTrue(mesh.contains("#ifdef UMBRA_FED_5\n    umbra_o5[lane].glcolor = glcolor;\n#endif\n"));
        // gl_Color always has its stand-in to read
        assertTrue(mesh.contains("layout(location = 1) out umbra_v1 { vec4 umbra_FrontColorIn; } umbra_o1[];\n"));
        assertTrue(mesh.contains("    umbra_o1[lane].umbra_FrontColorIn = umbra_FrontColor;\n"));
        // Nothing reads the declared-only inputs from the vertex stage, so no array is declared for them
        assertFalse(mesh.contains("UMBRA_FED_6 1"));
        // The mipmap flag the fetch decodes from the material is always fed
        assertTrue(mesh.contains("float umbra_Mipped = 1.0;"));
        assertTrue(mesh.contains("umbra_Mipped = float((v.a.y >> 16) & 1u);"));
        assertTrue(mesh.contains("layout(location = 11) out umbra_v11 { flat float umbra_Mipped; } umbra_o11[];\n"));
        assertTrue(mesh.contains("    umbra_o11[lane].umbra_Mipped = umbra_Mipped;\n"));
        // Each lane learns its section's age from the task stage before the pack's code runs
        assertTrue(mesh.contains("float umbra_SectionAgeMs = 0.0;"));
        assertTrue(mesh.contains("umbra_SectionAgeMs = umbra_Task.ageMs;\n    umbra_vertexMain();"));
        assertTrue(mesh.contains("#define gl_Position umbra_Position"));
        assertTrue(mesh.indexOf("#undef gl_Position") > mesh.indexOf("void umbra_vertexMain()"));
        assertTrue(mesh.indexOf("#undef gl_Position") < mesh.indexOf("void umbra_fetchVertex("));
    }

    @Test
    void olderPacksLoseNamesLaterVersionsReserved() {
        Port port = MeshShaderTranslator.port(ImpetusTerrainTransformer.transformVertexShader(LEGACY_VERTEX), LEGACY_FRAGMENT);
        assertTrue(port.mesh().startsWith("#version 460 core\n"));
        assertTrue(port.mesh().contains("vec4 umbra_sample;"));
        // The copy into the output array follows the rename
        assertTrue(port.mesh().contains("umbra_o7[lane].umbra_sample = umbra_sample;"));
        assertTrue(port.mesh().contains("umbra_o2[lane].iris_TexCoordArr = iris_TexCoordArr;"));
        // Block members match by name, so the fragment stage renames them alike
        assertTrue(port.fragment().contains("layout(location = 7) in umbra_v7 { vec4 umbra_sample; };"));
        assertTrue(port.fragment().contains("gl_FragColor = umbra_sample *"));

        assertEquals("float umbra_buffer; float umbra_shared;", MeshShaderTranslator.renameLaterKeywords("float buffer; float shared;"));
        String ssbo = "#extension GL_ARB_shader_storage_buffer_object : enable\nbuffer Data { float x; };";
        assertEquals(ssbo, MeshShaderTranslator.renameLaterKeywords(ssbo));

        // A pack already on 400 keeps its names, since they are its keywords too
        String modern = "#version 430 core\nout vec4 sample;\nvoid main() { sample = vec4(1.0); }";
        assertTrue(MeshShaderTranslator.meshShader(modern, List.of(), 32).contains("vec4 sample;"));
    }

    @Test
    void stageInterfaceOddmentsAreFoldedAway() {
        String vertex = String.join("\n",
                "#version 330 core",
                "invariant gl_Position;",
                "out gl_PerVertex { vec4 gl_Position; };",
                "out VertexData { flat int id; layout(location = 3) vec2 uv; } vs_out;",
                "out Loose { vec3 normal; };",
                "out vec3 init = vec3(1.0);",
                "attribute vec2 legacy[2];",
                "void main() { vs_out.uv = vec2(0.0); }");
        String mesh = MeshShaderTranslator.meshShader(vertex, List.of(), 32);
        assertFalse(mesh.contains("invariant gl_Position"));
        assertFalse(mesh.contains("gl_PerVertex"));
        assertTrue(mesh.contains("struct umbra_block_VertexData {"));
        assertTrue(mesh.contains("umbra_block_VertexData vs_out;"));
        assertFalse(mesh.contains("flat int id"));
        assertTrue(mesh.contains(" vec3 normal; "));
        assertTrue(mesh.contains("vec3 init = vec3 (1.0);"));
        assertTrue(mesh.contains("vec2 legacy[2];"));
    }

    @Test
    void theFragmentStagePinsEveryInputAndRecordsTheBridge() {
        Port port = MeshShaderTranslator.port(modernVertex(), MODERN_FRAGMENT);
        String fragment = port.fragment();
        assertTrue(fragment.startsWith("#version 330 compatibility\n" + MeshShaderTranslator.HEADER
                + "\n#extension GL_ARB_separate_shader_objects : enable\n/* NVIDIUM_BRIDGE: iris_AlphaCutoff=0 */\n"
                + "layout(location = 1) in umbra_v1 { vec4 umbra_FrontColorIn; };\n#define gl_Color umbra_FrontColorIn\n"));
        assertTrue(fragment.contains("layout(location = 2) in umbra_v2 { vec2 texcoord; };"));
        // The pack's own location gives way to the assigned one
        assertTrue(fragment.contains("layout(location = 3) in umbra_v3 { vec2 lmcoord; };"));
        assertFalse(fragment.contains("location = 5) in vec2"));
        assertTrue(fragment.contains("layout(location = 4) in umbra_v4 { flat int material; };"));
        // Both branches' declarations get the same location
        assertEquals(2, fragment.split("layout\\(location = 5\\) in umbra_v5 \\{ vec4 glcolor; \\};").length - 1);
        // Atlas lookups key on the quad's mipmap flag, declared in the header
        assertTrue(fragment.contains("layout(location = 11) in umbra_v11 { flat float umbra_Mipped; };\n" + AtlasSampling.BIAS_DEFINE));
        assertTrue(fragment.contains("texture(gtexture, texcoord, UMBRA_ATLAS_BIAS)"));

        // Without a version line the header goes on top; from 4.10 on locations need no extension
        String bare = MeshShaderTranslator.fragmentShader("varying vec4 c;", MeshShaderTranslator.assignLocations("varying vec4 c;"));
        assertTrue(bare.startsWith(MeshShaderTranslator.HEADER + "\n#extension GL_ARB_separate_shader_objects : enable\n"));
        assertTrue(bare.endsWith("layout(location = 1) in umbra_v1 { vec4 c; };"));
        String modern = MeshShaderTranslator.fragmentShader("#version 450\nin vec4 c;", MeshShaderTranslator.assignLocations("in vec4 c;"));
        assertFalse(modern.contains("separate_shader_objects"));
        assertNull(MeshShaderTranslator.fragmentInput(GlslDeclarations.parse("uniform vec2 size;"), Map.of()));
        assertNull(MeshShaderTranslator.fragmentInput(GlslDeclarations.parse("in Block { vec2 uv; } fs_in;"), Map.of()));
    }

    @Test
    void theLoaderPinsTheBridgeInputsWhereThePortPutThem() {
        Port port = MeshShaderTranslator.port(ImpetusTerrainTransformer.transformVertexShader(LEGACY_VERTEX), LEGACY_FRAGMENT);
        String transformed = ImpetusTerrainTransformer.transformFragmentShader(port.fragment(), DrawBuffers.DEFAULT);
        String pinned = MeshShaderTranslator.pinBridgeInputs(transformed);
        assertTrue(pinned.contains("layout(location = 0) in umbra_v0 { flat float iris_AlphaCutoff; };"));
        assertTrue(pinned.contains("layout(location = 1) in umbra_v1 { float iris_FogFragCoord; };"));
        assertTrue(pinned.contains("layout(location = 2) in umbra_v2 { vec4 iris_TexCoordArr[4]; };"));
        assertTrue(pinned.contains("layout(location = 6) in umbra_v6 { vec2 texcoord; };"));
        // A fragment stage that never came from a port is left as it is
        assertEquals(transformed.replace("NVIDIUM_BRIDGE", "X"), MeshShaderTranslator.pinBridgeInputs(transformed.replace("NVIDIUM_BRIDGE", "X")));
        assertEquals("/* NVIDIUM_BRIDGE: bad */", MeshShaderTranslator.pinBridgeInputs("/* NVIDIUM_BRIDGE: bad */"));
        // Input blocks need GLSL 1.50, so an older stage is lifted to it; a newer one and a bridge input that is not there are left alone
        assertEquals("#version 150 compatibility\n/* NVIDIUM_BRIDGE: iris_AlphaCutoff=0 */\nlayout(location = 0) in umbra_v0 { flat float iris_AlphaCutoff; };\n",
                MeshShaderTranslator.pinBridgeInputs("#version 130\n/* NVIDIUM_BRIDGE: iris_AlphaCutoff=0 */\nflat in float iris_AlphaCutoff;\n"));
        String newer = "#version 330 core\n/* NVIDIUM_BRIDGE: iris_FogFragCoord=1 */\nvoid main() {}\n";
        assertEquals(newer, MeshShaderTranslator.pinBridgeInputs(newer));
    }

    @Test
    void chunkFadeReadsTheSectionAgeAndTheScenesRate() {
        String vertex = "#version 330 compatibility\nout float fade;\nvoid main() { gl_Position = ftransform(); fade = mc_chunkFade; }";
        String bridged = ImpetusTerrainTransformer.transformVertexShaderModern(vertex);
        assertTrue(bridged.contains("iris_ChunkAgesMs[drawId]"));
        String mesh = MeshShaderTranslator.meshShader(bridged, MeshShaderTranslator.assignLocations("in float fade;"), 32);
        // The raster path's uniforms are gone, their reads now the lane's section age and the scene block's rate
        assertFalse(mesh.contains("iris_Chunk"));
        assertTrue(mesh.contains("clamp(umbra_SectionAgeMs * umbra_ChunkFadeInv, 0.0, 1.0)"));
        assertTrue(mesh.contains("float umbra_ChunkFadeInv;"));
    }

    @Test
    void preprocessorBranchesEachMarkThemselvesFed() {
        String vertex = "#version 330 core\n#ifdef A\nout vec3 a;\n#else\nout vec4 a;\n#endif\nvoid main() {}";
        List<Varying> varyings = MeshShaderTranslator.assignLocations("in vec3 a;");
        String mesh = MeshShaderTranslator.meshShader(vertex, varyings, 32);
        assertTrue(mesh.contains("#ifdef A\nvec3 a;\n#define UMBRA_FED_1 1\nlayout(location = 1) out umbra_v1 { vec3 a; } umbra_o1[];\n"));
        assertTrue(mesh.contains("#else\nvec4 a;\n#define UMBRA_FED_1 1\nlayout(location = 1) out umbra_v1 { vec4 a; } umbra_o1[];\n"));
    }
}
