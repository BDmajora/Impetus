package com.bdmajora.impetus.umbra.uniforms.custom;

import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.testing.Mixins;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.joml.Vector4i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomUniformsTest {
    @AfterEach
    void clearActive() {
        ActiveCustomUniforms.clear();
    }

    private static CustomUniforms.Builder declarations() {
        CustomUniforms.Builder builder = new CustomUniforms.Builder();
        assertTrue(builder.isEmpty());
        builder.addVariable("float", "base", "2", false);
        // An unknown type and a second definition are both dropped
        builder.addVariable("mat4", "skipped", "1", true);
        builder.addVariable("float", "base", "3", false);
        builder.addVariable("int", "count", "base * 2 + 0.7", true);
        builder.addVariable("bool", "flag", "base > 1", true);
        builder.addVariable("float", "scaled", "smooth(base * 0.5)", true);
        builder.addVariable("vec2", "pair", "base", true);
        builder.addVariable("vec3", "triple", "vec4(1, 2, 3, 4)", true);
        builder.addVariable("VEC4", "quad", "vec2(1, 2)", true);
        builder.addVariable("ivec2", "ipair", "vec2(5, 6)", false);
        builder.addVariable("ivec3", "itriple", "1", false);
        builder.addVariable("ivec4", "iquad", "1", false);
        builder.addVariable("float", "self", "self + 1", true);
        builder.addVariable("vec2", "mismatch", "vec2(1, 2) + vec3(1, 2, 3)", true);
        builder.addVariable("float", "broken", "(", true);
        assertFalse(builder.isEmpty());
        return builder;
    }

    @Test
    void declarationsEvaluateInOrderAndOnlyUniformsUpload() {
        CustomUniforms uniforms = declarations().build();
        assertFalse(uniforms.isEmpty());
        // The unparseable one is dropped, everything else is kept
        assertEquals(12, uniforms.size());
        uniforms.update();
        // A failing expression keeps its last value and warns only once
        uniforms.update();
        Map<String, String> snapshot = uniforms.snapshot();
        assertEquals("2.0", snapshot.get("variable.base"));
        assertEquals("4.7", snapshot.get("uniform.count"));
        assertEquals("1.0", snapshot.get("uniform.flag"));
        assertEquals("1.0", snapshot.get("uniform.scaled"));
        assertEquals("2.0, 2.0", snapshot.get("uniform.pair"));
        assertEquals("1.0, 2.0, 3.0", snapshot.get("uniform.triple"));
        assertEquals("1.0, 2.0, 0.0, 0.0", snapshot.get("uniform.quad"));
        // A name that is its own expression reads the built-in table, which has nothing by that name
        assertEquals("1.0", snapshot.get("uniform.self"));
        assertEquals("0.0, 0.0", snapshot.get("uniform.mismatch"));
        assertEquals("1.0, 1.0, 1.0, 1.0", snapshot.get("variable.iquad"));

        CustomUniformInputs uploaded = new CustomUniformInputs();
        uniforms.assignTo(uploaded);
        assertEquals(4.0F, uploaded.resolve("count").x());
        assertEquals(1.0F, uploaded.resolve("flag").x());
        assertEquals(1.0F, uploaded.resolve("scaled").x());
        assertEquals("vec2(2.0, 2.0)", uploaded.resolve("pair").toString());
        assertEquals("vec3(1.0, 2.0, 3.0)", uploaded.resolve("triple").toString());
        assertEquals("vec4(1.0, 2.0, 0.0, 0.0)", uploaded.resolve("quad").toString());
        assertFalse(uploaded.has("base"));
        assertFalse(uploaded.has("ipair"));
    }

    @Test
    void theActiveSetIsOptional() {
        ActiveCustomUniforms.update();
        ActiveCustomUniforms.assignTo(new CustomUniformInputs());
        assertTrue(ActiveCustomUniforms.snapshot().isEmpty());
        ActiveCustomUniforms.set(declarations().build());
        ActiveCustomUniforms.update();
        CustomUniformInputs uploaded = new CustomUniformInputs();
        ActiveCustomUniforms.assignTo(uploaded);
        assertTrue(uploaded.has("count"));
        assertEquals("2.0", ActiveCustomUniforms.snapshot().get("variable.base"));
    }

    @Test
    void pendingDeclarationsAreValueRecords() throws Exception {
        CustomUniforms.Builder builder = new CustomUniforms.Builder();
        builder.addVariable("float", "a", "1", true);
        Map<String, ?> pending = Mixins.get(builder, "pending");
        Object record = pending.get("a");
        Class<?> type = record.getClass();
        for (String accessor : new String[] {"name", "type", "expression", "isUniform"}) {
            Method method = type.getDeclaredMethod(accessor);
            method.setAccessible(true);
            assertNotNull(method.invoke(record), accessor);
        }
        assertEquals(record, record);
        assertNotEquals(record, null);
        assertEquals(record.hashCode(), record.hashCode());
        assertTrue(record.toString().contains("a"));
    }

    @Test
    void theInputTableReadsEveryUniformShape() {
        CustomUniformInputs inputs = new CustomUniformInputs();
        inputs.uniform1f(UniformUpdateFrequency.PER_FRAME, "f", () -> 1.5F)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "i", () -> 3)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "v2", () -> new org.joml.Vector2f(1, 2))
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "iv2", () -> new Vector2i(3, 4))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "v3", () -> new org.joml.Vector3f(1, 2, 3))
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "iv3", () -> new Vector3i(4, 5, 6))
                .uniform4f(UniformUpdateFrequency.PER_FRAME, "v4", () -> new org.joml.Vector4f(1, 2, 3, 4))
                .uniform4i(UniformUpdateFrequency.PER_FRAME, "iv4", () -> new Vector4i(5, 6, 7, 8))
                .uniformMatrix3(UniformUpdateFrequency.PER_FRAME, "m3", () -> new Matrix3f().m10(9))
                .uniformMatrix3(UniformUpdateFrequency.PER_FRAME, "noM3", () -> null)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "m4", () -> new Matrix4f().m31(7))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "noM4", () -> null);
        assertEquals(1.5F, inputs.resolve("f").x());
        assertEquals(3.0F, inputs.resolve("i").x());
        assertEquals("vec2(1.0, 2.0)", inputs.resolve("v2").toString());
        assertEquals("vec2(3.0, 4.0)", inputs.resolve("iv2").toString());
        assertEquals("vec3(1.0, 2.0, 3.0)", inputs.resolve("v3").toString());
        assertEquals("vec3(4.0, 5.0, 6.0)", inputs.resolve("iv3").toString());
        assertEquals("vec4(1.0, 2.0, 3.0, 4.0)", inputs.resolve("v4").toString());
        assertEquals("vec4(5.0, 6.0, 7.0, 8.0)", inputs.resolve("iv4").toString());
        // Matrices are read one cell at a time, column then row
        assertEquals(9.0F, inputs.resolve("m3.1.0").x());
        assertEquals(1.0F, inputs.resolve("m3.2.2").x());
        assertEquals(0.0F, inputs.resolve("noM3.0.0").x());
        assertEquals(7.0F, inputs.resolve("m4.3.1").x());
        assertEquals(0.0F, inputs.resolve("noM4.0.0").x());
        assertNull(inputs.resolve("absent"));
        assertFalse(inputs.has("m4"));
    }
}
