package com.bdmajora.impetus.engine.impl.gl.debug;

import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.opengl.GL43C;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// KHR_debug object labels and debug groups, so a RenderDoc or Nsight capture names Impetus's passes and programs; on only with -Dimpetus.enableGLDebug=true
public final class GLDebug {
    private static boolean enabled;
    private static int stackSize;

    private GLDebug() {}

    // Re-reads the switch against the current context, which must exist
    public static void reloadDebugState() {
        enabled = Boolean.getBoolean("impetus.enableGLDebug")
                && (LWJGL.isOpenGLVersionSupported(4, 3) || LWJGL.isExtensionSupported(GLExtension.KHR_debug));
        stackSize = 0;
    }

    public static void nameObject(int identifier, int object, String name) {
        if (enabled) {
            LWJGL.glObjectLabel(identifier, object, name);
        }
    }

    public static void pushGroup(int id, String name) {
        if (enabled) {
            LWJGL.glPushDebugGroup(GL43C.GL_DEBUG_SOURCE_APPLICATION, id, name);
            stackSize++;
        }
    }

    // Pops only groups this class pushed, so a switch flipped mid-frame cannot underflow the driver's stack
    public static void popGroup() {
        if (enabled && stackSize > 0) {
            LWJGL.glPopDebugGroup();
            stackSize--;
        }
    }
}
