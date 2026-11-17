package com.bdmajora.impetus.umbra.gl.uniform;

// Uniform that writes straight into its program.
public interface ProgramTargetedUniform {
    // Uploads the current value into the program when it changed.
    void updateIn(int program);
}
