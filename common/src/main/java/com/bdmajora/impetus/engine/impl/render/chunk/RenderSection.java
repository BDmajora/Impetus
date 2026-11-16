package com.bdmajora.impetus.engine.impl.render.chunk;

import lombok.Getter;
import lombok.Setter;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

// Render state of a chunk section and its place in the visibility graph.
public class RenderSection extends AbstractSection {
    // Region that holds the section.
    private final RenderRegion region;

    // Built data of an empty section that sees through every face.
    public static final BuiltRenderSectionData EMPTY_DATA = new BuiltRenderSectionData();

    // Marks the empty data as having no geometry and full visibility
    static {
        EMPTY_DATA.hasBlockGeometry = false;
        EMPTY_DATA.visibilityData = VisibilityEncoding.EVERYTHING;
    }

    // Built data of the section or null.
    private BuiltRenderSectionData contextData;

    // Packed visibility and visual flags and pending update and build state.
    private long packedMetadata;

    // Receiver of every metadata change or null.
    @Nullable
    private MetadataSink metadataSink;

    // Receiver of metadata changes.
    public interface MetadataSink {
        // Handles a metadata change of a section.
        void onMetadataChanged(RenderSection section);
    }

    // Sort index of a section without translucent passes.
    public static final int NO_TRANSLUCENT_GEOMETRY = -1;

    // Sort states of the translucent passes that resort as the camera moves.
    @Getter
    @NotNull
    private Map<TerrainRenderPass, SortState.Resortable> translucencySortStates = Collections.emptyMap();

    // Debug index of the most expensive sort of the section or the no translucent marker.
    @Getter
    private int highestSortingIndex = NO_TRANSLUCENT_GEOMETRY;

    // Sorting modes of a section.
    public enum SortMode {
        NONE,
        TREE,
        DYNAMIC
    }

    // Sorting mode of the section.
    @Getter
    @NotNull
    private SortMode sortMode = SortMode.NONE;

    // Token of the build in flight or null.
    @Nullable
    private CancellationToken buildCancellationToken = null;

    // Frame of the last finished build.
    private int lastBuiltFrame = -1;
    // Frame of the last submitted build.
    private int lastSubmittedFrame = -1;

    // Deleted state of the section.
    private boolean disposed;

    // Duration of the last build in nanoseconds.
    @Getter
    @Setter
    private long lastBuildDurationNanos;

    // Camera position of the last translucency sort.
    public double lastCameraX, lastCameraY, lastCameraZ;

    // Creates a section in a region at section coordinates.
    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        super(chunkX, chunkY, chunkZ);

        this.region = region;

        this.contextData = null;
        this.updateCachedContextDataFlags();
    }

    // Cancels the build in flight and drops the built data and marks the section deleted.
    public void delete() {
        if (this.buildCancellationToken != null) {
            this.buildCancellationToken.setCancelled();
            this.setBuildCancellationToken(null);
        }

        this.setInfo(null);
        this.disposed = true;
    }

    // Stores new built data and returns whether it changed.
    public boolean setInfo(@Nullable BuiltRenderSectionData info) {
        boolean changed = !Objects.equals(info, this.contextData);
        if (changed) {
            var region = this.getRegion();
            if (this.contextData == null) {
                region.updateSectionLoadTime(this);
            }
            region.onSectionDataChanged();
            this.contextData = info;
            this.updateCachedContextDataFlags();
        }
        return changed;
    }

    // Returns whether the section was deleted.
    public boolean isDisposed() {
        return this.disposed;
    }

    // Returns whether the section has built data.
    public boolean isBuilt() {
        return this.contextData != null;
    }

    // Returns the region of the section.
    public RenderRegion getRegion() {
        return this.region;
    }

    // Returns the built data or null.
    public @Nullable BuiltRenderSectionData getBuiltContext() {
        return this.contextData;
    }

    // Writes the visual flags and visibility and occluder state of the built data into the metadata.
    public void updateCachedContextDataFlags() {
        int flags = 0;
        if (this.contextData != null) {
            flags = this.contextData.getVisualBitmaskForSection();
            if (this.sortMode == SortMode.DYNAMIC) {
                flags |= 1 << RenderVisualsService.NEEDS_DYNAMIC_SORT;
            }
        }
        long visibilityData = this.contextData != null ? this.contextData.visibilityData : VisibilityEncoding.NULL;
        boolean hasOccluderData = this.contextData != null && this.contextData.occluderBoxes != null;

        this.writeMetadata(PackedSectionMetadata.withHasOccluderData(PackedSectionMetadata.withVisibilityData(
                PackedSectionMetadata.withVisualsFlags(this.packedMetadata, flags), visibilityData), hasOccluderData));
    }

    // Stores the metadata and notifies the sink.
    private void writeMetadata(long packed) {
        this.packedMetadata = packed;
        if (this.metadataSink != null) {
            this.metadataSink.onMetadataChanged(this);
        }
    }

    // Returns the packed metadata.
    public long getPackedMetadata() {
        return this.packedMetadata;
    }

    // Stores the metadata sink.
    public void setMetadataSink(@Nullable MetadataSink sink) {
        this.metadataSink = sink;
    }

    // Returns the visual flags.
    public int getVisualsServiceFlags() {
        return PackedSectionMetadata.getVisualsFlags(this.packedMetadata);
    }

    // Returns the visibility data.
    public long getVisibilityData() {
        return PackedSectionMetadata.getVisibilityData(this.packedMetadata);
    }

    // Returns whether any visual flag is set.
    public boolean hasAnythingToRender() {
        return this.getVisualsServiceFlags() != 0;
    }

    // Stores a copy of the sort states and the highest sort index and derives the sorting mode.
    public void setTranslucencySortStates(@NotNull Map<TerrainRenderPass, SortState.Resortable> sortStates, int highestSortingIndex) {
        this.translucencySortStates = Map.copyOf(sortStates);
        this.highestSortingIndex = highestSortingIndex;
        if (sortStates.isEmpty()) {
            this.sortMode = SortMode.NONE;
        } else {
            SortMode mode = SortMode.TREE;
            for (SortState.Resortable state : sortStates.values()) {
                if (!(state instanceof PartitionTree)) {
                    mode = SortMode.DYNAMIC;
                    break;
                }
            }
            this.sortMode = mode;
        }
        this.updateCachedContextDataFlags();
    }

    // Clears the sort states and the sorting mode.
    public void clearTranslucencySortStates() {
        this.translucencySortStates = Collections.emptyMap();
        this.sortMode = SortMode.NONE;
        this.updateCachedContextDataFlags();
    }

    // Returns whether the section sorts by partition tree.
    public boolean isTreeSorted() {
        return this.sortMode == SortMode.TREE;
    }

    // Returns the token of the build in flight or null.
    public @Nullable CancellationToken getBuildCancellationToken() {
        return this.buildCancellationToken;
    }

    // Stores the token of the build in flight and the build flag.
    public void setBuildCancellationToken(@Nullable CancellationToken token) {
        this.buildCancellationToken = token;
        this.writeMetadata(PackedSectionMetadata.withBuildInFlight(this.packedMetadata, token != null));
    }

    // Returns the pending update or null.
    public @Nullable ChunkUpdateType getPendingUpdate() {
        return PackedSectionMetadata.getPendingUpdate(this.packedMetadata);
    }

    // Stores the pending update.
    public void setPendingUpdate(@Nullable ChunkUpdateType type) {
        this.writeMetadata(PackedSectionMetadata.withPendingUpdate(this.packedMetadata, type));
    }

    // Requests an update and promotes the pending one and returns whether it changed.
    public boolean requestUpdate(ChunkUpdateType type) {
        type = ChunkUpdateType.getPromotionUpdateType(this.getPendingUpdate(), type);

        if (type != null) {
            this.setPendingUpdate(type);
            return true;
        } else {
            return false;
        }
    }

    // Returns the frame of the last finished build.
    public int getLastBuiltFrame() {
        return this.lastBuiltFrame;
    }

    // Stores the frame of the last finished build.
    public void setLastBuiltFrame(int lastBuiltFrame) {
        this.lastBuiltFrame = lastBuiltFrame;
    }

    // Returns the frame of the last submitted build.
    public int getLastSubmittedFrame() {
        return this.lastSubmittedFrame;
    }

    // Stores the frame of the last submitted build.
    public void setLastSubmittedFrame(int lastSubmittedFrame) {
        this.lastSubmittedFrame = lastSubmittedFrame;
    }
}
