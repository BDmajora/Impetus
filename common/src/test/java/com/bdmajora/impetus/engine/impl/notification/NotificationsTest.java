package com.bdmajora.impetus.engine.impl.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationsTest {
    @Test
    void toastsDedupeExpireAndCap() {
        ImpetusNotification toast = new ImpetusNotification(ImpetusNotification.Level.INFO, "t", List.of("a"), 1000, 500);
        assertFalse(toast.isExpired(1500));
        assertTrue(toast.isExpired(1501));
        assertEquals("t", toast.title());
        ImpetusNotifications.push(ImpetusNotification.Level.INFO, "old", 0, "gone");
        ImpetusNotifications.info("Info", "line");
        ImpetusNotifications.info("Info", "line");
        ImpetusNotifications.warn("Warn", "line");
        ImpetusNotifications.error("Error", "line");
        ImpetusNotifications.info("Fourth");
        ImpetusNotifications.info("Fifth");
        List<ImpetusNotification> visible = ImpetusNotifications.getVisible();
        assertEquals(4, visible.size());
        assertEquals("Warn", visible.get(0).title());
        assertEquals(ImpetusNotification.Level.ERROR, visible.get(1).level());
        assertEquals("Fifth", visible.get(3).title());
        assertTrue(visible.stream().noneMatch(n -> n.title().equals("old")));
    }
}
