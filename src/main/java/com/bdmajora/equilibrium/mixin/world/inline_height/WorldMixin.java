package com.bdmajora.equilibrium.mixin.world.inline_height;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements ChunkAccess {
    // Replaces the whole method getHeight.
    @Overwrite
    public int getHeight(int x, int z) {
        World world = (World) (Object) this;

        int height;

        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            Chunk chunk = this.equilibrium$getLoadedChunk(x >> 4, z >> 4);

            height = chunk == null ? 0 : chunk.getHeightValue(x & 15, z & 15);
        } else {
            height = world.getSeaLevel() + 1;
        }

        return height;
    }
}
