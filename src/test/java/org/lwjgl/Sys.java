package org.lwjgl;

// Test stand-in for Cleanroom's lwjglx Sys, which its Keyboard reads timestamps from
public final class Sys {
    private Sys() {}

    public static void initialize() {}

    public static String getVersion() {
        return "2.9.4-test";
    }

    // The test JVM is always 64-bit, which is what Pointer.POINTER_SIZE and PointerBuffer derive from
    public static boolean is64Bit() {
        return true;
    }

    public static long getTimerResolution() {
        return 1000L;
    }

    public static long getTime() {
        return System.currentTimeMillis();
    }

    public static long getNanoTime() {
        return System.nanoTime();
    }

    public static boolean openURL(String url) {
        return false;
    }

    public static void alert(String title, String message) {}

    public static String getClipboard() {
        return "";
    }
}
