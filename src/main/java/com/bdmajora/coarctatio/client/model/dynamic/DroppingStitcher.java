package com.bdmajora.coarctatio.client.model.dynamic;

import java.util.Set;

// Implemented on Stitcher by mixin; lets the atlas be retried with fewer sprites when the scan over-collected and the first stitch did not fit
public interface DroppingStitcher {
    // Removes every sprite the scan registered weakly that no model turned out to reference
    void coarctatio$retainSprites(Set<String> referencedNames);

    // Removes the largest remaining sprite; false when nothing is left to drop
    boolean coarctatio$dropLargestSprite();
}
