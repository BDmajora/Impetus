package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.FontMetricsProvider;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.mockito.MockSettings;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TickBoxControlTest {
    // The box from 184 to 194 over rows 4 to 14, its tick from 186 to 192 over rows 6 to 12
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 18);
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ACCENT = DefaultColors.ELEMENT_ACTIVATED;
    private static final int DISABLED = DefaultColors.TEXT_DISABLED;

    // A filled box by its edges and colour
    private record Fill(int x1, int y1, int x2, int y2, int color) { }

    // A piece of text drawn by where it starts and ends
    private record Placed(String text, int x, int end) { }

    // Fake fonts keeping every fill and piece of text drawn and counting accent lookups
    private static class Recording extends FakeDrawContext {
        final List<Fill> fills = new ArrayList<>();
        final List<Placed> texts = new ArrayList<>();
        int accents;

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            this.fills.add(new Fill(x1, y1, x2, y2, color));
            super.fill(x1, y1, x2, y2, color);
        }

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.texts.add(new Placed(extractString(str), x, x + getStringWidth(str)));
            return super.drawString(str, x, y, color, shadow);
        }

        @Override
        public int getModAccentColor(String modId) {
            this.accents++;
            return super.getModAccentColor(modId);
        }

        // The fills in the given colour, in the order drawn
        List<Fill> in(int color) {
            return this.fills.stream().filter(fill -> fill.color() == color).toList();
        }

        void clear() {
            this.fills.clear();
            this.texts.clear();
            this.calls.clear();
            this.strings.clear();
        }
    }

    // A mock option holding a value that clicks and keys write back, available
    private static Option<Boolean> valued(Boolean initial) {
        return valued(initial, Mockito.withSettings());
    }

    // The same with the mock's settings, so sweeps can use one that records no calls
    private static Option<Boolean> valued(Boolean initial, MockSettings settings) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class, settings);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        Mockito.doAnswer(invocation -> holder[0] = invocation.getArgument(0)).when(option).setValue(any());
        when(option.getName()).thenReturn(TextComponent.literal("Tick"));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A tick box bound to its option as the pages bind it
    private static TickBoxControl bound(Option<Boolean> option) {
        TickBoxControl control = new TickBoxControl(option);
        when(option.getControl()).thenReturn(control);
        return control;
    }

    // A row at the bounds from a bound tick box
    private static ControlElement<Boolean> row(Option<Boolean> option, Dim2i dim) {
        return bound(option).createElement(dim);
    }

    // One frame of the row with the cursor far away
    private static Recording render(ControlElement<?> row) {
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        return draw;
    }

    // The four one pixel fills drawBorder makes for the edges
    private static List<Fill> border(int x1, int y1, int x2, int y2, int color) {
        return List.of(new Fill(x1, y1, x2, y1 + 1, color), new Fill(x1, y2 - 1, x2, y2, color), new Fill(x1, y1, x1 + 1, y2, color), new Fill(x2 - 1, y1, x2, y2, color));
    }

    // The tick inside the box followed by the box's outline, all in the colour
    private static List<Fill> tickedBox(int x1, int y1, int x2, int y2, int color) {
        List<Fill> fills = new ArrayList<>();
        fills.add(new Fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, color));
        fills.addAll(border(x1, y1, x2, y2, color));
        return fills;
    }

    // Asserts the call throws the given type with the given message
    private static <E extends Throwable> E refused(Class<E> type, String message, Executable call) {
        E thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        return thrown;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    // Names of the instance fields the class itself declares
    private static Set<String> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getName).collect(Collectors.toSet());
    }

    @Test
    void theControlRefusesAMissingOptionAndAnswersItsOptionAndAThirtyPixelColumnForAnyFonts() {
        refused(NullPointerException.class, "Option must not be null", () -> new TickBoxControl(null));
        Option<Boolean> option = valued(false);
        clearInvocations(option);
        TickBoxControl control = new TickBoxControl(option);
        assertSame(option, control.getOption());
        assertEquals(30, control.getMaxWidth());
        assertEquals(30, control.getMaxWidth(new FakeDrawContext()));
        assertEquals(30, control.getMaxWidth((FontMetricsProvider) null));
        assertSame(Boolean.class, control.getValueType());
        // The column narrows to a narrower row and a row of no or negative width has none
        assertEquals(30, Control.columnWidth(control, new FakeDrawContext(), 200));
        assertEquals(30, Control.columnWidth(control, new FakeDrawContext(), 30));
        assertEquals(20, Control.columnWidth(control, new FakeDrawContext(), 20));
        assertEquals(0, Control.columnWidth(control, new FakeDrawContext(), 0));
        assertEquals(0, Control.columnWidth(control, new FakeDrawContext(), -5));
        verifyNoInteractions(option);
    }

    @Test
    void everyRowIsANewWidgetAtExactlyTheBoundsGivenAndMakingOneAsksNothingOfTheOption() {
        Option<Boolean> option = valued(true);
        TickBoxControl control = bound(option);
        clearInvocations(option);
        Dim2i[] bounds = {DIM, new Dim2i(-50, -20, 15, 9), new Dim2i(0, 0, 0, 0), new Dim2i(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE)};
        for (Dim2i dim : bounds) {
            ControlElement<Boolean> first = control.createElement(dim);
            ControlElement<Boolean> second = control.createElement(dim);
            assertNotSame(first, second, dim::toString);
            assertSame(dim, first.getDimensions(), dim::toString);
            assertSame(option, first.getOption(), dim::toString);
            assertFalse(first.isFocused(), dim::toString);
        }
        verifyNoInteractions(option);
        refused(NullPointerException.class, "Dimensions must not be null", () -> control.createElement(null));
        // The strict path the strict page checks take accepts the row
        assertSame(DIM, Control.createElementFor(option, DIM).getDimensions());
    }

    @Test
    void anUntickedBoxIsAWhiteOutlineTenPixelsSquareSixInFromTheRightEdgeOnTheCentreLine() {
        Option<Boolean> option = valued(false);
        Recording draw = render(row(option, DIM));
        // The background first, then the name, then the box and no tick
        assertEquals(new Fill(0, 0, 200, 18, DefaultColors.BACKGROUND_DEFAULT), draw.fills.getFirst());
        assertEquals(List.of("Tick"), draw.strings);
        assertEquals(border(184, 4, 194, 14, WHITE), draw.fills.subList(1, draw.fills.size()));
        // No accent is asked for a box that shows none
        assertEquals(0, draw.accents);
        verify(option, never()).getId();
    }

    @Test
    void aTickedBoxIsFilledInsideItsOutlineInTheOwningModsAccentLookedUpOnceAcrossFrames() {
        Option<Boolean> option = valued(true);
        when(option.getId()).thenReturn(OptionIdentifier.create("coarctatio", "tick_box_control_test", Boolean.class));
        int accent = DefaultColors.getModAccentColor("coarctatio");
        assertNotEquals(ACCENT, accent);
        ControlElement<Boolean> row = row(option, DIM);
        Recording draw = new Recording();
        for (int frame = 0; frame < 3; frame++) {
            draw.clear();
            row.render(draw, 5000, 5000, 0f);
            assertEquals(tickedBox(184, 4, 194, 14, accent), draw.fills.subList(1, draw.fills.size()), "frame " + frame);
        }
        assertEquals(1, draw.accents);
        verify(option, times(1)).getId();
        // With no id the box takes the default accent
        assertEquals(tickedBox(184, 4, 194, 14, ACCENT), render(row(valued(true), DIM)).in(ACCENT));
    }

    @Test
    void anUnavailableBoxIsDrawnInTheDisabledColourTickedOrNotWithoutAskingForTheAccentAndCannotBeFocused() {
        for (boolean ticked : new boolean[]{true, false}) {
            Option<Boolean> option = valued(ticked);
            when(option.isAvailable()).thenReturn(false);
            ControlElement<Boolean> row = row(option, DIM);
            Recording draw = render(row);
            List<Fill> box = ticked ? tickedBox(184, 4, 194, 14, DISABLED) : border(184, 4, 194, 14, DISABLED);
            assertEquals(box, draw.fills.subList(1, draw.fills.size()), "ticked " + ticked);
            assertEquals(0, draw.accents, "ticked " + ticked);
            verify(option, never()).getId();
            assertFalse(row.canFocus(), "ticked " + ticked);
            verify(option, never()).setValue(any());
        }
    }

    @Test
    void aMissingValueShowsAnEmptyBoxAndAClickOrPressTicksIt() {
        Option<Boolean> option = valued(null);
        ControlElement<Boolean> row = row(option, DIM);
        assertEquals(border(184, 4, 194, 14, WHITE), render(row).in(WHITE));
        assertTrue(row.mouseClicked(new FakeInteractionContext(), 100, 9, 0));
        assertEquals(true, option.getValue());
        option.setValue(null);
        row.setFocused(true);
        assertTrue(row.keyTyped(new FakeInteractionContext(), ' ', Keys.SPACE));
        assertEquals(true, option.getValue());
    }

    @Test
    void eachFrameReadsTheValueAndTheAvailabilityOnceWritesNothingAndDrawsTheBoxInTheStateTheNameWasDrawnIn() {
        Option<Boolean> option = valued(false);
        // Available for the first question only, so a box that asked again would come out disabled
        when(option.isAvailable()).thenReturn(true, false);
        ControlElement<Boolean> row = row(option, DIM);
        clearInvocations(option);
        assertEquals(border(184, 4, 194, 14, WHITE), render(row).fills.subList(1, 5));
        verify(option, times(1)).isAvailable();
        verify(option, times(1)).getValue();
        // The next frame is drawn disabled, and a value set elsewhere shows at once
        option.setValue(true);
        clearInvocations(option);
        assertEquals(tickedBox(184, 4, 194, 14, DISABLED), render(row).fills.subList(1, 6));
        verify(option, times(1)).isAvailable();
        verify(option, times(1)).getValue();
        verify(option, never()).setValue(any());
    }

    @Test
    void theBoxIsDrawnExactlyWhenTheRowIsAtLeastSixteenWideAndTenHighAndAlwaysInsideItCentredToAPixel() {
        // Bound once and recording no calls, since the sweep makes some twenty-five thousand rows
        TickBoxControl unticked = bound(valued(false, Mockito.withSettings().stubOnly()));
        TickBoxControl ticked = bound(valued(true, Mockito.withSettings().stubOnly()));
        for (int x : new int[]{-37, 0, 5}) {
            for (int y : new int[]{-37, 0, 5}) {
                for (int width = -2; width <= 40; width++) {
                    for (int height = -2; height <= 30; height++) {
                        Dim2i dim = new Dim2i(x, y, width, height);
                        boolean fits = width >= 16 && height >= 10;
                        int right = x + width - 6;
                        int top = y + height / 2 - 5;
                        Recording empty = render(unticked.createElement(dim));
                        Recording full = render(ticked.createElement(dim));
                        assertEquals(fits ? border(right - 10, top, right, top + 10, WHITE) : List.of(), empty.in(WHITE), dim::toString);
                        assertEquals(fits ? tickedBox(right - 10, top, right, top + 10, ACCENT) : List.of(), full.in(ACCENT), dim::toString);
                        // Every fill a row draws stays inside it, and a row with an area still draws its background
                        for (Fill fill : full.fills) {
                            assertTrue(fill.x1() >= x && fill.x2() <= x + width && fill.y1() >= y && fill.y2() <= y + height, () -> dim + " " + fill);
                        }
                        assertEquals(width > 0 && height > 0, !full.fills.isEmpty(), dim::toString);
                        if (fits) {
                            int above = top - y;
                            int below = y + height - top - 10;
                            assertTrue(below - above == height % 2, dim::toString);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aRowAtTheCoordinateLimitDrawsItsBoxInPlaceAndARowPastItDrawsNothing() {
        int limit = 1 << 30;
        Object[][] cases = {
                {new Dim2i(0, 0, limit, 18), border(limit - 16, 4, limit - 6, 14, WHITE)},
                {new Dim2i(-limit, -limit, 200, 18), border(-limit + 184, -limit + 4, -limit + 194, -limit + 14, WHITE)},
                {new Dim2i(0, limit - 18, 200, 18), border(184, limit - 14, 194, limit - 4, WHITE)}};
        for (Object[] reaching : cases) {
            Dim2i dim = (Dim2i) reaching[0];
            assertEquals(reaching[1], render(row(valued(false), dim)).in(WHITE), dim::toString);
        }
        Dim2i[] past = {new Dim2i(0, 0, limit + 1, 18), new Dim2i(-limit - 1, 0, 200, 18), new Dim2i(0, limit - 17, 200, 18),
                new Dim2i(Integer.MAX_VALUE - 100, 0, 200, 18), new Dim2i(0, Integer.MAX_VALUE - 5, 200, 18)};
        for (Dim2i dim : past) {
            assertEquals(List.of(), render(row(valued(true), dim)).calls, dim::toString);
        }
    }

    @Test
    void theBoxIsDrawnOverTheFocusOutlineAndNoNameReachesItAtAnyWidth() {
        Option<Boolean> option = valued(true);
        ControlElement<Boolean> row = row(option, DIM);
        row.setFocused(true);
        List<Fill> expected = new ArrayList<>();
        expected.add(new Fill(0, 0, 200, 18, DefaultColors.BACKGROUND_HOVERED));
        expected.addAll(border(0, 0, 200, 18, ACCENT));
        expected.addAll(tickedBox(184, 4, 194, 14, ACCENT));
        assertEquals(expected, render(row).fills);
        for (boolean changed : new boolean[]{false, true}) {
            Option<Boolean> named = valued(false, Mockito.withSettings().stubOnly());
            when(named.getName()).thenReturn(TextComponent.literal("A very long option name that will not fit in the row"));
            when(named.hasChanged()).thenReturn(changed);
            TickBoxControl control = bound(named);
            for (int width = 16; width <= 260; width++) {
                Recording draw = render(control.createElement(new Dim2i(0, 0, width, 18)));
                int boxLeft = width - 16;
                assertEquals(boxLeft, draw.in(WHITE).getFirst().x1());
                for (Placed text : draw.texts) {
                    assertTrue(text.end() < boxLeft, () -> text + " reaches the box at " + boxLeft);
                }
            }
        }
    }

    @Test
    void aLeftClickAnywhereOnTheRowTogglesTheValueAndPlaysOneClickEachTime() {
        Option<Boolean> option = valued(false);
        ControlElement<Boolean> row = row(option, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        // The top left corner, the far corner, the box itself and the name
        double[][] places = {{0, 0}, {199.999, 17.999}, {189, 9}, {10, 9}, {0.5, 17}};
        boolean expected = false;
        for (double[] at : places) {
            assertTrue(row.mouseClicked(context, at[0], at[1], 0), at[0] + "," + at[1]);
            expected = !expected;
            assertEquals(expected, option.getValue(), at[0] + "," + at[1]);
        }
        assertEquals(places.length, context.clicks);
    }

    @Test
    void everyPressKeyTogglesAFocusedRowByItsCodeOrByItsCharacter() {
        Option<Boolean> option = valued(false);
        ControlElement<Boolean> row = row(option, DIM);
        row.setFocused(true);
        FakeInteractionContext context = new FakeInteractionContext();
        char[] typed = {'\0', '\0', '\0', ' ', '\r', '\n'};
        int[] codes = {Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER, 0, 0, 0};
        boolean expected = false;
        for (int i = 0; i < typed.length; i++) {
            assertTrue(row.keyTyped(context, typed[i], codes[i]), "key " + i);
            expected = !expected;
            assertEquals(expected, option.getValue(), "key " + i);
        }
        assertEquals(typed.length, context.clicks);
    }

    @Test
    void aToggleAsksTheAvailabilityThenReadsOnceWritesTheOppositeOnceAndOnlyThenPlaysTheClick() {
        for (boolean byKey : new boolean[]{false, true}) {
            Option<Boolean> option = valued(true);
            ControlElement<Boolean> row = row(option, DIM);
            row.setFocused(byKey);
            InteractionContext context = Mockito.mock(InteractionContext.class);
            clearInvocations(option);
            assertTrue(byKey ? row.keyTyped(context, ' ', Keys.SPACE) : row.mouseClicked(context, 189, 9, 0));
            InOrder order = Mockito.inOrder(option, context);
            order.verify(option).isAvailable();
            order.verify(option).getValue();
            order.verify(option).setValue(false);
            order.verify(context).playClickSound();
            verify(option, times(1)).getValue();
            verify(option, times(1)).setValue(any());
            verify(option, never()).hasChanged();
            verify(option, never()).getId();
            // Shift changes nothing for a box
            verify(context, never()).isSpecialKeyDown(any());
        }
    }

    @Test
    void aWriteThatThrowsPlaysNoClickAndAReadThatThrowsWritesNothing() {
        for (boolean byKey : new boolean[]{false, true}) {
            Option<Boolean> refusing = valued(false);
            doThrow(new IllegalStateException("refused")).when(refusing).setValue(any());
            ControlElement<Boolean> row = row(refusing, DIM);
            row.setFocused(byKey);
            FakeInteractionContext context = new FakeInteractionContext();
            refused(IllegalStateException.class, "refused", () -> press(row, context, byKey));
            assertEquals(0, context.clicks, "by key " + byKey);

            Option<Boolean> unreadable = valued(false);
            ControlElement<Boolean> other = row(unreadable, DIM);
            other.setFocused(byKey);
            when(unreadable.getValue()).thenThrow(new IllegalStateException("unreadable"));
            refused(IllegalStateException.class, "unreadable", () -> press(other, context, byKey));
            verify(unreadable, never()).setValue(any());
            assertEquals(0, context.clicks, "by key " + byKey);
        }
    }

    // Presses the row by Space or by a left click on its box
    private static boolean press(ControlElement<Boolean> row, InteractionContext context, boolean byKey) {
        return byKey ? row.keyTyped(context, ' ', Keys.SPACE) : row.mouseClicked(context, 189, 9, 0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aValueOfAnotherTypeFailsOnEveryPathWithoutWritingOrClicking() {
        Option raw = valued(false);
        when(raw.getValue()).thenReturn(1);
        ControlElement<Boolean> row = row((Option<Boolean>) raw, DIM);
        assertThrows(ClassCastException.class, () -> row.render(new Recording(), 0, 0, 0f));
        FakeInteractionContext context = new FakeInteractionContext();
        assertThrows(ClassCastException.class, () -> row.mouseClicked(context, 189, 9, 0));
        row.setFocused(true);
        assertThrows(ClassCastException.class, () -> row.keyTyped(context, ' ', Keys.SPACE));
        verify(raw, never()).setValue(any());
        assertEquals(0, context.clicks);
    }

    @Test
    void aRowWithAnAreaButTooSmallForItsBoxStillTogglesAsACyclerWhoseNameIsCutStillCycles() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 15, 18), new Dim2i(0, 0, 200, 9), new Dim2i(-40, -40, 1, 1)}) {
            Option<Boolean> option = valued(false);
            ControlElement<Boolean> row = row(option, dim);
            Recording draw = render(row);
            assertEquals(List.of(), draw.in(WHITE), dim::toString);
            assertEquals(1, draw.fills.size(), dim::toString);
            FakeInteractionContext context = new FakeInteractionContext();
            assertTrue(row.mouseClicked(context, dim.x(), dim.y(), 0), dim::toString);
            assertEquals(true, option.getValue(), dim::toString);
            assertTrue(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertTrue(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertEquals(false, option.getValue(), dim::toString);
            assertEquals(2, context.clicks, dim::toString);
        }
    }

    @Test
    void aRowHoldsOnlyWhetherItsBoxFitsAndKeepsItsToggleToItself() throws ReflectiveOperationException {
        Class<?> type = row(valued(false), DIM).getClass();
        assertEquals(Set.of("option"), instanceFields(TickBoxControl.class));
        assertEquals(Set.of("boxFits"), instanceFields(type));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertTrue(Modifier.isPrivate(type.getModifiers()));
        // The widget methods are its only public ones
        Set<String> exposed = Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::getName).collect(Collectors.toSet());
        assertEquals(Set.of("render", "mouseClicked", "keyTyped"), exposed);
        // Worked out once when the row is made, and false for a row with no drawable area
        assertEquals(true, field(row(valued(false), DIM), "boxFits"));
        assertEquals(true, field(row(valued(false), new Dim2i(-5, -5, 16, 10)), "boxFits"));
        assertEquals(false, field(row(valued(false), new Dim2i(0, 0, 15, 18)), "boxFits"));
        assertEquals(false, field(row(valued(false), new Dim2i(0, 0, 200, 9)), "boxFits"));
        assertEquals(false, field(row(valued(false), new Dim2i(0, 0, (1 << 30) + 1, 18)), "boxFits"));
        assertEquals(false, field(row(valued(false), new Dim2i(Integer.MAX_VALUE - 100, 0, 200, 18)), "boxFits"));
    }

    @Test
    void aRowWithNoAreaOrTooFarOutToBeDrawnTakesNoClickOrKeyEvenWithTheCursorOnItsBoxOrFocusForcedOnIt() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, (1 << 30) + 1, 18), new Dim2i(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 18), new Dim2i(0, (1 << 30) - 10, 200, 18),
                new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, -200, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, 200, -18)}) {
            Option<Boolean> option = valued(false);
            ControlElement<Boolean> row = row(option, dim);
            clearInvocations(option);
            // Where the box would lie, 11 in from the right edge on the centre line, which the edges of a row too far out still hold
            double x = (double) dim.x() + dim.width() - 11;
            double y = dim.getCenterY();
            assertEquals(dim.width() > 0 && dim.height() > 0, dim.containsCursor(x, y), dim::toString);
            FakeInteractionContext context = new FakeInteractionContext();
            assertFalse(row.mouseClicked(context, x, y, 0), dim::toString);
            assertFalse(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertFalse(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertFalse(row.keyTyped(context, '\r', Keys.RETURN), dim::toString);
            assertFalse(row.keyTyped(context, '\0', Keys.NUMPAD_ENTER), dim::toString);
            verify(option, never()).isAvailable();
            verify(option, never()).getValue();
            verify(option, never()).setValue(any());
            assertEquals(0, context.clicks, dim::toString);
        }
    }

    @Test
    void aRowReachingExactlyToTheCoordinateLimitStillTogglesOnAClickAndAPress() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 1 << 30, 18), new Dim2i(-(1 << 30), -(1 << 30), 200, 18), new Dim2i(0, (1 << 30) - 18, 200, 18)}) {
            Option<Boolean> option = valued(false);
            ControlElement<Boolean> row = row(option, dim);
            FakeInteractionContext context = new FakeInteractionContext();
            assertTrue(row.mouseClicked(context, (double) dim.x() + dim.width() - 11, dim.getCenterY(), 0), dim::toString);
            assertEquals(true, option.getValue(), dim::toString);
            assertTrue(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertTrue(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertEquals(false, option.getValue(), dim::toString);
            assertEquals(2, context.clicks, dim::toString);
        }
    }

    @Test
    void onlyALeftClickInsideOrAPressWhileFocusedAsksAboutTheOptionAndAnUnavailableOneTakesNeither() {
        Option<Boolean> option = valued(false);
        ControlElement<Boolean> row = row(option, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        clearInvocations(option);
        for (int button : new int[]{1, 2, -1, 7}) {
            assertFalse(row.mouseClicked(context, 189, 9, button), "button " + button);
        }
        // Just past each edge, the right and bottom edges themselves, and no place at all
        for (double[] at : new double[][]{{-0.01, 9}, {200, 9}, {189, -0.01}, {189, 18}, {Double.NaN, 9}, {189, Double.NaN}}) {
            assertFalse(row.mouseClicked(context, at[0], at[1], 0), at[0] + "," + at[1]);
        }
        // Unfocused, every press is someone else's
        assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
        row.setFocused(true);
        // Letters, Tab by key and by character, Escape and both arrows
        char[] typed = {'a', '\t', '\0', '\0', '\0', '\0'};
        int[] codes = {30, 0, Keys.TAB, 1, Keys.LEFT, Keys.RIGHT};
        for (int i = 0; i < typed.length; i++) {
            assertFalse(row.keyTyped(context, typed[i], codes[i]), "key " + i);
        }
        verify(option, never()).isAvailable();
        verify(option, never()).getValue();
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.mouseClicked(context, 189, 9, 0));
        for (int code : new int[]{Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER}) {
            assertFalse(row.keyTyped(context, '\0', code), "code " + code);
        }
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        assertEquals(0, context.clicks);
    }
}
