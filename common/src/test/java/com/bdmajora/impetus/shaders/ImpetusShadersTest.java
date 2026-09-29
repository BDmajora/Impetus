package com.bdmajora.impetus.shaders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ImpetusShadersTest {
    @Test
    void exposesOneLogger() {
        assertSame(ImpetusShaders.logger, ImpetusShaders.logger());
        new ImpetusShaders();
    }
}
