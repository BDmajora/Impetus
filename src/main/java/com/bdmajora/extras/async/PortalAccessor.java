package com.bdmajora.extras.async;

// Exposes Entity.inPortal: an entity mid-transit rewrites two worlds' entity lists when its counter runs out, so it ticks on the main thread until it is through
public interface PortalAccessor {
    boolean impetus$isInPortal();
}
