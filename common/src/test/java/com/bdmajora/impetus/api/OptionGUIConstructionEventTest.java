package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.eventbus.BusListeners;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionGUIConstructionEventTest {
    private static OptionPage page(String name) {
        return Mockito.mock(OptionPage.class, name);
    }

    // A list that fails on any call, so the event reading, sizing or copying it fails the test
    @SuppressWarnings("unchecked")
    private static List<OptionPage> untouchable() {
        return (List<OptionPage>) Proxy.newProxyInstance(List.class.getClassLoader(), new Class<?>[]{List.class}, (proxy, method, args) -> {
            throw new AssertionError("Event called " + method.getName() + " on the list");
        });
    }

    // A list that only takes a one-argument add and records it, so a size, scan or indexed insert fails the test
    @SuppressWarnings("unchecked")
    private static List<OptionPage> addOnly(List<Object> added) {
        return (List<OptionPage>) Proxy.newProxyInstance(List.class.getClassLoader(), new Class<?>[]{List.class}, (proxy, method, args) -> {
            if (method.getName().equals("add") && args != null && args.length == 1) {
                added.add(args[0]);
                return true;
            }
            throw new AssertionError("Event called " + method.getName() + " on the list");
        });
    }

    // Posts the event with listeners that only act on it, so other tests' posts pass through them untouched
    @SafeVarargs
    private static boolean post(OptionGUIConstructionEvent event, EventHandlerRegistrar.Handler<OptionGUIConstructionEvent>... handlers) {
        List<EventHandlerRegistrar.Handler<OptionGUIConstructionEvent>> filtered = new ArrayList<>();
        for (EventHandlerRegistrar.Handler<OptionGUIConstructionEvent> handler : handlers) {
            EventHandlerRegistrar.Handler<OptionGUIConstructionEvent> only = seen -> {
                if (seen == event) {
                    handler.acceptEvent(seen);
                }
            };
            filtered.add(only);
            OptionGUIConstructionEvent.BUS.addListener(only);
        }
        try {
            return OptionGUIConstructionEvent.BUS.post(event);
        } finally {
            for (EventHandlerRegistrar.Handler<OptionGUIConstructionEvent> only : filtered) {
                assertTrue(BusListeners.remove(OptionGUIConstructionEvent.BUS, only));
            }
        }
    }

    @Test
    void theEventHandsBackTheCallersOwnList() {
        List<OptionPage> pages = new ArrayList<>(List.of(page("own")));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        assertSame(pages, event.getPages());
        // Repeated reads give the same reference, never a fresh copy
        assertSame(event.getPages(), event.getPages());
    }

    @Test
    void aNullPageListIsRefusedAtConstruction() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> new OptionGUIConstructionEvent(null));
        assertEquals("Pages must not be null", thrown.getMessage());
    }

    @Test
    void constructionAndReadsNeverTouchTheList() {
        // O(1) whatever the list holds: the event never sizes, iterates or copies it
        List<OptionPage> pages = untouchable();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        assertSame(pages, event.getPages());
    }

    @Test
    void anEmptyListIsKeptAsIs() {
        List<OptionPage> empty = List.of();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(empty);
        assertSame(empty, event.getPages());
        assertTrue(event.getPages().isEmpty());
    }

    @Test
    void addPageAppendsAfterTheCallersPagesInCallOrder() {
        OptionPage own = page("own"), first = page("first"), second = page("second");
        List<OptionPage> pages = new ArrayList<>(List.of(own));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        event.addPage(first);
        event.addPage(second);
        assertEquals(List.of(own, first, second), pages);
        assertSame(pages, event.getPages());
    }

    @Test
    void addPageIsOneAppendAndNothingElse() {
        // A single add per page with no size, scan or duplicate check keeps addPage at the list's own add cost
        OptionPage a = page("a"), b = page("b");
        List<Object> added = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(addOnly(added));
        event.addPage(a);
        event.addPage(b);
        assertEquals(List.of(a, b), added);
    }

    @Test
    void theSamePageAddedTwiceIsKeptTwice() {
        OptionPage page = page("twice");
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        event.addPage(page);
        event.addPage(page);
        assertEquals(List.of(page, page), pages);
    }

    @Test
    void aNullPageIsRefusedWithoutTouchingTheList() {
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(untouchable());
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> event.addPage(null));
        assertEquals("Page must not be null", thrown.getMessage());
    }

    @Test
    void aNullPageLeavesTheListAsItWas() {
        OptionPage own = page("own");
        List<OptionPage> pages = new ArrayList<>(List.of(own));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        assertThrows(NullPointerException.class, () -> event.addPage(null));
        assertEquals(List.of(own), pages);
        // The event is still usable after the refusal
        OptionPage later = page("later");
        event.addPage(later);
        assertEquals(List.of(own, later), pages);
    }

    @Test
    void aNullPageIsRefusedBeforeAnUnmodifiableListCanObject() {
        List<OptionPage> fixed = List.of(page("fixed"));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(fixed);
        assertEquals("Page must not be null", assertThrows(NullPointerException.class, () -> event.addPage(null)).getMessage());
    }

    @Test
    void anUnmodifiableListIsNotWrappedSoAddPageThrows() {
        OptionPage own = page("own");
        List<OptionPage> fixed = Collections.unmodifiableList(new ArrayList<>(List.of(own)));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(fixed);
        assertSame(fixed, event.getPages());
        assertThrows(UnsupportedOperationException.class, () -> event.addPage(page("rejected")));
        assertEquals(List.of(own), fixed);
    }

    @Test
    void theListenerSeesThePostedEventAndTheCallersList() {
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        AtomicReference<OptionGUIConstructionEvent> seen = new AtomicReference<>();
        assertFalse(post(event, seen::set));
        assertSame(event, seen.get());
        assertSame(pages, seen.get().getPages());
    }

    @Test
    void aListenerCanPutAPageAnywhereThroughTheList() {
        OptionPage own = page("own"), first = page("first"), last = page("last");
        List<OptionPage> pages = new ArrayList<>(List.of(own));
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        assertFalse(post(event, seen -> {
            seen.getPages().add(0, first);
            seen.addPage(last);
        }));
        assertEquals(List.of(first, own, last), pages);
    }

    @Test
    void listenersAddInTheOrderTheyWereRegistered() {
        OptionPage a = page("a"), b = page("b");
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        post(event, seen -> seen.addPage(a), seen -> seen.addPage(b));
        assertEquals(List.of(a, b), pages);
    }

    @Test
    void aListenerAddingANullPageFailsThePostButLaterListenersStillAdd() {
        OptionPage later = page("later");
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> post(event, seen -> seen.addPage(null), seen -> seen.addPage(later)));
        assertEquals("Page must not be null", thrown.getMessage());
        assertEquals(List.of(later), pages);
    }

    @Test
    void aNullAddedThroughTheListItselfIsNotFiltered() {
        // getPages hands out the caller's list, so only addPage can refuse a null
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        post(event, seen -> seen.getPages().add(null));
        assertEquals(Collections.singletonList(null), pages);
    }

    @Test
    void theEventIsNotCancelable() {
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(new ArrayList<>());
        assertFalse(event.isCancelable());
        assertFalse(event.isCanceled());
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class, () -> event.setCanceled(true));
        assertEquals("Event " + OptionGUIConstructionEvent.class.getName() + " is not cancelable", thrown.getMessage());
        assertFalse(event.isCanceled());
        event.setCanceled(false);
        assertFalse(event.isCanceled());
    }

    @Test
    void aListenerTryingToCancelFailsThePostAndTheEventStaysUncanceled() {
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(new ArrayList<>());
        assertThrows(UnsupportedOperationException.class, () -> post(event, seen -> seen.setCanceled(true)));
        assertFalse(event.isCanceled());
    }

    @Test
    void theSameEventCanBePostedTwiceAndKeepsTheFirstPostsAdditions() {
        OptionPage page = page("repost");
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        post(event, seen -> seen.addPage(page));
        post(event, seen -> seen.addPage(page));
        assertEquals(List.of(page, page), pages);
    }

    @Test
    void theBusIsOneSharedConstant() throws ReflectiveOperationException {
        Field bus = OptionGUIConstructionEvent.class.getDeclaredField("BUS");
        int modifiers = bus.getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertNotNull(OptionGUIConstructionEvent.BUS);
        assertSame(OptionGUIConstructionEvent.BUS, bus.get(null));
    }

    @Test
    void theEventIsFinalAndHoldsOnlyItsFinalPageList() {
        assertTrue(Modifier.isFinal(OptionGUIConstructionEvent.class.getModifiers()));
        assertSame(ImpetusEvent.class, OptionGUIConstructionEvent.class.getSuperclass());
        // Static and synthetic fields (JaCoCo's probe array) are not event state
        List<Field> fields = Arrays.stream(OptionGUIConstructionEvent.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .toList();
        assertEquals(Set.of("pages"), fields.stream().map(Field::getName).collect(Collectors.toSet()));
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field::toString);
        }
    }
}
