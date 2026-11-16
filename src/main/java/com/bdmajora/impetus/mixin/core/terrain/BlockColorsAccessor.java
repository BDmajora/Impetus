package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraftforge.registries.IRegistryDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Mixin that changes BlockColors.
@Mixin(BlockColors.class)
public interface BlockColorsAccessor {
    // Gives access to the field get block color map.
    @Accessor("blockColorMap")
    Map<IRegistryDelegate<Block>, IBlockColor> getBlockColorMap();
}
