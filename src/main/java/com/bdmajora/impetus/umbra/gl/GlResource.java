package com.bdmajora.impetus.umbra.gl;

// Base of a GL object with one name.
public abstract class GlResource {
    // Name of the GL object.
    private int handle;
    // Flag set after the object is deleted.
    private boolean destroyed;

    // Creates a resource without a name.
    protected GlResource() {
        this.handle = -1;
    }

    // Stores the GL name.
    protected final void setHandle(int handle) {
        this.handle = handle;
    }

    // Returns the GL name and rejects a deleted resource.
    public final int getGlId() {
        if (this.destroyed) {
            throw new IllegalStateException("Tried to use a destroyed GL resource (" + getClass().getSimpleName() + ")");
        }
        return this.handle;
    }

    // Returns whether the resource was deleted.
    public final boolean isDestroyed() {
        return this.destroyed;
    }

    // Deletes the object once and keeps it usable when the delete throws.
    public final void destroy() {
        if (this.destroyed) {
            return;
        }
        destroyInternal();
        this.destroyed = true;
        this.handle = -1;
    }

    // Deletes the GL object.
    protected abstract void destroyInternal();
}
