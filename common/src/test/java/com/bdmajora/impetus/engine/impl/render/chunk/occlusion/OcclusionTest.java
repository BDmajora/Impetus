package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Sections;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OcclusionTest {
    private GLRenderDevice device;
    private RenderRegionManager regions;

    @BeforeEach
    void activate() {
        device = Devices.active();
        regions = new RenderRegionManager(device.createCommandList());
    }

    @AfterEach
    void deactivate() {
        regions.delete(device.createCommandList());
        device.makeInactive();
    }

    @Test
    void nodesTrackNeighboursAndWalkState() {
        RenderSection section = Sections.section(regions, 1, 2, 3);
        OcclusionNode node = new OcclusionNode(section);
        assertSame(section, node.getRenderSection());
        assertEquals(section.getRegion().getId(), node.getRenderRegionId());
        assertEquals(0, node.getAdjacentMask());
        OcclusionNode up = new OcclusionNode(Sections.section(regions, 1, 3, 3));
        for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
            node.setAdjacentNode(dir, up);
            assertSame(up, node.getAdjacent(dir));
        }
        assertEquals(GraphDirectionSet.ALL, node.getAdjacentMask());
        node.setAdjacentNode(GraphDirection.UP, null);
        assertNull(node.getAdjacent(GraphDirection.UP));
        assertNull(node.getAdjacent(99));
        node.setAdjacentNode(99, up);
        node.setLastVisibleFrame(4);
        assertEquals(4, node.getLastVisibleFrame());
        node.setIncomingDirections(1);
        node.addIncomingDirections(2);
        assertEquals(3, node.getIncomingDirections());
        node.setVisibilityData(5L);
        assertEquals(5L, node.getVisibilityData());
        assertEquals(3, AsyncOcclusionMode.values().length);
    }

    @Test
    void visibilityBuilderFloodsThroughOpenings() {
        SectionVisibilityBuilder open = new SectionVisibilityBuilder();
        assertEquals(VisibilityEncoding.EVERYTHING, open.computeVisibilityEncoding());
        SectionVisibilityBuilder solid = new SectionVisibilityBuilder();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    solid.markOpaque(x, y, z);
                }
            }
        }
        assertEquals(VisibilityEncoding.NULL, solid.computeVisibilityEncoding());
        // A floor at y=0 and a full wall across x=8 splits the section into a west half and an east half
        SectionVisibilityBuilder walled = new SectionVisibilityBuilder();
        for (int a = 0; a < 16; a++) {
            for (int b = 0; b < 16; b++) {
                walled.markOpaque(a, 0, b);
                walled.markOpaque(8, a, b);
            }
        }
        long encoding = walled.computeVisibilityEncoding();
        int fromWest = VisibilityEncoding.getConnections(encoding, GraphDirectionSet.of(GraphDirection.WEST));
        assertTrue(GraphDirectionSet.contains(fromWest, GraphDirection.UP));
        assertTrue(GraphDirectionSet.contains(fromWest, GraphDirection.NORTH));
        assertFalse(GraphDirectionSet.contains(fromWest, GraphDirection.EAST));
        assertFalse(GraphDirectionSet.contains(fromWest, GraphDirection.DOWN));
        int fromEast = VisibilityEncoding.getConnections(encoding, GraphDirectionSet.of(GraphDirection.EAST));
        assertFalse(GraphDirectionSet.contains(fromEast, GraphDirection.WEST));
    }

    @Test
    void sectionTreeInsertsSplitsAndVisits() {
        SectionTree tree = new SectionTree();
        assertTrue(tree.isEmpty());
        List<OcclusionNode> nodes = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                OcclusionNode node = new OcclusionNode(Sections.section(regions, x, 0, z));
                nodes.add(node);
                tree.add(node);
            }
        }
        tree.add(nodes.get(0));
        assertFalse(tree.isEmpty());
        List<OcclusionNode> seen = new ArrayList<>();
        tree.forEachVisible(Sections.viewport(0, 8, 0), 1000f, seen::add);
        assertEquals(nodes.size(), seen.size());
        seen.clear();
        tree.forEachVisible(Sections.viewport(0, 8, 0), 20f, seen::add);
        assertTrue(seen.size() < nodes.size() && !seen.isEmpty());
        seen.clear();
        tree.forEachVisible(new Viewport(Sections.NOTHING, new Vector3d(0, 8, 0)), 1000f, seen::add);
        assertTrue(seen.isEmpty());
        OcclusionNode far = new OcclusionNode(Sections.section(regions, 40, 3, -40));
        tree.add(far);
        tree.remove(far);
        tree.remove(far);
        tree.remove(new OcclusionNode(new RenderSection(nodes.get(0).getRenderSection().getRegion(), 100, 0, 100)));
        for (OcclusionNode node : nodes) {
            tree.remove(node);
        }
        assertTrue(tree.isEmpty());
        tree.remove(far);
        tree.add(far);
        tree.clear();
        assertTrue(tree.isEmpty());
        SectionTree small = new SectionTree();
        OcclusionNode lone = new OcclusionNode(Sections.section(regions, 50, 0, 50));
        small.add(lone);
        small.forEachVisible(Sections.viewport(800, 8, 800), 1000f, seen::add);
        assertEquals(List.of(lone), seen);
        small.remove(lone);
        assertTrue(small.isEmpty());
    }

    @Test
    void cullerWalksTheGraphFromTheCamera() {
        Long2ReferenceOpenHashMap<OcclusionNode> nodes = Sections.grid(regions, -2, 0, -2, 2, 1, 2);
        SectionTree tree = new SectionTree();
        nodes.values().forEach(tree::add);
        OcclusionCuller culler = new OcclusionCuller(nodes, tree, 0, 2);
        List<OcclusionNode> visited = new ArrayList<>();
        culler.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, 8, 8), 1000f, true, 1);
        assertEquals(nodes.size(), visited.size());

        // A closed section only lets the walk pass straight through, so with the camera inside it the whole grid is still reached from its own openings
        OcclusionNode centre = nodes.get(PositionUtil.packSection(0, 0, 0));
        centre.setVisibilityData(VisibilityEncoding.NULL);
        visited.clear();
        culler.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, 8, 8), 1000f, true, 2);
        assertEquals(1, visited.size());
        visited.clear();
        culler.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, 8, 8), 1000f, false, 3);
        assertEquals(nodes.size(), visited.size());
        centre.setVisibilityData(VisibilityEncoding.EVERYTHING);

        // From below, above and an unloaded column the walk seeds along the world edge
        visited.clear();
        culler.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, -50, 8), 100f, true, 4);
        assertFalse(visited.isEmpty());
        visited.clear();
        culler.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, 90, 8), 100f, true, 5);
        assertFalse(visited.isEmpty());
        OcclusionCuller strict = new OcclusionCuller(nodes, tree, -5, 5);
        visited.clear();
        strict.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, -50, 8), 100f, true, 6);
        assertFalse(visited.isEmpty());
        visited.clear();
        strict.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(8, 70, 8), 100f, true, 7);
        assertFalse(visited.isEmpty());
        visited.clear();
        strict.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(200, 8, 200), 100f, true, 8);
        assertTrue(visited.isEmpty());
        visited.clear();
        strict.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, Sections.viewport(-40, 8, 8), 1000f, true, 9);
        assertEquals(nodes.size(), visited.size());
        visited.clear();
        strict.findVisible((node, visible) -> {
            if (visible) visited.add(node);
        }, new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), 1000f, true, 10);
        assertEquals(1, visited.size());
        assertTrue(OcclusionCuller.isWithinFrustum(Sections.viewport(0, 0, 0), centre));
    }
}
