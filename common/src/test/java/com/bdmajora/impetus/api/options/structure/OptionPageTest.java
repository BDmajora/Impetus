package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionPageTest {
    // Ids stay interned and listeners stay on the static bus for the whole JVM, so this class keeps to its own mod id and one page id per case
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

    // One listener per call, each appending its groups in order whenever the given page is built
    private static void extras(OptionIdentifier<Void> pageId, OptionGroup... groups) {
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId() == pageId) {
                for (OptionGroup group : groups) {
                    e.addGroup(group);
                }
            }
        });
    }

    // Every mutator throws, whether or not the list is empty, and the list is left as it was
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
    }

    @Test
    void callerMutatingItsListAfterConstructionDoesNotChangeThePage() {
        Option<?> a = option();
        Option<?> b = option();
        OptionGroup first = group("mutate/first", a);
        OptionGroup second = group("mutate/second", b);
        OptionGroup intruder = group("mutate/intruder", option());

        // An ArrayList, as the search results page passes
        List<OptionGroup> caller = new ArrayList<>(List.of(first, second));
        OptionPage page = new OptionPage(pageId("mutate/plain"), NAME, caller);
        caller.add(intruder);
        caller.set(0, intruder);
        caller.remove(second);
        assertEquals(List.of(first, second), page.getGroups());
        assertEquals(List.of(a, b), page.getOptions());
        caller.clear();
        assertEquals(List.of(first, second), page.getGroups());

        // An unmodifiable view still changes when its backing list does, so it is copied too
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

        OptionPage emptyGroups = new OptionPage(pageId("plain/empty_groups"), NAME, List.of(group("plain/hollow_a"), group("plain/hollow_b")));
        assertTrue(emptyGroups.getOptions().isEmpty());
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

        // A page whose only groups come from listeners
        OptionIdentifier<Void> onlyExtras = pageId("extended/only");
        OptionGroup lone = group("extended/lone", option());
        extras(onlyExtras, lone);
        OptionPage fromListener = new OptionPage(onlyExtras, NAME, List.of());
        assertEquals(List.of(lone), fromListener.getGroups());
        assertUnmodifiable(fromListener.getGroups(), group("extended/spare_two"));
        assertUnmodifiable(fromListener.getOptions(), option());
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

        // Listeners run again for every page built, so a second page gets the same groups after its own
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
        // An option in two groups is listed once per group, where each group puts it
        assertEquals(List.of(a, b, c, e, d, a), page.getOptions());

        // Without listener groups too
        OptionPage plain = new OptionPage(pageId("flatten/plain"), NAME, own);
        assertEquals(List.of(a, b, c, e), plain.getOptions());

        // A lone empty group gives an empty option list
        OptionPage hollow = new OptionPage(pageId("flatten/hollow"), NAME, List.of(group("flatten/only_empty")));
        assertTrue(hollow.getOptions().isEmpty());
    }

    @Test
    void unmodifiableInputsAreKeptWithoutACopy() {
        OptionGroup one = group("reuse/one", option(), option());
        OptionGroup two = group("reuse/two", option());

        // List.of and List.copyOf results, as ConfigPageBuilder.build passes, are already what the page needs
        List<OptionGroup> frozen = List.of(one, two);
        assertSame(frozen, new OptionPage(pageId("reuse/frozen"), NAME, frozen).getGroups());
        List<OptionGroup> copied = List.copyOf(new ArrayList<>(frozen));
        assertSame(copied, new OptionPage(pageId("reuse/copied"), NAME, copied).getGroups());

        // A lone group's options are the group's own unmodifiable list
        OptionPage single = new OptionPage(pageId("reuse/single"), NAME, List.of(one));
        assertSame(one.getOptions(), single.getOptions());

        // Listener groups need a fresh list, and the caller's frozen list is left alone
        OptionIdentifier<Void> extended = pageId("reuse/extended");
        OptionGroup extra = group("reuse/extra", option());
        extras(extended, extra);
        OptionPage withExtras = new OptionPage(extended, NAME, frozen);
        assertNotSame(frozen, withExtras.getGroups());
        assertEquals(List.of(one, two, extra), withExtras.getGroups());
        assertEquals(List.of(one, two), frozen);
    }

    @Test
    void nullGroupsAreRejected() {
        OptionGroup real = group("null/real", option());

        // From the caller's list, wherever it sits
        assertThrows(NullPointerException.class, () -> new OptionPage(pageId("null/first"), NAME, Arrays.asList(null, real)));
        assertThrows(NullPointerException.class, () -> new OptionPage(pageId("null/last"), NAME, Arrays.asList(real, null)));
        assertThrows(NullPointerException.class, () -> new OptionPage(pageId("null/only"), NAME, Collections.singletonList(null)));

        // From a listener that skips ConfigApi's own null check
        OptionIdentifier<Void> fromListener = pageId("null/listener");
        extras(fromListener, (OptionGroup) null);
        assertThrows(NullPointerException.class, () -> new OptionPage(fromListener, NAME, List.of(real)));
        assertThrows(NullPointerException.class, () -> new OptionPage(fromListener, NAME, List.of()));

        // A null list is no list of groups either
        assertThrows(NullPointerException.class, () -> new OptionPage(pageId("null/list"), NAME, null));
    }
}
