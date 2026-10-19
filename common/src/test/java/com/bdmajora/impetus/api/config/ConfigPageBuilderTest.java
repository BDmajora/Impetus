package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConfigPageBuilderTest {
    // Ids stay interned for the whole JVM, so this class keeps to its own mod id
    private static final String MOD = "config_page_builder_test";
    private static final TextComponent NAME = TextComponent.literal("Page");

    @Test
    void shapeIsTwoPublicMethodsOverAPackagePrivateConstructor() {
        Class<ConfigPageBuilder> type = ConfigPageBuilder.class;
        assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        // toGenericString carries any throws clause, so this also pins that neither method declares a checked exception
        Set<String> published = Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::toGenericString).collect(Collectors.toSet());
        assertEquals(Set.of(
                "public com.bdmajora.impetus.api.config.ConfigPageBuilder com.bdmajora.impetus.api.config.ConfigPageBuilder.addGroup(com.bdmajora.impetus.api.options.structure.OptionGroup)",
                "public com.bdmajora.impetus.api.options.structure.OptionPage com.bdmajora.impetus.api.config.ConfigPageBuilder.build()"), published);
        // Only ConfigBuilders makes one, so its id and name checks cannot be skipped from another package
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals("com.bdmajora.impetus.api.config.ConfigPageBuilder(com.bdmajora.impetus.api.options.OptionIdentifier<java.lang.Void>,com.bdmajora.impetus.engine.impl.gui.framework.TextComponent)", constructors[0].toGenericString());
        // Every field is private per-builder state, so two builders never share groups
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isPrivate(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()), field.getName());
            }
        }
    }

    @Test
    void constructorRefusesANullIdThenANullName() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "constructor");
        npe("Page id must not be null", () -> new ConfigPageBuilder(null, NAME));
        npe("Page name must not be null", () -> new ConfigPageBuilder(id, null));
        npe("Page id must not be null", () -> new ConfigPageBuilder(null, null));
    }

    @Test
    void aBuilderWithNoGroupsBuildsAnEmptyPageWithTheGivenIdAndName() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "empty");
        TextComponent name = TextComponent.literal("Empty");
        ConfigPageBuilder builder = new ConfigPageBuilder(id, name);
        OptionPage page = builder.build();
        assertSame(id, page.getId());
        assertSame(name, page.getName());
        assertEquals(List.of(), page.getGroups());
        assertEquals(List.of(), page.getOptions());
        // The empty list is shared from the first build on, before any group was ever added
        assertSame(page.getGroups(), builder.build().getGroups());
    }

    @Test
    void groupsAndTheirOptionsKeepTheirAddOrderAtEverySize() {
        // 1 and 2 groups copy into List.of's two-slot form, 3 into its array form, 11 and 1000 past ArrayList's first growths
        for (int size : new int[] {1, 2, 3, 10, 11, 1000}) {
            ConfigPageBuilder builder = builder("order_" + size);
            List<OptionGroup> groups = new ArrayList<>();
            List<Option<?>> options = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                // Zero, one or two options per group, so empty groups sit between full ones
                Option<?>[] own = new Option<?>[i % 3];
                for (int o = 0; o < own.length; o++) {
                    own[o] = Mockito.mock(Option.class);
                }
                // Every group shares one id, which the duplicate check must not mistake for the same group
                OptionGroup group = group("order", own);
                assertSame(builder, builder.addGroup(group));
                groups.add(group);
                Collections.addAll(options, own);
            }
            OptionPage page = builder.build();
            assertEquals(groups, page.getGroups(), "size " + size);
            assertEquals(options, page.getOptions(), "size " + size);
        }
    }

    @Test
    void aNullGroupIsRefusedWhereItIsAddedAndChangesNothing() {
        OptionGroup kept = group("null_kept");
        ConfigPageBuilder builder = builder("null_group").addGroup(kept);
        OptionPage before = builder.build();
        npe("Group must not be null", () -> builder.addGroup(null));
        OptionPage after = builder.build();
        assertEquals(List.of(kept), after.getGroups());
        // Nothing was added, so the next page reuses the last one's copy
        assertSame(before.getGroups(), after.getGroups());
        // and the builder still takes groups afterwards
        OptionGroup later = group("null_later");
        assertEquals(List.of(kept, later), builder.addGroup(later).build().getGroups());
    }

    @Test
    void theSameGroupTwiceIsRefusedWhereItIsAddedAndChangesNothing() {
        OptionGroup first = group("duplicate_first");
        OptionGroup second = group("duplicate_second");
        ConfigPageBuilder builder = builder("duplicate").addGroup(first).addGroup(second);
        iae("Group 'config_page_builder_test:duplicate_first' is already on page 'config_page_builder_test:duplicate'", () -> builder.addGroup(first));
        iae("Group 'config_page_builder_test:duplicate_second' is already on page 'config_page_builder_test:duplicate'", () -> builder.addGroup(second));
        // Still refused after a build, since every later page from this builder holds the group too
        OptionPage page = builder.build();
        iae("Group 'config_page_builder_test:duplicate_first' is already on page 'config_page_builder_test:duplicate'", () -> builder.addGroup(first));
        OptionPage again = builder.build();
        assertEquals(List.of(first, second), again.getGroups());
        // A refused add leaves the copy valid, so nothing is copied again
        assertSame(page.getGroups(), again.getGroups());
        // A group built without an id is named by DEFAULT_ID in the message
        OptionGroup unnamed = OptionGroup.createBuilder().build();
        builder.addGroup(unnamed);
        iae("Group 'impetus:empty' is already on page 'config_page_builder_test:duplicate'", () -> builder.addGroup(unnamed));
        assertEquals(List.of(first, second, unnamed), builder.build().getGroups());
    }

    @Test
    void distinctGroupsMayShareAnIdAndOneGroupMaySitOnSeveralPages() {
        // Groups built without an id all take DEFAULT_ID, so the duplicate check goes by instance and never by id
        OptionGroup unnamedA = OptionGroup.createBuilder().build();
        OptionGroup unnamedB = OptionGroup.createBuilder().build();
        OptionGroup sharedA = group("shared_id");
        OptionGroup sharedB = group("shared_id");
        assertSame(unnamedA.getId(), unnamedB.getId());
        assertSame(sharedA.getId(), sharedB.getId());
        OptionPage page = builder("shared_page").addGroup(unnamedA).addGroup(unnamedB).addGroup(sharedA).addGroup(sharedB).build();
        assertEquals(List.of(unnamedA, unnamedB, sharedA, sharedB), page.getGroups());
        // A group belongs to no builder, so another page takes it as well
        assertEquals(List.of(sharedA, unnamedA), builder("shared_other").addGroup(sharedA).addGroup(unnamedA).build().getGroups());
        // Two builders for one page id each check only their own groups
        assertEquals(List.of(sharedA), builder("shared_page").addGroup(sharedA).build().getGroups());
    }

    @Test
    void aBuiltPageIsFrozenAgainstItsCallerAndLaterAdds() {
        Option<?> option = Mockito.mock(Option.class);
        OptionGroup first = group("frozen_first", option);
        OptionGroup later = group("frozen_later", Mockito.mock(Option.class));
        ConfigPageBuilder builder = builder("frozen").addGroup(first);
        OptionPage page = builder.build();
        List<OptionGroup> groups = page.getGroups();
        assertThrows(UnsupportedOperationException.class, () -> groups.add(later));
        assertThrows(UnsupportedOperationException.class, () -> groups.add(0, later));
        assertThrows(UnsupportedOperationException.class, () -> groups.addAll(List.of(later)));
        assertThrows(UnsupportedOperationException.class, () -> groups.set(0, later));
        assertThrows(UnsupportedOperationException.class, () -> groups.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> groups.remove(first));
        assertThrows(UnsupportedOperationException.class, groups::clear);
        assertThrows(UnsupportedOperationException.class, () -> groups.removeIf(group -> true));
        assertThrows(UnsupportedOperationException.class, () -> groups.replaceAll(group -> later));
        assertThrows(UnsupportedOperationException.class, () -> groups.sort(null));
        assertThrows(UnsupportedOperationException.class, () -> groups.iterator().remove());
        // Groups added afterwards go to the next page only
        OptionPage next = builder.addGroup(later).build();
        assertEquals(List.of(first), page.getGroups());
        assertEquals(List.of(option), page.getOptions());
        assertEquals(List.of(first, later), next.getGroups());
        assertNotSame(page.getGroups(), next.getGroups());
    }

    @Test
    void everyBuildMakesANewPageAndFiresItsEventButCopiesOnlyAfterAnAdd() throws Throwable {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "rebuild");
        AtomicInteger fired = new AtomicInteger();
        listening(event -> {
            if (event.getId() == id) {
                fired.incrementAndGet();
            }
        }, () -> {
            ConfigPageBuilder builder = new ConfigPageBuilder(id, NAME);
            OptionPage emptyA = builder.build();
            OptionPage emptyB = builder.build();
            assertNotSame(emptyA, emptyB);
            assertSame(emptyA.getGroups(), emptyB.getGroups());
            OptionGroup group = group("rebuild_group");
            OptionPage fullA = builder.addGroup(group).build();
            OptionPage fullB = builder.build();
            assertNotSame(fullA, fullB);
            assertSame(fullA.getGroups(), fullB.getGroups());
            assertNotSame(emptyA.getGroups(), fullA.getGroups());
            assertEquals(List.of(group), fullB.getGroups());
            // The cache skips only the copy; other mods still hear about every page
            assertEquals(4, fired.get());
        });
    }

    @Test
    void registeredGroupsFollowTheBuildersOwnOnEveryBuildWithoutLeakingIntoIt() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "registered");
        List<OptionGroup> supplied = new ArrayList<>();
        ConfigApi.registerGroup(id, () -> {
            OptionGroup extra = group("registered_extra");
            supplied.add(extra);
            return extra;
        });
        try {
            OptionGroup own = group("registered_own");
            ConfigPageBuilder builder = new ConfigPageBuilder(id, NAME).addGroup(own);
            OptionPage first = builder.build();
            OptionPage second = builder.build();
            // The supplier runs once per build, its group lands after the builder's, and the first page's extra never reaches the second
            assertEquals(2, supplied.size());
            assertEquals(List.of(own, supplied.get(0)), first.getGroups());
            assertEquals(List.of(own, supplied.get(1)), second.getGroups());
            // The builder's own groups are all it checks, so an extra may still be added by hand
            OptionGroup adopted = supplied.get(0);
            OptionPage third = builder.addGroup(adopted).build();
            assertEquals(List.of(own, adopted, supplied.get(2)), third.getGroups());
        } finally {
            Map<OptionIdentifier<?>, ?> groups = Statics.get(ConfigApi.class, "GROUPS");
            groups.remove(id);
        }
    }

    @Test
    void aGroupAddedWhileThePageIsBeingBuiltLandsOnTheNextPage() throws Throwable {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "reentrant");
        OptionGroup first = group("reentrant_first");
        OptionGroup late = group("reentrant_late");
        ConfigPageBuilder builder = new ConfigPageBuilder(id, NAME).addGroup(first);
        AtomicBoolean added = new AtomicBoolean();
        listening(event -> {
            if (event.getId() == id && added.compareAndSet(false, true)) {
                builder.addGroup(late);
            }
        }, () -> {
            // The page took its copy before the event fired, so the add neither shows up on it nor disturbs it
            OptionPage during = builder.build();
            assertTrue(added.get());
            assertEquals(List.of(first), during.getGroups());
            OptionPage after = builder.build();
            assertEquals(List.of(first, late), after.getGroups());
            assertEquals(List.of(first), during.getGroups());
        });
    }

    @Test
    void aFailingConstructionListenerReachesTheCallerAndLeavesTheBuilderUsable() throws Throwable {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "failing");
        IllegalStateException failure = new IllegalStateException("listener broke");
        AtomicBoolean fail = new AtomicBoolean(true);
        listening(event -> {
            if (event.getId() == id && fail.getAndSet(false)) {
                throw failure;
            }
        }, () -> {
            OptionGroup group = group("failing_group");
            ConfigPageBuilder builder = new ConfigPageBuilder(id, NAME).addGroup(group);
            assertSame(failure, assertThrowsExactly(IllegalStateException.class, builder::build));
            // The copy made before the failure is still right, and later adds still invalidate it
            assertEquals(List.of(group), builder.build().getGroups());
            OptionGroup more = group("failing_more");
            assertEquals(List.of(group, more), builder.addGroup(more).build().getGroups());
            iae("Group 'config_page_builder_test:failing_group' is already on page 'config_page_builder_test:failing'", () -> builder.addGroup(group));
        });
    }

    private static ConfigPageBuilder builder(String path) {
        return new ConfigPageBuilder(OptionIdentifier.create(MOD, path), NAME);
    }

    private static OptionGroup group(String path, Option<?>... options) {
        OptionGroup.Builder builder = ConfigBuilders.group(MOD, path);
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }

    // Runs the body with the handler on the page construction bus, and takes it off again however the body ends
    private static void listening(EventHandlerRegistrar.Handler<OptionPageConstructionEvent> handler, Executable body) throws Throwable {
        OptionPageConstructionEvent.BUS.addListener(handler);
        try {
            body.execute();
        } finally {
            handlers().remove(handler);
        }
    }

    // The bus has no way to unsubscribe, so handlers come off its list directly
    @SuppressWarnings("unchecked")
    private static List<EventHandlerRegistrar.Handler<OptionPageConstructionEvent>> handlers() {
        try {
            Field field = EventHandlerRegistrar.class.getDeclaredField("handlerList");
            field.setAccessible(true);
            return (List<EventHandlerRegistrar.Handler<OptionPageConstructionEvent>>) field.get(OptionPageConstructionEvent.BUS);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(NullPointerException.class, call).getMessage());
    }

    private static void iae(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(IllegalArgumentException.class, call).getMessage());
    }
}
