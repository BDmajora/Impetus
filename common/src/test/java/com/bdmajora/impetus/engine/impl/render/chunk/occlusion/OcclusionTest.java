package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Passes;
import com.bdmajora.testing.Sections;
import grondag.bitraster.PackedBox;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
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
        regions = new RenderRegionManager(device.createCommandList(), Passes.CONFIG);
    }

    @AfterEach
    void deactivate() {
        regions.delete(device.createCommandList());
        device.makeInactive();
    }

    // Records every reported section by position, split by whether it passed the visibility tests
    private static final class Seen implements OcclusionCuller.Visitor {
        final List<String> visible = new ArrayList<>();
        final List<String> hidden = new ArrayList<>();

        @Override
        public void visit(int latticeIndex, int regionId, int sectionIndex, int x, int y, int z, int meta, boolean passed) {
            (passed ? visible : hidden).add(x + "," + y + "," + z);
        }
    }

    private RenderSection section(int x, int y, int z) {
        return Sections.section(regions, x, y, z);
    }

    // Camera at the centre of a section
    private static Viewport at(int x, int y, int z) {
        return Sections.viewport(x * 16 + 8, y * 16 + 8, z * 16 + 8);
    }

    private Seen search(SectionLattice lattice, Viewport viewport, float distance, boolean occlusion, boolean clamping, int frame) {
        Seen seen = new Seen();
        lattice.findVisible(seen, viewport, distance, regions.getRegionIdsLength(), occlusion, clamping, frame);
        return seen;
    }

    @Test
    void visibilityBuilderFloodsThroughOpeningsAndBoxesItsOccluders() {
        SectionVisibilityBuilder open = new SectionVisibilityBuilder();
        assertEquals(VisibilityEncoding.EVERYTHING, open.computeVisibilityEncoding());
        assertNull(open.computeOccluderBoxes());
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
        // The flood fill works on a copy, so the opacity is still there to box afterwards
        int[] walls = walled.computeOccluderBoxes();
        assertNotNull(walls);
        assertTrue(walls.length > 1);
        assertEquals(PackedBox.FULL_BOX, SectionVisibilityBuilder.bounds(walls));

        // What a section draws is padded by a block and bounds its occluder data even with nothing opaque
        SectionVisibilityBuilder sparse = new SectionVisibilityBuilder();
        sparse.markRenderable(2, 3, 4);
        sparse.markRenderable(18, 3, 4);
        int[] bounds = sparse.computeOccluderBoxes();
        assertEquals(1, bounds.length);
        assertEquals(PackedBox.pack(1, 2, 3, 4, 5, 6, PackedBox.RANGE_EXTREME), SectionVisibilityBuilder.bounds(bounds));
        SectionVisibilityBuilder whole = new SectionVisibilityBuilder();
        whole.markSectionRenderable();
        assertEquals(PackedBox.FULL_BOX, SectionVisibilityBuilder.bounds(whole.computeOccluderBoxes()));
        assertEquals(3, AsyncOcclusionMode.values().length);
    }

    @Test
    void theLatticeKeepsEveryAttachedSectionButSearchesOnlyItsWindow() {
        SectionLattice lattice = new SectionLattice(0, 2, false, false);
        RenderSection origin = section(0, 0, 0);
        lattice.attach(origin);
        assertSame(origin, lattice.getRenderSection(0, 0, 0));
        assertNull(lattice.getRenderSection(0, 1, 0));
        assertThrows(IllegalStateException.class, () -> lattice.attach(origin));
        // Nothing is installed before the first window, so no metadata change can affect a search yet
        assertFalse(lattice.updateSectionMetadata(0, 0, 0, VisibilityEncoding.NULL));

        lattice.ensureWindowCovers(new Vector3i(0, 0, 0), 32f);
        RenderSection east = section(1, 0, 0);
        RenderSection far = section(40, 0, 0);
        lattice.attach(east);
        lattice.attach(far);
        Seen seen = search(lattice, at(0, 0, 0), 32f, true, true, 1);
        assertEquals(List.of("0,0,0", "1,0,0"), seen.visible);
        assertTrue(seen.hidden.isEmpty());
        assertSame(east, lattice.sectionAt(lattice.indexOf(1, 0, 0)));

        // Only the visibility graph and the visual flags are inputs to the search
        long packed = origin.getPackedMetadata();
        assertFalse(lattice.updateSectionMetadata(0, 0, 0, packed));
        assertFalse(lattice.updateSectionMetadata(0, 0, 0, PackedSectionMetadata.withPendingUpdate(packed, ChunkUpdateType.REBUILD)));
        assertTrue(lattice.updateSectionMetadata(0, 0, 0, PackedSectionMetadata.withVisibilityData(packed, VisibilityEncoding.NULL)));
        assertFalse(lattice.updateSectionMetadata(40, 0, 0, packed));
        // A closed camera section shows only itself while occlusion is on
        assertEquals(List.of("0,0,0"), search(lattice, at(0, 0, 0), 32f, true, true, 2).visible);
        assertEquals(List.of("0,0,0", "1,0,0"), search(lattice, at(0, 0, 0), 32f, false, true, 3).visible);

        SectionLattice.VisibilitySnapshot snapshot = lattice.findVisible(new Seen(), at(0, 0, 0), 32f, regions.getRegionIdsLength(), false, false, 4);
        assertTrue(snapshot.isSectionVisible(1, 0, 0, 4));
        assertFalse(snapshot.isSectionVisible(1, 0, 0, 5));
        assertFalse(snapshot.isSectionVisible(40, 0, 0, 0));
        assertFalse(snapshot.isSectionVisible(0, 5, 0, 0));
        assertFalse(SectionLattice.VisibilitySnapshot.EMPTY.isSectionVisible(0, 0, 0, 0));

        lattice.detach(east);
        assertThrows(IllegalStateException.class, () -> lattice.detach(east));
        lattice.detach(far);
        assertEquals(List.of("0,0,0"), search(lattice, at(0, 0, 0), 32f, false, true, 5).visible);
        assertThrows(IllegalStateException.class, () -> new SectionLattice(0, 2000, false, false));
        assertThrows(IllegalStateException.class, () -> new SectionLattice(0, 2, false, false).ensureWindowCovers(new Vector3i(), 100_000f));
        assertNull(lattice.rasterBufferSize());
        assertEquals(0, lattice.rasterBacktrackCount());
        assertNotNull(lattice.rasterBudget());
    }

    @Test
    void theWindowSlidesWithTheCameraAndKeepsWhatStillFits() {
        SectionLattice lattice = new SectionLattice(0, 1, false, false);
        for (int x = 0; x <= 12; x++) {
            lattice.attach(section(x, 0, 0));
        }
        lattice.ensureWindowCovers(new Vector3i(0, 0, 0), 32f);
        // A small move stays inside the window
        lattice.ensureWindowCovers(new Vector3i(2, 0, 0), 32f);
        // Moving east slides the window along x, bringing in the sections that entered it
        lattice.ensureWindowCovers(new Vector3i(4, 0, 0), 32f);
        Seen east = search(lattice, at(4, 0, 0), 32f, true, true, 1);
        assertEquals(List.of("2,0,0", "3,0,0", "4,0,0", "5,0,0", "6,0,0"), east.visible.stream().sorted().toList());
        // Sections past the distance are still reported, so the walk can stop at them
        assertEquals(List.of("1,0,0", "7,0,0"), east.hidden.stream().sorted().toList());
        // Moving along z slides plane by plane
        lattice.ensureWindowCovers(new Vector3i(4, 0, 5), 32f);
        assertEquals(5, search(lattice, at(4, 0, 0), 32f, true, true, 2).visible.size());
        // Back west and north at once: the window now ends at x=4
        lattice.ensureWindowCovers(new Vector3i(-2, 0, 0), 32f);
        assertEquals(List.of("0,0,0", "1,0,0", "2,0,0", "3,0,0", "4,0,0"), search(lattice, at(2, 0, 0), 32f, true, true, 3).visible.stream().sorted().toList());
        // A jump with no overlap rebuilds from scratch
        lattice.ensureWindowCovers(new Vector3i(500, 0, 500), 32f);
        lattice.ensureWindowCovers(new Vector3i(4, 0, 0), 32f);
        assertEquals(5, search(lattice, at(4, 0, 0), 32f, true, true, 4).visible.size());
        // A longer distance needs a larger window, which is reallocated and refilled
        lattice.ensureWindowCovers(new Vector3i(4, 0, 0), 200f);
        assertEquals(13, search(lattice, at(4, 0, 0), 200f, true, true, 5).visible.size());
    }

    @Test
    void anUnloadedCameraSectionScansItsPlaneWithoutOcclusion() {
        SectionLattice lattice = new SectionLattice(0, 1, false, false);
        for (int x = 0; x <= 3; x++) {
            lattice.attach(section(x, 0, 0));
        }
        lattice.ensureWindowCovers(new Vector3i(-2, 0, 0), 32f);
        // Two sections west of the loaded row: the scan seeds what lies within the distance on the camera's plane
        Seen seen = search(lattice, at(-2, 0, 0), 32f, true, true, 1);
        assertEquals(List.of("0,0,0"), seen.visible);
        assertEquals(List.of("1,0,0"), seen.hidden);
    }

    @Test
    void aCameraOutsideTheWorldHeightSeedsTheNearestLayer() {
        SectionLattice lattice = new SectionLattice(0, 2, false, false);
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                lattice.attach(section(x, y, 0));
            }
        }
        lattice.ensureWindowCovers(new Vector3i(0, 5, 0), 64f);
        Seen above = search(lattice, at(0, 5, 0), 64f, true, true, 1);
        assertEquals(List.of("0,1,0", "1,1,0"), above.visible.stream().sorted().toList());
        assertEquals(List.of("0,0,0", "1,0,0"), above.hidden.stream().sorted().toList());
        Seen below = search(lattice, at(0, -3, 0), 64f, true, true, 2);
        assertEquals(4, below.visible.size());
        // A frustum that rejects everything rejects every seed too
        Seen blind = new Seen();
        lattice.findVisible(blind, new Viewport(Sections.NOTHING, new Vector3d(8, 88, 8)), 64f, regions.getRegionIdsLength(), true, true, 3);
        assertTrue(blind.visible.isEmpty() && blind.hidden.isEmpty());
    }

    @Test
    void theFrustumAndTheApertureClampLimitTheWalk() {
        SectionLattice lattice = new SectionLattice(0, 1, false, false);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                lattice.attach(section(x, 0, z));
            }
        }
        lattice.ensureWindowCovers(new Vector3i(0, 0, 0), 64f);
        // Cells reached along several paths widen their aperture rather than being queued twice
        assertEquals(25, search(lattice, at(0, 0, 0), 64f, true, true, 1).visible.size());
        assertEquals(25, search(lattice, at(0, 0, 0), 64f, true, false, 2).visible.size());
        // The camera section is always drawn; everything else fails a frustum that sees nothing
        Seen blind = new Seen();
        lattice.findVisible(blind, new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), 64f, regions.getRegionIdsLength(), true, true, 3);
        assertEquals(List.of("0,0,0"), blind.visible);
        assertEquals(4, blind.hidden.size());
        // The section test pads the box for model overhang
        assertTrue(OcclusionCuller.isWithinFrustum(at(0, 0, 0), 0, 0, 0));
        assertFalse(OcclusionCuller.isWithinFrustum(new Viewport(Sections.NOTHING, new Vector3d()), 0, 0, 0));
    }

    @Test
    void theShadowSearchFollowsTheLightFromWhatThePlayerSees() {
        SectionLattice lattice = new SectionLattice(0, 3, true, false);
        for (int y = 0; y <= 2; y++) {
            lattice.attach(section(0, y, 0));
            lattice.attach(section(1, y, 0));
        }
        lattice.ensureWindowCovers(new Vector3i(0, 0, 0), 64f);
        // The main search records its visible cells as the shadow search's roots
        search(lattice, at(0, 0, 0), 64f, true, true, 1);
        Seen sun = new Seen();
        SectionLattice.VisibilitySnapshot lit = lattice.findShadowVisible(sun, at(0, 0, 0), 64f, regions.getRegionIdsLength(), new Vector3f(0, 1, 0), 1);
        assertEquals(6, sun.visible.size());
        assertTrue(lit.isSectionVisible(0, 2, 0, 1));
        // A slanted light still only steps toward the light; a shadow frustum that sees nothing drops every root
        assertFalse(lattice.findShadowVisible(new Seen(), at(0, 0, 0), 64f, regions.getRegionIdsLength(), new Vector3f(0.6f, 0.8f, 0), 2).isSectionVisible(5, 5, 5, 2));
        Seen blind = new Seen();
        lattice.findShadowVisible(blind, new Viewport(Sections.NOTHING, new Vector3d(8, 8, 8)), 64f, regions.getRegionIdsLength(), new Vector3f(0, 1, 0), 3);
        assertTrue(blind.visible.isEmpty());
        // Without a light vector it is the plain frustum scan, on its own visit state
        Seen scan = new Seen();
        lattice.findShadowVisible(scan, at(0, 0, 0), 64f, regions.getRegionIdsLength(), null, 4);
        assertEquals(6, scan.visible.size());
        assertThrows(IllegalStateException.class, () -> new SectionLattice(0, 1, false, false).findShadowVisible(new Seen(), at(0, 0, 0), 16f, 1, null, 1));
        assertEquals(GraphDirectionSet.ALL, ShadowOcclusionCuller.towardLightDirections(0, 0, 0));
        assertEquals(GraphDirectionSet.of(GraphDirection.UP) | GraphDirectionSet.of(GraphDirection.WEST) | GraphDirectionSet.of(GraphDirection.EAST)
                | GraphDirectionSet.of(GraphDirection.NORTH) | GraphDirectionSet.of(GraphDirection.SOUTH), ShadowOcclusionCuller.towardLightDirections(0, 1, 0));
    }

    @Test
    void theRasterOccluderTestsSectionsAgainstWhatIsAlreadyDrawn() {
        SectionLattice lattice = new SectionLattice(0, 1, false, true);
        SectionVisibilityBuilder slab = new SectionVisibilityBuilder();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    slab.markOpaque(x, y, z);
                    slab.markRenderable(x, y, z);
                }
            }
        }
        int[] boxes = slab.computeOccluderBoxes();
        // The camera stands in open air, with a row of solid sections ahead of it
        lattice.attach(section(0, 0, 0));
        lattice.attach(section(0, 0, 1));
        for (int z = 2; z <= 8; z++) {
            RenderSection section = section(0, 0, z);
            BuiltRenderSectionData data = new BuiltRenderSectionData();
            data.hasBlockGeometry = true;
            data.visibilityData = VisibilityEncoding.EVERYTHING;
            data.occluderBoxes = boxes;
            section.setInfo(data);
            lattice.attach(section);
        }
        lattice.ensureWindowCovers(new Vector3i(0, 0, 0), 160f);
        // Looking down +z from just in front of the row, so each solid section hides the ones behind it
        Matrix4f vp = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9f, 0.05f, 400f)
                .lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0));
        Viewport viewport = new Viewport(Sections.EVERYTHING, new Vector3d(8, 8, 8), vp);
        Seen seen = new Seen();
        lattice.findVisible(seen, viewport, 160f, regions.getRegionIdsLength(), true, true, 1);
        assertFalse(seen.visible.isEmpty());
        assertTrue(seen.visible.size() + seen.hidden.size() <= 9);
        assertNotNull(lattice.rasterBufferSize());
        assertTrue(lattice.rasterBufferSize().matches("\\d+x\\d+"));
        assertTrue(lattice.rasterBacktrackCount() >= 0);
        // A metadata change that swaps the boxes is a search input even when the packed word is unchanged
        RenderSection first = lattice.getRenderSection(0, 0, 2);
        BuiltRenderSectionData rebuilt = new BuiltRenderSectionData();
        rebuilt.hasBlockGeometry = true;
        rebuilt.visibilityData = VisibilityEncoding.EVERYTHING;
        rebuilt.occluderBoxes = java.util.Arrays.copyOf(boxes, 1);
        first.setInfo(rebuilt);
        assertTrue(lattice.updateSectionMetadata(0, 0, 2, first.getPackedMetadata()));
        assertFalse(lattice.updateSectionMetadata(0, 0, 2, first.getPackedMetadata()));
        // A section waiting on a rebuild occludes with its whole box
        first.setPendingUpdate(ChunkUpdateType.REBUILD);
        lattice.updateSectionMetadata(0, 0, 2, first.getPackedMetadata());
        lattice.findVisible(new Seen(), viewport, 160f, regions.getRegionIdsLength(), true, true, 2);
        // No view matrix, no raster
        assertFalse(search(lattice, Sections.viewport(8, 8, 8), 160f, true, true, 3).visible.isEmpty());
    }
}
