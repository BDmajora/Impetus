package com.bdmajora.impetus.umbra.gl.shader;

// Exception thrown when a stage fails to compile.
public class ShaderCompileException extends RuntimeException {
    // Name of the source file.
    private final String filename;
    // Driver log of the failure.
    private final String error;

    // Creates a shader compile exception from its filename and error.
    public ShaderCompileException(String filename, String error) {
        super("Failed to compile " + filename + ": " + error);
        this.filename = filename;
        this.error = error;
    }

    // Returns the filename.
    public String getFilename() {
        return this.filename;
    }

    // Returns the error.
    public String getError() {
        return this.error;
    }
}
