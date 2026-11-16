package com.bdmajora.equilibrium.common.hopper;

// Hopper that keeps one inventory cache per side.
public interface HopperCacheHolder {
    // Returns the cache of the inventory above.
    HopperInventoryCache equilibrium$sourceCache();

    // Returns the cache of the inventory that receives items.
    HopperInventoryCache equilibrium$destinationCache();
}
