package com.bdmajora.coarctatio.state;

// Object that holds state only needed while models bake.
public interface BakeStateReleasable {
    // Drops the baking state and returns how many entries were released.
    int coarctatio$releaseBakeryState();
}
