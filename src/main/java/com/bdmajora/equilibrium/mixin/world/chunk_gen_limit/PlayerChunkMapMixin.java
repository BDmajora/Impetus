package com.bdmajora.equilibrium.mixin.world.chunk_gen_limit;

import net.minecraft.server.management.PlayerChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// PlayerChunkMap.tick generates up to 49 chunks per tick as long as it stays under 50 ms, which is the whole tick budget and the source of the stutter while flying (Universal Tweaks' "Chunk Gen Limit", Barteks2x's numbers); this caps a tick at 24 chunks and 25 ms so loading spreads over more ticks and entity ticking keeps its share. Off by default since it slows terrain loading on a fast server
@Mixin(PlayerChunkMap.class)
public abstract class PlayerChunkMapMixin {
    private static final int CHUNKS_PER_TICK = 24;
    private static final long NANOS_PER_TICK = 25_000_000L;

    // The loop counter compares against 49, i.e. the 50th chunk stops it
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 49))
    private int equilibrium$chunkLimit(int vanilla) {
        return CHUNKS_PER_TICK - 1;
    }

    @ModifyConstant(method = "tick", constant = @Constant(longValue = 50000000L))
    private long equilibrium$timeLimit(long vanilla) {
        return NANOS_PER_TICK;
    }
}
