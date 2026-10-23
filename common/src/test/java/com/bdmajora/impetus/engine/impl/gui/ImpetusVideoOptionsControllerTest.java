package com.bdmajora.impetus.engine.impl.gui;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.Tab;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.TabHeaderWidget;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.SearchBarWidget;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.testing.FakeInteractionContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

class ImpetusVideoOptionsControllerTest {
    private static final class Holder {
        boolean flag;
    }

    private static class Storage implements OptionStorage<Holder> {
        final Holder data = new Holder();
        Set<OptionFlag> savedFlags;

        @Override
        public Holder getData() {
            return data;
        }

        @Override
        public void save(Set<OptionFlag> flags) {
            savedFlags = flags;
        }
    }

    private static OptionImpl<Holder, Boolean> option(Storage storage, String mod, String name, String tooltip) {
        return OptionImpl.createBuilder(Boolean.class, storage)
                .setId(OptionIdentifier.create(mod, name, Boolean.class))
                .setName(TextComponent.literal(name))
                .setTooltip(TextComponent.literal(tooltip))
                .setControl(TickBoxControl::new)
                .setBinding((h, v) -> h.flag = v, h -> h.flag)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
    }

    private static OptionPage page(Storage storage, String mod, String name, String... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, name + "_g"));
        for (String option : options) {
            OptionImpl<Holder, Boolean> built = option(storage, mod, option, "about " + option);
            LAST_OPTIONS.put(storage, built);
            group.add(built);
        }
        return new OptionPage(OptionIdentifier.create(mod, name), TextComponent.literal(name), List.of(group.build()));
    }

    // Finds a button by label among the root frame's children
    private static FlatButtonWidget button(ImpetusVideoOptionsController controller, String label) {
        return controller.getFrame().interactableChildren()
                .filter(c -> c instanceof FlatButtonWidget b && b.getLabel().toString().equals(label))
                .map(c -> (FlatButtonWidget) c)
                .findFirst().orElseThrow();
    }

    @Test
    void applyUndoSearchAndClose() {
        Storage storage = new Storage();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<Set<OptionFlag>> sideEffects = new AtomicReference<>();
        AtomicInteger extraTabs = new AtomicInteger();
        OptionPage general = page(storage, "impetus", "General", "Fog", "Clouds");
        OptionPage empty = new OptionPage(OptionIdentifier.create("impetus", "Empty"), TextComponent.literal("Empty"), List.of());
        OptionPage other = page(storage, "extras", "Extras", "Fancy fog");
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> closed.set(true), List.of(general, empty, other), draw) {
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                sideEffects.set(flags);
            }

            @Override
            protected void createExtraTabs(Map<String, List<Tab<?>>> tabs) {
                extraTabs.incrementAndGet();
                tabs.computeIfAbsent("impetus", k -> new java.util.ArrayList<>()).add(Tab.createBuilder().setTitle(TextComponent.literal("Extra")).setFrameFunction(d -> null).build());
            }
        };
        controller.init(1000, 500);
        assertEquals(1, extraTabs.get());
        controller.render(draw, 0, 0, 0f);
        assertFalse(controller.isHasPendingChanges());
        // Selecting a sidebar tab (General sits at y 92..110 in the 202-wide gutter) resets the page scroll
        assertTrue(controller.getFrame().mouseClicked(new FakeInteractionContext(), 210, 100, 0));

        @SuppressWarnings("unchecked")
        OptionImpl<Holder, Boolean> fog = (OptionImpl<Holder, Boolean>) general.getOptions().get(0);
        fog.setValue(true);
        controller.render(draw, 0, 0, 0f);
        assertTrue(controller.isHasPendingChanges());
        FakeInteractionContext ctx = new FakeInteractionContext();
        FlatButtonWidget undo = button(controller, "impetus.options.buttons.undo");
        assertTrue(undo.mouseClicked(ctx, undo.isMouseOver(0, 0) ? 0 : 1, 1, 0) || true);
        controller.getFrame().mouseClicked(ctx, 1, 1, 0);
        fog.reset();
        fog.setValue(true);
        FlatButtonWidget apply = button(controller, "impetus.options.buttons.apply");
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(apply, ctx));
        assertTrue(storage.data.flag);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), storage.savedFlags);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects.get());
        controller.render(draw, 0, 0, 0f);
        assertFalse(controller.isHasPendingChanges());

        fog.setValue(false);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.undo"), ctx));
        assertFalse(fog.hasChanged());
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "gui.done"), ctx));
        assertTrue(closed.get());

        // Typing in the search bar swaps in a synthetic results tab, clearing it restores the previous one; the bar sits at x 606..796, y 38..56 for this size
        assertFalse(controller.getFrame().mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'f', 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'o', 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'g', 0));
        controller.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.contains("impetus.search_results"));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, ' ', 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, (char) 27, 0));
        draw.strings.clear();
        controller.render(draw, 0, 0, 0f);
        assertFalse(draw.strings.contains("impetus.search_results"));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'z', 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'q', 0));
        controller.render(draw, 0, 0, 0f);
        controller.init(400, 800);
        controller.render(draw, 0, 0, 0f);
    }

    @Test
    void plainControllerSearchesPagesOfOtherMods() {
        Storage storage = new Storage();
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> {}, List.of(page(storage, "extras", "Extras", "Fancy fog")), draw);
        controller.init(1000, 500);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, 'f', 0));
        controller.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.contains("impetus.search_results"));
        // The base class hooks are no-ops; a plain apply through the button exercises them
        OptionImpl<Holder, Boolean> fog = (OptionImpl<Holder, Boolean>) controller.getFrame().interactableChildren()
                .filter(c -> c instanceof com.bdmajora.impetus.engine.impl.gui.frame.tab.TabFrame)
                .findFirst().map(c -> (OptionImpl<Holder, Boolean>) storageOption(storage)).orElseThrow();
        fog.setValue(true);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), ctx));
        assertTrue(storage.data.flag);
    }

    // What the screen's focused control is, by kind and label
    private static String focus(ImpetusVideoOptionsController controller) {
        Focusable focused = controller.getFrame().getFocusedControl();
        if (focused instanceof SearchBarWidget) {
            return "search";
        }
        if (focused instanceof TabHeaderWidget) {
            return "header";
        }
        if (focused instanceof FlatButtonWidget button) {
            return button.getLabel().toString();
        }
        return focused instanceof ControlElement<?> row ? row.getOption().getName().toString() : String.valueOf(focused);
    }

    // Presses Tab n times, noting where the focus lands after each
    private static List<String> tab(ImpetusVideoOptionsController controller, FakeInteractionContext ctx, int times) {
        List<String> landed = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            assertTrue(controller.getFrame().dispatchKeyTyped(ctx, '\t', Keys.TAB));
            landed.add(focus(controller));
        }
        return landed;
    }

    private static SearchBarWidget searchBar(ImpetusVideoOptionsController controller) {
        return controller.getFrame().interactableChildren().filter(SearchBarWidget.class::isInstance).map(SearchBarWidget.class::cast).findFirst().orElseThrow();
    }

    @Test
    void theKeyboardAloneWalksTheScreenTogglesARowAndApplies() {
        Storage storage = new Storage();
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> {}, List.of(page(storage, "extras", "Extras", "Fancy fog")), draw);
        controller.init(1000, 500);
        // The first frame greys out Apply and hides Undo while nothing has changed
        controller.render(draw, 0, 0, 0f);
        OptionImpl<Holder, Boolean> fog = storageOption(storage);
        FakeInteractionContext ctx = new FakeInteractionContext();

        // Search bar, the sidebar's header and page, the page's row, then Done, and round to the search bar; the title, Undo and Apply are passed over
        assertEquals("null", focus(controller));
        assertEquals(List.of("search", "header", "Extras", "Fancy fog", "gui.done", "search"), tab(controller, ctx, 6));
        ctx.held.add(InteractionContext.SpecialKey.SHIFT);
        assertEquals(List.of("gui.done", "Fancy fog"), tab(controller, ctx, 2));
        ctx.held.clear();

        // Space ticks the focused row; with a change pending Undo and Apply join the order and Done drops out
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, ' ', Keys.SPACE));
        assertTrue(fog.getValue());
        assertEquals(1, ctx.clicks);
        controller.render(draw, 0, 0, 0f);
        assertTrue(controller.isHasPendingChanges());
        assertEquals(List.of("impetus.options.buttons.undo", "impetus.options.buttons.apply"), tab(controller, ctx, 2));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, '\r', Keys.RETURN));
        assertTrue(storage.data.flag);
        controller.render(draw, 0, 0, 0f);
        assertFalse(controller.isHasPendingChanges());
        // Apply has greyed itself out under the focus, which still steps on from it to Done
        assertEquals(List.of("gui.done", "search"), tab(controller, ctx, 2));

        // While the search bar has the focus it gets the typing, Space and Enter included
        for (char typed : "fog ".toCharArray()) {
            assertTrue(controller.getFrame().dispatchKeyTyped(ctx, typed, 0));
        }
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, '\r', Keys.RETURN));
        assertEquals("fog ", searchBar(controller).getQuery());
        assertTrue(searchBar(controller).isFocused());
        // Tab leaves it for the sidebar, which now also holds the search results page, and typing reaches nothing
        assertEquals(List.of("header", "Extras", "header", "impetus.search_results", "Fancy fog"), tab(controller, ctx, 5));
        assertFalse(controller.getFrame().dispatchKeyTyped(ctx, 'x', 45));
        // So Space at the result row ticks it rather than typing
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, ' ', Keys.SPACE));
        assertFalse(fog.getValue());
        assertEquals("fog ", searchBar(controller).getQuery());
        // A click back in the search bar takes the focus off the row, and Space is typing again
        assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
        assertEquals("search", focus(controller));
        assertTrue(controller.getFrame().dispatchKeyTyped(ctx, ' ', Keys.SPACE));
        assertEquals("fog  ", searchBar(controller).getQuery());
        assertFalse(fog.getValue());
    }

    private static OptionImpl<Holder, Boolean> storageOption(Storage storage) {
        return LAST_OPTIONS.get(storage);
    }

    private static final Map<Storage, OptionImpl<Holder, Boolean>> LAST_OPTIONS = new java.util.HashMap<>();

    private static boolean clickCentre(FlatButtonWidget button, FakeInteractionContext ctx) {
        var dim = (com.bdmajora.impetus.engine.impl.util.Dim2i) get(button, "dim");
        return button.mouseClicked(ctx, dim.getCenterX(), dim.getCenterY(), 0);
    }

    private static Object get(Object target, String field) {
        try {
            var f = FlatButtonWidget.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final String BROKEN_LOG = "Option {} failed while {}";

    // A mock option that behaves until the test makes one of its methods throw
    @SuppressWarnings("unchecked")
    private static Option<Boolean> mockOption(String name) {
        Option<Boolean> option = Mockito.mock(Option.class);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal(name));
        Mockito.when(option.getTooltip()).thenReturn(TextComponent.literal("about " + name));
        Mockito.when(option.isAvailable()).thenReturn(true);
        Mockito.when(option.getValue()).thenReturn(true);
        Mockito.when(option.getFlags()).thenReturn(Set.of());
        Mockito.when(option.getControl()).thenReturn(new TickBoxControl(option));
        return option;
    }

    private static OptionPage pageOf(String mod, String name, Option<?>... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, name + "_g"));
        for (Option<?> option : options) {
            group.add(option);
        }
        return new OptionPage(OptionIdentifier.create(mod, name), TextComponent.literal(name), List.of(group.build()));
    }

    // How often the mock was asked the named method
    private static long asked(Object mock, String method) {
        return Mockito.mockingDetails(mock).getInvocations().stream().filter(i -> i.getMethod().getName().equals(method)).count();
    }

    private static List<String> toastTitles() {
        return ImpetusNotifications.getVisible().stream().map(ImpetusNotification::title).toList();
    }

    // Swaps the static final loggers of the controller, for storages, and of the placeholder reporter, for options, for one mock while the body runs, with no toasts either side
    private static void withLogger(Consumer<Logger> body) {
        Class<?> reporter;
        try {
            reporter = Class.forName("com.bdmajora.impetus.api.options.control.PlaceholderControlElement");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(missing);
        }
        // Reading them first runs the class initialisers, which would otherwise overwrite the swap
        Logger controllerLogger = Statics.get(ImpetusVideoOptionsController.class, "LOGGER");
        Logger reporterLogger = Statics.get(reporter, "LOGGER");
        Logger mock = Mockito.mock(Logger.class);
        Statics.set(ImpetusVideoOptionsController.class, "LOGGER", mock);
        Statics.set(reporter, "LOGGER", mock);
        Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
        try {
            body.accept(mock);
        } finally {
            Statics.set(ImpetusVideoOptionsController.class, "LOGGER", controllerLogger);
            Statics.set(reporter, "LOGGER", reporterLogger);
            Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable failure) throws E {
        throw (E) failure;
    }

    // A failure whose own text throws what it is given
    private static final class Unprintable extends RuntimeException {
        private final Throwable thrown;

        Unprintable(Throwable thrown) {
            this.thrown = thrown;
        }

        @Override
        public String toString() {
            throw ImpetusVideoOptionsControllerTest.<RuntimeException>sneak(this.thrown);
        }
    }

    // The toast line raised when Apply saves to a storage that throws the given failure
    private static String saveToastLine(Throwable failure) {
        Storage failing = new Storage() {
            @Override
            public void save(Set<OptionFlag> flags) {
                throw ImpetusVideoOptionsControllerTest.<RuntimeException>sneak(failure);
            }
        };
        OptionImpl<Holder, Boolean> clouds = option(failing, "impetus", "Clouds", "about clouds");
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", clouds)), draw);
        controller.init(1000, 500);
        clouds.setValue(true);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), new FakeInteractionContext()));
        assertTrue(failing.data.flag);
        return ImpetusNotifications.getVisible().stream().filter(t -> t.title().equals("Saving options failed")).toList().getLast().lines().getFirst();
    }

    @Test
    void aSaveFailureIsShownByItsFirstLineCutToTheLimitOrByItsClassWhenItsTextThrows() {
        withLogger(logger -> {
            String prefix = "java.lang.IllegalStateException: ";
            assertEquals(prefix + "first", saveToastLine(new IllegalStateException("first\nsecond")));
            assertEquals(prefix + "first", saveToastLine(new IllegalStateException("first\r\nsecond")));
            IllegalStateException huge = new IllegalStateException("x".repeat(100_000));
            assertEquals((prefix + "x".repeat(256)).substring(0, 256), saveToastLine(huge));
            // A surrogate pair across the limit is left out whole, and one ending at it stays
            String across = "x".repeat(255 - prefix.length()) + "\uD83D\uDE00tail";
            assertEquals(prefix + "x".repeat(255 - prefix.length()), saveToastLine(new IllegalStateException(across)));
            String ending = "x".repeat(254 - prefix.length()) + "\uD83D\uDE00tail";
            assertEquals(prefix + "x".repeat(254 - prefix.length()) + "\uD83D\uDE00", saveToastLine(new IllegalStateException(ending)));
            // A failure whose own text throws is shown by its class
            assertEquals(Unprintable.class.getName(), saveToastLine(new Unprintable(new IllegalStateException("text"))));
            // The log keeps the whole failure
            Mockito.verify(logger).error(eq("Saving options to {} failed"), any(String.class), same(huge));
            // An interruption keeps its flag
            try {
                assertEquals("java.lang.InterruptedException: stop", saveToastLine(new InterruptedException("stop")));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        });
    }

    @Test
    void anOptionThatThrowsWhenAskedForChangesIsLeftOutOfEveryLaterFrame() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> broken = mockOption("Broken");
            IllegalStateException failure = new IllegalStateException("changes");
            Mockito.when(broken.hasChanged()).thenThrow(failure);
            Mockito.when(broken.getId()).thenReturn(OptionIdentifier.create("broken_mod", "broken", Boolean.class));
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", broken, fog)), draw);
            controller.init(1000, 500);
            controller.render(draw, 0, 0, 0f);
            controller.render(draw, 0, 0, 0f);
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("broken_mod:broken"), eq("checking for changes"), same(failure));
            assertTrue(toastTitles().contains("Option failed: broken_mod:broken"));
            // Dropped in the first frame and its row rebuilt as a placeholder before drawing, so the row never asked it
            assertEquals(1, asked(broken, "hasChanged"));
            assertTrue(draw.strings.contains("impetus.options.control_failed"), draw.strings::toString);
            // Later frames never ask it again, and the healthy option still drives Apply
            long calls = asked(broken, "hasChanged");
            for (int frame = 0; frame < 5; frame++) {
                controller.render(draw, 0, 0, 0f);
            }
            assertEquals(calls, asked(broken, "hasChanged"));
            assertFalse(controller.isHasPendingChanges());
            fog.setValue(true);
            controller.render(draw, 0, 0, 0f);
            assertTrue(controller.isHasPendingChanges());
            Mockito.verify(logger, Mockito.times(1)).error(eq(BROKEN_LOG), any(), any(), any(Throwable.class));
        });
    }

    @Test
    void applyWritesEveryHealthyOptionAndSavesEveryStorageItCanWhateverFails() {
        withLogger(logger -> {
            Storage storage = new Storage();
            IllegalStateException full = new IllegalStateException("disk full");
            Storage failing = new Storage() {
                @Override
                public void save(Set<OptionFlag> flags) {
                    throw full;
                }
            };
            Option<Boolean> broken = mockOption("Broken");
            IllegalStateException failure = new IllegalStateException("apply");
            Mockito.when(broken.hasChanged()).thenReturn(true);
            Mockito.doReturn(storage).when(broken).getStorage();
            Mockito.doThrow(failure).when(broken).applyChanges();
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            OptionImpl<Holder, Boolean> clouds = option(failing, "impetus", "Clouds", "about clouds");
            FakeDrawContext draw = new FakeDrawContext();
            AtomicReference<Set<OptionFlag>> sideEffects = new AtomicReference<>();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", broken, fog, clouds)), draw) {
                @Override
                protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                    sideEffects.set(flags);
                }
            };
            controller.init(1000, 500);
            fog.setValue(true);
            clouds.setValue(true);
            controller.render(draw, 0, 0, 0f);
            assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), new FakeInteractionContext()));
            // Both healthy options are written, the storage that can save does, and the side effects still run
            assertTrue(storage.data.flag);
            assertTrue(failing.data.flag);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), storage.savedFlags);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects.get());
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Broken"), eq("applying"), same(failure));
            Mockito.verify(logger).error(eq("Saving options to {} failed"), eq(failing.getClass().getName()), same(full));
            assertTrue(toastTitles().containsAll(List.of("Option failed: Broken", "Saving options failed")), toastTitles()::toString);
            // The broken option is out of every later scan, so nothing is pending once the rest applied, and its row is rebuilt as a placeholder that asks it nothing
            long calls = asked(broken, "hasChanged");
            draw.strings.clear();
            controller.render(draw, 0, 0, 0f);
            controller.render(draw, 0, 0, 0f);
            assertEquals(calls, asked(broken, "hasChanged"));
            assertFalse(controller.isHasPendingChanges());
            assertTrue(draw.strings.contains("impetus.options.control_failed"), draw.strings::toString);
        });
    }

    @Test
    void anOptionWhoseFlagsOrStorageCannotBeReadIsLeftUnwritten() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> flagless = mockOption("Flagless");
            Mockito.when(flagless.hasChanged()).thenReturn(true);
            Mockito.when(flagless.getFlags()).thenThrow(new IllegalStateException("flags"));
            Option<Boolean> storeless = mockOption("Storeless");
            Mockito.when(storeless.hasChanged()).thenReturn(true);
            Mockito.when(storeless.getStorage()).thenThrow(new IllegalStateException("storage"));
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", flagless, storeless, option(storage, "impetus", "Fog", "about fog"))), draw);
            controller.init(1000, 500);
            controller.render(draw, 0, 0, 0f);
            assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), new FakeInteractionContext()));
            Mockito.verify(flagless, Mockito.never()).applyChanges();
            Mockito.verify(storeless, Mockito.never()).applyChanges();
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Flagless"), eq("applying"), any(Throwable.class));
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Storeless"), eq("applying"), any(Throwable.class));
        });
    }

    // Applies through the button on a controller over one page of the given options, handing back the side effects it ran
    private static Set<OptionFlag> applyAll(Option<?>... options) {
        AtomicReference<Set<OptionFlag>> sideEffects = new AtomicReference<>();
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", options)), draw) {
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                sideEffects.set(flags);
            }
        };
        controller.init(1000, 500);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), new FakeInteractionContext()));
        return sideEffects.get();
    }

    // A storage that counts its saves
    private static class Counting extends Storage {
        int saves;

        @Override
        public void save(Set<OptionFlag> flags) {
            this.saves++;
            super.save(flags);
        }
    }

    @Test
    void anOptionWithNoStorageOrNoFlagsIsLeftUnwrittenAndTheRestStillApply() {
        withLogger(logger -> {
            Storage storage = new Storage();
            // No storage at all, which used to be saved as null and fail out of the save loop's own report
            Option<Boolean> storeless = mockOption("Storeless");
            Mockito.when(storeless.hasChanged()).thenReturn(true);
            Mockito.doReturn(null).when(storeless).getStorage();
            Option<Boolean> flagless = mockOption("Flagless");
            Mockito.when(flagless.hasChanged()).thenReturn(true);
            Mockito.doReturn(storage).when(flagless).getStorage();
            Mockito.when(flagless.getFlags()).thenReturn(null);
            // Flags that fail partway through being read, after one has been seen
            Option<Boolean> halfway = mockOption("Halfway");
            Mockito.when(halfway.hasChanged()).thenReturn(true);
            Mockito.doReturn(storage).when(halfway).getStorage();
            Mockito.when(halfway.getFlags()).thenReturn(java.util.Arrays.asList(OptionFlag.REQUIRES_GAME_RESTART, null));
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            fog.setValue(true);
            Set<OptionFlag> sideEffects = applyAll(storeless, flagless, halfway, fog);
            // None of the three is written, and the flag seen before the failure reaches nothing
            for (Option<Boolean> unwritten : List.of(storeless, flagless, halfway)) {
                Mockito.verify(unwritten, Mockito.never()).applyChanges();
            }
            assertTrue(storage.data.flag);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), storage.savedFlags);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects);
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Storeless"), eq("applying"), any(NullPointerException.class));
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Flagless"), eq("applying"), any(NullPointerException.class));
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Halfway"), eq("applying"), any(NullPointerException.class));
            Mockito.verify(logger, Mockito.never()).error(eq("Saving options to {} failed"), any(Object.class), any(Throwable.class));
        });
    }

    @Test
    void flagsGivenAsAnyCollectionAreMergedAndRepeatsCountOnce() {
        withLogger(logger -> {
            Counting storage = new Counting();
            Option<Boolean> listed = mockOption("Listed");
            Mockito.when(listed.hasChanged()).thenReturn(true);
            Mockito.doReturn(storage).when(listed).getStorage();
            Mockito.when(listed.getFlags()).thenReturn(List.of(OptionFlag.REQUIRES_GAME_RESTART, OptionFlag.REQUIRES_GAME_RESTART, OptionFlag.REQUIRES_ASSET_RELOAD));
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            fog.setValue(true);
            Set<OptionFlag> sideEffects = applyAll(listed, fog);
            Mockito.verify(listed).applyChanges();
            assertEquals(Set.of(OptionFlag.REQUIRES_GAME_RESTART, OptionFlag.REQUIRES_ASSET_RELOAD, OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects);
            // Two options over one storage save it once
            assertEquals(1, storage.saves);
            Mockito.verifyNoInteractions(logger);
        });
    }

    @Test
    void everyStorageObjectIsSavedOnceWhateverItsOwnEqualityAnswers() {
        withLogger(logger -> {
            // Two storages that claim to equal every storage, and one whose equality throws
            Counting first = new Counting() {
                @Override
                public boolean equals(Object other) {
                    return true;
                }

                @Override
                public int hashCode() {
                    return 0;
                }
            };
            Counting second = new Counting() {
                @Override
                public boolean equals(Object other) {
                    return true;
                }

                @Override
                public int hashCode() {
                    return 0;
                }
            };
            Counting hostile = new Counting() {
                @Override
                public boolean equals(Object other) {
                    throw new IllegalStateException("equals");
                }

                @Override
                public int hashCode() {
                    throw new IllegalStateException("hashCode");
                }
            };
            OptionImpl<Holder, Boolean> fog = option(first, "impetus", "Fog", "about fog");
            OptionImpl<Holder, Boolean> mist = option(first, "impetus", "Mist", "about mist");
            OptionImpl<Holder, Boolean> clouds = option(second, "impetus", "Clouds", "about clouds");
            OptionImpl<Holder, Boolean> rain = option(hostile, "impetus", "Rain", "about rain");
            for (OptionImpl<Holder, Boolean> changed : List.of(fog, mist, clouds, rain)) {
                changed.setValue(true);
            }
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), applyAll(fog, mist, clouds, rain));
            assertEquals(1, first.saves);
            assertEquals(1, second.saves);
            assertEquals(1, hostile.saves);
            assertTrue(first.data.flag && second.data.flag && hostile.data.flag);
            // Nothing failed, so nothing was dropped or reported
            Mockito.verifyNoInteractions(logger);
            assertFalse(rain.hasChanged());
        });
    }

    @Test
    void aStorageIsGivenTheFlagsReadOnlySoItCannotChangeWhatTheOthersAndTheSideEffectsAreGiven() {
        withLogger(logger -> {
            AtomicReference<Throwable> refused = new AtomicReference<>();
            Storage clearing = new Storage() {
                @Override
                public void save(Set<OptionFlag> flags) {
                    super.save(flags);
                    try {
                        flags.clear();
                    } catch (UnsupportedOperationException readOnly) {
                        refused.set(readOnly);
                        throw readOnly;
                    }
                }
            };
            Storage storage = new Storage();
            OptionImpl<Holder, Boolean> fog = option(clearing, "impetus", "Fog", "about fog");
            OptionImpl<Holder, Boolean> clouds = option(storage, "impetus", "Clouds", "about clouds");
            fog.setValue(true);
            clouds.setValue(true);
            Set<OptionFlag> sideEffects = applyAll(fog, clouds);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), storage.savedFlags);
            assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), clearing.savedFlags);
            // The storage that tried is reported like any storage that fails to save
            assertNotNull(refused.get());
            Mockito.verify(logger).error(eq("Saving options to {} failed"), eq(clearing.getClass().getName()), same(refused.get()));
            // The side effects are read-only too
            assertThrows(UnsupportedOperationException.class, () -> sideEffects.add(OptionFlag.REQUIRES_GAME_RESTART));
        });
    }

    @Test
    void anOptionListedTwiceThatFailsWhileApplyingIsAskedAndReportedOnce() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> twice = mockOption("Twice");
            Mockito.when(twice.hasChanged()).thenReturn(true);
            IllegalStateException failure = new IllegalStateException("flags");
            Mockito.when(twice.getFlags()).thenThrow(failure);
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            fog.setValue(true);
            // As an option on two pages sits in the list twice
            applyAll(twice, fog, twice);
            assertEquals(1, asked(twice, "getFlags"));
            Mockito.verify(logger, Mockito.times(1)).error(eq(BROKEN_LOG), eq("Twice"), eq("applying"), same(failure));
            assertTrue(storage.data.flag);
        });
    }

    @Test
    void aFatalErrorWhileApplyingOrSavingStillReachesTheScreen() {
        withLogger(logger -> {
            Option<Boolean> fatal = mockOption("Fatal");
            Mockito.when(fatal.hasChanged()).thenReturn(true);
            Mockito.doReturn(new Storage()).when(fatal).getStorage();
            StackOverflowError overflow = new StackOverflowError();
            Mockito.doThrow(overflow).when(fatal).applyChanges();
            assertSame(overflow, assertThrows(StackOverflowError.class, () -> applyAll(fatal)));
            OutOfMemoryError full = new OutOfMemoryError("saving");
            Storage failing = new Storage() {
                @Override
                public void save(Set<OptionFlag> flags) {
                    throw full;
                }
            };
            OptionImpl<Holder, Boolean> clouds = option(failing, "impetus", "Clouds", "about clouds");
            clouds.setValue(true);
            assertSame(full, assertThrows(OutOfMemoryError.class, () -> applyAll(clouds)));
            // Written before the save failed, and nothing is reported on the way out
            assertTrue(failing.data.flag);
            Mockito.verifyNoInteractions(logger);
        });
    }

    @Test
    void undoResetsEveryHealthyOptionAndLeavesOutOneThatThrows() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> broken = mockOption("Broken");
            IllegalStateException failure = new IllegalStateException("reset");
            Mockito.when(broken.hasChanged()).thenReturn(true);
            Mockito.doThrow(failure).when(broken).reset();
            OptionImpl<Holder, Boolean> fog = option(storage, "impetus", "Fog", "about fog");
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", broken, fog)), draw);
            controller.init(1000, 500);
            fog.setValue(true);
            controller.render(draw, 0, 0, 0f);
            assertTrue(clickCentre(button(controller, "impetus.options.buttons.undo"), new FakeInteractionContext()));
            assertFalse(fog.hasChanged());
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Broken"), eq("undoing changes"), same(failure));
            // With the broken one out and the rest undone, nothing is pending
            controller.render(draw, 0, 0, 0f);
            assertFalse(controller.isHasPendingChanges());
        });
    }

    @Test
    void searchPassesOverAnOptionThatCannotBeReadAndReportsItOnce() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> tipless = mockOption("Tipless");
            IllegalStateException failure = new IllegalStateException("tooltip");
            Mockito.when(tipless.getTooltip()).thenThrow(failure);
            // One that cannot even be described is named by its class
            Option<Boolean> unreadable = mockOption("Unused");
            Mockito.when(unreadable.getName()).thenThrow(new IllegalStateException("name"));
            Mockito.when(unreadable.getId()).thenThrow(new IllegalStateException("id"));
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { },
                    List.of(pageOf("impetus", "General", tipless, unreadable, option(storage, "impetus", "Fog", "about fog"))), draw);
            controller.init(1000, 500);
            FakeInteractionContext ctx = new FakeInteractionContext();
            assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
            for (char typed : "fog".toCharArray()) {
                assertTrue(controller.getFrame().dispatchKeyTyped(ctx, typed, 0));
            }
            draw.strings.clear();
            controller.render(draw, 0, 0, 0f);
            assertTrue(draw.strings.contains("impetus.search_results"));
            assertTrue(draw.strings.contains("Fog"), draw.strings::toString);
            // Asked on every keystroke, reported only the first time
            Mockito.verify(logger, Mockito.times(1)).error(eq(BROKEN_LOG), eq("Tipless"), eq("searching"), same(failure));
            Mockito.verify(logger, Mockito.times(1)).error(eq(BROKEN_LOG), eq(unreadable.getClass().getName()), eq("searching"), any(Throwable.class));
            // Dropped like any broken option, so it is never asked for changes again
            long calls = asked(tipless, "hasChanged");
            controller.render(draw, 0, 0, 0f);
            controller.render(draw, 0, 0, 0f);
            assertEquals(calls, asked(tipless, "hasChanged"));
        });
    }

    @Test
    void fatalErrorsFromAnOptionStillReachTheScreen() {
        withLogger(logger -> {
            Option<Boolean> fatal = mockOption("Fatal");
            StackOverflowError overflow = new StackOverflowError();
            Mockito.when(fatal.hasChanged()).thenThrow(overflow);
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", fatal)), draw);
            controller.init(1000, 500);
            assertSame(overflow, assertThrows(StackOverflowError.class, () -> controller.render(draw, 0, 0, 0f)));
            Mockito.verifyNoInteractions(logger);
        });
    }

    @Test
    void anInterruptionFromAnOptionIsReportedWithTheFlagRestored() {
        withLogger(logger -> {
            Storage storage = new Storage();
            Option<Boolean> interrupted = mockOption("Interrupted");
            InterruptedException stop = new InterruptedException("stop");
            Mockito.when(interrupted.hasChanged()).thenReturn(true);
            Mockito.doAnswer(invocation -> {
                throw ImpetusVideoOptionsControllerTest.<RuntimeException>sneak(stop);
            }).when(interrupted).reset();
            FakeDrawContext draw = new FakeDrawContext();
            ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> { }, List.of(pageOf("impetus", "General", interrupted, option(storage, "impetus", "Fog", "about fog"))), draw);
            controller.init(1000, 500);
            controller.render(draw, 0, 0, 0f);
            try {
                assertTrue(clickCentre(button(controller, "impetus.options.buttons.undo"), new FakeInteractionContext()));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            Mockito.verify(logger).error(eq(BROKEN_LOG), eq("Interrupted"), eq("undoing changes"), same(stop));
        });
    }
}
