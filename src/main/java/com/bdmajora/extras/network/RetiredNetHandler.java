package com.bdmajora.extras.network;

// Contract of the retired net handler.
public interface RetiredNetHandler {
    // Returns whether the handler was replaced and must ignore late packets.
    boolean impetus$isRetired();
}
