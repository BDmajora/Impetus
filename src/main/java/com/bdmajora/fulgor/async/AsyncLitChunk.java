package com.bdmajora.fulgor.async;

// Implemented by Chunk under the async engine: the SWMR light storage, its emptiness maps and the readiness flags the engine drives
public interface AsyncLitChunk {
    // True once initial propagation and edge reconciliation are done; light read before this is provisional
    boolean fulgor$isLightReady();

    void fulgor$setLightReady(boolean ready);

    // Internal gate: true after propagation so neighbours can reconcile against this chunk while the public flag stays false until its edges settle
    boolean fulgor$isLightUsable();

    void fulgor$setLightUsable(boolean usable);

    // Set when valid saved light was restored from NBT, so onLoad skips the initial pass
    void fulgor$setSavedLightValid(boolean valid);

    boolean fulgor$hasSavedLightValid();

    // Copies the visible SWMR data into the vanilla nibble arrays, for packets and code reading section storage directly
    void fulgor$syncLightToVanilla();

    SWMRNibbleArray[] fulgor$getBlockNibbles();

    void fulgor$setBlockNibbles(SWMRNibbleArray[] nibbles);

    boolean[] fulgor$getBlockEmptinessMap();

    void fulgor$setBlockEmptinessMap(boolean[] emptinessMap);

    SWMRNibbleArray[] fulgor$getSkyNibbles();

    void fulgor$setSkyNibbles(SWMRNibbleArray[] nibbles);

    boolean[] fulgor$getSkyEmptinessMap();

    void fulgor$setSkyEmptinessMap(boolean[] emptinessMap);
}
