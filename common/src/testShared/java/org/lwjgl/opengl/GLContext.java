package org.lwjgl.opengl;

// Test stand-in for LWJGL2's GLContext, whose real static initialiser loads natives
public final class GLContext {
    public static ContextCapabilities capabilities = new ContextCapabilities();

    private GLContext() {}

    public static ContextCapabilities getCapabilities() {
        return capabilities;
    }

    public static void reset() {
        capabilities = new ContextCapabilities();
    }
}
