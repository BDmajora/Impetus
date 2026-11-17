package com.bdmajora.impetus.umbra.gl.blending;

import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Guard that stops blend changes while a program blend override is active.
public final class BlendOverrideGuard {
    // Flag set while blend calls are captured.
    private static boolean capturing;
    // Flag set while blend calls are blocked.
    private static boolean locked;
    // Flag set when the locked state has blending off.
    private static boolean lockedBlendOff;

    // Prevents instantiation
    private BlendOverrideGuard() {
    }

    // Locks blend calls before a program override applies.
    public static void beforeProgramBlendApply(boolean active) {
        release();
        capturing = active;
    }

    // Unlocks blend calls after a program override applied.
    public static void afterProgramBlendApply() {
        if (!capturing) {
            return;
        }
        capturing = false;
        locked = true;
        lockedBlendOff = LWJGL.glGetInteger(GL11.GL_BLEND) == 0;
    }

    // Lets blend calls through again.
    public static void release() {
        locked = false;
        capturing = false;
        lockedBlendOff = false;
    }

    // Returns whether an enable blend call may run.
    public static boolean recordEnableBlend() {
        return shouldSuppressBlendEnable();
    }

    // Returns whether glEnable(GL_BLEND) must be dropped for a locked blend off state.
    private static boolean shouldSuppressBlendEnable() {
        return locked && lockedBlendOff;
    }
}
