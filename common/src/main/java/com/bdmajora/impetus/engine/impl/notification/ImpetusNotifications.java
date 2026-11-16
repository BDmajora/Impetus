package com.bdmajora.impetus.engine.impl.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Queue of the notifications that are visible now.
public final class ImpetusNotifications {
    // Number of notifications kept.
    private static final int MAX_VISIBLE = 4;
    // Display time in milliseconds.
    private static final long DEFAULT_DURATION_MS = 12_000L;
    // Notifications in arrival order.
    private static final List<ImpetusNotification> NOTIFICATIONS = new ArrayList<>();

    // Prevents instantiation
    private ImpetusNotifications() {
    }

    // Queues an info notification with the default duration.
    public static void info(String title, String... lines) {
        push(ImpetusNotification.Level.INFO, title, DEFAULT_DURATION_MS, lines);
    }

    // Queues a warning notification with the default duration.
    public static void warn(String title, String... lines) {
        push(ImpetusNotification.Level.WARNING, title, DEFAULT_DURATION_MS, lines);
    }

    // Queues an error notification with the default duration.
    public static void error(String title, String... lines) {
        push(ImpetusNotification.Level.ERROR, title, DEFAULT_DURATION_MS, lines);
    }

    // Queues a notification and replaces an identical one and drops the oldest past the limit and rejects null parts.
    public static synchronized void push(ImpetusNotification.Level level, String title, long durationMillis, String... lines) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(title, "title");
        List<String> copy = List.of(lines);

        long now = System.currentTimeMillis();
        pruneExpired(now);

        NOTIFICATIONS.removeIf(notification -> notification.title().equals(title) && notification.lines().equals(copy));
        NOTIFICATIONS.add(new ImpetusNotification(level, title, copy, now, durationMillis));

        while (NOTIFICATIONS.size() > MAX_VISIBLE) {
            NOTIFICATIONS.remove(0);
        }
    }

    // Returns a copy of the notifications that have not expired.
    public static synchronized List<ImpetusNotification> getVisible() {
        pruneExpired(System.currentTimeMillis());
        return List.copyOf(NOTIFICATIONS);
    }

    // Removes the notifications that have expired.
    private static void pruneExpired(long now) {
        NOTIFICATIONS.removeIf(notification -> notification.isExpired(now));
    }
}
