package com.bdmajora.impetus.api.eventbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
    private static final class Cancelable extends ImpetusEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    private static final class Plain extends ImpetusEvent {}

    @Test
    void everyListenerRunsAndCancellationIsReported() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            seen.add("first");
            e.setCanceled(true);
        });
        bus.addListener(e -> seen.add("second"));
        Cancelable event = new Cancelable();
        assertTrue(bus.post(event));
        assertEquals(List.of("first", "second"), seen);
        assertTrue(event.isCanceled());
        EventHandlerRegistrar<Cancelable> quiet = new EventHandlerRegistrar<>();
        quiet.addListener(e -> seen.add("quiet"));
        assertFalse(quiet.post(new Cancelable()));
    }

    @Test
    void plainEventsCannotBeCancelled() {
        Plain event = new Plain();
        assertFalse(event.isCancelable());
        assertFalse(event.isCanceled());
        assertThrows(UnsupportedOperationException.class, () -> event.setCanceled(true));
        assertFalse(new EventHandlerRegistrar<Plain>().post(event));
    }
}
