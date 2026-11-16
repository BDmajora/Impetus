package com.bdmajora.fulgor.async;

// Contract of the async lit chunk.
public interface AsyncLitChunk {
    // Returns whether light ready.
    boolean fulgor$isLightReady();

    // Stores the light ready.
    void fulgor$setLightReady(boolean ready);

    // Returns whether light usable.
    boolean fulgor$isLightUsable();

    // Stores the light usable.
    void fulgor$setLightUsable(boolean usable);

    // Stores the saved light valid.
    void fulgor$setSavedLightValid(boolean valid);

    // Returns whether saved light valid.
    boolean fulgor$hasSavedLightValid();

    // Synchronizes the light to vanilla.
    void fulgor$syncLightToVanilla();

    // Returns the block nibbles.
    SWMRNibbleArray[] fulgor$getBlockNibbles();

    // Stores the block nibbles.
    void fulgor$setBlockNibbles(SWMRNibbleArray[] nibbles);

    // Returns the block emptiness map.
    boolean[] fulgor$getBlockEmptinessMap();

    // Stores the block emptiness map.
    void fulgor$setBlockEmptinessMap(boolean[] emptinessMap);

    // Returns the sky nibbles.
    SWMRNibbleArray[] fulgor$getSkyNibbles();

    // Stores the sky nibbles.
    void fulgor$setSkyNibbles(SWMRNibbleArray[] nibbles);

    // Returns the sky emptiness map.
    boolean[] fulgor$getSkyEmptinessMap();

    // Stores the sky emptiness map.
    void fulgor$setSkyEmptinessMap(boolean[] emptinessMap);
}
