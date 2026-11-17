package com.bdmajora.impetus.engine.impl.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import com.bdmajora.impetus.engine.impl.gl.arena.PendingUpload;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.MappedStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
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

// Creates the regions and uploads the meshes to them.
public class RenderRegionManager {
    // Whether mapped staging buffers are used when the driver supports them.
    public static boolean USE_ADVANCED_STAGING_BUFFERS = true;

    // Loaded regions by key.
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    // Ids in use.
    private final BitSet regionIds = new BitSet();
    // Smallest id that may be free.
    private int nextFreeId = 0;

    // Staging buffer shared by the regions.
    private final StagingBuffer stagingBuffer;

    // Configuration of the render passes.
    private final RenderPassConfiguration<?> renderPassConfiguration;

    // Vertex stride shared by every pass.
    private final int commonVertexStride;

    // Estimator of the upload time.
    private final UploadDurationEstimator uploadDurationEstimator = new UploadDurationEstimator();

    // Creates the manager and rejects a null configuration.
    public RenderRegionManager(CommandList commandList, RenderPassConfiguration<?> renderPassConfiguration) {
        Objects.requireNonNull(renderPassConfiguration, "renderPassConfiguration");
        this.stagingBuffer = createStagingBuffer(commandList);
        this.renderPassConfiguration = renderPassConfiguration;
        this.commonVertexStride = computeCommonVertexStride(renderPassConfiguration);
    }

    // Returns the least common multiple of the vertex strides.
    private static int computeCommonVertexStride(RenderPassConfiguration<?> renderPassConfiguration) {
        return renderPassConfiguration.getAllKnownRenderPasses()
                .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                .reduce(1, MathUtil::lcm);
    }

    // Deletes the regions that became empty.
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

    // Uploads the finished meshes region by region and records the upload time.
    public void uploadMeshes(CommandList commandList, Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        Objects.requireNonNull(commandList, "commandList");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(graphUpdateTrigger, "graphUpdateTrigger");
        long uploadedBytes = 0L;
        long startTime = System.nanoTime();
        for (var entry : this.createMeshUploadQueues(results)) {
            uploadedBytes += new MeshUploader(commandList, entry.getKey(), graphUpdateTrigger).processResults(entry.getValue());
        }
        if (uploadedBytes > 0L) {
            this.uploadDurationEstimator.recordUpload(uploadedBytes, System.nanoTime() - startTime);
        }
    }

    // Returns the upload estimator.
    public UploadDurationEstimator getUploadDurationEstimator() {
        return this.uploadDurationEstimator;
    }

    // Returns an iterable of the entries of a map that reuses the entry object.
    private static <K, V> ObjectIterable<Reference2ReferenceMap.Entry<K, V>> fastIterable(Reference2ReferenceMap<K, V> map) {
        final ObjectSet<Reference2ReferenceMap.Entry<K, V>> entries = map.reference2ReferenceEntrySet();
        return entries instanceof Reference2ReferenceMap.FastEntrySet ? () -> ((Reference2ReferenceMap.FastEntrySet<K, V>)entries).fastIterator() : entries;
    }

    // Uploads the results of one region.
    private class MeshUploader {
        // Queued uploads.
        private final ArrayList<PendingSectionUpload> uploads = new ArrayList<>();
        // Command list of the upload.
        private final CommandList commandList;
        // Region of the upload.
        private final RenderRegion region;
        // Callback for a changed set of passes.
        private final Runnable graphUpdateTrigger;

        // Whether an index buffer is needed.
        private boolean needIndexBuffer;

        // Creates an uploader.
        private MeshUploader(CommandList commandList, RenderRegion region, Runnable graphUpdateTrigger) {
            this.commandList = commandList;
            this.region = region;
            this.graphUpdateTrigger = graphUpdateTrigger;
        }

        // Queues the uploads of a built section.
        private void processBuildResult(ChunkBuildOutput result) {
            region.removeMeshes(result.render.getSectionIndex());

            for (var entry : fastIterable(result.meshes)) {
                BuiltSectionMeshParts mesh = Objects.requireNonNull(entry.getValue());

                needIndexBuffer |= mesh.indexBuffer() != null;

                uploads.add(new PendingMeshRebuildUpload(result.render, mesh, entry.getKey(),
                        PendingUpload.of(mesh.vertexBuffer()), PendingUpload.of(mesh.indexBuffer())));
            }
        }

        // Queues the uploads of a sorted section.
        private void processSortResult(ChunkSortOutput result) {
            needIndexBuffer = true;

            for (var entry : fastIterable(result.meshes)) {
                var pass = entry.getKey();
                var mesh = entry.getValue();

                var storage = region.getStorage(pass);

                if (storage != null) {
                    storage.removeIndexBuffer(result.render.getSectionIndex());
                }

                uploads.add(new PendingMeshSortUpload(result.render, pass, PendingUpload.of(mesh.indexData())));
            }
        }

        // Uploads every result of the region and returns the queued bytes.
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

            if (uploads.isEmpty()) {
                return 0L;
            }

            var resources = region.createResources(commandList);

            List<PendingUpload> vertexUploads = new ArrayList<>(uploads.size());
            List<PendingUpload> indexUploads = needIndexBuffer ? new ArrayList<>(uploads.size()) : null;
            long uploadedBytes = 0L;
            for (PendingSectionUpload upload : uploads) {
                if (upload.vertexUpload() != null) {
                    vertexUploads.add(upload.vertexUpload());
                    uploadedBytes += upload.vertexUpload().getLength();
                }
                if (upload.indexUpload() != null) {
                    uploadedBytes += upload.indexUpload().getLength();
                    if (indexUploads != null) {
                        indexUploads.add(upload.indexUpload());
                    }
                }
            }

            boolean bufferChanged = resources.getGeometryArena().upload(commandList, vertexUploads);

            if (indexUploads != null) {
                bufferChanged |= resources.getOrCreateIndexArena(commandList).upload(commandList, indexUploads);
            }

            if (bufferChanged) {
                region.refresh(commandList);
            }

            int previousPassCookie = region.getPassSetUpdateCount();

            for (PendingSectionUpload upload : uploads) {
                var storage = region.createStorage(upload.pass(), renderPassConfiguration);
                if (upload instanceof PendingMeshRebuildUpload meshUpload) {
                    var indexResult = upload.indexUpload() != null ? upload.indexUpload().getResult() : null;
                    storage.setMeshes(upload.section().getSectionIndex(),
                            upload.vertexUpload().getResult(), indexResult, meshUpload.meshData().ranges());
                } else if (upload instanceof PendingMeshSortUpload) {
                    storage.replaceIndexBuffer(upload.section().getSectionIndex(), upload.indexUpload().getResult());
                } else {
                    throw new IllegalStateException();
                }
            }

            region.removeEmptyStorages();

            if (region.getPassSetUpdateCount() != previousPassCookie) {
                graphUpdateTrigger.run();
            }

            return uploadedBytes;
        }
    }

    // Groups the results by region.
    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<ChunkTaskOutput>> createMeshUploadQueues(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        var map = new Reference2ReferenceOpenHashMap<RenderRegion, List<ChunkTaskOutput>>();

        for (var holder : results) {
            var result = holder.output();
            var queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    // Deletes every region and the staging buffer.
    public void delete(CommandList commandList) {
        try {
            for (RenderRegion region : this.regions.values()) {
                region.delete(commandList);
            }
        } finally {
            this.regions.clear();
            this.regionIds.clear();
            this.nextFreeId = 0;
            this.stagingBuffer.delete(commandList);
        }
    }

    // Returns the loaded regions.
    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    // Returns the staging buffer.
    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    // Returns the region of a section and creates it when missing.
    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    // Returns an unused id and marks it used.
    private int getNextId() {
        int id = this.nextFreeId;
        this.nextFreeId = this.regionIds.nextClearBit(id + 1);
        this.regionIds.set(id);
        return id;
    }

    // Returns the region at region coordinates and creates it when missing.
    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.getNextId(), this.stagingBuffer, this.commonVertexStride));
        }

        return instance;
    }

    // Returns the length of the id range in use.
    public int getRegionIdsLength() {
        return this.regionIds.length();
    }

    // Upload of one section.
    private interface PendingSectionUpload {
        // Returns the section.
        RenderSection section();
        // Returns the pass.
        TerrainRenderPass pass();
        // Returns the vertex upload or null.
        PendingUpload vertexUpload();
        // Returns the index upload or null.
        PendingUpload indexUpload();
    }

    // Upload of a rebuilt mesh.
    private record PendingMeshRebuildUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass,
                                            PendingUpload vertexUpload, PendingUpload indexUpload) implements PendingSectionUpload {}

    // Upload of a sorted index buffer.
    private record PendingMeshSortUpload(RenderSection section, TerrainRenderPass pass, PendingUpload indexUpload) implements PendingSectionUpload {
        // Returns null.
        @Override
        public PendingUpload vertexUpload() {
            return null;
        }
    }

    // Creates a mapped staging buffer when allowed and supported and a fallback buffer otherwise.
    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (USE_ADVANCED_STAGING_BUFFERS && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
