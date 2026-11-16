package com.bdmajora.fulgor.mixin.async.world;

import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that exposes the palette of a section's block storage, so the light engine can skip sections without emitters.
@Mixin(BlockStateContainer.class)
public interface BlockStateContainerPaletteAccessor {
    // Gives access to the field palette.
    @Accessor("palette")
    IBlockStatePalette fulgor$getPalette();
}
