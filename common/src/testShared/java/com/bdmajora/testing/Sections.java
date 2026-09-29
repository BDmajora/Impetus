package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.GraphDirection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionNode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import org.joml.Vector3d;

// Builds small loaded worlds of sections and occlusion nodes for the culling and list tests
public final class Sections {
    public static final Frustum EVERYTHING = (a, b, c, d, e, f) -> true;
    public static final Frustum NOTHING = (a, b, c, d, e, f) -> false;

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

    // Nodes for every section in a box, linked to their neighbours, keyed the way the culler looks them up
    public static Long2ReferenceOpenHashMap<OcclusionNode> grid(RenderRegionManager regions, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Long2ReferenceOpenHashMap<OcclusionNode> nodes = new Long2ReferenceOpenHashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    OcclusionNode node = new OcclusionNode(section(regions, x, y, z));
                    node.setVisibilityData(VisibilityEncoding.EVERYTHING);
                    nodes.put(PositionUtil.packSection(x, y, z), node);
                }
            }
        }
        link(nodes);
        return nodes;
    }

    // Connects every node to the neighbours present in the map
    public static void link(Long2ReferenceOpenHashMap<OcclusionNode> nodes) {
        for (OcclusionNode node : nodes.values()) {
            for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
                OcclusionNode adjacent = nodes.get(PositionUtil.packSection(
                        node.getChunkX() + GraphDirection.x(dir), node.getChunkY() + GraphDirection.y(dir), node.getChunkZ() + GraphDirection.z(dir)));
                node.setAdjacentNode(dir, adjacent);
            }
        }
    }
}
