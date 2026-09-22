package com.bdmajora.impetus.engine.impl.render.chunk;

import lombok.Getter;
import lombok.Setter;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

// Render state for a chunk section: the graphics state for each render pass plus its data in the chunk visibility graph
public class RenderSection extends AbstractSection {
    // Render Region State
    private final RenderRegion region;

    // Must be EVERYTHING for the default visibility encoding so ContextBundle.empty() matches the data generated for an empty section
    public static final BuiltRenderSectionData EMPTY_DATA = new BuiltRenderSectionData();

    static {
        EMPTY_DATA.hasBlockGeometry = false;
        EMPTY_DATA.visibilityData = VisibilityEncoding.EVERYTHING;
    }

    // Rendering State
    private BuiltRenderSectionData contextData;
    private boolean hasAnythingToRender;
    @Getter
    private int visualsServiceFlags;

    // Maps each translucent render pass to its sort state (data needed to re-sort as the camera moves); empty for sections without translucent passes
    @Getter
    @NotNull
    private Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucencySortStates = Collections.emptyMap();

    @Getter
    private TranslucentQuadAnalyzer.Level highestSortingLevel = TranslucentQuadAnalyzer.Level.NONE;

    @Getter
    @Setter
    private boolean needsDynamicTranslucencySorting;

    // Pending Update State

    // In-flight build job, if any: lets delete() cancel early and lets VisibleChunkCollector skip re-queuing (submitRebuildTasks does the authoritative type check)
    @Nullable
    private CancellationToken buildCancellationToken = null;

    @Nullable
    private ChunkUpdateType pendingUpdateType;

    private int lastBuiltFrame = -1;
    private int lastSubmittedFrame = -1;

    // Lifetime state
    private boolean disposed;

    @Getter
    @Setter
    private long lastBuildDurationNanos;

    // Used by the translucency sorter, to determine when a section needs sorting again
    public double lastCameraX, lastCameraY, lastCameraZ;

    // Set when the camera crossed one of this section's translucent geometry planes (see TranslucencyTriggerIndex), cleared when the sort task is scheduled; only meaningful for dynamically sorted sections
    public boolean pendingTriggeredSort;

    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        super(chunkX, chunkY, chunkZ);

        this.region = region;

        this.contextData = null;
        this.updateCachedContextDataFlags();
    }

    // Deletes all data attached to this render and drops pending tasks; the object cannot be used afterwards
    public void delete() {
        if (this.buildCancellationToken != null) {
            this.buildCancellationToken.setCancelled();
            this.buildCancellationToken = null;
        }

        this.setInfo(null);
        this.disposed = true;
    }

    // Installs built data; true when the visual flags changed
    public boolean setInfo(@Nullable BuiltRenderSectionData info) {
        boolean changed = !Objects.equals(info, this.contextData);
        if (changed) {
            if (this.contextData == null) {
                this.getRegion().updateSectionLoadTime(this);
            }
            this.contextData = info;
            this.updateCachedContextDataFlags();
        }
        return changed;
    }

    // Whether removed
    public boolean isDisposed() {
        return this.disposed;
    }

    // Whether it has data
    public boolean isBuilt() {
        return this.contextData != null;
    }

    // Owning region
    public RenderRegion getRegion() {
        return this.region;
    }

    public @Nullable BuiltRenderSectionData getBuiltContext() {
        return this.contextData;
    }

    // Refreshes the flags from the data
    public void updateCachedContextDataFlags() {
        this.visualsServiceFlags = this.contextData != null ? this.contextData.getVisualBitmaskForSection() : 0;
        this.hasAnythingToRender = this.visualsServiceFlags != 0;
    }

    // Geometry, sprites or entities
    public boolean hasAnythingToRender() {
        return this.hasAnythingToRender;
    }

    // Records what re-sorting each pass needs
    public void setTranslucencySortStates(@NotNull Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> sortStates) {
        this.translucencySortStates = Map.copyOf(sortStates);

        TranslucentQuadAnalyzer.Level level = TranslucentQuadAnalyzer.Level.NONE;
        boolean needsDynamicSorting = false;

        // The highest level among all sort states
        for (TranslucentQuadAnalyzer.SortState state : sortStates.values()) {
            if (state.level().ordinal() > level.ordinal()) {
                level = state.level();
            }
            needsDynamicSorting |= state.requiresDynamicSorting();
        }

        this.highestSortingLevel = level;
        this.needsDynamicTranslucencySorting = needsDynamicSorting;
    }

    public @Nullable CancellationToken getBuildCancellationToken() {
        return this.buildCancellationToken;
    }

    // So a superseded build can be cancelled
    public void setBuildCancellationToken(@Nullable CancellationToken token) {
        this.buildCancellationToken = token;
    }

    public @Nullable ChunkUpdateType getPendingUpdate() {
        return this.pendingUpdateType;
    }

    // The queued rebuild type, or null
    public void setPendingUpdate(@Nullable ChunkUpdateType type) {
        this.pendingUpdateType = type;
    }

    // Requests a chunk update, possibly "upgrading" an existing pending one; returns true if the pending type actually changed
    public boolean requestUpdate(ChunkUpdateType type) {
        type = ChunkUpdateType.getPromotionUpdateType(this.pendingUpdateType, type);

        if (type != null) {
            this.pendingUpdateType = type;
            return true;
        } else {
            return false;
        }
    }

    // For result filtering
    public int getLastBuiltFrame() {
        return this.lastBuiltFrame;
    }

    // Stamped on install
    public void setLastBuiltFrame(int lastBuiltFrame) {
        this.lastBuiltFrame = lastBuiltFrame;
    }

    // For result filtering
    public int getLastSubmittedFrame() {
        return this.lastSubmittedFrame;
    }

    // Stamped on submit
    public void setLastSubmittedFrame(int lastSubmittedFrame) {
        this.lastSubmittedFrame = lastSubmittedFrame;
    }
}
