package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.client.multiplayer.WorldClient;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes WorldClient.
@Mixin(WorldClient.class)
public class WorldClientMixin implements ChunkTrackerHolder {
    // Tracker of loaded chunks.
    private final ChunkTracker impetus$tracker = new ChunkTracker();

    // Returns the tracker.
    @Override
    public ChunkTracker impetus$getTracker() {
        return impetus$tracker;
    }
}
