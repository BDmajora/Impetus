package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.binding.GenericBinding;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionImplTest {
    private static final String MOD = "option_impl_test";

    private static OptionStorage<AtomicInteger> storage(int value) {
        AtomicInteger data = new AtomicInteger(value);
        return () -> data;
    }

    @SuppressWarnings("unchecked")
    private static Control<Integer> control() {
        return Mockito.mock(Control.class);
    }

    private static OptionIdentifier<Integer> id(String path) {
        return OptionIdentifier.create(MOD, path, Integer.class);
    }

    // The lang key an id infers, as the draw expects it: one key and no arguments
    private static TextComponent key(String key) {
        return new TextComponent.Translatable(List.of(key), List.of());
    }

    // Throws any throwable from a place that declares none
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable failure) throws E {
        throw (E) failure;
    }

    private static OptionImpl.Builder<AtomicInteger, Integer> builder(OptionStorage<AtomicInteger> storage) {
        return OptionImpl.createBuilder(Integer.class, storage)
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> control());
    }

    private static OptionImpl.Builder<AtomicInteger, Integer> named(OptionStorage<AtomicInteger> storage) {
        return builder(storage).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t"));
    }

    // The value type an option reports when built for the given type
    @SuppressWarnings("unchecked")
    private static <T> Class<?> builtType(Class<T> type) {
        AtomicReference<T> data = new AtomicReference<>();
        OptionStorage<AtomicReference<T>> storage = () -> data;
        return OptionImpl.createBuilder(type, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding(AtomicReference::set, AtomicReference::get)
                .setControl(o -> Mockito.mock(Control.class))
                .build()
                .getValueType();
    }

    @Test
    void theOptionHoldsOnlyWhatItAnswersAndComesOnlyFromABuilder() {
        assertTrue(Modifier.isFinal(OptionImpl.class.getModifiers()));
        Set<String> fields = Arrays.stream(OptionImpl.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("storage", "binding", "control", "controlFailure", "flags", "id", "valueType", "name", "tooltip", "impact", "modifiedValue", "enabled"), fields);
        for (Constructor<?> constructor : OptionImpl.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()), constructor::toString);
        }
        // Each call is a builder of its own
        assertNotSame(OptionImpl.createBuilder(Integer.class, storage(0)), OptionImpl.createBuilder(Integer.class, storage(0)));
    }

    @Test
    void optionTracksPendingValueUntilApplied() {
        OptionStorage<AtomicInteger> storage = storage(1);
        OptionImpl<AtomicInteger, Integer> option = builder(storage)
                .setId(OptionIdentifier.create("t", "opt", Integer.class))
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
        assertEquals(1, option.getValue());
        assertFalse(option.hasChanged());
        option.setValue(1);
        assertFalse(option.hasChanged());
        option.setValue(2);
        assertTrue(option.hasChanged());
        assertEquals(2, option.getValue());
        option.reset();
        assertEquals(1, option.getValue());
        option.setValue(3);
        option.applyChanges();
        assertEquals(3, storage.getData().get());
        assertFalse(option.hasChanged());
        option.applyChanges();
        assertEquals(OptionImpact.HIGH, option.getImpact());
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), Set.copyOf(option.getFlags()));
        assertSame(storage, option.getStorage());
        assertNotNull(option.getControl());
        assertTrue(option.isAvailable());
        assertEquals("t.options.opt.name", option.getName().toString());
        assertEquals("t.options.opt.tooltip", option.getTooltip().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasChangedComparesThePendingValueAgainstANullStoredOne() {
        AtomicReference<String> data = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        OptionStorage<AtomicReference<String>> storage = () -> data;
        OptionImpl<AtomicReference<String>, String> option = OptionImpl.createBuilder(String.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding(AtomicReference::set, ref -> {
                    reads.incrementAndGet();
                    return ref.get();
                })
                .setControl(o -> Mockito.mock(Control.class))
                .build();
        // Nothing pending reads nothing, which keeps the per-frame check free
        assertFalse(option.hasChanged());
        assertEquals(0, reads.get());
        option.setValue("set");
        assertTrue(option.hasChanged());
        assertEquals(1, reads.get());
        option.applyChanges();
        assertEquals("set", data.get());
        assertFalse(option.hasChanged());
        // Equal but not the same instance is not a change
        option.setValue(new String("set"));
        assertFalse(option.hasChanged());
        // The stored value going back to null makes the pending one a change again
        data.set(null);
        assertTrue(option.hasChanged());
        option.reset();
        assertFalse(option.hasChanged());
        // Nothing pending over a null stored value answers that null
        assertNull(option.getValue());
    }

    @Test
    void getValueReadsTheStorageLiveUntilAValueIsPending() {
        AtomicInteger data = new AtomicInteger(1);
        AtomicInteger reads = new AtomicInteger();
        OptionStorage<AtomicInteger> storage = () -> data;
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding(AtomicInteger::set, d -> {
                    reads.incrementAndGet();
                    return d.get();
                })
                .setControl(o -> control())
                .build();
        // Building reads nothing
        assertEquals(0, reads.get());
        assertEquals(1, option.getValue());
        // A change made to the storage behind the option's back shows on the next read
        data.set(2);
        assertEquals(2, option.getValue());
        assertEquals(2, reads.get());
        // A pending value answers without reading the storage
        option.setValue(7);
        assertEquals(7, option.getValue());
        assertEquals(7, option.getValue());
        assertEquals(2, reads.get());
        // The storage catching up with the pending value is no change
        data.set(7);
        assertFalse(option.hasChanged());
        assertEquals(3, reads.get());
    }

    @Test
    void settingNullClearsThePendingValueAndWritesNothing() {
        AtomicInteger data = new AtomicInteger(3);
        AtomicInteger writes = new AtomicInteger();
        OptionStorage<AtomicInteger> storage = () -> data;
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding((d, v) -> {
                    writes.incrementAndGet();
                    d.set(v);
                }, AtomicInteger::get)
                .setControl(o -> control())
                .build();
        option.setValue(5);
        assertTrue(option.hasChanged());
        // The binding never receives null, so null stands for nothing pending
        option.setValue(null);
        assertFalse(option.hasChanged());
        assertEquals(3, option.getValue());
        option.applyChanges();
        assertEquals(0, writes.get());
        assertEquals(3, data.get());
    }

    @Test
    void applyWritesOnceAndOnlyWhenSomethingIsPending() {
        AtomicInteger data = new AtomicInteger(0);
        AtomicInteger gets = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        OptionStorage<AtomicInteger> storage = () -> {
            gets.incrementAndGet();
            return data;
        };
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding((d, v) -> {
                    writes.incrementAndGet();
                    d.set(v);
                }, AtomicInteger::get)
                .setControl(o -> control())
                .build();
        // Nothing pending touches neither the storage nor the binding
        option.applyChanges();
        option.reset();
        assertEquals(0, gets.get());
        assertEquals(0, writes.get());
        option.setValue(3);
        option.applyChanges();
        assertEquals(1, gets.get());
        assertEquals(1, writes.get());
        assertEquals(3, data.get());
        // Applied once, so applying again writes nothing
        option.applyChanges();
        assertEquals(1, writes.get());
        // An unchanged value that was set is still written, as Apply only asks options that changed
        option.setValue(3);
        option.applyChanges();
        assertEquals(2, writes.get());
    }

    @Test
    void aWriteThatFailsKeepsThePendingValueForTheNextApply() {
        AtomicInteger data = new AtomicInteger(0);
        AtomicBoolean refuse = new AtomicBoolean(true);
        AtomicBoolean unreadable = new AtomicBoolean(false);
        IllegalStateException refused = new IllegalStateException("read-only");
        IllegalStateException missing = new IllegalStateException("no data");
        OptionStorage<AtomicInteger> storage = () -> {
            if (unreadable.get()) {
                throw missing;
            }
            return data;
        };
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding((d, v) -> {
                    if (refuse.get()) {
                        throw refused;
                    }
                    d.set(v);
                }, AtomicInteger::get)
                .setControl(o -> control())
                .build();
        option.setValue(4);
        assertSame(refused, assertThrows(IllegalStateException.class, option::applyChanges));
        assertEquals(4, option.getValue());
        assertTrue(option.hasChanged());
        assertEquals(0, data.get());
        // A storage that cannot be reached keeps it too
        unreadable.set(true);
        assertSame(missing, assertThrows(IllegalStateException.class, option::applyChanges));
        unreadable.set(false);
        assertTrue(option.hasChanged());
        refuse.set(false);
        option.applyChanges();
        assertEquals(4, data.get());
        assertFalse(option.hasChanged());
    }

    @Test
    void aValueSetWhileTheBindingWritesStaysPending() {
        AtomicInteger data = new AtomicInteger(0);
        OptionStorage<AtomicInteger> storage = () -> data;
        AtomicReference<OptionImpl<AtomicInteger, Integer>> self = new AtomicReference<>();
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding((d, v) -> {
                    d.set(v);
                    // A binding that settles on another value hands it back as the next pending one
                    if (v == 1) {
                        self.get().setValue(2);
                    } else if (v == 5) {
                        self.get().reset();
                    }
                }, AtomicInteger::get)
                .setControl(o -> control())
                .build();
        self.set(option);
        option.setValue(1);
        option.applyChanges();
        assertEquals(1, data.get());
        assertEquals(2, option.getValue());
        assertTrue(option.hasChanged());
        option.applyChanges();
        assertEquals(2, data.get());
        assertFalse(option.hasChanged());
        // A reset while writing leaves nothing pending
        option.setValue(5);
        option.applyChanges();
        assertEquals(5, data.get());
        assertFalse(option.hasChanged());
    }

    @Test
    void theControlFactoryIsAskedOnceWithTheOptionAlreadyWhole() {
        OptionStorage<AtomicInteger> storage = storage(4);
        OptionIdentifier<Integer> id = id("whole");
        Control<Integer> made = control();
        List<OptionImpl<AtomicInteger, Integer>> asked = new ArrayList<>();
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                .setId(id)
                .setImpact(OptionImpact.LOW)
                .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                .setEnabled(false)
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> {
                    asked.add(o);
                    // A control may read anything about its option while it is made
                    assertSame(id, o.getId());
                    assertSame(Integer.class, o.getValueType());
                    assertEquals(key(MOD + ".options.whole.name"), o.getName());
                    assertEquals(key(MOD + ".options.whole.tooltip"), o.getTooltip());
                    assertSame(OptionImpact.LOW, o.getImpact());
                    assertSame(storage, o.getStorage());
                    assertEquals(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD), Set.copyOf(o.getFlags()));
                    assertFalse(o.isAvailable());
                    assertEquals(4, o.getValue());
                    assertFalse(o.hasChanged());
                    return made;
                })
                .build();
        assertEquals(1, asked.size());
        assertSame(option, asked.getFirst());
        // Made once and kept
        assertSame(made, option.getControl());
        assertSame(made, option.getControl());
        assertEquals(1, asked.size());
    }

    @Test
    void aValueTheFactorySetsIsDroppedSoANewOptionStartsUnchanged() {
        OptionImpl<AtomicInteger, Integer> option = named(storage(1)).setControl(o -> {
            o.setValue(9);
            return control();
        }).build();
        assertFalse(option.hasChanged());
        assertEquals(1, option.getValue());
    }

    @Test
    void aFactoryThatThrowsLeavesTheOptionStandingAndGetControlRethrowsIt() {
        List<Throwable> failures = List.of(new IllegalArgumentException("bad"), new NoClassDefFoundError("optional/Mod"),
                new ExceptionInInitializerError("static"), new AssertionError("assert"), new IOException("checked"));
        for (Throwable failure : failures) {
            OptionStorage<AtomicInteger> storage = storage(6);
            OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(Integer.class, storage)
                    .setId(id("failing"))
                    .setBinding(AtomicInteger::set, AtomicInteger::get)
                    .setControl(o -> {
                        throw sneaky(failure);
                    })
                    .build();
            IllegalStateException first = assertThrows(IllegalStateException.class, option::getControl);
            assertEquals("Control of option " + MOD + ":failing could not be made", first.getMessage());
            assertSame(failure, first.getCause());
            // Every ask fails the same way, on a fresh exception so no caller's suppressed list reaches another
            IllegalStateException second = assertThrows(IllegalStateException.class, option::getControl);
            assertNotSame(first, second);
            assertSame(failure, second.getCause());
            // The rest of the option still answers, so the page shows a placeholder row for it
            assertEquals(6, option.getValue());
            assertSame(storage, option.getStorage());
            assertTrue(option.isAvailable());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void aFailedControlOfAnOptionWithoutAnIdIsNamedByItsLabel() {
        IllegalStateException failure = new IllegalStateException("broken");
        OptionImpl<AtomicInteger, Integer> unnamed = OptionImpl.createBuilder(Integer.class, storage(0))
                .setName(TextComponent.literal("Shadows"))
                .setTooltip(TextComponent.literal("t"))
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> {
                    throw failure;
                })
                .build();
        assertSame(OptionIdentifier.EMPTY, unnamed.getId());
        IllegalStateException thrown = assertThrows(IllegalStateException.class, unnamed::getControl);
        assertEquals("Control of option Shadows could not be made", thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    @Test
    void aFactoryInterruptedLeavesTheThreadInterruptedAndTheOptionStanding() {
        InterruptedException interrupted = new InterruptedException("stop");
        try {
            OptionImpl<AtomicInteger, Integer> option = named(storage(0)).setControl(o -> {
                throw sneaky(interrupted);
            }).build();
            assertTrue(Thread.currentThread().isInterrupted());
            assertSame(interrupted, assertThrows(IllegalStateException.class, option::getControl).getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void aVirtualMachineErrorFromTheFactoryFailsTheBuild() {
        for (VirtualMachineError fatal : List.of(new StackOverflowError("deep"), new OutOfMemoryError("full"), new InternalError("jvm"))) {
            OptionImpl.Builder<AtomicInteger, Integer> builder = named(storage(0)).setControl(o -> {
                throw fatal;
            });
            assertSame(fatal, assertThrows(VirtualMachineError.class, builder::build));
        }
    }

    @Test
    void aFactoryThatAnswersNoControlLeavesTheRowToThePlaceholderCheck() {
        OptionImpl<AtomicInteger, Integer> option = named(storage(0)).setId(id("no_control")).setControl(o -> null).build();
        assertNull(option.getControl());
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> Control.createElementFor(option, new Dim2i(0, 0, 200, 20)));
        assertEquals("Option " + MOD + ":no_control has no control", thrown.getMessage());
    }

    @Test
    void builderInfersNothingWithoutAnIdAndValidatesRequiredParts() {
        OptionImpl<AtomicInteger, Integer> option = builder(storage(0))
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("tip"))
                .setEnabled(false)
                .build();
        assertSame(OptionIdentifier.EMPTY, option.getId());
        assertFalse(option.isAvailable());
        assertEquals("n", option.getName().toString());
        assertEquals("Name must be specified or inferred from a specified ID", assertThrows(NullPointerException.class, () -> builder(storage(0)).build()).getMessage());
        assertEquals("Tooltip must be specified or inferred from a specified ID", assertThrows(NullPointerException.class, () -> builder(storage(0)).setName(TextComponent.literal("n")).build()).getMessage());
        assertEquals("Option binding must be specified", assertThrows(NullPointerException.class, () -> OptionImpl.createBuilder(Integer.class, storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t")).build()).getMessage());
        assertEquals("Control must be specified", assertThrows(NullPointerException.class, () -> OptionImpl.createBuilder(Integer.class, storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t")).setBinding(AtomicInteger::set, AtomicInteger::get).build()).getMessage());
        OptionImpl.Builder<AtomicInteger, Integer> b = OptionImpl.createBuilder(Integer.class, storage(0));
        assertThrows(NullPointerException.class, () -> b.setId(null));
        assertThrows(NullPointerException.class, () -> b.setName(null));
        assertThrows(NullPointerException.class, () -> b.setTooltip(null));
        assertThrows(NullPointerException.class, () -> b.setBinding(null));
        assertThrows(NullPointerException.class, () -> b.setBinding(null, AtomicInteger::get));
        assertThrows(NullPointerException.class, () -> b.setBinding(AtomicInteger::set, null));
        assertThrows(NullPointerException.class, () -> b.setControl(null));
        OptionImpl<AtomicInteger, Integer> enabled = builder(storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t"))
                .setBinding(new GenericBinding<>(AtomicInteger::set, AtomicInteger::get))
                .setEnabledPredicate(() -> true).setEnabled(true).build();
        assertTrue(enabled.isAvailable());
        // The impact is optional and can be taken back
        assertNull(named(storage(0)).build().getImpact());
        assertNull(named(storage(0)).setImpact(OptionImpact.HIGH).setImpact(null).build().getImpact());
    }

    @Test
    void aBuilderRefusesANullStorageAtOnce() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> OptionImpl.createBuilder(Integer.class, null));
        assertEquals("Storage must not be null", thrown.getMessage());
    }

    @Test
    void inferredLabelsAreTheIdsLangKeysAndAnExplicitOneWins() {
        OptionImpl<AtomicInteger, Integer> inferred = builder(storage(0)).setId(id("inferred")).build();
        assertEquals(key(MOD + ".options.inferred.name"), inferred.getName());
        assertEquals(key(MOD + ".options.inferred.tooltip"), inferred.getTooltip());
        TextComponent name = TextComponent.literal("Name");
        TextComponent tooltip = TextComponent.literal("Tip");
        OptionImpl<AtomicInteger, Integer> halfNamed = builder(storage(0)).setId(id("half")).setName(name).build();
        assertSame(name, halfNamed.getName());
        assertEquals(key(MOD + ".options.half.tooltip"), halfNamed.getTooltip());
        OptionImpl<AtomicInteger, Integer> halfTipped = builder(storage(0)).setTooltip(tooltip).setId(id("tipped")).build();
        assertEquals(key(MOD + ".options.tipped.name"), halfTipped.getName());
        assertSame(tooltip, halfTipped.getTooltip());
    }

    @Test
    void aReusedBuilderInfersLabelsFromEachBuildsOwnId() {
        OptionImpl.Builder<AtomicInteger, Integer> builder = builder(storage(0));
        OptionImpl<AtomicInteger, Integer> first = builder.setId(id("first")).build();
        OptionImpl<AtomicInteger, Integer> second = builder.setId(id("second")).build();
        assertEquals(key(MOD + ".options.first.name"), first.getName());
        assertEquals(key(MOD + ".options.first.tooltip"), first.getTooltip());
        assertEquals(key(MOD + ".options.second.name"), second.getName());
        assertEquals(key(MOD + ".options.second.tooltip"), second.getTooltip());
        // An explicit name is kept for every build while the tooltip still follows the id
        TextComponent fixed = TextComponent.literal("Fixed");
        OptionImpl<AtomicInteger, Integer> third = builder.setName(fixed).setId(id("third")).build();
        assertSame(fixed, third.getName());
        assertEquals(key(MOD + ".options.third.tooltip"), third.getTooltip());
    }

    @Test
    void aFailedBuildLeavesTheBuilderAsItWas() {
        OptionImpl.Builder<AtomicInteger, Integer> unbound = OptionImpl.createBuilder(Integer.class, storage(0)).setControl(o -> control());
        unbound.setId(id("unbound"));
        assertEquals("Option binding must be specified", assertThrows(NullPointerException.class, unbound::build).getMessage());
        OptionImpl<AtomicInteger, Integer> bound = unbound.setId(id("bound")).setBinding(AtomicInteger::set, AtomicInteger::get).build();
        assertEquals(key(MOD + ".options.bound.name"), bound.getName());
        assertEquals(key(MOD + ".options.bound.tooltip"), bound.getTooltip());
        // A builder with neither id nor labels fails, then builds once it has an id
        OptionImpl.Builder<AtomicInteger, Integer> anonymous = builder(storage(0));
        assertThrows(NullPointerException.class, anonymous::build);
        assertEquals(key(MOD + ".options.late.name"), anonymous.setId(id("late")).build().getName());
        // A build without an id leaves the next build free to take one
        OptionImpl.Builder<AtomicInteger, Integer> labelled = named(storage(0));
        assertSame(OptionIdentifier.EMPTY, labelled.build().getId());
        OptionIdentifier<Integer> later = id("later");
        assertSame(later, labelled.setId(later).build().getId());
    }

    @Test
    void anOptionsFlagsAreReadOnlyAndKeptApartFromTheBuilderThatMadeIt() {
        OptionImpl.Builder<AtomicInteger, Integer> builder = named(storage(0)).setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD);
        OptionImpl<AtomicInteger, Integer> first = builder.build();
        Collection<OptionFlag> flags = first.getFlags();
        assertThrows(UnsupportedOperationException.class, () -> flags.add(OptionFlag.REQUIRES_GAME_RESTART));
        assertThrows(UnsupportedOperationException.class, () -> flags.remove(OptionFlag.REQUIRES_RENDERER_RELOAD));
        assertThrows(UnsupportedOperationException.class, () -> flags.removeIf(flag -> true));
        assertThrows(UnsupportedOperationException.class, flags::clear);
        assertThrows(UnsupportedOperationException.class, () -> {
            Iterator<OptionFlag> iterator = flags.iterator();
            iterator.next();
            iterator.remove();
        });
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), Set.copyOf(first.getFlags()));
        // The builder going on to make another option leaves the first one as it was built
        OptionImpl<AtomicInteger, Integer> second = builder.setFlags(OptionFlag.REQUIRES_GAME_RESTART).build();
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), Set.copyOf(first.getFlags()));
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_GAME_RESTART), Set.copyOf(second.getFlags()));
        // The same collection on every call, so reading the flags allocates nothing
        assertSame(first.getFlags(), first.getFlags());
        // No flags is an empty collection, never null
        assertTrue(named(storage(0)).build().getFlags().isEmpty());
    }

    @Test
    void optionsWithTheSameFlagsShareOneReadOnlySetInDeclarationOrder() {
        OptionFlag[] all = OptionFlag.values();
        for (int bits = 0; bits < 1 << all.length; bits++) {
            EnumSet<OptionFlag> expected = EnumSet.noneOf(OptionFlag.class);
            List<OptionFlag> reversed = new ArrayList<>();
            for (OptionFlag flag : all) {
                if ((bits & 1 << flag.ordinal()) != 0) {
                    expected.add(flag);
                    reversed.addFirst(flag);
                }
            }
            Collection<OptionFlag> first = named(storage(0)).setFlags(reversed.toArray(OptionFlag[]::new)).build().getFlags();
            // Given twice over and in another order, still the same flags
            OptionFlag[] given = expected.toArray(OptionFlag[]::new);
            Collection<OptionFlag> second = named(storage(0)).setFlags(given).setFlags(given).build().getFlags();
            assertEquals(List.copyOf(expected), List.copyOf(first), expected::toString);
            assertSame(first, second, expected::toString);
            assertThrows(UnsupportedOperationException.class, () -> first.add(OptionFlag.REQUIRES_GAME_RESTART));
            assertThrows(UnsupportedOperationException.class, first::clear);
        }
    }

    @Test
    void flagsWithANullAmongThemAreRefusedWithoutAddingAny() {
        OptionImpl.Builder<AtomicInteger, Integer> builder = named(storage(0));
        assertThrows(NullPointerException.class, () -> builder.setFlags(OptionFlag.REQUIRES_GAME_RESTART, null));
        assertThrows(NullPointerException.class, () -> builder.setFlags((OptionFlag[]) null));
        assertTrue(builder.build().getFlags().isEmpty());
        // None given is no change
        assertTrue(builder.setFlags().build().getFlags().isEmpty());
        assertEquals(List.of(OptionFlag.REQUIRES_ASSET_RELOAD), List.copyOf(builder.setFlags(OptionFlag.REQUIRES_ASSET_RELOAD).setFlags().build().getFlags()));
    }

    @Test
    void isAvailableAsksThePredicateOnEveryCallAndANullOneIsRefused() {
        OptionImpl.Builder<AtomicInteger, Integer> builder = named(storage(0));
        assertEquals("Enabled predicate must not be null", assertThrows(NullPointerException.class, () -> builder.setEnabledPredicate(null)).getMessage());
        // The refused predicate leaves the default in place
        assertTrue(builder.build().isAvailable());
        AtomicInteger asks = new AtomicInteger();
        BooleanSupplier flipping = () -> asks.incrementAndGet() % 2 == 1;
        OptionImpl<AtomicInteger, Integer> live = builder.setEnabledPredicate(flipping).build();
        // Not asked while building
        assertEquals(0, asks.get());
        assertTrue(live.isAvailable());
        assertFalse(live.isAvailable());
        assertTrue(live.isAvailable());
        assertEquals(3, asks.get());
        // The constant form replaces a predicate given before it
        assertFalse(builder.setEnabled(false).build().isAvailable());
        assertTrue(builder.setEnabled(true).build().isAvailable());
        assertEquals(3, asks.get());
    }

    @Test
    void anOptionKeepsTheBoxedTypeItsBuilderWasGiven() {
        assertSame(Boolean.class, builtType(boolean.class));
        assertSame(Integer.class, builtType(int.class));
        assertSame(Long.class, builtType(long.class));
        assertSame(Double.class, builtType(double.class));
        assertSame(Character.class, builtType(char.class));
        assertSame(Boolean.class, builtType(Boolean.class));
        assertSame(String.class, builtType(String.class));
        assertSame(OptionImpact.class, builtType(OptionImpact.class));
        // Arrays are reference types even of primitives
        assertSame(int[].class, builtType(int[].class));
        // No type says nothing, which matches any control
        assertSame(Object.class, builtType(null));
        // Kept from the builder even without an id, where the id has no type to give
        OptionImpl<AtomicInteger, Integer> unnamed = named(storage(0)).build();
        assertSame(OptionIdentifier.EMPTY, unnamed.getId());
        assertSame(Integer.class, unnamed.getValueType());
        // The same class the built-in controls declare, so the check against them is one comparison
        @SuppressWarnings("unchecked")
        Option<Boolean> tick = Mockito.mock(Option.class);
        assertSame(new TickBoxControl(tick).getValueType(), builtType(boolean.class));
    }

    @Test
    void aVoidBuilderTypeIsUntypedLikeAnUntypedId() {
        // As the interface answers for a void id, since no value is an instance of Void
        assertSame(Object.class, builtType(void.class));
        assertSame(Object.class, builtType(Void.class));
    }
}
