package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.OptionGUIConstructionEvent;
import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.eventbus.BusListeners;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConfigApiTest {
    private static final OptionIdentifier<Void> PAGE_A = OptionIdentifier.create("config_api_test", "page_a");
    private static final OptionIdentifier<Void> PAGE_B = OptionIdentifier.create("config_api_test", "page_b");
    private static final OptionIdentifier<Void> GROUP_A = OptionIdentifier.create("config_api_test", "group_a");
    private static final OptionIdentifier<Void> GROUP_B = OptionIdentifier.create("config_api_test", "group_b");

    // The bucket's private fields, so tests can read the registry and put it back to its starting state
    private static final Field SUPPLIERS = bucketField("suppliers");
    private static final Field SIZE = bucketField("size");

    // The registries are static and the suite shares one JVM, so every test starts empty and leaves nothing for the screen tests
    @BeforeEach
    void startEmpty() {
        clearRegistries();
    }

    @AfterEach
    void leaveEmpty() {
        clearRegistries();
    }

    @Test
    void aNullPageSupplierIsRejectedAndRegistersNothing() {
        assertEquals("Page supplier must not be null", npe(() -> ConfigApi.registerPage(null)));
        assertEquals(0, size(pages()));
        assertEquals(List.of(), screen());
    }

    @Test
    void aNullGroupArgumentIsRejectedAndRegistersNothing() {
        assertEquals("Page id must not be null", npe(() -> ConfigApi.registerGroup(null, ConfigApiTest::group)));
        assertEquals("Group supplier must not be null", npe(() -> ConfigApi.registerGroup(PAGE_A, null)));
        // The id is checked before the supplier
        assertEquals("Page id must not be null", npe(() -> ConfigApi.registerGroup(null, null)));
        assertTrue(table("GROUPS").isEmpty());
        assertEquals(List.of(), groupsOf(PAGE_A));
    }

    @Test
    void aNullOptionArgumentIsRejectedAndRegistersNothing() {
        assertEquals("Group id must not be null", npe(() -> ConfigApi.registerOption(null, ConfigApiTest::option)));
        assertEquals("Option supplier must not be null", npe(() -> ConfigApi.registerOption(GROUP_A, null)));
        assertEquals("Group id must not be null", npe(() -> ConfigApi.registerOption(null, null)));
        assertTrue(table("OPTIONS").isEmpty());
        assertEquals(List.of(), optionsOf(GROUP_A));
    }

    @Test
    void aRejectedRegistrationLeavesTheOthersWorking() {
        OptionPage page = page();
        OptionGroup group = group();
        Option<?> option = option();
        ConfigApi.registerPage(() -> page);
        ConfigApi.registerGroup(PAGE_A, () -> group);
        ConfigApi.registerOption(GROUP_A, () -> option);
        assertThrows(NullPointerException.class, () -> ConfigApi.registerPage(null));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerGroup(PAGE_A, null));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerOption(GROUP_A, null));
        assertEquals(List.of(page), screen());
        assertEquals(List.of(group), groupsOf(PAGE_A));
        assertEquals(List.of(option), optionsOf(GROUP_A));
        OptionPage later = page();
        ConfigApi.registerPage(() -> later);
        assertEquals(List.of(page, later), screen());
    }

    @Test
    void withNothingRegisteredTheScreenKeepsExactlyItsOwnPages() {
        OptionPage builtIn = page();
        assertEquals(List.of(builtIn), screen(builtIn));
        assertEquals(List.of(), screen());
        // Nothing registered means the list is never touched, so even an unmodifiable one is fine
        assertDoesNotThrow(() -> OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(List.of(builtIn))));
    }

    @Test
    void registeredPagesFollowTheBuiltInOnesInRegistrationOrder() {
        OptionPage builtIn = page();
        OptionPage first = page();
        OptionPage second = page();
        OptionPage third = page();
        ConfigApi.registerPage(() -> first);
        ConfigApi.registerPage(() -> second);
        ConfigApi.registerPage(() -> third);
        assertEquals(List.of(builtIn, first, second, third), screen(builtIn));
    }

    @Test
    void aPageSupplierRunsOncePerScreenBuildOnTheBuildingThreadAndNeverAtRegistration() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> caller = new AtomicReference<>();
        OptionPage page = page();
        ConfigApi.registerPage(() -> {
            calls.incrementAndGet();
            caller.set(Thread.currentThread());
            return page;
        });
        assertEquals(0, calls.get());
        screen();
        assertEquals(1, calls.get());
        assertSame(Thread.currentThread(), caller.get());
        screen();
        screen();
        assertEquals(3, calls.get());
    }

    @Test
    void everyScreenBuildGetsWhateverTheSupplierReturnsThatTime() {
        OptionPage first = page();
        OptionPage second = page();
        AtomicInteger builds = new AtomicInteger();
        ConfigApi.registerPage(() -> builds.getAndIncrement() == 0 ? first : second);
        assertSame(first, screen().get(0));
        assertSame(second, screen().get(0));
        // A supplier that hands back one instance gets that instance every time, nothing is copied
        OptionPage shared = page();
        clearRegistries();
        ConfigApi.registerPage(() -> shared);
        assertSame(shared, screen().get(0));
        assertSame(shared, screen().get(0));
    }

    @Test
    void theSameSupplierRegisteredTwiceAddsItsPageTwice() {
        OptionPage page = page();
        Supplier<OptionPage> supplier = () -> page;
        ConfigApi.registerPage(supplier);
        ConfigApi.registerPage(supplier);
        assertEquals(List.of(page, page), screen());
    }

    @Test
    void aNullPageStopsTheBuildWithAMessage() {
        OptionPage before = page();
        OptionPage after = page();
        ConfigApi.registerPage(() -> before);
        ConfigApi.registerPage(() -> null);
        ConfigApi.registerPage(() -> after);
        List<OptionPage> pages = new ArrayList<>();
        assertEquals("Page supplier returned null", npe(() -> OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(pages))));
        // Pages ahead of the bad one are already in; no null and nothing after it
        assertEquals(List.of(before), pages);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aPageOfTheWrongTypeFailsTheBuildInsteadOfJoiningTheList() {
        // Only reachable through an unchecked cast, which is exactly what a mod compiled against a different API might do
        ConfigApi.registerPage((Supplier) () -> "not a page");
        List<OptionPage> pages = new ArrayList<>();
        assertThrows(ClassCastException.class, () -> OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(pages)));
        assertEquals(List.of(), pages);
    }

    @Test
    void aPageSupplierThatThrowsStopsThatBuildAndLeavesTheRegistryUsable() {
        OptionPage before = page();
        OptionPage middle = page();
        OptionPage after = page();
        IllegalStateException failure = new IllegalStateException("broken page");
        AtomicBoolean failing = new AtomicBoolean(true);
        ConfigApi.registerPage(() -> before);
        ConfigApi.registerPage(() -> {
            if (failing.get()) {
                throw failure;
            }
            return middle;
        });
        ConfigApi.registerPage(() -> after);
        List<OptionPage> pages = new ArrayList<>();
        // The exception reaches the poster unwrapped
        assertSame(failure, assertThrows(IllegalStateException.class, () -> OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(pages))));
        assertEquals(List.of(before), pages);
        // Nothing was dropped and the lock was released, so registering and building both still work
        failing.set(false);
        OptionPage extra = page();
        ConfigApi.registerPage(() -> extra);
        assertEquals(List.of(before, middle, after, extra), screen());
    }

    @Test
    void anErrorFromAPageSupplierPassesThroughUntouched() {
        StackOverflowError error = new StackOverflowError();
        ConfigApi.registerPage(() -> {
            throw error;
        });
        assertSame(error, assertThrows(StackOverflowError.class, ConfigApiTest::screen));
    }

    @Test
    void aPageRegisteredMidBuildJoinsFromTheNextBuildEvenWhenItGrowsTheBucket() {
        OptionPage first = page();
        OptionPage second = page();
        OptionPage late = page();
        AtomicBoolean registered = new AtomicBoolean();
        ConfigApi.registerPage(() -> {
            if (registered.compareAndSet(false, true)) {
                ConfigApi.registerPage(() -> late);
            }
            return first;
        });
        ConfigApi.registerPage(() -> second);
        // Two of two slots are full, so the registration inside the build moves the bucket to a new array while the build walks the old one
        assertEquals(2, slots(pages()).length);
        assertEquals(List.of(first, second), screen());
        assertEquals(4, slots(pages()).length);
        assertEquals(List.of(first, second, late), screen());
    }

    @Test
    void aPageSupplierMayBuildTheScreenAgainFromInsideABuild() {
        OptionPage outer = page();
        OptionPage inner = page();
        OptionPage last = page();
        AtomicReference<List<OptionPage>> nested = new AtomicReference<>();
        ConfigApi.registerPage(() -> {
            if (nested.get() == null) {
                nested.set(List.of());
                nested.set(screen());
                return outer;
            }
            return inner;
        });
        ConfigApi.registerPage(() -> last);
        assertEquals(List.of(outer, last), screen());
        assertEquals(List.of(inner, last), nested.get());
    }

    @Test
    void anUnmodifiableScreenListFailsOnlyOnceAPageIsRegistered() {
        OptionPage builtIn = page();
        List<OptionPage> fixed = List.of(builtIn);
        ConfigApi.registerPage(ConfigApiTest::page);
        assertThrows(UnsupportedOperationException.class, () -> OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(fixed)));
        assertEquals(List.of(builtIn), fixed);
    }

    @Test
    void pagesKeepTheirOrderAcrossEveryGrowthOfTheBucket() {
        List<OptionPage> expected = new ArrayList<>();
        for (int n = 1; n <= 70; n++) {
            OptionPage page = page();
            expected.add(page);
            ConfigApi.registerPage(() -> page);
            Object bucket = pages();
            assertEquals(n, size(bucket));
            // Capacity is the smallest power of two that fits, so an append copies only when the array is full
            Supplier<?>[] slots = slots(bucket);
            assertEquals(Math.max(1, Integer.highestOneBit(n - 1) << 1), slots.length, "after " + n);
            for (int i = n; i < slots.length; i++) {
                assertNull(slots[i], "slot " + i + " after " + n);
            }
            assertEquals(expected, screen(), "after " + n);
        }
    }

    @Test
    void aPageWithoutRegisteredGroupsGetsNone() {
        assertEquals(List.of(), groupsOf(PAGE_A));
        ConfigApi.registerGroup(PAGE_B, ConfigApiTest::group);
        assertEquals(List.of(), groupsOf(PAGE_A));
    }

    @Test
    void aGroupGoesOnlyToThePageItWasRegisteredForAndIsOnlyBuiltForIt() {
        AtomicInteger calls = new AtomicInteger();
        OptionGroup group = group();
        ConfigApi.registerGroup(PAGE_A, () -> {
            calls.incrementAndGet();
            return group;
        });
        assertEquals(0, calls.get());
        assertEquals(List.of(), groupsOf(PAGE_B));
        assertEquals(0, calls.get());
        assertEquals(List.of(group), groupsOf(PAGE_A));
        assertEquals(1, calls.get());
        assertEquals(List.of(group), groupsOf(PAGE_A));
        assertEquals(2, calls.get());
    }

    @Test
    void groupsKeepRegistrationOrderPerPageWhenInterleavedWithAnotherPage() {
        OptionGroup a1 = group();
        OptionGroup b1 = group();
        OptionGroup a2 = group();
        OptionGroup b2 = group();
        OptionGroup a3 = group();
        ConfigApi.registerGroup(PAGE_A, () -> a1);
        ConfigApi.registerGroup(PAGE_B, () -> b1);
        ConfigApi.registerGroup(PAGE_A, () -> a2);
        ConfigApi.registerGroup(PAGE_B, () -> b2);
        ConfigApi.registerGroup(PAGE_A, () -> a3);
        assertEquals(List.of(a1, a2, a3), groupsOf(PAGE_A));
        assertEquals(List.of(b1, b2), groupsOf(PAGE_B));
    }

    @Test
    void theSameGroupSupplierCanServeTwiceAndServeSeveralPages() {
        OptionGroup group = group();
        Supplier<OptionGroup> supplier = () -> group;
        ConfigApi.registerGroup(PAGE_A, supplier);
        ConfigApi.registerGroup(PAGE_A, supplier);
        ConfigApi.registerGroup(PAGE_B, supplier);
        assertEquals(List.of(group, group), groupsOf(PAGE_A));
        assertEquals(List.of(group), groupsOf(PAGE_B));
    }

    @Test
    void pageIdsMatchByInternedIdentityAndNothingElse() {
        OptionGroup group = group();
        OptionIdentifier<Void> split = OptionIdentifier.create("config_api_test", "x:y");
        ConfigApi.registerGroup(split, () -> group);
        // Creating the same id again hands back the interned instance, so it matches
        assertSame(split, OptionIdentifier.create("config_api_test", "x:y"));
        assertEquals(List.of(group), groupsOf(OptionIdentifier.create("config_api_test", "x:y")));
        // Same text when printed, other case, padding or an empty half are all other ids
        OptionIdentifier<Void> sameText = OptionIdentifier.create("config_api_test:x", "y");
        assertEquals(split.toString(), sameText.toString());
        for (OptionIdentifier<Void> other : List.of(sameText, OptionIdentifier.create("CONFIG_API_TEST", "x:y"), OptionIdentifier.create("config_api_test", "X:Y"),
                OptionIdentifier.create("config_api_test", " x:y"), OptionIdentifier.create("config_api_test", "x:y "), OptionIdentifier.create("config_api_test", ""),
                OptionIdentifier.create("", "x:y"), OptionIdentifier.EMPTY)) {
            assertEquals(List.of(), groupsOf(other), other.toString());
        }
    }

    @Test
    void anIdOfAnyValueTypeMatchesThroughItsOneInstance() {
        OptionIdentifier<Integer> typed = OptionIdentifier.create("config_api_test", "typed", Integer.class);
        OptionGroup group = group();
        Option<?> option = option();
        ConfigApi.registerGroup(typed.cast(), () -> group);
        ConfigApi.registerOption(typed.cast(), () -> option);
        assertEquals(List.of(group), groupsOf(typed.cast()));
        assertEquals(List.of(option), optionsOf(typed.cast()));
    }

    @Test
    void theEmptyIdIsAKeyLikeAnyOther() {
        OptionGroup group = group();
        Option<?> option = option();
        ConfigApi.registerGroup(OptionIdentifier.EMPTY, () -> group);
        ConfigApi.registerOption(OptionIdentifier.EMPTY, () -> option);
        assertEquals(List.of(group), groupsOf(OptionIdentifier.EMPTY));
        assertEquals(List.of(option), optionsOf(OptionIdentifier.EMPTY));
        assertEquals(List.of(), groupsOf(PAGE_A));
        assertEquals(List.of(), optionsOf(GROUP_A));
    }

    @Test
    void aPageEventWithoutAnIdCannotReachTheApi() {
        ConfigApi.registerGroup(PAGE_A, ConfigApiTest::group);
        // The event refuses a null id itself, so addGroups never looks one up
        assertEquals("Page id must not be null", npe(() -> groupsOf(null)));
        assertEquals(Set.of(PAGE_A), keys("GROUPS"));
    }

    @Test
    void oneIdNamingBothAPageAndAGroupKeepsTheirRegistrationsApart() {
        OptionIdentifier<Void> shared = OptionIdentifier.create("config_api_test", "shared");
        OptionGroup group = group();
        Option<?> option = option();
        ConfigApi.registerGroup(shared, () -> group);
        ConfigApi.registerOption(shared, () -> option);
        assertEquals(List.of(group), groupsOf(shared));
        assertEquals(List.of(option), optionsOf(shared));
        assertEquals(Set.of(shared), keys("GROUPS"));
        assertEquals(Set.of(shared), keys("OPTIONS"));
    }

    @Test
    void aNullGroupStopsThePageBuildNamingThePage() {
        OptionGroup before = group();
        ConfigApi.registerGroup(PAGE_A, () -> before);
        ConfigApi.registerGroup(PAGE_A, () -> null);
        ConfigApi.registerGroup(PAGE_A, ConfigApiTest::group);
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(PAGE_A, TextComponent.literal("A"));
        assertEquals("Group supplier for page config_api_test:page_a returned null", npe(() -> OptionPageConstructionEvent.BUS.post(event)));
        assertEquals(List.of(before), event.getAdditionalGroups());
        // Another page is unaffected
        OptionGroup other = group();
        ConfigApi.registerGroup(PAGE_B, () -> other);
        assertEquals(List.of(other), groupsOf(PAGE_B));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aGroupOfTheWrongTypeFailsThePageBuild() {
        ConfigApi.registerGroup(PAGE_A, (Supplier) () -> 42);
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(PAGE_A, TextComponent.literal("A"));
        assertThrows(ClassCastException.class, () -> OptionPageConstructionEvent.BUS.post(event));
        assertEquals(List.of(), event.getAdditionalGroups());
    }

    @Test
    void aGroupSupplierThatThrowsStopsThatPageAndLeavesTheRegistryUsable() {
        OptionGroup before = group();
        OptionGroup middle = group();
        OptionGroup after = group();
        IllegalArgumentException failure = new IllegalArgumentException("broken group");
        AtomicBoolean failing = new AtomicBoolean(true);
        ConfigApi.registerGroup(PAGE_A, () -> before);
        ConfigApi.registerGroup(PAGE_A, () -> {
            if (failing.get()) {
                throw failure;
            }
            return middle;
        });
        ConfigApi.registerGroup(PAGE_A, () -> after);
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(PAGE_A, TextComponent.literal("A"));
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> OptionPageConstructionEvent.BUS.post(event)));
        assertEquals(List.of(before), event.getAdditionalGroups());
        failing.set(false);
        assertEquals(List.of(before, middle, after), groupsOf(PAGE_A));
    }

    @Test
    void aGroupRegisteredMidBuildJoinsFromTheNextBuildOfItsPage() {
        OptionGroup first = group();
        OptionGroup late = group();
        OptionGroup elsewhere = group();
        AtomicBoolean registered = new AtomicBoolean();
        ConfigApi.registerGroup(PAGE_A, () -> {
            if (registered.compareAndSet(false, true)) {
                // Grows the full one-slot bucket under the running build, and opens a bucket for a page that had none
                ConfigApi.registerGroup(PAGE_A, () -> late);
                ConfigApi.registerGroup(PAGE_B, () -> elsewhere);
            }
            return first;
        });
        assertEquals(List.of(first), groupsOf(PAGE_A));
        assertEquals(List.of(first, late), groupsOf(PAGE_A));
        assertEquals(List.of(elsewhere), groupsOf(PAGE_B));
    }

    @Test
    void aGroupSupplierMayBuildAnotherPageFromInsideABuild() {
        OptionGroup inner = group();
        OptionGroup outer = group();
        AtomicReference<List<OptionGroup>> nested = new AtomicReference<>();
        ConfigApi.registerGroup(PAGE_B, () -> inner);
        ConfigApi.registerGroup(PAGE_A, () -> {
            nested.set(groupsOf(PAGE_B));
            return outer;
        });
        assertEquals(List.of(outer), groupsOf(PAGE_A));
        assertEquals(List.of(inner), nested.get());
    }

    @Test
    void aGroupWithoutRegisteredOptionsKeepsExactlyItsOwn() {
        Option<?> own = option();
        assertEquals(List.of(own), optionsOf(GROUP_A, own));
        ConfigApi.registerOption(GROUP_B, ConfigApiTest::option);
        assertEquals(List.of(own), optionsOf(GROUP_A, own));
        // Nothing registered for this group means its list is never touched, so even an unmodifiable one is fine
        assertDoesNotThrow(() -> OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(GROUP_A, List.of(own))));
    }

    @Test
    void registeredOptionsFollowTheGroupsOwnInRegistrationOrder() {
        Option<?> own = option();
        Option<?> first = option();
        Option<?> second = option();
        Option<?> third = option();
        ConfigApi.registerOption(GROUP_A, () -> first);
        ConfigApi.registerOption(GROUP_B, ConfigApiTest::option);
        ConfigApi.registerOption(GROUP_A, () -> second);
        ConfigApi.registerOption(GROUP_A, () -> third);
        assertEquals(List.of(own, first, second, third), optionsOf(GROUP_A, own));
    }

    @Test
    void anOptionGoesOnlyToTheGroupItWasRegisteredForAndIsOnlyBuiltForIt() {
        AtomicInteger calls = new AtomicInteger();
        Option<?> option = option();
        ConfigApi.registerOption(GROUP_A, () -> {
            calls.incrementAndGet();
            return option;
        });
        assertEquals(0, calls.get());
        assertEquals(List.of(), optionsOf(GROUP_B));
        assertEquals(0, calls.get());
        assertEquals(List.of(option), optionsOf(GROUP_A));
        assertEquals(List.of(option), optionsOf(GROUP_A));
        assertEquals(2, calls.get());
    }

    @Test
    void theSameOptionSupplierCanServeTwiceAndServeSeveralGroups() {
        Option<?> option = option();
        Supplier<Option<?>> supplier = () -> option;
        ConfigApi.registerOption(GROUP_A, supplier);
        ConfigApi.registerOption(GROUP_A, supplier);
        ConfigApi.registerOption(GROUP_B, supplier);
        assertEquals(List.of(option, option), optionsOf(GROUP_A));
        assertEquals(List.of(option), optionsOf(GROUP_B));
    }

    @Test
    void groupIdsMatchByInternedIdentityAndNothingElse() {
        Option<?> option = option();
        OptionIdentifier<Void> split = OptionIdentifier.create("config_api_test", "g:h");
        ConfigApi.registerOption(split, () -> option);
        assertEquals(List.of(option), optionsOf(OptionIdentifier.create("config_api_test", "g:h")));
        for (OptionIdentifier<Void> other : List.of(OptionIdentifier.create("config_api_test:g", "h"), OptionIdentifier.create("CONFIG_API_TEST", "g:h"),
                OptionIdentifier.create("config_api_test", "G:H"), OptionIdentifier.create("config_api_test", "g:h "), OptionIdentifier.EMPTY, OptionGroup.DEFAULT_ID)) {
            assertEquals(List.of(), optionsOf(other), other.toString());
        }
    }

    @Test
    void aGroupEventWithoutAnIdCannotReachTheApi() {
        Option<?> own = option();
        ConfigApi.registerOption(GROUP_A, ConfigApiTest::option);
        // The event refuses a null id itself, so addOptions never looks one up
        assertEquals("Group id must not be null", assertThrows(NullPointerException.class, () -> optionsOf(null, own)).getMessage());
        assertEquals(Set.of(GROUP_A), keys("OPTIONS"));
    }

    @Test
    void aNullOptionStopsTheGroupBuildNamingTheGroup() {
        Option<?> before = option();
        ConfigApi.registerOption(GROUP_A, () -> before);
        ConfigApi.registerOption(GROUP_A, () -> null);
        ConfigApi.registerOption(GROUP_A, ConfigApiTest::option);
        List<Option<?>> options = new ArrayList<>();
        assertEquals("Option supplier for group config_api_test:group_a returned null",
                npe(() -> OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(GROUP_A, options))));
        assertEquals(List.of(before), options);
    }

    @Test
    void aNullOptionFailsARealGroupBuildBeforeItCanReachTheGroup() {
        ConfigApi.registerOption(GROUP_A, () -> null);
        assertEquals("Option supplier for group config_api_test:group_a returned null", npe(() -> OptionGroup.createBuilder().setId(GROUP_A).build()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void anOptionOfTheWrongTypeFailsTheGroupBuildInsteadOfJoiningTheList() {
        // List.add takes Object after erasure, so without ConfigApi's own cast this would slip in silently
        ConfigApi.registerOption(GROUP_A, (Supplier) () -> "not an option");
        List<Option<?>> options = new ArrayList<>();
        assertThrows(ClassCastException.class, () -> OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(GROUP_A, options)));
        assertEquals(List.of(), options);
    }

    @Test
    void anOptionSupplierThatThrowsStopsThatGroupAndLeavesTheRegistryUsable() {
        Option<?> before = option();
        Option<?> middle = option();
        IllegalStateException failure = new IllegalStateException("broken option");
        AtomicBoolean failing = new AtomicBoolean(true);
        ConfigApi.registerOption(GROUP_A, () -> before);
        ConfigApi.registerOption(GROUP_A, () -> {
            if (failing.get()) {
                throw failure;
            }
            return middle;
        });
        List<Option<?>> options = new ArrayList<>();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(GROUP_A, options))));
        assertEquals(List.of(before), options);
        failing.set(false);
        assertEquals(List.of(before, middle), optionsOf(GROUP_A));
    }

    @Test
    void anOptionRegisteredMidBuildJoinsFromTheNextBuildOfItsGroup() {
        Option<?> first = option();
        Option<?> late = option();
        AtomicBoolean registered = new AtomicBoolean();
        ConfigApi.registerOption(GROUP_A, () -> {
            if (registered.compareAndSet(false, true)) {
                ConfigApi.registerOption(GROUP_A, () -> late);
            }
            return first;
        });
        assertEquals(List.of(first), optionsOf(GROUP_A));
        assertEquals(List.of(first, late), optionsOf(GROUP_A));
    }

    @Test
    void anUnmodifiableOptionListFailsOnlyOnceAnOptionIsRegisteredForThatGroup() {
        Option<?> own = option();
        List<Option<?>> fixed = List.of(own);
        ConfigApi.registerOption(GROUP_A, ConfigApiTest::option);
        assertThrows(UnsupportedOperationException.class, () -> OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(GROUP_A, fixed)));
        assertEquals(List.of(own), fixed);
    }

    @Test
    void realGroupsAndPagesPickUpRegistrationsAfterTheirOwnContent() {
        Option<?> own = option();
        Option<?> added = option();
        Option<?> forUnnamed = option();
        ConfigApi.registerOption(GROUP_A, () -> added);
        ConfigApi.registerOption(OptionGroup.DEFAULT_ID, () -> forUnnamed);
        OptionGroup built = OptionGroup.createBuilder().setId(GROUP_A).add(own).build();
        assertEquals(List.of(own, added), built.getOptions());
        // A group built without an id falls back to DEFAULT_ID and gets what was registered there, and only that
        assertEquals(List.of(forUnnamed), OptionGroup.createBuilder().build().getOptions());

        OptionGroup extra = group();
        ConfigApi.registerGroup(PAGE_A, () -> extra);
        OptionPage page = new OptionPage(PAGE_A, TextComponent.literal("A"), List.of(built));
        assertEquals(List.of(built, extra), page.getGroups());
        assertEquals(List.of(own, added), page.getOptions());
        OptionPage plain = new OptionPage(PAGE_B, TextComponent.literal("B"), List.of(built));
        assertEquals(List.of(built), plain.getGroups());
    }

    @Test
    void aRegisteredPageCanAssembleItselfFromRegisteredGroupsAndOptions() {
        Option<?> option = option();
        ConfigApi.registerOption(GROUP_A, () -> option);
        ConfigApi.registerGroup(PAGE_A, () -> OptionGroup.createBuilder().setId(GROUP_A).build());
        ConfigApi.registerPage(() -> new OptionPage(PAGE_A, TextComponent.literal("A"), List.of()));
        // Each build nests a page event inside the screen event and a group event inside that one
        List<OptionPage> first = screen();
        List<OptionPage> second = screen();
        assertEquals(1, first.size());
        OptionPage page = first.get(0);
        assertSame(PAGE_A, page.getId());
        assertEquals(1, page.getGroups().size());
        assertSame(GROUP_A, page.getGroups().get(0).getId());
        assertEquals(List.of(option), page.getOptions());
        // Every build constructs its own page and group
        assertNotSame(page, second.get(0));
        assertNotSame(page.getGroups().get(0), second.get(0).getGroups().get(0));
        assertEquals(List.of(option), second.get(0).getOptions());
    }

    @Test
    void eachBusCarriesOneConfigApiListenerHoweverManyRegistrations() {
        List<EventHandlerRegistrar<?>> buses = List.of(OptionGUIConstructionEvent.BUS, OptionPageConstructionEvent.BUS, OptionGroupConstructionEvent.BUS);
        int[] before = new int[buses.size()];
        for (int i = 0; i < buses.size(); i++) {
            assertEquals(1, ownListeners(buses.get(i)), "bus " + i);
            before[i] = BusListeners.of(buses.get(i)).size();
        }
        for (int i = 0; i < 1_000; i++) {
            ConfigApi.registerPage(ConfigApiTest::page);
            ConfigApi.registerGroup((i & 1) == 0 ? PAGE_A : PAGE_B, ConfigApiTest::group);
            ConfigApi.registerOption((i & 1) == 0 ? GROUP_A : GROUP_B, ConfigApiTest::option);
        }
        // Registering grows the registry only; the buses, and what every post walks, stay the same length
        for (int i = 0; i < buses.size(); i++) {
            assertEquals(1, ownListeners(buses.get(i)), "bus " + i);
            assertEquals(before[i], BusListeners.of(buses.get(i)).size(), "bus " + i);
        }
        assertEquals(1_000, size(pages()));
        assertEquals(Set.of(PAGE_A, PAGE_B), keys("GROUPS"));
        assertEquals(Set.of(GROUP_A, GROUP_B), keys("OPTIONS"));
        assertEquals(500, size(table("GROUPS").get(PAGE_A)));
        assertEquals(500, size(table("OPTIONS").get(GROUP_B)));
    }

    @Test
    void buildingIdsNobodyRegisteredForAllocatesNothingInTheRegistry() {
        ConfigApi.registerGroup(PAGE_A, ConfigApiTest::group);
        ConfigApi.registerOption(GROUP_A, ConfigApiTest::option);
        for (int i = 0; i < 100; i++) {
            OptionIdentifier<Void> id = OptionIdentifier.create("config_api_test", "unregistered_" + i);
            assertEquals(List.of(), groupsOf(id));
            assertEquals(List.of(), optionsOf(id));
        }
        assertEquals(Set.of(PAGE_A), keys("GROUPS"));
        assertEquals(Set.of(GROUP_A), keys("OPTIONS"));
    }

    @Test
    void listenersAddedStraightToTheBusesStillRunAfterConfigApis() {
        OptionPage registeredPage = page();
        OptionPage directPage = page();
        OptionGroup registeredGroup = group();
        OptionGroup directGroup = group();
        Option<?> registeredOption = option();
        Option<?> directOption = option();
        ConfigApi.registerPage(() -> registeredPage);
        ConfigApi.registerGroup(PAGE_A, () -> registeredGroup);
        ConfigApi.registerOption(GROUP_A, () -> registeredOption);
        EventHandlerRegistrar.Handler<OptionGUIConstructionEvent> pageHandler = event -> event.addPage(directPage);
        EventHandlerRegistrar.Handler<OptionPageConstructionEvent> groupHandler = event -> event.addGroup(directGroup);
        EventHandlerRegistrar.Handler<OptionGroupConstructionEvent> optionHandler = event -> event.getOptions().add(directOption);
        OptionGUIConstructionEvent.BUS.addListener(pageHandler);
        OptionPageConstructionEvent.BUS.addListener(groupHandler);
        OptionGroupConstructionEvent.BUS.addListener(optionHandler);
        try {
            // ConfigApi's listeners went on when the class initialised, ahead of these
            assertEquals(List.of(registeredPage, directPage), screen());
            assertEquals(List.of(registeredGroup, directGroup), groupsOf(PAGE_A));
            assertEquals(List.of(registeredOption, directOption), optionsOf(GROUP_A));
            assertEquals(List.of(directGroup), groupsOf(PAGE_B));
        } finally {
            // The bus has no way to unsubscribe, so the handlers come off its list directly
            BusListeners.remove(OptionGUIConstructionEvent.BUS, pageHandler);
            BusListeners.remove(OptionPageConstructionEvent.BUS, groupHandler);
            BusListeners.remove(OptionGroupConstructionEvent.BUS, optionHandler);
        }
    }

    @Test
    void registrationAndLookupCostStayFlatAtScale() {
        int count = 200_000;
        OptionPage page = page();
        OptionGroup group = group();
        Option<?> option = option();
        Supplier<OptionPage> pageSupplier = () -> page;
        Supplier<OptionGroup> groupSupplier = () -> group;
        Supplier<Option<?>> optionSupplier = () -> option;
        // A listener per registration on a copy-on-write bus made both loops quadratic, minutes at this size; buckets keep them linear
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            for (int i = 0; i < count; i++) {
                ConfigApi.registerPage(pageSupplier);
                ConfigApi.registerGroup(PAGE_A, groupSupplier);
                ConfigApi.registerOption(GROUP_A, optionSupplier);
            }
            for (int i = 0; i < count; i++) {
                assertTrue(groupsOf(PAGE_B).isEmpty());
                assertTrue(optionsOf(GROUP_B).isEmpty());
            }
            assertEquals(count, screen().size());
            assertEquals(count, groupsOf(PAGE_A).size());
            assertEquals(count, optionsOf(GROUP_A).size());
        });
        assertEquals(262_144, slots(pages()).length);
        assertEquals(count, size(table("GROUPS").get(PAGE_A)));
    }

    @Test
    void buildsRacingRegistrationsSeeAGrowingPrefixOfTheFinalOrder() throws Exception {
        int writers = 4;
        int perWriter = 2_500;
        OptionPage page = page();
        OptionGroup group = group();
        Option<?> option = option();
        // Suppliers run only on whichever thread builds, and record their tag into the list that build swapped in
        AtomicReference<List<Integer>> sink = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        List<List<Integer>> lastSeen;
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                int writer = w;
                writes.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perWriter; i++) {
                        int tag = writer * perWriter + i;
                        ConfigApi.registerPage(() -> {
                            sink.get().add(tag);
                            return page;
                        });
                        ConfigApi.registerGroup(PAGE_A, () -> {
                            sink.get().add(tag);
                            return group;
                        });
                        ConfigApi.registerOption(GROUP_A, () -> {
                            sink.get().add(tag);
                            return option;
                        });
                    }
                    return null;
                }));
            }
            // Buckets only ever append, so each build sees a prefix of the next one and every writer's tags in the order it registered them
            Future<List<List<Integer>>> read = pool.submit(() -> {
                start.await();
                List<List<Integer>> previous = List.of(List.of(), List.of(), List.of());
                boolean more = true;
                while (more) {
                    more = writing.get();
                    List<List<Integer>> seen = traces(sink);
                    for (int k = 0; k < seen.size(); k++) {
                        assertPrefix(previous.get(k), seen.get(k));
                        assertWriterOrder(seen.get(k), writers, perWriter, false);
                    }
                    previous = seen;
                }
                return previous;
            });
            start.countDown();
            try {
                for (Future<?> write : writes) {
                    join(write);
                }
            } finally {
                writing.set(false);
            }
            lastSeen = join(read);
        } finally {
            pool.shutdownNow();
        }
        // Nothing lost, nothing doubled, and the reader's final build already had everything
        List<List<Integer>> all = traces(sink);
        for (int k = 0; k < all.size(); k++) {
            assertWriterOrder(all.get(k), writers, perWriter, true);
            assertEquals(all.get(k), lastSeen.get(k));
        }
    }

    @Test
    void concurrentBuildsEachSeeEverySupplierInOrder() throws Exception {
        List<OptionPage> pages = new ArrayList<>();
        List<OptionGroup> groups = new ArrayList<>();
        List<Option<?>> options = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            OptionPage page = page();
            OptionGroup group = group();
            Option<?> option = option();
            pages.add(page);
            groups.add(group);
            options.add(option);
            ConfigApi.registerPage(() -> page);
            ConfigApi.registerGroup(PAGE_A, () -> group);
            ConfigApi.registerOption(GROUP_A, () -> option);
        }
        int builders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(builders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> builds = new ArrayList<>();
            for (int b = 0; b < builders; b++) {
                builds.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        assertEquals(pages, screen());
                        assertEquals(groups, groupsOf(PAGE_A));
                        assertEquals(options, optionsOf(GROUP_A));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> build : builds) {
                join(build);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void suppliersRunWithoutTheLockHeld() throws Exception {
        OptionGroup inner = group();
        OptionGroup outer = group();
        Option<?> option = option();
        OptionPage page = page();
        AtomicReference<List<OptionGroup>> seenByOther = new AtomicReference<>();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            ConfigApi.registerGroup(PAGE_B, () -> inner);
            ConfigApi.registerGroup(PAGE_A, () -> {
                // Another thread registers and builds while this supplier is still running, which a held lock would deadlock
                try {
                    join(other.submit(() -> {
                        ConfigApi.registerPage(() -> page);
                        ConfigApi.registerOption(GROUP_A, () -> option);
                        seenByOther.set(groupsOf(PAGE_B));
                        return null;
                    }), 10);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                return outer;
            });
            assertEquals(List.of(outer), groupsOf(PAGE_A));
        } finally {
            other.shutdownNow();
        }
        assertEquals(List.of(inner), seenByOther.get());
        assertEquals(List.of(page), screen());
        assertEquals(List.of(option), optionsOf(GROUP_A));
    }

    @Test
    void publicShapeStaysTheApiOtherModsCompileAgainst() throws ReflectiveOperationException {
        Class<ConfigApi> type = ConfigApi.class;
        assertTrue(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        // Erased descriptors are what compiled mods link against, and the generic ones are what their sources compile against
        Map<String, List<String>> expected = Map.of(
                "registerPage", List.of("java.util.function.Supplier<com.bdmajora.impetus.api.options.structure.OptionPage>"),
                "registerGroup", List.of("com.bdmajora.impetus.api.options.OptionIdentifier<java.lang.Void>",
                        "java.util.function.Supplier<com.bdmajora.impetus.api.options.structure.OptionGroup>"),
                "registerOption", List.of("com.bdmajora.impetus.api.options.OptionIdentifier<java.lang.Void>",
                        "java.util.function.Supplier<com.bdmajora.impetus.api.options.structure.Option<?>>"));
        List<Method> published = Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertEquals(expected.keySet(), published.stream().map(Method::getName).collect(Collectors.toSet()));
        assertEquals(3, published.size());
        for (Method method : published) {
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
            assertEquals(void.class, method.getReturnType(), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
            assertEquals(expected.get(method.getName()), Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).toList(), method.getName());
        }
        type.getMethod("registerPage", Supplier.class);
        type.getMethod("registerGroup", OptionIdentifier.class, Supplier.class);
        type.getMethod("registerOption", OptionIdentifier.class, Supplier.class);
        assertEquals(0, type.getFields().length);
        for (String constant : List.of("LOCK", "PAGES", "GROUPS", "OPTIONS")) {
            int modifiers = type.getDeclaredField(constant).getModifiers();
            assertTrue(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), constant);
        }
        // The bucket is the registry's own detail
        Class<?> bucket = SUPPLIERS.getDeclaringClass();
        assertTrue(Modifier.isPrivate(bucket.getModifiers()) && Modifier.isStatic(bucket.getModifiers()) && Modifier.isFinal(bucket.getModifiers()));
        assertSame(type, bucket.getEnclosingClass());
        // One private no-argument constructor, so nothing outside can make an instance
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        constructors[0].setAccessible(true);
        assertInstanceOf(type, constructors[0].newInstance());
    }

    @Test
    void storageForwardsSavesWithAndWithoutFlags() {
        AtomicReference<Set<OptionFlag>> seen = new AtomicReference<>();
        ConfigStorage<String> flagged = ConfigStorage.of("data", seen::set);
        assertEquals("data", flagged.getData());
        flagged.save();
        assertEquals(Set.of(), seen.get());
        flagged.save(Set.of(OptionFlag.REQUIRES_GAME_RESTART));
        assertEquals(Set.of(OptionFlag.REQUIRES_GAME_RESTART), seen.get());
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> plain = ConfigStorage.of("d", runs::incrementAndGet);
        plain.save(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD));
        assertEquals(1, runs.get());
        assertThrows(NullPointerException.class, () -> ConfigStorage.of("d", (Runnable) null));
        assertThrows(NullPointerException.class, () -> ConfigStorage.of(null, runs::incrementAndGet));
    }

    private static OptionPage page() {
        return Mockito.mock(OptionPage.class);
    }

    private static OptionGroup group() {
        return Mockito.mock(OptionGroup.class);
    }

    private static Option<?> option() {
        return Mockito.mock(Option.class);
    }

    // Builds the screen's page list the way ImpetusVideoOptionsController does, starting from its built-in pages
    private static List<OptionPage> screen(OptionPage... builtIn) {
        List<OptionPage> pages = new ArrayList<>(List.of(builtIn));
        OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(pages));
        return pages;
    }

    // The groups other mods add to a page with this id, the way OptionPage asks for them
    private static List<OptionGroup> groupsOf(OptionIdentifier<Void> pageId) {
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(pageId, TextComponent.literal("page"));
        OptionPageConstructionEvent.BUS.post(event);
        return event.getAdditionalGroups();
    }

    // A group's option list after other mods add to it, the way OptionGroup.Builder asks for them
    private static List<Option<?>> optionsOf(OptionIdentifier<Void> groupId, Option<?>... own) {
        List<Option<?>> options = new ArrayList<>(List.of(own));
        OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(groupId, options));
        return options;
    }

    // One build of each kind, returning the tags their suppliers recorded in call order
    private static List<List<Integer>> traces(AtomicReference<List<Integer>> sink) {
        List<Integer> pages = new ArrayList<>();
        sink.set(pages);
        screen();
        List<Integer> groups = new ArrayList<>();
        sink.set(groups);
        groupsOf(PAGE_A);
        List<Integer> options = new ArrayList<>();
        sink.set(options);
        optionsOf(GROUP_A);
        return List.of(pages, groups, options);
    }

    private static void assertPrefix(List<Integer> earlier, List<Integer> later) {
        assertTrue(later.size() >= earlier.size(), () -> later.size() + " after " + earlier.size());
        assertEquals(earlier, later.subList(0, earlier.size()));
    }

    // Each writer's tags appear once each, from its first in the order it registered them, with nothing missing in between
    private static void assertWriterOrder(List<Integer> tags, int writers, int perWriter, boolean complete) {
        int[] next = new int[writers];
        for (int tag : tags) {
            int writer = tag / perWriter;
            assertEquals(writer * perWriter + next[writer]++, tag);
        }
        if (complete) {
            for (int w = 0; w < writers; w++) {
                assertEquals(perWriter, next[w], "writer " + w);
            }
        }
    }

    private static String npe(Executable executable) {
        return assertThrows(NullPointerException.class, executable).getMessage();
    }

    private static Object pages() {
        return Statics.get(ConfigApi.class, "PAGES");
    }

    private static Map<OptionIdentifier<?>, Object> table(String name) {
        return Statics.get(ConfigApi.class, name);
    }

    private static Set<OptionIdentifier<?>> keys(String name) {
        return Set.copyOf(table(name).keySet());
    }

    private static int size(Object bucket) {
        try {
            return SIZE.getInt(bucket);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Supplier<?>[] slots(Object bucket) {
        try {
            return (Supplier<?>[]) SUPPLIERS.get(bucket);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Puts the registries back to how class init left them, keeping the bus listeners it installed
    private static void clearRegistries() {
        Object pages = pages();
        try {
            SUPPLIERS.set(pages, new Supplier<?>[1]);
            SIZE.setInt(pages, 0);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        table("GROUPS").clear();
        table("OPTIONS").clear();
    }

    private static Field bucketField(String name) {
        try {
            Field field = Class.forName(ConfigApi.class.getName() + "$Bucket").getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ConfigApi's dispatchers are method references, so they show up as its own lambda classes
    private static long ownListeners(EventHandlerRegistrar<?> bus) {
        String prefix = ConfigApi.class.getName() + "$$Lambda";
        return BusListeners.of(bus).stream().filter(handler -> handler.getClass().getName().startsWith(prefix)).count();
    }

    private static <T> T join(Future<T> task) throws Exception {
        return join(task, 60);
    }

    // Waits for a task and rethrows what failed inside it, so a broken assertion in a thread reads like any other
    private static <T> T join(Future<T> task, int seconds) throws Exception {
        try {
            return task.get(seconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
