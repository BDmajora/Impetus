package com.bdmajora.impetus.engine.impl.util.collections;

import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WeakIdentitySetTest {
    // Equal to every other one, as a value compared by its contents would be
    private static final class Alike {
        @Override
        public boolean equals(Object other) {
            return other instanceof Alike;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    // An element whose own equality throws
    private static final class Hostile {
        @Override
        public boolean equals(Object other) {
            throw new IllegalStateException("equals");
        }

        @Override
        public int hashCode() {
            throw new IllegalStateException("hashCode");
        }
    }

    // The set's entries, copied under its own lock without comparing them
    private static List<?> entries(WeakIdentitySet<?> set) {
        try {
            Field field = WeakIdentitySet.class.getDeclaredField("entries");
            field.setAccessible(true);
            synchronized (set) {
                return List.copyOf((Set<?>) field.get(set));
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // The set's entry for the element, found by identity
    private static Reference<?> entryOf(WeakIdentitySet<?> set, Object element) {
        return entries(set).stream().map(entry -> (Reference<?>) entry).filter(entry -> entry.get() == element).findFirst().orElseThrow();
    }

    // Whether the set still holds this very entry
    private static boolean holds(WeakIdentitySet<?> set, Reference<?> entry) {
        return entries(set).stream().anyMatch(held -> held == entry);
    }

    @Test
    void addReportsWhetherTheElementIsNewAndContainsFindsIt() {
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        Object element = new Object();
        assertFalse(set.contains(element));
        assertTrue(set.add(element));
        assertTrue(set.contains(element));
        assertFalse(set.add(element));
        // A lookup adds nothing
        assertFalse(set.contains(new Object()));
        assertEquals(1, entries(set).size());
    }

    @Test
    void anElementIsHeldByIdentitySoAnEqualOneIsNotTakenForIt() {
        WeakIdentitySet<Alike> set = new WeakIdentitySet<>();
        Alike first = new Alike();
        Alike second = new Alike();
        assertEquals(first, second);
        assertTrue(set.add(first));
        assertFalse(set.contains(second));
        assertTrue(set.add(second));
        assertTrue(set.contains(first) && set.contains(second));
        assertEquals(2, entries(set).size());
    }

    @Test
    void anElementWhoseOwnEqualityThrowsIsHeldWithoutBeingAsked() {
        WeakIdentitySet<Hostile> set = new WeakIdentitySet<>();
        Hostile element = new Hostile();
        assertFalse(set.contains(element));
        assertTrue(set.add(element));
        assertTrue(set.contains(element));
        assertFalse(set.add(element));
        assertFalse(set.contains(new Hostile()));
    }

    @Test
    void aMissingElementIsRefusedRatherThanHeldForever() {
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        assertEquals("Element must not be null", assertThrows(NullPointerException.class, () -> set.add(null)).getMessage());
        assertEquals("Element must not be null", assertThrows(NullPointerException.class, () -> set.contains(null)).getMessage());
        assertEquals(List.of(), entries(set));
    }

    @Test
    void anElementWhoseEntryIsClearedIsForgottenAndItsEntryDropped() {
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        Object element = new Object();
        set.add(element);
        Reference<?> entry = entryOf(set, element);
        assertTrue(holds(set, entry));
        // What the collector does once the element is unreachable: clear the entry and queue it
        entry.enqueue();
        assertFalse(set.contains(element));
        assertFalse(holds(set, entry));
        // Added afresh afterwards, and an add drops queued entries too
        assertTrue(set.add(element));
        Reference<?> again = entryOf(set, element);
        again.enqueue();
        Object other = new Object();
        assertTrue(set.add(other));
        assertFalse(holds(set, again));
        assertEquals(1, entries(set).size());
    }

    @Test
    void anElementNoLongerReachableIsCollectedAndItsEntryDropped() {
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        Reference<?>[] held = addedAndDropped(set);
        WeakReference<?> element = (WeakReference<?>) held[0];
        Reference<?> entry = held[1];
        Object probe = new Object();
        for (int attempt = 0; attempt < 100 && holds(set, entry); attempt++) {
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            // Any lookup drops the entries the collector queued
            set.contains(probe);
        }
        assertNull(element.get());
        assertNull(entry.get());
        assertFalse(holds(set, entry));
    }

    // Adds an element from a frame that then returns, leaving it reachable only through the set
    private static Reference<?>[] addedAndDropped(WeakIdentitySet<Object> set) {
        Object element = new Object();
        assertTrue(set.add(element));
        return new Reference<?>[]{new WeakReference<>(element), entryOf(set, element)};
    }

    @Test
    void anEntryEqualsOnlyAnEntryForTheSameElementAndACollectedOneOnlyItself() throws ReflectiveOperationException {
        Constructor<?> make = Class.forName(WeakIdentitySet.class.getName() + "$Entry").getDeclaredConstructor(Object.class, ReferenceQueue.class);
        make.setAccessible(true);
        Alike element = new Alike();
        Reference<?> entry = (Reference<?>) make.newInstance(element, null);
        Reference<?> same = (Reference<?>) make.newInstance(element, null);
        Reference<?> other = (Reference<?>) make.newInstance(new Alike(), null);
        assertEquals(entry, entry);
        assertEquals(entry, same);
        assertEquals(entry.hashCode(), same.hashCode());
        assertEquals(System.identityHashCode(element), entry.hashCode());
        // Never by the element's own equality, which calls these two equal
        assertNotEquals(entry, other);
        assertNotEquals(entry, new WeakReference<>(element));
        assertNotEquals(entry, null);
        // Once collected it matches nothing else, not even another collected entry, but stays equal to itself
        entry.clear();
        assertEquals(entry, entry);
        assertNotEquals(entry, same);
        same.clear();
        assertNotEquals(entry, same);
        assertEquals(System.identityHashCode(element), entry.hashCode());
    }

    @Test
    void manyThreadsAddingOneElementAddItOnce() throws InterruptedException {
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        Object element = new Object();
        AtomicInteger added = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                if (set.add(element)) {
                    added.incrementAndGet();
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, added.get());
        assertTrue(set.contains(element));
    }
}
