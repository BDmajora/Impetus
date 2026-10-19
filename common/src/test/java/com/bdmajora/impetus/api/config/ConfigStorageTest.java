package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStorageTest {
    @Test
    void shapeIsTwoPublicFactoriesOverOnePrivateConstructor() {
        Class<?> type = ConfigStorage.class;
        assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        assertTrue(OptionStorage.class.isAssignableFrom(type));
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        // Erasures are what compiled mods link against
        Set<String> factories = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && !m.isSynthetic())
                .peek(m -> assertTrue(Modifier.isPublic(m.getModifiers()), m::toString))
                .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "of[class java.lang.Object, interface java.lang.Runnable]",
                "of[class java.lang.Object, interface java.util.function.Consumer]"), factories);
    }

    @Test
    void bothFactoriesHoldTheSameDataInstance() {
        List<String> data = new ArrayList<>();
        assertSame(data, ConfigStorage.of(data, () -> { }).getData());
        assertSame(data, ConfigStorage.of(data, (Consumer<Set<OptionFlag>>) flags -> { }).getData());
        // Held, not copied, so later edits to the data show through
        ConfigStorage<List<String>> storage = ConfigStorage.of(data, () -> { });
        data.add("later");
        assertEquals(List.of("later"), storage.getData());
        // Every call wraps afresh
        Runnable action = () -> { };
        assertNotSame(ConfigStorage.of(data, action), ConfigStorage.of(data, action));
    }

    @Test
    void makingStorageRunsNothing() {
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage.of("data", runs::incrementAndGet);
        ConfigStorage.of("data", (Consumer<Set<OptionFlag>>) flags -> runs.incrementAndGet());
        assertEquals(0, runs.get());
    }

    @Test
    void consumerFactoryChecksTheDataThenTheAction() {
        npe("Config data must not be null", () -> ConfigStorage.of(null, (Consumer<Set<OptionFlag>>) flags -> { }));
        npe("Save action must not be null", () -> ConfigStorage.of("data", (Consumer<Set<OptionFlag>>) null));
        npe("Config data must not be null", () -> ConfigStorage.of(null, (Consumer<Set<OptionFlag>>) null));
    }

    @Test
    void runnableFactoryChecksTheDataThenTheAction() {
        npe("Config data must not be null", () -> ConfigStorage.of(null, () -> { }));
        npe("Save action must not be null", () -> ConfigStorage.of("data", (Runnable) null));
        // Same order as the Consumer overload, so both null reports the data
        npe("Config data must not be null", () -> ConfigStorage.of(null, (Runnable) null));
    }

    @Test
    void plainSaveHandsTheActionAnEmptyImmutableSet() {
        List<Set<OptionFlag>> seen = new ArrayList<>();
        ConfigStorage<String> storage = ConfigStorage.of("data", seen::add);
        storage.save();
        storage.save();
        assertEquals(2, seen.size());
        for (Set<OptionFlag> flags : seen) {
            assertTrue(flags.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> flags.add(OptionFlag.REQUIRES_GAME_RESTART));
        }
    }

    @Test
    void flaggedSaveHandsTheActionEqualFlagsOncePerCall() {
        List<Set<OptionFlag>> seen = new ArrayList<>();
        ConfigStorage<String> storage = ConfigStorage.of("data", seen::add);
        Set<OptionFlag> all = EnumSet.allOf(OptionFlag.class);
        storage.save(all);
        storage.save(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD));
        storage.save(Set.of());
        assertEquals(List.of(all, Set.of(OptionFlag.REQUIRES_ASSET_RELOAD), Set.of()), seen);
    }

    @Test
    void flaggedSaveRefusesNullFlagsBeforeTheActionRuns() {
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> flagged = ConfigStorage.of("data", (Consumer<Set<OptionFlag>>) flags -> runs.incrementAndGet());
        ConfigStorage<String> plain = ConfigStorage.of("data", runs::incrementAndGet);
        npe("Flags must not be null", () -> flagged.save(null));
        // The Runnable never sees the flags, but null is still a caller bug
        npe("Flags must not be null", () -> plain.save(null));
        assertEquals(0, runs.get());
        // A refused save leaves the storage usable
        flagged.save(Set.of());
        plain.save(Set.of());
        assertEquals(2, runs.get());
    }

    @Test
    void flaggedSaveRefusesANullFlagBeforeTheActionRuns() {
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> storage = ConfigStorage.of("data", (Consumer<Set<OptionFlag>>) flags -> runs.incrementAndGet());
        Set<OptionFlag> withNull = new HashSet<>(Arrays.asList(OptionFlag.REQUIRES_RENDERER_RELOAD, null));
        assertThrowsExactly(NullPointerException.class, () -> storage.save(withNull));
        assertEquals(0, runs.get());
    }

    @Test
    void theActionGetsASnapshotNotTheCallersSet() {
        List<Set<OptionFlag>> seen = new ArrayList<>();
        ConfigStorage<String> storage = ConfigStorage.of("data", seen::add);
        EnumSet<OptionFlag> flags = EnumSet.of(OptionFlag.REQUIRES_RENDERER_UPDATE);
        storage.save(flags);
        assertNotSame(flags, seen.get(0));
        // A set the action keeps does not follow later edits to the caller's set
        flags.add(OptionFlag.REQUIRES_GAME_RESTART);
        flags.remove(OptionFlag.REQUIRES_RENDERER_UPDATE);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_UPDATE), seen.get(0));
    }

    @Test
    void anActionCannotChangeTheFlagsTheNextStorageSees() {
        // The options controller hands one set to every dirty storage in turn
        EnumSet<OptionFlag> shared = EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_ASSET_RELOAD);
        ConfigStorage<String> greedy = ConfigStorage.of("first", (Consumer<Set<OptionFlag>>) Set::clear);
        List<Set<OptionFlag>> seen = new ArrayList<>();
        ConfigStorage<String> next = ConfigStorage.of("second", seen::add);
        assertThrows(UnsupportedOperationException.class, () -> greedy.save(shared));
        next.save(shared);
        assertEquals(EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_ASSET_RELOAD), shared);
        assertEquals(List.of(shared), seen);
    }

    @Test
    void runnableActionRunsOncePerSaveOfEitherForm() {
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> storage = ConfigStorage.of("data", runs::incrementAndGet);
        storage.save();
        storage.save(Set.of());
        storage.save(EnumSet.allOf(OptionFlag.class));
        assertEquals(3, runs.get());
    }

    @Test
    void savesThroughTheInterfaceReachTheOverrides() {
        List<Set<OptionFlag>> seen = new ArrayList<>();
        OptionStorage<String> storage = ConfigStorage.of("data", seen::add);
        // The interface's save(flags) default would drop the flags and call save()
        storage.save(Set.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD));
        storage.save();
        assertEquals(List.of(Set.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD), Set.of()), seen);
        assertEquals("data", storage.getData());
    }

    @Test
    void aFailingActionReachesTheCallerAndLeavesTheStorageUsable() {
        IllegalStateException failure = new IllegalStateException("disk full");
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> storage = ConfigStorage.of("data", (Consumer<Set<OptionFlag>>) flags -> {
            if (runs.incrementAndGet() == 1) {
                throw failure;
            }
        });
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> storage.save(Set.of())));
        storage.save(Set.of());
        assertEquals(2, runs.get());
        // The Runnable wrapper passes failures through unwrapped too
        ConfigStorage<String> plain = ConfigStorage.of("data", () -> { throw failure; });
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, plain::save));
    }

    @Test
    void theStorageMethodsAreDeclaredHereNotInherited() throws NoSuchMethodException {
        Class<?> type = ConfigStorage.class;
        for (Method method : List.of(type.getMethod("getData"), type.getMethod("save"), type.getMethod("save", Set.class))) {
            assertSame(type, method.getDeclaringClass(), method::toString);
        }
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(NullPointerException.class, call).getMessage());
    }
}
