package com.bdmajora.impetus.impl.world.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeColorHelper;
import com.bdmajora.impetus.impl.world.WorldSlice;

// Biome color cache of vintage biomes.
public class BiomeColorCache extends com.bdmajora.impetus.engine.impl.biome.BiomeColorCache<Biome, BiomeColorHelper.ColorResolver> {
    // Reusable biome position.
    private final BlockPos.MutableBlockPos biomeCursor = new BlockPos.MutableBlockPos();

    // Creates a biome color cache from its slice and blend radius.
    public BiomeColorCache(WorldSlice slice, int blendRadius) {
        super(slice::getBiome, blendRadius);
    }

    // Returns the color of a resolver at a position.
    @Override
    protected int resolveColor(BiomeColorHelper.ColorResolver colorResolver, Biome biome, int worldX, int worldY, int worldZ) {
        return colorResolver.getColorAtPos(biome, biomeCursor.setPos(worldX, worldY, worldZ));
    }
}
