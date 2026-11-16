package com.bdmajora.impetus.engine.impl.gl.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.NVMeshShader;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests stage enums and file extensions.
class ShaderTypeTest {
    // Maps each stage to its GL enum and extension.
    @Test
    void mappings() {
        assertEquals(GL20.GL_VERTEX_SHADER, ShaderType.VERTEX.id);
        assertEquals(GL20.GL_FRAGMENT_SHADER, ShaderType.FRAGMENT.id);
        assertEquals(GL32.GL_GEOMETRY_SHADER, ShaderType.GEOM.id);
        assertEquals(GL42.GL_TESS_CONTROL_SHADER, ShaderType.TESS_CTRL.id);
        assertEquals(GL42.GL_TESS_EVALUATION_SHADER, ShaderType.TESS_EVALUATE.id);
        assertEquals(GL43.GL_COMPUTE_SHADER, ShaderType.COMPUTE.id);
        assertEquals(NVMeshShader.GL_TASK_SHADER_NV, ShaderType.TASK.id);
        assertEquals(NVMeshShader.GL_MESH_SHADER_NV, ShaderType.MESH.id);
        assertEquals("vsh", ShaderType.VERTEX.fileExtension);
        assertEquals("fsh", ShaderType.FRAGMENT.fileExtension);
        assertEquals("gsh", ShaderType.GEOM.fileExtension);
        assertEquals("tcs", ShaderType.TESS_CTRL.fileExtension);
        assertEquals("tes", ShaderType.TESS_EVALUATE.fileExtension);
        assertEquals("csh", ShaderType.COMPUTE.fileExtension);
        assertEquals("task", ShaderType.TASK.fileExtension);
        assertEquals("mesh", ShaderType.MESH.fileExtension);
    }

    // Keeps ids and extensions unique and non empty.
    @Test
    void uniqueValues() {
        Set<Integer> ids = new HashSet<>();
        Set<String> extensions = new HashSet<>();
        for (ShaderType type : ShaderType.values()) {
            assertTrue(ids.add(type.id), type.name());
            assertTrue(extensions.add(type.fileExtension), type.name());
            assertFalse(type.fileExtension.isEmpty());
            assertSame(type, ShaderType.valueOf(type.name()));
        }
        assertEquals(8, ShaderType.values().length);
    }

    // Rejects unknown names.
    @Test
    void unknownNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShaderType.valueOf("vertex"));
        assertThrows(NullPointerException.class, () -> ShaderType.valueOf(null));
    }
}
