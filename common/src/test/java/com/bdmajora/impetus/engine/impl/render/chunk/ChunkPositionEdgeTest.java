package com.bdmajora.impetus.engine.impl.render.chunk;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

// Tests index packing and update promotion and matrix construction.
class ChunkPositionEdgeTest {
    // Section used by the tests.
    private static final class Section extends AbstractSection {
        // Creates a section.
        Section(int x, int y, int z) {
            super(x, y, z);
        }
    }

    // Round trips every coordinate of a region and keeps indexes unique.
    @Test
    void localIndexRoundTrip() {
        boolean[] seen = new boolean[256];
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 8; z++) {
                    int index = LocalSectionIndex.pack(x, y, z);
                    assertTrue(index >= 0 && index < 256);
                    assertFalse(seen[index]);
                    seen[index] = true;
                    assertEquals(x, LocalSectionIndex.unpackX(index));
                    assertEquals(y, LocalSectionIndex.unpackY(index));
                    assertEquals(z, LocalSectionIndex.unpackZ(index));
                }
            }
        }
    }

    // Wraps coordinates outside a region and negative coordinates.
    @Test
    void localIndexWraps() {
        assertEquals(LocalSectionIndex.pack(0, 0, 0), LocalSectionIndex.pack(8, 4, 8));
        assertEquals(LocalSectionIndex.pack(7, 3, 7), LocalSectionIndex.pack(-1, -1, -1));
        assertEquals(7, LocalSectionIndex.unpackX(LocalSectionIndex.pack(Integer.MIN_VALUE + 7, 0, 0)));
    }

    // Derives block positions and distances from section coordinates.
    @Test
    void sectionGeometry() {
        Section section = new Section(-1, 2, 3);
        assertEquals(-16, section.getOriginX());
        assertEquals(32, section.getOriginY());
        assertEquals(48, section.getOriginZ());
        assertEquals(-8, section.getCenterX());
        assertEquals(40, section.getCenterY());
        assertEquals(56, section.getCenterZ());
        assertEquals(0f, section.getSquaredDistance(-8, 40, 56));
        assertEquals(3f, section.getSquaredDistance(-7, 41, 57));
        assertEquals(0.75f, section.getSquaredDistanceFromBlockCenter(-9, 39, 55), 1e-3);
        assertTrue(section.isAlignedWithSectionOnGrid(-1, 9, 9));
        assertTrue(section.isAlignedWithSectionOnGrid(9, 2, 9));
        assertTrue(section.isAlignedWithSectionOnGrid(9, 9, 3));
        assertFalse(section.isAlignedWithSectionOnGrid(9, 9, 9));
        assertEquals(LocalSectionIndex.pack(-1, 2, 3), section.getSectionIndex());
        assertTrue(section.toString().contains("(-1, 2, 3)"));
        assertTrue(section.toString().contains("(-16, 32, 48)"));
        assertNotEquals(section.positionAsLong(), new Section(1, 2, 3).positionAsLong());
    }

    // Keeps the origin of the farthest supported sections exact.
    @Test
    void largeCoordinates() {
        Section far = new Section(1_000_000, -64, -1_000_000);
        assertEquals(16_000_000, far.getOriginX());
        assertEquals(-16_000_000, far.getOriginZ());
        assertEquals(0f, far.getSquaredDistance(16_000_008, -1016, -15_999_992), 1e-3);
    }

    // Promotes updates by their rules and never repeats one.
    @Test
    void promotionTable() {
        ChunkUpdateType[] all = ChunkUpdateType.VALUES;
        for (ChunkUpdateType type : all) {
            assertNull(ChunkUpdateType.getPromotionUpdateType(type, type));
            assertEquals(type, ChunkUpdateType.getPromotionUpdateType(null, type));
            if (type != ChunkUpdateType.SORT) {
                assertEquals(type, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.SORT, type));
            }
        }
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_REBUILD));
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.IMPORTANT_SORT, ChunkUpdateType.REBUILD));
        assertEquals(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_SORT));
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.REBUILD, ChunkUpdateType.SORT));
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.IMPORTANT_REBUILD, ChunkUpdateType.REBUILD));
        assertNull(ChunkUpdateType.getPromotionUpdateType(ChunkUpdateType.INITIAL_BUILD, ChunkUpdateType.REBUILD));
    }

    // Classifies every update type.
    @Test
    @SuppressWarnings("deprecation")
    void typeFlags() {
        assertTrue(ChunkUpdateType.IMPORTANT_SORT.isImportant());
        assertTrue(ChunkUpdateType.IMPORTANT_REBUILD.isImportant());
        assertFalse(ChunkUpdateType.REBUILD.isImportant());
        assertTrue(ChunkUpdateType.SORT.isSort());
        assertTrue(ChunkUpdateType.IMPORTANT_SORT.isSort());
        assertFalse(ChunkUpdateType.INITIAL_BUILD.isSort());
        assertTrue(ChunkUpdateType.canPromote(null, ChunkUpdateType.SORT));
        assertTrue(ChunkUpdateType.canPromote(ChunkUpdateType.REBUILD, ChunkUpdateType.IMPORTANT_REBUILD));
        assertFalse(ChunkUpdateType.canPromote(ChunkUpdateType.SORT, ChunkUpdateType.REBUILD));
        assertTrue(ChunkUpdateType.VALUES.length <= 7);
    }

    // Rejects null matrices and copies buffers without consuming them.
    @Test
    void matrices() {
        assertThrows(NullPointerException.class, () -> new ChunkRenderMatrices((org.joml.Matrix4fc) null, new Matrix4f()));
        assertThrows(NullPointerException.class, () -> new ChunkRenderMatrices(new Matrix4f(), (org.joml.Matrix4fc) null));
        FloatBuffer buffer = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        new Matrix4f().scale(2).get(buffer);
        FloatBuffer other = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(buffer, other);
        assertEquals(0, buffer.position());
        assertEquals(2f, matrices.projection().m00());
        FloatBuffer small = java.nio.ByteBuffer.allocateDirect(60).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        assertThrows(java.nio.BufferUnderflowException.class, () -> new ChunkRenderMatrices(small, other));
        assertThrows(java.nio.BufferUnderflowException.class, () -> new ChunkRenderMatrices(buffer, small));
        buffer.position(1);
        assertThrows(java.nio.BufferUnderflowException.class, () -> new ChunkRenderMatrices(buffer, other));
    }
}
