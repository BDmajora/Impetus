package com.bdmajora.coarctatio.client.model.dynamic;

import java.util.Set;

// Stitcher that can discard sprites.
public interface DroppingStitcher {
    // Keeps only the sprites with the names.
    void coarctatio$retainSprites(Set<String> referencedNames);

    // Discards the largest sprite and returns whether one was discarded.
    boolean coarctatio$dropLargestSprite();
}
