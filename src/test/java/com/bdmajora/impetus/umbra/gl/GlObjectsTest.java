package com.bdmajora.impetus.umbra.gl;

import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.program.ProgramCreationException;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderCompileException;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.gl.uniform.FloatUniform;
import com.bdmajora.impetus.umbra.gl.uniform.IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Matrix3Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.MatrixUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector2IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector2Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector3IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector3Uniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector4IntUniform;
import com.bdmajora.impetus.umbra.gl.uniform.Vector4Uniform;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlObjectsTest {
    // Uploads once, skips an unchanged value, uploads again when it moves
    private static void updateTwiceThenChange(Uniform uniform, Runnable change) {
        uniform.update();
        uniform.update();
        change.run();
        uniform.update();
    }

    @Test
    void scalarAndVectorUniformsOnlyUploadWhatMoved() {
        float[] f = {0};
        FloatUniform floatUniform = new FloatUniform(1, () -> f[0]);
        assertEquals(1, floatUniform.getLocation());
        updateTwiceThenChange(floatUniform, () -> f[0] = 2);
        verify(TestGl.gl()).glUniform1f(1, 0.0F);
        verify(TestGl.gl()).glUniform1f(1, 2.0F);

        int[] i = {0};
        updateTwiceThenChange(new IntUniform(2, () -> i[0]), () -> i[0] = 3);
        verify(TestGl.gl()).glUniform1i(2, 0);
        verify(TestGl.gl()).glUniform1i(2, 3);

        Vector2f v2 = new Vector2f();
        updateTwiceThenChange(new Vector2Uniform(3, () -> v2), () -> v2.set(1, 2));
        verify(TestGl.gl(), times(2)).glUniform2f(eq(3), anyFloat(), anyFloat());
        Vector3f v3 = new Vector3f();
        updateTwiceThenChange(new Vector3Uniform(4, () -> v3), () -> v3.set(1, 2, 3));
        verify(TestGl.gl(), times(2)).glUniform3f(eq(4), anyFloat(), anyFloat(), anyFloat());
        Vector4f v4 = new Vector4f();
        updateTwiceThenChange(new Vector4Uniform(5, () -> v4), () -> v4.set(1, 2, 3, 4));
        verify(TestGl.gl(), times(2)).glUniform4f(eq(5), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        Vector2i i2 = new Vector2i();
        updateTwiceThenChange(new Vector2IntUniform(6, () -> i2), () -> i2.set(1, 2));
        verify(TestGl.gl(), times(2)).glUniform2i(eq(6), anyInt(), anyInt());
        Vector3i i3 = new Vector3i();
        updateTwiceThenChange(new Vector3IntUniform(7, () -> i3), () -> i3.set(1, 2, 3));
        verify(TestGl.gl(), times(2)).glUniform3i(eq(7), anyInt(), anyInt(), anyInt());
        Vector4i i4 = new Vector4i();
        updateTwiceThenChange(new Vector4IntUniform(8, () -> i4), () -> i4.set(1, 2, 3, 4));
        verify(TestGl.gl(), times(2)).glUniform4i(eq(8), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void matricesUploadEveryTimeUnlessThereIsNone() {
        AtomicReference<Matrix4f> mat4 = new AtomicReference<>(new Matrix4f());
        MatrixUniform matrix = new MatrixUniform(9, mat4::get);
        matrix.update();
        matrix.update();
        mat4.set(null);
        matrix.update();
        verify(TestGl.gl(), times(2)).glUniformMatrix4fv(eq(9), eq(false), any(FloatBuffer.class));
        AtomicReference<Matrix3f> mat3 = new AtomicReference<>(new Matrix3f());
        Matrix3Uniform normal = new Matrix3Uniform(10, mat3::get);
        normal.update();
        mat3.set(null);
        normal.update();
        verify(TestGl.gl(), times(1)).glUniformMatrix3fv(eq(10), eq(false), any(FloatBuffer.class));
    }

    @Test
    void shadersCompileOrExplainWhy() {
        GlShader shader = new GlShader(ShaderType.VERTEX, "test.vsh", "void main() {}");
        assertEquals("test.vsh", shader.getName());
        int id = shader.getGlId();
        assertTrue(id > 0);
        assertFalse(shader.isDestroyed());
        shader.destroy();
        shader.destroy();
        assertTrue(shader.isDestroyed());
        verify(TestGl.gl(), times(1)).glDeleteShader(id);
        // A destroyed name may be recycled by the driver, so using it is an error
        assertThrows(IllegalStateException.class, shader::getGlId);

        when(TestGl.gl().glCreateShader(anyInt())).thenReturn(0);
        assertThrows(ShaderCompileException.class, () -> new GlShader(ShaderType.FRAGMENT, "a.fsh", ""));
        when(TestGl.gl().glCreateShader(anyInt())).thenReturn(42);
        when(TestGl.gl().glGetShaderi(42, GL20.GL_COMPILE_STATUS)).thenReturn(0);
        ShaderCompileException silent = assertThrows(ShaderCompileException.class, () -> new GlShader(ShaderType.GEOMETRY, "b.gsh", ""));
        assertEquals("b.gsh", silent.getFilename());
        assertEquals("(no info log)", silent.getError());
        when(TestGl.gl().glGetShaderInfoLog(42, 32768)).thenReturn("0:1: error\n");
        assertEquals("0:1: error", assertThrows(ShaderCompileException.class,
                () -> new GlShader(ShaderType.COMPUTE, "c.csh", "")).getError());
        verify(TestGl.gl(), times(2)).glDeleteShader(42);
        assertEquals(ShaderType.values().length, 6);
        assertEquals(GL20.GL_FRAGMENT_SHADER, ShaderType.valueOf("FRAGMENT").id);
    }

    @Test
    void programsLinkFromTheirStages() {
        GlShader vertex = new GlShader(ShaderType.VERTEX, "p.vsh", "void main() {}");
        GlShader fragment = new GlShader(ShaderType.FRAGMENT, "p.fsh", "void main() {}");
        GlProgram program = ProgramBuilder.begin("p")
                .attach(vertex)
                .attach(fragment)
                .bindAttributeLocation(10, "mc_Entity")
                .bindFragmentDataLocation(0, "outColor")
                .link();
        assertEquals("p", program.getName());
        verify(TestGl.gl()).glBindAttribLocation(program.getGlId(), 10, "mc_Entity");
        verify(TestGl.gl()).glBindFragDataLocation(program.getGlId(), 0, "outColor");
        verify(TestGl.gl()).glDetachShader(program.getGlId(), vertex.getGlId());
        program.bind();
        verify(TestGl.gl()).glUseProgram(program.getGlId());
        program.unbind();
        verify(TestGl.gl()).glUseProgram(0);
        assertEquals(-1, program.getUniformLocation("missing"));
        assertEquals(-1, program.getAttributeLocation("missing"));

        UmbraProgram wrapped = new UmbraProgram(program, null);
        assertArrayEquals(new int[] {0}, wrapped.getDrawBuffers());
        UmbraProgram routed = new UmbraProgram(program, new int[] {3, 1});
        int[] buffers = routed.getDrawBuffers();
        buffers[0] = 9;
        assertArrayEquals(new int[] {3, 1}, routed.getDrawBuffers());
        assertSame(program, routed.getProgram());
        routed.bind();
        routed.unbind();
        routed.destroy();
        verify(TestGl.gl()).glDeleteProgram(anyInt());
    }

    @Test
    void linkingCanFail() {
        when(TestGl.gl().glCreateProgram()).thenReturn(0);
        assertThrows(ProgramCreationException.class, () -> ProgramBuilder.begin("none"));
        when(TestGl.gl().glCreateProgram()).thenReturn(77);
        // A log mentioning an error is surfaced even on success
        when(TestGl.gl().glGetProgramInfoLog(77, 32768)).thenReturn("warning: error-ish\n");
        assertNotNull(ProgramBuilder.begin("noisy").link());
        when(TestGl.gl().glGetProgramInfoLog(77, 32768)).thenReturn("deprecation warning");
        assertNotNull(ProgramBuilder.begin("quiet").link());
        when(TestGl.gl().glGetProgrami(77, GL20.GL_LINK_STATUS)).thenReturn(0);
        GlShader destroyed = new GlShader(ShaderType.VERTEX, "d.vsh", "");
        GlShader kept = new GlShader(ShaderType.FRAGMENT, "k.fsh", "");
        ProgramBuilder failing = ProgramBuilder.begin("broken").attach(destroyed).attach(kept);
        int destroyedId = destroyed.getGlId();
        destroyed.destroy();
        assertTrue(assertThrows(ProgramCreationException.class, failing::link).getMessage().contains("warning"));
        when(TestGl.gl().glGetProgramInfoLog(77, 32768)).thenReturn("");
        assertTrue(assertThrows(ProgramCreationException.class, () -> ProgramBuilder.begin("silent").link())
                .getMessage().contains("(no info log)"));
        verify(TestGl.gl(), times(2)).glDeleteProgram(77);
        // A stage already deleted is not detached, the others are
        verify(TestGl.gl(), never()).glDetachShader(77, destroyedId);
        verify(TestGl.gl()).glDetachShader(77, kept.getGlId());
    }

    @Test
    void macrosNameTheHostAndTheGpu() {
        Map<String, String> standard = ShaderMacros.standard();
        assertEquals("11202", standard.get("MC_VERSION"));
        assertTrue(standard.keySet().stream().anyMatch(key -> key.startsWith("MC_OS_")));
        assertEquals("10805", standard.get("IRIS_VERSION"));
        String[][] gpus = {{"ATI Technologies", "MC_GL_VENDOR_ATI", "AMD Radeon", "MC_GL_RENDERER_RADEON"},
                {"Intel", "MC_GL_VENDOR_INTEL", "ati mobility", "MC_GL_RENDERER_RADEON"},
                {"NVIDIA Corporation", "MC_GL_VENDOR_NVIDIA", "Radeon Pro", "MC_GL_RENDERER_RADEON"},
                {"AMD", "MC_GL_VENDOR_AMD", "Gallium 0.4", "MC_GL_RENDERER_GALLIUM"},
                {"X.Org", "MC_GL_VENDOR_XORG", "Intel(R) UHD", "MC_GL_RENDERER_INTEL"},
                {"Apple", "MC_GL_VENDOR_OTHER", "GeForce RTX", "MC_GL_RENDERER_GEFORCE"},
                {null, "MC_GL_VENDOR_OTHER", "NVIDIA GeForce", "MC_GL_RENDERER_GEFORCE"},
                {"Mesa", "MC_GL_VENDOR_OTHER", "Quadro P1000", "MC_GL_RENDERER_QUADRO"},
                {"Mesa", "MC_GL_VENDOR_OTHER", "NVS 510", "MC_GL_RENDERER_QUADRO"},
                {"Mesa", "MC_GL_VENDOR_OTHER", "Mesa Intel(R) Arc(tm)", "MC_GL_RENDERER_MESA"},
                {"Apple", "MC_GL_VENDOR_OTHER", "Apple M1", "MC_GL_RENDERER_APPLE"},
                {"Apple", "MC_GL_VENDOR_OTHER", "llvmpipe", "MC_GL_RENDERER_OTHER"},
                {"Apple", "MC_GL_VENDOR_OTHER", null, "MC_GL_RENDERER_OTHER"}};
        for (String[] gpu : gpus) {
            Map<String, String> macros = new LinkedHashMap<>();
            ShaderMacros.withGlInfo(macros, 460, 46, gpu[0], gpu[2]);
            assertEquals("460", macros.get("MC_GLSL_VERSION"));
            assertEquals("46", macros.get("MC_GL_VERSION"));
            assertTrue(macros.containsKey(gpu[1]), gpu[0] + " " + macros);
            assertTrue(macros.containsKey(gpu[3]), gpu[2] + " " + macros);
        }
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("FLAG", "");
        defines.put("VALUE", "2");
        assertEquals("#version 330\n#define FLAG\n#define VALUE 2\nvoid main() {}",
                ShaderMacros.injectDefines("#version 330\n#version 120\nvoid main() {}", defines));
        assertEquals("#define FLAG\n#define VALUE 2\nvoid main() {}", ShaderMacros.injectDefines("void main() {}", defines));
        assertSame(standard, ShaderMacros.forProgram(standard, null));
    }

    @Test
    void theOperatingSystemMacroFollowsTheJvm() throws Exception {
        String os = System.getProperty("os.name");
        java.lang.reflect.Method osMacro = ShaderMacros.class.getDeclaredMethod("osMacro");
        osMacro.setAccessible(true);
        try {
            String[][] cases = {{"Windows 11", "MC_OS_WINDOWS"}, {"Mac OS X", "MC_OS_MAC"}, {"Darwin", "MC_OS_MAC"},
                    {"Linux", "MC_OS_LINUX"}, {"SunOS unix", "MC_OS_LINUX"}, {"Haiku", "MC_OS_OTHER"}};
            for (String[] entry : cases) {
                System.setProperty("os.name", entry[0]);
                assertEquals(entry[1], osMacro.invoke(null), entry[0]);
            }
        } finally {
            System.setProperty("os.name", os);
        }
    }
}
