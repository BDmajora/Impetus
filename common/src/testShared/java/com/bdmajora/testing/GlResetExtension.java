package com.bdmajora.testing;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GLContext;

// Auto-registered through META-INF/services so every test starts from a clean GL backend and window stub
public final class GlResetExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        TestGl.reset();
        Display.reset();
        GLContext.reset();
    }
}
