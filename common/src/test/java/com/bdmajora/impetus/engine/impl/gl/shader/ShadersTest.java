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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShadersTest {
    // Message of a negative uniform block binding point
    private static final String BAD_BINDING = "Uniform block binding must not be negative";

    // Links an empty program and clears the recorded calls
    private static GlProgram<Object> linkedProgram() {
        GlProgram<Object> program = GlProgram.builder("blocks").link(ctx -> null);
        Mockito.clearInvocations(TestGl.gl());

        return program;
    }

    @Test
    void vertexFormatsBindConsecutiveAttributeLocationsBeforeLinking() {
        var format = com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat.builder(16)
                .addElement("position", 0, com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat.FLOAT, 3, false, false)
                .addElement("color", 12, com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .build();
        Mockito.when(TestGl.gl().glGetProgrami(Mockito.anyInt(), Mockito.eq(0x8B82))).thenReturn(1);
        var program = GlProgram.builder("attributes").bindAttributes(format, 3).link(ctx -> null);
        Mockito.verify(TestGl.gl()).glBindAttribLocation(program.handle(), 3, "position");
        Mockito.verify(TestGl.gl()).glBindAttribLocation(program.handle(), 4, "color");
        program.delete();
    }

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

    // Rejects a negative binding point for a present or missing block before any GL call
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void uniformBlockRejectsANegativeBindingPointBeforeAnyGlCall(int binding) {
        Mockito.doReturn(1).when(TestGl.gl()).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlProgram<Object> program = linkedProgram();

        for (String name : new String[]{"Block", "Missing"}) {
            IllegalArgumentException optional = assertThrows(IllegalArgumentException.class, () -> program.bindUniformBlockIfPresent(name, binding));
            assertEquals(BAD_BINDING, optional.getMessage());
            IllegalArgumentException required = assertThrows(IllegalArgumentException.class, () -> program.bindUniformBlock(name, binding));
            assertEquals(BAD_BINDING, required.getMessage());
        }

        Mockito.verifyNoInteractions(TestGl.gl());
    }

    // Queries the block index then assigns every accepted binding point that the returned block binds at
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 35, 36, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void uniformBlockAssignsEveryAcceptedBindingPoint(int binding) {
        Mockito.doReturn(4).when(TestGl.gl()).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlProgram<Object> program = linkedProgram();

        GlUniformBlock block = program.bindUniformBlockIfPresent("Block", binding);
        assertNotNull(block);
        block.bindBuffer(buffer);

        InOrder order = Mockito.inOrder(TestGl.gl());
        order.verify(TestGl.gl()).glGetUniformBlockIndex(program.handle(), "Block");
        order.verify(TestGl.gl()).glUniformBlockBinding(program.handle(), 4, binding);
        order.verify(TestGl.gl()).glBindBufferBase(0x8A11, binding, buffer.handle());
        Mockito.verifyNoMoreInteractions(TestGl.gl());
    }

    // Returns null for every negative block index and assigns the binding point for every other index
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, Integer.MIN_VALUE, 0, 1, Integer.MAX_VALUE})
    void uniformBlockIndexDecidesPresence(int index) {
        Mockito.doReturn(index).when(TestGl.gl()).glGetUniformBlockIndex(Mockito.anyInt(), Mockito.eq("Block"));
        GlProgram<Object> program = linkedProgram();

        GlUniformBlock block = program.bindUniformBlockIfPresent("Block", 2);

        Mockito.verify(TestGl.gl()).glGetUniformBlockIndex(program.handle(), "Block");

        if (index < 0) {
            assertNull(block);
        } else {
            assertNotNull(block);
            Mockito.verify(TestGl.gl()).glUniformBlockBinding(program.handle(), index, 2);
        }

        Mockito.verifyNoMoreInteractions(TestGl.gl());
    }

    // Rejects a deleted program before any GL call
    @Test
    void uniformBlockRejectsADeletedProgram() {
        GlProgram<Object> program = linkedProgram();
        program.delete();
        Mockito.clearInvocations(TestGl.gl());

        assertThrows(IllegalStateException.class, () -> program.bindUniformBlockIfPresent("Block", 0));
        assertThrows(IllegalStateException.class, () -> program.bindUniformBlock("Block", 0));
        Mockito.verifyNoInteractions(TestGl.gl());
    }
}
