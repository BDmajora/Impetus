package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OptionPageTest {
    // Mod id of this class's ids.
    private static final String MOD = "option_page_test";
    private static final TextComponent NAME = TextComponent.literal("Page");

    private static Option<?> option() {
        return Mockito.mock(Option.class);
    }

    private static OptionGroup group(String path, Option<?>... options) {
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(OptionIdentifier.create(MOD, path));
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }

    private static OptionIdentifier<Void> pageId(String path) {
        return OptionIdentifier.create(MOD, path);
    }

    // Adds groups to a page by listener.
    private static void extras(OptionIdentifier<Void> pageId, OptionGroup... groups) {
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == pageId) {
                for (OptionGroup group : groups) {
                    e.addGroup(group);
                }
            }
        });
    }

    private static String npe(Executable executable) {
        return assertThrows(NullPointerException.class, executable).getMessage();
    }

    // Checks every mutator throws.
    private static <T> void assertUnmodifiable(List<T> list, T element) {
        List<T> before = new ArrayList<>(list);
        assertThrows(UnsupportedOperationException.class, () -> list.add(element));
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, element));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(List.of(element)));
        assertThrows(UnsupportedOperationException.class, () -> list.set(0, element));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(element));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> list.removeIf(e -> true));
        assertThrows(UnsupportedOperationException.class, () -> list.replaceAll(e -> element));
        assertThrows(UnsupportedOperationException.class, () -> list.sort(null));
        assertThrows(UnsupportedOperationException.class, list::clear);
        assertThrows(UnsupportedOperationException.class, () -> list.iterator().remove());
        assertEquals(before, list);
        // Checks the list is not copied.
        assertSame(list, List.copyOf(list));
    }

    @Test
    void callerMutatingItsListAfterConstructionDoesNotChangeThePage() {
        Option<?> a = option();
        Option<?> b = option();
        OptionGroup first = group("mutate/first", a);
        OptionGroup second = group("mutate/second", b);
        OptionGroup intruder = group("mutate/intruder", option());

        // Passes an ArrayList.
        List<OptionGroup> caller = new ArrayList<>(List.of(first, second));
        OptionPage page = new OptionPage(pageId("mutate/plain"), NAME, caller);
        caller.add(intruder);
        caller.set(0, intruder);
        caller.remove(second);
        assertEquals(List.of(first, second), page.getGroups());
        assertEquals(List.of(a, b), page.getOptions());
        caller.clear();
        assertEquals(List.of(first, second), page.getGroups());

        // Checks an unmodifiable view is copied.
        List<OptionGroup> backing = new ArrayList<>(List.of(first));
        OptionPage viewed = new OptionPage(pageId("mutate/view"), NAME, Collections.unmodifiableList(backing));
        backing.add(second);
        backing.set(0, intruder);
        assertEquals(List.of(first), viewed.getGroups());
        assertEquals(List.of(a), viewed.getOptions());

        // With listener groups appended the caller's list is still not kept
        OptionIdentifier<Void> extended = pageId("mutate/extended");
        OptionGroup extra = group("mutate/extra", option());
        extras(extended, extra);
        List<OptionGroup> callerWithExtras = new ArrayList<>(List.of(first));
        OptionPage withExtras = new OptionPage(extended, NAME, callerWithExtras);
        callerWithExtras.add(second);
        callerWithExtras.clear();
        assertEquals(List.of(first, extra), withExtras.getGroups());
        assertTrue(callerWithExtras.isEmpty());
    }

    @Test
    void aListenerChangingTheCallersListMidBuildDoesNotReachThePage() {
        OptionGroup own = group("midbuild/own", option());
        OptionGroup intruder = group("midbuild/intruder", option());

        // Checks a list grown mid build.
        OptionIdentifier<Void> grown = pageId("midbuild/grown");
        List<OptionGroup> caller = new ArrayList<>(List.of(own));
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == grown) {
                caller.add(intruder);
            }
        });
        OptionPage page = new OptionPage(grown, NAME, caller);
        assertEquals(List.of(own), page.getGroups());
        assertSame(own.getOptions(), page.getOptions());
        assertEquals(List.of(own, intruder), caller);

        // Checks a list emptied mid build.
        OptionIdentifier<Void> emptied = pageId("midbuild/emptied");
        List<OptionGroup> cleared = new ArrayList<>(List.of(own));
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == emptied) {
                cleared.clear();
            }
        });
        assertEquals(List.of(own), new OptionPage(emptied, NAME, cleared).getGroups());
        assertTrue(cleared.isEmpty());

        // Checks a null added mid build.
        OptionIdentifier<Void> poisoned = pageId("midbuild/poisoned");
        List<OptionGroup> poisonedList = new ArrayList<>(List.of(own));
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == poisoned) {
                poisonedList.add(null);
            }
        });
        assertEquals(List.of(own), new OptionPage(poisoned, NAME, poisonedList).getGroups());
    }

    @Test
    void aMissingIdNameOrListFailsBeforeAnyListenerRuns() {
        TextComponent name = TextComponent.literal("Doomed");
        OptionIdentifier<Void> noName = pageId("doomed/no_name");
        AtomicInteger posts = new AtomicInteger();
        // Adds a listener matched by name.
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getTranslationKey() == name || e.getId() == noName) {
                posts.incrementAndGet();
            }
        });
        OptionGroup real = group("doomed/real", option());
        List<OptionGroup> groups = List.of(real);

        assertEquals("Page id must not be null", npe(() -> new OptionPage(null, name, groups)));
        assertEquals("Page name must not be null", npe(() -> new OptionPage(noName, null, groups)));
        assertEquals("Page groups must not be null", npe(() -> new OptionPage(pageId("doomed/no_list"), name, null)));
        assertEquals("A null group was given to page option_page_test:doomed/null_group", npe(() -> new OptionPage(pageId("doomed/null_group"), name, Arrays.asList(real, null))));
        assertEquals(0, posts.get());

        // Checks good arguments reach the listener.
        OptionPage fine = new OptionPage(pageId("doomed/fine"), name, groups);
        assertEquals(1, posts.get());
        assertEquals(groups, fine.getGroups());
    }

    @Test
    void nullGroupsAreRejectedWithThePageNamed() {
        OptionGroup real = group("null/real", option());

        // Checks a null in the caller's list.
        assertEquals("A null group was given to page option_page_test:null/first", npe(() -> new OptionPage(pageId("null/first"), NAME, Arrays.asList(null, real))));
        assertEquals("A null group was given to page option_page_test:null/last", npe(() -> new OptionPage(pageId("null/last"), NAME, Arrays.asList(real, null))));
        assertEquals("A null group was given to page option_page_test:null/only", npe(() -> new OptionPage(pageId("null/only"), NAME, Collections.singletonList(null))));
        // Checks a Stream.toList list.
        assertEquals("A null group was given to page option_page_test:null/stream", npe(() -> new OptionPage(pageId("null/stream"), NAME, Stream.of(real, null).toList())));

        // Checks a null from a listener.
        OptionIdentifier<Void> fromListener = pageId("null/listener");
        extras(fromListener, (OptionGroup) null);
        assertEquals("A listener added a null group to page option_page_test:null/listener", npe(() -> new OptionPage(fromListener, NAME, List.of(real))));
        assertEquals("A listener added a null group to page option_page_test:null/listener", npe(() -> new OptionPage(fromListener, NAME, List.of())));
        OptionIdentifier<Void> afterReal = pageId("null/after_real");
        extras(afterReal, group("null/listener_real", option()), null);
        assertEquals("A listener added a null group to page option_page_test:null/after_real", npe(() -> new OptionPage(afterReal, NAME, List.of(real))));
    }

    @Test
    void theEventCarriesThePagesIdAndNameAndIsFreshForEveryPage() {
        OptionIdentifier<Void> id = pageId("event/page");
        TextComponent name = TextComponent.literal("Event");
        OptionGroup extra = group("event/extra", option());
        List<OptionPageConstructionEvent> seen = new ArrayList<>();
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == id) {
                seen.add(e);
                e.addGroup(extra);
            }
        });

        OptionPage page = new OptionPage(id, name, List.of());
        assertEquals(1, seen.size());
        assertSame(id, seen.get(0).getId());
        assertSame(name, seen.get(0).getTranslationKey());
        assertSame(id, page.getId());
        assertSame(name, page.getName());

        OptionPage second = new OptionPage(id, name, List.of());
        assertEquals(2, seen.size());
        assertNotSame(seen.get(0), seen.get(1));
        assertEquals(List.of(extra), seen.get(1).getAdditionalGroups());

        // A group added to a kept event once its page is built does not reach that page
        seen.get(0).addGroup(group("event/late", option()));
        seen.get(1).addGroup(group("event/later", option()));
        assertEquals(List.of(extra), page.getGroups());
        assertEquals(List.of(extra), second.getGroups());
        assertSame(extra.getOptions(), second.getOptions());
    }

    @Test
    void aThrowingListenerFailsTheBuildWithItsOwnException() {
        OptionIdentifier<Void> id = pageId("throwing/page");
        IllegalStateException boom = new IllegalStateException("boom");
        AtomicInteger laterRuns = new AtomicInteger();
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == id) {
                e.addGroup(group("throwing/added_before", option()));
                throw boom;
            }
        });
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == id) {
                laterRuns.incrementAndGet();
            }
        });

        assertSame(boom, assertThrows(IllegalStateException.class, () -> new OptionPage(id, NAME, List.of(group("throwing/own", option())))));
        // The bus still runs every listener before it rethrows
        assertEquals(1, laterRuns.get());
    }

    @Test
    void listenersMayBuildPagesAndAddListenersMidBuild() {
        // Checks a nested page build.
        OptionIdentifier<Void> outer = pageId("nested/outer");
        OptionIdentifier<Void> inner = pageId("nested/inner");
        Option<?> innerOption = option();
        Option<?> innerExtraOption = option();
        Option<?> outerOption = option();
        OptionGroup innerOwn = group("nested/inner_own", innerOption);
        OptionGroup innerExtra = group("nested/inner_extra", innerExtraOption);
        extras(inner, innerExtra);
        AtomicReference<OptionPage> innerPage = new AtomicReference<>();
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == outer) {
                OptionPage built = new OptionPage(inner, NAME, List.of(innerOwn));
                innerPage.set(built);
                for (OptionGroup group : built.getGroups()) {
                    e.addGroup(group);
                }
            }
        });
        OptionGroup outerOwn = group("nested/outer_own", outerOption);
        OptionPage page = new OptionPage(outer, NAME, List.of(outerOwn));
        assertEquals(List.of(innerOwn, innerExtra), innerPage.get().getGroups());
        assertEquals(List.of(innerOption, innerExtraOption), innerPage.get().getOptions());
        assertEquals(List.of(outerOwn, innerOwn, innerExtra), page.getGroups());
        assertEquals(List.of(outerOption, innerOption, innerExtraOption), page.getOptions());

        // Checks a listener added mid build.
        OptionIdentifier<Void> late = pageId("nested/late");
        OptionGroup lateGroup = group("nested/late_group", option());
        AtomicBoolean added = new AtomicBoolean();
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == late && !added.getAndSet(true)) {
                extras(late, lateGroup);
            }
        });
        assertTrue(new OptionPage(late, NAME, List.of()).getGroups().isEmpty());
        assertEquals(List.of(lateGroup), new OptionPage(late, NAME, List.of()).getGroups());
    }

    @Test
    void groupsAndOptionsAreUnmodifiableWithoutListenerGroups() {
        OptionGroup spare = group("plain/spare", option());
        Option<?> spareOption = option();

        OptionPage fromArrayList = new OptionPage(pageId("plain/array"), NAME, new ArrayList<>(List.of(group("plain/a", option(), option()), group("plain/b", option()))));
        assertUnmodifiable(fromArrayList.getGroups(), spare);
        assertUnmodifiable(fromArrayList.getOptions(), spareOption);

        OptionPage single = new OptionPage(pageId("plain/single"), NAME, new ArrayList<>(List.of(group("plain/lone", option()))));
        assertUnmodifiable(single.getGroups(), spare);
        assertUnmodifiable(single.getOptions(), spareOption);

        OptionPage empty = new OptionPage(pageId("plain/empty"), NAME, new ArrayList<>());
        assertTrue(empty.getGroups().isEmpty());
        assertTrue(empty.getOptions().isEmpty());
        assertUnmodifiable(empty.getGroups(), spare);
        assertUnmodifiable(empty.getOptions(), spareOption);

        // Groups with nothing in them share the one empty list rather than each page making its own
        OptionPage emptyGroups = new OptionPage(pageId("plain/empty_groups"), NAME, List.of(group("plain/hollow_a"), group("plain/hollow_b")));
        assertSame(List.of(), emptyGroups.getOptions());
        assertSame(List.of(), empty.getOptions());
        assertUnmodifiable(emptyGroups.getOptions(), spareOption);
    }

    @Test
    void groupsAndOptionsAreUnmodifiableWithListenerGroups() {
        OptionIdentifier<Void> id = pageId("extended/page");
        extras(id, group("extended/extra", option()));
        OptionPage page = new OptionPage(id, NAME, new ArrayList<>(List.of(group("extended/own", option()))));
        assertEquals(2, page.getGroups().size());
        assertEquals(2, page.getOptions().size());
        assertUnmodifiable(page.getGroups(), group("extended/spare"));
        assertUnmodifiable(page.getOptions(), option());

        // Checks a page of listener groups only.
        OptionIdentifier<Void> onlyExtras = pageId("extended/only");
        OptionGroup lone = group("extended/lone", option());
        extras(onlyExtras, lone);
        OptionPage fromListener = new OptionPage(onlyExtras, NAME, List.of());
        assertEquals(List.of(lone), fromListener.getGroups());
        assertSame(lone.getOptions(), fromListener.getOptions());
        assertUnmodifiable(fromListener.getGroups(), group("extended/spare_two"));
        assertUnmodifiable(fromListener.getOptions(), option());

        // Several listener groups after several own ones
        OptionIdentifier<Void> many = pageId("extended/many");
        extras(many, group("extended/many_a", option()), group("extended/many_b"), group("extended/many_c", option(), option()));
        OptionPage crowded = new OptionPage(many, NAME, List.of(group("extended/own_a", option()), group("extended/own_b", option())));
        assertEquals(5, crowded.getGroups().size());
        assertEquals(5, crowded.getOptions().size());
        assertUnmodifiable(crowded.getGroups(), group("extended/spare_three"));
        assertUnmodifiable(crowded.getOptions(), option());
    }

    @Test
    void listenerGroupsFollowTheOwnGroupsInListenerThenAddOrder() {
        OptionIdentifier<Void> id = pageId("order/page");
        OptionGroup own1 = group("order/own1", option());
        OptionGroup own2 = group("order/own2", option());
        OptionGroup extraA = group("order/extra_a", option());
        OptionGroup extraB = group("order/extra_b", option());
        OptionGroup extraC = group("order/extra_c", option());
        extras(id, extraA, extraB);
        extras(id, extraC);
        // A listener for another page adds nothing here
        extras(pageId("order/other"), group("order/stray", option()));

        OptionPage page = new OptionPage(id, NAME, List.of(own1, own2));
        assertEquals(List.of(own1, own2, extraA, extraB, extraC), page.getGroups());

        // Checks a second page.
        OptionPage again = new OptionPage(id, NAME, List.of(own2));
        assertEquals(List.of(own2, extraA, extraB, extraC), again.getGroups());
        assertEquals(List.of(own1, own2, extraA, extraB, extraC), page.getGroups());
    }

    @Test
    void optionsFlattenInGroupOrderAcrossEmptyGroups() {
        Option<?> a = option();
        Option<?> b = option();
        Option<?> c = option();
        Option<?> d = option();
        Option<?> e = option();
        OptionIdentifier<Void> id = pageId("flatten/page");
        extras(id, group("flatten/extra_empty"), group("flatten/extra", d, a), group("flatten/extra_tail_empty"));

        List<OptionGroup> own = List.of(group("flatten/leading_empty"), group("flatten/ab", a, b), group("flatten/middle_empty"), group("flatten/c", c), group("flatten/e", e), group("flatten/trailing_empty"));
        OptionPage page = new OptionPage(id, NAME, own);
        assertEquals(9, page.getGroups().size());
        // Checks an option in two groups.
        assertEquals(List.of(a, b, c, e, d, a), page.getOptions());

        // Without listener groups too
        OptionPage plain = new OptionPage(pageId("flatten/plain"), NAME, own);
        assertEquals(List.of(a, b, c, e), plain.getOptions());

        // A lone empty group gives an empty option list
        OptionPage hollow = new OptionPage(pageId("flatten/hollow"), NAME, List.of(group("flatten/only_empty")));
        assertTrue(hollow.getOptions().isEmpty());

        // Checks the last group holding options.
        OptionPage tail = new OptionPage(pageId("flatten/tail"), NAME, List.of(group("flatten/tail_empty_a"), group("flatten/tail_empty_b"), group("flatten/tail_ce", c, e)));
        assertEquals(List.of(c, e), tail.getOptions());
    }

    @Test
    void aGroupListedMoreThanOnceIsKeptEveryTime() {
        Option<?> a = option();
        Option<?> b = option();
        OptionGroup twice = group("twice/group", a, b);
        OptionIdentifier<Void> id = pageId("twice/page");
        extras(id, twice);

        OptionPage page = new OptionPage(id, NAME, List.of(twice, twice));
        assertEquals(List.of(twice, twice, twice), page.getGroups());
        assertEquals(List.of(a, b, a, b, a, b), page.getOptions());

        // Checks a group given twice.
        OptionPage plain = new OptionPage(pageId("twice/plain"), NAME, List.of(twice, twice));
        assertEquals(List.of(a, b, a, b), plain.getOptions());
    }

    @Test
    void optionTotalsPastWhatAListCanHoldAreRefusedBeforeAnyCopy() {
        // Builds a huge group.
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(OptionIdentifier.create(MOD, "huge/wide"));
        Option<?> one = option();
        for (int i = 0; i < 1 << 16; i++) {
            builder.add(one);
        }
        OptionGroup wide = builder.build();

        // Checks exactly 2^31 options.
        IllegalArgumentException exact = assertThrows(IllegalArgumentException.class, () -> new OptionPage(pageId("huge/exact"), NAME, Collections.nCopies(1 << 15, wide)));
        assertEquals("Page option_page_test:huge/exact holds 2147483648 options, more than a list can", exact.getMessage());

        // Checks a wrapped sum.
        IllegalArgumentException wrapped = assertThrows(IllegalArgumentException.class, () -> new OptionPage(pageId("huge/wrapped"), NAME, Collections.nCopies((1 << 16) + 1, wide)));
        assertEquals("Page option_page_test:huge/wrapped holds 4295032832 options, more than a list can", wrapped.getMessage());

        // Checks a listener group tips the count.
        OptionIdentifier<Void> tipped = pageId("huge/tipped");
        extras(tipped, wide);
        IllegalArgumentException tippedOver = assertThrows(IllegalArgumentException.class, () -> new OptionPage(tipped, NAME, Collections.nCopies((1 << 15) - 1, wide)));
        assertEquals("Page option_page_test:huge/tipped holds 2147483648 options, more than a list can", tippedOver.getMessage());
    }

    @Test
    void unmodifiableInputsAreKeptWithoutACopy() {
        OptionGroup one = group("reuse/one", option(), option());
        OptionGroup two = group("reuse/two", option());

        // Checks frozen lists are kept.
        List<OptionGroup> frozen = List.of(one, two);
        assertSame(frozen, new OptionPage(pageId("reuse/frozen"), NAME, frozen).getGroups());
        List<OptionGroup> copied = List.copyOf(new ArrayList<>(frozen));
        assertSame(copied, new OptionPage(pageId("reuse/copied"), NAME, copied).getGroups());
        List<OptionGroup> none = List.of();
        assertSame(none, new OptionPage(pageId("reuse/none"), NAME, none).getGroups());

        // A lone group's options are the group's own unmodifiable list
        OptionPage single = new OptionPage(pageId("reuse/single"), NAME, List.of(one));
        assertSame(one.getOptions(), single.getOptions());

        // Checks a fresh list for listener groups.
        OptionIdentifier<Void> extended = pageId("reuse/extended");
        OptionGroup extra = group("reuse/extra", option());
        extras(extended, extra);
        OptionPage withExtras = new OptionPage(extended, NAME, frozen);
        assertNotSame(frozen, withExtras.getGroups());
        assertEquals(List.of(one, two, extra), withExtras.getGroups());
        assertEquals(List.of(one, two), frozen);
    }

    @Test
    void thePageIsFinalAndHoldsOnlyItsFourFinalFields() {
        assertTrue(Modifier.isFinal(OptionPage.class.getModifiers()));
        // Static and synthetic fields (JaCoCo's probe array) are not page state
        List<Field> fields = Arrays.stream(OptionPage.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .toList();
        assertEquals(Set.of("id", "name", "groups", "options"), fields.stream().map(Field::getName).collect(Collectors.toSet()));
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field::toString);
        }
    }
}
