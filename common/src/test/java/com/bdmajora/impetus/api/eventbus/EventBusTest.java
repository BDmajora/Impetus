package com.bdmajora.impetus.api.eventbus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EventBusTest {
    private static final class Cancelable extends ImpetusEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    private static final class Plain extends ImpetusEvent {}

    // Carries which listeners ran, so each post can be checked on its own while another thread adds
    private static final class Recording extends ImpetusEvent {
        final List<Integer> seen = new ArrayList<>();
    }

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

    @Test
    void aLaterListenerCanUndoACancellation() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        bus.addListener(e -> e.setCanceled(true));
        bus.addListener(e -> e.setCanceled(false));
        Cancelable event = new Cancelable();
        assertFalse(bus.post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void aCancellationMadeBeforeThePostIsClearedByIt() {
        Cancelable event = new Cancelable();
        event.setCanceled(true);
        assertFalse(new EventHandlerRegistrar<Cancelable>().post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void aNullListenerIsRejectedWhenAddedAndTheBusKeepsWorking() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> seen.add("kept"));
        NullPointerException rejected = assertThrowsExactly(NullPointerException.class, () -> bus.addListener(null));
        assertEquals("Listener must not be null", rejected.getMessage());
        assertEquals(1, BusListeners.of(bus).size());
        assertFalse(bus.post(new Plain()));
        assertEquals(List.of("kept"), seen);
    }

    @Test
    void aNullEventIsRejectedBeforeAnyListenerRuns() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        List<Plain> seen = new ArrayList<>();
        bus.addListener(seen::add);
        NullPointerException rejected = assertThrowsExactly(NullPointerException.class, () -> bus.post(null));
        assertEquals("Event must not be null", rejected.getMessage());
        assertEquals(List.of(), seen);
        // An empty bus rejects it too, rather than failing later on isCancelable
        assertThrowsExactly(NullPointerException.class, () -> new EventHandlerRegistrar<Plain>().post(null));
    }

    @Test
    void theSameListenerAddedTwiceRunsTwice() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        int[] calls = new int[1];
        EventHandlerRegistrar.Handler<Plain> listener = e -> calls[0]++;
        bus.addListener(listener);
        bus.addListener(listener);
        bus.post(new Plain());
        assertEquals(2, calls[0]);
        assertEquals(List.of(listener, listener), BusListeners.of(bus));
    }

    @Test
    void registrarsShareNoListeners() {
        EventHandlerRegistrar<Plain> used = new EventHandlerRegistrar<>();
        EventHandlerRegistrar<Plain> untouched = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        used.addListener(e -> seen.add("used"));
        assertFalse(untouched.post(new Plain()));
        assertEquals(List.of(), seen);
        assertEquals(List.of(), BusListeners.of(untouched));
        assertEquals(1, BusListeners.of(used).size());
    }

    @Test
    void aThrowingListenerDoesNotStopTheOnesAfterIt() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        IllegalStateException failure = new IllegalStateException("broken");
        List<String> seen = new ArrayList<>();
        boolean[] failing = {true};
        bus.addListener(e -> {
            seen.add("first");
            e.setCanceled(true);
            if (failing[0]) {
                throw failure;
            }
        });
        bus.addListener(e -> seen.add("second"));
        Cancelable event = new Cancelable();
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> bus.post(event)));
        assertEquals(List.of("first", "second"), seen);
        assertEquals(0, failure.getSuppressed().length);
        // The cancellation still landed on the event, though post threw instead of reporting it
        assertTrue(event.isCanceled());
        // One failed post leaves the bus as it was
        failing[0] = false;
        assertTrue(bus.post(new Cancelable()));
        assertEquals(List.of("first", "second", "first", "second"), seen);
    }

    @Test
    void laterFailuresAreSuppressedOnTheFirstInOrder() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        RuntimeException first = new IllegalStateException("first");
        RuntimeException second = new IllegalArgumentException("second");
        RuntimeException third = new UnsupportedOperationException("third");
        bus.addListener(e -> {
            throw first;
        });
        bus.addListener(e -> {
            throw second;
        });
        bus.addListener(e -> {
            throw third;
        });
        assertSame(first, assertThrowsExactly(IllegalStateException.class, () -> bus.post(new Plain())));
        assertArrayEquals(new Throwable[] {second, third}, first.getSuppressed());
        assertEquals(0, second.getSuppressed().length);
    }

    @Test
    void aFailureThrownAgainIsNeitherSuppressedOnItselfNorListedTwice() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        RuntimeException cached = new IllegalStateException("cached");
        RuntimeException other = new IllegalArgumentException("other");
        bus.addListener(e -> {
            throw cached;
        });
        bus.addListener(e -> {
            throw other;
        });
        bus.addListener(e -> {
            throw cached;
        });
        bus.addListener(e -> {
            throw other;
        });
        // Listeners that rethrow the same instances every post must not grow its suppressed list post after post
        for (int i = 0; i < 3; i++) {
            assertSame(cached, assertThrowsExactly(IllegalStateException.class, () -> bus.post(new Plain())));
            assertArrayEquals(new Throwable[] {other}, cached.getSuppressed());
        }
    }

    @Test
    void anErrorTakesOverFromEarlierExceptionsAndTheRestStaySuppressed() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        RuntimeException first = new IllegalStateException("first");
        NoClassDefFoundError missing = new NoClassDefFoundError("missing");
        AssertionError second = new AssertionError("second");
        RuntimeException last = new IllegalArgumentException("last");
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            throw first;
        });
        bus.addListener(e -> {
            throw missing;
        });
        bus.addListener(e -> {
            throw second;
        });
        bus.addListener(e -> {
            throw last;
        });
        bus.addListener(e -> seen.add("after"));
        assertSame(missing, assertThrowsExactly(NoClassDefFoundError.class, () -> bus.post(new Plain())));
        // The first Error stays on top of a later one
        assertArrayEquals(new Throwable[] {first, second, last}, missing.getSuppressed());
        assertEquals(0, first.getSuppressed().length);
        assertEquals(List.of("after"), seen);
    }

    @Test
    void aVirtualMachineErrorStopsThePostAtOnceWithEarlierFailuresAttached() {
        for (VirtualMachineError fatal : List.of(new StackOverflowError(), new OutOfMemoryError(), new InternalError())) {
            EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
            RuntimeException earlier = new IllegalStateException("earlier");
            List<String> seen = new ArrayList<>();
            bus.addListener(e -> {
                throw earlier;
            });
            bus.addListener(e -> {
                throw fatal;
            });
            bus.addListener(e -> seen.add("after"));
            assertSame(fatal, assertThrows(VirtualMachineError.class, () -> bus.post(new Plain())));
            assertArrayEquals(new Throwable[] {earlier}, fatal.getSuppressed());
            assertEquals(List.of(), seen);
        }
        // With nothing failed before it, the error comes out bare
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        StackOverflowError alone = new StackOverflowError();
        bus.addListener(e -> {
            throw alone;
        });
        assertSame(alone, assertThrowsExactly(StackOverflowError.class, () -> bus.post(new Plain())));
        assertEquals(0, alone.getSuppressed().length);
    }

    @Test
    void runawayRecursionThroughTwoListenersFailsFast() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        bus.addListener(bus::post);
        bus.addListener(bus::post);
        // Carrying on past the overflow would send every frame into both listeners, which never finishes
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> assertThrows(StackOverflowError.class, () -> bus.post(new Plain())));
    }

    @Test
    void aCheckedExceptionThrownSneakilyComesOutUnwrapped() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        IOException io = new IOException("disk");
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            throw sneaky(io);
        });
        bus.addListener(e -> seen.add("after"));
        assertSame(io, assertThrowsExactly(IOException.class, () -> bus.post(new Plain())));
        assertEquals(List.of("after"), seen);
    }

    @Test
    void aListenerAddedDuringAPostFirstRunsOnTheNext() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        boolean[] added = new boolean[1];
        bus.addListener(e -> {
            seen.add("adder");
            if (!added[0]) {
                added[0] = true;
                bus.addListener(late -> seen.add("late"));
            }
        });
        bus.post(new Plain());
        assertEquals(List.of("adder"), seen);
        bus.post(new Plain());
        assertEquals(List.of("adder", "adder", "late"), seen);
    }

    @Test
    void postingFromInsideAListenerReachesEveryListenerInOrder() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        Plain inner = new Plain();
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            seen.add(e == inner ? "first inner" : "first outer");
            if (e != inner) {
                bus.post(inner);
            }
        });
        bus.addListener(e -> seen.add(e == inner ? "second inner" : "second outer"));
        assertFalse(bus.post(new Plain()));
        assertEquals(List.of("first outer", "first inner", "second inner", "second outer"), seen);
    }

    @Test
    void aFailureInANestedPostReachesTheOuterPostAfterItsOtherListenersRun() {
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        Plain inner = new Plain();
        IllegalStateException failure = new IllegalStateException("inner");
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            if (e != inner) {
                bus.post(inner);
            }
        });
        bus.addListener(e -> {
            seen.add(e == inner ? "inner" : "outer");
            if (e == inner) {
                throw failure;
            }
        });
        // The inner post rethrows into the first listener, and the outer post holds it until the second listener has run
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> bus.post(new Plain())));
        assertEquals(List.of("inner", "outer"), seen);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void concurrentAddsLoseNoListenerAndKeepEachThreadsOrder() throws Exception {
        EventHandlerRegistrar<Recording> bus = new EventHandlerRegistrar<>();
        int threads = 8;
        int perThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                tasks.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        int code = thread * perThread + i;
                        bus.addListener(e -> e.seen.add(code));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        Recording event = new Recording();
        bus.post(event);
        assertEquals(threads * perThread, event.seen.size());
        // Every listener ran once, and each thread's in the order that thread added them
        int[] next = new int[threads];
        for (int code : event.seen) {
            assertEquals(next[code / perThread]++, code % perThread);
        }
        for (int count : next) {
            assertEquals(perThread, count);
        }
    }

    @Test
    void eachPostSeesAGrowingPrefixOfTheListenersWhileAnotherThreadAdds() throws Exception {
        EventHandlerRegistrar<Recording> bus = new EventHandlerRegistrar<>();
        int count = 5_000;
        ExecutorService adder = Executors.newSingleThreadExecutor();
        try {
            Future<?> adding = adder.submit(() -> {
                for (int i = 0; i < count; i++) {
                    int index = i;
                    bus.addListener(e -> e.seen.add(index));
                }
            });
            int last = 0;
            while (!adding.isDone()) {
                last = postAndCheckPrefix(bus, last);
            }
            adding.get(60, TimeUnit.SECONDS);
            assertEquals(count, postAndCheckPrefix(bus, last));
        } finally {
            adder.shutdownNow();
        }
    }

    @Test
    void aWarmPostAllocatesNothing() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        EventHandlerRegistrar<Plain> bus = new EventHandlerRegistrar<>();
        int[] calls = new int[1];
        bus.addListener(e -> calls[0]++);
        bus.addListener(e -> calls[0]++);
        Plain event = new Plain();
        for (int i = 0; i < 20_000; i++) {
            bus.post(event);
        }
        threads.getCurrentThreadAllocatedBytes();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100_000; i++) {
            bus.post(event);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertEquals(240_000, calls[0]);
        // An iterator per post would be at least 1.6 MB here; the slack covers the measuring calls themselves
        assertTrue(allocated < 16_384, allocated + " bytes allocated");
    }

    // Posts once, checks the listeners that ran were the first ones added in order and no fewer than before, and returns how many ran
    private static int postAndCheckPrefix(EventHandlerRegistrar<Recording> bus, int atLeast) {
        Recording event = new Recording();
        bus.post(event);
        for (int i = 0; i < event.seen.size(); i++) {
            assertEquals(i, (int) event.seen.get(i));
        }
        assertTrue(event.seen.size() >= atLeast, event.seen.size() + " < " + atLeast);
        return event.seen.size();
    }

    @SuppressWarnings("unchecked")
    private static <X extends Throwable> RuntimeException sneaky(Throwable failure) throws X {
        throw (X) failure;
    }
}
