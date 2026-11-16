package com.bdmajora.impetus.umbra.gl.program;

// Exception thrown when a program fails to build.
public class ProgramCreationException extends RuntimeException {
    // Creates a program creation exception from its message.
    public ProgramCreationException(String message) {
        super(message);
    }
}
