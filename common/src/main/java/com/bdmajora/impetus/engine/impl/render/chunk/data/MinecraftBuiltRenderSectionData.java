package com.bdmajora.impetus.engine.impl.render.chunk.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;

import java.util.*;
import java.util.function.Consumer;

public class MinecraftBuiltRenderSectionData<SPRITE, BLOCKENTITY> extends BuiltRenderSectionData {
    public Collection<SPRITE> animatedSprites = new ObjectOpenHashSet<>();
    public List<BLOCKENTITY> culledBlockEntities = new ArrayList<>();
    public List<BLOCKENTITY> globalBlockEntities = new ArrayList<>();

    // Freezes the block entity and sprite lists
    @Override
    public void bake() {
        super.bake();
        animatedSprites = List.copyOf(animatedSprites);
        culledBlockEntities = List.copyOf(culledBlockEntities);
        globalBlockEntities = List.copyOf(globalBlockEntities);
    }

    // Which of geometry, sprites and entities the section has
    @Override
    public int getVisualBitmaskForSection() {
        int flags = super.getVisualBitmaskForSection();
        if (!animatedSprites.isEmpty()) {
            flags |= (1 << RenderVisualsService.HAS_SPRITES);
        }
        if (!culledBlockEntities.isEmpty() || !globalBlockEntities.isEmpty()) {
            flags |= (1 << RenderVisualsService.HAS_BLOCK_ENTITIES);
        }
        return flags;
    }

    // By contents
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MinecraftBuiltRenderSectionData<?, ?> that = (MinecraftBuiltRenderSectionData<?, ?>) o;
        return Objects.equals(animatedSprites, that.animatedSprites) && Objects.equals(culledBlockEntities, that.culledBlockEntities) && Objects.equals(globalBlockEntities, that.globalBlockEntities);
    }

    // By contents
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), animatedSprites, culledBlockEntities, globalBlockEntities);
    }

    // Visits the built data of every visible section flagged as holding block entities, in render-list order; sections whose data is not ours (or that are gone) are skipped
    public static void forEachVisibleSectionData(SortedRenderLists renderLists, Consumer<MinecraftBuiltRenderSectionData<?, ?>> visitor) {
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSection = renderRegion.getSection(renderSectionIterator.nextByteAsInt());

                if (renderSection != null && renderSection.getBuiltContext() instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                    visitor.accept(mcData);
                }
            }
        }
    }

    // Same for the sections flagged as holding global block entities (rendered regardless of visibility, like beacons)
    public static void forEachGlobalSectionData(Collection<RenderSection> globalSections, Consumer<MinecraftBuiltRenderSectionData<?, ?>> visitor) {
        for (var renderSection : globalSections) {
            if (renderSection.getBuiltContext() instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                visitor.accept(mcData);
            }
        }
    }
}
