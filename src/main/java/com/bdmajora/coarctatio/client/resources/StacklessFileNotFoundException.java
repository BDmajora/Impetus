package com.bdmajora.coarctatio.client.resources;

import java.io.FileNotFoundException;

// The resource manager reports a missing file by throwing, and model loading probes for files that mostly do not exist (.mcmeta beside every texture, armature files beside every model, optional overrides), so it throws tens of thousands of times per load; filling the stack trace is nearly the whole cost of each. Callers only ever catch these by type
public final class StacklessFileNotFoundException extends FileNotFoundException {
    public StacklessFileNotFoundException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
