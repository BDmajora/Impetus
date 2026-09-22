package com.bdmajora.impetus.engine.impl.gl;

// Base for the engine's GL objects; the handle accessor checks validity to catch use-after-delete, but a copied-out int handle escapes it, so it is a guard rail not a guarantee
public abstract class GlObject {
    private static final int INVALID_HANDLE = 0;

    private int handle = INVALID_HANDLE;

    protected GlObject() {

    }

    // Called once by the subclass constructor
    protected final void setHandle(int handle) {
        this.handle = handle;
    }

    // The GL name; throws once deleted
    public final int handle() {
        this.checkHandle();

        return this.handle;
    }

    // Throws on use after delete
    protected final void checkHandle() {
        if (!this.isHandleValid()) {
            throw new IllegalStateException("Handle is not valid");
        }
    }

    // Whether not yet deleted
    protected final boolean isHandleValid() {
        return this.handle != INVALID_HANDLE;
    }

    // Frees once; a second call is a no-op rather than a use-after-delete throw from the handle check
    public final void delete() {
        if (!this.isHandleValid()) {
            return;
        }
        this.destroyInternal();
        this.handle = INVALID_HANDLE;
    }

    // Alias of delete
    @Deprecated // kept around to avoid huge diffs in old Umbra code
    public final void destroy() {
        this.delete();
    }

    protected abstract void destroyInternal();
}
