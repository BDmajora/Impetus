package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.iterator.ReversibleObjectArrayIterator;

import java.util.Set;

public class SortedRenderLists implements ChunkRenderListIterable {
    private static final SortedRenderLists EMPTY = new SortedRenderLists(new ObjectArrayList<>());

    private final ObjectArrayList<ChunkRenderList> lists;
    private final ReferenceOpenHashSet<TerrainRenderPass> passes;
    private final boolean hasSortedPass;

    SortedRenderLists(ObjectArrayList<ChunkRenderList> lists) {
        this.lists = lists;
        this.passes = getAllPassesInLists(lists);
        this.hasSortedPass = anySorted(this.passes);
    }

    private static boolean anySorted(Set<TerrainRenderPass> passes) {
        for (TerrainRenderPass pass : passes) {
            if (pass.isSorted()) {
                return true;
            }
        }
        return false;
    }

    // Union of passes any listed section uses
    private static ReferenceOpenHashSet<TerrainRenderPass> getAllPassesInLists(ObjectArrayList<ChunkRenderList> lists) {
        ReferenceOpenHashSet<TerrainRenderPass> usedPasses = new ReferenceOpenHashSet<>();

        for (var list : lists) {
            usedPasses.addAll(list.getRegion().getPasses());
        }

        return usedPasses;
    }

    // Reverse for translucent passes, which draw back-to-front
    @Override
    public ReversibleObjectArrayIterator<ChunkRenderList> iterator(boolean reverse) {
        return new ReversibleObjectArrayIterator<>(this.lists, reverse);
    }

    // Whether anything uses the pass, so an empty pass can be skipped
    @Override
    public boolean hasPass(TerrainRenderPass pass) {
        return this.passes.contains(pass);
    }

    // Every pass in use
    public Set<TerrainRenderPass> getPasses() {
        return this.passes;
    }

    // Whether any listed section draws a pass that needs translucency sorting; decided once per list, since the manager asks every frame
    public boolean hasSortedPass() {
        return this.hasSortedPass;
    }

    // For frames with nothing visible
    public static SortedRenderLists empty() {
        return EMPTY;
    }
}
