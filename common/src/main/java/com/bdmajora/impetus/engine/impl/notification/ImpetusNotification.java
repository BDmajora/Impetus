package com.bdmajora.impetus.engine.impl.notification;

import java.util.List;
import java.util.Objects;

// Message shown to the user for a limited time.
public record ImpetusNotification(Level level, String title, List<String> lines, long createdAtMillis, long durationMillis) {
    // Rejects null parts and stores a copy of the lines.
    public ImpetusNotification {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(title, "title");
        lines = List.copyOf(lines);
    }

    // Severity of a notification.
    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    // Returns whether the display time has passed.
    public boolean isExpired(long nowMillis) {
        return nowMillis - this.createdAtMillis > this.durationMillis;
    }
}
