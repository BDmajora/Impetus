package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.eventbus.BusListeners;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionGroupTest {
    private static final String MOD = "option_group_test";

    // A listener on the group bus for one test, taken off again on close
    private interface Listening extends AutoCloseable {
        @Override
        void close();
    }

    private static Listening listening(EventHandlerRegistrar.Handler<OptionGroupConstructionEvent> handler) {
        OptionGroupConstructionEvent.BUS.addListener(handler);
        return () -> BusListeners.remove(OptionGroupConstructionEvent.BUS, handler);
    }

    // A listener that only acts on the given group, so other tests' builds pass through it untouched
    private static Listening onGroup(OptionIdentifier<Void> id, EventHandlerRegistrar.Handler<OptionGroupConstructionEvent> handler) {
        return listening(event -> {
            if (event.getId() == id) {
                handler.acceptEvent(event);
            }
        });
    }

    private static OptionIdentifier<Void> id(String path) {
        return OptionIdentifier.create(MOD, path);
    }

    // A distinct option that answers only identity, so the group touching anything else fails the test
    private static Option<?> option(String label) {
        return (Option<?>) Proxy.newProxyInstance(Option.class.getClassLoader(), new Class<?>[]{Option.class}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> label;
            default -> throw new AssertionError("Group asked option " + label + " for " + method.getName());
        });
    }

    @Test
    void optionsKeepTheirOrderWithListenerAdditionsLast() {
        Option<?> a = option("a"), b = option("b"), c = option("c"), d = option("d");
        OptionIdentifier<Void> id = id("order");
        try (Listening ignored = onGroup(id, event -> event.getOptions().add(d))) {
            OptionGroup group = OptionGroup.createBuilder().setId(id).add(a).add(b).addConditionally(true, () -> c).build();
            assertEquals(List.of(a, b, c, d), group.getOptions());
        }
    }

    @Test
    void theSameOptionTwiceIsKeptTwice() {
        Option<?> a = option("a");
        OptionGroup group = OptionGroup.createBuilder().setId(id("twice")).add(a).add(a).build();
        assertEquals(List.of(a, a), group.getOptions());
    }

    @Test
    void aConditionalOptionIsOnlyMadeWhenAsked() {
        AtomicInteger made = new AtomicInteger();
        Option<?> a = option("a");
        Supplier<Option<?>> supplier = () -> {
            made.incrementAndGet();
            return a;
        };
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("conditional")).addConditionally(false, supplier);
        assertEquals(0, made.get());
        builder.addConditionally(true, supplier);
        assertEquals(1, made.get());
        assertEquals(List.of(a), builder.build().getOptions());
        // Building does not ask the supplier again
        assertEquals(1, made.get());
    }

    @Test
    void aNullOptionIsRefusedWhereItIsAdded() {
        Option<?> a = option("a");
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("null_option")).add(a);
        assertEquals("Option must not be null", assertThrows(NullPointerException.class, () -> builder.add(null)).getMessage());
        assertEquals("Option supplier returned null", assertThrows(NullPointerException.class, () -> builder.addConditionally(true, () -> null)).getMessage());
        // A missing supplier is a mistake whichever way the condition goes, so it fails on every machine, not just the ones where the condition holds
        assertEquals("Option supplier must not be null", assertThrows(NullPointerException.class, () -> builder.addConditionally(true, null)).getMessage());
        assertEquals("Option supplier must not be null", assertThrows(NullPointerException.class, () -> builder.addConditionally(false, null)).getMessage());
        // Nothing refused got in, so the builder still builds
        assertEquals(List.of(a), builder.build().getOptions());
    }

    @Test
    void aNullIdIsRefusedAndNoIdMeansTheDefault() {
        OptionGroup.Builder builder = OptionGroup.createBuilder();
        assertEquals("Group id must not be null", assertThrows(NullPointerException.class, () -> builder.setId(null)).getMessage());
        assertSame(OptionGroup.DEFAULT_ID, builder.build().getId());
        assertSame(OptionIdentifier.create("impetus", "empty"), OptionGroup.DEFAULT_ID);
        // An unnamed group's event carries the default id, so a listener can tell what it is extending
        AtomicReference<OptionIdentifier<Void>> seen = new AtomicReference<>();
        Option<?> a = option("a");
        try (Listening ignored = listening(event -> {
            if (event.getOptions().contains(a)) {
                seen.set(event.getId());
            }
        })) {
            OptionGroup group = OptionGroup.createBuilder().add(a).build();
            assertSame(OptionGroup.DEFAULT_ID, seen.get());
            assertSame(OptionGroup.DEFAULT_ID, group.getId());
            assertSame(group.id, group.getId());
        }
        // An id set later replaces the default on the next build
        OptionIdentifier<Void> later = id("later");
        assertSame(later, builder.setId(later).build().getId());
    }

    @Test
    void aTypedEmptyIdAModMadeBeforeTheClassLoadedIsAdoptedInsteadOfFailingIt() throws Exception {
        FreshClasses fresh = new FreshClasses(OptionIdentifier.class, OptionGroup.class);
        Class<?> ids = fresh.load(OptionIdentifier.class);
        Method getType = ids.getMethod("getType");
        Object typed = ids.getMethod("create", String.class, String.class, Class.class).invoke(null, "impetus", "empty", Boolean.class);
        Class<?> group = fresh.initialise(OptionGroup.class);
        assertNotSame(OptionGroup.class, group);
        assertSame(typed, group.getField("DEFAULT_ID").get(null));
        assertSame(Boolean.class, getType.invoke(typed));
        // With nobody first, the default is made untyped and is the pair every later create hands out
        FreshClasses clean = new FreshClasses(OptionIdentifier.class, OptionGroup.class);
        Object made = clean.initialise(OptionGroup.class).getField("DEFAULT_ID").get(null);
        Class<?> cleanIds = clean.load(OptionIdentifier.class);
        assertSame(void.class, cleanIds.getMethod("getType").invoke(made));
        assertEquals("impetus:empty", made.toString());
        assertSame(made, cleanIds.getMethod("create", String.class, String.class).invoke(null, "impetus", "empty"));
        // A typed create after the class loaded is the mod's own error
        InvocationTargetException late = assertThrows(InvocationTargetException.class, () -> cleanIds.getMethod("create", String.class, String.class, Class.class).invoke(null, "impetus", "empty", Boolean.class));
        assertInstanceOf(IllegalArgumentException.class, late.getCause());
    }

    @Test
    void theNameIsOptionalAndCanBeCleared() {
        TextComponent heading = TextComponent.literal("Heading");
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("name"));
        assertNull(builder.build().getName());
        assertSame(heading, builder.setName(heading).build().getName());
        assertNull(builder.setName(null).build().getName());
    }

    @Test
    void theBuiltOptionsAreReadOnlyAndTheSameListOnEveryRead() {
        Option<?> extra = option("extra");
        // Empty, one, two and many options, which the JDK stores in different list shapes
        for (int size = 0; size <= 4; size++) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("read_only_" + size));
            for (int i = 0; i < size; i++) {
                builder.add(option("o" + i));
            }
            List<Option<?>> options = builder.build().getOptions();
            assertEquals(size, options.size());
            assertThrows(UnsupportedOperationException.class, () -> options.add(extra));
            assertThrows(UnsupportedOperationException.class, () -> options.addAll(List.of(extra)));
            assertThrows(UnsupportedOperationException.class, () -> options.remove(extra));
            assertThrows(UnsupportedOperationException.class, () -> options.removeIf(o -> true));
            assertThrows(UnsupportedOperationException.class, options::clear);
            if (size > 0) {
                assertThrows(UnsupportedOperationException.class, () -> options.set(0, extra));
            }
            // OptionPage copies a lone group's list with List.copyOf and relies on getting the same list back
            assertSame(options, List.copyOf(options));
        }
        OptionGroup group = OptionGroup.createBuilder().setId(id("same_list")).add(extra).build();
        assertSame(group.getOptions(), group.getOptions());
    }

    @Test
    void theBuilderGoingOnLeavesABuiltGroupAsItWas() {
        Option<?> a = option("a"), b = option("b");
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("going_on")).setName(TextComponent.literal("First")).add(a);
        OptionGroup first = builder.build();
        OptionGroup second = builder.add(b).setName(TextComponent.literal("Second")).setId(id("going_on_2")).build();
        assertEquals(List.of(a), first.getOptions());
        assertEquals("First", first.getName().toString());
        assertSame(id("going_on"), first.getId());
        assertEquals(List.of(a, b), second.getOptions());
        assertEquals("Second", second.getName().toString());
        assertSame(id("going_on_2"), second.getId());
    }

    @Test
    void listenerAdditionsDoNotPileUpInAReusedBuilder() {
        Option<?> a = option("a"), added = option("added");
        OptionIdentifier<Void> id = id("pile_up");
        try (Listening ignored = onGroup(id, event -> event.getOptions().add(added))) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id).add(a);
            assertEquals(List.of(a, added), builder.build().getOptions());
            assertEquals(List.of(a, added), builder.build().getOptions());
            assertEquals(List.of(a, added), builder.build().getOptions());
        }
    }

    @Test
    void aListenerCanReplaceOrRemoveOptionsForOneBuildOnly() {
        Option<?> a = option("a"), b = option("b"), replacement = option("replacement");
        OptionIdentifier<Void> id = id("replace");
        AtomicInteger builds = new AtomicInteger();
        try (Listening ignored = onGroup(id, event -> {
            switch (builds.getAndIncrement()) {
                case 0 -> event.getOptions().set(0, replacement);
                case 1 -> event.getOptions().clear();
                default -> { }
            }
        })) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id).add(a).add(b);
            assertEquals(List.of(replacement, b), builder.build().getOptions());
            assertEquals(List.of(), builder.build().getOptions());
            // The builder still holds what was added to it
            assertEquals(List.of(a, b), builder.build().getOptions());
        }
    }

    @Test
    void aListenerKeepingTheListChangesNeitherTheGroupNorTheBuilder() {
        Option<?> a = option("a"), late = option("late");
        OptionIdentifier<Void> id = id("kept");
        AtomicReference<List<Option<?>>> kept = new AtomicReference<>();
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id).add(a);
        OptionGroup group;
        try (Listening ignored = onGroup(id, event -> kept.compareAndSet(null, event.getOptions()))) {
            group = builder.build();
        }
        kept.get().add(late);
        assertEquals(List.of(a), group.getOptions());
        assertEquals(List.of(a), builder.build().getOptions());
    }

    @Test
    void aNullFromAListenerFailsTheBuildWithTheGroupNamed() {
        Option<?> a = option("a");
        OptionIdentifier<Void> id = id("listener_null");
        AtomicInteger builds = new AtomicInteger();
        try (Listening ignored = onGroup(id, event -> {
            if (builds.getAndIncrement() == 0) {
                event.getOptions().add(null);
            }
        })) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id).add(a);
            NullPointerException thrown = assertThrows(NullPointerException.class, builder::build);
            assertEquals("A listener added a null option to group " + MOD + ":listener_null", thrown.getMessage());
            // The null went into that build's list only
            assertEquals(List.of(a), builder.build().getOptions());
        }
    }

    @Test
    void aThrowingListenerLeavesTheBuilderReusable() {
        Option<?> a = option("a"), added = option("added");
        OptionIdentifier<Void> id = id("throwing");
        IllegalStateException failure = new IllegalStateException("listener broke");
        AtomicInteger builds = new AtomicInteger();
        try (Listening ignored = onGroup(id, event -> {
            if (builds.getAndIncrement() == 0) {
                event.getOptions().add(added);
                throw failure;
            }
        })) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id).add(a);
            assertSame(failure, assertThrows(IllegalStateException.class, builder::build));
            // What the failed build's listener added is gone with it
            assertEquals(List.of(a), builder.build().getOptions());
        }
    }

    @Test
    void theEventFiresOncePerBuildWithTheBuildersOptions() {
        Option<?> a = option("a"), b = option("b");
        OptionIdentifier<Void> id = id("events");
        List<List<Option<?>>> seen = new ArrayList<>();
        try (Listening ignored = onGroup(id, event -> seen.add(List.copyOf(event.getOptions())))) {
            OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id);
            builder.build();
            builder.add(a).build();
            builder.add(b).build();
        }
        assertEquals(List.of(List.of(), List.of(a), List.of(a, b)), seen);
    }

    @Test
    void aLargeGroupKeepsEveryOptionInOrder() {
        int count = 10_000;
        Option<?>[] made = new Option<?>[count];
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id("large"));
        for (int i = 0; i < count; i++) {
            made[i] = option("o" + i);
            builder.add(made[i]);
        }
        List<Option<?>> options = builder.build().getOptions();
        assertEquals(count, options.size());
        for (int i = 0; i < count; i++) {
            assertSame(made[i], options.get(i));
        }
    }

    @Test
    void theGroupAndBuilderHoldOnlyWhatTheyNeed() {
        // Pinned so a new field or method is a decision, not drift
        assertEquals(Set.of("id", "name", "options"), instanceFields(OptionGroup.class));
        assertEquals(Set.of("id", "name", "options"), instanceFields(OptionGroup.Builder.class));
        assertEquals(Set.of("getId", "getName", "getOptions", "createBuilder"), publicMethods(OptionGroup.class));
        assertEquals(Set.of("setId", "setName", "add", "addConditionally", "build"), publicMethods(OptionGroup.Builder.class));
        assertNotSame(OptionGroup.createBuilder(), OptionGroup.createBuilder());
    }

    private static Set<String> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
