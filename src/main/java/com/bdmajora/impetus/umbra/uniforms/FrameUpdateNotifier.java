package com.bdmajora.impetus.umbra.uniforms;

import java.util.ArrayList;
import java.util.List;

// Notifier of frame start listeners.
public final class FrameUpdateNotifier {
    // Listeners to run.
    private final List<Runnable> listeners = new ArrayList<>();

    // Adds a listener.
    public void addListener(Runnable listener) {
        this.listeners.add(listener);
    }

    // Handles the new frame.
    public void onNewFrame() {
        for (Runnable listener : this.listeners) {
            listener.run();
        }
    }
}
