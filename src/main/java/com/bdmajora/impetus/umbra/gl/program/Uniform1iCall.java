package com.bdmajora.impetus.umbra.gl.program;

import com.github.bsideup.jabel.Desugar;

// One deferred glUniform1i(location, value), issued on a program's first update() since the call writes into the bound program and nothing is bound at build time; shared by the sampler and image builders
@Desugar
record Uniform1iCall(int location, int value) {
}
