package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisibilityEncodingTest {
    @Test
    void encodesFaceToFaceTables() {
        long upDown = VisibilityEncoding.encode((from, to) -> (from == GraphDirection.UP && to == GraphDirection.DOWN) || (from == GraphDirection.DOWN && to == GraphDirection.UP));
        assertEquals(GraphDirectionSet.of(GraphDirection.DOWN), VisibilityEncoding.getConnections(upDown, GraphDirectionSet.of(GraphDirection.UP)));
        assertEquals(GraphDirectionSet.of(GraphDirection.DOWN) | GraphDirectionSet.of(GraphDirection.UP), VisibilityEncoding.getConnections(upDown));
        assertEquals(GraphDirectionSet.ALL, VisibilityEncoding.getConnections(VisibilityEncoding.EVERYTHING));
        assertEquals(GraphDirectionSet.NONE, VisibilityEncoding.getConnections(VisibilityEncoding.NULL));
        String table = VisibilityEncoding.stringify(upDown);
        assertTrue(table.startsWith("  DUNSWE"));
        assertTrue(table.contains("x"));
        assertEquals(9, VisibilityEncoding.bit(1, 1));
        new VisibilityEncoding();
    }

    @Test
    void directionsHaveOppositesAndOffsets() {
        assertEquals(GraphDirection.UP, GraphDirection.opposite(GraphDirection.DOWN));
        assertEquals(GraphDirection.WEST, GraphDirection.opposite(GraphDirection.EAST));
        assertEquals(1, GraphDirection.x(GraphDirection.EAST));
        assertEquals(-1, GraphDirection.y(GraphDirection.DOWN));
        assertEquals(1, GraphDirection.z(GraphDirection.SOUTH));
        assertTrue(GraphDirectionSet.contains(GraphDirectionSet.ALL, GraphDirection.NORTH));
        assertFalse(GraphDirectionSet.contains(GraphDirectionSet.NONE, GraphDirection.NORTH));
        new GraphDirection();
        new GraphDirectionSet();
    }
}
