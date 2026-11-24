package com.bdmajora.extras;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;

// Retries the unload of chunks no player watches.
public final class UnwatchedChunks {
    // Prevents instantiation
    private UnwatchedChunks() {
    }

    // Queues every loaded chunk outside the view of all players. Any read of a chunk between its queueing and the tick that
    // would unload it cancels the unload for good; vanilla only queues such chunks again at the end of an autosave.
    public static void queueUnload(WorldServer world) {
        if (world.disableLevelSaving || !world.getChunkProvider().canSave()) {
            return;
        }
        for (Chunk chunk : new ArrayList<>(world.getChunkProvider().getLoadedChunks())) {
            if (chunk != null && !world.getPlayerChunkMap().contains(chunk.x, chunk.z)) {
                world.getChunkProvider().queueUnload(chunk);
            }
        }
    }
}
