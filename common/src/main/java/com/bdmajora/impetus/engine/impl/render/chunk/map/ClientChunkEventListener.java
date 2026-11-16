package com.bdmajora.impetus.engine.impl.render.chunk.map;

// Receiver of chunk events of the client.
public interface ClientChunkEventListener {
    // Receives the new center of the loaded area.
    void updateMapCenter(int chunkX, int chunkZ);

    // Receives the new load distance.
    void updateLoadDistance(int loadDistance);

    // Receives flags that a chunk gained.
    void onChunkStatusAdded(int x, int z, int flags);
    // Receives flags that a chunk lost.
    void onChunkStatusRemoved(int x, int z, int flags);
}
