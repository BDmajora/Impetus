package com.bdmajora.impetus.engine.impl.render.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ShaderLoaderTest {
    @Test
    void loadsBundledSourcesResolvingImportsAndDefines() {
        String plain = ShaderLoader.getShaderSource("impetus:blocks/block_layer_opaque.fsh");
        assertTrue(plain.contains("#import <impetus:include/fog.glsl>"));
        assertThrows(RuntimeException.class, () -> ShaderLoader.getShaderSource("missing.vsh"));
        GlShader shader = ShaderLoader.loadShader(ShaderType.FRAGMENT, "impetus:blocks/block_layer_opaque.fsh", ShaderConstants.builder().add("TEST_DEFINE").build());
        assertEquals("impetus:blocks/block_layer_opaque.fsh", shader.getName());
        ArgumentCaptor<CharSequence> source = ArgumentCaptor.forClass(CharSequence.class);
        Mockito.verify(TestGl.gl()).glShaderSourceSafe(Mockito.anyInt(), source.capture());
        String compiled = source.getValue().toString();
        assertTrue(compiled.contains("#define TEST_DEFINE"));
        assertFalse(compiled.contains("#import"));
        new ShaderLoader();
    }
}
