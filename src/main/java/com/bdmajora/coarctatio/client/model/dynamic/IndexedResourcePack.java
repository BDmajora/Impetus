package com.bdmajora.coarctatio.client.model.dynamic;

import java.util.Set;

// Implemented on the file and folder packs by the existence-cache mixins; every path the pack holds, so the texture scan reads the index those already built instead of walking the pack again
public interface IndexedResourcePack {
    // Null when the pack could not be indexed
    Set<String> coarctatio$indexedPaths();
}
