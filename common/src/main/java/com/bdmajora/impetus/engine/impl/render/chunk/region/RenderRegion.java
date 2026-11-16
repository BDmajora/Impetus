package com.bdmajora.impetus.engine.impl.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferArena;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// Group of sections that share buffers.
public class RenderRegion {
    // Sections along x.
    public static final int REGION_WIDTH = 8;
    // Sections along y.
    public static final int REGION_HEIGHT = 4;
    // Sections along z.
    public static final int REGION_LENGTH = 8;

    // Blocks along x.
    public static final int REGION_BLOCK_WIDTH = REGION_WIDTH * 16;
    // Blocks along y.
    public static final int REGION_BLOCK_HEIGHT = REGION_HEIGHT * 16;
    // Blocks along z.
    public static final int REGION_BLOCK_LENGTH = REGION_LENGTH * 16;

    // Mask of a section x in a region.
    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    // Mask of a section y in a region.
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    // Mask of a section z in a region.
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    // Shift from section x to region x.
    public static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    // Shift from section y to region y.
    public static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    // Shift from section z to region z.
    public static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    // Sections in a region.
    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    // Rejects region sizes that are not powers of two.
    static {
        if(!MathUtil.isPowerOfTwo(REGION_WIDTH) || !MathUtil.isPowerOfTwo(REGION_HEIGHT) || !MathUtil.isPowerOfTwo(REGION_LENGTH)) {
            throw new IllegalStateException("Region width/height/length are not powers of two");
        }
    }

    // Smallest size of the geometry arena.
    private static final int MINIMUM_GEOMETRY_ARENA_BYTES = 512 * 1024;
    // Smallest size of the index arena.
    private static final int MINIMUM_INDEX_ARENA_BYTES = 64 * 1024;

    // Staging buffer for uploads.
    private final StagingBuffer stagingBuffer;
    // Vertex stride shared by every pass.
    private final int commonVertexStride;
    // Position of the region in region units.
    private final int x, y, z;

    // Dense id of the region.
    @Getter
    private final int id;

    // Sections by index.
    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    // Load time of each section.
    @Getter
    private final long[] sectionLoadTimes = new long[RenderRegion.REGION_SIZE];
    // Newest load time.
    @Getter
    private long newestSectionLoadTime;

    // Number of sections.
    private int sectionCount;

    // Mesh data by pass.
    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();

    // GL resources or null.
    @Nullable
    private DeviceResources resources;

    // Counter that changes when the passes change.
    @Getter
    private int passSetUpdateCount = 0;

    // Counter that changes when section data changes.
    @Getter
    private int dataRevision = 0;

    // Creates a region.
    RenderRegion(int x, int y, int z, int id, StagingBuffer stagingBuffer, int commonVertexStride) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.id = id;
        this.stagingBuffer = stagingBuffer;
        this.commonVertexStride = commonVertexStride;
    }

    // Returns the key of region coordinates.
    public static long key(int x, int y, int z) {
        return PositionUtil.packSection(x, y, z);
    }

    // Returns the section x of the first section.
    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    // Returns the section y of the first section.
    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    // Returns the section z of the first section.
    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    // Returns the first block x.
    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    // Returns the first block y.
    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    // Returns the first block z.
    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    // Returns the center block x.
    public int getCenterX() {
        return (this.getChunkX() + REGION_WIDTH / 2) << 4;
    }

    // Returns the center block y.
    public int getCenterY() {
        return (this.getChunkY() + REGION_HEIGHT / 2) << 4;
    }

    // Returns the center block z.
    public int getCenterZ() {
        return (this.getChunkZ() + REGION_LENGTH / 2) << 4;
    }

    // Deletes the data and resources and the sections.
    public void delete(CommandList commandList) {
        try {
            for (var storage : this.sectionRenderData.values()) {
                storage.delete();
            }
        } finally {
            this.sectionRenderData.clear();

            try {
                if (this.resources != null) {
                    this.resources.delete(commandList);
                }
            } finally {
                this.resources = null;

                Arrays.fill(this.sections, null);
                Arrays.fill(this.sectionLoadTimes, 0);
                this.sectionCount = 0;
            }
        }
    }

    // Returns whether the region has no sections.
    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    // Counts a change of section data.
    public void onSectionDataChanged() {
        this.dataRevision++;
    }

    // Returns the data of a pass or null.
    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    // Returns the data of a pass and creates it when missing.
    public SectionRenderDataStorage createStorage(TerrainRenderPass pass, RenderPassConfiguration<?> renderPassConfiguration) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            int stride = pass.vertexType().getVertexFormat().getStride();

            if (this.commonVertexStride % stride != 0) {
                throw new IllegalStateException("Pass " + pass + " uses a vertex stride of " + stride
                        + ", which does not divide the region's common vertex stride of " + this.commonVertexStride
                        + "; its vertex format was not declared by the render pass configuration");
            }

            this.sectionRenderData.put(pass, storage = new SectionRenderDataStorage(pass.primitiveType(), pass.isSorted(), this.commonVertexStride / stride));
            this.passSetUpdateCount++;
        }

        return storage;
    }

    // Deletes the data of passes without meshes.
    public void removeEmptyStorages() {
        if (this.sectionRenderData.isEmpty()) {
            return;
        }

        boolean anyRemoved = this.sectionRenderData.values().removeIf(s -> {
            if (s.isEmpty()) {
                s.delete();
                return true;
            } else {
                return false;
            }
        });

        if (anyRemoved) {
            this.passSetUpdateCount++;
        }
    }

    // Removes the meshes of a section from every pass.
    public void removeMeshes(int sectionIndex) {
        if (this.sectionRenderData.isEmpty()) {
            return;
        }
        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }
    }

    // Returns whether the pass has data.
    public boolean hasSectionsInPass(TerrainRenderPass pass) {
        return this.sectionRenderData.containsKey(pass);
    }

    // Returns the passes with data.
    public Set<TerrainRenderPass> getPasses() {
        return this.sectionRenderData.keySet();
    }

    // Rewrites the data after the buffers moved.
    public void refresh(CommandList commandList) {
        if (this.resources != null) {
            this.resources.deleteTessellations(commandList);
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }
    }

    // Deletes the cached batches of every pass.
    private void invalidateCachedBatches() {
        for (var storage : this.sectionRenderData.values()) {
            storage.invalidateCachedBatches();
        }
    }

    // Adds a section and rejects a second section at its index.
    public void addSection(RenderSection section) {
        Objects.requireNonNull(section, "section");
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        this.sections[sectionIndex] = section;
        this.sectionLoadTimes[sectionIndex] = 0;
        this.sectionCount++;
        this.dataRevision++;
    }

    // Removes a section and its meshes and rejects a wrong section.
    public void removeSection(RenderSection section) {
        Objects.requireNonNull(section, "section");
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }

        this.sections[sectionIndex] = null;
        this.sectionLoadTimes[sectionIndex] = 0;
        this.sectionCount--;
        this.dataRevision++;
    }

    // Stores the current time as the load time of a section.
    public void updateSectionLoadTime(RenderSection section) {
        Objects.requireNonNull(section, "section");
        long timestamp = System.nanoTime();
        this.sectionLoadTimes[section.getSectionIndex()] = timestamp;
        this.newestSectionLoadTime = timestamp;
    }

    // Returns the section at an index or null.
    @Nullable
    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    // Returns the GL resources or null.
    @Nullable
    public DeviceResources getResources() {
        return this.resources;
    }

    // Returns the GL resources and creates them when missing.
    public DeviceResources createResources(CommandList commandList) {
        if (this.resources == null) {
            this.resources = new DeviceResources(commandList, this.stagingBuffer, this.commonVertexStride);
        }

        return this.resources;
    }

    // Deletes unused resources.
    public void update(CommandList commandList) {
        var resources = this.resources;

        if (resources == null) {
            return;
        }

        if (resources.shouldDelete()) {
            resources.delete(commandList);
            this.resources = null;
            this.invalidateCachedBatches();
        } else if (resources.deleteIndexArenaIfPossible(commandList)) {
            this.invalidateCachedBatches();
        }
    }

    // GL buffers and tessellations of a region.
    public static class DeviceResources {
        // Arena of the vertices.
        private final GlBufferArena geometryArena;
        // Staging buffer for uploads.
        private final StagingBuffer stagingBuffer;
        // Arena of the indices or null.
        private GlBufferArena indexArena;
        // Whether the resources were deleted.
        private boolean deleted;
        // Tessellations by key.
        private final Map<TerrainRenderPass.TessellationKey, GlTessellation> tessellations = new Object2ReferenceOpenHashMap<>();

        // Creates the resources.
        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer, int commonVertexStride) {
            this.geometryArena = new GlBufferArena(commandList, commonVertexStride, MINIMUM_GEOMETRY_ARENA_BYTES, stagingBuffer);
            this.stagingBuffer = stagingBuffer;
        }

        // Returns the tessellation of a key or null.
        public GlTessellation getTessellation(TerrainRenderPass.TessellationKey key) {
            return this.tessellations.get(key);
        }

        // Stores a tessellation and deletes the one it replaces.
        public void updateTessellation(CommandList commandList, TerrainRenderPass.TessellationKey key, GlTessellation tessellation) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(tessellation, "tessellation");
            var previous = this.tessellations.put(key, tessellation);

            if (previous != null) {
                previous.delete(commandList);
            }
        }

        // Deletes every tessellation.
        public void deleteTessellations(CommandList commandList) {
            for (var tessellation : this.tessellations.values()) {
                tessellation.delete(commandList);
            }

            this.tessellations.clear();
        }

        // Returns the vertex buffer.
        public GlBuffer getVertexBuffer() {
            return this.geometryArena.getBufferObject();
        }

        // Returns the index buffer and rejects a region without indices.
        public GlBuffer getIndexBuffer() {
            if (this.indexArena == null) {
                throw new IllegalStateException("Attempted to retrieve index buffer for a non-indexed region");
            }
            return this.indexArena.getBufferObject();
        }

        // Deletes the resources once.
        public void delete(CommandList commandList) {
            if (this.deleted) {
                return;
            }
            this.deleted = true;

            try {
                this.deleteTessellations(commandList);
            } finally {
                try {
                    this.geometryArena.delete(commandList);
                } finally {
                    if (this.indexArena != null) {
                        this.indexArena.delete(commandList);
                    }
                }
            }
        }

        // Returns the vertex arena.
        public GlBufferArena getGeometryArena() {
            return this.geometryArena;
        }

        // Returns the index arena or null.
        public GlBufferArena getIndexArena() {
            return this.indexArena;
        }

        // Returns the index arena and creates it when missing.
        public GlBufferArena getOrCreateIndexArena(CommandList commandList) {
            if (this.indexArena == null) {
                this.indexArena = new GlBufferArena(commandList, 4, MINIMUM_INDEX_ARENA_BYTES, this.stagingBuffer);
            }
            return this.indexArena;
        }

        // Returns whether the vertex arena is empty.
        public boolean shouldDelete() {
            return this.geometryArena.isEmpty();
        }

        // Deletes the index arena when it is empty and returns whether it did.
        public boolean deleteIndexArenaIfPossible(CommandList commandList) {
            if (this.indexArena == null || !this.indexArena.isEmpty()) {
                return false;
            }

            this.deleteTessellations(commandList);
            this.indexArena.delete(commandList);
            this.indexArena = null;

            return true;
        }
    }
}
