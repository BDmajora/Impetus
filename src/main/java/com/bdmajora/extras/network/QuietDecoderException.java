package com.bdmajora.extras.network;

import io.netty.handler.codec.DecoderException;

// A decoder failure the peer caused, thrown as a shared instance with no stack trace since the trace would only ever point at the decoder (Krypton)
public final class QuietDecoderException extends DecoderException {
    public QuietDecoderException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
