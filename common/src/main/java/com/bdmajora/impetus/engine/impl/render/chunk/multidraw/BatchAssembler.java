package com.bdmajora.impetus.engine.impl.render.chunk.multidraw;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gl.device.DirectMultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.device.IndirectMultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDeviceFeature;
import com.bdmajora.impetus.engine.impl.gl.device.MultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.util.BitwiseMath;

// Turns the visible sections of a region into draw batches.
public final class BatchAssembler {
    // Marker of a region whose sections have different cull masks.
    private static final int MASK_NOT_UNIFORM = -1;

    // Prevents instantiation
    private BatchAssembler() {
    }

    // Creates an indirect batch when the option and the device allow it and a direct batch otherwise.
    private static MultiDrawBatch createBatch(int capacity) {
        if (ImpetusRuntimeOptions.multiDrawIndirect && RenderDevice.INSTANCE.getGpuDevice().supports(GpuDeviceFeature.MULTI_DRAW_INDIRECT)) {
            return new IndirectMultiDrawBatch(capacity);
        }
        return new DirectMultiDrawBatch(capacity);
    }

    // Creates the draw batch of a region and returns null when nothing is drawn.
    public static MultiDrawBatch fillRegion(RenderRegion region,
                                  SectionRenderDataStorage storage,
                                  ChunkRenderList renderList,
                                  CameraTransform camera,
                                  TerrainRenderPass pass,
                                  boolean useBlockFaceCulling) {
        java.util.Objects.requireNonNull(region, "region");
        java.util.Objects.requireNonNull(storage, "storage");
        java.util.Objects.requireNonNull(camera, "camera");
        java.util.Objects.requireNonNull(pass, "pass");
        var sections = renderList.getSectionsWithGeometry();
        int sectionCount = renderList.getSectionsWithGeometryCount();

        if (sectionCount == 0) {
            return null;
        }

        boolean reverse = pass.isReverseOrder();
        var primitiveType = storage.getPrimitiveType();

        if (pass.isSorted()) {
            // Uses one command per section and facing without culling
            var batch = createBatch(sectionCount * ModelQuadFacing.COUNT);
            fillSorted(batch, storage, sections, sectionCount, reverse, primitiveType);
            return batch;
        }

        if (!useBlockFaceCulling) {
            // Uses one command per section
            var batch = createBatch(sectionCount);
            fillSingleRun(batch, storage, sections, sectionCount, reverse, primitiveType, 0, ModelQuadFacing.COUNT - 1);
            return batch;
        }

        // Checks whether every section shares one cull mask
        int uniformMask = uniformCullMask(region, camera);

        if (uniformMask == MASK_NOT_UNIFORM) {
            var batch = createBatch(sectionCount * ModelQuadFacing.COUNT);
            fillPerSectionRuns(batch, region, storage, sections, sectionCount, reverse, primitiveType, camera);
            return batch;
        }

        long runs = packRuns(uniformMask);
        int runCount = runCount(runs);

        if (runCount == 0) {
            return null;
        }

        var batch = createBatch(sectionCount * runCount);
        if (runCount == 1) {
            fillSingleRun(batch, storage, sections, sectionCount, reverse, primitiveType,
                    runFirst(runs, 0), runLast(runs, 0));
        } else {
            fillUniformRuns(batch, storage, sections, sectionCount, reverse, primitiveType, runs, runCount);
        }
        return batch;
    }

    // Source of the tessellation of a region and pass.
    @FunctionalInterface
    public interface TessellationProvider {
        // Returns the tessellation.
        GlTessellation getTessellation(CommandList commandList, RenderRegion region, TerrainRenderPass pass);
    }

    // Creates a batch with its tessellation and the camera cell where it stays valid and deletes the batch on a failure.
    public static CachedBatch createCachedBatch(RenderRegion region,
                                                SectionRenderDataStorage storage,
                                                ChunkRenderList renderList,
                                                CameraTransform camera,
                                                TerrainRenderPass pass,
                                                boolean useBlockFaceCulling,
                                                CommandList commandList,
                                                TessellationProvider tessellationProvider) {
        java.util.Objects.requireNonNull(commandList, "commandList");
        java.util.Objects.requireNonNull(tessellationProvider, "tessellationProvider");
        var batch = fillRegion(region, storage, renderList, camera, pass, useBlockFaceCulling);

        GlTessellation tessellation = null;

        try {
            if (batch != null) {
                batch.upload(commandList);

                if (!batch.isEmpty()) {
                    tessellation = tessellationProvider.getTessellation(commandList, region, pass);
                }
            }
        } catch (Throwable failure) {
            if (batch != null) {
                batch.delete();
            }
            throw failure;
        }

        long intervalX, intervalY, intervalZ;

        if (useBlockFaceCulling) {
            intervalX = cameraValidityInterval(camera.intX, region.getChunkX(), RenderRegion.REGION_WIDTH);
            intervalY = cameraValidityInterval(camera.intY, region.getChunkY(), RenderRegion.REGION_HEIGHT);
            intervalZ = cameraValidityInterval(camera.intZ, region.getChunkZ(), RenderRegion.REGION_LENGTH);
        } else {
            intervalX = intervalY = intervalZ = (Integer.MIN_VALUE & 0xFFFFFFFFL) | ((long) Integer.MAX_VALUE << 32);
        }

        return new CachedBatch(batch, tessellation,
                renderList.getSectionsWithGeometry(), renderList.getSectionsWithGeometryCount(),
                intervalMin(intervalX), intervalMax(intervalX),
                intervalMin(intervalY), intervalMax(intervalY),
                intervalMin(intervalZ), intervalMax(intervalZ));
    }


    // Returns the cull mask shared by every section of the region or the marker when they differ.
    private static int uniformCullMask(RenderRegion region, CameraTransform camera) {
        int minX = region.getChunkX(), minY = region.getChunkY(), minZ = region.getChunkZ();
        int maxX = minX + RenderRegion.REGION_WIDTH - 1;
        int maxY = minY + RenderRegion.REGION_HEIGHT - 1;
        int maxZ = minZ + RenderRegion.REGION_LENGTH - 1;

        int atMin = getVisibleFaces(camera.intX, camera.intY, camera.intZ, minX, minY, minZ);
        int atMax = getVisibleFaces(camera.intX, camera.intY, camera.intZ, maxX, maxY, maxZ);

        return atMin == atMax ? atMin : MASK_NOT_UNIFORM;
    }

    // Bit position of the run count in a packed run plan.
    private static final int RUN_COUNT_SHIFT = 32;

    // Packs a cull mask into runs of consecutive facings.
    private static long packRuns(int mask) {
        long runs = 0;
        int count = 0;

        while (mask != 0) {
            int first = Integer.numberOfTrailingZeros(mask);

            // Measures the run of set bits
            int length = Integer.numberOfTrailingZeros(~(mask >>> first));
            int last = (first + length) - 1;

            // Stores the run
            int shift = count << 3;
            runs |= ((long) first << shift) | ((long) last << (shift + 4));
            count++;

            mask &= ~(((1 << length) - 1) << first);
        }

        return runs | ((long) count << RUN_COUNT_SHIFT);
    }

    // Returns the number of runs of a plan.
    private static int runCount(long runs) {
        return (int) (runs >>> RUN_COUNT_SHIFT) & 0xF;
    }

    // Returns the first facing of a run.
    private static int runFirst(long runs, int run) {
        return (int) (runs >>> (run << 3)) & 0xF;
    }

    // Returns the last facing of a run.
    private static int runLast(long runs, int run) {
        return (int) (runs >>> ((run << 3) + 4)) & 0xF;
    }

    // Returns the half open interval of camera coordinates on one axis that keep the culling of every section of a region.
    public static long cameraValidityInterval(int camera, int minChunkCoord, int sizeInChunks) {
        long maxChunkCoord = (long) minChunkCoord + sizeInChunks - 1;

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        // Visits the boundaries of the positive and negative faces
        for (int offset = -2; offset <= 19; offset += 21) {
            // Finds the section whose boundary is at or below the camera
            long lastBelow = Math.floorDiv((long) camera - offset, 16L);

            // Checks that boundary and the next one
            for (int i = 0; i <= 1; i++) {
                // Limits the section to the region
                long k = Math.max(minChunkCoord, Math.min(lastBelow + i, maxChunkCoord));
                long threshold = (k << 4) + offset;

                // Keeps the closest boundary on each side
                if (threshold <= camera) {
                    min = (int) Math.max(min, threshold);
                } else {
                    max = (int) Math.min(max, threshold);
                }
            }
        }

        return (min & 0xFFFFFFFFL) | ((long) max << 32);
    }

    // Returns the lower end of an interval.
    private static int intervalMin(long interval) {
        return (int) interval;
    }

    // Returns the upper end of an interval.
    private static int intervalMax(long interval) {
        return (int) (interval >>> 32);
    }

    // Ordinals of the facings.
    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X      = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y      = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z      = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X      = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y      = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z      = ModelQuadFacing.NEG_Z.ordinal();

    // Whether the face culling checks are inverted for debugging.
    private static final boolean DEBUG_BLOCK_FACE_CULLING = false;

    // Returns the mask of the facings of a section that face the camera.
    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // Always shows the unassigned facing
        int planes = (1 << MODEL_UNASSIGNED);

        if (DEBUG_BLOCK_FACE_CULLING) {
            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_POS_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_POS_Z;

            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_NEG_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_NEG_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_NEG_Z;
        } else {
            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;
        }

        return planes;
    }

    // Draws every section as one command.
    private static void fillSingleRun(MultiDrawBatch batch,
                                      SectionRenderDataStorage storage,
                                      byte[] sections, int sectionCount, boolean reverse,
                                      ChunkPrimitiveType primitiveType,
                                      int firstFacing, int lastFacing) {
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.COMPACT.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);

            int start = SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pMeshData, firstFacing);
            int end = SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

            batch.appendDrawCommand(start, SectionRenderDataUnsafe.elementsForVertices(end - start, primitiveType), 0L);
        }
    }

    // Draws every section as one command per run of a shared cull mask.
    private static void fillUniformRuns(MultiDrawBatch batch,
                                        SectionRenderDataStorage storage,
                                        byte[] sections, int sectionCount, boolean reverse,
                                        ChunkPrimitiveType primitiveType,
                                        long runs, int runCount) {
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.COMPACT.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);
            int sectionStart = batch.size();
            int previousEnd = 0;

            for (int run = 0; run < runCount; run++) {
                int firstFacing = runFirst(runs, run);
                int lastFacing = runLast(runs, run);

                int start = SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pMeshData, firstFacing);
                int end = SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

                previousEnd = writeUnsorted(batch, start, end, primitiveType, sectionStart, previousEnd);
            }
        }
    }

    // Draws every section with a cull mask calculated for the section.
    private static void fillPerSectionRuns(MultiDrawBatch batch,
                                           RenderRegion region,
                                           SectionRenderDataStorage storage,
                                           byte[] sections, int sectionCount, boolean reverse,
                                           ChunkPrimitiveType primitiveType,
                                           CameraTransform camera) {
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.COMPACT.getStride();

        final int originX = region.getChunkX();
        final int originY = region.getChunkY();
        final int originZ = region.getChunkZ();

        final int cameraX = camera.intX, cameraY = camera.intY, cameraZ = camera.intZ;

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            int sectionIndex = sections[cursor] & 0xFF;

            int mask = getVisibleFaces(cameraX, cameraY, cameraZ,
                    originX + LocalSectionIndex.unpackX(sectionIndex),
                    originY + LocalSectionIndex.unpackY(sectionIndex),
                    originZ + LocalSectionIndex.unpackZ(sectionIndex));

            long pMeshData = pBase + (sectionIndex * stride);
            int sectionStart = batch.size();
            int previousEnd = 0;

            // Emits at least one run because the mask is never zero
            do {
                int firstFacing = Integer.numberOfTrailingZeros(mask);
                int lastFacing = firstFacing + Integer.numberOfTrailingZeros(~(mask >>> firstFacing)) - 1;

                int start = SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pMeshData, firstFacing);
                int end = SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

                previousEnd = writeUnsorted(batch, start, end, primitiveType, sectionStart, previousEnd);

                // Clears the consumed facings
                mask &= -1 << (lastFacing + 1);
            } while (mask != 0);
        }
    }

    // Draws every populated facing of every section of a sorted pass.
    private static void fillSorted(MultiDrawBatch batch,
                                   SectionRenderDataStorage storage,
                                   byte[] sections, int sectionCount, boolean reverse,
                                   ChunkPrimitiveType primitiveType) {
        final long pBase = storage.getRowBasePointer();
        final long stride = SectionRenderDataUnsafe.Strategy.FULL.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                // Appends the command and relies on the batch to skip empty ones
                batch.appendDrawCommand(
                        SectionRenderDataUnsafe.Strategy.FULL.getVertexOffset(pMeshData, facing),
                        SectionRenderDataUnsafe.Strategy.FULL.getElementCount(pMeshData, facing, primitiveType),
                        SectionRenderDataUnsafe.Strategy.FULL.getIndexOffset(pMeshData, facing));
            }
        }
    }

    // Appends a run or merges it into the previous command and returns the end of the run.
    private static int writeUnsorted(MultiDrawBatch batch, int baseVertex, int endVertex,
                                     ChunkPrimitiveType primitiveType, int sectionStart, int previousEnd) {
        int elementCount = SectionRenderDataUnsafe.elementsForVertices(endVertex - baseVertex, primitiveType);

        if (elementCount == 0) {
            return previousEnd;
        }

        if (batch.size() > sectionStart && previousEnd == baseVertex) {
            batch.mergeIntoLastCommand(elementCount);
        } else {
            batch.appendDrawCommand(baseVertex, elementCount, 0L);
        }

        return endVertex;
    }
}
