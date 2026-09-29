package com.bdmajora.impetus.engine.impl.gl.shader;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformBlock;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat3v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat4v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloatArray;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix3f;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformMatrix4f;
import com.bdmajora.testing.TestGl;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShadersTest {
    @Test
    void shadersCompileAndReportFailures() {
        GlShader shader = new GlShader(ShaderType.VERTEX, "test.vsh", "void main() {}");
        assertEquals("test.vsh", shader.getName());
        assertTrue(shader.handle() > 0);
        Mockito.verify(TestGl.gl()).glShaderSourceSafe(shader.handle(), "void main() {}");
        int handle = shader.handle();
        shader.delete();
        Mockito.verify(TestGl.gl()).glDeleteShader(handle);
        Mockito.when(TestGl.gl().glGetShaderInfoLog(Mockito.anyInt(), Mockito.anyInt())).thenReturn("warning: something");
        Mockito.when(TestGl.gl().glGetShaderi(Mockito.anyInt(), Mockito.eq(0x8B81))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> new GlShader(ShaderType.FRAGMENT, "bad.fsh", "oops"));
        assertEquals("vsh", ShaderType.VERTEX.fileExtension);
        assertEquals(0x8B31, ShaderType.VERTEX.id);
    }

    @Test
    void programsLinkAndBindUniforms() {
        GlShader vertex = new GlShader(ShaderType.VERTEX, "v", "");
        Mockito.when(TestGl.gl().glGetUniformLocation(Mockito.anyInt(), Mockito.eq("u_Color"))).thenReturn(3);
        Mockito.when(TestGl.gl().glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"))).thenReturn(1);
        GlProgram<Object[]> program = GlProgram.builder("prog")
                .attachShader(vertex)
                .bindAttribute("a_Pos", 0)
                .bindFragmentData("fragColor", 0)
                .link(ctx -> new Object[]{
                        ctx.bindUniform("u_Color", GlUniformFloat4v::new),
                        ctx.bindUniformIfPresent("u_Missing", GlUniformInt::new),
                        ctx.bindUniformBlock("Block", 2),
                        ctx.bindUniformBlockIfPresent("Missing", 3)});
        Object[] iface = program.getInterface();
        assertNotNull(iface[0]);
        assertNull(iface[1]);
        assertNotNull(iface[2]);
        assertNull(iface[3]);
        Mockito.verify(TestGl.gl()).glUniformBlockBinding(program.handle(), 1, 2);
        program.bind();
        Mockito.verify(TestGl.gl()).glUseProgram(program.handle());
        program.unbind();
        Mockito.verify(TestGl.gl()).glUseProgram(0);
        assertThrows(NullPointerException.class, () -> program.bindUniform("nope", GlUniformInt::new));
        assertThrows(NullPointerException.class, () -> program.bindUniformBlock("nope", 1));
        GlMutableBuffer buffer = new GlMutableBuffer();
        ((GlUniformBlock) iface[2]).bindBuffer(buffer);
        Mockito.verify(TestGl.gl()).glBindBufferBase(0x8A11, 2, buffer.handle());
        int handle = program.handle();
        program.delete();
        Mockito.verify(TestGl.gl()).glDeleteProgram(handle);

        Mockito.when(TestGl.gl().glGetProgramInfoLog(Mockito.anyInt(), Mockito.anyInt())).thenReturn("link warning");
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> GlProgram.builder("broken").link(ctx -> null));
    }

    @Test
    void uniformsForwardToGl() {
        new GlUniformFloat(1).set(2.5f);
        Mockito.verify(TestGl.gl()).glUniform1f(1, 2.5f);
        new GlUniformInt(2).set(9);
        Mockito.verify(TestGl.gl()).glUniform1i(2, 9);
        new GlUniformFloat3v(3).set(new float[]{1, 2, 3});
        Mockito.verify(TestGl.gl()).glUniform3fv(3, new float[]{1, 2, 3});
        new GlUniformFloat3v(3).set(4, 5, 6);
        Mockito.verify(TestGl.gl()).glUniform3f(3, 4, 5, 6);
        assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat3v(3).set(new float[2]));
        new GlUniformFloat4v(4).set(new float[]{1, 2, 3, 4});
        Mockito.verify(TestGl.gl()).glUniform4fv(4, new float[]{1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class, () -> new GlUniformFloat4v(4).set(new float[3]));
        new GlUniformFloatArray(5).set(new float[]{7, 8});
        Mockito.verify(TestGl.gl()).glUniform1fv(Mockito.eq(5), Mockito.any(FloatBuffer.class));
        FloatBuffer direct = FloatBuffer.allocate(2);
        new GlUniformFloatArray(6).set(direct);
        Mockito.verify(TestGl.gl()).glUniform1fv(6, direct);
        new GlUniformMatrix3f(7).set(new Matrix3f());
        Mockito.verify(TestGl.gl()).glUniformMatrix3fv(Mockito.eq(7), Mockito.eq(false), Mockito.any(FloatBuffer.class));
        new GlUniformMatrix4f(8).set(new Matrix4f());
        Mockito.verify(TestGl.gl()).glUniformMatrix4fv(Mockito.eq(8), Mockito.eq(false), Mockito.any(FloatBuffer.class));
    }

    @Test
    void constantsAndParser() {
        ShaderConstants constants = ShaderConstants.builder()
                .add("A")
                .add("B", "2")
                .addAll(List.of("C"))
                .addAll(Map.of("D", "4"))
                .build();
        assertEquals(List.of("#define A", "#define B 2", "#define C", "#define D 4"), constants.getDefineStrings());
        assertTrue(ShaderConstants.EMPTY.getDefineStrings().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ShaderConstants.builder().add("A").add("A", "1"));

        String parsed = ShaderParser.parseShader("#version 150\n#import <impetus:lib.glsl>\nvoid main() {}", name -> {
            assertEquals("impetus:lib.glsl", name);
            return "float lib() { return 1.0; }";
        }, constants);
        assertEquals("#version 150\n#define A\n#define B 2\n#define C\n#define D 4\nfloat lib() { return 1.0; }\nvoid main() {}", parsed);
        assertThrows(IllegalArgumentException.class, () -> ShaderParser.parseShader("#import broken", n -> ""));
        new ShaderParser();
    }
}
