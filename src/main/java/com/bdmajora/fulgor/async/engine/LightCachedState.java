package com.bdmajora.fulgor.async.engine;

// Implemented by BlockStateContainer$StateImplementation: the packed LightInfo for the state, computed on first use (Starlight's per-state opacity cache)
public interface LightCachedState {
    int fulgor$lightInfo();
}
