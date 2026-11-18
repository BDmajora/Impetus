package com.bdmajora.extras.client.culling;

// Object that can be hidden by occlusion.
public interface Cullable {
    // Returns whether the object is hidden.
    boolean impetus$isOccluded();

    // Stores the hidden state and the time of the test.
    void impetus$setOccluded(boolean occluded, long stampNanos);

    // Returns the time of the last test.
    long impetus$occlusionStamp();

    // Returns when the worker first saw the object hidden or zero while it is seen.
    long impetus$hiddenSince();

    // Stores when the worker first saw the object hidden.
    void impetus$setHiddenSince(long nanos);
}
