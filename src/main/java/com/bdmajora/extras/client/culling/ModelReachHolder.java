package com.bdmajora.extras.client.culling;

// Entity that remembers how far its equipment models reach.
public interface ModelReachHolder {
    // Returns the cached reach or null before the first measurement.
    ModelReachState impetus$modelReach();

    // Stores the cached reach.
    void impetus$setModelReach(ModelReachState state);
}
