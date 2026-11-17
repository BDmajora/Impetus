package com.bdmajora.impetus.impl.world;

import java.util.Objects;

// Records the integrated server's world saves for the autosave indicator.
public final class WorldSaveTracker {
    // Whether a save is running now.
    private static volatile boolean saving;
    // Number of saves begun so far.
    private static volatile int started;

    // Prevents instantiation
    private WorldSaveTracker() {
    }

    // Runs a save with the flag raised and lowers it even when the save throws
    public static void duringSave(Runnable save) {
        Objects.requireNonNull(save, "save");
        started++;
        saving = true;
        try {
            save.run();
        } finally {
            saving = false;
        }
    }

    // Returns whether a save is running now
    public static boolean saving() {
        return saving;
    }

    // Returns the number of saves begun so far.
    public static int started() {
        return started;
    }
}
