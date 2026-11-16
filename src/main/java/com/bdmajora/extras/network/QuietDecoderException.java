package com.bdmajora.extras.network;

import io.netty.handler.codec.DecoderException;

// Decoder exception without a stack trace.
public final class QuietDecoderException extends DecoderException {
    // Creates a quiet decoder exception from its message.
    public QuietDecoderException(String message) {
        super(message);
    }

    // Handles the fill in stack trace.
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
