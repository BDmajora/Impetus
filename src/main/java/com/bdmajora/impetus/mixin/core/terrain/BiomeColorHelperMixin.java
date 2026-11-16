package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;

// Mixin that changes BiomeColorHelper.
@Mixin(value = BiomeColorHelper.class, priority = 1200)
public class BiomeColorHelperMixin {
    // Replaces the whole method getColorAtPos.
    @Overwrite
    private static int getColorAtPos(IBlockAccess blockAccess, BlockPos pos, BiomeColorHelper.ColorResolver colorResolver)
    {
        if (blockAccess instanceof ImpetusBlockAccess) {
            return ((ImpetusBlockAccess)blockAccess).getBlockTint(pos, colorResolver);
        }

        int radius = ImpetusVintage.options().quality.legacyBiomeBlendRadius;
        if (radius == 0) {
            return colorResolver.getColorAtPos(blockAccess.getBiome(pos), pos);
        } else {
            int blockCount = (radius * 2 + 1) * (radius * 2 + 1);

            int i = 0;
            int j = 0;
            int k = 0;

            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            for(int z = -radius; z <= radius; z++) {
                for(int x = -radius; x <= radius; x++) {
                    mutablePos.setPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                    int l = colorResolver.getColorAtPos(blockAccess.getBiome(mutablePos), mutablePos);
                    i += (l & 16711680) >> 16;
                    j += (l & 65280) >> 8;
                    k += l & 255;
                }
            }

            return (i / blockCount & 255) << 16 | (j / blockCount & 255) << 8 | k / blockCount & 255;
        }
    }
}