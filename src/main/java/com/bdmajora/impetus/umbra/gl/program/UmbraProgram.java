package com.bdmajora.impetus.umbra.gl.program;

// Program with the draw buffers its fragment stage writes.
public class UmbraProgram {
    // Linked program.
    private final GlProgram program;
    // Draw buffers of the program.
    private final int[] drawBuffers;

    // Creates a program with a copy of its draw buffers or the default ones.
    public UmbraProgram(GlProgram program, int[] drawBuffers) {
        this.program = program;
        this.drawBuffers = drawBuffers == null ? DrawBuffers.DEFAULT.clone() : drawBuffers.clone();
    }

    // Returns the linked program.
    public GlProgram getProgram() {
        return this.program;
    }

    // Returns a copy of the draw buffers.
    public int[] getDrawBuffers() {
        return this.drawBuffers.clone();
    }

    // Binds the program.
    public void bind() {
        this.program.bind();
    }

    // Unbinds the program.
    public void unbind() {
        this.program.unbind();
    }

    // Deletes the program.
    public void destroy() {
        this.program.destroy();
    }
}
