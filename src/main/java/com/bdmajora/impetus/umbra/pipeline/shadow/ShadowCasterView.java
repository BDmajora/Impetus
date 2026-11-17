package com.bdmajora.impetus.umbra.pipeline.shadow;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import java.util.function.Consumer;

// Sections the shadow search kept.
public interface ShadowCasterView {
    // Returns whether the entity overlaps a section the shadow search kept.
    boolean isShadowCasterVisible(Entity entity);

    // Visits the block entities of the kept sections and then every global one.
    void forEachShadowBlockEntity(Consumer<TileEntity> visitor);
}
