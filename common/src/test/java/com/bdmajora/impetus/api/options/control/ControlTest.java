package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.BasicFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.OptionPageFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.FontMetricsProvider;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ControlTest {
    private static final Dim2i DIM = new Dim2i(10, 20, 200, 18);
    private static final String MOD = "control_contract_test";
    private static final String OPEN_KEY = "control_contract_test.open";
    private static final TextComponent OPEN = TextComponent.translatable(OPEN_KEY);
    private static final String PLACEHOLDER_KEY = "impetus.options.control_failed";
    private static final String FAILURE_LOG = "Control of option {} failed, showing a placeholder row";

    private enum Mode { A, B, C }

    private enum Other { X, Y }

    // A mock option with a name, available and unchanged, whose control the test binds
    private static <T> Option<T> option(String name) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A mock option holding a value, for the built-in controls whose elements read it when made
    private static <T> Option<T> valued(String name, T initial) {
        Option<T> option = option(name);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        return option;
    }

    // A control whose option, width, value type and element factory the test picks, counting every call so the contract's costs are checkable
    private static final class Probe<T> implements Control<T> {
        Option<T> option;
        int maxWidth;
        Class<?> valueType = Object.class;
        Function<Dim2i, ControlElement<T>> factory;
        int optionReads;
        int widthReads;
        int typeReads;
        int creations;

        Probe(Option<T> option, int maxWidth) {
            this.option = option;
            this.maxWidth = maxWidth;
            this.factory = dim -> new ControlElement<>(this.option, dim);
        }

        @Override
        public Option<T> getOption() {
            this.optionReads++;
            return this.option;
        }

        @Override
        public ControlElement<T> createElement(Dim2i dim) {
            this.creations++;
            return this.factory.apply(dim);
        }

        @Override
        public int getMaxWidth() {
            this.widthReads++;
            return this.maxWidth;
        }

        @Override
        public Class<?> getValueType() {
            this.typeReads++;
            return this.valueType;
        }
    }

    // Binds a probe to the option the way OptionImpl binds a control, so each edits the other
    private static <T> Probe<T> bound(Option<T> option, int maxWidth) {
        Probe<T> probe = new Probe<>(option, maxWidth);
        when(option.getControl()).thenReturn(probe);
        return probe;
    }

    // Sizes its column from a translated label plus 18 pixels, as a control whose text changes with the language would
    private static final class LabelSized implements Control<Boolean> {
        private final Option<Boolean> option;
        int measures;

        LabelSized(Option<Boolean> option) {
            this.option = option;
        }

        @Override
        public Option<Boolean> getOption() {
            return this.option;
        }

        @Override
        public ControlElement<Boolean> createElement(Dim2i dim) {
            return new ControlElement<>(this.option, dim);
        }

        @Override
        public int getMaxWidth() {
            return 70;
        }

        @Override
        public int getMaxWidth(FontMetricsProvider fonts) {
            this.measures++;
            return fonts.getStringWidth(OPEN) + 18;
        }
    }

    // Fake fonts reading translations from a table, standing in for the game's language, and counting how often each key is measured
    private static final class Language extends FakeDrawContext {
        private final Map<String, String> table;
        final Map<String, Integer> measured = new HashMap<>();
        final List<TextComponent> drawn = new ArrayList<>();

        // The open label as given and the placeholder label as "Error"
        Language(String open) {
            this.table = Map.of(OPEN_KEY, open, PLACEHOLDER_KEY, "Error");
        }

        @Override
        public String extractString(TextComponent component) {
            if (component instanceof TextComponent.Translatable translatable && this.table.containsKey(translatable.keys().getFirst())) {
                return this.table.get(translatable.keys().getFirst());
            }
            return super.extractString(component);
        }

        // Set while the fake's own drawString measures what it draws, which is not the caller measuring
        private boolean drawing;

        @Override
        public int getStringWidth(TextComponent component) {
            if (!this.drawing && component instanceof TextComponent.Translatable translatable) {
                this.measured.merge(translatable.keys().getFirst(), 1, Integer::sum);
            }
            return super.getStringWidth(component);
        }

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.drawn.add(str);
            this.drawing = true;
            try {
                return super.drawString(str, x, y, color, shadow);
            } finally {
                this.drawing = false;
            }
        }
    }

    // A row that throws from whichever of its methods the test arms, counting every call made on it
    private static final class Faulty extends ControlElement<Boolean> {
        final Set<String> armed = new HashSet<>();
        Throwable failure = new IllegalStateException("faulty");
        int calls;
        // Pushes a transform, a scissor and another transform before its draw is entered, as a row failing halfway would leave them
        boolean pushesFirst;

        Faulty(Option<Boolean> option, Dim2i dim) {
            super(option, dim);
        }

        private void enter(String method) {
            this.calls++;
            if (this.armed.contains(method)) {
                throw ControlTest.<RuntimeException>sneak(this.failure);
            }
        }

        @Override
        public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            if (this.pushesFirst) {
                drawContext.pushMatrix();
                drawContext.enableScissor(0, 18, 200, 36);
                drawContext.pushMatrix();
            }
            this.enter("render");
            super.render(drawContext, mouseX, mouseY, delta);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            this.enter("isMouseOver");
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
            this.enter("mouseClicked");
            return this.dim.containsCursor(mouseX, mouseY);
        }

        @Override
        public boolean mouseReleased(InteractionContext context, double mouseX, double mouseY, int button) {
            this.enter("mouseReleased");
            return false;
        }

        @Override
        public boolean mouseDragged(InteractionContext context, double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            this.enter("mouseDragged");
            return true;
        }

        @Override
        public boolean mouseScrolled(InteractionContext context, double mouseX, double mouseY, double deltaX, double deltaY) {
            this.enter("mouseScrolled");
            return false;
        }

        @Override
        public boolean keyTyped(InteractionContext context, char typedChar, int keyCode) {
            this.enter("keyTyped");
            return false;
        }

        @Override
        public boolean canFocus() {
            this.enter("canFocus");
            return true;
        }

        @Override
        public boolean isFocused() {
            this.enter("isFocused");
            return super.isFocused();
        }

        @Override
        public void setFocused(boolean focused) {
            this.enter("setFocused");
            super.setFocused(focused);
        }
    }

    // A page whose first row is a faulty one for the given option, over a second plain row named Fine; rows sit at y 18 and 36 of a 200 wide frame
    private record FaultyPage(OptionPageFrame frame, Option<Boolean> option, Faulty row) {
        static FaultyPage of(String name) {
            // Qualified, as the record's own option() accessor would hide the helper
            Option<Boolean> option = ControlTest.option(name);
            AtomicReference<Faulty> made = new AtomicReference<>();
            bound(option, 30).factory = dim -> {
                Faulty row = new Faulty(option, dim);
                made.set(row);
                return row;
            };
            Option<Boolean> fine = ControlTest.option("Fine");
            bound(fine, 30);
            OptionPageFrame frame = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page(option, fine), o -> true);
            return new FaultyPage(frame, option, made.get());
        }

        // Arms the row and returns the calls it has had so far
        int arm(String... methods) {
            this.row.armed.addAll(List.of(methods));
            return this.row.calls;
        }

        ControlElement<?> first() {
            return rows(this.frame).getFirst();
        }

        ControlElement<?> second() {
            return rows(this.frame).get(1);
        }
    }

    // One option per built-in control, each bound to its own control as OptionImpl binds them; widths 30, 70, 130, 90 and 70
    private static List<Option<?>> builtIns() {
        Option<Boolean> tick = valued("Tick", true);
        when(tick.getControl()).thenReturn(new TickBoxControl(tick));
        Option<Mode> cycle = valued("Cycle", Mode.B);
        when(cycle.getControl()).thenReturn(new CyclingControl<>(cycle, Mode.class));
        Option<Integer> slide = valued("Slide", 5);
        when(slide.getControl()).thenReturn(new SliderControl(slide, 0, 10, 1, ControlValueFormatter.number()));
        Option<String> text = valued("Text", "value");
        when(text.getControl()).thenReturn(new ReadOnlyStringControl(text));
        Option<Boolean> button = valued("Button", false);
        when(button.getControl()).thenReturn(new ActionButtonControl(button, () -> { }));
        return List.of(tick, cycle, slide, text, button);
    }

    private static final int[] BUILT_IN_WIDTHS = {30, 70, 130, 90, 70};

    private static OptionPage page(Option<?>... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder();
        for (Option<?> option : options) {
            group.add(option);
        }
        return new OptionPage(OptionIdentifier.create(MOD, "page"), TextComponent.literal("Page"), List.of(group.build()));
    }

    // The frame's option rows in order
    private static List<ControlElement<?>> rows(OptionPageFrame frame) {
        return frame.interactableChildren().filter(ControlElement.class::isInstance).<ControlElement<?>>map(child -> (ControlElement<?>) child).toList();
    }

    // The strings one frame of a bare row draws, which is only its name
    private static List<String> nameDrawnAt(int width, String name, int maxWidth) {
        Option<Boolean> option = option(name);
        bound(option, maxWidth);
        FakeDrawContext draw = new FakeDrawContext();
        Control.createElementFor(option, new Dim2i(0, 0, width, 18)).render(draw, 0, 0, 0f);
        return draw.strings;
    }

    // The strings one frame of a placeholder row of the given width draws in the fake language
    private static List<String> placeholderDrawnAt(int width) {
        List<String> strings = new ArrayList<>();
        withLogger(logger -> {
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option("Name"), new Dim2i(0, 0, width, 18));
            Language draw = new Language("Open");
            row.render(draw, 0, 0, 0f);
            strings.addAll(draw.strings);
        });
        return strings;
    }

    private static String signature(Method method) {
        return method.getReturnType().getSimpleName() + " " + method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).toList();
    }

    private static void assertParameterized(Type type, Class<?> raw, Type... arguments) {
        ParameterizedType parameterized = assertInstanceOf(ParameterizedType.class, type);
        assertSame(raw, parameterized.getRawType());
        assertArrayEquals(arguments, parameterized.getActualTypeArguments());
    }

    private static Set<TextFormattingStyle> styles(TextComponent component) {
        return component instanceof TextComponent.Styled styled ? styled.styles() : EnumSet.noneOf(TextFormattingStyle.class);
    }

    // Swaps the placeholder's static final logger for a mock while the body runs
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

    // The error toasts placeholders raised, oldest first
    private static List<ImpetusNotification> toasts() {
        return ImpetusNotifications.getVisible().stream().filter(t -> t.title().startsWith("Option failed: ")).toList();
    }

    private static void clearToasts() {
        Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable failure) throws E {
        throw (E) failure;
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrows(NullPointerException.class, call).getMessage());
    }

    private static void ise(String message, Executable call) {
        assertEquals(message, assertThrows(IllegalStateException.class, call).getMessage());
    }

    @Test
    void theAbstractShapeIsUnchangedSoControlsModsHaveCompiledStillLink() throws NoSuchMethodException {
        Class<?> type = Control.class;
        assertTrue(type.isInterface() && !type.isAnnotation());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(0, type.getInterfaces().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertEquals(0, Arrays.stream(type.getDeclaredFields()).filter(f -> !f.isSynthetic()).count());
        TypeVariable<?>[] parameters = type.getTypeParameters();
        assertEquals(1, parameters.length);
        assertEquals("T", parameters[0].getName());
        assertArrayEquals(new Type[]{Object.class}, parameters[0].getBounds());
        // A new abstract method would break every control already compiled against the interface
        Set<String> abstracts = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && Modifier.isAbstract(m.getModifiers()))
                .peek(m -> assertTrue(Modifier.isPublic(m.getModifiers()) && !m.isDefault(), m::toString))
                .map(ControlTest::signature)
                .collect(Collectors.toSet());
        assertEquals(Set.of("Option getOption[]", "ControlElement createElement[Dim2i]", "int getMaxWidth[]"), abstracts);
        assertParameterized(type.getMethod("getOption").getGenericReturnType(), Option.class, parameters[0]);
        assertParameterized(type.getMethod("createElement", Dim2i.class).getGenericReturnType(), ControlElement.class, parameters[0]);
        assertSame(int.class, type.getMethod("getMaxWidth").getGenericReturnType());
    }

    @Test
    void theAdditionsAreTwoDefaultsFivePublicStaticsAndTwoPrivateHelpers() throws NoSuchMethodException {
        Map<String, Integer> concrete = Arrays.stream(Control.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !Modifier.isAbstract(m.getModifiers()))
                .collect(Collectors.toMap(ControlTest::signature, Method::getModifiers));
        assertEquals(Set.of("int getMaxWidth[FontMetricsProvider]", "Class getValueType[]", "int columnWidth[Control, FontMetricsProvider, int]",
                "ControlElement createElementFor[Option, Dim2i]", "ControlElement createElementOrPlaceholder[Option, Dim2i]",
                "ControlElement placeholderFor[ControlElement, Throwable]", "void reportBroken[Option, String, Throwable]",
                "ControlElement placeholder[Option, Dim2i, Throwable]", "String describe[Option]"), concrete.keySet());
        assertTrue(Control.class.getMethod("getMaxWidth", FontMetricsProvider.class).isDefault());
        assertTrue(Control.class.getMethod("getValueType").isDefault());
        // Static, so no implementation can override the checks away
        for (String name : List.of("int columnWidth[Control, FontMetricsProvider, int]", "ControlElement createElementFor[Option, Dim2i]",
                "ControlElement createElementOrPlaceholder[Option, Dim2i]", "ControlElement placeholderFor[ControlElement, Throwable]",
                "void reportBroken[Option, String, Throwable]")) {
            int modifiers = concrete.get(name);
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers), name);
        }
        for (String name : List.of("String describe[Option]", "ControlElement placeholder[Option, Dim2i, Throwable]")) {
            int modifiers = concrete.get(name);
            assertTrue(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers), name);
        }
        // placeholderFor keeps the element's type
        Method replace = Control.class.getMethod("placeholderFor", ControlElement.class, Throwable.class);
        assertParameterized(replace.getGenericParameterTypes()[0], ControlElement.class, replace.getTypeParameters()[0]);
        assertParameterized(replace.getGenericReturnType(), ControlElement.class, replace.getTypeParameters()[0]);
        // Both creators tie the element's type to the option's through their own type parameter
        for (String name : List.of("createElementFor", "createElementOrPlaceholder")) {
            Method create = Control.class.getMethod(name, Option.class, Dim2i.class);
            TypeVariable<Method>[] own = create.getTypeParameters();
            assertEquals(1, own.length);
            assertArrayEquals(new Type[]{Object.class}, own[0].getBounds());
            assertParameterized(create.getGenericParameterTypes()[0], Option.class, own[0]);
            assertSame(Dim2i.class, create.getGenericParameterTypes()[1]);
            assertParameterized(create.getGenericReturnType(), ControlElement.class, own[0]);
        }
        // columnWidth takes a control of any type, and the value type is any class
        Type control = Control.class.getMethod("columnWidth", Control.class, FontMetricsProvider.class, int.class).getGenericParameterTypes()[0];
        WildcardType any = assertInstanceOf(WildcardType.class, assertInstanceOf(ParameterizedType.class, control).getActualTypeArguments()[0]);
        assertArrayEquals(new Type[]{Object.class}, any.getUpperBounds());
        assertEquals(0, any.getLowerBounds().length);
        Type valueType = Control.class.getMethod("getValueType").getGenericReturnType();
        assertInstanceOf(WildcardType.class, assertInstanceOf(ParameterizedType.class, valueType).getActualTypeArguments()[0]);
        for (Method method : Control.class.getDeclaredMethods()) {
            assertEquals(0, method.getExceptionTypes().length, method::toString);
        }
    }

    @Test
    void theHelperClassesArePackagePrivateAndFinal() {
        for (Class<?> type : List.of(ControlValueTypes.class, PlaceholderControlElement.class)) {
            int modifiers = type.getModifiers();
            assertFalse(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers), type::toString);
            assertTrue(Modifier.isFinal(modifiers), type::toString);
        }
        assertSame(ControlElement.class, PlaceholderControlElement.class.getSuperclass());
    }

    @Test
    void theDefaultFontWidthIsTheFixedWidthAndNeverMeasures() {
        FontMetricsProvider fonts = Mockito.mock(FontMetricsProvider.class);
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        int calls = 0;
        for (int width : new int[]{Integer.MIN_VALUE, -5, -1, 0, 1, 30, 70, 130, 4096, Integer.MAX_VALUE}) {
            probe.maxWidth = width;
            assertEquals(width, probe.getMaxWidth(fonts));
            // One fixed read per call and nothing else
            assertEquals(++calls, probe.widthReads);
        }
        verifyNoInteractions(fonts);
        assertEquals(0, probe.optionReads);
        assertEquals(0, probe.creations);
    }

    @Test
    void theDefaultFontWidthIgnoresAMissingFontSourceAsItNeverReadsIt() {
        Probe<Boolean> probe = new Probe<>(option("Name"), 42);
        assertEquals(42, probe.getMaxWidth(null));
        assertEquals(1, probe.widthReads);
    }

    @Test
    void everyBuiltInControlReportsOneWidthWithOrWithoutFontsAndFitsItsColumnToTheRow() {
        FontMetricsProvider fonts = Mockito.mock(FontMetricsProvider.class);
        List<Option<?>> options = builtIns();
        for (int i = 0; i < options.size(); i++) {
            Control<?> control = options.get(i).getControl();
            int width = BUILT_IN_WIDTHS[i];
            assertEquals(width, control.getMaxWidth(), control::toString);
            assertEquals(width, control.getMaxWidth(fonts), control::toString);
            assertEquals(width, Control.columnWidth(control, fonts, 200));
            assertEquals(width, Control.columnWidth(control, fonts, width));
            assertEquals(width - 1, Control.columnWidth(control, fonts, width - 1));
            assertEquals(Math.min(width, 50), Control.columnWidth(control, fonts, 50));
            assertEquals(0, Control.columnWidth(control, fonts, 0));
            assertEquals(0, Control.columnWidth(control, fonts, -1));
        }
        verifyNoInteractions(fonts);
    }

    @Test
    void everyBuiltInControlEditsItsOwnOption() {
        for (Option<?> option : builtIns()) {
            assertNotNull(option.getControl().getOption());
            assertSame(option, option.getControl().getOption());
        }
    }

    @Test
    void aFontSizedControlFollowsTheLanguageItIsMeasuredIn() {
        LabelSized control = new LabelSized(option("Name"));
        // "Open" is 24 pixels and "Ouvrir le menu" 84, each plus 18
        assertEquals(42, control.getMaxWidth(new Language("Open")));
        assertEquals(102, control.getMaxWidth(new Language("Ouvrir le menu")));
        assertEquals(42, Control.columnWidth(control, new Language("Open"), 200));
        assertEquals(102, Control.columnWidth(control, new Language("Ouvrir le menu"), 200));
        // A translation wider than the row is held to the row
        assertEquals(80, Control.columnWidth(control, new Language("Ouvrir le menu"), 80));
        assertEquals(200, Control.columnWidth(control, new Language("x".repeat(40)), 200));
        // Callers without fonts still get the fixed width
        assertEquals(70, control.getMaxWidth());
        assertEquals(6, control.measures);
    }

    @Test
    void aMaximumInsideTheRowIsUsedAsIs() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        for (int width : new int[]{1, 12, 30, 70, 90, 130, 199, 200}) {
            probe.maxWidth = width;
            assertEquals(width, Control.columnWidth(probe, fonts, 200));
        }
    }

    @Test
    void aZeroMaximumGivesAnEmptyColumnInAnyRow() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        for (int row : new int[]{Integer.MIN_VALUE, -1, 0, 1, 200, Integer.MAX_VALUE}) {
            assertEquals(0, Control.columnWidth(probe, fonts, row));
        }
    }

    @Test
    void aNegativeMaximumGivesAnEmptyColumnRatherThanARoomWiderThanTheRow() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        for (int width : new int[]{-1, -12, -13, -200, -1000, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
            probe.maxWidth = width;
            assertEquals(0, Control.columnWidth(probe, fonts, 200), () -> "maximum " + probe.maxWidth);
            assertEquals(0, Control.columnWidth(probe, fonts, Integer.MAX_VALUE));
        }
    }

    @Test
    void aMaximumWiderThanTheRowIsCutToTheRow() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        for (int width : new int[]{201, 202, 500, 4096, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            probe.maxWidth = width;
            assertEquals(200, Control.columnWidth(probe, fonts, 200));
            assertEquals(1, Control.columnWidth(probe, fonts, 1));
            assertEquals(0, Control.columnWidth(probe, fonts, 0));
        }
    }

    @Test
    void aRowWithNoWidthOrANegativeOneHasNoColumnWhateverTheMaximum() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        for (int row : new int[]{0, -1, -18, -200, Integer.MIN_VALUE + 1, Integer.MIN_VALUE}) {
            for (int width : new int[]{Integer.MIN_VALUE, -1, 0, 1, 70, 200, Integer.MAX_VALUE}) {
                probe.maxWidth = width;
                assertEquals(0, Control.columnWidth(probe, fonts, row), () -> "row " + row + " maximum " + probe.maxWidth);
            }
        }
    }

    @Test
    void theEndsOfTheIntRangeNeitherOverflowNorThrow() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, Control.columnWidth(probe, fonts, Integer.MAX_VALUE));
        assertEquals(0, Control.columnWidth(probe, fonts, Integer.MIN_VALUE));
        assertEquals(0, Control.columnWidth(probe, fonts, 0));
        probe.maxWidth = Integer.MIN_VALUE;
        assertEquals(0, Control.columnWidth(probe, fonts, Integer.MAX_VALUE));
        assertEquals(0, Control.columnWidth(probe, fonts, Integer.MIN_VALUE));
        probe.maxWidth = Integer.MAX_VALUE - 1;
        assertEquals(Integer.MAX_VALUE - 1, Control.columnWidth(probe, fonts, Integer.MAX_VALUE));
    }

    @Test
    void columnWidthMatchesAClampWorkedOutInLongsOverAGridOfMaximumsAndRows() {
        FakeDrawContext fonts = new FakeDrawContext();
        Probe<Boolean> probe = new Probe<>(option("Name"), 0);
        List<Integer> maximums = new ArrayList<>(List.of(Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        for (int width = -300; width <= 300; width += 7) {
            maximums.add(width);
        }
        List<Integer> rows = new ArrayList<>(List.of(Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        for (int row = -50; row <= 300; row += 3) {
            rows.add(row);
        }
        for (int maximum : maximums) {
            probe.maxWidth = maximum;
            for (int row : rows) {
                long expected = Math.max(0L, Math.min((long) maximum, Math.max(0L, row)));
                int column = Control.columnWidth(probe, fonts, row);
                assertEquals(expected, column, () -> "row " + row + " maximum " + maximum);
                assertTrue(column >= 0 && column <= Math.max(0, row));
                if (maximum >= 0 && maximum <= row) {
                    assertEquals(maximum, column);
                }
            }
        }
        assertEquals(maximums.size() * rows.size(), probe.widthReads);
    }

    @Test
    void columnWidthAsksForTheWidthOnceAndTouchesNothingElse() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        clearInvocations(option);
        FontMetricsProvider fonts = Mockito.mock(FontMetricsProvider.class);
        for (int i = 1; i <= 5; i++) {
            assertEquals(70, Control.columnWidth(probe, fonts, 200));
            assertEquals(i, probe.widthReads);
        }
        assertEquals(0, probe.optionReads);
        assertEquals(0, probe.typeReads);
        assertEquals(0, probe.creations);
        verifyNoInteractions(option, fonts);
    }

    @Test
    void columnWidthUsesTheFontAwareWidthOverTheFixedOneAndPassesTheSameFonts() {
        AtomicReference<FontMetricsProvider> seen = new AtomicReference<>();
        Control<Boolean> control = new Control<Boolean>() {
            @Override
            public Option<Boolean> getOption() {
                return null;
            }

            @Override
            public ControlElement<Boolean> createElement(Dim2i dim) {
                return null;
            }

            @Override
            public int getMaxWidth() {
                return 70;
            }

            @Override
            public int getMaxWidth(FontMetricsProvider fonts) {
                seen.set(fonts);
                return 20;
            }
        };
        FakeDrawContext fonts = new FakeDrawContext();
        assertEquals(20, Control.columnWidth(control, fonts, 200));
        assertSame(fonts, seen.get());
        assertEquals(10, Control.columnWidth(control, fonts, 10));
    }

    @Test
    void columnWidthRefusesAMissingControlBeforeAMissingFontSource() {
        Probe<Boolean> probe = new Probe<>(option("Name"), 70);
        FakeDrawContext fonts = new FakeDrawContext();
        npe("Control must not be null", () -> Control.columnWidth(null, fonts, 200));
        npe("Fonts must not be null", () -> Control.columnWidth(probe, null, 200));
        npe("Control must not be null", () -> Control.columnWidth(null, null, 200));
        // Refused before the width is asked for, so an override never sees null
        assertEquals(0, probe.widthReads);
    }

    @Test
    void aWidthThatThrowsReachesTheCallerUnchanged() {
        IllegalStateException broken = new IllegalStateException("broken");
        Control<Boolean> control = new Control<Boolean>() {
            @Override
            public Option<Boolean> getOption() {
                return null;
            }

            @Override
            public ControlElement<Boolean> createElement(Dim2i dim) {
                return null;
            }

            @Override
            public int getMaxWidth() {
                throw broken;
            }
        };
        // Through the default font-aware width, so a failing fixed width is not swallowed on the way
        assertSame(broken, assertThrows(IllegalStateException.class, () -> Control.columnWidth(control, new FakeDrawContext(), 200)));
        assertSame(broken, assertThrows(IllegalStateException.class, () -> control.getMaxWidth(new FakeDrawContext())));
    }

    @Test
    void theBuiltInControlsDeclareTheTypesTheyEdit() {
        assertSame(Boolean.class, new TickBoxControl(option("Tick")).getValueType());
        assertSame(Integer.class, new SliderControl(option("Slide"), 0, 1, 1, ControlValueFormatter.number()).getValueType());
        assertSame(String.class, new ReadOnlyStringControl(option("Text")).getValueType());
        assertSame(Boolean.class, new ActionButtonControl(option("Button"), () -> { }).getValueType());
        // The cycler leaves its argument open, so it reads the type off its values instead
        assertSame(Object.class, ControlValueTypes.declared(CyclingControl.class));
        assertSame(Mode.class, new CyclingControl<>(ControlTest.<Mode>option("Cycle"), Mode.class).getValueType());
        assertSame(Mode.class, new CyclingControl<>(ControlTest.<Mode>option("Cycle"), new Mode[]{Mode.B}, new TextComponent[]{TextComponent.literal("b")}).getValueType());
        assertSame(Object.class, new CyclingControl<>(ControlTest.option("Cycle"), new Object[]{"a"}, new TextComponent[]{TextComponent.literal("a")}).getValueType());
    }

    @Test
    void anOptionTakesItsValueTypeFromItsIdUnlessTheIdIsMissingOrUntyped() {
        Option<Boolean> option = option("Name");
        doCallRealMethod().when(option).getValueType();
        assertSame(Object.class, option.getValueType());
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.EMPTY);
        assertSame(Object.class, option.getValueType());
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "typed_by_id", boolean.class));
        assertSame(boolean.class, option.getValueType());
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "boxed_by_id", Boolean.class));
        assertSame(Boolean.class, option.getValueType());
        // Untyped ids, which pages and some options carry, say nothing about the value
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.create(MOD, "untyped_id"));
        assertSame(Object.class, option.getValueType());
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.create(MOD, "void_typed_id", Void.class));
        assertSame(Object.class, option.getValueType());
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.create(MOD, "class_less_id", null));
        assertSame(Object.class, option.getValueType());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aControlBuiltOverItsOwnOptionOfAnotherTypeIsCaughtBeforeItsFirstFrame() {
        AtomicInteger number = new AtomicInteger(7);
        OptionStorage<AtomicInteger> numbers = () -> number;
        // Only a raw cast compiles this, and erasure lets it run
        OptionImpl<AtomicInteger, Integer> option = OptionImpl.createBuilder(int.class, numbers)
                .setId(OptionIdentifier.create(MOD, "raw_tick", int.class))
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> (Control) new TickBoxControl((Option) o))
                .build();
        // The pairing holds, which is why the value types are compared too
        assertSame(option, option.getControl().getOption());
        ise("Control of option " + MOD + ":raw_tick edits java.lang.Boolean values but the option holds java.lang.Integer", () -> Control.createElementFor(option, DIM));
        assertEquals(7, number.get());
        assertFalse(option.hasChanged());
        // Without the check, the first frame would fail reading the number as a boolean
        ControlElement<Integer> unchecked = option.getControl().createElement(DIM);
        assertThrows(ClassCastException.class, () -> unchecked.render(new FakeDrawContext(), 0, 0, 0f));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slidersAndCyclersOverTheWrongTypeAreCaughtTheSameWay() {
        AtomicBoolean flag = new AtomicBoolean(true);
        OptionStorage<AtomicBoolean> flags = () -> flag;
        OptionImpl<AtomicBoolean, Boolean> sliding = OptionImpl.createBuilder(boolean.class, flags)
                .setId(OptionIdentifier.create(MOD, "raw_slider", boolean.class))
                .setBinding(AtomicBoolean::set, AtomicBoolean::get)
                .setControl(o -> (Control) new SliderControl((Option) o, 0, 10, 1, ControlValueFormatter.number()))
                .build();
        ise("Control of option " + MOD + ":raw_slider edits java.lang.Integer values but the option holds java.lang.Boolean", () -> Control.createElementFor(sliding, DIM));
        AtomicReference<Other> other = new AtomicReference<>(Other.X);
        OptionStorage<AtomicReference<Other>> others = () -> other;
        OptionImpl<AtomicReference<Other>, Other> cycling = OptionImpl.createBuilder(Other.class, others)
                .setId(OptionIdentifier.create(MOD, "raw_cycler", Other.class))
                .setBinding(AtomicReference::set, AtomicReference::get)
                .setControl(o -> (Control) new CyclingControl<>((Option) o, Mode.class))
                .build();
        ise("Control of option " + MOD + ":raw_cycler edits " + Mode.class.getName() + " values but the option holds " + Other.class.getName(),
                () -> Control.createElementFor(cycling, DIM));
        assertTrue(flag.get());
        assertSame(Other.X, other.get());
    }

    @Test
    void eachValueTypeIsAskedOnceAndCheckedBeforeTheElementIsMade() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 30);
        probe.valueType = Boolean.class;
        doReturn(Boolean.class).when(option).getValueType();
        clearInvocations(option);
        Control.createElementFor(option, DIM);
        assertEquals(1, probe.typeReads);
        verify(option).getControl();
        verify(option).getValueType();
        verifyNoMoreInteractions(option);
        // A mismatch stops before the element is made or a value is read
        probe.valueType = Integer.class;
        ise("Control of option Name edits java.lang.Integer values but the option holds java.lang.Boolean", () -> Control.createElementFor(option, DIM));
        assertEquals(1, probe.creations);
        verify(option, never()).getValue();
        // A primitive on the option's side is named as it was given
        doReturn(boolean.class).when(option).getValueType();
        ise("Control of option Name edits java.lang.Integer values but the option holds boolean", () -> Control.createElementFor(option, DIM));
    }

    @Test
    void primitiveRelatedAndUnknownValueTypesAllPass() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 30);
        Class<?>[][] pairs = {{Boolean.class, boolean.class}, {Boolean.class, Boolean.class}, {Number.class, Integer.class}, {Integer.class, Number.class},
                {Object.class, Boolean.class}, {Boolean.class, Object.class}, {null, Boolean.class}, {Boolean.class, null}, {null, null}};
        for (Class<?>[] pair : pairs) {
            probe.valueType = pair[0];
            doReturn(pair[1]).when(option).getValueType();
            assertSame(option, Control.createElementFor(option, DIM).getOption(), () -> Arrays.toString(pair));
        }
        assertEquals(pairs.length, probe.creations);
    }

    @Test
    void createElementForHandsBackExactlyTheElementTheControlMade() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        List<ControlElement<Boolean>> made = new ArrayList<>();
        probe.factory = dim -> {
            ControlElement<Boolean> element = new ControlElement<>(option, dim);
            made.add(element);
            return element;
        };
        ControlElement<Boolean> element = Control.createElementFor(option, DIM);
        assertEquals(1, made.size());
        assertSame(made.getFirst(), element);
        assertSame(option, element.getOption());
        assertSame(DIM, element.getDimensions());
    }

    @Test
    void createElementForAsksTheOptionTheControlAndTheElementOnceEachAndNothingMore() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        clearInvocations(option);
        Control.createElementFor(option, DIM);
        verify(option).getControl();
        verify(option).getValueType();
        // No value, name, id or availability read, so making a row costs nothing beyond the element itself
        verifyNoMoreInteractions(option);
        assertEquals(1, probe.optionReads);
        assertEquals(1, probe.typeReads);
        assertEquals(1, probe.creations);
        assertEquals(0, probe.widthReads);
        for (int i = 2; i <= 10; i++) {
            Control.createElementFor(option, DIM.withY(DIM.y() + i * 18));
            assertEquals(i, probe.optionReads);
            assertEquals(i, probe.typeReads);
            assertEquals(i, probe.creations);
        }
        assertEquals(0, probe.widthReads);
    }

    @Test
    void missingArgumentsAreRefusedInParameterOrderBeforeTheOptionIsAsked() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        clearInvocations(option);
        for (Function<Option<Boolean>, Function<Dim2i, ?>> create : List.<Function<Option<Boolean>, Function<Dim2i, ?>>>of(
                o -> d -> Control.createElementFor(o, d), o -> d -> Control.createElementOrPlaceholder(o, d))) {
            npe("Option must not be null", () -> create.apply(null).apply(DIM));
            npe("Option must not be null", () -> create.apply(null).apply(null));
            npe("Dimensions must not be null", () -> create.apply(option).apply(null));
        }
        verifyNoInteractions(option);
        assertEquals(0, probe.optionReads);
        assertEquals(0, probe.creations);
    }

    @Test
    void anOptionWithNoControlIsRefusedNamingTheOption() {
        Option<Boolean> option = option("Name");
        ise("Option Name has no control", () -> Control.createElementFor(option, DIM));
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "no_control", Boolean.class));
        ise("Option " + MOD + ":no_control has no control", () -> Control.createElementFor(option, DIM));
    }

    @Test
    void failuresNameTheOptionByItsIdWhenPresentAndByItsNameOtherwise() {
        Option<Boolean> option = option("Shown name");
        when(option.getControl()).thenReturn(null);
        ise("Option Shown name has no control", () -> Control.createElementFor(option, DIM));
        // The empty placeholder id names nothing, so the name is used
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.EMPTY);
        ise("Option Shown name has no control", () -> Control.createElementFor(option, DIM));
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "described", Boolean.class));
        ise("Option " + MOD + ":described has no control", () -> Control.createElementFor(option, DIM));
        // A translated name is named by its key, and an option with neither still reports
        when(option.getId()).thenReturn(null);
        when(option.getName()).thenReturn(TextComponent.translatable("mod.options.key.name"));
        ise("Option mod.options.key.name has no control", () -> Control.createElementFor(option, DIM));
        when(option.getName()).thenReturn(null);
        ise("Option null has no control", () -> Control.createElementFor(option, DIM));
    }

    @Test
    void anOptionThatFailsToDescribeItselfIsNamedByItsClass() {
        Option<Boolean> option = option("Name");
        when(option.getId()).thenThrow(new IllegalStateException("no id"));
        ise("Option " + option.getClass().getName() + " has no control", () -> Control.createElementFor(option, DIM));
        Option<Boolean> nameless = option("Name");
        when(nameless.getName()).thenThrow(new UnsupportedOperationException("no name"));
        ise("Option " + nameless.getClass().getName() + " has no control", () -> Control.createElementFor(nameless, DIM));
    }

    @Test
    void theOptionIsOnlyDescribedWhenSomethingFails() {
        Option<Boolean> option = option("Name");
        bound(option, 70);
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "quiet", Boolean.class));
        clearInvocations(option);
        Control.createElementFor(option, DIM);
        verify(option, never()).getId();
        verify(option, never()).getName();
    }

    @Test
    void aControlMadeForAnotherOptionIsRefusedBeforeItMakesAnything() {
        Option<Boolean> owner = option("Owner");
        Option<Boolean> borrower = option("Borrower");
        Probe<Boolean> probe = bound(owner, 70);
        when(borrower.getControl()).thenReturn(probe);
        ise("Control of option Borrower edits a different option", () -> Control.createElementFor(borrower, DIM));
        assertEquals(0, probe.creations);
        assertEquals(0, probe.typeReads);
        // Its own option still gets an element
        assertSame(owner, Control.createElementFor(owner, DIM).getOption());
    }

    @Test
    void aControlWithNoOptionIsRefusedAsEditingADifferentOne() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        probe.option = null;
        ise("Control of option Name edits a different option", () -> Control.createElementFor(option, DIM));
        assertEquals(0, probe.creations);
    }

    @Test
    void aSharedControlInstanceWorksOnlyForTheOptionItWasMadeFor() {
        Option<Boolean> first = valued("First", true);
        Option<Boolean> second = valued("Second", false);
        TickBoxControl shared = new TickBoxControl(first);
        when(first.getControl()).thenReturn(shared);
        when(second.getControl()).thenReturn(shared);
        assertSame(first, Control.createElementFor(first, DIM).getOption());
        ise("Control of option Second edits a different option", () -> Control.createElementFor(second, DIM));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aControlOfAnotherOptionsValueTypeIsRefusedBeforeAnyValueIsRead() {
        Option<Boolean> flags = valued("Flags", true);
        Option<Integer> numbers = valued("Numbers", 7);
        TickBoxControl tick = new TickBoxControl(flags);
        when(flags.getControl()).thenReturn(tick);
        // The raw cast compiles and erasure lets it run, so the pairing shows the mismatch
        when(numbers.getControl()).thenReturn((Control<Integer>) (Control<?>) tick);
        clearInvocations(flags, numbers);
        ise("Control of option Numbers edits a different option", () -> Control.createElementFor(numbers, DIM));
        verify(numbers, never()).getValue();
        verify(flags, never()).getValue();
        // A slider over a boolean option is caught the same way, before its element would read the value as an int
        Option<Integer> sliding = valued("Sliding", 3);
        SliderControl slider = new SliderControl(sliding, 0, 10, 1, ControlValueFormatter.number());
        when(flags.getControl()).thenReturn((Control<Boolean>) (Control<?>) slider);
        clearInvocations(flags, sliding);
        ise("Control of option Flags edits a different option", () -> Control.createElementFor(flags, DIM));
        verify(flags, never()).getValue();
        verify(sliding, never()).getValue();
    }

    @Test
    void aControlThatMakesNoElementIsRefused() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        probe.factory = dim -> null;
        ise("Control of option Name created no element", () -> Control.createElementFor(option, DIM));
        assertEquals(1, probe.creations);
    }

    @Test
    void anElementForAnotherOptionIsRefused() {
        Option<Boolean> option = option("Name");
        Option<Boolean> other = option("Other");
        Probe<Boolean> probe = bound(option, 70);
        probe.factory = dim -> new ControlElement<>(other, dim);
        ise("Element of option Name edits a different option", () -> Control.createElementFor(option, DIM));
        // A row with no option at all is refused by its own constructor before the check is reached
        probe.factory = dim -> new ControlElement<>(null, dim);
        npe("Option must not be null", () -> Control.createElementFor(option, DIM));
    }

    @Test
    void anElementAtOtherBoundsIsRefusedNamingBoth() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        probe.factory = dim -> new ControlElement<>(option, dim.withX(dim.x() + 1));
        ise("Element of option Name has bounds Dim2i[x=11, y=20, width=200, height=18] instead of Dim2i[x=10, y=20, width=200, height=18]",
                () -> Control.createElementFor(option, DIM));
        // Every one of the four sides counts
        for (Function<Dim2i, Dim2i> moved : List.<Function<Dim2i, Dim2i>>of(d -> d.withY(d.y() - 1), d -> d.withWidth(d.width() + 1), d -> d.withHeight(0))) {
            probe.factory = dim -> new ControlElement<>(option, moved.apply(dim));
            assertThrows(IllegalStateException.class, () -> Control.createElementFor(option, DIM));
        }
    }

    @Test
    void anElementThatReportsNoBoundsIsRefused() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        probe.factory = dim -> new ControlElement<>(option, dim) {
            @Override
            public Dim2i getDimensions() {
                return null;
            }
        };
        ise("Element of option Name has bounds null instead of Dim2i[x=10, y=20, width=200, height=18]", () -> Control.createElementFor(option, DIM));
    }

    @Test
    void equalBoundsInAnotherInstanceAreAccepted() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        probe.factory = dim -> new ControlElement<>(option, new Dim2i(dim.x(), dim.y(), dim.width(), dim.height()));
        ControlElement<Boolean> element = Control.createElementFor(option, DIM);
        assertNotSame(DIM, element.getDimensions());
        assertEquals(DIM, element.getDimensions());
    }

    @Test
    void aControlThatKeepsOneElementIsCaughtTheFirstTimeTheRowMoves() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        List<ControlElement<Boolean>> kept = new ArrayList<>();
        probe.factory = dim -> {
            if (kept.isEmpty()) {
                kept.add(new ControlElement<>(option, dim));
            }
            return kept.getFirst();
        };
        ControlElement<Boolean> first = Control.createElementFor(option, DIM);
        // Handing the same element back at the same bounds is harmless, so only stale bounds are caught
        assertSame(first, Control.createElementFor(option, new Dim2i(10, 20, 200, 18)));
        ise("Element of option Name has bounds Dim2i[x=10, y=20, width=200, height=18] instead of Dim2i[x=10, y=38, width=200, height=18]",
                () -> Control.createElementFor(option, DIM.withY(38)));
        ise("Element of option Name has bounds Dim2i[x=10, y=20, width=200, height=18] instead of Dim2i[x=10, y=20, width=320, height=18]",
                () -> Control.createElementFor(option, DIM.withWidth(320)));
    }

    @Test
    void everyBuiltInControlPassesTheChecksWithAFreshElementPerCall() {
        for (Option<?> option : builtIns()) {
            Control<?> control = option.getControl();
            ControlElement<?> first = Control.createElementFor(option, DIM);
            ControlElement<?> again = Control.createElementFor(option, DIM);
            ControlElement<?> moved = Control.createElementFor(option, DIM.withY(38));
            assertNotSame(first, again, control::toString);
            assertNotSame(again, moved, control::toString);
            for (ControlElement<?> element : List.of(first, again, moved)) {
                assertSame(option, element.getOption());
                // The control's own nested element, not a bare row
                assertSame(control.getClass(), element.getClass().getEnclosingClass());
            }
            assertSame(DIM, first.getDimensions());
            assertEquals(DIM.withY(38), moved.getDimensions());
        }
    }

    @Test
    void optionsBuiltByOptionImplPassTheChecksAsThePagesBuildThem() {
        AtomicBoolean flag = new AtomicBoolean(true);
        OptionStorage<AtomicBoolean> flags = () -> flag;
        OptionImpl<AtomicBoolean, Boolean> tick = OptionImpl.createBuilder(boolean.class, flags)
                .setId(OptionIdentifier.create(MOD, "impl_tick", boolean.class))
                .setBinding(AtomicBoolean::set, AtomicBoolean::get)
                .setControl(TickBoxControl::new)
                .build();
        AtomicInteger number = new AtomicInteger(4);
        OptionStorage<AtomicInteger> numbers = () -> number;
        OptionImpl<AtomicInteger, Integer> slide = OptionImpl.createBuilder(int.class, numbers)
                .setId(OptionIdentifier.create(MOD, "impl_slide", int.class))
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> new SliderControl(o, 0, 8, 2, ControlValueFormatter.number()))
                .build();
        OptionImpl<AtomicBoolean, Boolean> button = OptionImpl.createBuilder(boolean.class, flags)
                .setId(OptionIdentifier.create(MOD, "impl_button", boolean.class))
                .setBinding(AtomicBoolean::set, AtomicBoolean::get)
                .setControl(o -> new ActionButtonControl(o, () -> { }))
                .build();
        // Both sides boxed to the same class, so the type check is a single comparison
        assertSame(tick.getValueType(), tick.getControl().getValueType());
        assertSame(slide.getValueType(), slide.getControl().getValueType());
        assertSame(button.getValueType(), button.getControl().getValueType());
        FakeDrawContext fonts = new FakeDrawContext();
        assertSame(tick, Control.createElementFor(tick, DIM).getOption());
        assertSame(slide, Control.createElementFor(slide, DIM).getOption());
        assertSame(button, Control.createElementFor(button, DIM).getOption());
        assertEquals(30, Control.columnWidth(tick.getControl(), fonts, 200));
        assertEquals(130, Control.columnWidth(slide.getControl(), fonts, 200));
        assertEquals(100, Control.columnWidth(slide.getControl(), fonts, 100));
        assertEquals(70, Control.columnWidth(button.getControl(), fonts, 200));
        // Making elements writes nothing back
        assertTrue(flag.get());
        assertEquals(4, number.get());
        assertFalse(tick.hasChanged() || slide.hasChanged() || button.hasChanged());
    }

    @Test
    void createElementForTakesTheWildcardOptionsAPageHolds() {
        List<Option<?>> options = builtIns();
        for (Option<?> option : options) {
            ControlElement<?> element = Control.createElementFor(option, DIM);
            assertSame(option, element.getOption());
        }
    }

    @Test
    void emptyNegativeAndExtremeRowsArePassedThroughForTheRowToHandle() {
        Option<Boolean> option = option("Name");
        bound(option, 70);
        for (Dim2i dim : List.of(new Dim2i(0, 0, 0, 0), new Dim2i(-5, -5, -1, -1), new Dim2i(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE))) {
            assertSame(dim, Control.createElementFor(option, dim).getDimensions());
            assertSame(dim, Control.createElementOrPlaceholder(option, dim).getDimensions());
        }
    }

    @Test
    void failuresInsideTheOptionOrTheControlReachTheCallerUnchanged() {
        Option<Boolean> option = option("Name");
        Probe<Boolean> probe = bound(option, 70);
        IllegalArgumentException made = new IllegalArgumentException("bad bounds");
        probe.factory = dim -> {
            throw made;
        };
        assertSame(made, assertThrows(IllegalArgumentException.class, () -> Control.createElementFor(option, DIM)));
        // Its own IllegalStateException is not mistaken for a broken contract
        IllegalStateException asked = new IllegalStateException("asked");
        when(option.getControl()).thenReturn(new Control<Boolean>() {
            @Override
            public Option<Boolean> getOption() {
                throw asked;
            }

            @Override
            public ControlElement<Boolean> createElement(Dim2i dim) {
                throw new AssertionError("made after a failed option read");
            }

            @Override
            public int getMaxWidth() {
                return 0;
            }
        });
        assertSame(asked, assertThrows(IllegalStateException.class, () -> Control.createElementFor(option, DIM)));
        UnsupportedOperationException lookup = new UnsupportedOperationException("no lookup");
        when(option.getControl()).thenThrow(lookup);
        assertSame(lookup, assertThrows(UnsupportedOperationException.class, () -> Control.createElementFor(option, DIM)));
    }

    @Test
    void aWorkingControlGetsItsOwnElementFromThePlaceholderPathAndNothingIsReported() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            Probe<Boolean> probe = bound(option, 30);
            List<ControlElement<Boolean>> made = new ArrayList<>();
            probe.factory = dim -> {
                ControlElement<Boolean> element = new ControlElement<>(option, dim);
                made.add(element);
                return element;
            };
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
            assertSame(made.getFirst(), row);
            assertFalse(row instanceof PlaceholderControlElement<?>);
            verifyNoInteractions(logger);
            assertEquals(List.of(), toasts());
            for (Option<?> builtIn : builtIns()) {
                assertFalse(Control.createElementOrPlaceholder(builtIn, DIM) instanceof PlaceholderControlElement<?>);
            }
            verifyNoInteractions(logger);
        });
    }

    @Test
    void everyKindOfBrokenControlGetsAPlaceholderRowAndAReport() {
        List<Consumer<Option<Boolean>>> breaks = List.of(
                option -> when(option.getControl()).thenReturn(null),
                option -> bound(option, 30).option = option("Owner"),
                option -> {
                    bound(option, 30).valueType = Integer.class;
                    doReturn(Boolean.class).when(option).getValueType();
                },
                option -> bound(option, 30).factory = dim -> null,
                option -> bound(option, 30).factory = dim -> new ControlElement<>(null, dim),
                option -> bound(option, 30).factory = dim -> new ControlElement<>(option, dim.withX(0)),
                option -> bound(option, 30).factory = dim -> {
                    throw new IllegalArgumentException("bad");
                },
                option -> bound(option, 30).factory = dim -> {
                    throw new NoClassDefFoundError("missing/ModClass");
                },
                option -> bound(option, 30).factory = dim -> {
                    throw new AssertionError("asserted");
                },
                option -> bound(option, 30).factory = dim -> {
                    throw ControlTest.<RuntimeException>sneak(new IOException("checked"));
                },
                option -> when(option.getControl()).thenThrow(new UnsupportedOperationException("no lookup")));
        withLogger(logger -> {
            for (int i = 0; i < breaks.size(); i++) {
                Option<Boolean> option = option("Broken " + i);
                breaks.get(i).accept(option);
                ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
                assertInstanceOf(PlaceholderControlElement.class, row, "break " + i);
                assertSame(option, row.getOption());
                assertSame(DIM, row.getDimensions());
                assertFalse(row.canFocus());
                verify(logger).error(eq(FAILURE_LOG), eq("Broken " + i), any(Throwable.class));
                assertEquals("Option failed: Broken " + i, toasts().getLast().title());
                assertFalse(Thread.currentThread().isInterrupted());
            }
        });
    }

    @Test
    void theReportCarriesTheFailureAndTheFirstLineOfItsMessage() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            IllegalStateException failure = new IllegalStateException("first\nsecond\r\nthird");
            bound(option, 30).factory = dim -> {
                throw failure;
            };
            Control.createElementOrPlaceholder(option, DIM);
            verify(logger).error(eq(FAILURE_LOG), eq("Name"), same(failure));
            ImpetusNotification toast = toasts().getFirst();
            assertEquals(ImpetusNotification.Level.ERROR, toast.level());
            assertEquals("Option failed: Name", toast.title());
            assertEquals(List.of("java.lang.IllegalStateException: first"), toast.lines());
            // A failed check reports its own message, and an option with an id is named by it
            Option<Boolean> named = option("Named");
            when(named.getId()).thenReturn(OptionIdentifier.create(MOD, "named_failure", Boolean.class));
            bound(named, 30).factory = dim -> null;
            Control.createElementOrPlaceholder(named, DIM);
            verify(logger).error(eq(FAILURE_LOG), eq(MOD + ":named_failure"), isA(IllegalStateException.class));
            assertEquals("Option failed: " + MOD + ":named_failure", toasts().getLast().title());
            assertEquals(List.of("java.lang.IllegalStateException: Control of option " + MOD + ":named_failure created no element"), toasts().getLast().lines());
            // A failure without a message still reports
            Option<Boolean> silent = option("Silent");
            bound(silent, 30).factory = dim -> {
                throw new IllegalStateException();
            };
            Control.createElementOrPlaceholder(silent, DIM);
            assertEquals(List.of("java.lang.IllegalStateException"), toasts().getLast().lines());
        });
    }

    @Test
    void eachOptionIsReportedOnceHoweverOftenThePageIsRebuilt() {
        withLogger(logger -> {
            Option<Boolean> option = option("Once");
            bound(option, 30).factory = dim -> null;
            List<ControlElement<Boolean>> rows = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                rows.add(Control.createElementOrPlaceholder(option, DIM.withY(i * 18)));
            }
            // A fresh placeholder at each layout's bounds, and a single report
            assertEquals(5, rows.stream().distinct().count());
            assertEquals(DIM.withY(72), rows.getLast().getDimensions());
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Once"), any(Throwable.class));
            assertEquals(1, toasts().size());
            Option<Boolean> another = option("Another");
            bound(another, 30).factory = dim -> null;
            Control.createElementOrPlaceholder(another, DIM);
            verify(logger).error(eq(FAILURE_LOG), eq("Another"), any(Throwable.class));
            assertEquals(2, toasts().size());
        });
    }

    @Test
    void anOptionThatFailsToDescribeItselfIsStillReportedByItsClass() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            when(option.getId()).thenThrow(new IllegalStateException("no id"));
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
            assertInstanceOf(PlaceholderControlElement.class, row);
            verify(logger).error(eq(FAILURE_LOG), eq(option.getClass().getName()), isA(IllegalStateException.class));
        });
    }

    @Test
    void fatalErrorsAreNeverTurnedIntoPlaceholders() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            Probe<Boolean> probe = bound(option, 30);
            for (VirtualMachineError fatal : List.of(new StackOverflowError(), new OutOfMemoryError(), new InternalError("vm"))) {
                probe.factory = dim -> {
                    throw fatal;
                };
                assertSame(fatal, assertThrows(VirtualMachineError.class, () -> Control.createElementOrPlaceholder(option, DIM)));
            }
            verifyNoInteractions(logger);
            assertEquals(List.of(), toasts());
        });
    }

    @Test
    void anInterruptionBecomesAPlaceholderWithTheInterruptFlagRestored() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            InterruptedException interrupted = new InterruptedException("stop");
            bound(option, 30).factory = dim -> {
                throw ControlTest.<RuntimeException>sneak(interrupted);
            };
            try {
                assertInstanceOf(PlaceholderControlElement.class, Control.createElementOrPlaceholder(option, DIM));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            verify(logger).error(eq(FAILURE_LOG), eq("Name"), same(interrupted));
        });
    }

    @Test
    void thePlaceholderGreysTheNameAndShowsTheErrorLabelInTheColumn() {
        withLogger(logger -> {
            Option<Boolean> option = option("Shadows");
            when(option.hasChanged()).thenReturn(true);
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
            clearInvocations(option);
            Language draw = new Language("Open");
            row.render(draw, 0, 0, 0f);
            assertEquals(List.of("Shadows", "Error"), draw.strings);
            // Struck through and grey with no change marker, though the option says it is available and changed
            assertEquals(EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(draw.drawn.getFirst()));
            assertTrue(draw.calls.stream().anyMatch(c -> c.startsWith("text(Shadows,16,25,")), draw.calls::toString);
            // "Error" is 30 pixels, right-aligned 6 in from the 210 edge in red, on the text line of the 20..38 row
            assertEquals(TextComponent.translatable(PLACEHOLDER_KEY), draw.drawn.get(1));
            assertTrue(draw.calls.contains("text(Error,174,25,ffff5555,false)"), draw.calls::toString);
            // Drawn without asking the option anything, its name having been read when the placeholder was made
            verify(option, never()).getName();
            verify(option, never()).isAvailable();
            verify(option, never()).hasChanged();
            verify(option, never()).getControl();
            verify(option, never()).getValue();
        });
    }

    @Test
    void thePlaceholderMeasuresItsLabelOnceAndLeavesTheNameTheRestOfTheRow() {
        withLogger(logger -> {
            String name = "Translucency sorting distance";
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option(name), DIM);
            Language draw = new Language("Open");
            for (int frame = 0; frame < 3; frame++) {
                row.render(draw, 0, 0, 0f);
            }
            assertEquals(1, draw.measured.get(PLACEHOLDER_KEY));
            // The 42 pixel column leaves 146 for the name: 21 characters before the ellipsis
            assertEquals(List.of("Translucency sorting...", "Error"), draw.strings.subList(0, 2));
            assertEquals(6, draw.strings.size());
        });
    }

    @Test
    void aPlaceholderInANarrowRowGivesWayFirstWithItsNameThenWithItsLabel() {
        assertEquals(List.of("Name", "Error"), placeholderDrawnAt(80));
        // 42 of 60 leaves 6, room for no name
        assertEquals(List.of("Error"), placeholderDrawnAt(60));
        // The column is held to the row, and the label needs its 30 plus the 6 inset
        assertEquals(List.of("Error"), placeholderDrawnAt(36));
        assertEquals(List.of(), placeholderDrawnAt(35));
        for (int width : new int[]{30, 1, 0, -5, Integer.MIN_VALUE}) {
            assertEquals(List.of(), placeholderDrawnAt(width), () -> "width " + width);
        }
    }

    @Test
    void thePlaceholderTakesNoInputAndTabPassesOverIt() {
        withLogger(logger -> {
            Option<Boolean> option = option("Name");
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
            FakeInteractionContext context = new FakeInteractionContext();
            assertFalse(row.canFocus());
            assertTrue(row.isMouseOver(100, 25));
            assertFalse(row.mouseClicked(context, 100, 25, 0));
            row.setFocused(true);
            assertFalse(row.keyTyped(context, '\r', Keys.RETURN));
            assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
            assertFalse(row.mouseDragged(context, 100, 25, 0, 1, 0));
            assertEquals(0, context.clicks);
            // Hovered and focused it still draws, with the focus outline
            FakeDrawContext draw = new FakeDrawContext();
            row.render(draw, 100, 25, 0f);
            assertTrue(draw.calls.size() > 4, draw.calls::toString);
        });
    }

    @Test
    void aNegativeMaximumNoLongerLetsTheNameRunPastTheRow() {
        String name = "A very long option name that will not fit in the row";
        // A 100 pixel row keeps 88 for the name, less 18 for the ellipsis, so eleven characters
        for (int maximum : new int[]{-1, -1000, Integer.MIN_VALUE, 0}) {
            assertEquals(List.of("A very long..."), nameDrawnAt(100, name, maximum), () -> "maximum " + maximum);
        }
        assertEquals(List.of("Short"), nameDrawnAt(100, "Short", -1000));
    }

    @Test
    void theMostNegativeRowWidthsDrawNoNameRatherThanWrappingToAHugeRoom() {
        for (int width : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 11, Integer.MIN_VALUE + 12, -13, -1}) {
            for (int maximum : new int[]{Integer.MIN_VALUE, 0, 70, Integer.MAX_VALUE}) {
                assertEquals(List.of(), nameDrawnAt(width, "Name", maximum), () -> "width " + width + " maximum " + maximum);
            }
        }
    }

    @Test
    void aMaximumAsWideAsTheRowOrWiderDrawsNoNameAndNoOverflow() {
        String name = "A very long option name that will not fit in the row";
        for (int maximum : new int[]{100, 101, 500, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            assertEquals(List.of(), nameDrawnAt(100, name, maximum), () -> "maximum " + maximum);
            assertEquals(List.of(), nameDrawnAt(100, "N", maximum));
        }
        // 70 of 100 leaves 18, room for the ellipsis alone
        assertEquals(List.of("..."), nameDrawnAt(100, name, 70));
    }

    @Test
    void aRowMeasuresItsColumnOnceAndKeepsItForItsLifetime() {
        Option<Boolean> option = option("Translucency sorting distance");
        Probe<Boolean> probe = bound(option, 70);
        ControlElement<Boolean> element = Control.createElementFor(option, DIM);
        for (int frame = 0; frame < 4; frame++) {
            element.render(new FakeDrawContext(), 0, 0, 0f);
        }
        assertEquals(1, probe.widthReads);
        // A new width shows on the next element, which every resize, page switch or language change makes
        probe.maxWidth = 130;
        FakeDrawContext kept = new FakeDrawContext();
        element.render(kept, 0, 0, 0f);
        assertEquals(List.of("Translucency sor..."), kept.strings);
        FakeDrawContext fresh = new FakeDrawContext();
        Control.createElementFor(option, DIM).render(fresh, 0, 0, 0f);
        assertEquals(List.of("Transl..."), fresh.strings);
        assertEquals(2, probe.widthReads);
    }

    @Test
    void framesAfterTheFirstNeverAskTheOptionForItsControl() {
        Option<Boolean> option = option("Name");
        bound(option, 30);
        ControlElement<Boolean> element = Control.createElementFor(option, DIM);
        element.render(new FakeDrawContext(), 0, 0, 0f);
        clearInvocations(option);
        for (int frame = 0; frame < 10; frame++) {
            element.render(new FakeDrawContext(), 0, 0, 0f);
        }
        verify(option, never()).getControl();
        // Only what can change between frames is read
        verify(option, times(10)).isAvailable();
        verify(option, times(10)).hasChanged();
        verify(option, times(10)).getName();
    }

    @Test
    void aMeasureOverrideThatGoesNegativeIsHeldAtZeroAndStillMeasuredOnce() {
        Option<Boolean> option = option("A very long option name that will not fit in the row");
        AtomicInteger measures = new AtomicInteger();
        ControlElement<Boolean> row = new ControlElement<>(option, new Dim2i(0, 0, 100, 18)) {
            @Override
            protected int measureColumnWidth(DrawContext drawContext) {
                measures.incrementAndGet();
                return -500;
            }
        };
        for (int frame = 0; frame < 3; frame++) {
            FakeDrawContext draw = new FakeDrawContext();
            row.render(draw, 0, 0, 0f);
            assertEquals(List.of("A very long..."), draw.strings);
        }
        assertEquals(1, measures.get());
        verify(option, never()).getControl();
    }

    @Test
    void availabilityIsReadLiveThroughTheHookForTheNameAndForFocus() {
        Option<Boolean> option = option("Name");
        bound(option, 30);
        AtomicBoolean available = new AtomicBoolean(true);
        when(option.isAvailable()).thenAnswer(invocation -> available.get());
        ControlElement<Boolean> row = Control.createElementFor(option, DIM);
        assertTrue(row.canFocus());
        Language on = new Language("Open");
        row.render(on, 0, 0, 0f);
        assertEquals(EnumSet.of(TextFormattingStyle.WHITE), styles(on.drawn.getFirst()));
        available.set(false);
        assertFalse(row.canFocus());
        Language off = new Language("Open");
        row.render(off, 0, 0, 0f);
        assertEquals(EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(off.drawn.getFirst()));
        // An element can decide for itself without asking the option
        ControlElement<Boolean> locked = new ControlElement<>(option, DIM) {
            @Override
            protected boolean isOptionAvailable() {
                return false;
            }
        };
        available.set(true);
        assertFalse(locked.canFocus());
    }

    @Test
    void theRowReservesTheFontAwareWidthOfTheLanguageItIsFirstDrawnIn() {
        Option<Boolean> option = option("Translucency sorting distance");
        LabelSized control = new LabelSized(option);
        when(option.getControl()).thenReturn(control);
        ControlElement<Boolean> element = Control.createElementFor(option, new Dim2i(0, 0, 200, 18));
        // "Open" takes 42, leaving 146 for the name, 128 of it before the ellipsis: 21 characters, the trailing space dropped
        Language english = new Language("Open");
        element.render(english, 0, 0, 0f);
        element.render(english, 0, 0, 0f);
        assertEquals(List.of("Translucency sorting...", "Translucency sorting..."), english.strings);
        assertEquals(1, control.measures);
        // Changing the language rebuilds the screen, and the new row measures in the new language: 102 leaves 86, 11 characters
        Language french = new Language("Ouvrir le menu");
        Control.createElementFor(option, new Dim2i(0, 0, 200, 18)).render(french, 0, 0, 0f);
        assertEquals(List.of("Translucenc..."), french.strings);
        // A label wider than the row takes all of it and the name gives way
        Language wide = new Language("x".repeat(40));
        Control.createElementFor(option, new Dim2i(0, 0, 200, 18)).render(wide, 0, 0, 0f);
        assertEquals(List.of(), wide.strings);
        assertEquals(3, control.measures);
    }

    @Test
    void theNameStaysOutOfTheColumnAndInsideTheRowForAnyMaximumAndWidth() {
        int[] maximums = {Integer.MIN_VALUE, -1000, -13, -1, 0, 1, 30, 70, 130, 199, 200, 201, 1000, Integer.MAX_VALUE};
        String[] names = {"N", "Name", "Render distance", "A very long option name that will not fit in the row"};
        for (String name : names) {
            Option<Boolean> option = option(name);
            Probe<Boolean> probe = bound(option, 0);
            for (int maximum : maximums) {
                probe.maxWidth = maximum;
                for (int width = -10; width <= 260; width++) {
                    FakeDrawContext draw = new FakeDrawContext();
                    Control.createElementFor(option, new Dim2i(0, 0, width, 18)).render(draw, 0, 0, 0f);
                    assertTrue(draw.strings.size() <= 1);
                    if (!draw.strings.isEmpty()) {
                        int column = Math.clamp(maximum, 0, Math.max(0, width));
                        int drawn = draw.strings.getFirst().length() * 6;
                        int row = width;
                        // Starts 6 in and ends at least 6 short of the column, so never past the row either
                        assertTrue(6 + drawn <= row - column - 6, () -> "width " + row + " maximum " + probe.maxWidth + " " + draw.strings);
                    }
                }
            }
        }
    }

    @Test
    void thePageFrameMakesEachVisibleRowOnceAndAFreshOnePerRebuild() {
        Option<Boolean> a = option("A");
        Option<Boolean> b = option("B");
        Option<Boolean> c = option("C");
        Probe<Boolean> pa = bound(a, 30);
        Probe<Boolean> pb = bound(b, 70);
        Probe<Boolean> pc = bound(c, 130);
        OptionPageFrame frame = new OptionPageFrame(new Dim2i(5, 7, 200, 300), false, page(a, b, c), o -> true);
        List<ControlElement<?>> rows = rows(frame);
        assertEquals(List.of(a, b, c), rows.stream().map(ControlElement::getOption).toList());
        // Sizing the frame makes nothing, so each row was made once
        assertEquals(List.of(1, 1, 1), List.of(pa.creations, pb.creations, pc.creations));
        // Under the 18 pixel page header, 18 pixel rows, offset by the frame
        assertEquals(new Dim2i(5, 25, 200, 18), rows.get(0).getDimensions());
        assertEquals(new Dim2i(5, 43, 200, 18), rows.get(1).getDimensions());
        assertEquals(new Dim2i(5, 61, 200, 18), rows.get(2).getDimensions());
        frame.setupFrame();
        assertEquals(List.of(), rows(frame));
        assertEquals(List.of(1, 1, 1), List.of(pa.creations, pb.creations, pc.creations));
        frame.buildFrame();
        List<ControlElement<?>> rebuilt = rows(frame);
        assertEquals(3, rebuilt.size());
        for (int i = 0; i < 3; i++) {
            assertNotSame(rows.get(i), rebuilt.get(i));
            assertEquals(rows.get(i).getDimensions(), rebuilt.get(i).getDimensions());
        }
        assertEquals(List.of(2, 2, 2), List.of(pa.creations, pb.creations, pc.creations));
    }

    @Test
    void aPageWithABorrowedControlBuildsWithAPlaceholderInThatRowAndReportsItOnce() {
        withLogger(logger -> {
            Option<Boolean> owner = option("Owner");
            Option<Boolean> borrower = option("Borrower");
            Probe<Boolean> probe = bound(owner, 30);
            when(borrower.getControl()).thenReturn(probe);
            OptionPageFrame frame = new OptionPageFrame(DIM, false, page(owner, borrower), o -> true);
            List<ControlElement<?>> rows = rows(frame);
            assertEquals(2, rows.size());
            assertSame(owner, rows.get(0).getOption());
            assertFalse(rows.get(0) instanceof PlaceholderControlElement<?>);
            assertInstanceOf(PlaceholderControlElement.class, rows.get(1));
            assertSame(borrower, rows.get(1).getOption());
            // In the borrower's own row, under the owner's
            assertEquals(new Dim2i(10, 56, 200, 18), rows.get(1).getDimensions());
            verify(logger).error(eq(FAILURE_LOG), eq("Borrower"), isA(IllegalStateException.class));
            // Rebuilding the page makes a new placeholder without reporting again
            frame.setupFrame();
            frame.buildFrame();
            List<ControlElement<?>> rebuilt = rows(frame);
            assertInstanceOf(PlaceholderControlElement.class, rebuilt.get(1));
            assertNotSame(rows.get(1), rebuilt.get(1));
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Borrower"), any(Throwable.class));
            assertEquals(List.of("Option failed: Borrower"), toasts().stream().map(ImpetusNotification::title).toList());
            // The page still draws, the owner's row as usual and the borrower's with its label
            Language draw = new Language("Open");
            frame.render(draw, -1, -1, 0f);
            assertTrue(draw.strings.containsAll(List.of("Page", "Owner", "Borrower", "Error")), draw.strings::toString);
            assertEquals(2, probe.creations);
        });
    }

    @Test
    void aPageWhoseControlsMakeNoElementOrAreMissingStillBuildsEveryRow() {
        withLogger(logger -> {
            Option<Boolean> empty = option("Empty");
            bound(empty, 30).factory = dim -> null;
            Option<Boolean> missing = option("Missing");
            Option<Boolean> fine = option("Fine");
            bound(fine, 30);
            OptionPageFrame frame = new OptionPageFrame(DIM, false, page(empty, missing, fine), o -> true);
            List<ControlElement<?>> rows = rows(frame);
            assertEquals(List.of(empty, missing, fine), rows.stream().map(ControlElement::getOption).toList());
            assertInstanceOf(PlaceholderControlElement.class, rows.get(0));
            assertInstanceOf(PlaceholderControlElement.class, rows.get(1));
            assertFalse(rows.get(2) instanceof PlaceholderControlElement<?>);
            // The layout is the one the frame measured before making any row
            assertEquals(18 + 3 * 18, frame.getDimensions().height());
            verify(logger).error(eq(FAILURE_LOG), eq("Empty"), isA(IllegalStateException.class));
            verify(logger).error(eq(FAILURE_LOG), eq("Missing"), isA(IllegalStateException.class));
        });
    }

    @Test
    void filteredOutOptionsAreNeverAskedForAControl() {
        Option<Boolean> shown = option("Shown");
        Probe<Boolean> probe = bound(shown, 30);
        // No control at all, which would make a placeholder if the frame made its row
        Option<Boolean> hidden = option("Hidden");
        OptionPageFrame frame = new OptionPageFrame(DIM, false, page(shown, hidden), o -> o == shown);
        assertEquals(List.of(shown), rows(frame).stream().map(ControlElement::getOption).toList());
        verify(hidden, never()).getControl();
        assertEquals(1, probe.creations);
    }

    @Test
    void aNarrowPageRendersEveryBuiltInRowWithEachNameFittedToItsColumn() {
        List<Option<?>> options = builtIns();
        OptionPageFrame frame = new OptionPageFrame(new Dim2i(0, 0, 100, 400), false, page(options.toArray(Option<?>[]::new)), o -> true);
        assertEquals(options, rows(frame).stream().map(ControlElement::getOption).toList());
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, -1, -1, 0f);
        // The tick box's 30 leaves 58, room for the whole name
        assertTrue(draw.strings.contains("Tick"));
        // The cycler's and button's 70 leave 18, room for the ellipsis alone
        assertEquals(2, draw.strings.stream().filter("..."::equals).count(), draw.strings::toString);
        // The read-only 90 leaves none, and the slider's 130 is held to the row and leaves none either
        assertFalse(draw.strings.contains("Text"));
        assertFalse(draw.strings.contains("Slide"));
        // Each control still draws its own value
        assertTrue(draw.strings.containsAll(List.of("B", "5", "value", ">")), draw.strings::toString);
    }

    @Test
    void aRowThatThrowsWhileDrawingIsReplacedAndItsPlaceholderDrawnTheSameFrame() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Broken");
            page.arm("render");
            Language draw = new Language("Open");
            page.frame().render(draw, -1, -1, 0f);
            ControlElement<?> first = page.first();
            assertInstanceOf(PlaceholderControlElement.class, first);
            assertSame(page.option(), first.getOption());
            assertEquals(new Dim2i(0, 18, 200, 18), first.getDimensions());
            // The placeholder in the broken row's place and the row after it, all on the frame that failed
            assertEquals(List.of("Page", "Broken", "Error", "Fine"), draw.strings);
            verify(logger).error(eq(FAILURE_LOG), eq("Broken"), same(page.row().failure));
            assertEquals("Option failed: Broken", toasts().getFirst().title());
            // Later frames never call into the broken row again, from any of the frame's lists, and report nothing more
            int calls = page.row().calls;
            for (int frame = 0; frame < 5; frame++) {
                page.frame().render(new Language("Open"), 100, 27, 0f);
            }
            assertEquals(calls, page.row().calls);
            verify(logger, times(1)).error(eq(FAILURE_LOG), any(), any(Throwable.class));
        });
    }

    @Test
    void aRowThatThrowsOnAClickIsReplacedAndTheClickEndsThere() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Clicked");
            page.arm("mouseClicked");
            FakeInteractionContext context = new FakeInteractionContext();
            assertTrue(page.frame().mouseClicked(context, 100, 27, 0));
            assertInstanceOf(PlaceholderControlElement.class, page.first());
            assertNull(page.frame().getCapturedChild());
            assertNull(page.frame().getFocusedControl());
            assertEquals(0, context.clicks);
            // The placeholder takes no click, nothing is reported twice and the row below is untouched
            assertFalse(page.frame().mouseClicked(context, 100, 27, 0));
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Clicked"), same(page.row().failure));
            assertFalse(page.second() instanceof PlaceholderControlElement<?>);
        });
    }

    @Test
    void aCapturedRowThatThrowsMidDragOrOnReleaseIsReplacedAndTheDragEnds() {
        withLogger(logger -> {
            FakeInteractionContext context = new FakeInteractionContext();
            FaultyPage dragged = FaultyPage.of("Dragged");
            assertTrue(dragged.frame().mouseClicked(context, 100, 27, 0));
            assertSame(dragged.row(), dragged.frame().getCapturedChild());
            dragged.arm("mouseDragged");
            // Well off the row, as a captured drag follows the cursor anywhere
            assertTrue(dragged.frame().mouseDragged(context, 100, 200, 0, 0, 5));
            assertInstanceOf(PlaceholderControlElement.class, dragged.first());
            assertNull(dragged.frame().getCapturedChild());
            assertFalse(dragged.frame().mouseReleased(context, 100, 200, 0));

            FaultyPage released = FaultyPage.of("Released");
            assertTrue(released.frame().mouseClicked(context, 100, 27, 0));
            released.arm("mouseReleased");
            assertTrue(released.frame().mouseReleased(context, 100, 27, 0));
            assertInstanceOf(PlaceholderControlElement.class, released.first());
            assertNull(released.frame().getCapturedChild());
            verify(logger).error(eq(FAILURE_LOG), eq("Dragged"), any(Throwable.class));
            verify(logger).error(eq(FAILURE_LOG), eq("Released"), any(Throwable.class));
        });
    }

    @Test
    void aRowThatThrowsOnAScrollOrAHoverTestIsReplaced() {
        withLogger(logger -> {
            FakeInteractionContext context = new FakeInteractionContext();
            FaultyPage scrolled = FaultyPage.of("Scrolled");
            scrolled.arm("mouseScrolled");
            assertTrue(scrolled.frame().mouseScrolled(context, 100, 27, 0, 1));
            assertInstanceOf(PlaceholderControlElement.class, scrolled.first());
            // The frame's hover pass on every frame, which then draws the placeholder
            FaultyPage hovered = FaultyPage.of("Hovered");
            hovered.arm("isMouseOver");
            Language draw = new Language("Open");
            hovered.frame().render(draw, 100, 27, 0f);
            assertInstanceOf(PlaceholderControlElement.class, hovered.first());
            assertTrue(draw.strings.contains("Error"), draw.strings::toString);
            // The container's own hover test, which answers no for the row that broke and yes once its placeholder is in
            FaultyPage tested = FaultyPage.of("Tested");
            tested.arm("isMouseOver");
            assertFalse(tested.frame().isMouseOver(100, 27));
            assertInstanceOf(PlaceholderControlElement.class, tested.first());
            assertTrue(tested.frame().isMouseOver(100, 27));
            for (String name : List.of("Scrolled", "Hovered", "Tested")) {
                verify(logger).error(eq(FAILURE_LOG), eq(name), any(Throwable.class));
            }
        });
    }

    @Test
    void aFocusedRowThatThrowsOnAKeyIsReplacedAndTheKeyEndsThere() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Typed");
            page.row().setFocused(true);
            page.arm("keyTyped");
            FakeInteractionContext context = new FakeInteractionContext();
            // Even a Tab, which a row that ignored it would have let move the focus
            assertTrue(page.frame().dispatchKeyTyped(context, '\t', Keys.TAB));
            assertInstanceOf(PlaceholderControlElement.class, page.first());
            assertNull(page.frame().getFocusedControl());
            // The next Tab starts over, passes the placeholder and lands on the row that works
            assertTrue(page.frame().dispatchKeyTyped(context, '\t', Keys.TAB));
            assertSame(page.second(), page.frame().getFocusedControl());
            verify(logger).error(eq(FAILURE_LOG), eq("Typed"), same(page.row().failure));
        });
    }

    @Test
    void aRowThatThrowsWhileFocusMovesIsReplacedAndTheFocusMovesOn() {
        withLogger(logger -> {
            // Asked whether Tab may stop there
            FaultyPage asked = FaultyPage.of("Asked");
            asked.arm("canFocus");
            assertTrue(asked.frame().moveFocus(true));
            assertInstanceOf(PlaceholderControlElement.class, asked.first());
            assertSame(asked.second(), asked.frame().getFocusedControl());
            // Asked whether it holds the focus
            FaultyPage held = FaultyPage.of("Held");
            held.arm("isFocused");
            assertNull(held.frame().getFocusedChild());
            assertInstanceOf(PlaceholderControlElement.class, held.first());
            // Told to drop the focus, by a click elsewhere and by Tab stepping off it
            FaultyPage cleared = FaultyPage.of("Cleared");
            cleared.row().setFocused(true);
            cleared.arm("setFocused");
            cleared.frame().clearFocus();
            assertInstanceOf(PlaceholderControlElement.class, cleared.first());
            assertNull(cleared.frame().getFocusedControl());
            FaultyPage left = FaultyPage.of("Left");
            left.row().setFocused(true);
            left.arm("setFocused");
            assertTrue(left.frame().moveFocus(true));
            assertInstanceOf(PlaceholderControlElement.class, left.first());
            assertSame(left.second(), left.frame().getFocusedControl());
            for (String name : List.of("Asked", "Held", "Cleared", "Left")) {
                verify(logger).error(eq(FAILURE_LOG), eq(name), any(Throwable.class));
            }
        });
    }

    @Test
    void aRowsFatalErrorIsNeverTurnedIntoAPlaceholder() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Fatal");
            StackOverflowError fatal = new StackOverflowError();
            page.row().failure = fatal;
            page.arm("render", "mouseClicked");
            assertSame(fatal, assertThrows(StackOverflowError.class, () -> page.frame().render(new FakeDrawContext(), -1, -1, 0f)));
            assertSame(fatal, assertThrows(StackOverflowError.class, () -> page.frame().mouseClicked(new FakeInteractionContext(), 100, 27, 0)));
            assertSame(page.row(), page.first());
            assertFalse(page.frame().recoverChild(page.row(), new OutOfMemoryError()));
            assertSame(page.row(), page.first());
            verifyNoInteractions(logger);
        });
    }

    @Test
    void onlyAFramesOwnRowsAreReplacedAndOtherWidgetsFailAsBefore() {
        withLogger(logger -> {
            IllegalStateException failure = new IllegalStateException("widget");
            AbstractWidget widget = new AbstractWidget() {
                @Override
                public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
                    throw failure;
                }

                @Override
                public boolean isMouseOver(double mouseX, double mouseY) {
                    return true;
                }

                @Override
                public boolean mouseClicked(InteractionContext context, double mouseX, double mouseY, int button) {
                    throw failure;
                }
            };
            BasicFrame frame = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).addChild(dim -> widget).build();
            assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.render(new FakeDrawContext(), 0, 0, 0f)));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.mouseClicked(new FakeInteractionContext(), 5, 5, 0)));
            assertFalse(frame.recoverChild(widget, failure));
            // A row another frame holds is left to that frame
            FaultyPage page = FaultyPage.of("Elsewhere");
            assertFalse(frame.recoverChild(page.row(), failure));
            assertSame(page.row(), page.first());
            verifyNoInteractions(logger);
        });
    }

    @Test
    void aRowInsideANestedFrameIsReplacedByTheFrameThatHoldsIt() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Nested");
            BasicFrame outer = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 200, 300)).addChild(dim -> page.frame()).build();
            page.arm("mouseClicked");
            assertTrue(outer.mouseClicked(new FakeInteractionContext(), 100, 27, 0));
            assertInstanceOf(PlaceholderControlElement.class, page.first());
            // The outer frame cannot replace a page, and has nothing left to replace
            assertFalse(outer.recoverChild(page.frame(), new IllegalStateException()));
            Language draw = new Language("Open");
            outer.render(draw, -1, -1, 0f);
            assertTrue(draw.strings.containsAll(List.of("Nested", "Error", "Fine")), draw.strings::toString);
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Nested"), any(Throwable.class));
        });
    }

    @Test
    void aPlaceholderThatFailsToDrawIsNotReplacedAgain() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Twice");
            page.arm("render");
            page.frame().render(new Language("Open"), -1, -1, 0f);
            ControlElement<?> placeholder = page.first();
            assertInstanceOf(PlaceholderControlElement.class, placeholder);
            IllegalStateException fonts = new IllegalStateException("fonts");
            FakeDrawContext failing = new FakeDrawContext() {
                @Override
                public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
                    if (str.equals(TextComponent.translatable(PLACEHOLDER_KEY))) {
                        throw fonts;
                    }
                    return super.drawString(str, x, y, color, shadow);
                }
            };
            assertSame(fonts, assertThrows(IllegalStateException.class, () -> page.frame().render(failing, -1, -1, 0f)));
            assertSame(placeholder, page.first());
            assertSame(placeholder, Control.placeholderFor(placeholder, fonts));
            verify(logger, times(1)).error(eq(FAILURE_LOG), any(), any(Throwable.class));
        });
    }

    @Test
    void anInterruptionThrownOnScreenIsReplacedWithTheFlagRestored() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Interrupted");
            InterruptedException interrupted = new InterruptedException("stop");
            page.row().failure = interrupted;
            page.arm("render");
            try {
                page.frame().render(new FakeDrawContext(), -1, -1, 0f);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertInstanceOf(PlaceholderControlElement.class, page.first());
            verify(logger).error(eq(FAILURE_LOG), eq("Interrupted"), same(interrupted));
        });
    }

    @Test
    void aHealthyRowIsAskedOnceForHoverAndOnceToDrawPerFrame() {
        FaultyPage page = FaultyPage.of("Counted");
        int before = page.row().calls;
        for (int frame = 1; frame <= 4; frame++) {
            page.frame().render(new FakeDrawContext(), -1, -1, 0f);
            // The frame's single hover pass, the draw, and the row's own hover test inside its draw
            assertEquals(before + 3 * frame, page.row().calls);
        }
        assertSame(page.row(), page.first());
    }

    @Test
    void placeholderForReadsTheBrokenElementsFieldsRatherThanItsGetters() {
        withLogger(logger -> {
            Option<Boolean> option = option("Fields");
            IllegalStateException failure = new IllegalStateException("drawn");
            ControlElement<Boolean> broken = new ControlElement<>(option, DIM) {
                @Override
                public Option<Boolean> getOption() {
                    throw new AssertionError("getter used");
                }

                @Override
                public Dim2i getDimensions() {
                    throw new AssertionError("getter used");
                }
            };
            ControlElement<Boolean> placeholder = Control.placeholderFor(broken, failure);
            assertInstanceOf(PlaceholderControlElement.class, placeholder);
            assertSame(option, placeholder.getOption());
            assertSame(DIM, placeholder.getDimensions());
            verify(logger).error(eq(FAILURE_LOG), eq("Fields"), same(failure));
            // Once per option, however many of its rows fail
            assertInstanceOf(PlaceholderControlElement.class, Control.placeholderFor(new ControlElement<>(option, DIM.withY(40)), failure));
            verify(logger, times(1)).error(eq(FAILURE_LOG), any(), any(Throwable.class));
            // A placeholder comes back as it is, unreported
            assertSame(placeholder, Control.placeholderFor(placeholder, failure));
            npe("Element must not be null", () -> Control.placeholderFor(null, failure));
            npe("Failure must not be null", () -> Control.placeholderFor(broken, null));
            verify(logger, times(1)).error(eq(FAILURE_LOG), any(), any(Throwable.class));
        });
    }

    @Test
    void aPlaceholderForAnOptionThatCannotNameItselfShowsItsDescription() {
        withLogger(logger -> {
            // A row with no option at all cannot be made, so there is never one to replace
            npe("Option must not be null", () -> new ControlElement<Boolean>(null, DIM));
            // A name that throws, with the id standing in; a row wide enough for all 180 pixels of it
            Option<Boolean> nameless = option("Unused");
            when(nameless.getId()).thenReturn(OptionIdentifier.create(MOD, "nameless", Boolean.class));
            when(nameless.getName()).thenThrow(new IllegalStateException("no name"));
            Language named = new Language("Open");
            Control.placeholderFor(new ControlElement<>(nameless, new Dim2i(0, 0, 400, 18)), new IllegalStateException("drawn")).render(named, 0, 0, 0f);
            assertEquals(List.of(MOD + ":nameless", "Error"), named.strings);
            // No name at all, which is described as null
            Option<Boolean> blank = option("Unused");
            when(blank.getName()).thenReturn(null);
            Language empty = new Language("Open");
            Control.placeholderFor(new ControlElement<>(blank, DIM), new IllegalStateException("drawn")).render(empty, 0, 0, 0f);
            assertEquals(List.of("null", "Error"), empty.strings);
        });
    }

    // Renders the frame hovering the given row three times, the last past the tooltip delay
    private static void hoverPastTheDelay(OptionPageFrame frame, FakeDrawContext draw, int x, int y) throws ReflectiveOperationException {
        java.lang.reflect.Field lastTime = OptionPageFrame.class.getDeclaredField("lastTime");
        lastTime.setAccessible(true);
        frame.render(draw, x, y, 0f);
        frame.render(draw, x, y, 0f);
        lastTime.setLong(frame, 1L);
        frame.render(draw, x, y, 0f);
    }

    @Test
    void aTooltipThatThrowsIsUnwoundReportedOnceAndLeftOffAfterwards() throws ReflectiveOperationException {
        Logger original = Statics.get(OptionPageFrame.class, "LOGGER");
        Logger logger = Mockito.mock(Logger.class);
        Statics.set(OptionPageFrame.class, "LOGGER", logger);
        clearToasts();
        try {
            // The option's own tooltip throws before anything is pushed
            Option<Boolean> option = option("Tipped");
            bound(option, 30);
            when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "tipped", Boolean.class));
            IllegalStateException failure = new IllegalStateException("no tooltip");
            when(option.getTooltip()).thenThrow(failure);
            OptionPageFrame frame = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page(option), o -> true);
            FakeDrawContext draw = new FakeDrawContext();
            hoverPastTheDelay(frame, draw, 100, 27);
            verify(logger).error(eq("Tooltip of option {} failed and is no longer shown"), eq(MOD + ":tipped"), same(failure));
            assertEquals("Tooltip failed: " + MOD + ":tipped", ImpetusNotifications.getVisible().getLast().title());
            // Never asked again, while the row itself keeps working
            hoverPastTheDelay(frame, draw, 100, 27);
            verify(option, times(1)).getTooltip();
            assertFalse(rows(frame).getFirst() instanceof PlaceholderControlElement<?>);
            assertEquals(0, draw.matrixDepth);

            // A failure inside the pushed transform pops it on the way out
            Option<Boolean> drawn = option("Drawn");
            bound(drawn, 30);
            when(drawn.getTooltip()).thenReturn(TextComponent.literal("tip"));
            OptionPageFrame second = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page(drawn), o -> true);
            IllegalStateException fonts = new IllegalStateException("fonts");
            FakeDrawContext failing = new FakeDrawContext() {
                @Override
                public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
                    // Only the tooltip draws with a shadow
                    if (shadow) {
                        throw fonts;
                    }
                    return super.drawString(str, x, y, color, shadow);
                }
            };
            hoverPastTheDelay(second, failing, 100, 27);
            assertEquals(0, failing.matrixDepth);
            assertTrue(failing.calls.contains("push") && failing.calls.contains("pop"), failing.calls::toString);
            verify(logger).error(eq("Tooltip of option {} failed and is no longer shown"), eq("null"), same(fonts));

            // A clip the failing draw left on is taken off too, though the tooltip never enables one itself
            Option<Boolean> clipped = option("Clipped");
            bound(clipped, 30);
            when(clipped.getTooltip()).thenReturn(TextComponent.literal("tip"));
            OptionPageFrame third = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page(clipped), o -> true);
            FakeDrawContext leaving = new FakeDrawContext() {
                @Override
                public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
                    if (shadow) {
                        this.enableScissor(0, 0, 1, 1);
                        throw fonts;
                    }
                    return super.drawString(str, x, y, color, shadow);
                }
            };
            // An outer clip, as the scrolling frame around a page holds, stays where it was
            leaving.enableScissor(0, 0, 200, 300);
            hoverPastTheDelay(third, leaving, 100, 27);
            assertEquals(1, leaving.scissorDepth);
            assertEquals(0, leaving.matrixDepth);
        } finally {
            Statics.set(OptionPageFrame.class, "LOGGER", original);
            clearToasts();
        }
    }

    @Test
    void aRowThatFailsHalfwayThroughDrawingIsUnwoundBeforeItsPlaceholderDraws() {
        withLogger(logger -> {
            FaultyPage page = FaultyPage.of("Half");
            page.row().pushesFirst = true;
            page.arm("render");
            // Translated, so the placeholder's label leaves its name room to be drawn
            Language draw = new Language("Open");
            // A clip and a transform around the page, as the scrolling frame holds
            draw.enableScissor(0, 0, 200, 300);
            draw.pushMatrix();
            page.frame().render(draw, -1, -1, 0f);
            // Back to what was pushed around the page, and no further
            assertEquals(1, draw.matrixDepth);
            assertEquals(1, draw.scissorDepth);
            // The row's two transforms and its scissor came off before the placeholder drew its name
            int name = draw.calls.indexOf(draw.calls.stream().filter(c -> c.startsWith("text(Half,")).findFirst().orElseThrow());
            assertEquals(List.of("push", "scissor(0,18,200,36)", "push", "pop", "pop", "unscissor"), draw.calls.subList(name - 7, name - 1));
            assertInstanceOf(PlaceholderControlElement.class, page.first());
            verify(logger).error(eq(FAILURE_LOG), eq("Half"), same(page.row().failure));
        });
    }

    @Test
    void aWidgetThatFailsHalfwayAndCannotBeReplacedIsLeftForTheScreenToReport() {
        IllegalStateException failure = new IllegalStateException("widget");
        AbstractWidget widget = new AbstractWidget() {
            @Override
            public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
                drawContext.pushMatrix();
                throw failure;
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                return false;
            }
        };
        BasicFrame frame = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).addChild(dim -> widget).build();
        FakeDrawContext draw = new FakeDrawContext();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.render(draw, 0, 0, 0f)));
        // Nothing is drawn after it, so nothing is unwound on the way out either
        assertEquals(1, draw.matrixDepth);
    }

    @Test
    void healthyFramesReadTheDepthsOnceEachAndNeverUnwind() {
        FaultyPage page = FaultyPage.of("Steady");
        var draw = new FakeDrawContext() {
            int reads;

            @Override
            public int matrixDepth() {
                this.reads++;
                return super.matrixDepth();
            }

            @Override
            public void unwindTo(int matrixDepth, int scissorDepth) {
                throw new AssertionError("unwound a healthy frame");
            }
        };
        for (int frame = 1; frame <= 3; frame++) {
            page.frame().render(draw, -1, -1, 0f);
            // One read for the page's own children and none per row
            assertEquals(frame, draw.reads);
        }
    }

    @Test
    void anOptionReportedBrokenElsewhereIsReportedOnceAndOnlyEverGetsPlaceholderRows() {
        withLogger(logger -> {
            Option<Boolean> option = option("Elsewhere");
            Probe<Boolean> probe = bound(option, 30);
            IllegalStateException failure = new IllegalStateException("applying");
            Control.reportBroken(option, "applying", failure);
            Control.reportBroken(option, "undoing changes", new IllegalStateException("again"));
            verify(logger, times(1)).error(eq("Option {} failed while {}"), eq("Elsewhere"), eq("applying"), same(failure));
            assertEquals(List.of("Option failed: Elsewhere"), toasts().stream().map(ImpetusNotification::title).toList());
            // Its rows are placeholders from now on, made without asking its control
            clearInvocations(option);
            for (int i = 0; i < 3; i++) {
                ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM.withY(i * 18));
                assertInstanceOf(PlaceholderControlElement.class, row);
                assertEquals(DIM.withY(i * 18), row.getDimensions());
            }
            assertEquals(0, probe.creations);
            assertEquals(0, probe.optionReads);
            verify(option, never()).getControl();
            // The strict path still makes its element, for callers that check rather than show
            assertFalse(Control.createElementFor(option, DIM) instanceof PlaceholderControlElement<?>);
            // A failure reported for an option whose control already failed adds no second report
            Option<Boolean> both = option("Both");
            bound(both, 30).factory = dim -> null;
            Control.createElementOrPlaceholder(both, DIM);
            Control.reportBroken(both, "applying", failure);
            verify(logger, never()).error(eq("Option {} failed while {}"), eq("Both"), any(), any(Throwable.class));
        });
    }

    @Test
    void aControlThatFailedIsNotAskedAgainWhenThePageIsRebuilt() {
        withLogger(logger -> {
            Option<Boolean> option = option("Rebuilt");
            Probe<Boolean> probe = bound(option, 30);
            probe.factory = dim -> null;
            Control.createElementOrPlaceholder(option, DIM);
            assertEquals(1, probe.creations);
            for (int i = 0; i < 4; i++) {
                assertInstanceOf(PlaceholderControlElement.class, Control.createElementOrPlaceholder(option, DIM));
            }
            assertEquals(1, probe.creations);
            assertEquals(1, probe.optionReads);
        });
    }

    @Test
    void reportBrokenRefusesMissingArgumentsAndRestoresAnInterruption() {
        withLogger(logger -> {
            Option<Boolean> option = option("Checked");
            IllegalStateException failure = new IllegalStateException("x");
            npe("Option must not be null", () -> Control.reportBroken(null, "applying", failure));
            npe("Activity must not be null", () -> Control.reportBroken(option, null, failure));
            npe("Failure must not be null", () -> Control.reportBroken(option, "applying", null));
            verifyNoInteractions(logger);
            InterruptedException stop = new InterruptedException("stop");
            try {
                Control.reportBroken(option, "applying", stop);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            verify(logger).error(eq("Option {} failed while {}"), eq("Checked"), eq("applying"), same(stop));
        });
    }
}
