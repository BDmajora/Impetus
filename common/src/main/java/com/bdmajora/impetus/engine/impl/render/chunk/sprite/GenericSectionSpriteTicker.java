package com.bdmajora.impetus.engine.impl.render.chunk.sprite;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

import java.util.List;
import java.util.function.Consumer;

// Marks the animated sprites of the visible sections as active.
public class GenericSectionSpriteTicker<T> implements SectionTicker {
    // Shared empty array of sprites.
    private static final Object[] NO_SPRITES = new Object[0];

    // Searches between two prunes of the cache.
    private static final int PRUNE_INTERVAL = 1024;

    // Sprites of the visible sections.
    private volatile ReferenceOpenHashSet<T> sprites = new ReferenceOpenHashSet<>();

    // Action that marks a sprite as active.
    private final Consumer<T> markActive;

    // Cached sprites of each region.
    private final Reference2ObjectOpenHashMap<RenderRegion, CachedRegionSprites> regionCache = new Reference2ObjectOpenHashMap<>();

    // Set that collects the sprites of a region.
    private final ReferenceOpenHashSet<Object> scratch = new ReferenceOpenHashSet<>();

    // Number of searches.
    private int searchIndex;

    // Sprites of a region at a data revision.
    private static final class CachedRegionSprites {
        // Data revision of the cache entry.
        int revision;
        // Sprites of the region.
        Object[] sprites;
        // Search that last used the entry.
        int lastSeenSearch;
    }

    // Creates a ticker and rejects a null action.
    public GenericSectionSpriteTicker(Consumer<T> markActive) {
        this.markActive = java.util.Objects.requireNonNull(markActive, "markActive");
    }

    // Marks every sprite of the visible sections as active.
    @Override
    public void tickVisibleRenders() {
        this.sprites.forEach(this.markActive);
    }

    // Returns the number of active sprites as text.
    @Override
    public String getDebugString() {
        return "A: " + this.sprites.size();
    }

    // Collects the sprites of the visible sections from the new lists.
    @Override
    public void onRenderListUpdated(List<ChunkRenderList> renderLists) {
        java.util.Objects.requireNonNull(renderLists, "renderLists");
        var spriteSet = new ReferenceOpenHashSet<T>(this.sprites.size());
        int search = ++this.searchIndex;

        for (int i = 0; i < renderLists.size(); i++) {
            ChunkRenderList renderList = renderLists.get(i);

            if (renderList.getSectionsWithSpritesCount() == 0) {
                continue;
            }

            var region = renderList.getRegion();
            var cached = this.regionCache.get(region);

            if (cached == null) {
                cached = new CachedRegionSprites();
                cached.revision = region.getDataRevision() - 1;
                this.regionCache.put(region, cached);
            }

            if (cached.revision != region.getDataRevision()) {
                this.recompute(region, cached);
            }

            cached.lastSeenSearch = search;

            var regionSprites = cached.sprites;
            for (int j = 0; j < regionSprites.length; j++) {
                @SuppressWarnings("unchecked")
                T sprite = (T) regionSprites[j];
                spriteSet.add(sprite);
            }
        }

        if ((search & (PRUNE_INTERVAL - 1)) == 0) {
            this.regionCache.values().removeIf(entry -> search - entry.lastSeenSearch > PRUNE_INTERVAL);
        }

        this.sprites = spriteSet;
    }

    // Collects the sprites of every section of a region into its cache entry.
    private void recompute(RenderRegion region, CachedRegionSprites cached) {
        var scratch = this.scratch;

        try {
            for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
                var section = region.getSection(sectionIndex);

                if (section == null) {
                    continue;
                }

                if (!(section.getBuiltContext() instanceof MinecraftBuiltRenderSectionData<?, ?> mcData)) {
                    continue;
                }

                var sprites = mcData.animatedSprites;

                if (!sprites.isEmpty()) {
                    scratch.addAll(sprites);
                }
            }

            cached.revision = region.getDataRevision();
            cached.sprites = scratch.isEmpty() ? NO_SPRITES : scratch.toArray();
        } finally {
            scratch.clear();
        }
    }
}
