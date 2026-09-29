package com.bdmajora.impetus.engine.impl.render;

import me.cortex.nvidium.Nvidium;
import net.irisshaders.iris.api.v0.IrisApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShaderModBridgeTest {
    @Test
    void bindsToTheShaderApiStubOnTheTestClasspath() {
        assertTrue(ShaderModBridge.isShaderModPresent());
        IrisApi.shadersInUse = true;
        assertTrue(ShaderModBridge.areShadersEnabled());
        IrisApi.shadersInUse = false;
        assertFalse(ShaderModBridge.areShadersEnabled());
        Nvidium.IS_ENABLED = true;
        assertTrue(ShaderModBridge.isNvidiumEnabled());
        Nvidium.IS_ENABLED = false;
        assertFalse(ShaderModBridge.isNvidiumEnabled());
        Object parent = new Object();
        assertEquals("screen", ShaderModBridge.openShaderScreen(parent));
        assertSame(parent, IrisApi.lastParent);
        new ShaderModBridge();
    }
}
