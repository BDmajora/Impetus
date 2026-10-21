package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.frame.OptionPageFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OptionControlElementTest {
    private static final Dim2i DIM = new Dim2i(10, 20, 200, 18);
    private static final String MOD = "option_control_element_test";
    private static final String FAILURE_LOG = "Control of option {} failed, showing a placeholder row";

    private enum Mode { A, B }

    // A mock option holding a value, available and unchanged
    private static <T> Option<T> option(String name, T initial) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A boolean control whose rows come from the test's factory
    private static final class Bound implements Control<Boolean> {
        private final Option<Boolean> option;
        Function<Dim2i, ControlElement<Boolean>> factory;

        Bound(Option<Boolean> option, Function<Dim2i, ControlElement<Boolean>> factory) {
            this.option = option;
            this.factory = factory;
        }

        @Override
        public Option<Boolean> getOption() {
            return this.option;
        }

        @Override
        public ControlElement<Boolean> createElement(Dim2i dim) {
            return this.factory.apply(dim);
        }

        @Override
        public int getMaxWidth() {
            return 30;
        }
    }

    // Binds a control to the option the way OptionImpl binds one, so each edits the other
    private static Bound bind(Option<Boolean> option, Function<Dim2i, ControlElement<Boolean>> factory) {
        Bound control = new Bound(option, factory);
        when(option.getControl()).thenReturn(control);
        return control;
    }

    // A row counting its getter calls and answering with whatever the test sets, its own option until then
    private static final class Counting extends ControlElement<Boolean> {
        Supplier<Option<Boolean>> answer;
        int reads;

        Counting(Option<Boolean> option, Dim2i dim) {
            super(option, dim);
            this.answer = () -> option;
        }

        @Override
        public Option<Boolean> getOption() {
            this.reads++;
            return this.answer.get();
        }
    }

    // A page whose only row is a counting one for the named option, at y 18 to 36 of a 200 wide frame
    private record Hovered(OptionPageFrame frame, Option<Boolean> option, Counting row) {
        static Hovered of(String name) {
            // Qualified, as the record's own option() accessor would hide the helper
            Option<Boolean> option = OptionControlElementTest.option(name, true);
            when(option.getTooltip()).thenReturn(TextComponent.literal("tip for " + name));
            AtomicReference<Counting> made = new AtomicReference<>();
            bind(option, dim -> {
                Counting row = new Counting(option, dim);
                made.set(row);
                return row;
            });
            OptionGroup group = OptionGroup.createBuilder().add(option).build();
            OptionPage page = new OptionPage(OptionIdentifier.create(MOD, "page"), TextComponent.literal("Page"), List.of(group));
            return new Hovered(new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page, o -> true), option, made.get());
        }

        // The frame's row as it stands now, the counting one or its placeholder
        ControlElement<?> current() {
            return this.frame.interactableChildren().filter(ControlElement.class::isInstance).<ControlElement<?>>map(child -> (ControlElement<?>) child).findFirst().orElseThrow();
        }

        // Renders the frame hovering the row twice, then once more past the tooltip delay
        void hoverPastTheDelay(FakeDrawContext draw) {
            this.frame.render(draw, 100, 27, 0f);
            this.frame.render(draw, 100, 27, 0f);
            try {
                Field lastTime = OptionPageFrame.class.getDeclaredField("lastTime");
                lastTime.setAccessible(true);
                lastTime.setLong(this.frame, 1L);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            this.frame.render(draw, 100, 27, 0f);
        }
    }

    // Runs the body with the placeholder logger swapped for a mock and no toasts before or after
    private static void withLogger(Consumer<Logger> body) {
        // Reading it first runs the class initialiser, which would otherwise overwrite the swap
        Logger original = Statics.get(PlaceholderControlElement.class, "LOGGER");
        Logger mock = Mockito.mock(Logger.class);
        Statics.set(PlaceholderControlElement.class, "LOGGER", mock);
        clearToasts();
        try {
            body.accept(mock);
        } finally {
            Statics.set(PlaceholderControlElement.class, "LOGGER", original);
            clearToasts();
        }
    }

    private static void clearToasts() {
        Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
    }

    private static List<String> toastTitles() {
        return ImpetusNotifications.getVisible().stream().map(ImpetusNotification::title).toList();
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrows(NullPointerException.class, call).getMessage());
    }

    // One row of every built-in kind, each with the option it was made for
    private static List<Map.Entry<Option<?>, ControlElement<?>>> builtInRows() {
        Option<Boolean> tick = option("Tick", true);
        Option<Integer> slide = option("Slide", 4);
        Option<Mode> cycle = option("Cycle", Mode.A);
        Option<Boolean> button = option("Button", false);
        Option<String> text = option("Text", "v1");
        Option<Boolean> plain = option("Plain", true);
        Option<Boolean> broken = option("Broken", true);
        return List.of(
                Map.entry(tick, new TickBoxControl(tick).createElement(DIM)),
                Map.entry(slide, new SliderControl(slide, 0, 10, 2, ControlValueFormatter.number()).createElement(DIM)),
                Map.entry(cycle, new CyclingControl<>(cycle, Mode.class).createElement(DIM)),
                Map.entry(button, new ActionButtonControl(button, () -> { }).createElement(DIM)),
                Map.entry(text, new ReadOnlyStringControl(text).createElement(DIM)),
                Map.entry(plain, new ControlElement<>(plain, DIM)),
                Map.entry(broken, new PlaceholderControlElement<>(broken, DIM, "Broken")));
    }

    @Test
    void theShapeIsUnchangedSoRowsAddonsCompiledAgainstItStillLink() throws NoSuchMethodException {
        Class<?> type = OptionControlElement.class;
        assertTrue(type.isInterface() && !type.isAnnotation());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(0, type.getInterfaces().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertEquals(0, Arrays.stream(type.getDeclaredFields()).filter(f -> !f.isSynthetic()).count());
        TypeVariable<?>[] parameters = type.getTypeParameters();
        assertEquals(1, parameters.length);
        assertArrayEquals(new Type[]{Object.class}, parameters[0].getBounds());
        // The getter is the only method, so an addon row needs nothing else and a new abstract method would break it
        Method getter = type.getMethod("getOption");
        assertEquals(List.of(getter), Arrays.stream(type.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList());
        assertTrue(Modifier.isPublic(getter.getModifiers()) && Modifier.isAbstract(getter.getModifiers()) && !getter.isDefault());
        assertEquals(0, getter.getParameterCount());
        assertEquals(0, getter.getExceptionTypes().length);
        assertSame(Option.class, getter.getReturnType());
        ParameterizedType returned = assertInstanceOf(ParameterizedType.class, getter.getGenericReturnType());
        assertSame(Option.class, returned.getRawType());
        assertArrayEquals(new Type[]{parameters[0]}, returned.getActualTypeArguments());
    }

    @Test
    void everyRowImplementsItWithItsOwnValueTypeAndAnOverridableGetter() throws NoSuchMethodException {
        ParameterizedType implemented = Arrays.stream(ControlElement.class.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(p -> p.getRawType() == OptionControlElement.class)
                .findFirst().orElseThrow();
        assertArrayEquals(ControlElement.class.getTypeParameters(), implemented.getActualTypeArguments());
        Method getter = ControlElement.class.getMethod("getOption");
        assertSame(ControlElement.class, getter.getDeclaringClass());
        assertSame(Option.class, getter.getReturnType());
        // Addon rows override it, which creation and the page's tooltip both guard against
        assertFalse(Modifier.isFinal(getter.getModifiers()));
        assertFalse(getter.isBridge());
    }

    @Test
    void everyBuiltInRowReportsTheOptionItWasMadeForWithoutAskingItAnything() {
        for (Map.Entry<Option<?>, ControlElement<?>> entry : builtInRows()) {
            Option<?> option = entry.getKey();
            OptionControlElement<?> row = entry.getValue();
            String kind = row.getClass().getName();
            clearInvocations(option);
            // The same instance on every call, through the interface as through the row
            for (int call = 0; call < 3; call++) {
                assertSame(option, row.getOption(), kind);
                assertSame(option, entry.getValue().getOption(), kind);
            }
            // A plain field read, so neither the option nor its control is called
            verifyNoInteractions(option);
        }
    }

    @Test
    void theValueTypeReachesCallersOfTheInterfaceSoNoCastIsNeeded() {
        Option<Integer> option = option("Slide", 4);
        OptionControlElement<Integer> row = new SliderControl(option, 0, 10, 2, ControlValueFormatter.number()).createElement(DIM);
        Option<Integer> read = row.getOption();
        Integer value = read.getValue();
        assertEquals(4, value);
        Option<Mode> cycled = option("Cycle", Mode.B);
        OptionControlElement<Mode> cycler = new CyclingControl<>(cycled, Mode.class).createElement(DIM);
        assertSame(Mode.B, cycler.getOption().getValue());
    }

    @Test
    void rowsOfOneOptionShareItAndRowsOfOtherOptionsKeepTheirOwn() {
        Option<Boolean> option = option("Shared", true);
        TickBoxControl control = new TickBoxControl(option);
        ControlElement<Boolean> first = control.createElement(DIM);
        ControlElement<Boolean> second = control.createElement(DIM.withY(40));
        assertNotSame(first, second);
        assertSame(option, first.getOption());
        assertSame(option, second.getOption());
        Option<Boolean> other = option("Other", true);
        assertSame(other, new TickBoxControl(other).createElement(DIM).getOption());
        // Making a row for another option leaves the earlier rows' options alone
        assertSame(option, first.getOption());
    }

    @Test
    void noRowCanBeMadeWithoutAnOptionSoTheGetterNeverAnswersNull() {
        npe("Option must not be null", () -> new ControlElement<Boolean>(null, DIM));
        npe("Option must not be null", () -> new PlaceholderControlElement<Boolean>(null, DIM, "None"));
        npe("Option must not be null", () -> new TickBoxControl(null));
        npe("Option must not be null", () -> new SliderControl(null, 0, 10, 2, ControlValueFormatter.number()));
        npe("Option must not be null", () -> new ReadOnlyStringControl(null));
        npe("Option must not be null", () -> new ActionButtonControl(null, () -> { }));
        assertThrows(NullPointerException.class, () -> new CyclingControl<>(null, Mode.class));
        // The placeholder path refuses it too, before any row is made
        npe("Option must not be null", () -> Control.createElementOrPlaceholder(null, DIM));
    }

    @Test
    void creationRefusesARowWhoseGetterAnswersNullAndThePlaceholderPathTakesOverFromOneThatThrows() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name", true);
            Bound control = bind(option, dim -> new ControlElement<>(option, dim) {
                @Override
                public Option<Boolean> getOption() {
                    return null;
                }
            });
            assertEquals("Element of option Name edits a different option",
                    assertThrows(IllegalStateException.class, () -> Control.createElementFor(option, DIM)).getMessage());
            IllegalStateException failure = new IllegalStateException("getter");
            control.factory = dim -> new ControlElement<>(option, dim) {
                @Override
                public Option<Boolean> getOption() {
                    throw failure;
                }
            };
            // Its own failure comes through untouched rather than as a broken contract
            assertSame(failure, assertThrows(IllegalStateException.class, () -> Control.createElementFor(option, DIM)));
            // The page's path shows a placeholder, which answers from its field
            ControlElement<Boolean> placeholder = Control.createElementOrPlaceholder(option, DIM);
            assertInstanceOf(PlaceholderControlElement.class, placeholder);
            assertSame(option, placeholder.getOption());
            verify(logger).error(eq(FAILURE_LOG), eq("Name"), same(failure));
        });
    }

    @Test
    void theHoveredRowIsAskedForItsOptionOncePerTooltipFrameAndNotBeforeTheDelay() {
        Hovered page = Hovered.of("Counted");
        // Creation checks it once
        int made = page.row().reads;
        assertEquals(1, made);
        FakeDrawContext draw = new FakeDrawContext();
        page.frame().render(draw, 100, 27, 0f);
        page.frame().render(draw, 100, 27, 0f);
        // Hovered but not yet due, so nothing is asked
        assertEquals(made, page.row().reads);
        page.hoverPastTheDelay(draw);
        assertEquals(made + 1, page.row().reads);
        assertTrue(draw.strings.contains("tip for Counted"), draw.strings::toString);
        for (int frame = 2; frame <= 4; frame++) {
            page.frame().render(draw, 100, 27, 0f);
            assertEquals(made + frame, page.row().reads);
        }
        // Moving off the row stops the asking
        page.frame().render(draw, -1, -1, 0f);
        page.frame().render(draw, -1, -1, 0f);
        assertEquals(made + 4, page.row().reads);
        assertSame(page.row(), page.current());
    }

    @Test
    void aHoveredRowWhoseGetterThrowsIsReplacedAndThePlaceholderShowsTheTooltipLater() {
        withLogger(logger -> {
            Hovered page = Hovered.of("Thrower");
            IllegalStateException failure = new IllegalStateException("getter");
            page.row().answer = () -> {
                throw failure;
            };
            FakeDrawContext draw = new FakeDrawContext();
            assertDoesNotThrow(() -> page.hoverPastTheDelay(draw));
            ControlElement<?> placeholder = assertInstanceOf(PlaceholderControlElement.class, page.current());
            assertSame(page.option(), placeholder.getOption());
            verify(logger).error(eq(FAILURE_LOG), eq("Thrower"), same(failure));
            assertEquals(List.of("Option failed: Thrower"), toastTitles());
            assertFalse(draw.strings.contains("tip for Thrower"));
            // The broken row is never asked again, and the placeholder's own hover brings the tooltip back
            int reads = page.row().reads;
            FakeDrawContext later = new FakeDrawContext();
            page.hoverPastTheDelay(later);
            assertTrue(later.strings.contains("tip for Thrower"), later.strings::toString);
            assertEquals(reads, page.row().reads);
            assertEquals(0, later.matrixDepth);
        });
    }

    @Test
    void aHoveredRowThatReportsNoOptionIsReplacedRatherThanBlamedOnTheTooltip() {
        withLogger(logger -> {
            Hovered page = Hovered.of("Nothing");
            page.row().answer = () -> null;
            FakeDrawContext draw = new FakeDrawContext();
            page.hoverPastTheDelay(draw);
            assertInstanceOf(PlaceholderControlElement.class, page.current());
            verify(logger).error(eq(FAILURE_LOG), eq("Nothing"),
                    argThat((Throwable t) -> t instanceof IllegalStateException && "Element reports no option".equals(t.getMessage())));
            // Reported as the row's failure only, never as a broken tooltip
            assertEquals(List.of("Option failed: Nothing"), toastTitles());
            FakeDrawContext later = new FakeDrawContext();
            page.hoverPastTheDelay(later);
            assertTrue(later.strings.contains("tip for Nothing"), later.strings::toString);
        });
    }

    @Test
    void aVirtualMachineErrorFromTheHoveredRowsGetterIsNeverSwallowed() {
        withLogger(logger -> {
            Hovered page = Hovered.of("Fatal");
            InternalError fatal = new InternalError("vm");
            page.row().answer = () -> {
                throw fatal;
            };
            assertSame(fatal, assertThrows(InternalError.class, () -> page.hoverPastTheDelay(new FakeDrawContext())));
            // The row stays, as nothing can be trusted to recover from it
            assertSame(page.row(), page.current());
            verifyNoInteractions(logger);
            assertEquals(List.of(), toastTitles());
        });
    }
}
