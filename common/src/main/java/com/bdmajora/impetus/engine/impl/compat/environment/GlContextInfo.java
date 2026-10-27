package com.bdmajora.impetus.engine.impl.compat.environment;

import com.bdmajora.impetus.lwjgl.LWJGLServiceProvider;

// Immutable snapshot of the vendor, renderer and version strings of a GL context
public record GlContextInfo(String vendor, String renderer, String version) {
    // GL enum value of the vendor string query
    private static final int GL_VENDOR = 0x1F00;
    // GL enum value of the renderer string query
    private static final int GL_RENDERER = 0x1F01;
    // GL enum value of the version string query
    private static final int GL_VERSION = 0x1F02;

    // Returns a snapshot of the strings of the current GL context with empty strings in place of null
    public static GlContextInfo capture() {
        var gl = LWJGLServiceProvider.LWJGL;

        return new GlContextInfo(
                safe(gl.glGetString(GL_VENDOR)),
                safe(gl.glGetString(GL_RENDERER)),
                safe(gl.glGetString(GL_VERSION)));
    }

    // Returns the value or an empty string when the value is null
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
