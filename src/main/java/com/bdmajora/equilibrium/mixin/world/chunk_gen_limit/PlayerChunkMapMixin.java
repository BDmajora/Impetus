package com.bdmajora.equilibrium.mixin.world.chunk_gen_limit;

import net.minecraft.server.management.PlayerChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes PlayerChunkMap.
@Mixin(PlayerChunkMap.class)
public abstract class PlayerChunkMapMixin {
    // Chunks generated per tick.
    private static final int CHUNKS_PER_TICK = 24;
    // Time budget of chunk generation per tick.
    private static final long NANOS_PER_TICK = 25_000_000L;

    // Changes a constant in tick.
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 49))
    private int equilibrium$chunkLimit(int vanilla) {
        return CHUNKS_PER_TICK - 1;
    }

    // Changes a constant in tick.
    @ModifyConstant(method = "tick", constant = @Constant(longValue = 50000000L))
    private long equilibrium$timeLimit(long vanilla) {
        return NANOS_PER_TICK;
    }
}
