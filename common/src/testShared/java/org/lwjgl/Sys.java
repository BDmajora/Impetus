package org.lwjgl;

// Test stand-in for LWJGL2's Sys, whose real static initialiser loads the lwjgl native library
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
}
