package com.bdmajora.impetus.umbra.gl.program;


// One deferred glUniform1i(location, value), issued on a program's first update() since the call writes into the bound program and nothing is bound at build time; shared by the sampler and image builders
record Uniform1iCall(int location, int value) {
}
