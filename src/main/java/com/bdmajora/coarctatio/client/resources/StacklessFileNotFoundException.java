package com.bdmajora.coarctatio.client.resources;

import java.io.FileNotFoundException;

// File not found error that carries no stack trace.
public final class StacklessFileNotFoundException extends FileNotFoundException {
    // Creates the error with a message.
    public StacklessFileNotFoundException(String message) {
        super(message);
    }

    // Returns the error without recording a stack trace.
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
