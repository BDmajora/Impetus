package com.bdmajora.impetus.engine.impl.gpu.device;

// Graphics APIs the engine can run on.
public enum GraphicsBackend {
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    // Name shown to the user.
    private final String displayName;

    // Creates a backend with a display name.
    GraphicsBackend(String displayName) {
        this.displayName = displayName;
    }

    // Returns the name shown to the user.
    public String getDisplayName() {
        return this.displayName;
    }
}
