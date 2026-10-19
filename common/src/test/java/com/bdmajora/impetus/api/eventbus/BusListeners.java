package com.bdmajora.impetus.api.eventbus;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

// Reads and edits a bus's private listener array, since the bus has no way to unsubscribe
public final class BusListeners {
    private static final Field HANDLERS = field("handlers");
    private static final Field WRITE_LOCK = field("writeLock");

    private BusListeners() {}

    // The bus's listeners in order, as a copy
    @SuppressWarnings("unchecked")
    public static <E extends ImpetusEvent> List<EventHandlerRegistrar.Handler<E>> of(EventHandlerRegistrar<E> bus) {
        try {
            return Arrays.asList(((EventHandlerRegistrar.Handler<E>[]) HANDLERS.get(bus)).clone());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Takes the first occurrence of the listener off the bus under the bus's own write lock, and returns whether it was there
    public static <E extends ImpetusEvent> boolean remove(EventHandlerRegistrar<E> bus, EventHandlerRegistrar.Handler<E> handler) {
        try {
            synchronized (WRITE_LOCK.get(bus)) {
                Object[] current = (Object[]) HANDLERS.get(bus);
                for (int i = 0; i < current.length; i++) {
                    if (current[i] == handler) {
                        EventHandlerRegistrar.Handler<?>[] next = new EventHandlerRegistrar.Handler<?>[current.length - 1];
                        System.arraycopy(current, 0, next, 0, i);
                        System.arraycopy(current, i + 1, next, i, next.length - i);
                        HANDLERS.set(bus, next);
                        return true;
                    }
                }
                return false;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Field field(String name) {
        try {
            Field field = EventHandlerRegistrar.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
