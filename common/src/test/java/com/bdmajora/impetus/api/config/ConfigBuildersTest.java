package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBuildersTest {
    // Ids stay interned for the whole JVM, so every path here is this class's own and belongs to one test
    private static final String MOD = "config_builders_test";
    private static final TextComponent NAME = TextComponent.literal("Name");
    private static final ConfigStorage<AtomicInteger> STORAGE = ConfigStorage.of(new AtomicInteger(), () -> { });
    // Blank by String.isBlank: empty, spaces, control whitespace and a Unicode em space
    private static final List<String> BLANKS = List.of("", " ", "\t\n", " ");
    // The racing kinds: a group (void) and two option types
    private static final List<Class<?>> KINDS = List.of(void.class, Integer.class, String.class);

    @Test
    void publicShapeStaysTheApiOtherModsCompileAgainst() throws ReflectiveOperationException {
        Class<ConfigBuilders> type = ConfigBuilders.class;
        assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        // Generic signatures are what other mods' sources compile against, and their erasures what compiled mods link against
        Set<String> expected = Set.of(
                "com.bdmajora.impetus.api.config.ConfigPageBuilder page(java.lang.String, java.lang.String, com.bdmajora.impetus.engine.impl.gui.framework.TextComponent)",
                "com.bdmajora.impetus.api.config.ConfigPageBuilder page(com.bdmajora.impetus.api.options.OptionIdentifier<java.lang.Void>, com.bdmajora.impetus.engine.impl.gui.framework.TextComponent)",
                "com.bdmajora.impetus.api.options.structure.OptionGroup$Builder group(java.lang.String, java.lang.String)",
                "com.bdmajora.impetus.api.options.structure.OptionGroup$Builder group(com.bdmajora.impetus.api.options.OptionIdentifier<java.lang.Void>)",
                "<S, T> com.bdmajora.impetus.api.options.structure.OptionImpl$Builder<S, T> option(java.lang.String, java.lang.String, java.lang.Class<T>, com.bdmajora.impetus.api.options.structure.OptionStorage<S>)",
                "<S, T> com.bdmajora.impetus.api.options.structure.OptionImpl$Builder<S, T> option(com.bdmajora.impetus.api.options.OptionIdentifier<T>, java.lang.Class<T>, com.bdmajora.impetus.api.options.structure.OptionStorage<S>)",
                "<T> com.bdmajora.impetus.api.config.ConfigStorage<T> storage(T, java.lang.Runnable)");
        List<Method> published = Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertEquals(expected, published.stream().map(ConfigBuildersTest::signature).collect(Collectors.toSet()));
        assertEquals(expected.size(), published.size());
        for (Method method : published) {
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
        }
        // The rest are private static helpers and there are no fields, so concurrent calls share nothing but OptionIdentifier's synchronised intern table
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPublic(method.getModifiers())) {
                assertTrue(Modifier.isPrivate(method.getModifiers()) && Modifier.isStatic(method.getModifiers()), method.getName());
            }
        }
        assertEquals(List.of(), Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).toList());
        // One private no-argument constructor, so nothing outside can make an instance
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        constructors[0].setAccessible(true);
        assertInstanceOf(type, constructors[0].newInstance());
    }

    @Test
    void pageFromPartsUsesTheInternedUntypedIdAndTheGivenName() {
        ConfigPageBuilder builder = ConfigBuilders.page(MOD, "page_parts", NAME);
        OptionPage page = builder.build();
        assertSame(OptionIdentifier.create(MOD, "page_parts"), page.getId());
        assertSame(void.class, page.getId().getType());
        assertSame(NAME, page.getName());
        assertEquals(List.of(), page.getGroups());
        // Groups keep the order they were added in, and the page lists their options in that order
        Option<?> firstOption = Mockito.mock(Option.class);
        Option<?> secondOption = Mockito.mock(Option.class);
        OptionGroup first = ConfigBuilders.group(MOD, "page_parts_first").add(firstOption).build();
        OptionGroup second = ConfigBuilders.group(MOD, "page_parts_second").add(secondOption).build();
        OptionPage full = builder.addGroup(first).addGroup(second).build();
        assertEquals(List.of(first, second), full.getGroups());
        assertEquals(List.of(firstOption, secondOption), full.getOptions());
        // The builder refuses a null group where it is added, not later when the page is built
        npe("Group must not be null", () -> builder.addGroup(null));
        // Every call hands out its own builder, so a second one for the same page starts empty
        ConfigPageBuilder again = ConfigBuilders.page(MOD, "page_parts", NAME);
        assertNotSame(builder, again);
        assertEquals(List.of(), again.build().getGroups());
        assertSame(page.getId(), again.build().getId());
    }

    @Test
    void pageFromPartsChecksEachArgumentLeftToRightBeforeInterning() {
        npe("Mod id must not be null", () -> ConfigBuilders.page(null, "page_reject", NAME));
        npe("Path must not be null", () -> ConfigBuilders.page(MOD, null, NAME));
        npe("Page name must not be null", () -> ConfigBuilders.page(MOD, "page_reject", null));
        for (String blank : BLANKS) {
            iae("Mod id must not be blank", () -> ConfigBuilders.page(blank, "page_reject", NAME));
            iae("Path must not be blank", () -> ConfigBuilders.page(MOD, blank, NAME));
        }
        // The leftmost bad argument is the one reported
        npe("Mod id must not be null", () -> ConfigBuilders.page(null, null, null));
        iae("Mod id must not be blank", () -> ConfigBuilders.page("", null, null));
        npe("Path must not be null", () -> ConfigBuilders.page(MOD, null, null));
        // A null name used to fail only after the id was interned, which tied the path to void for good
        assertSame(String.class, OptionIdentifier.create(MOD, "page_reject", String.class).getType());
    }

    @Test
    void pageFromIdKeepsThatExactInstanceAndTheGivenName() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "page_id");
        ConfigPageBuilder builder = ConfigBuilders.page(id, NAME);
        OptionPage page = builder.build();
        assertSame(id, page.getId());
        assertSame(NAME, page.getName());
        assertNotSame(builder, ConfigBuilders.page(id, NAME));
        assertSame(id, ConfigBuilders.page(MOD, "page_id", TextComponent.literal("Other")).build().getId());
        // Ids made elsewhere work the same, the built-in ones included
        assertSame(StandardOptions.Pages.GENERAL, ConfigBuilders.page(StandardOptions.Pages.GENERAL, NAME).build().getId());
    }

    @Test
    void pageFromIdChecksTheIdThenTheName() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "page_id_reject");
        npe("Page id must not be null", () -> ConfigBuilders.page(null, NAME));
        npe("Page name must not be null", () -> ConfigBuilders.page(id, null));
        npe("Page id must not be null", () -> ConfigBuilders.page(null, null));
        // An option id cast to Void, and EMPTY, are not page ids even though the compiler takes them
        OptionIdentifier<Void> typed = OptionIdentifier.create(MOD, "page_typed", Integer.class).cast();
        iae("Page id 'config_builders_test:page_typed' has type class java.lang.Integer, but page and group ids have type void", () -> ConfigBuilders.page(typed, NAME));
        iae("Page id ':' has type class java.lang.Void, but page and group ids have type void", () -> ConfigBuilders.page(OptionIdentifier.EMPTY, NAME));
        // A bad id is reported before a missing name
        iae("Page id ':' has type class java.lang.Void, but page and group ids have type void", () -> ConfigBuilders.page(OptionIdentifier.EMPTY, null));
    }

    @Test
    void groupFromPartsPresetsTheInternedIdAndNothingElse() {
        OptionGroup.Builder builder = ConfigBuilders.group(MOD, "group_parts");
        OptionGroup group = builder.build();
        assertSame(OptionIdentifier.create(MOD, "group_parts"), group.getId());
        assertNull(group.getName());
        assertEquals(List.of(), group.getOptions());
        // Every call hands out its own builder, so options added to one never show up in another
        Option<?> option = Mockito.mock(Option.class);
        OptionGroup.Builder again = ConfigBuilders.group(MOD, "group_parts");
        assertNotSame(builder, again);
        assertEquals(List.of(option), builder.add(option).build().getOptions());
        assertEquals(List.of(), again.build().getOptions());
        // A page may share a group's id; ConfigApi keeps their registrations apart
        assertSame(group.getId(), ConfigBuilders.page(MOD, "group_parts", NAME).build().getId());
    }

    @Test
    void groupFromPartsChecksEachArgumentLeftToRightBeforeInterning() {
        npe("Mod id must not be null", () -> ConfigBuilders.group(null, "group_reject"));
        npe("Path must not be null", () -> ConfigBuilders.group(MOD, null));
        for (String blank : BLANKS) {
            iae("Mod id must not be blank", () -> ConfigBuilders.group(blank, "group_reject"));
            iae("Path must not be blank", () -> ConfigBuilders.group(MOD, blank));
        }
        // The leftmost bad argument is the one reported
        npe("Mod id must not be null", () -> ConfigBuilders.group(null, null));
        iae("Mod id must not be blank", () -> ConfigBuilders.group(" ", null));
        assertSame(String.class, OptionIdentifier.create(MOD, "group_reject", String.class).getType());
    }

    @Test
    void groupFromIdPresetsThatExactInstance() {
        OptionIdentifier<Void> id = OptionIdentifier.create(MOD, "group_id");
        OptionGroup.Builder builder = ConfigBuilders.group(id);
        assertSame(id, builder.build().getId());
        assertNotSame(builder, ConfigBuilders.group(id));
        assertSame(id, ConfigBuilders.group(MOD, "group_id").build().getId());
        // Ids made elsewhere work the same, the built-in ones included
        assertSame(StandardOptions.Group.RENDERING, ConfigBuilders.group(StandardOptions.Group.RENDERING).build().getId());
        assertSame(OptionGroup.DEFAULT_ID, ConfigBuilders.group(OptionGroup.DEFAULT_ID).build().getId());
    }

    @Test
    void groupFromIdRefusesNullTypedIdsAndEmpty() {
        // OptionGroup.Builder would quietly swap a null id for DEFAULT_ID, so the mistake is caught here
        npe("Group id must not be null", () -> ConfigBuilders.group(null));
        OptionIdentifier<Void> typed = OptionIdentifier.create(MOD, "group_typed", boolean.class).cast();
        iae("Group id 'config_builders_test:group_typed' has type boolean, but page and group ids have type void", () -> ConfigBuilders.group(typed));
        iae("Group id ':' has type class java.lang.Void, but page and group ids have type void", () -> ConfigBuilders.group(OptionIdentifier.EMPTY));
    }

    @Test
    void optionFromPartsInternsATypedIdAndWiresTheStorage() {
        AtomicInteger data = new AtomicInteger(7);
        ConfigStorage<AtomicInteger> storage = ConfigStorage.of(data, () -> { });
        OptionImpl.Builder<AtomicInteger, Integer> builder = ConfigBuilders.option(MOD, "option_parts", Integer.class, storage);
        assertNotSame(builder, ConfigBuilders.option(MOD, "option_parts", Integer.class, storage));
        OptionImpl<AtomicInteger, Integer> option = builder.setBinding(AtomicInteger::set, AtomicInteger::get).setControl(o -> null).build();
        assertSame(OptionIdentifier.create(MOD, "option_parts", Integer.class), option.getId());
        assertSame(storage, option.getStorage());
        // With no name or tooltip set, both come from the id's parts
        assertEquals(TextComponent.translatable(MOD + ".options.option_parts.name"), option.getName());
        assertEquals(TextComponent.translatable(MOD + ".options.option_parts.tooltip"), option.getTooltip());
        // Values go through the storage that was passed in
        assertEquals(7, option.getValue());
        option.setValue(9);
        option.applyChanges();
        assertEquals(9, data.get());
    }

    @Test
    void optionFromPartsTakesAnyValueType() {
        // Primitives are how the built-in pages type their options, so each keeps its own type rather than its box
        for (Class<?> type : List.of(int.class, boolean.class, Integer.class, String.class, OptionFlag.class, int[].class)) {
            assertSame(type, built(ConfigBuilders.option(MOD, "option_type_" + type.getSimpleName(), type, STORAGE)).getId().getType());
        }
    }

    @Test
    void optionFromPartsChecksEachArgumentLeftToRightBeforeInterning() {
        npe("Mod id must not be null", () -> ConfigBuilders.option(null, "option_reject", Integer.class, STORAGE));
        npe("Path must not be null", () -> ConfigBuilders.option(MOD, null, Integer.class, STORAGE));
        npe("Option type must not be null", () -> ConfigBuilders.option(MOD, "option_reject", null, STORAGE));
        npe("Option storage must not be null", () -> ConfigBuilders.option(MOD, "option_reject", Integer.class, null));
        for (String blank : BLANKS) {
            iae("Mod id must not be blank", () -> ConfigBuilders.option(blank, "option_reject", Integer.class, STORAGE));
            iae("Path must not be blank", () -> ConfigBuilders.option(MOD, blank, Integer.class, STORAGE));
        }
        // void would intern the option as a page or group id, and Void leaves it nothing to hold
        iae("Option type must not be void", () -> ConfigBuilders.option(MOD, "option_reject", void.class, STORAGE));
        iae("Option type must not be class java.lang.Void", () -> ConfigBuilders.option(MOD, "option_reject", Void.class, STORAGE));
        // The leftmost bad argument is the one reported
        npe("Mod id must not be null", () -> ConfigBuilders.option(null, null, null, null));
        npe("Path must not be null", () -> ConfigBuilders.option(MOD, null, null, null));
        npe("Option type must not be null", () -> ConfigBuilders.option(MOD, "option_reject", null, null));
        iae("Option type must not be void", () -> ConfigBuilders.option(MOD, "option_reject", void.class, null));
        // A null type used to intern a typeless id, after which every typed option on the path was refused
        assertSame(Integer.class, built(ConfigBuilders.option(MOD, "option_reject", Integer.class, STORAGE)).getId().getType());
    }

    @Test
    void optionFromIdKeepsThatExactInstance() {
        OptionIdentifier<Integer> id = OptionIdentifier.create(MOD, "option_id", Integer.class);
        OptionImpl.Builder<AtomicInteger, Integer> builder = ConfigBuilders.option(id, Integer.class, STORAGE);
        assertNotSame(builder, ConfigBuilders.option(id, Integer.class, STORAGE));
        assertSame(id, built(builder).getId());
        assertSame(id, built(ConfigBuilders.option(MOD, "option_id", Integer.class, STORAGE)).getId());
        OptionIdentifier<Integer> primitive = OptionIdentifier.create(MOD, "option_id_int", int.class);
        assertSame(primitive, built(ConfigBuilders.option(primitive, int.class, STORAGE)).getId());
        // Built-in options are named with untyped ids cast to their value type, so those pass too
        OptionIdentifier<Integer> renderDistance = StandardOptions.Option.RENDER_DISTANCE.cast();
        assertSame(StandardOptions.Option.RENDER_DISTANCE, built(ConfigBuilders.option(renderDistance, int.class, STORAGE)).getId());
    }

    @Test
    void optionFromIdChecksEachArgumentLeftToRight() {
        OptionIdentifier<Integer> id = OptionIdentifier.create(MOD, "option_id_reject", Integer.class);
        npe("Option id must not be null", () -> ConfigBuilders.option(null, Integer.class, STORAGE));
        npe("Option type must not be null", () -> ConfigBuilders.option(id, null, STORAGE));
        npe("Option storage must not be null", () -> ConfigBuilders.option(id, Integer.class, null));
        npe("Option id must not be null", () -> ConfigBuilders.option(null, null, null));
        npe("Option type must not be null", () -> ConfigBuilders.option(id, null, null));
        // An untyped id with a void type would make the option indistinguishable from the group of the same name
        OptionIdentifier<Void> untyped = OptionIdentifier.create(MOD, "option_id_void");
        iae("Option type must not be void", () -> ConfigBuilders.option(untyped, void.class, STORAGE));
        iae("Option type must not be void", () -> ConfigBuilders.option(untyped, void.class, null));
        iae("Option type must not be class java.lang.Void", () -> ConfigBuilders.option(OptionIdentifier.EMPTY, Void.class, STORAGE));
    }

    @Test
    void optionFromIdRefusesAnIdTypedForAnotherValue() {
        OptionIdentifier<Integer> boxed = OptionIdentifier.create(MOD, "option_mismatch", Integer.class);
        // Erasure lets a cast id through the compiler with any type
        OptionIdentifier<String> asString = boxed.cast();
        iae("Option id 'config_builders_test:option_mismatch' has type class java.lang.Integer, not class java.lang.String", () -> ConfigBuilders.option(asString, String.class, STORAGE));
        // int.class is a Class<Integer>, so this compiles without a cast, yet the intern table holds int and Integer apart
        iae("Option id 'config_builders_test:option_mismatch' has type class java.lang.Integer, not int", () -> ConfigBuilders.option(boxed, int.class, STORAGE));
        // EMPTY stands for no id at all, and would name the option ".options..name"
        OptionIdentifier<Integer> empty = OptionIdentifier.EMPTY.cast();
        iae("Option id ':' has type class java.lang.Void, not class java.lang.Integer", () -> ConfigBuilders.option(empty, Integer.class, STORAGE));
        // A mismatch is reported before a missing storage
        iae("Option id ':' has type class java.lang.Void, not class java.lang.Integer", () -> ConfigBuilders.option(empty, Integer.class, null));
        // The parts form meets the same refusal in the intern table
        clash(MOD + ":option_mismatch", () -> ConfigBuilders.option(MOD, "option_mismatch", String.class, STORAGE));
    }

    @Test
    void onePathHoldsOneKindOfIdForTheWholeGame() {
        // The group comes first, since OptionGroup's class init interns DEFAULT_ID and would throw off the counts below
        OptionIdentifier<Void> group = ConfigBuilders.group(MOD, "clash_group").build().getId();
        OptionIdentifier<Integer> boxed = built(ConfigBuilders.option(MOD, "clash_option", Integer.class, STORAGE)).getId();
        String option = MOD + ":clash_option";
        // Another value type is refused, the primitive of the same box included
        clash(option, () -> ConfigBuilders.option(MOD, "clash_option", String.class, STORAGE));
        clash(option, () -> ConfigBuilders.option(MOD, "clash_option", int.class, STORAGE));
        // Pages and groups are untyped, so they cannot take an option's path, nor an option theirs
        clash(option, () -> ConfigBuilders.page(MOD, "clash_option", NAME));
        clash(option, () -> ConfigBuilders.group(MOD, "clash_option"));
        clash(MOD + ":clash_group", () -> ConfigBuilders.option(MOD, "clash_group", Integer.class, STORAGE));
        // Whoever came first keeps the path
        assertSame(boxed, OptionIdentifier.create(MOD, "clash_option", Integer.class));
        assertSame(group, OptionIdentifier.create(MOD, "clash_group"));
    }

    @Test
    void emptyPartsAreRefusedInsteadOfClashingWithEmpty() {
        // ("", "") is EMPTY's own key, which the intern table would refuse with a message about Void that names no argument
        iae("Mod id must not be blank", () -> ConfigBuilders.page("", "", NAME));
        iae("Mod id must not be blank", () -> ConfigBuilders.group("", ""));
        iae("Mod id must not be blank", () -> ConfigBuilders.option("", "", Integer.class, STORAGE));
        assertSame(Void.class, OptionIdentifier.EMPTY.getType());
    }

    @Test
    void partsAreTakenVerbatim() {
        // Ids must be the ones OptionIdentifier.create makes from the same parts or ConfigApi lookups would miss, so nothing is trimmed or folded
        OptionIdentifier<Void> plain = ConfigBuilders.group(MOD, "verbatim").build().getId();
        OptionIdentifier<Void> upper = ConfigBuilders.group(MOD, "Verbatim").build().getId();
        OptionIdentifier<Void> padded = ConfigBuilders.group(MOD, " verbatim ").build().getId();
        assertNotSame(plain, upper);
        assertNotSame(plain, padded);
        assertEquals(" verbatim ", padded.getPath());
        assertSame(padded, OptionIdentifier.create(MOD, " verbatim "));
        // Paths may nest, as the search results pages' do
        assertEquals("verbatim/nested", ConfigBuilders.group(MOD, "verbatim/nested").build().getId().getPath());
    }

    @Test
    void storageWrapsTheSameDataAndRunsTheActionOncePerSave() {
        List<String> data = new ArrayList<>();
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<List<String>> storage = ConfigBuilders.storage(data, runs::incrementAndGet);
        // Making the storage saves nothing
        assertEquals(0, runs.get());
        assertSame(data, storage.getData());
        storage.save();
        assertEquals(1, runs.get());
        // A Runnable has nowhere to take flags, so every form of save runs it once
        storage.save(Set.of(OptionFlag.REQUIRES_GAME_RESTART));
        storage.save(Set.of());
        assertEquals(3, runs.get());
        // Null flags are still a caller bug, so they are refused before the action runs
        npe("Flags must not be null", () -> storage.save(null));
        assertEquals(3, runs.get());
        // The data is held, not copied, so options read later changes
        data.add("later");
        assertEquals(List.of("later"), storage.getData());
        // Every call wraps afresh, even over the same data and action
        assertNotSame(storage, ConfigBuilders.storage(data, runs::incrementAndGet));
    }

    @Test
    void storageChecksTheDataThenTheAction() {
        npe("Config data must not be null", () -> ConfigBuilders.storage(null, () -> { }));
        npe("Save action must not be null", () -> ConfigBuilders.storage("data", null));
        // The leftmost bad argument is the one reported
        npe("Config data must not be null", () -> ConfigBuilders.storage(null, null));
    }

    @Test
    void aFailingSaveReachesTheCallerAndLeavesTheStorageUsable() {
        IllegalStateException failure = new IllegalStateException("disk full");
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> storage = ConfigBuilders.storage("data", () -> {
            if (runs.incrementAndGet() == 1) {
                throw failure;
            }
        });
        assertSame(failure, assertThrowsExactly(IllegalStateException.class, () -> storage.save()));
        storage.save();
        assertEquals(2, runs.get());
    }

    @Test
    void builtGroupsAndPagesPickUpWhatConfigApiRegisteredUnderTheirIds() {
        OptionIdentifier<Void> pageId = OptionIdentifier.create(MOD, "api_page");
        OptionIdentifier<Void> groupId = OptionIdentifier.create(MOD, "api_group");
        Option<?> extraOption = Mockito.mock(Option.class);
        OptionGroup extraGroup = Mockito.mock(OptionGroup.class);
        try {
            ConfigApi.registerOption(groupId, () -> extraOption);
            ConfigApi.registerGroup(pageId, () -> extraGroup);
            // ConfigApi keys by identity, which only works because both sides reach the same interned id
            Option<?> own = Mockito.mock(Option.class);
            assertEquals(List.of(own, extraOption), ConfigBuilders.group(MOD, "api_group").add(own).build().getOptions());
            assertEquals(List.of(extraOption), ConfigBuilders.group(groupId).build().getOptions());
            OptionGroup ownGroup = ConfigBuilders.group(MOD, "api_own").build();
            assertEquals(List.of(ownGroup, extraGroup), ConfigBuilders.page(MOD, "api_page", NAME).addGroup(ownGroup).build().getGroups());
            assertEquals(List.of(extraGroup), ConfigBuilders.page(pageId, NAME).build().getGroups());
        } finally {
            Map<OptionIdentifier<?>, ?> options = Statics.get(ConfigApi.class, "OPTIONS");
            Map<OptionIdentifier<?>, ?> groups = Statics.get(ConfigApi.class, "GROUPS");
            options.remove(groupId);
            groups.remove(pageId);
        }
    }

    @Test
    void concurrentCallsShareOneInternedIdAndNeverABuilder() throws Exception {
        int threads = 8;
        int rounds = 100;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Object> builders = new ArrayList<>();
        try {
            List<Future<List<Object>>> calls = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    List<Object> made = new ArrayList<>();
                    for (int r = 0; r < rounds; r++) {
                        made.add(ConfigBuilders.page(MOD, "race_shared", NAME));
                        made.add(ConfigBuilders.group(MOD, "race_shared"));
                        made.add(ConfigBuilders.option(MOD, "race_shared_option", Integer.class, STORAGE));
                    }
                    return made;
                }));
            }
            start.countDown();
            for (Future<List<Object>> call : calls) {
                builders.addAll(join(call));
            }
        } finally {
            pool.shutdownNow();
        }
        // Every call made its own builder
        Set<Object> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(builders);
        assertEquals(threads * rounds * 3, distinct.size());
        // and each one carries the single interned id for its path; built here, since a builder belongs to one thread
        OptionIdentifier<Void> shared = OptionIdentifier.create(MOD, "race_shared");
        OptionIdentifier<Integer> option = OptionIdentifier.create(MOD, "race_shared_option", Integer.class);
        for (Object builder : builders) {
            OptionIdentifier<?> id = switch (builder) {
                case ConfigPageBuilder page -> page.build().getId();
                case OptionGroup.Builder group -> group.build().getId();
                case OptionImpl.Builder<?, ?> impl -> built(impl).getId();
                default -> throw new AssertionError(builder);
            };
            assertSame(builder instanceof OptionImpl.Builder ? option : shared, id);
        }
    }

    @Test
    void racingKindsOnOnePathLetExactlyOneWin() throws Exception {
        int threads = 9;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            // Fresh paths each round, so every round races for the first intern
            for (int round = 0; round < 50; round++) {
                String path = "race_kind_" + round;
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Class<?>>> calls = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    int kind = t % KINDS.size();
                    calls.add(pool.submit(() -> {
                        start.await();
                        try {
                            switch (kind) {
                                case 0 -> ConfigBuilders.group(MOD, path);
                                case 1 -> ConfigBuilders.option(MOD, path, Integer.class, STORAGE);
                                default -> ConfigBuilders.option(MOD, path, String.class, STORAGE);
                            }
                            return KINDS.get(kind);
                        } catch (IllegalArgumentException refused) {
                            return null;
                        }
                    }));
                }
                start.countDown();
                Set<Class<?>> winners = new HashSet<>();
                int refused = 0;
                for (Future<Class<?>> call : calls) {
                    Class<?> won = join(call);
                    if (won == null) {
                        refused++;
                    } else {
                        winners.add(won);
                    }
                }
                // One kind got through, every call of that kind did, every other call was refused, and the id kept the winner's type
                assertEquals(1, winners.size(), path);
                assertEquals(threads - threads / KINDS.size(), refused, path);
                Class<?> winner = winners.iterator().next();
                assertSame(winner, OptionIdentifier.create(MOD, path, winner).getType(), path);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSavesAreEachForwarded() throws Exception {
        int threads = 8;
        int saves = 1000;
        LongAdder runs = new LongAdder();
        ConfigStorage<String> storage = ConfigBuilders.storage("data", runs::increment);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    for (int s = 0; s < saves; s++) {
                        if ((s & 1) == 0) {
                            storage.save();
                        } else {
                            storage.save(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD));
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> call : calls) {
                join(call);
            }
        } finally {
            pool.shutdownNow();
        }
        // No save is merged or dropped on the way to the action
        assertEquals((long) threads * saves, runs.sum());
    }

    // Builds with a no-op binding and no control widget, the two parts OptionImpl.Builder insists on
    private static <S, T> OptionImpl<S, T> built(OptionImpl.Builder<S, T> builder) {
        return builder.setBinding((data, value) -> { }, data -> null).setControl(option -> null).build();
    }

    private static void npe(String message, Executable call) {
        rejected(NullPointerException.class, message, call);
    }

    private static void iae(String message, Executable call) {
        rejected(IllegalArgumentException.class, message, call);
    }

    // Fails with exactly this exception and message, and leaves the intern table as it found it
    private static void rejected(Class<? extends RuntimeException> type, String message, Executable call) {
        int before = interned();
        assertEquals(message, assertThrowsExactly(type, call).getMessage());
        assertEquals(before, interned(), message);
    }

    // Refused by the intern table, which names the id already holding the path, and nothing new is interned
    private static void clash(String id, Executable call) {
        int before = interned();
        String message = assertThrowsExactly(IllegalArgumentException.class, call).getMessage();
        assertTrue(message.startsWith("OptionIdentifier '" + id + "' created with differing class type"), message);
        assertEquals(before, interned(), message);
    }

    // Every id interned so far in this JVM; tests that read it run on one thread
    private static int interned() {
        Map<?, ? extends Map<?, ?>> mods = Statics.get(OptionIdentifier.class, "IDENTIFIERS");
        return mods.values().stream().mapToInt(Map::size).sum();
    }

    // Type parameters, generic return type, name and generic parameters, as a mod's source sees the method
    private static String signature(Method method) {
        TypeVariable<Method>[] variables = method.getTypeParameters();
        String parameters = variables.length == 0 ? "" : Arrays.stream(variables).map(TypeVariable::getName).collect(Collectors.joining(", ", "<", "> "));
        return parameters + method.getGenericReturnType().getTypeName() + " " + method.getName()
                + Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).collect(Collectors.joining(", ", "(", ")"));
    }

    // Waits for a task and rethrows what failed inside it, so a broken assertion in a thread reads like any other
    private static <T> T join(Future<T> task) throws Exception {
        try {
            return task.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
