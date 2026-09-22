package com.bdmajora.impetus.engine.impl.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.MappedStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.estimation.UploadDurationEstimator;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobResult;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenderRegionManager {
    public static boolean USE_ADVANCED_STAGING_BUFFERS = true;

    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    private final BitSet regionIds = new BitSet();
    private int nextFreeId = 0;

    private final StagingBuffer stagingBuffer;

    private final UploadDurationEstimator uploadDurationEstimator = new UploadDurationEstimator();

    public RenderRegionManager(CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
    }

    // Deletes empty regions and refreshes the rest
    public void update() {
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values()
                    .iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);

                    it.remove();

                    this.regionIds.clear(region.getId());
                    this.nextFreeId = Math.min(this.nextFreeId, region.getId());
                }
            }
        }
    }

    // Uploads build results region by region, triggering a graph update when visibility changed
    public void uploadMeshes(CommandList commandList, Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        long uploadedBytes = 0L;
        long startTime = System.nanoTime();

        for (var entry : this.createMeshUploadQueues(results)) {
            uploadedBytes += new MeshUploader(commandList, entry.getKey(), graphUpdateTrigger).processResults(entry.getValue());
        }

        if (uploadedBytes > 0L) {
            this.uploadDurationEstimator.recordUpload(uploadedBytes, System.nanoTime() - startTime);
        }
    }

    // For the frame budget
    public UploadDurationEstimator getUploadDurationEstimator() {
        return this.uploadDurationEstimator;
    }

    // Copied from fastutil 8, which is unavailable when limited to fastutil 7
    private static <K, V> ObjectIterable<Reference2ReferenceMap.Entry<K, V>> fastIterable(Reference2ReferenceMap<K, V> map) {
        final ObjectSet<Reference2ReferenceMap.Entry<K, V>> entries = map.reference2ReferenceEntrySet();
        return entries instanceof Reference2ReferenceMap.FastEntrySet ? () -> ((Reference2ReferenceMap.FastEntrySet<K, V>)entries).fastIterator() : entries;
    }

    private class MeshUploader {
        private final Map<GlVertexFormat, ArrayList<PendingSectionUpload>> uploadsByFormat = new Object2ObjectOpenHashMap<>(2);
        private final CommandList commandList;
        private final RenderRegion region;
        private final Runnable graphUpdateTrigger;

        private boolean needIndexBuffer;

        private MeshUploader(CommandList commandList, RenderRegion region, Runnable graphUpdateTrigger) {
            this.commandList = commandList;
            this.region = region;
            this.graphUpdateTrigger = graphUpdateTrigger;
        }

        // Per-pass upload queue, created lazily
        private ArrayList<PendingSectionUpload> getUploadQueue(TerrainRenderPass pass) {
            return uploadsByFormat.computeIfAbsent(pass.vertexType().getVertexFormat(), $ -> new ArrayList<>());
        }

        // Queues a rebuilt section's meshes for upload
        private void processBuildResult(ChunkBuildOutput result) {
            // Delete all existing data for the section in the region
            region.removeMeshes(result.render.getSectionIndex());

            // Add uploads for any new data
            for (var entry : fastIterable(result.meshes)) {
                BuiltSectionMeshParts mesh = Objects.requireNonNull(entry.getValue());

                needIndexBuffer |= mesh.indexBuffer() != null;

                getUploadQueue(entry.getKey()).add(new PendingMeshRebuildUpload(result.render, mesh, entry.getKey(),
                        PendingUpload.of(mesh.vertexBuffer()), PendingUpload.of(mesh.indexBuffer())));
            }
        }

        // Queues re-sorted index buffers for upload
        private void processSortResult(ChunkSortOutput result) {
            needIndexBuffer = true;

            for (var entry : fastIterable(result.meshes)) {
                var pass = entry.getKey();
                var mesh = entry.getValue();

                var storage = region.getStorage(pass);

                if (storage != null) {
                    storage.removeIndexBuffer(result.render.getSectionIndex());
                }

                getUploadQueue(entry.getKey()).add(new PendingMeshSortUpload(result.render, pass, PendingUpload.of(mesh.indexData())));
            }
        }

        // Queues every result and returns the bytes pending
        public long processResults(Collection<? extends ChunkTaskOutput> results) {
            for (ChunkTaskOutput output : results) {
                if (output instanceof ChunkBuildOutput result) {
                    processBuildResult(result);
                } else if (output instanceof ChunkSortOutput result) {
                    processSortResult(result);
                } else {
                    throw new IllegalStateException("Unexpected result type: " + output.getClass().getName());
                }
            }

            // If we have nothing to upload, abort!
            if (uploadsByFormat.isEmpty()) {
                return 0L;
            }

            boolean bufferChanged = false;
            long uploadedBytes = this.getQueuedUploadBytes();

            for (var entry : uploadsByFormat.entrySet()) {
                var resources = region.createResources(entry.getKey(), commandList);
                var uploads = entry.getValue();

                // Split into the two arenas' queues in one walk; the arena empties the list it is handed
                List<PendingUpload> vertexUploads = new ArrayList<>(uploads.size());
                List<PendingUpload> indexUploads = needIndexBuffer ? new ArrayList<>(uploads.size()) : null;

                for (PendingSectionUpload upload : uploads) {
                    if (upload.vertexUpload() != null) {
                        vertexUploads.add(upload.vertexUpload());
                    }

                    if (indexUploads != null && upload.indexUpload() != null) {
                        indexUploads.add(upload.indexUpload());
                    }
                }

                bufferChanged |= resources.getGeometryArena().upload(commandList, vertexUploads);

                if (indexUploads != null) {
                    bufferChanged |= resources.getOrCreateIndexArena(commandList).upload(commandList, indexUploads);
                }
            }

            // Any buffer change invalidates the tessellation, which is re-created on next use
            if (bufferChanged) {
                region.refresh(commandList);
            }

            int previousPassCookie = region.getPassSetUpdateCount();

            // Collect the upload results
            for (var uploads : uploadsByFormat.values()) {
                for (PendingSectionUpload upload : uploads) {
                    var storage = region.createStorage(upload.pass());
                    if (upload instanceof PendingMeshRebuildUpload meshUpload) {
                        // Replace meshes
                        var indexResult = upload.indexUpload() != null ? upload.indexUpload().getResult() : null;
                        storage.setMeshes(upload.section().getSectionIndex(),
                                upload.vertexUpload().getResult(), indexResult, meshUpload.meshData().ranges());
                    } else if (upload instanceof PendingMeshSortUpload) {
                        // Replace index buffer
                        storage.replaceIndexBuffer(upload.section().getSectionIndex(), upload.indexUpload().getResult());
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }

            region.removeEmptyStorages();

            if (region.getPassSetUpdateCount() != previousPassCookie) {
                graphUpdateTrigger.run();
            }

            return uploadedBytes;
        }

        // Sum over every queue
        private long getQueuedUploadBytes() {
            long bytes = 0L;

            for (var uploads : uploadsByFormat.values()) {
                for (PendingSectionUpload upload : uploads) {
                    bytes += getUploadLength(upload.vertexUpload());
                    bytes += getUploadLength(upload.indexUpload());
                }
            }

            return bytes;
        }
    }

    // Bytes of one upload
    private static long getUploadLength(PendingUpload upload) {
        return upload != null ? upload.getLength() : 0L;
    }

    // Groups results by region so each region's arena is touched once
    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<ChunkTaskOutput>> createMeshUploadQueues(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        var map = new Reference2ReferenceOpenHashMap<RenderRegion, List<ChunkTaskOutput>>();

        for (var holder : results) {
            var result = holder.output();
            var queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    // Frees every region and the staging buffer
    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.delete(commandList);
        }

        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    // Every region
    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    // The upload staging path in use
    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    // The region containing a section, created if absent
    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    // Reuses freed ids before growing
    private int getNextId() {
        int id = this.nextFreeId;
        this.nextFreeId = this.regionIds.nextClearBit(id + 1);
        this.regionIds.set(id);
        return id;
    }

    // Allocates a region and its id
    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.getNextId(), this.stagingBuffer));
        }

        return instance;
    }

    // Id space size, for per-region arrays
    public int getRegionIdsLength() {
        return this.regionIds.length();
    }

    private interface PendingSectionUpload {
        RenderSection section();
        TerrainRenderPass pass();
        PendingUpload vertexUpload();
        PendingUpload indexUpload();
    }

    private record PendingMeshRebuildUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass,
                                            PendingUpload vertexUpload, PendingUpload indexUpload) implements PendingSectionUpload {}

    private record PendingMeshSortUpload(RenderSection section, TerrainRenderPass pass, PendingUpload indexUpload) implements PendingSectionUpload {
        // The vertex half of a pending section upload
        @Override
        public PendingUpload vertexUpload() {
            return null;
        }
    }

    // Mapped when supported, else the fallback
    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (USE_ADVANCED_STAGING_BUFFERS && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
