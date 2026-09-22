package com.bdmajora.impetus.api.eventbus;

// Base class for all Impetus events; on (Neo)Forge this would extend their native event class to hook their bus, on Fabric it extends nothing
public abstract class ImpetusEvent {
    // Defaults to no
    public boolean isCancelable() {
        return false;
    }

    private boolean canceled;

    // Whether a handler cancelled it
    public boolean isCanceled() {
        return canceled;
    }

    // Throws on a non-cancelable event, so a handler cannot believe it stopped something the poster never checks
    public void setCanceled(boolean cancel) {
        if (!isCancelable()) {
            throw new UnsupportedOperationException("Event " + getClass().getName() + " is not cancelable");
        }
        canceled = cancel;
    }
}
