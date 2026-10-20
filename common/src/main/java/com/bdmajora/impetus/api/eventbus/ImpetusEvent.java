package com.bdmajora.impetus.api.eventbus;

// Base class for all Impetus events
public abstract class ImpetusEvent {
    // Returns false unless a subclass overrides it to allow canceling
    public boolean isCancelable() {
        return false;
    }

    // Set when a listener cancels the event and visible to every thread
    volatile boolean canceled;

    // Number of posts of this event currently running on the posting thread
    int postDepth;

    // Returns true if a listener canceled the event
    public boolean isCanceled() {
        return canceled;
    }

    // Sets the canceled state and throws when canceling an event that is not cancelable
    public void setCanceled(boolean cancel) {
        // Allows clearing the canceled state on any event
        if (cancel && !isCancelable()) {
            throw new UnsupportedOperationException("Event " + getClass().getName() + " is not cancelable");
        }
        canceled = cancel;
    }
}