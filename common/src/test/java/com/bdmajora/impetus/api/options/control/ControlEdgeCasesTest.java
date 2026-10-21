package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.OptionPageFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlEdgeCasesTest {
    private enum Mode { A, B, C }

    // A mock option named and holding a value that clicks and keys write back, available and unchanged
    private static <T> Option<T> valued(String name, T initial) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        Mockito.doAnswer(invocation -> holder[0] = invocation.getArgument(0)).when(option).setValue(Mockito.any());
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // Fake fonts keeping every component drawn
    private static class Recording extends FakeDrawContext {
        final List<TextComponent> drawn = new ArrayList<>();

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.drawn.add(str);
            return super.drawString(str, x, y, color, shadow);
        }
    }

    // A piece of text drawn, where it starts and how wide it is
    private record Placed(String text, int x, int width) { }

    // Fake fonts keeping every piece of text drawn with its place, and every fill as its four edges
    private static class Placing extends FakeDrawContext {
        final List<Placed> texts = new ArrayList<>();
        final List<int[]> fills = new ArrayList<>();

        // Where each piece of text starts down the screen, in the order drawn
        final List<Integer> tops = new ArrayList<>();

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.texts.add(new Placed(extractString(str), x, getStringWidth(str)));
            this.tops.add(y);
            return super.drawString(str, x, y, color, shadow);
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            this.fills.add(new int[]{x1, y1, x2, y2});
            super.fill(x1, y1, x2, y2, color);
        }
    }

    // The name a bare row over the same option draws, or null when it draws none
    @SuppressWarnings("unchecked")
    private static String nameOf(ControlElement<?> row) {
        FakeDrawContext draw = new FakeDrawContext();
        new ControlElement<>((Option<Object>) row.getOption(), row.getDimensions()).render(draw, 5000, 5000, 0f);
        return draw.strings.isEmpty() ? null : draw.strings.getFirst();
    }

    // Long-texted rows: a cycler of long names, long read-only text, a slider with a long formatted value and a button with a long label
    private static List<Function<Dim2i, ControlElement<?>>> longTexts(String name) {
        return List.of(
                dim -> bound(new CyclingControl<>(valued(name, Mode.B), Mode.values(),
                        new TextComponent[]{TextComponent.literal("A"), TextComponent.literal("A VERY LONG MODE NAME"), TextComponent.literal("C")})).createElement(dim),
                dim -> bound(new ReadOnlyStringControl(valued(name, "VERSION 1.2.3 BUILT ON A LONG DAY"))).createElement(dim),
                dim -> bound(new SliderControl(valued(name, 5), 0, 10, 1, value -> TextComponent.literal(value + " VERY LONG UNITS"))).createElement(dim),
                dim -> bound(new ActionButtonControl(valued(name, false), TextComponent.literal("OPEN THE LONG MENU"), () -> { })).createElement(dim));
    }

    // Whether a component carries the given style on any layer
    private static boolean styled(TextComponent component, TextFormattingStyle style) {
        return component instanceof TextComponent.Styled styled && (styled.styles().contains(style) || styled(styled.inner(), style));
    }

    // Binds the control to its option as the pages do and returns it
    private static <T, C extends Control<T>> C bound(C control) {
        when(control.getOption().getControl()).thenReturn(control);
        return control;
    }

    // Makers of every built-in row and the placeholder, each over a fresh option
    private static List<Function<Dim2i, ControlElement<?>>> everyRow() {
        return List.of(
                dim -> bound(new TickBoxControl(valued("Tick", true))).createElement(dim),
                dim -> bound(new CyclingControl<>(valued("Cycle", Mode.B), Mode.class)).createElement(dim),
                dim -> bound(new SliderControl(valued("Slide", 5), 0, 10, 1, ControlValueFormatter.number())).createElement(dim),
                dim -> bound(new ReadOnlyStringControl(valued("Text", "value"))).createElement(dim),
                dim -> bound(new ActionButtonControl(valued("Button", false), () -> { })).createElement(dim),
                dim -> new PlaceholderControlElement<>(valued("Broken", false), dim, "broken"));
    }

    @Test
    void noRowDrawsAnythingWhenItHasNoAreaWhetherIdleHoveredFocusedOrHeld() {
        Dim2i[] empty = {new Dim2i(0, 0, 0, 18), new Dim2i(10, 10, -10, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, 200, -18),
                new Dim2i(-5, -5, -1, -1), new Dim2i(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE)};
        List<Function<Dim2i, ControlElement<?>>> rows = everyRow();
        for (int kind = 0; kind < rows.size(); kind++) {
            for (Dim2i dim : empty) {
                ControlElement<?> row = rows.get(kind).apply(dim);
                String context = "row " + kind + " " + dim;
                FakeDrawContext idle = new FakeDrawContext();
                row.render(idle, 5000, 5000, 0f);
                assertEquals(List.of(), idle.calls, context);
                // Held by a press, then focused, with the cursor on its corner
                row.mouseClicked(new FakeInteractionContext(), dim.x(), dim.y(), 0);
                row.setFocused(true);
                FakeDrawContext busy = new FakeDrawContext();
                row.render(busy, dim.x(), dim.y(), 0f);
                assertEquals(List.of(), busy.calls, context);
            }
        }
    }

    @Test
    void eachRowAsksForAvailabilityOnceAFrameAndDrawsItsNameAndControlInThatOneState() {
        Dim2i dim = new Dim2i(0, 0, 300, 18);
        List<Function<Dim2i, ControlElement<?>>> rows = everyRow().subList(0, 5);
        for (int kind = 0; kind < rows.size(); kind++) {
            ControlElement<?> row = rows.get(kind).apply(dim);
            Option<?> option = row.getOption();
            // Unavailable at the first read of every frame and available at any later one, as a switch flipped partway through a frame would answer
            AtomicInteger reads = new AtomicInteger();
            when(option.isAvailable()).thenAnswer(invocation -> reads.getAndIncrement() % 2 != 0);
            clearInvocations(option);
            for (int frame = 0; frame < 3; frame++) {
                reads.set(0);
                Recording draw = new Recording();
                // The cursor over the middle of the slider's track, where an available slider shows its thumb
                row.render(draw, 250, 9, 0f);
                String context = "row " + kind + " frame " + frame + " " + draw.calls;
                assertEquals(1, reads.get(), context);
                // Nothing in the mod's accent, which only an available control uses
                assertFalse(draw.drew(Integer.toHexString(DefaultColors.ELEMENT_ACTIVATED)), context);
                // Every piece of text but the button's arrow is struck through like the name
                for (TextComponent text : draw.drawn) {
                    if (!(text instanceof TextComponent.Literal literal && literal.text().equals(">"))) {
                        assertTrue(styled(text, TextFormattingStyle.STRIKETHROUGH), context);
                    }
                }
            }
            verify(option, times(3)).isAvailable();
        }
    }

    @Test
    void aControlsTextNeverRunsOverTheNameNorOutOfTheRowAtAnyWidth() {
        String[] names = {"n", "name", "graphics quality", "a very long option name that will not fit in the row"};
        for (String name : names) {
            List<Function<Dim2i, ControlElement<?>>> rows = longTexts(name);
            for (int kind = 0; kind < rows.size(); kind++) {
                for (int width = -5; width <= 330; width++) {
                    for (boolean focused : new boolean[]{false, true}) {
                        Dim2i dim = new Dim2i(-40, 0, width, 18);
                        ControlElement<?> row = rows.get(kind).apply(dim);
                        row.setFocused(focused);
                        Placing draw = new Placing();
                        row.render(draw, 5000, 5000, 0f);
                        String drawnName = nameOf(row);
                        String context = "row " + kind + " name '" + name + "' width " + width + " focused " + focused + " " + draw.texts;
                        int from = 0;
                        // Past the name and its 6 pixel gap, or anywhere in the row when there is no name
                        int limit = dim.x();
                        if (drawnName != null) {
                            assertEquals(new Placed(drawnName, dim.x() + 6, drawnName.length() * 6), draw.texts.getFirst(), context);
                            limit = dim.x() + 6 + drawnName.length() * 6 + 6;
                            from = 1;
                        }
                        for (Placed text : draw.texts.subList(from, draw.texts.size())) {
                            assertTrue(text.x() >= limit, context);
                            assertTrue(text.x() + text.width() <= dim.getLimitX(), context);
                        }
                    }
                }
            }
        }
    }

    @Test
    void nothingAnyRowFillsLeavesTheRowOrTurnsInsideOutAtAnySize() {
        List<Function<Dim2i, ControlElement<?>>> rows = new ArrayList<>(everyRow());
        rows.addAll(longTexts("name"));
        for (int kind = 0; kind < rows.size(); kind++) {
            for (int height : new int[]{1, 5, 9, 10, 11, 18}) {
                for (int width = 1; width <= 150; width++) {
                    for (boolean focused : new boolean[]{false, true}) {
                        Dim2i dim = new Dim2i(-40, 30, width, height);
                        ControlElement<?> row = rows.get(kind).apply(dim);
                        row.setFocused(focused);
                        Placing draw = new Placing();
                        // The cursor in the row's middle, where a slider shows its track
                        row.render(draw, dim.getCenterX(), dim.getCenterY(), 0f);
                        for (int[] fill : draw.fills) {
                            String context = "row " + kind + " " + dim + " focused " + focused + " fill " + java.util.Arrays.toString(fill);
                            assertTrue(fill[0] <= fill[2] && fill[1] <= fill[3], context);
                            assertTrue(fill[0] >= dim.x() && fill[2] <= dim.getLimitX(), context);
                            assertTrue(fill[1] >= dim.y() && fill[3] <= dim.getLimitY(), context);
                        }
                        for (int i = 0; i < draw.texts.size(); i++) {
                            Placed text = draw.texts.get(i);
                            int top = draw.tops.get(i);
                            String context = "row " + kind + " " + dim + " " + draw.texts + " at " + draw.tops;
                            assertTrue(text.x() >= dim.x() && text.x() + text.width() <= dim.getLimitX(), context);
                            // A whole line of text, nine high in the fake fonts, inside the row
                            assertTrue(top >= dim.y() && top + draw.lineHeight() <= dim.getLimitY(), context);
                        }
                    }
                }
            }
        }
    }

    @Test
    void releasingADraggedSliderEndsTheDragSoItsTrackHidesOnceTheCursorLeaves() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        FakeInteractionContext context = new FakeInteractionContext();
        // The track runs x 104..193 on rows 4..13
        assertTrue(row.mouseClicked(context, 150, 9, 0));
        assertTrue(row.mouseDragged(context, 160, 9, 0, 0, 0));
        FakeDrawContext held = new FakeDrawContext();
        row.render(held, 5000, 5000, 0f);
        assertTrue(held.drew("fill(104,8,194,9,"));
        // The release ends the drag it was part of, and only that one
        assertTrue(row.mouseReleased(context, 160, 9, 0));
        assertFalse(row.mouseReleased(context, 160, 9, 0));
        FakeDrawContext released = new FakeDrawContext();
        row.render(released, 5000, 5000, 0f);
        assertFalse(released.drew("fill(104,8,194,9,"), released.calls::toString);
        assertFalse(row.mouseDragged(context, 120, 9, 0, 0, 0));
        // Another button's release leaves a drag going
        assertTrue(row.mouseClicked(context, 150, 9, 0));
        assertFalse(row.mouseReleased(context, 150, 9, 1));
        assertTrue(row.mouseDragged(context, 120, 9, 0, 0, 0));
    }

    @Test
    void aSliderOverTheWholeIntRangeReachesBothEndsAndNeverWrapsRound() {
        Option<Integer> option = valued("Wide", 0);
        ControlElement<?> row = bound(new SliderControl(option, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(row.mouseClicked(context, 104, 9, 0));
        assertEquals(Integer.MIN_VALUE, option.getValue());
        assertTrue(row.mouseDragged(context, 193, 9, 0, 0, 0));
        assertEquals(Integer.MAX_VALUE, option.getValue());
        assertTrue(row.mouseDragged(context, 148, 9, 0, 0, 0));
        assertEquals((int) (Integer.MIN_VALUE + Math.round(44.0 / 89 * 4294967295L)), option.getValue());
        row.setFocused(true);
        Mockito.doAnswer(invocation -> null).when(option).setValue(Mockito.any());
        when(option.getValue()).thenReturn(Integer.MAX_VALUE);
        clearInvocations(option);
        assertTrue(row.keyTyped(context, '\0', Keys.RIGHT));
        verify(option, never()).setValue(Mockito.any());
        assertTrue(row.keyTyped(context, '\0', Keys.LEFT));
        verify(option).setValue(Integer.MAX_VALUE - 1);
        when(option.getValue()).thenReturn(Integer.MIN_VALUE);
        clearInvocations(option);
        assertTrue(row.keyTyped(context, '\0', Keys.LEFT));
        verify(option, never()).setValue(Mockito.any());
        // The thumb sits at either end of the track
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertTrue(draw.drew("fill(102,4,106,14,"), draw.calls::toString);
    }

    @Test
    void aValueOutsideTheRangeStepsInFromTheNearEndRatherThanWrappingToTheFarOne() {
        Option<Integer> option = valued("Slide", Integer.MAX_VALUE);
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 2, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        row.setFocused(true);
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(row.keyTyped(context, '\0', Keys.RIGHT));
        assertEquals(10, option.getValue());
        option.setValue(Integer.MIN_VALUE);
        assertTrue(row.keyTyped(context, '\0', Keys.LEFT));
        assertEquals(0, option.getValue());
        // Shown as it is, with the thumb held at the end it is past
        option.setValue(-7);
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertTrue(draw.strings.contains("-7"));
        assertTrue(draw.drew("fill(102,4,106,14,"), draw.calls::toString);
    }

    @Test
    void aSliderShowsAValueOffTheIntervalAsItIsWhetherIdleOrShown() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 2, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        FakeDrawContext idle = new FakeDrawContext();
        row.render(idle, 5000, 5000, 0f);
        assertEquals(List.of("Slide", "5"), idle.strings);
        row.setFocused(true);
        FakeDrawContext shown = new FakeDrawContext();
        row.render(shown, 5000, 5000, 0f);
        assertEquals(List.of("Slide", "5"), shown.strings);
        // Halfway along the 90 pixel track, less the thumb's half width
        assertTrue(shown.drew("fill(147,4,151,14,"), shown.calls::toString);
        // A step snaps it back onto the interval
        assertTrue(row.keyTyped(new FakeInteractionContext(), '\0', Keys.RIGHT));
        assertEquals(8, option.getValue());
    }

    @Test
    void aSliderWithNoValueShowsAndStepsFromItsMinimum() {
        Option<Integer> option = valued("Slide", null);
        ControlElement<?> row = bound(new SliderControl(option, 3, 9, 3, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(List.of("Slide", "3"), draw.strings);
        row.setFocused(true);
        assertTrue(row.keyTyped(new FakeInteractionContext(), '\0', Keys.RIGHT));
        assertEquals(6, option.getValue());
        option.setValue(null);
        assertTrue(row.mouseClicked(new FakeInteractionContext(), 104, 9, 0));
        assertEquals(3, option.getValue());
    }

    @Test
    void aSliderTooBigForItsRowShowsOnlyItsValueAndCannotBeGrabbed() {
        Option<Integer> option = valued("Slide", 5);
        // Nine high leaves no room for the ten pixel slider
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 9));
        row.setFocused(true);
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 150, 4, 0f);
        assertEquals(List.of("Slide", "5"), draw.strings);
        // The background and the focus outline's four sides, and no track or thumb
        assertEquals(5, draw.calls.stream().filter(call -> call.startsWith("fill(")).count(), draw.calls::toString);
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(row.mouseClicked(context, 150, 4, 0));
        assertFalse(row.mouseDragged(context, 120, 4, 0, 0, 0));
        assertEquals(5, option.getValue());
        // The arrows still step it
        assertTrue(row.keyTyped(context, '\0', Keys.RIGHT));
        assertEquals(6, option.getValue());
    }

    @Test
    void eachControlFormatsMeasuresAndCutsItsTextOnlyWhenWhatItShowsChanges() {
        AtomicInteger formats = new AtomicInteger();
        Option<Integer> slide = valued("Slide", 5);
        ControlElement<?> slider = bound(new SliderControl(slide, 0, 10, 1, value -> {
            formats.incrementAndGet();
            return TextComponent.literal(value + " UNITS");
        })).createElement(new Dim2i(0, 0, 200, 18));
        Option<Mode> cycle = valued("Cycle", Mode.B);
        ControlElement<?> cycler = bound(new CyclingControl<>(cycle, Mode.class)).createElement(new Dim2i(0, 0, 200, 18));
        Option<String> text = valued("Text", "VALUE");
        ControlElement<?> reader = bound(new ReadOnlyStringControl(text)).createElement(new Dim2i(0, 0, 200, 18));
        for (ControlElement<?> row : List.of(slider, cycler, reader)) {
            Recording draw = new Recording();
            row.render(draw, 5000, 5000, 0f);
            TextComponent value = draw.drawn.getLast();
            for (int frame = 0; frame < 5; frame++) {
                draw.drawn.clear();
                row.render(draw, 5000, 5000, 0f);
                assertSame(value, draw.drawn.getLast());
            }
        }
        assertEquals(1, formats.get());
        // A new value is formatted once
        slide.setValue(6);
        for (int frame = 0; frame < 3; frame++) {
            slider.render(new Recording(), 5000, 5000, 0f);
        }
        assertEquals(1 + 3, formats.get());
        Recording draw = new Recording();
        for (int frame = 0; frame < 3; frame++) {
            slider.render(draw, 5000, 5000, 0f);
        }
        assertEquals(5, formats.get());
        assertEquals("6 UNITS", draw.strings.getLast());
        // Shown beside the track it is formatted once more for its own place, then kept
        slider.setFocused(true);
        Recording shown = new Recording();
        for (int frame = 0; frame < 4; frame++) {
            shown.drawn.clear();
            slider.render(shown, 5000, 5000, 0f);
        }
        assertEquals(6, formats.get());
        assertEquals(TextComponent.literal("6 UNITS"), shown.drawn.getLast());
    }

    @Test
    void controlsWithNothingToShowDrawOnlyTheName() {
        // A formatter that gives nothing and a missing read-only value
        ControlElement<?> slider = bound(new SliderControl(valued("Slide", 5), 0, 10, 1, value -> null)).createElement(new Dim2i(0, 0, 200, 18));
        ControlElement<?> reader = bound(new ReadOnlyStringControl(valued("Text", null))).createElement(new Dim2i(0, 0, 200, 18));
        for (ControlElement<?> row : List.of(slider, reader)) {
            for (boolean available : new boolean[]{true, false}) {
                when(row.getOption().isAvailable()).thenReturn(available);
                FakeDrawContext draw = new FakeDrawContext();
                row.render(draw, 5000, 5000, 0f);
                assertEquals(List.of(((TextComponent.Literal) row.getOption().getName()).text()), draw.strings);
            }
        }
    }

    @Test
    void aTickBoxWithNoValueShowsEmptyAndAClickTicksIt() {
        Option<Boolean> option = valued("Tick", null);
        ControlElement<?> row = bound(new TickBoxControl(option)).createElement(new Dim2i(0, 0, 200, 18));
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        // The white outline and no inner fill
        assertTrue(draw.drew("fill(184,4,194,5,ffffffff)"));
        assertFalse(draw.drew("fill(186,6,192,12,"));
        assertTrue(row.mouseClicked(new FakeInteractionContext(), 100, 9, 0));
        assertEquals(true, option.getValue());
    }

    // Swaps the placeholder's logger for a mock while the body runs and clears the toasts it raises
    private static void withLogger(Runnable body) {
        // Reading it first runs the class initialiser, which would otherwise overwrite the swap
        Logger original = Statics.get(PlaceholderControlElement.class, "LOGGER");
        Statics.set(PlaceholderControlElement.class, "LOGGER", Mockito.mock(Logger.class));
        try {
            body.run();
        } finally {
            Statics.set(PlaceholderControlElement.class, "LOGGER", original);
            Statics.<List<?>>get(com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications.class, "NOTIFICATIONS").clear();
        }
    }

    @Test
    void anOptionThatThrowsAnErrorWhileNamedStillGetsItsPlaceholderButAVirtualMachineErrorIsNotHeld() {
        withLogger(() -> {
            Option<Boolean> option = valued("Unused", false);
            when(option.getName()).thenThrow(new AssertionError("no name"));
            ControlElement<Boolean> row = new PlaceholderControlElement<>(option, new Dim2i(0, 0, 400, 18), "described");
            FakeDrawContext draw = new FakeDrawContext();
            row.render(draw, 0, 0, 0f);
            assertEquals("described", draw.strings.getFirst());
            // Through the page's own path, with the id and the name both failing as errors
            Option<Boolean> broken = valued("Unused", false);
            when(broken.getControl()).thenThrow(new IllegalStateException("no control"));
            when(broken.getId()).thenThrow(new NoClassDefFoundError("missing/Id"));
            when(broken.getName()).thenThrow(new AssertionError("no name"));
            ControlElement<Boolean> placeholder = Control.createElementOrPlaceholder(broken, new Dim2i(0, 0, 400, 18));
            assertInstanceOf(PlaceholderControlElement.class, placeholder);
            FakeDrawContext drawn = new FakeDrawContext();
            placeholder.render(drawn, 0, 0, 0f);
            // Described by its class, cut to the 196 the 192 pixel label column leaves: 29 characters before the ellipsis
            assertEquals(List.of(broken.getClass().getName().substring(0, 29) + "...", "impetus.options.control_failed"), drawn.strings);
            Option<Boolean> overflowing = valued("Unused", false);
            when(overflowing.getName()).thenThrow(new StackOverflowError());
            assertThrows(StackOverflowError.class, () -> new PlaceholderControlElement<>(overflowing, new Dim2i(0, 0, 400, 18), "described"));
        });
    }

    @Test
    void aButtonLabelCutNeverSplitsACharacterAndSurvivesAFontSourceThatCutsBadly() {
        Option<Boolean> option = valued("Button", false);
        // 54 wide leaves 36 for the label, 18 after the ellipsis: "Ab" and half the emoji
        FakeDrawContext draw = new FakeDrawContext();
        bound(new ActionButtonControl(option, TextComponent.literal("Ab\uD83D\uDE00cdefgh"), () -> { })).createElement(new Dim2i(0, 0, 54, 18)).render(draw, 0, 0, 0f);
        assertEquals(List.of("Ab...", ">"), draw.strings);
        for (boolean tooLong : new boolean[]{false, true}) {
            FakeDrawContext bad = new FakeDrawContext() {
                @Override
                public String substrByWidth(String str, int maxWidth) {
                    return tooLong ? str : null;
                }
            };
            Option<Boolean> other = valued("Button", false);
            bound(new ActionButtonControl(other, TextComponent.literal("Configure everything"), () -> { })).createElement(new Dim2i(0, 0, 200, 18)).render(bad, 0, 0, 0f);
            assertEquals(List.of("Button", "...", ">"), bad.strings);
        }
    }

    @Test
    void tabPassesOverEveryRowWithNoAreaAsItDoesOverAnUnavailableOne() {
        List<Function<Dim2i, ControlElement<?>>> rows = everyRow();
        // The focusable kinds: tick box, cycler, slider and button
        for (int kind : new int[]{0, 1, 2, 4}) {
            assertTrue(rows.get(kind).apply(new Dim2i(0, 0, 1, 1)).canFocus(), "row " + kind);
            for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, -3, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, 200, -1)}) {
                assertFalse(rows.get(kind).apply(dim).canFocus(), "row " + kind + " " + dim);
            }
        }
        // The read-only row and the placeholder never take focus
        assertFalse(rows.get(3).apply(new Dim2i(0, 0, 200, 18)).canFocus());
        assertFalse(rows.get(5).apply(new Dim2i(0, 0, 200, 18)).canFocus());
    }

    @Test
    void aButtonLabelTheFontSourceHasNoTextForIsCutToTheEllipsis() {
        TextComponent label = TextComponent.literal("Go");
        FakeDrawContext blank = new FakeDrawContext() {
            @Override
            public int getStringWidth(TextComponent component) {
                return extractString(component) == null ? 500 : super.getStringWidth(component);
            }

            @Override
            public String extractString(TextComponent component) {
                return component.equals(label.withStyle(TextFormattingStyle.UNDERLINE)) || component.equals(label) ? null : super.extractString(component);
            }
        };
        bound(new ActionButtonControl(valued("Button", false), label, () -> { })).createElement(new Dim2i(0, 0, 200, 18)).render(blank, 0, 0, 0f);
        assertEquals(List.of("Button", "...", ">"), blank.strings);
    }

    @Test
    void anInterruptionWhileDescribingOrNamingABrokenOptionIsKeptAndAVirtualMachineErrorIsNotHeld() {
        withLogger(() -> {
            Option<Boolean> named = valued("Unused", false);
            when(named.getName()).thenAnswer(invocation -> {
                throw new InterruptedException("named");
            });
            FakeDrawContext draw = new FakeDrawContext();
            new PlaceholderControlElement<>(named, new Dim2i(0, 0, 400, 18), "described").render(draw, 0, 0, 0f);
            assertEquals("described", draw.strings.getFirst());
            assertTrue(Thread.interrupted());
            // The id read by the page's own path
            Option<Boolean> described = valued("Unused", false);
            when(described.getControl()).thenThrow(new IllegalStateException("no control"));
            when(described.getId()).thenAnswer(invocation -> {
                throw new InterruptedException("described");
            });
            assertInstanceOf(PlaceholderControlElement.class, Control.createElementOrPlaceholder(described, new Dim2i(0, 0, 400, 18)));
            assertTrue(Thread.interrupted());
            Option<Boolean> overflowing = valued("Unused", false);
            when(overflowing.getControl()).thenThrow(new IllegalStateException("no control"));
            when(overflowing.getId()).thenThrow(new StackOverflowError());
            assertThrows(StackOverflowError.class, () -> Control.createElementOrPlaceholder(overflowing, new Dim2i(0, 0, 400, 18)));
        });
    }

    @Test
    void aSliderGoneUnavailableMidDragIgnoresTheRestOfIt() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(row.mouseClicked(context, 150, 9, 0));
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.mouseDragged(context, 104, 9, 0, 0, 0));
        assertEquals(5, option.getValue());
        // A drag through a cursor that is not a number goes to the start
        when(option.isAvailable()).thenReturn(true);
        assertTrue(row.mouseDragged(context, Double.NaN, 9, 0, 0, 0));
        assertEquals(0, option.getValue());
    }

    @Test
    void aShownSliderWhoseFormatterGivesNothingDrawsOnlyItsTrackAndThumb() {
        ControlElement<?> row = bound(new SliderControl(valued("Slide", 5), 0, 10, 1, value -> null)).createElement(new Dim2i(0, 0, 200, 18));
        row.setFocused(true);
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(List.of("Slide"), draw.strings);
        assertTrue(draw.drew("fill(104,8,194,9,"));
    }

    @Test
    void aOnePixelTrackSetsTheMinimumWhereverItIsGrabbed() {
        // Thirteen wide leaves one pixel between the two six pixel insets
        Option<Integer> option = valued("Slide", 5);
        ControlElement<?> row = bound(new SliderControl(option, 0, 10, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 13, 18));
        assertTrue(row.mouseClicked(new FakeInteractionContext(), 6, 9, 0));
        assertEquals(0, option.getValue());
        row.setFocused(true);
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertTrue(draw.drew("fill(6,8,7,9,"), draw.calls::toString);
    }

    // Asserts the call throws the given type with the given message
    private static <E extends Throwable> E refused(Class<E> type, String message, org.junit.jupiter.api.function.Executable call) {
        E thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        return thrown;
    }

    @Test
    void everyControlRefusesAMissingOptionWhenMadeRatherThanWhenItsRowIsFirstDrawn() {
        refused(NullPointerException.class, "Option must not be null", () -> new TickBoxControl(null));
        refused(NullPointerException.class, "Option must not be null", () -> new ReadOnlyStringControl(null));
        refused(NullPointerException.class, "Option must not be null", () -> new SliderControl(null, 0, 10, 1, ControlValueFormatter.number()));
        refused(NullPointerException.class, "Option must not be null", () -> new CyclingControl<>(null, Mode.class));
        refused(NullPointerException.class, "Option must not be null", () -> new CyclingControl<>(null, Mode.values(), new TextComponent[3]));
        // The option is checked before anything else, as ActionButtonControl checks it
        refused(NullPointerException.class, "Option must not be null", () -> new SliderControl(null, 10, 0, 0, null));
        refused(NullPointerException.class, "Option must not be null", () -> new CyclingControl<>(null, (Mode[]) null, null));
    }

    // An option over an int holder whose control the test picks
    private static OptionImpl<int[], Mode> built(String path, java.util.function.Function<OptionImpl<int[], Mode>, Control<Mode>> control) {
        int[] holder = {0};
        OptionStorage<int[]> storage = () -> holder;
        return OptionImpl.createBuilder(Mode.class, storage)
                .setId(OptionIdentifier.create("control_edge_cases_test", path, Mode.class))
                .setBinding((h, v) -> h[0] = v.ordinal(), h -> Mode.values()[h[0]])
                .setControl(control)
                .build();
    }

    @Test
    void anOptionWhoseControlCannotBeMadeIsStillBuiltAndItsPageShowsAPlaceholderRow() {
        withLogger(() -> {
            OptionImpl<int[], Mode> broken = built("broken", o -> new CyclingControl<>(o, new Mode[0], new TextComponent[0]));
            IllegalStateException failure = refused(IllegalStateException.class, "Control of option control_edge_cases_test:broken could not be made", broken::getControl);
            assertEquals("A cycler needs at least one value", failure.getCause().getMessage());
            // The same failure every time it is asked
            assertSame(failure.getCause(), assertThrows(IllegalStateException.class, broken::getControl).getCause());
            assertEquals(Mode.A, broken.getValue());
            OptionImpl<int[], Mode> healthy = built("healthy", o -> new CyclingControl<>(o, Mode.class));
            OptionGroup.Builder group = OptionGroup.createBuilder();
            group.add(broken);
            group.add(healthy);
            OptionPage page = new OptionPage(OptionIdentifier.create("control_edge_cases_test", "page"), TextComponent.literal("Page"), List.of(group.build()));
            OptionPageFrame frame = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page, o -> true);
            List<ControlElement<?>> rows = frame.interactableChildren().filter(ControlElement.class::isInstance).<ControlElement<?>>map(child -> (ControlElement<?>) child).toList();
            assertEquals(List.of(broken, healthy), rows.stream().map(ControlElement::getOption).toList());
            assertInstanceOf(PlaceholderControlElement.class, rows.get(0));
            assertFalse(rows.get(1) instanceof PlaceholderControlElement<?>);
        });
    }

    @Test
    void aControlFactoryThatFailsWithAnInterruptionKeepsTheFlagAndAVirtualMachineErrorIsNotHeld() {
        OptionImpl<int[], Mode> interrupted = built("interrupted", o -> {
            throw ControlEdgeCasesTest.<RuntimeException>sneak(new InterruptedException("made"));
        });
        assertTrue(Thread.interrupted());
        assertInstanceOf(InterruptedException.class, assertThrows(IllegalStateException.class, interrupted::getControl).getCause());
        assertThrows(StackOverflowError.class, () -> built("overflowing", o -> {
            throw new StackOverflowError();
        }));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable failure) throws E {
        throw (E) failure;
    }

    @Test
    void noRowDrawsTextInARowShorterThanALineOfItButStillDrawsItsBackground() {
        List<Function<Dim2i, ControlElement<?>>> rows = new ArrayList<>(everyRow());
        rows.addAll(longTexts("name"));
        for (int kind = 0; kind < rows.size(); kind++) {
            for (int height = 1; height <= 8; height++) {
                ControlElement<?> row = rows.get(kind).apply(new Dim2i(0, 0, 300, height));
                row.setFocused(true);
                FakeDrawContext draw = new FakeDrawContext();
                row.render(draw, 5000, 5000, 0f);
                assertEquals(List.of(), draw.strings, "row " + kind + " height " + height);
                assertTrue(draw.drew("fill(0,0,300," + height + ","), "row " + kind + " height " + height);
            }
            // Nine high holds the nine pixel line exactly
            FakeDrawContext draw = new FakeDrawContext();
            rows.get(kind).apply(new Dim2i(0, 0, 300, 9)).render(draw, 5000, 5000, 0f);
            assertFalse(draw.strings.isEmpty(), "row " + kind);
        }
    }

    @Test
    void theLineHeightIsTheFontSourcesOwnOrTheGamesNineWhenItHasNone() {
        for (int lineHeight : new int[]{0, -5, Integer.MIN_VALUE}) {
            FakeDrawContext unknown = new FakeDrawContext() {
                @Override
                public int lineHeight() {
                    return lineHeight;
                }
            };
            ControlElement<?> tall = everyRow().get(1).apply(new Dim2i(0, 0, 300, 9));
            tall.render(unknown, 5000, 5000, 0f);
            assertEquals(List.of("Cycle", "B"), unknown.strings, "line height " + lineHeight);
            unknown.strings.clear();
            everyRow().get(1).apply(new Dim2i(0, 0, 300, 8)).render(unknown, 5000, 5000, 0f);
            assertEquals(List.of(), unknown.strings, "line height " + lineHeight);
        }
        // A twenty pixel line raised four above the centre needs a row of 31, whose centre at 15 puts the line at 11 to 31
        for (int height : new int[]{30, 31}) {
            FakeDrawContext large = new FakeDrawContext() {
                @Override
                public int lineHeight() {
                    return 20;
                }
            };
            everyRow().get(1).apply(new Dim2i(0, 0, 300, height)).render(large, 5000, 5000, 0f);
            assertEquals(height == 31 ? List.of("Cycle", "B") : List.of(), large.strings, "height " + height);
        }
    }

    @Test
    void aRowSoFarOutThatItsEdgesWrapAnIntDrawsNothingAndTakesNoFocus() {
        Dim2i[] far = {new Dim2i(Integer.MAX_VALUE - 5, 0, 100, 18), new Dim2i(Integer.MIN_VALUE, 0, 100, 18), new Dim2i(0, Integer.MAX_VALUE - 5, 200, 18),
                new Dim2i(0, Integer.MIN_VALUE, 200, 18), new Dim2i(-(1 << 30) - 1, 0, 200, 18), new Dim2i(0, 0, (1 << 30) + 1, 18)};
        List<Function<Dim2i, ControlElement<?>>> rows = everyRow();
        for (int kind = 0; kind < rows.size(); kind++) {
            for (Dim2i dim : far) {
                ControlElement<?> row = rows.get(kind).apply(dim);
                row.setFocused(true);
                FakeDrawContext draw = new FakeDrawContext();
                row.render(draw, dim.x() + 10, dim.y() + 9, 0f);
                assertEquals(List.of(), draw.calls, "row " + kind + " " + dim);
                assertFalse(row.canFocus(), "row " + kind + " " + dim);
            }
            // Right up to a billion pixels out either way is still drawn
            for (Dim2i dim : new Dim2i[]{new Dim2i(-(1 << 30), -(1 << 30), 200, 18), new Dim2i((1 << 30) - 200, (1 << 30) - 18, 200, 18)}) {
                FakeDrawContext draw = new FakeDrawContext();
                rows.get(kind).apply(dim).render(draw, 0, 0, 0f);
                assertTrue(draw.drew("fill(" + dim.x() + "," + dim.y() + ","), "row " + kind + " " + dim);
            }
        }
    }

    @Test
    void aFontSourceGivingNegativeWidthsCannotPushAnyTextPastTheRow() {
        FakeDrawContext negative = new FakeDrawContext() {
            @Override
            public int getStringWidth(TextComponent component) {
                return -6 * extractString(component).length() - 1;
            }
        };
        List<Function<Dim2i, ControlElement<?>>> rows = new ArrayList<>(everyRow());
        rows.addAll(longTexts("name"));
        for (int kind = 0; kind < rows.size(); kind++) {
            for (boolean focused : new boolean[]{false, true}) {
                ControlElement<?> row = rows.get(kind).apply(new Dim2i(0, 0, 200, 18));
                row.setFocused(focused);
                negative.strings.clear();
                row.render(negative, 5000, 5000, 0f);
                // Only the button's arrow, which is placed without being measured
                for (String text : negative.strings) {
                    assertEquals(">", text, "row " + kind + " " + negative.strings);
                }
            }
        }
    }

    // Fake fonts counting the text work rows do themselves, apart from what the fake's own drawString measures, with a version the test moves on
    private static class Working extends FakeDrawContext {
        int work;
        long version;
        String translation = "FIRST";
        private boolean drawing;

        @Override
        public int getStringWidth(TextComponent component) {
            if (!this.drawing) {
                this.work++;
            }
            return super.getStringWidth(component);
        }

        @Override
        public String extractString(TextComponent component) {
            if (!this.drawing) {
                this.work++;
            }
            if (component instanceof TextComponent.Translatable translatable && translatable.keys().getFirst().equals("control_edge_cases_test.mode")) {
                return this.translation;
            }
            return super.extractString(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.work++;
            return super.substrByWidth(str, maxWidth);
        }

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.drawing = true;
            try {
                return super.drawString(str, x, y, color, shadow);
            } finally {
                this.drawing = false;
            }
        }

        // Not marked as an override so the fake also stands for a context written before versions existed
        public long textVersion() {
            return this.version;
        }
    }

    @Test
    void anUnchangedFrameDoesNoTextWorkInAnyRowIncludingTheButtonsLongLabel() {
        List<Function<Dim2i, ControlElement<?>>> rows = new ArrayList<>(everyRow());
        rows.addAll(longTexts("name"));
        for (int kind = 0; kind < rows.size(); kind++) {
            for (boolean focused : new boolean[]{false, true}) {
                ControlElement<?> row = rows.get(kind).apply(new Dim2i(0, 0, 200, 18));
                row.setFocused(focused);
                Working draw = new Working();
                row.render(draw, 5000, 5000, 0f);
                List<String> first = List.copyOf(draw.strings);
                for (int frame = 0; frame < 4; frame++) {
                    draw.work = 0;
                    draw.strings.clear();
                    row.render(draw, 5000, 5000, 0f);
                    assertEquals(0, draw.work, "row " + kind + " focused " + focused);
                    assertEquals(first, draw.strings, "row " + kind + " focused " + focused);
                }
            }
        }
    }

    @Test
    void aLanguageReloadInTheSameFontSourceRefitsEveryControlsTextOnTheNextFrame() {
        TextComponent translated = TextComponent.translatable("control_edge_cases_test.mode");
        List<ControlElement<?>> rows = List.of(
                bound(new CyclingControl<>(valued("Cycle", Mode.A), Mode.values(), new TextComponent[]{translated, translated, translated})).createElement(new Dim2i(0, 0, 200, 18)),
                bound(new SliderControl(valued("Slide", 5), 0, 10, 1, value -> translated)).createElement(new Dim2i(0, 0, 200, 18)),
                bound(new ActionButtonControl(valued("Button", false), translated, () -> { })).createElement(new Dim2i(0, 0, 200, 18)));
        // Right-aligned 6 in from the 200 edge at its new width, and the button's cut to the 34 its 52 pixel column leaves after the ellipsis, 18 in
        List<String> placed = List.of("text(SECOND AND LONGER,92,", "text(SECOND AND LONGER,92,", "text(SECON...,134,");
        for (int i = 0; i < rows.size(); i++) {
            ControlElement<?> row = rows.get(i);
            Working draw = new Working();
            row.render(draw, 5000, 5000, 0f);
            assertTrue(draw.strings.contains("FIRST"), draw.strings::toString);
            draw.translation = "SECOND AND LONGER";
            draw.version = 1;
            draw.calls.clear();
            row.render(draw, 5000, 5000, 0f);
            assertTrue(draw.drew(placed.get(i)), draw.calls::toString);
        }
    }

    // Fake fonts in which underlined text is a hundred pixels wider, as a style that changes widths would make it
    private static final class WideUnderline extends FakeDrawContext {
        @Override
        public int getStringWidth(TextComponent component) {
            return super.getStringWidth(component) + (styled(component, TextFormattingStyle.UNDERLINE) ? 100 : 0);
        }
    }

    @Test
    void aButtonLabelCutThatItsUnderlineWidensPastTheRoomIsLeftOutRatherThanDrawnOverTheEdge() {
        // The button's own label is underlined, so its cut is left out and only the arrow drawn
        FakeDrawContext draw = new WideUnderline();
        bound(new ActionButtonControl(valued("Button", false), TextComponent.literal("Configure everything"), () -> { })).createElement(new Dim2i(0, 0, 200, 18)).render(draw, 0, 0, 0f);
        assertEquals(List.of("Button", ">"), draw.strings);
    }

    @Test
    void aDisabledStyleThatWidensTheNamePastItsRoomLeavesTheNameOut() {
        Option<Boolean> option = valued("Name", false);
        when(option.isAvailable()).thenReturn(false);
        ControlElement<Boolean> row = new ControlElement<>(option, new Dim2i(0, 0, 200, 18)) {
            @Override
            protected int measureColumnWidth(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext) {
                return 30;
            }

            @Override
            protected TextComponent formatDisabledControlValue(TextComponent value) {
                return TextComponent.literal("A HOOK THAT ADDS A GREAT DEAL OF TEXT TO THE NAME");
            }
        };
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(List.of(), draw.strings);
        assertTrue(draw.drew("fill(0,0,200,18,"));
    }

    @Test
    void theNameLimitIsTheRowsLeftEdgeWhereNoNameCanBeDrawn() {
        AtomicInteger limit = new AtomicInteger();
        for (int height : new int[]{8, 18}) {
            ControlElement<Boolean> row = new ControlElement<>(valued("Name", false), new Dim2i(10, 0, 200, height)) {
                @Override
                protected int measureColumnWidth(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext) {
                    return 30;
                }

                @Override
                public void render(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext, int mouseX, int mouseY, float delta) {
                    super.render(drawContext, mouseX, mouseY, delta);
                    limit.set(this.getNameLimitX());
                }
            };
            row.render(new FakeDrawContext(), 5000, 5000, 0f);
            // Eight high holds no line, so the name is not drawn and the whole row is free; eighteen holds "Name" from 16 to 40 and its gap
            assertEquals(height == 8 ? 10 : 46, limit.get(), "height " + height);
        }
    }

    @Test
    void aSliderWhoseTrackFitsButWhoseLineDoesNotShowsOnlyTheTrack() {
        FakeDrawContext tall = new FakeDrawContext() {
            @Override
            public int lineHeight() {
                return 20;
            }
        };
        ControlElement<?> row = bound(new SliderControl(valued("Slide", 5), 0, 10, 1, ControlValueFormatter.number())).createElement(new Dim2i(0, 0, 200, 18));
        row.setFocused(true);
        row.render(tall, 5000, 5000, 0f);
        assertEquals(List.of(), tall.strings);
        assertTrue(tall.drew("fill(104,8,194,9,"), tall.calls::toString);
    }
}
