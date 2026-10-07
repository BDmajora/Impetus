package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawBuffersTest {
    @AfterEach
    void forgetTheDriverQuery() {
        Mixins.set(DrawBuffers.class, "fragmentOutputArraySize", -1);
        CustomTextureTransformer.setActivePatches(null);
    }

    @Test
    void theLastActiveDirectiveWins() {
        assertArrayEquals(new int[] {0}, DrawBuffers.parseActive(null, Map.of()));
        assertArrayEquals(new int[] {0}, DrawBuffers.parseActive("void main() {}", Map.of()));
        assertArrayEquals(new int[] {0, 3, 6}, DrawBuffers.parseActive("/* DRAWBUFFERS:036 */", Map.of()));
        assertArrayEquals(new int[] {10, 2}, DrawBuffers.parseActive("/* RENDERTARGETS: 10, 2 */", Map.of()));
        // An unparsable or empty RENDERTARGETS is ignored
        assertArrayEquals(new int[] {1}, DrawBuffers.parseActive("/* DRAWBUFFERS:1 */\n/* RENDERTARGETS: 1,x */\n/* RENDERTARGETS: */", Map.of()));
        String gated = String.join("\n",
                "#ifdef WATER",
                "/* DRAWBUFFERS:01 */",
                "#else",
                "/* RENDERTARGETS: 0,4,5 */",
                "#endif");
        assertArrayEquals(new int[] {0, 4, 5}, DrawBuffers.parseActive(gated, Map.of()));
        assertArrayEquals(new int[] {0, 1}, DrawBuffers.parseActive(gated, Map.of("WATER", "")));
        // Without defines the standard macros decide, and a gate hiding every directive falls back to the raw last one
        assertArrayEquals(new int[] {0, 4, 5}, DrawBuffers.parseActive(gated, null));
        assertArrayEquals(new int[] {7}, DrawBuffers.parseActive("#if 0\n/* DRAWBUFFERS:7 */\n#endif", Map.of()));
    }

    @Test
    void sanitizingDropsBadAndRepeatedTargets() {
        int[] clean = {0, 1, 2};
        assertSame(clean, DrawBuffers.sanitize(clean, 16));
        assertArrayEquals(new int[] {0}, DrawBuffers.sanitize(null, 16));
        assertArrayEquals(new int[] {0}, DrawBuffers.sanitize(new int[0], 16));
        List<Integer> invalid = new ArrayList<>();
        assertArrayEquals(new int[] {3, 1}, DrawBuffers.sanitize(new int[] {3, -1, 3, 20, 1}, 16, invalid::add));
        assertEquals(List.of(-1, 20), invalid);
        assertArrayEquals(new int[] {0}, DrawBuffers.sanitize(new int[] {20, 30}, 16));
    }

    @Test
    void fragmentOutputsAreNormalisedToDenseSlots() {
        assertNull(DrawBuffers.rewriteFragmentOutputs(null, new int[] {0}));
        String source = String.join("\n",
                "#define OUT gl_FragColor",
                "#define gl_FragColor something",
                "#version 120",
                "out vec4 albedo;",
                "layout(location = 2) out vec4 normals;",
                "flat out highp vec3 material; // comment",
                "out vec4 iris_FragData0;",
                "void main() { gl_FragColor = vec4(1.0); }");
        String rewritten = DrawBuffers.rewriteFragmentOutputs(source, new int[] {0});
        assertTrue(rewritten.contains("#define OUT gl_FragData[0]"), rewritten);
        assertTrue(rewritten.contains("#define gl_FragColor something"), rewritten);
        assertTrue(rewritten.contains("gl_FragData[0] = vec4(1.0);"), rewritten);
        assertTrue(rewritten.contains("layout(location = 0) out vec4 albedo;"), rewritten);
        assertTrue(rewritten.contains("layout(location = 2) out vec4 normals;"), rewritten);
        // Implicit slots continue after the highest explicit one
        assertTrue(rewritten.contains("layout(location = 3) flat out highp vec3 material;"), rewritten);
        assertTrue(rewritten.contains("\nout vec4 iris_FragData0;"), rewritten);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 17; i++) {
            many.append("out vec4 o").append(i).append(";\n");
        }
        assertTrue(DrawBuffers.rewriteFragmentOutputs(many.toString(), new int[] {0}).contains("\nout vec4 o16;"));
    }

    @Test
    void theOutputArrayIsSizedByTheDriver() {
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(0);
        assertEquals(8, DrawBuffers.fragmentOutputArraySize());
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(32);
        assertEquals(8, DrawBuffers.fragmentOutputArraySize());
        forgetTheDriverQuery();
        assertEquals(16, DrawBuffers.fragmentOutputArraySize());
    }

    private static final String VERTEX = String.join("\n",
            "#version 120",
            "",
            "#extension GL_ARB_foo : enable",
            "// comment",
            "attribute vec4 mc_Entity;",
            "attribute vec2 mc_midTexCoord;",
            "attribute vec4 at_tangent;",
            "void main() { gl_TexCoord[1] = gl_MultiTexCoord1; }");

    @Test
    void gbufferProgramsArePatchedThenCompiled() {
        ProgramSource hand = new ProgramSource("gbuffers_hand", VERTEX, "// geometry", null, null,
                "/* DRAWBUFFERS:02 */\nvoid main() { gl_FragColor = vec4(1.0); }", null);
        ShaderProgramCompiler.PatchedSource patched = ShaderProgramCompiler.patchSource("gbuffers_hand", hand, Map.of("FLAG", ""));
        assertArrayEquals(new int[] {0, 2}, patched.drawBuffers);
        // The unfed tangent and mid-texture attributes become derived values, the hand reads its lightmap from a uniform
        assertTrue(patched.vertex.contains("iris_tangentFallback()"), patched.vertex);
        assertTrue(patched.vertex.contains("#define mc_midTexCoord gl_MultiTexCoord0.xy"), patched.vertex);
        assertTrue(patched.vertex.contains("uniform vec2 " + ShaderProgramCompiler.HAND_LIGHTMAP_UNIFORM + ";"), patched.vertex);
        assertTrue(patched.vertex.contains("vec4(impetus_HandLightmap, 0.0, 1.0)"), patched.vertex);
        assertTrue(patched.fragment.contains("#define FLAG"), patched.fragment);
        assertNotNull(patched.geometry);

        UmbraProgram program = ShaderProgramCompiler.compile("gbuffers_hand", hand, Map.of());
        assertArrayEquals(new int[] {0, 2}, program.getDrawBuffers());
        // The entity attribute is still declared after patching, so it keeps OptiFine's slot
        verify(TestGl.gl()).glBindAttribLocation(anyInt(), eq(10), eq("mc_Entity"));
        verify(TestGl.gl(), times(3)).glDeleteShader(anyInt());

        // A modern single-source program is versioned for the compatibility context, without a geometry stage
        String modernVertex = "#version 330 core\nin float mc_midTexCoord;\nvoid main() {}";
        ProgramSource modern = new ProgramSource("gbuffers_clouds", modernVertex, null, null, null,
                "#version 330 core\n/* RENDERTARGETS: 0 */\nout vec4 color;\nvoid main() { color = vec4(1.0); }", null);
        ShaderProgramCompiler.PatchedSource patchedModern = ShaderProgramCompiler.patchSource("gbuffers_clouds", modern, Map.of());
        assertNull(patchedModern.geometry);
        assertTrue(patchedModern.vertex.contains("#define mc_midTexCoord gl_MultiTexCoord0.x"), patchedModern.vertex);
        ProgramSource modernWithGeometry = new ProgramSource("gbuffers_basic", "#version 330\nin vec3 mc_midTexCoord;\nin vec4 mc_midTexCoord2;\nvoid main() {}",
                "#version 330\nvoid main() {}", null, null, "#version 330\nvoid main() {}", null);
        assertNotNull(ShaderProgramCompiler.patchSource("gbuffers_basic", modernWithGeometry, Map.of()).geometry);
        ProgramSource vec4Mid = new ProgramSource("gbuffers_hand_water", "attribute vec4 mc_midTexCoord;\nvoid main() {}",
                null, null, null, "void main() {}", null);
        ShaderProgramCompiler.PatchedSource water = ShaderProgramCompiler.patchSource("gbuffers_hand_water", vec4Mid, Map.of());
        // A hand program with no #version gets the default one, with the bridge right after it
        assertTrue(water.vertex.startsWith("#version 120"), water.vertex);
        assertTrue(water.vertex.contains("#define mc_midTexCoord gl_MultiTexCoord0\n"), water.vertex);
        verify(TestGl.gl(), never()).glBindAttribLocation(anyInt(), eq(12), eq("at_tangent"));

        // Tessellation needs both stages; a lone control stage is dropped, and a program reading mc_chunkFade outside terrain sees -1
        ProgramSource tessellated = new ProgramSource("gbuffers_entities", "#version 400 compatibility\nout float fade;\nvoid main() { fade = mc_chunkFade; }",
                null, "#version 400 compatibility\nlayout(vertices = 3) out;\nvoid main() {}",
                "#version 400 compatibility\nlayout(triangles) in;\nvoid main() {}", "#version 400 compatibility\nvoid main() {}", null);
        ShaderProgramCompiler.PatchedSource patchedTess = ShaderProgramCompiler.patchSource("gbuffers_entities", tessellated, Map.of());
        assertNotNull(patchedTess.tessControl);
        assertNotNull(patchedTess.tessEval);
        assertTrue(patchedTess.vertex.contains("#define mc_chunkFade (-1.0)"), patchedTess.vertex);
        assertTrue(patchedTess.tessEval.contains("#define mc_chunkFade (-1.0)"), patchedTess.tessEval);
        // A define the caller already supplies wins
        assertFalse(ShaderProgramCompiler.patchSource("gbuffers_entities", tessellated, Map.of("mc_chunkFade", "0.5")).vertex.contains("(-1.0)"));
        ProgramSource controlOnly = new ProgramSource("gbuffers_entities", "void main() {}", null, "void main() {}", null, "void main() {}", null);
        assertNull(ShaderProgramCompiler.patchSource("gbuffers_entities", controlOnly, Map.of()).tessControl);
        Mockito.clearInvocations(TestGl.gl());
        ShaderProgramCompiler.compile("gbuffers_entities", tessellated, Map.of());
        verify(TestGl.gl()).glCreateShader(0x8E88);
        verify(TestGl.gl()).glCreateShader(0x8E87);
        verify(TestGl.gl(), times(4)).glDeleteShader(anyInt());

        ProgramSource broken = new ProgramSource("gbuffers_basic", null, null, null, null, "void main() {}", null);
        assertThrows(ProgramCreationException.class, () -> ShaderProgramCompiler.patchSource("gbuffers_basic", broken, Map.of()));
        // A stage that fails to compile still frees the stages made before it
        when(TestGl.gl().glGetShaderi(anyInt(), eq(0x8B81))).thenReturn(1, 0);
        assertThrows(RuntimeException.class, () -> ShaderProgramCompiler.compile("gbuffers_hand", hand, Map.of()));
    }

    // Desugared records keep their generated members as ordinary methods
    private static void valueSemantics(Object a, Object b, Object other) {
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, other);
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.toString().isEmpty());
    }

    @Test
    void theSmallValueTypesBehaveAsValues() throws Exception {
        valueSemantics(new Uniform1iCall(1, 2), new Uniform1iCall(1, 2), new Uniform1iCall(1, 3));
        com.bdmajora.impetus.umbra.gl.blending.BlendMode mode = com.bdmajora.impetus.umbra.gl.blending.BlendMode.parse("ONE ZERO");
        valueSemantics(mode, com.bdmajora.impetus.umbra.gl.blending.BlendMode.parse("ONE ZERO"),
                com.bdmajora.impetus.umbra.gl.blending.BlendMode.parse("ZERO ONE"));
        Class<?> directive = Class.forName(DrawBuffers.class.getName() + "$Directive");
        int[] buffers = {1};
        Object first = Mixins.construct(directive, 0, buffers);
        valueSemantics(first, Mixins.construct(directive, 0, buffers), Mixins.construct(directive, 5, buffers));
        assertEquals(4, new com.bdmajora.impetus.umbra.gl.sampler.SamplerBinding(4, 0x0DE1, () -> 1).getTextureUnit());
    }
}
