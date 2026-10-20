package com.bdmajora.impetus.api.eventbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ImpetusEventTest {
    private static class Cancelable extends ImpetusEvent {
        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    private static final class CancelableChild extends Cancelable {}

    // Turns cancelability back off below a cancelable parent
    private static final class UncancelableChild extends Cancelable {
        @Override
        public boolean isCancelable() {
            return false;
        }
    }

    private static final class Plain extends ImpetusEvent {}

    // Cancelability a test can switch, counting how often it is asked
    private static final class Switchable extends ImpetusEvent {
        boolean cancelable = true;
        int asked;

        @Override
        public boolean isCancelable() {
            asked++;
            return cancelable;
        }
    }

    // Tells the listener which round of a reused event it is seeing
    private static final class Numbered extends Cancelable {
        int round;
    }

    @Test
    void newEventsStartUncanceledWhetherOrNotTheyCanBeCanceled() {
        assertFalse(new Plain().isCancelable());
        assertFalse(new Plain().isCanceled());
        assertFalse(new ImpetusEvent() {}.isCancelable());
        assertTrue(new Cancelable().isCancelable());
        assertFalse(new Cancelable().isCanceled());
        assertEquals(0, new Cancelable().postDepth);
    }

    @Test
    void cancelabilityIsInheritedAndASubclassCanTurnItBackOff() {
        CancelableChild child = new CancelableChild();
        assertTrue(child.isCancelable());
        child.setCanceled(true);
        assertTrue(child.isCanceled());
        UncancelableChild off = new UncancelableChild();
        assertFalse(off.isCancelable());
        assertThrowsExactly(UnsupportedOperationException.class, () -> off.setCanceled(true));
        assertFalse(off.isCanceled());
    }

    @Test
    void cancelingANonCancelableEventThrowsNamingItsClassAndChangesNothing() {
        ImpetusEvent anonymous = new ImpetusEvent() {};
        for (ImpetusEvent event : List.of(new Plain(), new UncancelableChild(), anonymous)) {
            UnsupportedOperationException thrown = assertThrowsExactly(UnsupportedOperationException.class, () -> event.setCanceled(true));
            assertEquals("Event " + event.getClass().getName() + " is not cancelable", thrown.getMessage());
            assertFalse(event.isCanceled());
        }
        // The binary name, so a nested class reads with its $ and an anonymous one is not left blank like its simple name
        assertEquals("Event com.bdmajora.impetus.api.eventbus.ImpetusEventTest$Plain is not cancelable",
                assertThrows(UnsupportedOperationException.class, () -> new Plain().setCanceled(true)).getMessage());
        assertTrue(assertThrows(UnsupportedOperationException.class, () -> anonymous.setCanceled(true)).getMessage()
                .startsWith("Event " + ImpetusEventTest.class.getName() + "$"));
    }

    @Test
    void clearingANonCancelableEventIsAllowedAndKeepsItUncanceled() {
        for (ImpetusEvent event : List.of(new Plain(), new UncancelableChild(), new ImpetusEvent() {})) {
            assertDoesNotThrow(() -> event.setCanceled(false));
            assertDoesNotThrow(() -> event.setCanceled(false));
            assertFalse(event.isCanceled());
        }
    }

    @Test
    void aCancelableEventTogglesEitherWayAndRepeatsAreHarmless() {
        Cancelable event = new Cancelable();
        event.setCanceled(false);
        assertFalse(event.isCanceled());
        event.setCanceled(true);
        event.setCanceled(true);
        assertTrue(event.isCanceled());
        event.setCanceled(false);
        assertFalse(event.isCanceled());
        event.setCanceled(false);
        assertFalse(event.isCanceled());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
    }

    @Test
    void cancelabilityIsAskedOnEveryCancelAndNeverToClear() {
        Switchable event = new Switchable();
        event.setCanceled(true);
        assertEquals(1, event.asked);
        assertTrue(event.isCanceled());
        event.cancelable = false;
        // Canceling again now fails and leaves the earlier state alone
        assertThrowsExactly(UnsupportedOperationException.class, () -> event.setCanceled(true));
        assertEquals(2, event.asked);
        assertTrue(event.isCanceled());
        // Clearing skips the check, which is what lets an event that stopped being cancelable be cleared at all
        event.setCanceled(false);
        assertEquals(2, event.asked);
        assertFalse(event.isCanceled());
    }

    @Test
    void aReusedEventStartsEveryPostUncanceled() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        List<Boolean> canceledOnEntry = new ArrayList<>();
        boolean[] cancel = new boolean[1];
        bus.addListener(e -> {
            canceledOnEntry.add(e.isCanceled());
            if (cancel[0]) {
                e.setCanceled(true);
            }
        });
        Cancelable event = new Cancelable();
        boolean[] rounds = {true, false, false, true, true, false};
        for (boolean round : rounds) {
            cancel[0] = round;
            assertEquals(round, bus.post(event));
            assertEquals(round, event.isCanceled());
            assertEquals(0, event.postDepth);
        }
        assertEquals(Collections.nCopies(rounds.length, false), canceledOnEntry);
    }

    @Test
    void aCancellationOnOneBusDoesNotCarryIntoAPostOnAnother() {
        EventHandlerRegistrar<Cancelable> canceling = new EventHandlerRegistrar<>();
        canceling.addListener(e -> e.setCanceled(true));
        EventHandlerRegistrar<Cancelable> quiet = new EventHandlerRegistrar<>();
        List<Boolean> seen = new ArrayList<>();
        quiet.addListener(e -> seen.add(e.isCanceled()));
        Cancelable event = new Cancelable();
        assertTrue(canceling.post(event));
        assertFalse(quiet.post(event));
        assertFalse(event.isCanceled());
        assertEquals(List.of(false), seen);
        assertTrue(canceling.post(event));
        // An empty bus clears it as well
        assertFalse(new EventHandlerRegistrar<Cancelable>().post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void nestedPostsOfTheSameEventShareOneCancellationAndTheDepthUnwinds() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        bus.addListener(e -> {
            seen.add(e.postDepth + ":" + e.isCanceled());
            e.setCanceled(true);
            if (e.postDepth < 3) {
                bus.post(e);
            }
        });
        Cancelable event = new Cancelable();
        event.setCanceled(true);
        assertTrue(bus.post(event));
        // Only the outermost post cleared the earlier cancellation; the inner ones kept the outer listener's
        assertEquals(List.of("1:false", "2:true", "3:true"), seen);
        assertEquals(0, event.postDepth);
        seen.clear();
        assertTrue(bus.post(event));
        assertEquals(List.of("1:false", "2:true", "3:true"), seen);
    }

    @Test
    void forwardingTheEventToAnotherBusKeepsTheOuterCancellation() {
        EventHandlerRegistrar<Cancelable> inner = new EventHandlerRegistrar<>();
        List<Boolean> innerSaw = new ArrayList<>();
        inner.addListener(e -> innerSaw.add(e.isCanceled()));
        EventHandlerRegistrar<Cancelable> outer = new EventHandlerRegistrar<>();
        List<Boolean> innerResults = new ArrayList<>();
        outer.addListener(e -> e.setCanceled(true));
        outer.addListener(e -> innerResults.add(inner.post(e)));
        Cancelable event = new Cancelable();
        assertTrue(outer.post(event));
        assertEquals(List.of(true), innerSaw);
        assertEquals(List.of(true), innerResults);
        assertTrue(event.isCanceled());
        assertEquals(0, event.postDepth);
    }

    @Test
    void aNestedPostCanCancelOrClearTheEventForTheOuterPost() {
        EventHandlerRegistrar<Cancelable> canceling = new EventHandlerRegistrar<>();
        canceling.addListener(e -> e.setCanceled(true));
        EventHandlerRegistrar<Cancelable> clearing = new EventHandlerRegistrar<>();
        clearing.addListener(e -> e.setCanceled(false));
        EventHandlerRegistrar<Cancelable> outer = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        outer.addListener(canceling::post);
        outer.addListener(e -> seen.add("after cancel " + e.isCanceled()));
        outer.addListener(clearing::post);
        outer.addListener(e -> seen.add("after clear " + e.isCanceled()));
        assertFalse(outer.post(new Cancelable()));
        assertEquals(List.of("after cancel true", "after clear false"), seen);
        EventHandlerRegistrar<Cancelable> reversed = new EventHandlerRegistrar<>();
        reversed.addListener(clearing::post);
        reversed.addListener(canceling::post);
        assertTrue(reversed.post(new Cancelable()));
    }

    @Test
    void anotherEventPostedInsideAPostIsStillClearedAndLeavesTheOuterOneAlone() {
        Cancelable carried = new Cancelable();
        carried.setCanceled(true);
        EventHandlerRegistrar<Cancelable> inner = new EventHandlerRegistrar<>();
        List<Boolean> innerSaw = new ArrayList<>();
        inner.addListener(e -> innerSaw.add(e.isCanceled()));
        EventHandlerRegistrar<Cancelable> outer = new EventHandlerRegistrar<>();
        boolean[] innerResult = {true};
        outer.addListener(e -> e.setCanceled(true));
        outer.addListener(e -> innerResult[0] = inner.post(carried));
        Cancelable event = new Cancelable();
        // The depth belongs to each event, so the outer post running does not make the carried event's post a nested one
        assertTrue(outer.post(event));
        assertFalse(innerResult[0]);
        assertEquals(List.of(false), innerSaw);
        assertFalse(carried.isCanceled());
        assertTrue(event.isCanceled());
        assertEquals(0, carried.postDepth);
        assertEquals(0, event.postDepth);
    }

    @Test
    void aPostThatThrowsLeavesTheEventReusable() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        IllegalStateException failure = new IllegalStateException("broken");
        boolean[] failing = {true};
        bus.addListener(e -> {
            if (failing[0]) {
                e.setCanceled(true);
                throw failure;
            }
        });
        Cancelable event = new Cancelable();
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> bus.post(event)));
        // The failed post's cancellation stays readable, and its depth was given back
        assertTrue(event.isCanceled());
        assertEquals(0, event.postDepth);
        failing[0] = false;
        assertFalse(bus.post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void aVirtualMachineErrorLeavesTheEventReusable() {
        for (VirtualMachineError fatal : List.of(new OutOfMemoryError("test"), new StackOverflowError("test"), new InternalError("test"))) {
            EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
            boolean[] failing = {true};
            List<String> seen = new ArrayList<>();
            bus.addListener(e -> {
                if (failing[0]) {
                    e.setCanceled(true);
                    throw fatal;
                }
            });
            bus.addListener(e -> seen.add("after"));
            Cancelable event = new Cancelable();
            assertSame(fatal, assertThrows(VirtualMachineError.class, () -> bus.post(event)));
            assertEquals(List.of(), seen);
            assertEquals(0, event.postDepth);
            assertTrue(event.isCanceled());
            failing[0] = false;
            assertFalse(bus.post(event));
            assertEquals(List.of("after"), seen);
        }
    }

    @Test
    void runawayRecursionOfOneEventUnwindsItsDepthToZero() {
        EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
        bus.addListener(e -> {
            e.setCanceled(true);
            bus.post(e);
        });
        Cancelable event = new Cancelable();
        assertThrows(StackOverflowError.class, () -> bus.post(event));
        // Every frame gave its depth back while the overflow unwound, so the next post is an outermost one again
        assertEquals(0, event.postDepth);
        assertTrue(event.isCanceled());
        assertFalse(new EventHandlerRegistrar<Cancelable>().post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void aFailingNestedPostLeavesBothLevelsBalanced() {
        EventHandlerRegistrar<Cancelable> inner = new EventHandlerRegistrar<>();
        IllegalStateException failure = new IllegalStateException("inner");
        inner.addListener(e -> {
            e.setCanceled(true);
            throw failure;
        });
        EventHandlerRegistrar<Cancelable> outer = new EventHandlerRegistrar<>();
        List<Integer> depthAfterInner = new ArrayList<>();
        outer.addListener(inner::post);
        outer.addListener(e -> depthAfterInner.add(e.postDepth));
        Cancelable event = new Cancelable();
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> outer.post(event)));
        assertEquals(List.of(1), depthAfterInner);
        assertEquals(0, event.postDepth);
        assertTrue(event.isCanceled());
        assertFalse(new EventHandlerRegistrar<Cancelable>().post(event));
    }

    @Test
    void aListenerMayClearButNotCancelANonCancelableEvent() {
        EventHandlerRegistrar<Plain> clearing = new EventHandlerRegistrar<>();
        clearing.addListener(e -> e.setCanceled(false));
        assertFalse(clearing.post(new Plain()));
        EventHandlerRegistrar<Plain> canceling = new EventHandlerRegistrar<>();
        List<String> seen = new ArrayList<>();
        canceling.addListener(e -> e.setCanceled(true));
        canceling.addListener(e -> seen.add("after"));
        Plain event = new Plain();
        assertThrowsExactly(UnsupportedOperationException.class, () -> canceling.post(event));
        assertEquals(List.of("after"), seen);
        assertFalse(event.isCanceled());
        assertEquals(0, event.postDepth);
    }

    @Test
    void anEventThatStopsBeingCancelableMidPostIsNotReportedCanceledAndIsStillClearedNextTime() {
        EventHandlerRegistrar<Switchable> bus = new EventHandlerRegistrar<>();
        boolean[] cancel = {true};
        bus.addListener(e -> {
            if (cancel[0]) {
                e.setCanceled(true);
                e.cancelable = false;
            }
        });
        Switchable event = new Switchable();
        assertFalse(bus.post(event));
        // The flag is still set; only post's answer goes through isCancelable
        assertTrue(event.isCanceled());
        cancel[0] = false;
        assertFalse(bus.post(event));
        assertFalse(event.isCanceled());
    }

    @Test
    void postReportsWhatTheEventsOwnOverridesSay() {
        // An override keeps its own state, which post neither clears nor second-guesses
        Cancelable stuck = new Cancelable() {
            @Override
            public boolean isCanceled() {
                return true;
            }
        };
        assertTrue(new EventHandlerRegistrar<Cancelable>().post(stuck));
        assertTrue(new EventHandlerRegistrar<Cancelable>().post(stuck));
        ImpetusEvent stuckButPlain = new ImpetusEvent() {
            @Override
            public boolean isCanceled() {
                return true;
            }
        };
        assertFalse(new EventHandlerRegistrar<ImpetusEvent>().post(stuckButPlain));
    }

    @Test
    void aListenerCanHaveAnotherThreadCancelTheEventBeforeItReturns() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            EventHandlerRegistrar<Cancelable> bus = new EventHandlerRegistrar<>();
            bus.addListener(e -> {
                try {
                    worker.submit(() -> e.setCanceled(true)).get(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });
            Cancelable event = new Cancelable();
            assertTrue(bus.post(event));
            assertTrue(event.isCanceled());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void aThreadWaitingOnTheFlagSeesAnotherThreadCancelAndClearIt() throws Exception {
        Cancelable event = new Cancelable();
        CountDownLatch spinning = new CountDownLatch(1);
        CountDownLatch sawCancel = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            spinning.countDown();
            // Without volatile the JIT may read the flag once and spin on that value for good
            while (!event.isCanceled()) {
                // Busy on purpose, since a sleep or onSpinWait would act as the barrier the flag has to provide
            }
            sawCancel.countDown();
            while (event.isCanceled()) {
                // Busy on purpose, as above
            }
        }, "impetus-event-reader");
        // A reader stuck on a stale value would otherwise keep the test JVM alive
        reader.setDaemon(true);
        reader.start();
        assertTrue(spinning.await(10, TimeUnit.SECONDS));
        // Gives the JIT time to compile the loop before the write it has to see
        Thread.sleep(500);
        event.setCanceled(true);
        assertTrue(sawCancel.await(10, TimeUnit.SECONDS), "the reader never saw the cancellation");
        Thread.sleep(500);
        event.setCanceled(false);
        reader.join(10_000);
        assertFalse(reader.isAlive(), "the reader never saw the cancellation cleared");
    }

    @Test
    void eventsReusedOnManyThreadsAtOnceKeepTheirOwnState() throws Exception {
        EventHandlerRegistrar<Numbered> bus = new EventHandlerRegistrar<>();
        AtomicInteger dirtyEntries = new AtomicInteger();
        bus.addListener(e -> {
            if (e.isCanceled()) {
                dirtyEntries.incrementAndGet();
            }
            if (e.round % 2 == 0) {
                e.setCanceled(true);
            }
        });
        int threads = 8;
        int rounds = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                // Each thread reuses its own event, so the clearing has to be per event and never reach another thread's
                tasks.add(pool.submit(() -> {
                    Numbered event = new Numbered();
                    int wrong = 0;
                    start.await();
                    for (int round = 0; round < rounds; round++) {
                        event.round = round;
                        if (bus.post(event) != (round % 2 == 0) || event.postDepth != 0) {
                            wrong++;
                        }
                    }
                    return wrong;
                }));
            }
            start.countDown();
            for (Future<Integer> task : tasks) {
                assertEquals(0, (int) task.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, dirtyEntries.get());
    }
}
