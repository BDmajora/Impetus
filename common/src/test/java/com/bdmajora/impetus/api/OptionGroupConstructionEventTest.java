package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.eventbus.BusListeners;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import org.junit.jupiter.api.Test;

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

class OptionGroupConstructionEventTest {
    private static final String MOD = "option_group_event_test";

    private static OptionIdentifier<Void> id(String path) {
        return OptionIdentifier.create(MOD, path);
    }

    // A distinct option that answers only identity, so the event asking it anything fails the test
    private static Option<?> option(String label) {
        return (Option<?>) Proxy.newProxyInstance(Option.class.getClassLoader(), new Class<?>[]{Option.class}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> label;
            default -> throw new AssertionError("Event asked option " + label + " for " + method.getName());
        });
    }

    // A list that fails on any call, so the event reading, sizing or copying it fails the test
    @SuppressWarnings("unchecked")
    private static List<Option<?>> untouchable() {
        return (List<Option<?>>) Proxy.newProxyInstance(List.class.getClassLoader(), new Class<?>[]{List.class}, (proxy, method, args) -> {
            throw new AssertionError("Event called " + method.getName() + " on the list");
        });
    }

    // Posts the event with a listener that only acts on its group, so other tests' posts pass through it untouched
    private static boolean post(OptionGroupConstructionEvent event, EventHandlerRegistrar.Handler<OptionGroupConstructionEvent> handler) {
        OptionIdentifier<Void> id = event.getId();
        EventHandlerRegistrar.Handler<OptionGroupConstructionEvent> filtered = seen -> {
            if (seen.getId() == id) {
                handler.acceptEvent(seen);
            }
        };
        OptionGroupConstructionEvent.BUS.addListener(filtered);
        try {
            return OptionGroupConstructionEvent.BUS.post(event);
        } finally {
            assertTrue(BusListeners.remove(OptionGroupConstructionEvent.BUS, filtered));
        }
    }

    @Test
    void theEventHandsBackTheIdAndTheCallersOwnList() {
        OptionIdentifier<Void> id = id("identity");
        List<Option<?>> options = new ArrayList<>(List.of(option("a")));
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id, options);
        assertSame(id, event.getId());
        assertSame(options, event.getOptions());
        // Repeated reads give the same references, never a fresh copy
        assertSame(event.getId(), event.getId());
        assertSame(event.getOptions(), event.getOptions());
    }

    @Test
    void aNullIdIsRefusedAtConstruction() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> new OptionGroupConstructionEvent(null, new ArrayList<>()));
        assertEquals("Group id must not be null", thrown.getMessage());
    }

    @Test
    void aNullOptionListIsRefusedAtConstruction() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> new OptionGroupConstructionEvent(id("null_list"), null));
        assertEquals("Options must not be null", thrown.getMessage());
    }

    @Test
    void theIdIsCheckedBeforeTheListWhenBothAreNull() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> new OptionGroupConstructionEvent(null, null));
        assertEquals("Group id must not be null", thrown.getMessage());
    }

    @Test
    void constructionAndReadsNeverTouchTheList() {
        // O(1) whatever the list holds: the event never sizes, iterates or copies it
        List<Option<?>> options = untouchable();
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("untouched"), options);
        assertSame(options, event.getOptions());
        event.getId();
    }

    @Test
    void theEventNeverAsksAnOptionAnything() {
        Option<?> a = option("a"), b = option("b");
        List<Option<?>> options = new ArrayList<>(List.of(a, b));
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("options_untouched"), options);
        assertSame(options, event.getOptions());
        assertEquals(List.of(a, b), options);
    }

    @Test
    void anEmptyListIsKeptAsIs() {
        List<Option<?>> empty = List.of();
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("empty"), empty);
        assertSame(empty, event.getOptions());
        assertTrue(event.getOptions().isEmpty());
    }

    @Test
    void listenerAdditionsLandInTheCallersListAfterItsOptions() {
        Option<?> own = option("own"), added = option("added"), later = option("later");
        List<Option<?>> options = new ArrayList<>(List.of(own));
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("append"), options);
        assertFalse(post(event, seen -> {
            seen.getOptions().add(added);
            seen.getOptions().add(later);
        }));
        assertEquals(List.of(own, added, later), options);
    }

    @Test
    void theListenerSeesThePostedEventAndItsId() {
        OptionIdentifier<Void> id = id("seen");
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id, new ArrayList<>());
        AtomicReference<OptionGroupConstructionEvent> seen = new AtomicReference<>();
        post(event, seen::set);
        assertSame(event, seen.get());
        assertSame(id, seen.get().getId());
    }

    @Test
    void anUnmodifiableListIsNotWrappedSoAListenerAddThrowsOutOfThePost() {
        List<Option<?>> fixed = Collections.unmodifiableList(new ArrayList<>(List.of(option("fixed"))));
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("fixed"), fixed);
        assertSame(fixed, event.getOptions());
        assertThrows(UnsupportedOperationException.class, () -> post(event, seen -> seen.getOptions().add(option("rejected"))));
        assertEquals(1, fixed.size());
    }

    @Test
    void aListenerMayAddNullAndTheEventDoesNotFilterIt() {
        // Null additions are OptionGroup.build's to report, the event passes the list through untouched
        List<Option<?>> options = new ArrayList<>();
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("null_add"), options);
        post(event, seen -> seen.getOptions().add(null));
        assertEquals(Collections.singletonList(null), options);
    }

    @Test
    void theEventIsNotCancelable() {
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("cancel"), new ArrayList<>());
        assertFalse(event.isCancelable());
        assertFalse(event.isCanceled());
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class, () -> event.setCanceled(true));
        assertEquals("Event " + OptionGroupConstructionEvent.class.getName() + " is not cancelable", thrown.getMessage());
        assertFalse(event.isCanceled());
        event.setCanceled(false);
        assertFalse(event.isCanceled());
    }

    @Test
    void aListenerTryingToCancelFailsThePostAndTheEventStaysUncanceled() {
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("cancel_in_post"), new ArrayList<>());
        assertThrows(UnsupportedOperationException.class, () -> post(event, seen -> seen.setCanceled(true)));
        assertFalse(event.isCanceled());
    }

    @Test
    void theSameEventCanBePostedTwiceAndKeepsTheFirstPostsAdditions() {
        Option<?> a = option("a");
        List<Option<?>> options = new ArrayList<>();
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id("repost"), options);
        post(event, seen -> seen.getOptions().add(a));
        post(event, seen -> seen.getOptions().add(a));
        assertEquals(List.of(a, a), options);
    }

    @Test
    void theBusIsOneSharedConstant() throws ReflectiveOperationException {
        Field bus = OptionGroupConstructionEvent.class.getDeclaredField("BUS");
        int modifiers = bus.getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertNotNull(OptionGroupConstructionEvent.BUS);
        assertSame(OptionGroupConstructionEvent.BUS, bus.get(null));
    }

    @Test
    void theEventIsFinalAndHoldsOnlyItsTwoFinalFields() {
        assertTrue(Modifier.isFinal(OptionGroupConstructionEvent.class.getModifiers()));
        assertSame(ImpetusEvent.class, OptionGroupConstructionEvent.class.getSuperclass());
        // Static and synthetic fields (JaCoCo's probe array) are not event state
        List<Field> fields = Arrays.stream(OptionGroupConstructionEvent.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .toList();
        assertEquals(Set.of("id", "options"), fields.stream().map(Field::getName).collect(Collectors.toSet()));
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field::toString);
        }
    }
}
