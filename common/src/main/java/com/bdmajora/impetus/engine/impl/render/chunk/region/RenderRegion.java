package com.bdmajora.impetus.engine.impl.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.gl.arena.GlBufferArena;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    protected static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    protected static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    protected static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        if(!MathUtil.isPowerOfTwo(REGION_WIDTH) || !MathUtil.isPowerOfTwo(REGION_HEIGHT) || !MathUtil.isPowerOfTwo(REGION_LENGTH)) {
            throw new IllegalStateException("Region width/height/length are not powers of two");
        }
    }

    private final StagingBuffer stagingBuffer;
    private final int x, y, z;

    @Getter
    private final int id;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    @Getter
    private final long[] sectionLoadTimes = new long[RenderRegion.REGION_SIZE];
    @Getter
    private long newestSectionLoadTime;

    private int sectionCount;

    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();

    @Unmodifiable
    private List<DeviceResources> allDeviceResources = List.of();

    // Bumped whenever the set of render passes in this region changes; a version counter rather than a dirty flag so several consumers can notice it without racing to clear it
    @Getter
    private int passSetUpdateCount = 0;

    RenderRegion(int x, int y, int z, int id, StagingBuffer stagingBuffer) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.id = id;
        this.stagingBuffer = stagingBuffer;
    }

    // Packed region coordinates
    public static long key(int x, int y, int z) {
        return PositionUtil.packSection(x, y, z);
    }

    // Region origin in sections
    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    // Region origin in sections
    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    // Region origin in sections
    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    // Region origin in blocks
    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    // Region origin in blocks
    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    // Region origin in blocks
    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    // Region centre in blocks
    public int getCenterX() {
        return (this.getChunkX() + REGION_WIDTH / 2) << 4;
    }

    // Region centre in blocks
    public int getCenterY() {
        return (this.getChunkY() + REGION_HEIGHT / 2) << 4;
    }

    // Region centre in blocks
    public int getCenterZ() {
        return (this.getChunkZ() + REGION_LENGTH / 2) << 4;
    }

    // Frees every storage and resource
    public void delete(CommandList commandList) {
        for (var storage : this.sectionRenderData.values()) {
            storage.delete();
        }

        this.sectionRenderData.clear();

        this.allDeviceResources.forEach(resources -> resources.delete(commandList));
        this.allDeviceResources = List.of();

        Arrays.fill(this.sections, null);
        Arrays.fill(this.sectionLoadTimes, 0);
    }

    // No sections attached
    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    // Storage for a pass, or null
    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    // Storage for a pass, created on first use
    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            this.sectionRenderData.put(pass, storage = new SectionRenderDataStorage(pass.primitiveType()));
            this.passSetUpdateCount++;
        }

        return storage;
    }

    // Frees pass storages no section uses any more
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

    // Frees a section's meshes in every pass
    public void removeMeshes(int sectionIndex) {
        if (this.sectionRenderData.isEmpty()) {
            return;
        }
        for (var storage : this.sectionRenderData.values()) {
            storage.removeMeshes(sectionIndex);
        }
    }

    // Whether the pass has any geometry here
    public boolean hasSectionsInPass(TerrainRenderPass pass) {
        return this.sectionRenderData.containsKey(pass);
    }

    // Passes with storage
    public Set<TerrainRenderPass> getPasses() {
        return this.sectionRenderData.keySet();
    }

    // Rewrites offsets after an arena resize
    public void refresh(CommandList commandList) {
        this.allDeviceResources.forEach(resources -> resources.deleteTessellations(commandList));

        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }
    }

    // Attaches a section at its local index
    public void addSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        this.sections[sectionIndex] = section;
        this.sectionLoadTimes[sectionIndex] = 0;
        this.sectionCount++;
    }

    // Detaches and frees its meshes
    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        this.removeMeshes(sectionIndex);

        this.sections[sectionIndex] = null;
        this.sectionLoadTimes[sectionIndex] = 0;
        this.sectionCount--;
    }

    // Stamps when a section first got geometry, for the fade-in
    public void updateSectionLoadTime(RenderSection section) {
        long timestamp = System.nanoTime();
        this.sectionLoadTimes[section.getSectionIndex()] = timestamp;
        this.newestSectionLoadTime = timestamp;
    }

    // Section by local index
    @Nullable
    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    // Per-vertex-format GPU resources
    public Collection<DeviceResources> getAllResources() {
        return this.allDeviceResources;
    }

    // Resources for a format, or null
    public DeviceResources getResources(GlVertexFormat format) {
        var stride = format.getStride();
        var list = this.allDeviceResources;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < list.size(); i++) {
            var resources = list.get(i);
            if (resources.stride == stride) {
                return resources;
            }
        }
        return null;
    }

    // Resources for a format, created on first use
    public DeviceResources createResources(GlVertexFormat format, CommandList commandList) {
        var resources = getResources(format);
        if (resources == null) {
            resources = new DeviceResources(commandList, this.stagingBuffer, format.getStride());

            var newList = new ArrayList<>(this.allDeviceResources);
            newList.add(resources);
            this.allDeviceResources = List.copyOf(newList);
        }

        return resources;
    }

    // Rebuilds tessellations after any arena changed
    public void update(CommandList commandList) {
        var oldList = this.allDeviceResources;
        boolean needListUpdate = false;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < oldList.size(); i++) {
            var resources = oldList.get(i);
            if (resources.shouldDelete()) {
                resources.delete(commandList);
                needListUpdate = true;
            } else {
                resources.deleteIndexArenaIfPossible(commandList);
            }
        }
        // Skip the list copy in the common case that nothing was deleted.
        if (needListUpdate) {
            var newList = new ArrayList<>(this.allDeviceResources);
            newList.removeIf(DeviceResources::isDeleted);
            this.allDeviceResources = List.copyOf(newList);
        }
    }

    public static class DeviceResources {
        private final GlBufferArena geometryArena;
        private final StagingBuffer stagingBuffer;
        private final int stride;
        private GlBufferArena indexArena;
        private GlTessellation tessellation;
        private GlTessellation indexedTessellation;

        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer, int stride) {
            this.geometryArena = new GlBufferArena(commandList, REGION_SIZE * 756, stride, stagingBuffer);
            this.stagingBuffer = stagingBuffer;
            this.stride = stride;
        }

        // Replaces the unsorted tessellation
        public void updateTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
            }

            this.tessellation = tessellation;
        }

        // For unsorted passes
        public GlTessellation getTessellation() {
            return this.tessellation;
        }

        // Replaces the sorted tessellation
        public void updateIndexedTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
            }

            this.indexedTessellation = tessellation;
        }

        // For sorted passes, with the per-section index arena
        public GlTessellation getIndexedTessellation() {
            return this.indexedTessellation;
        }

        // Frees both
        public void deleteTessellations(CommandList commandList) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
                this.tessellation = null;
            }

            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
                this.indexedTessellation = null;
            }
        }

        // The geometry arena's buffer
        public GlBuffer getVertexBuffer() {
            return this.geometryArena.getBufferObject();
        }

        // The index arena's buffer, or null
        public GlBuffer getIndexBuffer() {
            if (this.indexArena == null) {
                throw new IllegalStateException("Attempted to retrieve index buffer for a non-indexed region");
            }
            return this.indexArena.getBufferObject();
        }

        // Frees the buffers
        public void delete(CommandList commandList) {
            this.deleteTessellations(commandList);
            this.geometryArena.delete(commandList);
            if (this.indexArena != null) {
                this.indexArena.delete(commandList);
            }
        }

        // Whether delete has run
        public boolean isDeleted() {
            return this.geometryArena.isDeleted();
        }

        // Vertex storage
        public GlBufferArena getGeometryArena() {
            return this.geometryArena;
        }

        // Sorted index storage, or null
        public GlBufferArena getIndexArena() {
            return this.indexArena;
        }

        // Created on first sorted section
        public GlBufferArena getOrCreateIndexArena(CommandList commandList) {
            if (this.indexArena == null) {
                this.indexArena = new GlBufferArena(commandList, (REGION_SIZE * 126) / 4 * 6, 4, this.stagingBuffer);
            }
            return this.indexArena;
        }

        // No geometry left
        public boolean shouldDelete() {
            return this.geometryArena.isEmpty();
        }

        // Frees the index arena once no section needs sorting
        public void deleteIndexArenaIfPossible(CommandList commandList) {
            if (this.indexArena != null && this.indexArena.isEmpty()) {
                this.updateIndexedTessellation(commandList, null);
                this.indexArena.delete(commandList);
                this.indexArena = null;
            }
        }
    }
}
