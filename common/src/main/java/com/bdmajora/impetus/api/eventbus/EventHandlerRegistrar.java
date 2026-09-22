package com.bdmajora.impetus.api.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Holds listeners for one event type and dispatches to them
public class EventHandlerRegistrar<T extends ImpetusEvent> {
    private final List<Handler<T>> handlerList = new CopyOnWriteArrayList<>();

    public EventHandlerRegistrar() {}

    // Subscribes
    public void addListener(Handler<T> listener) {
        handlerList.add(listener);
    }

    // Returns true if the event was cancelable and got canceled by a listener; every listener still runs, since cancellation is advisory to the poster, not a short-circuit
    public boolean post(T event) {
        for (Handler<T> handler : handlerList) {
            handler.acceptEvent(event);
        }
        return event.isCancelable() && event.isCanceled();
    }

    @FunctionalInterface
    public interface Handler<T extends ImpetusEvent> {
        void acceptEvent(T event);
    }
}
