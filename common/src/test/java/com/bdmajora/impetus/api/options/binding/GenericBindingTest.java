package com.bdmajora.impetus.api.options.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class GenericBindingTest {
    @Test
    void shapeIsTwoFinalDelegatesBehindOnePublicConstructor() {
        Class<?> type = GenericBinding.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(OptionBinding.class.isAssignableFrom(type));
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{BiConsumer.class, Function.class}, constructors[0].getParameterTypes());
        // Only final state, so a binding handed to another thread without a lock is complete there
        List<Field> state = Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toList();
        assertEquals(Set.of("setter", "getter"), state.stream().map(Field::getName).collect(Collectors.toSet()));
        for (Field field : state) {
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field::toString);
        }
        // Erasures are what compiled mods link against
        Set<String> methods = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .peek(m -> assertTrue(Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()), m::toString))
                .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("setValue[class java.lang.Object, class java.lang.Object]", "getValue[class java.lang.Object]"), methods);
    }

    @Test
    void writesAndReadsRoundTripThroughTheStorage() {
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>(AtomicInteger::set, AtomicInteger::get);
        AtomicInteger storage = new AtomicInteger(1);
        assertEquals(1, binding.getValue(storage));
        binding.setValue(storage, 5);
        assertEquals(5, storage.get());
        assertEquals(5, binding.getValue(storage));
        // Same calls through the interface reach the same delegates
        OptionBinding<AtomicInteger, Integer> view = binding;
        view.setValue(storage, -3);
        assertEquals(-3, view.getValue(storage));
    }

    @Test
    void constructorRefusesANullSetterThenANullGetter() {
        npe("Setter must not be null", () -> new GenericBinding<AtomicInteger, Integer>(null, AtomicInteger::get));
        npe("Getter must not be null", () -> new GenericBinding<AtomicInteger, Integer>(AtomicInteger::set, null));
        // Parameter order, so both null reports the setter
        npe("Setter must not be null", () -> new GenericBinding<AtomicInteger, Integer>(null, null));
    }

    @Test
    void constructingRunsNeitherDelegate() {
        AtomicInteger runs = new AtomicInteger();
        new GenericBinding<Object, Object>((s, v) -> runs.incrementAndGet(), s -> runs.incrementAndGet());
        assertEquals(0, runs.get());
    }

    @Test
    void aReadRunsOnlyTheGetterOnceAndHandsBackItsResultAsIs() {
        Object storage = new Object();
        Object result = new Object();
        List<Object> reads = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        GenericBinding<Object, Object> binding = new GenericBinding<>((s, v) -> writes.incrementAndGet(), s -> {
            reads.add(s);
            return result;
        });
        assertSame(result, binding.getValue(storage));
        assertSame(result, binding.getValue(storage));
        assertEquals(2, reads.size());
        reads.forEach(seen -> assertSame(storage, seen));
        assertEquals(0, writes.get());
    }

    @Test
    void aWriteRunsOnlyTheSetterOnceWithTheExactStorageAndValue() {
        Object storage = new Object();
        Object value = new Object();
        List<Object[]> writes = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger();
        GenericBinding<Object, Object> binding = new GenericBinding<>((s, v) -> writes.add(new Object[]{s, v}), s -> reads.incrementAndGet());
        binding.setValue(storage, value);
        assertEquals(1, writes.size());
        assertSame(storage, writes.get(0)[0]);
        assertSame(value, writes.get(0)[1]);
        assertEquals(0, reads.get());
    }

    @Test
    void aNullStorageIsRefusedBeforeEitherDelegateRuns() {
        AtomicInteger runs = new AtomicInteger();
        // Delegates that never touch the storage would otherwise let a broken OptionStorage go unnoticed
        GenericBinding<Object, String> binding = new GenericBinding<>((s, v) -> runs.incrementAndGet(), s -> {
            runs.incrementAndGet();
            return "value";
        });
        npe("Storage must not be null", () -> binding.setValue(null, "value"));
        npe("Storage must not be null", () -> binding.setValue(null, null));
        npe("Storage must not be null", () -> binding.getValue(null));
        OptionBinding<Object, String> view = binding;
        npe("Storage must not be null", () -> view.setValue(null, "value"));
        npe("Storage must not be null", () -> view.getValue(null));
        assertEquals(0, runs.get());
        // A refused call leaves the binding usable
        binding.setValue(new Object(), "value");
        assertEquals("value", binding.getValue(new Object()));
        assertEquals(2, runs.get());
    }

    @Test
    void nullValuesPassThroughInBothDirections() {
        GenericBinding<AtomicReference<String>, String> binding = new GenericBinding<>(AtomicReference::set, AtomicReference::get);
        AtomicReference<String> storage = new AtomicReference<>("set");
        binding.setValue(storage, null);
        assertNull(storage.get());
        // A config field Gson left null must still read, so the control can show it and write a real value back
        assertNull(binding.getValue(storage));
        binding.setValue(storage, "healed");
        assertEquals("healed", binding.getValue(storage));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aRawBindingHandedTheWrongTypesFailsInTheDelegatesAndLeavesTheStorageAlone() {
        OptionBinding raw = new GenericBinding<AtomicInteger, Integer>(AtomicInteger::set, AtomicInteger::get);
        AtomicInteger storage = new AtomicInteger(3);
        assertThrows(ClassCastException.class, () -> raw.setValue(storage, "four"));
        assertThrows(ClassCastException.class, () -> raw.setValue("not storage", 4));
        assertThrows(ClassCastException.class, () -> raw.getValue("not storage"));
        assertEquals(3, storage.get());
        assertEquals(3, raw.getValue(storage));
    }

    @Test
    void delegateFailuresReachTheCallerUnwrappedAndLeaveTheBindingUsable() {
        AtomicReference<Throwable> armed = new AtomicReference<>();
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>((s, v) -> {
            throwIfArmed(armed);
            s.set(v);
        }, s -> {
            throwIfArmed(armed);
            return s.get();
        });
        AtomicInteger storage = new AtomicInteger(1);
        // Unchecked, checked (thrown past javac) and an Error: none are caught, wrapped or swallowed
        for (Throwable failure : List.of(new IllegalStateException("disk full"), new IOException("gone"), new StackOverflowError())) {
            armed.set(failure);
            assertSame(failure, assertThrowsExactly(failure.getClass(), () -> binding.setValue(storage, 2)));
            assertSame(failure, assertThrowsExactly(failure.getClass(), () -> binding.getValue(storage)));
        }
        armed.set(null);
        assertEquals(1, storage.get());
        binding.setValue(storage, 2);
        assertEquals(2, binding.getValue(storage));
    }

    @Test
    void oneBindingTouchesOnlyTheStorageEachCallIsGiven() {
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>(AtomicInteger::set, AtomicInteger::get);
        AtomicInteger first = new AtomicInteger(1);
        AtomicInteger second = new AtomicInteger(2);
        binding.setValue(first, 10);
        assertEquals(10, binding.getValue(first));
        assertEquals(2, binding.getValue(second));
        binding.setValue(second, 20);
        assertEquals(10, first.get());
        assertEquals(20, second.get());
    }

    @Test
    void aDelegateCanCallBackIntoItsOwnBinding() {
        List<String> calls = new ArrayList<>();
        AtomicReference<GenericBinding<int[], Integer>> self = new AtomicReference<>();
        // Each write reads the old value and then writes the next value down through the same binding
        self.set(new GenericBinding<>((s, v) -> {
            calls.add("set " + v + " over " + self.get().getValue(s));
            s[0] = v;
            if (v > 0) {
                self.get().setValue(s, v - 1);
            }
        }, s -> s[0]));
        int[] storage = {7};
        self.get().setValue(storage, 2);
        assertEquals(List.of("set 2 over 7", "set 1 over 2", "set 0 over 1"), calls);
        assertEquals(0, storage[0]);
    }

    @Test
    void manyThreadsSharingOneBindingEachReadBackTheirOwnWrites() throws Exception {
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>(AtomicInteger::set, AtomicInteger::get);
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        int rounds = 50_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> mismatches = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int base = t * rounds;
                mismatches.add(pool.submit(() -> {
                    AtomicInteger own = new AtomicInteger(-1);
                    start.await();
                    int wrong = 0;
                    for (int i = 0; i < rounds; i++) {
                        binding.setValue(own, base + i);
                        if (binding.getValue(own) != base + i) {
                            wrong++;
                        }
                    }
                    return wrong;
                }));
            }
            start.countDown();
            for (Future<Integer> mismatch : mismatches) {
                assertEquals(0, mismatch.get(30, SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWritesToOneSharedStorageEachReachTheSetterExactlyOnce() throws Exception {
        LongAdder storage = new LongAdder();
        GenericBinding<LongAdder, Long> binding = new GenericBinding<>(LongAdder::add, LongAdder::sum);
        int writers = Math.max(4, Runtime.getRuntime().availableProcessors());
        int rounds = 50_000;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        try {
            // Only additions happen, so one reader must never see the total go down
            Future<Boolean> monotonic = pool.submit(() -> {
                start.await();
                long last = 0;
                while (writing.get()) {
                    long now = binding.getValue(storage);
                    if (now < last) {
                        return false;
                    }
                    last = now;
                }
                return true;
            });
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < writers; t++) {
                done.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        binding.setValue(storage, 1L);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : done) {
                writer.get(30, SECONDS);
            }
            writing.set(false);
            assertTrue(monotonic.get(30, SECONDS));
            assertEquals((long) writers * rounds, binding.getValue(storage));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theBindingTakesNoLockSoAStalledSetterCannotBlockAnotherThread() throws Exception {
        CountDownLatch inSetter = new CountDownLatch(1);
        CountDownLatch otherThreadDone = new CountDownLatch(1);
        AtomicBoolean waitedOut = new AtomicBoolean();
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>((s, v) -> {
            if (v == 1) {
                inSetter.countDown();
                // Stalls until another thread has gone through the same binding and storage
                try {
                    waitedOut.set(!otherThreadDone.await(10, SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            s.set(v);
        }, AtomicInteger::get);
        AtomicInteger storage = new AtomicInteger();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> stalled = pool.submit(() -> binding.setValue(storage, 1));
            assertTrue(inSetter.await(10, SECONDS));
            // The stalled write has not landed, and this thread is not held behind it
            assertEquals(0, binding.getValue(storage));
            binding.setValue(storage, 2);
            assertEquals(2, binding.getValue(storage));
            otherThreadDone.countDown();
            stalled.get(10, SECONDS);
            assertFalse(waitedOut.get());
            assertEquals(1, storage.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aBindingPublishedThroughARacyFieldIsAlwaysComplete() throws Exception {
        // The final fields are the guarantee; this exercises it on whatever memory model the host has
        Mailbox mailbox = new Mailbox();
        AtomicBoolean publishing = new AtomicBoolean(true);
        AtomicInteger observed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        int publications = 1_000_000;
        int wanted = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> reader = pool.submit(() -> {
                AtomicInteger storage = new AtomicInteger();
                start.await();
                int seen = 0;
                while (publishing.get()) {
                    GenericBinding<AtomicInteger, Integer> binding = mailbox.binding;
                    if (binding != null) {
                        binding.setValue(storage, seen);
                        assertEquals(seen, binding.getValue(storage));
                        observed.setOpaque(++seen);
                    }
                }
                return seen;
            });
            Future<?> writer = pool.submit(() -> {
                start.await();
                long deadline = System.nanoTime() + SECONDS.toNanos(10);
                // Keeps publishing until the reader has raced plenty of them, so the test cannot pass on a starved reader
                for (int i = 0; (i < publications || observed.getOpaque() < wanted) && System.nanoTime() < deadline; i++) {
                    mailbox.binding = new GenericBinding<>(AtomicInteger::set, AtomicInteger::get);
                }
                return null;
            });
            start.countDown();
            writer.get(30, SECONDS);
            publishing.set(false);
            assertTrue(reader.get(30, SECONDS) >= wanted);
        } finally {
            pool.shutdownNow();
        }
    }

    // Deliberately not volatile, so the reader gets no happens-before edge from the writer
    private static final class Mailbox {
        GenericBinding<AtomicInteger, Integer> binding;
    }

    private static void throwIfArmed(AtomicReference<Throwable> armed) {
        Throwable failure = armed.get();
        if (failure != null) {
            throw GenericBindingTest.<RuntimeException>sneak(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable failure) throws E {
        throw (E) failure;
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(NullPointerException.class, call).getMessage());
    }
}