package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.collections.DoubleBufferedQueue;
import com.bdmajora.impetus.engine.impl.util.collections.ReadQueue;
import com.bdmajora.impetus.engine.impl.util.collections.WriteQueue;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3ic;

import java.util.Objects;

public class OcclusionCuller {
    private final Long2ReferenceMap<OcclusionNode> sections;
    private final SectionTree sectionTree;
    private final int minSectionY, maxSectionY;

    private final DoubleBufferedQueue<OcclusionNode> queue = new DoubleBufferedQueue<>();

    private boolean isCameraInUnloadedSection;

    public OcclusionCuller(Long2ReferenceMap<OcclusionNode> sections, SectionTree sectionTree, int minSectionY, int maxSectionY) {
        this.sections = sections;
        this.sectionTree = sectionTree;
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
    }

    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        if (this.shouldUseSectionTree(viewport, useOcclusionCulling)) {
            this.findVisibleWithSectionTree(visitor, viewport, searchDistance, frame);
            return;
        }

        final var queues = this.queue;
        queues.reset();

        this.isCameraInUnloadedSection = false;
        this.init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);
        if(this.isCameraInUnloadedSection) {
            useOcclusionCulling = false;
        }

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }
    }

    // The tree walk replaces the graph walk when occlusion is off or the camera is outside the world
    private boolean shouldUseSectionTree(Viewport viewport, boolean useOcclusionCulling) {
        if (!useOcclusionCulling) {
            return true;
        }

        var origin = viewport.getChunkCoord();

        return origin.y() < this.minSectionY
                || origin.y() >= this.maxSectionY
                || this.getRenderSection(origin.x(), origin.y(), origin.z()) == null;
    }

    // Frustum-only visibility via the octree
    private void findVisibleWithSectionTree(Visitor visitor, Viewport viewport, float searchDistance, int frame) {
        this.sectionTree.forEachVisible(viewport, searchDistance, section -> {
            if (section.getLastVisibleFrame() == frame) {
                return;
            }

            section.setLastVisibleFrame(frame);
            section.setIncomingDirections(GraphDirectionSet.NONE);
            visitor.visit(section, true);
        });
    }

    private static void processQueue(Visitor visitor,
                                     Viewport viewport,
                                     float searchDistance,
                                     boolean useOcclusionCulling,
                                     int frame,
                                     ReadQueue<OcclusionNode> readQueue,
                                     WriteQueue<OcclusionNode> writeQueue)
    {
        OcclusionNode section;

        while ((section = readQueue.dequeue()) != null) {
            boolean visible = isSectionVisible(section, viewport, searchDistance);
            visitor.visit(section, visible);

            if (!visible) {
                continue;
            }

            int connections;

            {
                if (useOcclusionCulling) {
                    // With occlusion culling we only traverse into neighbours reachable through this chunk: the union of outgoing paths from every incoming path
                    connections = VisibilityEncoding.getConnections(section.getVisibilityData(), section.getIncomingDirections());
                } else {
                    // Not using any occlusion culling, so traversing in any direction is legal.
                    connections = GraphDirectionSet.ALL;
                }

                // Only traverse *outwards* from the centre of the graph search, so mask off invalid directions
                connections &= getOutwardDirections(viewport.getChunkCoord(), section);
            }

            visitNeighbors(writeQueue, section, connections, frame);
        }
    }

    // Distance then frustum
    static boolean isSectionVisible(OcclusionNode section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    // Queues each neighbour the section's visibility data lets light through to
    private static void visitNeighbors(final WriteQueue<OcclusionNode> queue, OcclusionNode section, int outgoing, int frame) {
        // Only traverse into neighbours actually present; avoids a null-check per enqueue, which the JIT then optimises away after profiling
        outgoing &= section.getAdjacentMask();

        // Check if there are any valid connections left, and if not, early-exit.
        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        // This helps the compiler move the checks for some invariants upwards.
        queue.ensureCapacity(6);

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            visitNode(queue, section.adjacentDown, GraphDirectionSet.of(GraphDirection.UP), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            visitNode(queue, section.adjacentUp, GraphDirectionSet.of(GraphDirection.DOWN), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            visitNode(queue, section.adjacentNorth, GraphDirectionSet.of(GraphDirection.SOUTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            visitNode(queue, section.adjacentSouth, GraphDirectionSet.of(GraphDirection.NORTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            visitNode(queue, section.adjacentWest, GraphDirectionSet.of(GraphDirection.EAST), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            visitNode(queue, section.adjacentEast, GraphDirectionSet.of(GraphDirection.WEST), frame);
        }
    }

    // Records the incoming direction; enqueues on first visit this frame
    private static void visitNode(final WriteQueue<OcclusionNode> queue, @NotNull OcclusionNode render, int incoming, int frame) {
        if (render.getLastVisibleFrame() != frame) {
            // First visit to this section this frame, so reset its state
            render.setLastVisibleFrame(frame);
            render.setIncomingDirections(GraphDirectionSet.NONE);

            queue.enqueue(render);
        }

        render.addIncomingDirections(incoming);
    }

    // Directions leading away from the camera, so the walk never doubles back
    private static int getOutwardDirections(Vector3ic origin, OcclusionNode section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.x() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.x() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.y() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.y() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.z() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.z() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    // Nearest-point distance of the section's 16-block box against the render distance
    private static boolean isWithinRenderDistance(CameraTransform camera, OcclusionNode section, float maxDistance) {
        int ox = section.getOriginX();
        int oy = section.getOriginY();
        int oz = section.getOriginZ();

        return isWithinRenderDistance(camera, ox, oy, oz, ox + 16, oy + 16, oz + 16, maxDistance);
    }

    // Nearest-point distance of a block-space box against the render distance; shared with the section tree's node test
    static boolean isWithinRenderDistance(CameraTransform camera, int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ, float maxDistance) {
        // Box corners relative to the camera's integer position (in view space)
        int ox = minX - camera.intX;
        int oy = minY - camera.intY;
        int oz = minZ - camera.intZ;
        int px = maxX - camera.intX;
        int py = maxY - camera.intY;
        int pz = maxZ - camera.intZ;

        // Closest point of the bounding box to the camera origin, in view space
        float dx = nearestToZero(ox, px) - camera.fracX;
        float dy = nearestToZero(oy, py) - camera.fracY;
        float dz = nearestToZero(oz, pz) - camera.fracZ;

        return ((((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance));
    }

    // Closest value in a range to zero
    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    // Block models may extend +/- 1.0 blocks outside their volume on every axis, plus a small epsilon for frustum float imprecision (see GH#2132)
    private static final float CHUNK_SECTION_SIZE = 8.0f /* chunk bounds */ + 1.0f /* maximum model extent */ + 0.125f /* epsilon */;

    // Section box against the frustum
    public static boolean isWithinFrustum(Viewport viewport, OcclusionNode section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(), CHUNK_SECTION_SIZE);
    }

    private void init(Visitor visitor,
                      WriteQueue<OcclusionNode> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.y() < this.minSectionY) {
            // below the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.minSectionY, GraphDirectionSet.of(GraphDirection.DOWN));
        } else if (origin.y() >= this.maxSectionY) {
            // above the world
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.maxSectionY - 1, GraphDirectionSet.of(GraphDirection.UP));
        } else if(this.getRenderSection(origin.x(), origin.y(), origin.z()) == null) {
            // inside the world height-wise, but in an unloaded section
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    origin.y(), GraphDirectionSet.of(GraphDirection.UP) | GraphDirectionSet.of(GraphDirection.DOWN));
            this.isCameraInUnloadedSection = true;
        } else {
            this.initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    // Seeds the walk from the camera's section, or the nearest column when it is outside the world
    private void initWithinWorld(Visitor visitor, WriteQueue<OcclusionNode> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = this.getRenderSection(origin.x(), origin.y(), origin.z());

        Objects.requireNonNull(section);

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section, true);

        int outgoing;

        if (useOcclusionCulling) {
            // The camera is inside this chunk so there are no "incoming" directions; find every path out and enqueue those neighbours instead
            outgoing = VisibilityEncoding.getConnections(section.getVisibilityData());
        } else {
            // Occlusion culling is disabled, so we can traverse into any neighbor.
            outgoing = GraphDirectionSet.ALL;
        }

        visitNeighbors(queue, section, outgoing, frame);
    }

    // Enqueues in-viewport sections in a diamond spiral (innermost layer first, each layer N->W->S->E) for a consistent order without sorting
    private void initOutsideWorldHeight(WriteQueue<OcclusionNode> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = MathUtil.mojfloor(searchDistance / 16.0f);

        // Layer 0
        this.tryVisitNode(queue, origin.x(), height, origin.z(), direction, frame, viewport);

        // Complete layers, excluding layer 0
        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }
        }

        // Incomplete layers
        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.x() + x, height, origin.z() + z, direction, frame, viewport);
            }
        }
    }

    // Seeds one section if it exists and is in the frustum
    private void tryVisitNode(WriteQueue<OcclusionNode> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        OcclusionNode section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, direction, frame);
    }

    // Node lookup by section coordinates
    private OcclusionNode getRenderSection(int x, int y, int z) {
        return this.sections.get(PositionUtil.packSection(x, y, z));
    }

    public interface Visitor {
        void visit(OcclusionNode section, boolean visible);
    }

}
