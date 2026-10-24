package com.bdmajora.impetus.api.options.structure;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionStorageTest {
    // Counts plain saves and data reads
    private static class Counting implements OptionStorage<Object> {
        final AtomicInteger saves = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public Object getData() {
            this.reads.incrementAndGet();
            return this;
        }

        @Override
        public void save() {
            this.saves.incrementAndGet();
        }
    }

    // A set that fails on any method at all, so the default can be shown never to look inside
    @SuppressWarnings("unchecked")
    private static Set<OptionFlag> untouchable() {
        return (Set<OptionFlag>) Proxy.newProxyInstance(OptionStorageTest.class.getClassLoader(), new Class<?>[]{Set.class},
                (proxy, method, args) -> {
                    throw new AssertionError("Set." + method.getName() + " was called");
                });
    }

    @Test
    void shapeIsOneAbstractReadAndTwoDefaultSaves() throws NoSuchMethodException {
        Class<?> type = OptionStorage.class;
        assertTrue(type.isInterface());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        // Compiler-checked, so lambda storages keep compiling
        assertNotNull(type.getAnnotation(FunctionalInterface.class));
        assertEquals(0, type.getInterfaces().length);
        assertEquals(0, type.getDeclaredFields().length);
        TypeVariable<?>[] parameters = type.getTypeParameters();
        assertEquals(1, parameters.length);
        assertEquals("T", parameters[0].getName());
        // Erasures are what compiled mods link against
        Set<String> methods = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("getData[]", "save[]", "save[interface java.util.Set]"), methods);
        Method getData = type.getMethod("getData");
        assertTrue(Modifier.isAbstract(getData.getModifiers()));
        assertSame(Object.class, getData.getReturnType());
        assertSame(parameters[0], getData.getGenericReturnType());
        Method plain = type.getMethod("save");
        assertTrue(plain.isDefault());
        assertSame(void.class, plain.getReturnType());
        Method flagged = type.getMethod("save", Set.class);
        assertTrue(flagged.isDefault());
        assertSame(void.class, flagged.getReturnType());
        ParameterizedType flagsType = (ParameterizedType) flagged.getGenericParameterTypes()[0];
        assertArrayEquals(new Object[]{OptionFlag.class}, flagsType.getActualTypeArguments());
    }

    @Test
    void aLambdaIsAStorageAndIsAskedOnEveryRead() {
        AtomicInteger calls = new AtomicInteger();
        OptionStorage<Integer> counting = calls::incrementAndGet;
        // Nothing is cached between reads
        assertEquals(1, counting.getData());
        assertEquals(2, counting.getData());
        assertEquals(3, counting.getData());
        // Null data passes through untouched, the interface has no opinion on it
        OptionStorage<String> empty = () -> null;
        assertNull(empty.getData());
        List<String> list = new ArrayList<>();
        OptionStorage<List<String>> same = () -> list;
        assertSame(list, same.getData());
        assertSame(same.getData(), same.getData());
    }

    @Test
    void theDefaultSaveDoesNothingAndNeverReadsTheData() {
        AtomicInteger reads = new AtomicInteger();
        OptionStorage<String> storage = () -> {
            reads.incrementAndGet();
            return "s";
        };
        for (int i = 0; i < 3; i++) {
            storage.save();
            storage.save(Set.of());
            storage.save(EnumSet.allOf(OptionFlag.class));
        }
        assertEquals(0, reads.get());
        // A storage whose data cannot be read still saves
        IllegalStateException unreadable = new IllegalStateException("unreadable");
        OptionStorage<String> broken = () -> {
            throw unreadable;
        };
        assertDoesNotThrow(() -> broken.save());
        assertDoesNotThrow(() -> broken.save(EnumSet.of(OptionFlag.REQUIRES_GAME_RESTART)));
        assertSame(unreadable, assertThrows(IllegalStateException.class, broken::getData));
    }

    @Test
    void theFlaggedSaveRunsThePlainSaveOnceForEveryKindOfSet() {
        Counting storage = new Counting();
        List<Set<OptionFlag>> sets = new ArrayList<>();
        sets.add(Set.of());
        sets.add(Collections.emptySet());
        sets.add(EnumSet.noneOf(OptionFlag.class));
        sets.add(EnumSet.allOf(OptionFlag.class));
        for (OptionFlag flag : OptionFlag.values()) {
            sets.add(EnumSet.of(flag));
            sets.add(Set.of(flag));
            sets.add(Collections.singleton(flag));
        }
        sets.add(Collections.unmodifiableSet(EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_ASSET_RELOAD)));
        sets.add(new HashSet<>(EnumSet.allOf(OptionFlag.class)));
        // A HashSet may hold a null flag; the default never looks, so it is not refused
        sets.add(new HashSet<>(Arrays.asList(OptionFlag.REQUIRES_GAME_RESTART, null)));
        int expected = 0;
        for (Set<OptionFlag> flags : sets) {
            storage.save(flags);
            assertEquals(++expected, storage.saves.get(), flags::toString);
        }
        // The same set again counts again, nothing remembers what was saved
        Set<OptionFlag> repeated = EnumSet.of(OptionFlag.REQUIRES_RENDERER_UPDATE);
        storage.save(repeated);
        storage.save(repeated);
        assertEquals(expected + 2, storage.saves.get());
        assertEquals(0, storage.reads.get());
    }

    @Test
    void theFlaggedSaveNeverLooksInsideOrChangesTheSet() {
        Counting storage = new Counting();
        // Not iterated, sized, copied, hashed or printed, so its cost does not grow with the set
        storage.save(untouchable());
        assertEquals(1, storage.saves.get());
        EnumSet<OptionFlag> flags = EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD);
        EnumSet<OptionFlag> before = EnumSet.copyOf(flags);
        storage.save(flags);
        assertEquals(before, flags);
        EnumSet<OptionFlag> none = EnumSet.noneOf(OptionFlag.class);
        storage.save(none);
        assertTrue(none.isEmpty());
        assertEquals(3, storage.saves.get());
    }

    @Test
    void aNullSetIsRefusedBeforeThePlainSaveRuns() {
        Counting storage = new Counting();
        NullPointerException refused = assertThrows(NullPointerException.class, () -> storage.save(null));
        // Same message as ConfigStorage, so either storage reports a caller bug the same way
        assertEquals("Flags must not be null", refused.getMessage());
        assertEquals(0, storage.saves.get());
        // A lambda storage is refused too, and both still save afterwards
        OptionStorage<String> lambda = () -> "s";
        assertEquals("Flags must not be null", assertThrows(NullPointerException.class, () -> lambda.save(null)).getMessage());
        assertDoesNotThrow(() -> lambda.save(Set.of()));
        storage.save(Set.of());
        assertEquals(1, storage.saves.get());
        assertEquals(0, storage.reads.get());
    }

    @Test
    void overridingOnlyTheFlaggedSaveLeavesThePlainSaveANoOp() {
        // MinecraftOptionsStorage's shape: the plain save does not forward, or the two defaults would call each other forever
        AtomicInteger flagged = new AtomicInteger();
        OptionStorage<String> storage = new OptionStorage<>() {
            @Override
            public String getData() {
                return "s";
            }

            @Override
            public void save(Set<OptionFlag> flags) {
                flagged.incrementAndGet();
            }
        };
        storage.save();
        assertEquals(0, flagged.get());
        storage.save(Set.of());
        // An override takes nulls on its own terms
        storage.save(null);
        assertEquals(2, flagged.get());
    }

    @Test
    void anOverrideCallingTheDefaultReachesItsOwnPlainSave() {
        List<String> calls = new ArrayList<>();
        OptionStorage<String> storage = new OptionStorage<>() {
            @Override
            public String getData() {
                return "s";
            }

            @Override
            public void save() {
                calls.add("plain");
                OptionStorage.super.save();
            }

            @Override
            public void save(Set<OptionFlag> flags) {
                calls.add("flagged " + flags);
                OptionStorage.super.save(flags);
            }
        };
        storage.save(EnumSet.of(OptionFlag.REQUIRES_ASSET_RELOAD));
        assertEquals(List.of("flagged [REQUIRES_ASSET_RELOAD]", "plain"), calls);
        calls.clear();
        // The default's null check runs inside the super call, after the override has started
        assertThrows(NullPointerException.class, () -> storage.save(null));
        assertEquals(List.of("flagged null"), calls);
    }

    @Test
    void whatThePlainSaveThrowsComesOutOfTheFlaggedSaveUnchanged() {
        AtomicInteger attempts = new AtomicInteger();
        List<Throwable> failures = List.of(
                new IllegalStateException("disk full"),
                new RuntimeException(new java.io.IOException("io")),
                new AssertionError("error"),
                new StackOverflowError());
        OptionStorage<String> storage = new OptionStorage<>() {
            @Override
            public String getData() {
                return "s";
            }

            @Override
            public void save() {
                int attempt = attempts.getAndIncrement();
                if (attempt < failures.size()) {
                    Throwable failure = failures.get(attempt);
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    throw (RuntimeException) failure;
                }
            }
        };
        // Neither wrapped nor swallowed, and not retried
        for (Throwable failure : failures) {
            int before = attempts.get();
            assertSame(failure, assertThrows(Throwable.class, () -> storage.save(Set.of(OptionFlag.REQUIRES_GAME_RESTART))));
            assertEquals(before + 1, attempts.get());
        }
        // Nothing is left broken for the next save
        assertDoesNotThrow(() -> storage.save(Set.of()));
        assertEquals(failures.size() + 1, attempts.get());
    }

    @Test
    void theDefaultsKeepNoStateSoConcurrentSavesAllArrive() throws InterruptedException {
        Counting storage = new Counting();
        int threads = 8;
        int each = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int t = 0; t < threads; t++) {
            OptionFlag flag = OptionFlag.values()[t % OptionFlag.values().length];
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    Set<OptionFlag> flags = EnumSet.of(flag);
                    for (int i = 0; i < each; i++) {
                        storage.save(flags);
                    }
                } catch (Throwable failure) {
                    errors.add(failure);
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(List.of(), errors);
        assertEquals(threads * each, storage.saves.get());
    }
}
