package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.iterator.ReversibleObjectArrayIterator;

import java.util.Set;

// Render lists in draw order with the passes they use.
public class SortedRenderLists implements ChunkRenderListIterable {
    // Shared instance without lists.
    private static final SortedRenderLists EMPTY = new SortedRenderLists(new ObjectArrayList<>());

    // Lists in draw order.
    private final ObjectArrayList<ChunkRenderList> lists;
    // Passes used by the lists.
    private final ReferenceOpenHashSet<TerrainRenderPass> passes;
    // Whether a pass needs sorting.
    private final boolean hasSortedPass;

    // Creates the lists and finds their passes.
    SortedRenderLists(ObjectArrayList<ChunkRenderList> lists) {
        this.lists = lists;
        this.passes = getAllPassesInLists(lists);
        this.hasSortedPass = anySorted(this.passes);
    }

    // Returns whether any pass is sorted.
    private static boolean anySorted(Set<TerrainRenderPass> passes) {
        for (TerrainRenderPass pass : passes) {
            if (pass.isSorted()) {
                return true;
            }
        }
        return false;
    }

    // Returns the passes used by any list.
    private static ReferenceOpenHashSet<TerrainRenderPass> getAllPassesInLists(ObjectArrayList<ChunkRenderList> lists) {
        ReferenceOpenHashSet<TerrainRenderPass> usedPasses = new ReferenceOpenHashSet<>();

        for (var list : lists) {
            usedPasses.addAll(list.getRegion().getPasses());
        }

        return usedPasses;
    }

    // Returns the number of lists.
    @Override
    public int getNumRegions() {
        return this.lists.size();
    }

    // Returns an iterator over the lists in draw order or the reverse order.
    @Override
    public ReversibleObjectArrayIterator<ChunkRenderList> iterator(boolean reverse) {
        return new ReversibleObjectArrayIterator<>(this.lists, reverse);
    }

    // Returns whether any list uses the pass.
    @Override
    public boolean hasPass(TerrainRenderPass pass) {
        return this.passes.contains(pass);
    }

    // Returns the passes in use.
    public Set<TerrainRenderPass> getPasses() {
        return this.passes;
    }

    // Returns whether a pass needs sorting.
    public boolean hasSortedPass() {
        return this.hasSortedPass;
    }

    // Returns the shared instance without lists.
    public static SortedRenderLists empty() {
        return EMPTY;
    }
}
