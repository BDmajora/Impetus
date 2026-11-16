package com.bdmajora.impetus.engine.impl.gl;

// Base class of GL objects that guards the handle against use after delete.
public abstract class GlObject {
    // Handle value of an object that holds no GL name.
    private static final int INVALID_HANDLE = 0;

    // GL name or the invalid handle after deletion.
    private int handle = INVALID_HANDLE;

    // Creates an object without a GL name.
    protected GlObject() {

    }

    // Stores the GL name.
    protected final void setHandle(int handle) {
        this.handle = handle;
    }

    // Returns the GL name and rejects a deleted object.
    public final int handle() {
        this.checkHandle();

        return this.handle;
    }

    // Rejects an object without a valid handle.
    protected final void checkHandle() {
        if (!this.isHandleValid()) {
            throw new IllegalStateException("Handle is not valid");
        }
    }

    // Returns whether the object holds a GL name.
    protected final boolean isHandleValid() {
        return this.handle != INVALID_HANDLE;
    }

    // Frees the GL object once and keeps the handle when freeing fails.
    public final void delete() {
        if (!this.isHandleValid()) {
            return;
        }

        this.destroyInternal();
        this.handle = INVALID_HANDLE;
    }

    // Frees the GL object through delete.
    @Deprecated
    public final void destroy() {
        this.delete();
    }

    // Frees the GL object while the handle is still valid.
    protected abstract void destroyInternal();
}
