package com.bdmajora.impetus.impl.world.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeColorHelper;
import com.bdmajora.impetus.impl.world.WorldSlice;

// Caches biome color blending per-block to avoid recomputing the blend radius every lookup
public class BiomeColorCache extends com.bdmajora.impetus.engine.impl.biome.BiomeColorCache<Biome, BiomeColorHelper.ColorResolver> {
    // Reused to avoid allocating a BlockPos per color resolve
    private final BlockPos.MutableBlockPos biomeCursor = new BlockPos.MutableBlockPos();

    public BiomeColorCache(WorldSlice slice, int blendRadius) {
        super(slice::getBiome, blendRadius);
    }

    // Asks vanilla's resolver for one biome's colour at a position
    @Override
    protected int resolveColor(BiomeColorHelper.ColorResolver colorResolver, Biome biome, int worldX, int worldY, int worldZ) {
        return colorResolver.getColorAtPos(biome, biomeCursor.setPos(worldX, worldY, worldZ));
    }
}
