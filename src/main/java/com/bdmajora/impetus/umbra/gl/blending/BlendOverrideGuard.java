package com.bdmajora.impetus.umbra.gl.blending;

import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Holds a pack's blend.<program>=off in place while vanilla's fixed-function renderers (which call enableBlend themselves) draw into packed gbuffer targets; only the enable is suppressed, replaying full state broke the hand
public final class BlendOverrideGuard {
    // Set between the two hooks below, while the pack's blend state is being applied and the result is not yet known
    private static boolean capturing;
    // True once a pack override has been captured and is being enforced
    private static boolean locked;
    // Only an override that turned blend OFF is worth enforcing; one that left it on agrees with vanilla anyway
    private static boolean lockedBlendOff;

    private BlendOverrideGuard() {
    }

    // Called immediately before a program's blend state is applied; releases first so a previous program's lock cannot leak
    public static void beforeProgramBlendApply(boolean active) {
        release();
        capturing = active;
    }

    // Called immediately after; reads real GL rather than trusting the request, since the directive goes through vanilla's state manager and may not have taken
    public static void afterProgramBlendApply() {
        if (!capturing) {
            return;
        }
        capturing = false;
        locked = true;
        lockedBlendOff = LWJGL.glGetInteger(GL11.GL_BLEND) == 0;
    }

    // Ends the override so vanilla's enableBlend works again
    public static void release() {
        locked = false;
        capturing = false;
        lockedBlendOff = false;
    }

    // The hook GlStateManager.enableBlend consults; true means "swallow this call"
    public static boolean recordEnableBlend() {
        return shouldSuppressBlendEnable();
    }

    // True only while a blend-off program is bound and drawing into the gbuffer
    private static boolean shouldSuppressBlendEnable() {
        return locked && lockedBlendOff;
    }
}
