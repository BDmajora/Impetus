package com.bdmajora.impetus.api.eventbus;

import java.util.Arrays;
import java.util.Objects;

// Holds listeners for one event type and dispatches events to them
public class EventHandlerRegistrar<T extends ImpetusEvent> {
    // Shared empty array every new registrar starts from
    private static final Handler<?>[] EMPTY = new Handler<?>[0];

    // Registered listeners in the order they were added
    @SuppressWarnings("unchecked")
    private volatile Handler<T>[] handlers = (Handler<T>[]) EMPTY;

    // Lock held by addListener() while the array is replaced
    private final Object writeLock = new Object();

    // Creates a registrar with no listeners
    public EventHandlerRegistrar() {}

    // Adds a listener to the end of the array by publishing a longer copy
    public void addListener(Handler<T> listener) {
        Objects.requireNonNull(listener, "Listener must not be null");
        synchronized (writeLock) {
            Handler<T>[] current = handlers;
            Handler<T>[] next = Arrays.copyOf(current, current.length + 1);
            next[current.length] = listener;
            handlers = next;
        }
    }

    // Runs every listener with the event and returns true if the event is cancelable and was canceled
    public boolean post(T event) {
        Objects.requireNonNull(event, "Event must not be null");
        // Reads the listener array once for the whole post
        Handler<T>[] snapshot = handlers;
        Throwable failure = null;
        for (Handler<T> handler : snapshot) {
            try {
                handler.acceptEvent(event);
            } catch (VirtualMachineError fatal) {
                // Rethrows a virtual machine error immediately with any earlier failure attached
                if (failure != null) {
                    fatal.addSuppressed(failure);
                }
                throw fatal;
            } catch (Throwable thrown) {
                // Records the failure and continues with the next listener
                failure = merge(failure, thrown);
            }
        }
        if (failure != null) {
            throw rethrow(failure);
        }
        return event.isCancelable() && event.isCanceled();
    }

    // Combines a listener's failure with the failures already collected
    private static Throwable merge(Throwable failure, Throwable thrown) {
        if (failure == null) {
            return thrown;
        }
        // Ignores a failure instance that is already collected
        if (thrown == failure) {
            return failure;
        }
        for (Throwable held : failure.getSuppressed()) {
            if (held == thrown) {
                return failure;
            }
        }
        // Makes an Error the primary failure and attaches the earlier exception to it
        if (thrown instanceof Error && !(failure instanceof Error)) {
            thrown.addSuppressed(failure);
            return thrown;
        }
        failure.addSuppressed(thrown);
        return failure;
    }

    // Throws the failure unchanged including checked exceptions
    @SuppressWarnings("unchecked")
    private static <X extends Throwable> X rethrow(Throwable failure) throws X {
        throw (X) failure;
    }

    // Listener that receives events of type T
    @FunctionalInterface
    public interface Handler<T extends ImpetusEvent> {
        void acceptEvent(T event);
    }
}