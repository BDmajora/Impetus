package com.bdmajora.extras.client.culling;

// Implemented on Entity and TileEntity: the occlusion thread's latest verdict plus when it was written, so a verdict the thread has not refreshed (world change, thread stall) expires back to visible rather than hiding something
public interface Cullable {
    boolean impetus$isOccluded();

    void impetus$setOccluded(boolean occluded, long stampNanos);

    long impetus$occlusionStamp();
}
