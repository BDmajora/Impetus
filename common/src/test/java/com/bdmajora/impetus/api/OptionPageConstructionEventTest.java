package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.eventbus.BusListeners;
import com.bdmajora.impetus.api.eventbus.EventHandlerRegistrar;
import com.bdmajora.impetus.api.eventbus.ImpetusEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionPageConstructionEventTest {
    // Ids are interned for the whole JVM, so this class keeps to its own mod id
    private static final OptionIdentifier<Void> PAGE = OptionIdentifier.create("option_page_construction_event_test", "page");
    private static final OptionIdentifier<Void> OTHER = OptionIdentifier.create("option_page_construction_event_test", "other");
    private static final TextComponent NAME = TextComponent.literal("Page");

    private static OptionGroup group(String name) {
        return Mockito.mock(OptionGroup.class, name);
    }

    private static OptionPageConstructionEvent event() {
        return new OptionPageConstructionEvent(PAGE, NAME);
    }

    private static OptionPageConstructionEvent event(OptionGroup... groups) {
        OptionPageConstructionEvent event = event();
        for (OptionGroup group : groups) {
            event.addGroup(group);
        }
        return event;
    }

    // Posts the event with listeners that only act on it, so other tests' posts pass through them untouched
    @SafeVarargs
    private static boolean post(OptionPageConstructionEvent event, EventHandlerRegistrar.Handler<OptionPageConstructionEvent>... handlers) {
        List<EventHandlerRegistrar.Handler<OptionPageConstructionEvent>> filtered = new ArrayList<>();
        for (EventHandlerRegistrar.Handler<OptionPageConstructionEvent> handler : handlers) {
            EventHandlerRegistrar.Handler<OptionPageConstructionEvent> only = seen -> {
                if (seen == event) {
                    handler.acceptEvent(seen);
                }
            };
            filtered.add(only);
            OptionPageConstructionEvent.BUS.addListener(only);
        }
        try {
            return OptionPageConstructionEvent.BUS.post(event);
        } finally {
            for (EventHandlerRegistrar.Handler<OptionPageConstructionEvent> only : filtered) {
                assertTrue(BusListeners.remove(OptionPageConstructionEvent.BUS, only));
            }
        }
    }

    // Runs a change that may or may not be refused, since an unmodifiable list may let a change that changes nothing through
    private static void attempt(Executable change) {
        try {
            change.execute();
        } catch (UnsupportedOperationException refused) {
            // Refusing is as good as doing nothing
        } catch (Throwable other) {
            throw new AssertionError("Unexpected " + other, other);
        }
    }

    private static Object read(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(owner.getClass().getName() + " has no field " + name, e);
        }
    }

    // The array the event's groups live in
    private static OptionGroup[] items(OptionPageConstructionEvent event) {
        return (OptionGroup[]) read(read(event, "groups"), "items");
    }

    @Test
    void theEventHandsBackItsIdAndNameAsGiven() {
        TextComponent name = TextComponent.literal("Own");
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(PAGE, name);
        assertSame(PAGE, event.getId());
        assertSame(name, event.getTranslationKey());
        // Repeated reads give the same references
        assertSame(event.getId(), event.getId());
        assertSame(event.getTranslationKey(), event.getTranslationKey());
    }

    @Test
    void aNullIdIsRefusedAtConstructionBeforeTheName() {
        assertEquals("Page id must not be null", assertThrows(NullPointerException.class, () -> new OptionPageConstructionEvent(null, NAME)).getMessage());
        assertEquals("Page id must not be null", assertThrows(NullPointerException.class, () -> new OptionPageConstructionEvent(null, null)).getMessage());
    }

    @Test
    void aNullNameIsRefusedAtConstruction() {
        assertEquals("Page name must not be null", assertThrows(NullPointerException.class, () -> new OptionPageConstructionEvent(PAGE, null)).getMessage());
    }

    @Test
    void constructionPostsNothing() {
        TextComponent name = TextComponent.literal("Unposted");
        AtomicInteger seen = new AtomicInteger();
        EventHandlerRegistrar.Handler<OptionPageConstructionEvent> counter = e -> {
            if (e.getTranslationKey() == name) {
                seen.incrementAndGet();
            }
        };
        OptionPageConstructionEvent.BUS.addListener(counter);
        try {
            OptionPageConstructionEvent event = new OptionPageConstructionEvent(PAGE, name);
            event.addGroup(group("unposted"));
            assertEquals(0, seen.get());
            // The listener does see the event once it is posted, so the zero above means something
            OptionPageConstructionEvent.BUS.post(event);
            assertEquals(1, seen.get());
        } finally {
            assertTrue(BusListeners.remove(OptionPageConstructionEvent.BUS, counter));
        }
    }

    @Test
    void aFreshEventHasNoGroupsAndHandsOutOneViewOnEveryRead() {
        OptionPageConstructionEvent event = event();
        List<OptionGroup> groups = event.getAdditionalGroups();
        assertTrue(groups.isEmpty());
        assertEquals(0, groups.size());
        assertEquals(List.of(), groups);
        // No read allocates a fresh wrapper, before or after groups are added
        assertSame(groups, event.getAdditionalGroups());
        event.addGroup(group("one"));
        assertSame(groups, event.getAdditionalGroups());
    }

    @Test
    void theViewIsLiveSoAReadBeforeTheAddsSeesThem() {
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event();
        List<OptionGroup> early = event.getAdditionalGroups();
        event.addGroup(a);
        assertEquals(List.of(a), early);
        event.addGroup(b);
        assertEquals(List.of(a, b), early);
        assertEquals(2, early.size());
    }

    @Test
    void addGroupAppendsInCallOrderAndKeepsRepeats() {
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event(a, b, a, a);
        assertEquals(List.of(a, b, a, a), event.getAdditionalGroups());
        assertSame(a, event.getAdditionalGroups().get(0));
        assertSame(b, event.getAdditionalGroups().get(1));
        assertSame(a, event.getAdditionalGroups().get(3));
    }

    @Test
    void addingAndReadingNeverAsksTheGroupAnything() {
        // No equals, hashCode or getter is called, so an add stays O(1) whatever the groups hold
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event(a, b, a);
        assertSame(a, event.getAdditionalGroups().get(0));
        assertSame(b, event.getAdditionalGroups().get(1));
        assertEquals(3, event.getAdditionalGroups().size());
        Mockito.verifyNoInteractions(a, b);
    }

    @Test
    void aNullGroupIsRefusedNamingThePageAndChangesNothing() {
        OptionGroup own = group("own"), later = group("later");
        OptionPageConstructionEvent event = event(own);
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> event.addGroup(null));
        assertEquals("A listener added a null group to page option_page_construction_event_test:page", thrown.getMessage());
        assertEquals(List.of(own), event.getAdditionalGroups());
        // The event is still usable after the refusal
        event.addGroup(later);
        assertEquals(List.of(own, later), event.getAdditionalGroups());
        // A first add that is null leaves a fresh event empty too
        OptionPageConstructionEvent fresh = new OptionPageConstructionEvent(OTHER, NAME);
        assertEquals("A listener added a null group to page option_page_construction_event_test:other", assertThrows(NullPointerException.class, () -> fresh.addGroup(null)).getMessage());
        assertTrue(fresh.getAdditionalGroups().isEmpty());
    }

    @Test
    void readsOutsideTheGroupsAreRefused() {
        List<OptionGroup> empty = event().getAdditionalGroups();
        for (int index : new int[]{-1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> empty.get(index), () -> "empty, index " + index);
        }
        List<OptionGroup> two = event(group("a"), group("b")).getAdditionalGroups();
        for (int index : new int[]{-1, 2, 3, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> two.get(index), () -> "two, index " + index);
        }
        // A spare slot in the backing array is not readable either
        OptionPageConstructionEvent three = event(group("x"), group("y"));
        assertTrue(items(three).length > 2);
        assertThrows(IndexOutOfBoundsException.class, () -> three.getAdditionalGroups().get(2));
    }

    @Test
    void theViewReadsLikeAnyRandomAccessList() {
        OptionGroup a = group("a"), b = group("b"), absent = group("absent");
        List<OptionGroup> view = event(a, b, a).getAdditionalGroups();
        assertInstanceOf(RandomAccess.class, view);
        assertEquals(List.of(a, b, a), view);
        assertEquals(view, List.of(a, b, a));
        assertNotEquals(List.of(a, b), view);
        assertEquals(List.of(a, b, a).hashCode(), view.hashCode());
        assertEquals("[a, b, a]", view.toString());
        assertTrue(view.contains(b));
        assertFalse(view.contains(absent));
        assertFalse(view.contains(null));
        assertEquals(0, view.indexOf(a));
        assertEquals(2, view.lastIndexOf(a));
        assertEquals(-1, view.indexOf(absent));
        assertEquals(List.of(b, a), view.subList(1, 3));
        assertEquals(List.of(a, b, a), view.stream().toList());
        assertEquals(List.of(a, b, a), view.reversed().reversed());
        assertEquals(List.of(a, b, a), List.copyOf(view));
        List<OptionGroup> walked = new ArrayList<>();
        for (Iterator<OptionGroup> it = view.iterator(); it.hasNext(); ) {
            walked.add(it.next());
        }
        assertEquals(List.of(a, b, a), walked);
        // A copied array is the caller's own
        Object[] copy = view.toArray();
        copy[0] = absent;
        assertSame(a, view.get(0));
        OptionGroup[] typed = view.toArray(new OptionGroup[0]);
        assertArrayEquals(new OptionGroup[]{a, b, a}, typed);
    }

    @Test
    void everyChangeThroughTheViewIsRefused() {
        OptionGroup a = group("a"), b = group("b"), stranger = group("stranger");
        List<OptionGroup> view = event(a, b).getAdditionalGroups();
        List<Executable> changes = List.of(
                () -> view.add(stranger),
                () -> view.add(0, stranger),
                () -> view.add(2, stranger),
                () -> view.set(0, stranger),
                () -> view.remove(0),
                () -> view.remove(a),
                () -> view.addAll(List.of(stranger)),
                () -> view.addAll(0, List.of(stranger)),
                () -> view.removeAll(List.of(a)),
                () -> view.retainAll(List.of()),
                () -> view.removeIf(g -> true),
                () -> view.replaceAll(g -> stranger),
                () -> view.sort((x, y) -> 0),
                () -> view.clear(),
                () -> view.addFirst(stranger),
                () -> view.addLast(stranger),
                () -> view.removeFirst(),
                () -> view.removeLast(),
                () -> {
                    Iterator<OptionGroup> it = view.iterator();
                    it.next();
                    it.remove();
                },
                () -> {
                    ListIterator<OptionGroup> it = view.listIterator();
                    it.next();
                    it.set(stranger);
                },
                () -> view.listIterator().add(stranger),
                () -> view.subList(0, 1).set(0, stranger),
                () -> view.subList(0, 1).clear(),
                () -> view.reversed().add(stranger));
        for (int i = 0; i < changes.size(); i++) {
            assertThrows(UnsupportedOperationException.class, changes.get(i), "change " + i);
            assertEquals(List.of(a, b), view, "change " + i);
        }
    }

    @Test
    void aChangeThatWouldChangeNothingLeavesTheViewAsItWas() {
        OptionGroup a = group("a"), absent = group("absent");
        List<OptionGroup> empty = event().getAdditionalGroups();
        List<OptionGroup> one = event(a).getAdditionalGroups();
        for (List<OptionGroup> view : List.of(empty, one)) {
            attempt(() -> view.addAll(List.of()));
            attempt(() -> view.remove(absent));
            attempt(() -> view.removeAll(List.of(absent)));
            attempt(() -> view.retainAll(List.copyOf(view)));
            attempt(() -> view.removeIf(g -> false));
            attempt(() -> view.subList(0, 0).clear());
        }
        attempt(empty::clear);
        attempt(() -> empty.sort(null));
        attempt(() -> empty.replaceAll(g -> absent));
        assertEquals(List.of(), empty);
        assertEquals(List.of(a), one);
    }

    @Test
    void aViewOpenWhileAGroupIsAddedFailsFast() {
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event(a);
        List<OptionGroup> view = event.getAdditionalGroups();

        Iterator<OptionGroup> it = view.iterator();
        assertSame(a, it.next());
        event.addGroup(b);
        assertThrows(ConcurrentModificationException.class, it::next);

        List<OptionGroup> head = view.subList(0, 1);
        event.addGroup(b);
        assertThrows(ConcurrentModificationException.class, head::size);

        // A listener that adds a group for every group it walks stops instead of looping forever
        OptionPageConstructionEvent walked = event(a);
        assertThrows(ConcurrentModificationException.class, () -> walked.getAdditionalGroups().forEach(g -> walked.addGroup(b)));
        assertEquals(List.of(a, b), walked.getAdditionalGroups());
    }

    @Test
    void theBackingArrayGrowsGeometricallySoAnAppendIsAmortisedConstant() throws ReflectiveOperationException {
        OptionPageConstructionEvent event = event();
        Object store = read(event, "groups");
        Field itemsField = store.getClass().getDeclaredField("items");
        itemsField.setAccessible(true);
        int resizes = 0;
        OptionGroup[] last = (OptionGroup[]) itemsField.get(store);
        int count = 1 << 17;
        OptionGroup[] added = new OptionGroup[count];
        // One mock repeated, since 2^17 mocks are slow to make and the store never looks at its groups
        OptionGroup shared = group("shared");
        for (int i = 0; i < count; i++) {
            event.addGroup(shared);
            added[i] = shared;
            OptionGroup[] now = (OptionGroup[]) itemsField.get(store);
            if (now != last) {
                resizes++;
                // Each resize at least doubles the room, and never leaves more than one spare slot per group
                assertTrue(now.length >= 2 * last.length, "grew from " + last.length + " to " + now.length);
                assertTrue(now.length <= 2 * (i + 1), "grew to " + now.length + " for " + (i + 1));
                last = now;
            }
        }
        // 2^17 appends take at most 18 resizes, so the copies add up to O(n)
        assertTrue(resizes <= 18, "resizes " + resizes);
        assertEquals(count, event.getAdditionalGroups().size());
        assertEquals(Arrays.asList(added), event.getAdditionalGroups());
    }

    @Test
    void smallPagesKeepSmallArrays() {
        // Most pages get one or two groups, so the array is sized for that rather than for ten
        assertEquals(1, items(event(group("a"))).length);
        assertTrue(items(event(group("a"), group("b"))).length <= 3);
        assertTrue(items(event(group("a"), group("b"), group("c"), group("d"))).length <= 7);
    }

    @Test
    void eventsWithoutGroupsShareOneEmptyArrayThatIsNeverWritten() {
        OptionPageConstructionEvent first = event(), second = event();
        OptionGroup[] shared = items(first);
        assertEquals(0, shared.length);
        assertSame(shared, items(second));
        // Reading allocates no array either
        first.getAdditionalGroups().size();
        assertSame(shared, items(first));

        OptionGroup a = group("a");
        first.addGroup(a);
        assertNotSame(shared, items(first));
        assertEquals(List.of(a), first.getAdditionalGroups());
        assertTrue(second.getAdditionalGroups().isEmpty());
        assertSame(shared, items(second));
        assertTrue(event().getAdditionalGroups().isEmpty());
    }

    @Test
    void eachEventKeepsItsOwnGroups() {
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent first = event(a);
        OptionPageConstructionEvent second = event(b);
        assertEquals(List.of(a), first.getAdditionalGroups());
        assertEquals(List.of(b), second.getAdditionalGroups());
        assertNotSame(first.getAdditionalGroups(), second.getAdditionalGroups());
    }

    @Test
    void theListenerSeesThePostedEventWithItsIdAndName() {
        TextComponent name = TextComponent.literal("Seen");
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(OTHER, name);
        AtomicReference<OptionPageConstructionEvent> seen = new AtomicReference<>();
        assertFalse(post(event, seen::set));
        assertSame(event, seen.get());
        assertSame(OTHER, seen.get().getId());
        assertSame(name, seen.get().getTranslationKey());
    }

    @Test
    void listenersAddInTheOrderTheyWereRegisteredAfterGroupsAddedBeforeThePost() {
        OptionGroup before = group("before"), a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event(before);
        List<OptionGroup> view = event.getAdditionalGroups();
        assertFalse(post(event, seen -> seen.addGroup(a), seen -> seen.addGroup(b)));
        assertEquals(List.of(before, a, b), view);
    }

    @Test
    void aListenerCanReadWhatEarlierListenersAdded() {
        OptionGroup a = group("a"), b = group("b");
        OptionPageConstructionEvent event = event();
        List<List<OptionGroup>> snapshots = new ArrayList<>();
        post(event, seen -> seen.addGroup(a), seen -> {
            snapshots.add(List.copyOf(seen.getAdditionalGroups()));
            seen.addGroup(b);
        });
        assertEquals(List.of(List.of(a)), snapshots);
        assertEquals(List.of(a, b), event.getAdditionalGroups());
    }

    @Test
    void aListenerAddingANullGroupFailsThePostButLaterListenersStillAdd() {
        OptionGroup earlier = group("earlier"), later = group("later");
        OptionPageConstructionEvent event = event();
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> post(event, seen -> {
            seen.addGroup(earlier);
            seen.addGroup(null);
        }, seen -> seen.addGroup(later)));
        assertEquals("A listener added a null group to page option_page_construction_event_test:page", thrown.getMessage());
        assertEquals(List.of(earlier, later), event.getAdditionalGroups());
        assertFalse(event.getAdditionalGroups().contains(null));
    }

    @Test
    void aListenerThatThrowsKeepsWhatItAddedBefore() {
        OptionGroup before = group("before"), after = group("after");
        IllegalStateException boom = new IllegalStateException("boom");
        OptionPageConstructionEvent event = event();
        assertSame(boom, assertThrows(IllegalStateException.class, () -> post(event, seen -> {
            seen.addGroup(before);
            throw boom;
        }, seen -> seen.addGroup(after))));
        assertEquals(List.of(before, after), event.getAdditionalGroups());
    }

    @Test
    void theEventIsNotCancelable() {
        OptionPageConstructionEvent event = event();
        assertFalse(event.isCancelable());
        assertFalse(event.isCanceled());
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class, () -> event.setCanceled(true));
        assertEquals("Event " + OptionPageConstructionEvent.class.getName() + " is not cancelable", thrown.getMessage());
        assertFalse(event.isCanceled());
        event.setCanceled(false);
        assertFalse(event.isCanceled());
    }

    @Test
    void aListenerTryingToCancelFailsThePostAndTheEventStaysUncanceled() {
        OptionGroup later = group("later");
        OptionPageConstructionEvent event = event();
        assertThrows(UnsupportedOperationException.class, () -> post(event, seen -> seen.setCanceled(true), seen -> seen.addGroup(later)));
        assertFalse(event.isCanceled());
        assertEquals(List.of(later), event.getAdditionalGroups());
    }

    @Test
    void theSameEventCanBePostedTwiceAndKeepsBothPostsAdditions() {
        OptionGroup group = group("repost");
        OptionPageConstructionEvent event = event();
        post(event, seen -> seen.addGroup(group));
        post(event, seen -> seen.addGroup(group));
        assertEquals(List.of(group, group), event.getAdditionalGroups());
    }

    @Test
    void theBusIsOneSharedConstant() throws ReflectiveOperationException {
        Field bus = OptionPageConstructionEvent.class.getDeclaredField("BUS");
        int modifiers = bus.getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertNotNull(OptionPageConstructionEvent.BUS);
        assertSame(OptionPageConstructionEvent.BUS, bus.get(null));
    }

    @Test
    void theEventIsFinalAndHoldsOnlyItsIdNameAndGroups() {
        assertTrue(Modifier.isFinal(OptionPageConstructionEvent.class.getModifiers()));
        assertSame(ImpetusEvent.class, OptionPageConstructionEvent.class.getSuperclass());
        // Static and synthetic fields (JaCoCo's probe array) are not event state
        List<Field> fields = Arrays.stream(OptionPageConstructionEvent.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .toList();
        assertEquals(Set.of("id", "name", "groups"), fields.stream().map(Field::getName).collect(Collectors.toSet()));
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field::toString);
        }
    }

    @Test
    void theGroupsLiveInOnePrivateListHoldingOnlyAnArrayAndASize() {
        Class<?> store = read(event(), "groups").getClass();
        assertSame(OptionPageConstructionEvent.class, store.getEnclosingClass());
        int modifiers = store.getModifiers();
        assertTrue(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        assertSame(AbstractList.class, store.getSuperclass());
        assertTrue(RandomAccess.class.isAssignableFrom(store));
        Set<String> state = Arrays.stream(store.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("items", "size"), state);
    }
}
