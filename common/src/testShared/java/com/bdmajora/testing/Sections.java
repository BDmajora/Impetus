package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.VisibleChunkCollector;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

// Builds small loaded worlds of sections, and runs the lattice search over them, for the culling and list tests
public final class Sections {
    public static final Frustum EVERYTHING = (a, b, c, d, e, f) -> true;
    public static final Frustum NOTHING = (a, b, c, d, e, f) -> false;
    // Far enough for every fixture world, small enough to keep the lattice window cheap
    public static final float DISTANCE = 1000f;

    private Sections() {}

    public static Viewport viewport(double x, double y, double z) {
        return new Viewport(EVERYTHING, new Vector3d(x, y, z));
    }

    // A section registered with its region, built with geometry and see-through visibility
    public static RenderSection section(RenderRegionManager regions, int x, int y, int z) {
        RenderSection section = new RenderSection(regions.createForChunk(x, y, z), x, y, z);
        section.getRegion().addSection(section);
        BuiltRenderSectionData data = new BuiltRenderSectionData();
        data.hasBlockGeometry = true;
        data.visibilityData = VisibilityEncoding.EVERYTHING;
        section.setInfo(data);
        return section;
    }

    // The sections attached to a lattice spanning their heights, its window placed around the viewport
    public static SectionLattice lattice(Viewport viewport, RenderSection... sections) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (RenderSection section : sections) {
            minY = Math.min(minY, section.getChunkY());
            maxY = Math.max(maxY, section.getChunkY());
        }
        SectionLattice lattice = sections.length == 0 ? new SectionLattice(0, 1, false, false) : new SectionLattice(minY, maxY + 1, false, false);
        for (RenderSection section : sections) {
            lattice.attach(section);
        }
        lattice.ensureWindowCovers(viewport.getChunkCoord(), DISTANCE);
        return lattice;
    }

    // One unoccluded search over the sections, collected the way RenderListManager collects it
    public static VisibleChunkCollector collect(RenderRegionManager regions, Viewport viewport, int frame, RenderSection... sections) {
        SectionLattice lattice = lattice(viewport, sections);
        VisibleChunkCollector collector = new VisibleChunkCollector(lattice, frame, regions.getRegionIdsLength(), 64, viewport.getBlockCoord());
        lattice.findVisible(collector, viewport, DISTANCE, regions.getRegionIdsLength(), false, false, frame);
        collector.finishRebuildLists();
        return collector;
    }

    // The render lists of one such search
    public static SortedRenderLists renderLists(RenderRegionManager regions, Viewport viewport, RenderSection... sections) {
        return collect(regions, viewport, 1, sections).createRenderLists();
    }
}
