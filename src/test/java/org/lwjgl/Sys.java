package org.lwjgl;

// Test stand-in for Cleanroom's lwjglx Sys.
public final class Sys {
    private Sys() {}

    public static void initialize() {}

    public static String getVersion() {
        return "2.9.4-test";
    }

    // Returns true for the 64 bit test JVM.
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
