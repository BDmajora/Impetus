package com.bdmajora.coarctatio.client.model.dynamic;

import java.util.Set;

// Resource pack that lists its files.
public interface IndexedResourcePack {
    // Returns the paths of every file in the pack.
    Set<String> coarctatio$indexedPaths();
}
