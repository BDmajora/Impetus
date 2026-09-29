package com.bdmajora.impetus.engine.impl.gl.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlVertexFormatTest {
    @Test
    void buildsAlignedLayouts() {
        GlVertexFormat format = GlVertexFormat.builder(16)
                .addElement("pos", 0, GlVertexAttributeFormat.SHORT, 3, false, false)
                .addElement("color", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
                .addElement("light", GlVertexFormat.NEXT_ALIGNED_POINTER, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
                .build();
        assertEquals(16, format.getStride());
        assertEquals(3, format.getAttributes().size());
        GlVertexAttribute color = format.getAttribute("color");
        assertEquals(8, color.getPointer());
        assertEquals(4, color.getSize());
        assertEquals(4, color.getCount());
        assertTrue(color.isNormalized());
        assertFalse(color.isIntType());
        assertEquals("color", color.getName());
        assertEquals(16, color.getStride());
        assertEquals(GlVertexAttributeFormat.UNSIGNED_BYTE, color.getFormat());
        assertTrue(format.getAttribute("light").isIntType());
        assertEquals(12, format.getAttribute("light").getPointer());
        assertEquals("GlVertexFormat{attributes=3,stride=16}", format.toString());
        assertThrows(NullPointerException.class, () -> format.getAttribute("missing"));
        assertThrows(UnsupportedOperationException.class, () -> format.getAttributes().clear());

        GlVertexFormat copy = GlVertexFormat.builder(20).addAllElements(format).addElement("extra", 16, GlVertexAttributeFormat.FLOAT, 1, false, false).build();
        assertEquals(4, copy.getAttributes().size());
        GlVertexAttributeBinding binding = new GlVertexAttributeBinding(2, color);
        assertEquals(2, binding.getIndex());
        assertEquals(color.getPointer(), binding.getPointer());
        assertEquals(4, GlVertexAttributeFormat.FLOAT.size());
        assertEquals(0x1406, GlVertexAttributeFormat.FLOAT.typeId());
    }

    @Test
    void rejectsBadLayouts() {
        assertThrows(IllegalArgumentException.class, () -> GlVertexFormat.builder(4).build());
        assertThrows(IllegalArgumentException.class, () -> GlVertexFormat.builder(4).addElement("a", 4, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertThrows(IllegalArgumentException.class, () -> GlVertexFormat.builder(4).addElement("a", 2, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertThrows(IllegalStateException.class, () -> GlVertexFormat.builder(8).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).addElement("a", 4, GlVertexAttributeFormat.FLOAT, 1, false, false));
        assertThrows(IllegalArgumentException.class, () -> GlVertexFormat.builder(8).addElement("a", 0, GlVertexAttributeFormat.FLOAT, 1, false, false).addElement("b", 2, GlVertexAttributeFormat.SHORT, 1, false, false).build());
    }
}
