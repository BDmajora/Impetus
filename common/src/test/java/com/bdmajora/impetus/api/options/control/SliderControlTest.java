package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SliderControlTest {
    // Name "Slide" from 6 to 36, the track from 104 to 194 over rows 4 to 14 with its line on row 8, text on line 5 (centre 9 less half the glyph height)
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 18);
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ACCENT = DefaultColors.ELEMENT_ACTIVATED;
    private static final int TRACK = ColorARGB.withAlpha(ACCENT, 0xB8);

    // A piece of text drawn with the component it came from, where and in what colour
    private record Drawn(TextComponent component, String text, int x, int y, int color) { }

    // A filled box by its edges and colour
    private record Fill(int x1, int y1, int x2, int y2, int color) {
        Fill moved(int dx, int dy) {
            return new Fill(this.x1 + dx, this.y1 + dy, this.x2 + dx, this.y2 + dy, this.color);
        }
    }

    // Fake fonts keeping every fill and piece of text drawn and counting the text work done outside drawing, with a settable line height and text version
    private static class Recording extends FakeDrawContext {
        final List<Drawn> drawn = new ArrayList<>();
        final List<Fill> fills = new ArrayList<>();
        int work;
        long version;
        int lineHeight = 9;
        private boolean drawing;

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            this.fills.add(new Fill(x1, y1, x2, y2, color));
            super.fill(x1, y1, x2, y2, color);
        }

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.drawing = true;
            try {
                this.drawn.add(new Drawn(str, extractString(str), x, y, color));
                return super.drawString(str, x, y, color, shadow);
            } finally {
                this.drawing = false;
            }
        }

        @Override
        public int getStringWidth(TextComponent component) {
            if (!this.drawing) {
                this.work++;
            }
            return super.getStringWidth(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.work++;
            return super.substrByWidth(str, maxWidth);
        }

        @Override
        public long textVersion() {
            return this.version;
        }

        @Override
        public int lineHeight() {
            return this.lineHeight;
        }

        // Times an accent was asked for
        int accents;

        @Override
        public int getModAccentColor(String modId) {
            this.accents++;
            return super.getModAccentColor(modId);
        }

        // The track's line, which is the only fill in its colour
        List<Fill> tracks() {
            return this.fills.stream().filter(fill -> fill.color() == TRACK).toList();
        }

        // The thumb, the only accent fill four wide and ten high, since the focus outline is one pixel thick
        List<Fill> thumbs() {
            return this.fills.stream().filter(fill -> fill.color() == ACCENT && fill.x2() - fill.x1() == 4 && fill.y2() - fill.y1() == 10).toList();
        }

        // The last piece of text drawn, which is the value when one is drawn
        Drawn last() {
            return this.drawn.getLast();
        }

        void clear() {
            this.drawn.clear();
            this.fills.clear();
            this.strings.clear();
            this.calls.clear();
            this.work = 0;
        }
    }

    // A mock option named and holding a value that clicks and keys write back, available and unchanged
    private static <T> Option<T> valued(String name, T initial) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        Mockito.doAnswer(invocation -> holder[0] = invocation.getArgument(0)).when(option).setValue(any());
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // Binds the control to its option as the pages do and returns it
    private static <T, C extends Control<T>> C bound(C control) {
        when(control.getOption().getControl()).thenReturn(control);
        return control;
    }

    // A bound slider's row showing plain numbers
    private static ControlElement<Integer> row(Option<Integer> option, int min, int max, int interval, Dim2i dim) {
        return bound(new SliderControl(option, min, max, interval, ControlValueFormatter.number())).createElement(dim);
    }

    // One frame of the row with the cursor at the given place
    private static Recording render(ControlElement<?> row, int mouseX, int mouseY) {
        Recording draw = new Recording();
        row.render(draw, mouseX, mouseY, 0f);
        return draw;
    }

    // Asserts the call throws the given type with the given message
    private static <E extends Throwable> E refused(Class<E> type, String message, Executable call) {
        E thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        return thrown;
    }

    private static boolean click(ControlElement<?> row, double mouseX, double mouseY) {
        return row.mouseClicked(new FakeInteractionContext(), mouseX, mouseY, 0);
    }

    private static boolean drag(ControlElement<?> row, double mouseX, double mouseY) {
        return row.mouseDragged(new FakeInteractionContext(), mouseX, mouseY, 0, 0, 0);
    }

    private static boolean key(ControlElement<?> row, int keyCode) {
        return row.keyTyped(new FakeInteractionContext(), '\0', keyCode);
    }

    @Test
    void theControlHoldsItsOptionAsksForA130PixelColumnWhateverTheFontsAndHandsOutAFreshRowAtExactlyTheBoundsGiven() {
        Option<Integer> option = valued("Slide", 4);
        clearInvocations(option);
        SliderControl control = new SliderControl(option, 0, 10, 2, ControlValueFormatter.number());
        assertSame(option, control.getOption());
        assertEquals(130, control.getMaxWidth());
        assertEquals(130, control.getMaxWidth(new FakeDrawContext()));
        assertSame(Integer.class, control.getValueType());
        Dim2i dim = new Dim2i(3, 4, 150, 18);
        ControlElement<Integer> first = control.createElement(dim);
        assertNotSame(first, control.createElement(dim));
        assertSame(option, first.getOption());
        assertSame(dim, first.getDimensions());
        refused(NullPointerException.class, "Dimensions must not be null", () -> control.createElement(null));
        // Making the control and its rows never touches the option
        verifyNoInteractions(option);
    }

    @Test
    void aMissingOptionIsRefusedFirstThenAMissingFormatterThenEachBadPartOfTheRangeByItsValues() {
        ControlValueFormatter number = ControlValueFormatter.number();
        Option<Integer> option = valued("Slide", 0);
        refused(NullPointerException.class, "Option must not be null", () -> new SliderControl(null, 10, 0, 0, null));
        // Every missing argument is refused as the other controls refuse theirs, before the range is looked at
        refused(NullPointerException.class, "Formatter must not be null", () -> new SliderControl(option, 10, 0, 0, null));
        refused(IllegalArgumentException.class, "Slider maximum 0 must be greater than its minimum 10", () -> new SliderControl(option, 10, 0, 0, number));
        refused(IllegalArgumentException.class, "Slider maximum 5 must be greater than its minimum 5", () -> new SliderControl(option, 5, 5, 1, number));
        refused(IllegalArgumentException.class, "Slider maximum -2147483648 must be greater than its minimum 2147483647",
                () -> new SliderControl(option, Integer.MAX_VALUE, Integer.MIN_VALUE, 1, number));
        for (int interval : new int[]{0, -1, -2, Integer.MIN_VALUE}) {
            refused(IllegalArgumentException.class, "Slider interval " + interval + " must be greater than zero", () -> new SliderControl(option, 0, 10, interval, number));
        }
        refused(IllegalArgumentException.class, "Slider range 0 to 10 is not a whole number of intervals of 3", () -> new SliderControl(option, 0, 10, 3, number));
        refused(IllegalArgumentException.class, "Slider range 0 to 10 is not a whole number of intervals of 20", () -> new SliderControl(option, 0, 10, 20, number));
        refused(IllegalArgumentException.class, "Slider range 0 to 10 is not a whole number of intervals of 2147483647", () -> new SliderControl(option, 0, 10, Integer.MAX_VALUE, number));
        // The whole int range spans 2^32 - 1, which is odd
        refused(IllegalArgumentException.class, "Slider range -2147483648 to 2147483647 is not a whole number of intervals of 2",
                () -> new SliderControl(option, Integer.MIN_VALUE, Integer.MAX_VALUE, 2, number));
        // Two stops, an interval equal to the range, and every divisor of 2^32 - 1 (3 x 5 x 17 x 257 x 65537) that fits an int over the whole range
        assertDoesNotThrow(() -> new SliderControl(option, 0, 1, 1, number));
        assertDoesNotThrow(() -> new SliderControl(option, 0, 10, 10, number));
        assertDoesNotThrow(() -> new SliderControl(option, -10, -5, 5, number));
        assertDoesNotThrow(() -> new SliderControl(option, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, 1, number));
        assertDoesNotThrow(() -> new SliderControl(option, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 1, number));
        for (int interval : new int[]{1, 3, 5, 15, 17, 255, 257, 65535, 65537, 0x55555555}) {
            assertDoesNotThrow(() -> new SliderControl(option, Integer.MIN_VALUE, Integer.MAX_VALUE, interval, number), "interval " + interval);
        }
    }

    @Test
    void aHoveredSliderDrawsItsTrackSixInFromTheRightEdgeWithTheThumbAtTheValueAndTheValueSixShortOfTheTrack() {
        ControlElement<Integer> row = row(valued("Slide", 4), 0, 10, 2, new Dim2i(10, 20, 200, 18));
        Recording draw = render(row, 11, 21);
        // The 90 pixel track from 114 to 204, its line on row 28: centre 29 less half the 10 pixel slider, then 4 down
        Fill track = new Fill(114, 28, 204, 29, TRACK);
        // 4 of 0..10 lies 36 pixels along it, the 4 pixel thumb centred there over the slider's height
        Fill thumb = new Fill(148, 24, 152, 34, ACCENT);
        assertEquals(List.of(track), draw.tracks());
        assertEquals(List.of(thumb), draw.thumbs());
        assertTrue(draw.fills.indexOf(thumb) < draw.fills.indexOf(track));
        // The value ends 6 short of the track at 108, on the name's line
        assertEquals(List.of(new Drawn(TextComponent.literal("Slide").withStyle(TextFormattingStyle.WHITE), "Slide", 16, 25, WHITE),
                new Drawn(TextComponent.literal("4"), "4", 102, 25, WHITE)), draw.drawn);
    }

    // One hovered frame of a 0..7 slider holding the value, at the bounds
    private static Recording hovered(Dim2i dim, int value) {
        return render(row(valued("Slide", value), 0, 7, 1, dim), dim.x(), dim.y());
    }

    @Test
    void aRowAnywhereOnScreenDrawsAndHitsItsTrackThumbAndValueAtTheSamePlacesWithinIt() {
        // 3 of 0..7 lies 38.57 pixels along the track, a place between two pixels
        for (int height : new int[]{10, 11, 17, 18, 19, 40}) {
            Recording origin = hovered(new Dim2i(0, 0, 200, height), 3);
            // The line 4 below the slider's top, which sits 5 above the centre
            assertEquals(List.of(new Fill(104, height / 2 - 1, 194, height / 2, TRACK)), origin.tracks(), "height " + height);
            assertEquals(List.of(new Fill(140, height / 2 - 5, 144, height / 2 + 5, ACCENT)), origin.thumbs(), "height " + height);
            for (int[] at : new int[][]{{-300, -40}, {-7, -13}, {-1, -1}, {-205, -5}, {-104, -9}, {37, 101}}) {
                Dim2i dim = new Dim2i(at[0], at[1], 200, height);
                Recording moved = hovered(dim, 3);
                String context = dim + " " + moved.fills;
                assertEquals(origin.fills.stream().map(fill -> fill.moved(at[0], at[1])).toList(), moved.fills, context);
                assertEquals(origin.drawn.stream().map(text -> new Drawn(text.component(), text.text(), text.x() + at[0], text.y() + at[1], text.color())).toList(), moved.drawn, context);
                // The track's first pixel is the minimum, its last the maximum and its middle the stop nearest halfway
                Option<Integer> option = valued("Slide", 3);
                ControlElement<Integer> row = row(option, 0, 7, 1, dim);
                int centre = dim.getCenterY();
                for (int[] hit : new int[][]{{104, 0}, {193, 7}, {148, 3}, {149, 4}}) {
                    assertTrue(click(row, at[0] + hit[0], centre), context);
                    assertEquals(hit[1], option.getValue(), context + " at " + hit[0]);
                }
            }
        }
    }

    @Test
    void theTrackShowsOnlyWhileTheRowIsHoveredFocusedOrHeldAndTheValueAloneIsRightAlignedOtherwise() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        Recording idle = render(row, 5000, 5000);
        assertEquals(List.of(), idle.tracks());
        assertEquals(List.of(), idle.thumbs());
        assertEquals(new Drawn(TextComponent.literal("4"), "4", 188, 5, WHITE), idle.last());
        // Hovered at the row's corners, its far edges lying outside it
        for (int[] at : new int[][]{{0, 0}, {199, 17}, {0, 17}, {199, 0}}) {
            assertEquals(1, render(row, at[0], at[1]).tracks().size(), at[0] + "," + at[1]);
        }
        for (int[] at : new int[][]{{200, 9}, {100, 18}, {-1, 9}, {100, -1}}) {
            assertEquals(List.of(), render(row, at[0], at[1]).tracks(), at[0] + "," + at[1]);
        }
        // Focused with the cursor elsewhere, the value moved beside the track
        row.setFocused(true);
        Recording focused = render(row, 5000, 5000);
        assertEquals(List.of(new Fill(104, 8, 194, 9, TRACK)), focused.tracks());
        assertEquals(new Drawn(TextComponent.literal("4"), "4", 92, 5, WHITE), focused.last());
        row.setFocused(false);
        // Held by a press on the track's first pixel and dragged off the row, until the release
        assertTrue(click(row, 104, 9));
        assertEquals(0, option.getValue());
        assertTrue(drag(row, -5000, -5000));
        assertEquals(List.of(new Fill(102, 4, 106, 14, ACCENT)), render(row, 5000, 5000).thumbs());
        assertTrue(row.mouseReleased(new FakeInteractionContext(), -5000, -5000, 0));
        Recording released = render(row, 5000, 5000);
        assertEquals(List.of(), released.tracks());
        assertEquals(new Drawn(TextComponent.literal("0"), "0", 188, 5, WHITE), released.last());
    }

    @Test
    void anUnavailableSliderShowsOnlyItsValueGreyedAndStruckThroughHoveredFocusedOrHeldAndPlainOnceAvailableAgain() {
        Option<Integer> option = valued("Slide", 6);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        assertTrue(click(row, 150, 9));
        row.setFocused(true);
        when(option.isAvailable()).thenReturn(false);
        Recording greyed = render(row, 150, 9);
        assertEquals(List.of(), greyed.tracks());
        assertEquals(List.of(), greyed.thumbs());
        assertEquals(new Drawn(TextComponent.literal("6").withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), "6", 188, 5, DefaultColors.TEXT_DISABLED), greyed.last());
        when(option.isAvailable()).thenReturn(true);
        Recording plain = render(row, 5000, 5000);
        assertEquals(List.of(new Fill(104, 8, 194, 9, TRACK)), plain.tracks());
        assertEquals(new Drawn(TextComponent.literal("6"), "6", 92, 5, WHITE), plain.last());
    }

    @Test
    void aValueOutsideTheRangeIsShownAsItIsWithTheThumbHeldAtTheEndItIsPast() {
        for (int value : new int[]{0, -1, -7, Integer.MIN_VALUE}) {
            Recording draw = hoveredAt(value);
            assertEquals(List.of(new Fill(102, 4, 106, 14, ACCENT)), draw.thumbs(), "value " + value);
        }
        for (int value : new int[]{10, 11, 15, Integer.MAX_VALUE}) {
            Recording draw = hoveredAt(value);
            // Two pixels past the track's end, still inside the six pixel inset
            assertEquals(List.of(new Fill(192, 4, 196, 14, ACCENT)), draw.thumbs(), "value " + value);
        }
        assertEquals("-7", hoveredAt(-7).last().text());
        assertEquals("15", hoveredAt(15).last().text());
    }

    // One frame of a 0..10 slider in twos holding the value, hovered on its track
    private static Recording hoveredAt(int value) {
        return render(row(valued("Slide", value), 0, 10, 2, DIM), 150, 9);
    }

    @Test
    void aSliderNeedsARowTenHighAndThirteenWideOrItShowsOnlyItsValueAndCannotBeGrabbedThoughTheArrowsStillStepIt() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 200, 10), new Dim2i(0, 0, 13, 18)}) {
            Recording draw = hovered(dim, 3);
            assertEquals(1, draw.tracks().size(), dim::toString);
        }
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 200, 9), new Dim2i(0, 0, 12, 18), new Dim2i(0, 0, 13, 9)}) {
            Option<Integer> option = valued("Slide", 5);
            ControlElement<Integer> row = row(option, 0, 10, 1, dim);
            row.setFocused(true);
            Recording draw = render(row, dim.x() + 1, dim.y() + 1);
            assertEquals(List.of(), draw.tracks(), dim::toString);
            assertEquals(List.of(), draw.thumbs(), dim::toString);
            // A press anywhere in the row is taken but grabs nothing
            for (int x = 0; x < dim.width(); x++) {
                assertTrue(click(row, x, dim.getCenterY()), dim + " at " + x);
                assertFalse(drag(row, x, dim.getCenterY()), dim + " at " + x);
            }
            verify(option, never()).setValue(any());
            assertTrue(key(row, Keys.RIGHT));
            assertEquals(6, option.getValue());
        }
    }

    @Test
    void aRowNarrowerThanTheTrackNarrowsItToKeepSixPixelsFreeAtEitherEnd() {
        for (int width = 13; width <= 120; width++) {
            int track = Math.min(90, width - 12);
            assertEquals(List.of(new Fill(width - 6 - track, 8, width - 6, 9, TRACK)), hovered(new Dim2i(0, 0, width, 18), 3).tracks(), "width " + width);
        }
    }

    @Test
    void aOnePixelTrackSetsTheMinimumWhereverItIsGrabbedAndDrawsTheThumbAtEitherEnd() {
        // Thirteen wide leaves one pixel between the two six pixel insets
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = row(option, 0, 10, 1, new Dim2i(0, 0, 13, 18));
        for (double x : new double[]{6, 6.5, 6.99}) {
            option.setValue(5);
            assertTrue(click(row, x, 9));
            assertEquals(0, option.getValue(), "at " + x);
            assertTrue(drag(row, 500, 9));
            assertEquals(0, option.getValue(), "at " + x);
        }
        row.setFocused(true);
        assertEquals(List.of(new Fill(4, 4, 8, 14, ACCENT)), render(row, 5000, 5000).thumbs());
        option.setValue(10);
        assertEquals(List.of(new Fill(5, 4, 9, 14, ACCENT)), render(row, 5000, 5000).thumbs());
        assertEquals(List.of(new Fill(6, 8, 7, 9, TRACK)), render(row, 5000, 5000).tracks());
    }

    @Test
    void aRowWithNoAreaOrTooFarOutToBeDrawnTakesNoClickDragOrKeyEvenWithTheCursorOnItsTrackOrFocusForcedOnIt() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, (1 << 30) + 1, 18), new Dim2i(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 18), new Dim2i(0, (1 << 30) - 10, 200, 18),
                new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, 200, -18)}) {
            Option<Integer> option = valued("Slide", 5);
            ControlElement<Integer> row = row(option, 0, 10, 1, dim);
            clearInvocations(option);
            // Where the track would lie, 50 in from the right edge on the centre line
            double x = (double) dim.x() + dim.width() - 50;
            double y = dim.getCenterY();
            assertFalse(click(row, x, y), dim::toString);
            assertFalse(drag(row, x, y), dim::toString);
            assertFalse(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertFalse(key(row, Keys.RIGHT), dim::toString);
            assertFalse(key(row, Keys.LEFT), dim::toString);
            verify(option, never()).getValue();
            verify(option, never()).setValue(any());
        }
    }

    @Test
    void anIdleRowWithNoRoomForALineDrawsNoValueAndNeverReadsTheOption() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 200, 8), new Dim2i(0, 0, 200, 1)}) {
            // Too short for the slider as well, so neither hovering nor focus shows it
            for (boolean focused : new boolean[]{false, true}) {
                Option<Integer> option = valued("Slide", 5);
                ControlElement<Integer> row = row(option, 0, 10, 1, dim);
                row.setFocused(focused);
                Recording draw = render(row, 1, 0);
                assertEquals(List.of(), draw.strings, dim::toString);
                verify(option, never()).getValue();
            }
        }
        // Lines twenty high leave no room in an eighteen high row, where only the shown track needs the value
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = row(option, 0, 10, 1, DIM);
        Recording tall = new Recording();
        tall.lineHeight = 20;
        row.render(tall, 5000, 5000, 0f);
        assertEquals(List.of(), tall.strings);
        verify(option, never()).getValue();
        row.setFocused(true);
        row.render(tall, 5000, 5000, 0f);
        assertEquals(List.of(), tall.strings);
        assertEquals(List.of(new Fill(104, 8, 194, 9, TRACK)), tall.tracks());
        assertEquals(List.of(new Fill(147, 4, 151, 14, ACCENT)), tall.thumbs());
    }

    @Test
    void aFormatterThatGivesNothingLeavesTheNameAloneIdleAndTheNameAndTrackWhenShown() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = bound(new SliderControl(option, 0, 10, 1, value -> null)).createElement(DIM);
        assertEquals(List.of("Slide"), render(row, 5000, 5000).strings);
        when(option.isAvailable()).thenReturn(false);
        assertEquals(List.of("Slide"), render(row, 5000, 5000).strings);
        when(option.isAvailable()).thenReturn(true);
        row.setFocused(true);
        Recording shown = render(row, 5000, 5000);
        assertEquals(List.of("Slide"), shown.strings);
        assertEquals(List.of(new Fill(104, 8, 194, 9, TRACK)), shown.tracks());
    }

    @Test
    void aClickOnTheTrackJumpsToTheStopNearestTheCursorFromTheFirstPixelToTheLast() {
        // 0..10 in twos over the 90 pixel track from 104, whose first pixel is the minimum and whose last, 193, the maximum
        for (int pixel = 104; pixel <= 193; pixel++) {
            Option<Integer> option = valued("Slide", -1);
            ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
            assertTrue(click(row, pixel, 9));
            assertEquals((int) Math.round((pixel - 104) / 89.0 * 5) * 2, option.getValue(), "pixel " + pixel);
        }
        // Anywhere over the slider's ten rows, and a cursor between pixels at the last one
        for (double[] at : new double[][]{{193.9, 4}, {193.9, 13.99}, {104, 4}}) {
            Option<Integer> option = valued("Slide", -1);
            assertTrue(click(row(option, 0, 10, 2, DIM), at[0], at[1]));
            assertEquals(at[0] > 150 ? 10 : 0, option.getValue(), at[0] + "," + at[1]);
        }
    }

    @Test
    void aClickInTheRowBesideTheTrackIsTakenButNeitherReadsNorMovesTheValueNorStartsADrag() {
        for (double[] at : new double[][]{{0, 0}, {103.99, 9}, {150, 3.99}, {150, 14}, {194, 9}, {199.99, 17.99}}) {
            Option<Integer> option = valued("Slide", 4);
            ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
            clearInvocations(option);
            String context = at[0] + "," + at[1];
            assertTrue(click(row, at[0], at[1]), context);
            assertFalse(drag(row, 150, 9), context);
            assertFalse(row.mouseReleased(new FakeInteractionContext(), 150, 9, 0), context);
            verify(option, never()).getValue();
            verify(option, never()).setValue(any());
        }
    }

    @Test
    void onlyALeftClickInsideTheRowOfAnAvailableOptionIsTaken() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        clearInvocations(option);
        for (int button : new int[]{1, 2, -1, 7}) {
            assertFalse(row.mouseClicked(new FakeInteractionContext(), 150, 9, button), "button " + button);
        }
        // Just past each edge, the right and bottom edges themselves, and no place at all
        for (double[] at : new double[][]{{-0.01, 9}, {200, 9}, {150, -0.01}, {150, 18}, {Double.NaN, 9}, {150, Double.NaN}}) {
            assertFalse(click(row, at[0], at[1]), at[0] + "," + at[1]);
        }
        verify(option, never()).isAvailable();
        verify(option, never()).getValue();
        when(option.isAvailable()).thenReturn(false);
        assertFalse(click(row, 150, 9));
        assertFalse(drag(row, 150, 9));
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
    }

    @Test
    void aHeldSliderFollowsTheCursorPastEitherEndOfTheTrackWritingOnlyWhenTheStopChanges() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        assertTrue(click(row, 150, 9));
        assertEquals(6, option.getValue());
        clearInvocations(option);
        // Every place from 148.5 to 166.3 rounds to the fourth stop, as does any height
        for (double x = 149; x <= 166; x += 0.5) {
            assertTrue(drag(row, x, 9), "at " + x);
        }
        assertTrue(drag(row, 160, 5000));
        assertTrue(drag(row, 160, -5000));
        verify(option, never()).setValue(any());
        double[] places = {500, -500, Double.MAX_VALUE, -Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 150, Double.NaN};
        int[] expected = {10, 0, 10, 0, 10, 0, 6, 0};
        for (int i = 0; i < places.length; i++) {
            assertTrue(drag(row, places[i], 9), "at " + places[i]);
            assertEquals(expected[i], option.getValue(), "at " + places[i]);
        }
        verify(option, times(places.length)).setValue(any());
    }

    @Test
    void onlyTheLeftReleaseEndsADragAndOnlyTheOneItWasPartOf() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = row(option, 0, 10, 1, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        assertTrue(click(row, 150, 9));
        assertTrue(drag(row, 160, 9));
        assertEquals(1, render(row, 5000, 5000).tracks().size());
        assertTrue(row.mouseReleased(context, 160, 9, 0));
        assertFalse(row.mouseReleased(context, 160, 9, 0));
        assertEquals(List.of(), render(row, 5000, 5000).tracks());
        assertFalse(drag(row, 120, 9));
        // Another button's release leaves the drag going
        assertTrue(click(row, 150, 9));
        assertFalse(row.mouseReleased(context, 150, 9, 1));
        assertTrue(drag(row, 120, 9));
        // As does a drag by another button, which moves nothing
        assertFalse(row.mouseDragged(context, 190, 9, 1, 0, 0));
        assertTrue(drag(row, 121, 9));
        assertEquals(2, option.getValue());
        assertEquals(0, context.clicks);
    }

    @Test
    void anyNewClickEndsADragWhicheverButtonAndWhereverItLands() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = row(option, 0, 10, 1, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        double[][] presses = {{150, 9, 1}, {5, 9, 0}, {500, 9, 0}, {150, 9, 2}};
        for (double[] press : presses) {
            assertTrue(click(row, 150, 9));
            row.mouseClicked(context, press[0], press[1], (int) press[2]);
            String at = press[0] + "," + press[1] + " button " + (int) press[2];
            assertFalse(drag(row, 120, 9), at);
            assertFalse(row.mouseReleased(context, 120, 9, 0), at);
            assertEquals(5, option.getValue(), at);
        }
    }

    @Test
    void aDragWhileTheOptionIsUnavailableIsIgnoredAndPicksUpAgainOnceAvailable() {
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = row(option, 0, 10, 1, DIM);
        assertTrue(click(row, 150, 9));
        when(option.isAvailable()).thenReturn(false);
        assertFalse(drag(row, 104, 9));
        assertEquals(5, option.getValue());
        when(option.isAvailable()).thenReturn(true);
        assertTrue(drag(row, 104, 9));
        assertEquals(0, option.getValue());
    }

    @Test
    void eachClickOnTheTrackDragAndArrowReadsTheOptionOnce() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        clearInvocations(option);
        assertTrue(click(row, 150, 9));
        verify(option, times(1)).getValue();
        clearInvocations(option);
        assertTrue(drag(row, 170, 9));
        verify(option, times(1)).getValue();
        row.setFocused(true);
        for (int code : new int[]{Keys.RIGHT, Keys.RIGHT, Keys.LEFT}) {
            clearInvocations(option);
            assertTrue(key(row, code));
            verify(option, times(1)).getValue();
        }
    }

    @Test
    void aFocusedSliderStepsOneIntervalPerArrowAndStopsAtEitherEndWithoutWriting() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        assertFalse(key(row, Keys.RIGHT));
        assertEquals(4, option.getValue());
        assertTrue(row.canFocus());
        row.setFocused(true);
        for (int expected : new int[]{6, 8, 10, 10}) {
            assertTrue(key(row, Keys.RIGHT));
            assertEquals(expected, option.getValue());
        }
        for (int expected : new int[]{8, 6, 4, 2, 0, 0}) {
            assertTrue(key(row, Keys.LEFT));
            assertEquals(expected, option.getValue());
        }
        // A step past either end writes nothing
        verify(option, times(8)).setValue(any());
    }

    // The stops of a slider in order
    private static List<Long> stops(int min, int max, int interval) {
        List<Long> stops = new ArrayList<>();
        for (long stop = min; stop <= max; stop += interval) {
            stops.add(stop);
        }
        return stops;
    }

    @Test
    void anArrowMovesToTheNeighbouringStopTheWayItPointsFromAnyValueOnOrOffTheIntervalInsideOrOutsideTheRange() {
        int[][] sliders = {{0, 10, 2}, {2, 32, 1}, {50, 500, 25}, {-30, 30, 15}, {0, 2000, 50}, {-7, 5, 4}, {10, 260, 10}, {0, 1, 1},
                {Integer.MIN_VALUE, Integer.MAX_VALUE, 0x55555555}, {Integer.MAX_VALUE - 6, Integer.MAX_VALUE, 3}, {Integer.MIN_VALUE, Integer.MIN_VALUE + 10, 5}};
        for (int[] slider : sliders) {
            List<Long> stops = stops(slider[0], slider[1], slider[2]);
            // Every stop, either side of it, halfway to the next and an interval below, and both ends of the int range
            TreeSet<Long> values = new TreeSet<>(List.of((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE));
            for (long stop : stops) {
                for (long near : new long[]{stop - 1, stop, stop + 1, stop + slider[2] / 2, stop - slider[2]}) {
                    if (near >= Integer.MIN_VALUE && near <= Integer.MAX_VALUE) {
                        values.add(near);
                    }
                }
            }
            Option<Integer> option = valued("Slide", 0);
            ControlElement<Integer> row = row(option, slider[0], slider[1], slider[2], DIM);
            row.setFocused(true);
            for (long value : values) {
                for (boolean right : new boolean[]{true, false}) {
                    long expected = right ? stops.stream().filter(stop -> stop > value).findFirst().orElse(stops.getLast())
                            : stops.reversed().stream().filter(stop -> stop < value).findFirst().orElse(stops.getFirst());
                    option.setValue((int) value);
                    clearInvocations(option);
                    String context = java.util.Arrays.toString(slider) + " from " + value + (right ? " right" : " left");
                    assertTrue(key(row, right ? Keys.RIGHT : Keys.LEFT), context);
                    assertEquals((int) expected, option.getValue(), context);
                    verify(option, times(expected == value ? 0 : 1)).setValue(any());
                }
            }
        }
    }

    @Test
    void aSliderOverTheWholeIntRangeReachesBothEndsAndNeverWrapsRound() {
        Option<Integer> option = valued("Wide", 0);
        ControlElement<Integer> row = row(option, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, DIM);
        assertTrue(click(row, 104, 9));
        assertEquals(Integer.MIN_VALUE, option.getValue());
        assertTrue(drag(row, 193, 9));
        assertEquals(Integer.MAX_VALUE, option.getValue());
        assertTrue(drag(row, 148, 9));
        assertEquals((int) (Integer.MIN_VALUE + Math.round(44.0 / 89 * 4294967295L)), option.getValue());
        row.setFocused(true);
        option.setValue(Integer.MAX_VALUE);
        clearInvocations(option);
        assertTrue(key(row, Keys.RIGHT));
        verify(option, never()).setValue(any());
        assertTrue(key(row, Keys.LEFT));
        assertEquals(Integer.MAX_VALUE - 1, option.getValue());
        option.setValue(Integer.MIN_VALUE);
        clearInvocations(option);
        assertTrue(key(row, Keys.LEFT));
        verify(option, never()).setValue(any());
        assertTrue(key(row, Keys.RIGHT));
        assertEquals(Integer.MIN_VALUE + 1, option.getValue());
        // The thumb sits at either end of the track
        option.setValue(Integer.MIN_VALUE);
        assertEquals(List.of(new Fill(102, 4, 106, 14, ACCENT)), render(row, 5000, 5000).thumbs());
        option.setValue(Integer.MAX_VALUE);
        assertEquals(List.of(new Fill(192, 4, 196, 14, ACCENT)), render(row, 5000, 5000).thumbs());
    }

    @Test
    void aMissingValueIsShownAsTheMinimumStepsFromItAndIsAlwaysWritten() {
        Option<Integer> option = valued("Slide", null);
        ControlElement<Integer> row = row(option, 3, 9, 3, DIM);
        assertEquals(List.of("Slide", "3"), render(row, 5000, 5000).strings);
        row.setFocused(true);
        assertEquals(List.of(new Fill(102, 4, 106, 14, ACCENT)), render(row, 5000, 5000).thumbs());
        assertTrue(key(row, Keys.RIGHT));
        assertEquals(6, option.getValue());
        // Stepping or clicking onto the minimum still writes it, as the option holds nothing
        for (Runnable write : List.<Runnable>of(() -> key(row, Keys.LEFT), () -> click(row, 104, 9), () -> click(row, 110, 9))) {
            option.setValue(null);
            clearInvocations(option);
            write.run();
            verify(option).setValue(3);
        }
        // And dragging from there onto it again does not
        clearInvocations(option);
        assertTrue(drag(row, 105, 9));
        verify(option, never()).setValue(any());
    }

    @Test
    void aKeyTheSliderDoesNotTakeNeverAsksAboutTheOptionAndAnUnfocusedOrUnavailableOneTakesNone() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        clearInvocations(option);
        // Unfocused, the arrows are someone else's
        assertFalse(row.keyTyped(context, '\0', Keys.RIGHT));
        assertFalse(row.keyTyped(context, '\0', Keys.LEFT));
        row.setFocused(true);
        // Letters, Tab by key and by character, Escape, Up, Down and every press
        char[] typed = {'a', '\t', '\0', '\0', '\0', '\0', ' ', '\r', '\0'};
        int[] codes = {30, 0, Keys.TAB, 1, 200, 208, Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER};
        for (int i = 0; i < typed.length; i++) {
            assertFalse(row.keyTyped(context, typed[i], codes[i]), "key " + i);
        }
        verify(option, never()).isAvailable();
        verify(option, never()).getValue();
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.keyTyped(context, '\0', Keys.RIGHT));
        assertFalse(row.keyTyped(context, '\0', Keys.LEFT));
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        assertEquals(0, context.clicks);
    }

    @Test
    void theValueIsFormattedOnceForEachPlaceItIsShownAndAgainOnlyWhenItsValueStateRoomOrFontSourceChanges() {
        AtomicInteger formats = new AtomicInteger();
        Option<Integer> option = valued("Slide", 5);
        ControlElement<Integer> row = bound(new SliderControl(option, 0, 10, 1, value -> {
            formats.incrementAndGet();
            return TextComponent.literal(value + " UNITS");
        })).createElement(DIM);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(1, formats.get());
        // Repeated frames do no text work at all, idle or shown, each keeping its own fit
        for (boolean focused : new boolean[]{false, true, false, true}) {
            row.setFocused(focused);
            row.render(draw, 5000, 5000, 0f);
            for (int frame = 0; frame < 3; frame++) {
                draw.clear();
                row.render(draw, 5000, 5000, 0f);
                assertEquals(0, draw.work, "focused " + focused);
            }
        }
        assertEquals(2, formats.get());
        assertEquals(new Drawn(TextComponent.literal("5 UNITS"), "5 UNITS", 56, 5, WHITE), draw.last());
        // A new value is formatted once where it is shown
        option.setValue(6);
        row.render(draw, 5000, 5000, 0f);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(3, formats.get());
        // Greyed while unavailable, then shown again as it was
        when(option.isAvailable()).thenReturn(false);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(4, formats.get());
        when(option.isAvailable()).thenReturn(true);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(4, formats.get());
        // The idle fit was the greyed one, so the plain idle value is formatted again
        row.setFocused(false);
        draw.clear();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(5, formats.get());
        assertEquals(new Drawn(TextComponent.literal("6 UNITS"), "6 UNITS", 152, 5, WHITE), draw.last());
        // A new font source, then a new text version in it, each fit it again in both places
        Recording other = new Recording();
        row.render(other, 5000, 5000, 0f);
        row.setFocused(true);
        row.render(other, 5000, 5000, 0f);
        assertEquals(7, formats.get());
        other.version = 1;
        row.render(other, 5000, 5000, 0f);
        row.setFocused(false);
        row.render(other, 5000, 5000, 0f);
        assertEquals(9, formats.get());
    }

    @Test
    void aLongValueIsCutToTheRoomTheNameLeavesBesideTheRowsEdgeIdleAndBesideTheTrackShown() {
        ControlElement<Integer> row = bound(new SliderControl(valued("Slide", 4), 0, 10, 2, value -> TextComponent.literal("VALUE " + value + " WITH A VERY LONG UNIT NAME"))).createElement(DIM);
        // From the name's end and gap at 42 to 194 leaves 152, 134 after the ellipsis: 22 characters
        Recording idle = render(row, 5000, 5000);
        assertEquals(new Drawn(TextComponent.literal("VALUE 4 WITH A VERY LO..."), "VALUE 4 WITH A VERY LO...", 44, 5, WHITE), idle.last());
        // From 42 to 98 leaves 56, 38 after the ellipsis: six characters, the space they end on dropped
        row.setFocused(true);
        Recording shown = render(row, 5000, 5000);
        assertEquals(new Drawn(TextComponent.literal("VALUE..."), "VALUE...", 50, 5, WHITE), shown.last());
    }

    @Test
    void theThumbLiesAtTheExactWholePixelAlongTheTrackWhereFloatingPointWouldFallShort() {
        // 7 of 0..10 is 63 pixels along the 90 pixel track, which 0.7 x 90 in doubles makes 62.99999999999999
        assertEquals(List.of(new Fill(165, 4, 169, 14, ACCENT)), hoveredAt(7).thumbs());
        // Every value of ranges where that happens, over tracks of 22, 55, 88 and 90 pixels
        for (int range : new int[]{7, 10, 11, 15, 22, 23, 26}) {
            for (int width : new int[]{34, 67, 100, 102, 200}) {
                int track = Math.min(90, width - 12);
                Option<Integer> option = valued("Slide", 0);
                ControlElement<Integer> row = row(option, 0, range, 1, new Dim2i(0, 0, width, 18));
                for (int value = 0; value <= range; value++) {
                    option.setValue(value);
                    int x = width - 6 - track + value * track / range - 2;
                    assertEquals(List.of(new Fill(x, 4, x + 4, 14, ACCENT)), render(row, 1, 1).thumbs(), value + " of 0.." + range + " over " + track);
                }
            }
        }
    }

    @Test
    void eachFrameAsksWhereTheCursorIsOnceAndTheAccentIsLookedUpOncePerFontSource() {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> spied = Mockito.spy(row(option, 0, 10, 2, DIM));
        for (int[] at : new int[][]{{5000, 5000}, {150, 9}, {5000, 5000}}) {
            clearInvocations(spied);
            spied.render(new Recording(), at[0], at[1], 0f);
            verify(spied, times(1)).isMouseOver(anyDouble(), anyDouble());
        }
        // Focused, the outline and the thumb both take the accent, asked for once across every frame in one font source
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        clearInvocations(option);
        row.setFocused(true);
        Recording draw = new Recording();
        for (int frame = 0; frame < 5; frame++) {
            row.render(draw, 5000, 5000, 0f);
        }
        assertEquals(1, draw.accents);
        assertEquals(5, draw.thumbs().size());
        verify(option, times(1)).getId();
        Recording other = new Recording();
        row.render(other, 5000, 5000, 0f);
        assertEquals(1, other.accents);
    }

    // A private field of a row, read by reflection
    private static Object field(Object row, String name) throws ReflectiveOperationException {
        Field field = row.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(row);
    }

    @Test
    void aRowKeepsOnlyItsTrackWidthItsDragItsTwoTextCachesAndItsControlWhoseRangeEveryRowShares() throws ReflectiveOperationException {
        SliderControl control = bound(new SliderControl(valued("Slide", 4), 0, 10, 2, ControlValueFormatter.number()));
        ControlElement<Integer> first = control.createElement(DIM);
        ControlElement<Integer> second = control.createElement(new Dim2i(0, 30, 120, 18));
        Set<String> fields = Arrays.stream(first.getClass().getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.of("control", "sliderWidth", "sliderHeld", "idleText", "shownText"), fields);
        assertSame(control, field(first, "control"));
        assertSame(control, field(second, "control"));
        assertEquals(90, field(first, "sliderWidth"));
        assertEquals(90, field(second, "sliderWidth"));
        // Worked out once, by the control
        assertEquals(10L, field(control, "range"));
        assertEquals(5L, field(control, "lastStop"));
        assertEquals(4294967295L, field(new SliderControl(valued("Wide", 0), Integer.MIN_VALUE, Integer.MAX_VALUE, 0x55555555, ControlValueFormatter.number()), "range"));
        // A row the slider does not fit holds no width, rather than a flag beside it
        assertEquals(0, field(control.createElement(new Dim2i(0, 0, 200, 9)), "sliderWidth"));
        assertEquals(0, field(control.createElement(new Dim2i(0, 0, (1 << 30) + 1, 18)), "sliderWidth"));
        assertEquals(1, field(control.createElement(new Dim2i(0, 0, 13, 10)), "sliderWidth"));
    }

    @Test
    void aRowMakesEachTextCacheOnlyOnceItFirstShowsTextThere() throws ReflectiveOperationException {
        Option<Integer> option = valued("Slide", 4);
        ControlElement<Integer> row = row(option, 0, 10, 2, DIM);
        assertNull(field(row, "idleText"));
        assertNull(field(row, "shownText"));
        render(row, 5000, 5000);
        Object idle = field(row, "idleText");
        assertNotNull(idle);
        assertNull(field(row, "shownText"));
        row.setFocused(true);
        render(row, 5000, 5000);
        Object shown = field(row, "shownText");
        assertNotNull(shown);
        // Each made once and kept
        row.setFocused(false);
        render(row, 5000, 5000);
        row.setFocused(true);
        render(row, 5000, 5000);
        assertSame(idle, field(row, "idleText"));
        assertSame(shown, field(row, "shownText"));
        // A row with no line for text makes neither, hovered, focused or held
        ControlElement<Integer> low = row(valued("Slide", 4), 0, 10, 2, new Dim2i(0, 0, 200, 8));
        ControlElement<Integer> tall = row(valued("Slide", 4), 0, 10, 2, DIM);
        Recording lines = new Recording();
        lines.lineHeight = 20;
        for (ControlElement<Integer> blank : List.of(low, tall)) {
            blank.render(lines, 5000, 5000, 0f);
            blank.render(lines, 1, 1, 0f);
            assertTrue(click(blank, 150, blank.getDimensions().getCenterY()));
            blank.setFocused(true);
            blank.render(lines, 5000, 5000, 0f);
            assertNull(field(blank, "idleText"));
            assertNull(field(blank, "shownText"));
        }
    }
}
