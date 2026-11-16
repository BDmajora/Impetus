package com.bdmajora.impetus.umbra.gl.uniform;

// Uniform that can write straight into its program while another program, or none, is bound.
public interface ProgramTargetedUniform {
    // Uploads the current value into the program when it changed, sharing the change test of update.
    void updateIn(int program);
}
