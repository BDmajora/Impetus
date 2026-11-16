package com.bdmajora.impetus.engine.impl.render.chunk.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;

import java.util.*;
import java.util.function.Consumer;

// Section data with the sprites and block entities of the game.
public class MinecraftBuiltRenderSectionData<SPRITE, BLOCKENTITY> extends BuiltRenderSectionData {
    // Sprites that animate.
    public Collection<SPRITE> animatedSprites = new ObjectOpenHashSet<>();
    // Block entities that are culled with the section.
    public List<BLOCKENTITY> culledBlockEntities = new ArrayList<>();
    // Block entities that render whatever the visibility.
    public List<BLOCKENTITY> globalBlockEntities = new ArrayList<>();

    // Makes the sprite and block entity collections unmodifiable.
    @Override
    public void bake() {
        super.bake();
        animatedSprites = List.copyOf(animatedSprites);
        culledBlockEntities = List.copyOf(culledBlockEntities);
        globalBlockEntities = List.copyOf(globalBlockEntities);
    }

    // Returns the bit mask of geometry and sprites and block entities.
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

    // Compares the contents.
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MinecraftBuiltRenderSectionData<?, ?> that = (MinecraftBuiltRenderSectionData<?, ?>) o;
        return Objects.equals(animatedSprites, that.animatedSprites) && Objects.equals(culledBlockEntities, that.culledBlockEntities) && Objects.equals(globalBlockEntities, that.globalBlockEntities);
    }

    // Combines the contents.
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), animatedSprites, culledBlockEntities, globalBlockEntities);
    }

    // Visits the data of every visible section with block entities and skips sections without such data.
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

    // Visits the data of the sections with global block entities.
    public static void forEachGlobalSectionData(Collection<RenderSection> globalSections, Consumer<MinecraftBuiltRenderSectionData<?, ?>> visitor) {
        for (var renderSection : globalSections) {
            if (renderSection.getBuiltContext() instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                visitor.accept(mcData);
            }
        }
    }
}
