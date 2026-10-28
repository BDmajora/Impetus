package com.bdmajora.impetus.engine.impl.gl.debug;

import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.opengl.GL43C;

import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Names GL objects and debug groups for KHR_debug captures when the impetus.enableGLDebug system property is true
public final class GLDebug {
    // Debug switch read from the system property and the context
    private static boolean enabled;
    // Largest label size in UTF 8 bytes the driver accepts or minus one when labels are off
    private static int labelBytes = -1;
    // Largest group message size in UTF 8 bytes the driver accepts
    private static int messageBytes;
    // Largest number of groups this class keeps on the driver stack
    private static int groupLimit;
    // Count of open groups including those past the group limit
    private static long depth;

    // Prevents instantiation
    private GLDebug() {}

    // Reads the switch and the driver limits from the current context and clears the group count
    public static void reloadDebugState() {
        boolean on = Boolean.getBoolean("impetus.enableGLDebug")
                && (LWJGL.isOpenGLVersionSupported(4, 3) || LWJGL.isExtensionSupported(GLExtension.KHR_debug));
        int label = -1;
        int message = -1;
        int groups = 0;
        if (on) {
            label = limit(GL43C.GL_MAX_LABEL_LENGTH);
            message = limit(GL43C.GL_MAX_DEBUG_MESSAGE_LENGTH);
            // Removes the default group and the overflow level from the driver depth
            groups = message < 0 ? 0 : Math.max(LWJGL.glGetInteger(GL43C.GL_MAX_DEBUG_GROUP_STACK_DEPTH), 2) - 2;
        }
        // Assigns the state only after every query succeeds
        enabled = on;
        labelBytes = label;
        messageBytes = message;
        groupLimit = groups;
        depth = 0;
    }

    // Returns one less than the driver limit or minus one when the limit is not positive
    private static int limit(int pname) {
        return Math.max(LWJGL.glGetInteger(pname), 0) - 1;
    }

    // Labels a GL object with the name cut to the driver limit and rejects a null name
    public static void nameObject(int identifier, int object, String name) {
        Objects.requireNonNull(name, "name");
        if (labelBytes >= 0) {
            LWJGL.glObjectLabel(identifier, object, fit(name, labelBytes));
        }
    }

    // Pushes an application debug group with the name cut to the driver limit and rejects a null name
    public static void pushGroup(int id, String name) {
        Objects.requireNonNull(name, "name");
        if (enabled) {
            // Counts a group past the limit without pushing it
            if (depth < groupLimit) {
                LWJGL.glPushDebugGroup(GL43C.GL_DEBUG_SOURCE_APPLICATION, id, fit(name, messageBytes));
            }
            depth++;
        }
    }

    // Pops the newest group this class pushed and skips groups counted past the limit
    public static void popGroup() {
        // Skips the pop when no group is open
        if (depth > 0) {
            if (depth <= groupLimit) {
                LWJGL.glPopDebugGroup();
            }
            depth--;
        }
    }

    // Returns the longest prefix of the text whose UTF 8 form fits the byte limit and keeps surrogate pairs whole
    private static String fit(String text, int limit) {
        int length = text.length();
        // Returns the text when three bytes per char cannot exceed the limit
        if (length <= limit / 3) {
            return text;
        }
        int remaining = limit;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            int size;
            if (c < 0x80) {
                size = 1;
            } else if (c < 0x800) {
                size = 2;
            } else if (!Character.isSurrogate(c)) {
                size = 3;
            } else if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                size = 4;
            } else {
                // Counts a lone surrogate as the single replacement byte the encoder writes
                size = 1;
            }
            remaining -= size;
            if (remaining < 0) {
                return text.substring(0, i);
            }
            // Skips the low half of a surrogate pair
            if (size == 4) {
                i++;
            }
        }
        return text;
    }
}
