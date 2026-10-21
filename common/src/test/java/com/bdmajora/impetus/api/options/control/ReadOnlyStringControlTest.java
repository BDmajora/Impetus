package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadOnlyStringControlTest {
    // Name "Text" at x 6 ending at 30, the value's room from 36 to the inset at 194, text on line 5 (centre 9 less half the glyph height)
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 18);
    private static final int WHITE = 0xFFFFFFFF;

    // A piece of text drawn with the component it came from, where and in what colour
    private record Drawn(TextComponent component, String text, int x, int y, int color) { }

    // Fake fonts keeping every piece of text drawn and every string measured or cut outside drawing, with a settable text version
    private static class Recording extends FakeDrawContext {
        final List<Drawn> drawn = new ArrayList<>();
        final List<String> measured = new ArrayList<>();
        long version;
        private boolean drawing;

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
                this.measured.add(extractString(component));
            }
            return super.getStringWidth(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.measured.add(str);
            return super.substrByWidth(str, maxWidth);
        }

        @Override
        public long textVersion() {
            return this.version;
        }

        // Times the text was measured or cut outside drawing
        long work(String text) {
            return this.measured.stream().filter(text::equals).count();
        }

        // The last piece of text drawn, which is the value when one is drawn
        Drawn last() {
            return this.drawn.getLast();
        }
    }

    // A mock option named and holding a value that writes go to, available and unchanged
    private static <T> Option<T> valued(String name, T initial) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        Mockito.doAnswer(invocation -> holder[0] = invocation.getArgument(0)).when(option).setValue(any());
        when(option.getName()).thenReturn(name != null ? TextComponent.literal(name) : null);
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // Binds the control to its option as the pages do and returns it
    private static <T, C extends Control<T>> C bound(C control) {
        when(control.getOption().getControl()).thenReturn(control);
        return control;
    }

    // A bound read-only row over the option at the bounds
    private static ControlElement<String> row(Option<String> option, Dim2i dim) {
        return bound(new ReadOnlyStringControl(option)).createElement(dim);
    }

    // The text calls one idle frame of the row makes
    private static List<String> texts(ControlElement<?> row) {
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        return draw.calls.stream().filter(call -> call.startsWith("text(")).toList();
    }

    // The text call for a white piece of text at the given place
    private static String text(String text, int x, int y) {
        return "text(" + text + "," + x + "," + y + "," + Integer.toHexString(WHITE) + ",false)";
    }

    // The name and the value one idle frame draws over a fresh row holding the value
    private static List<String> shown(String value) {
        return texts(row(valued("Text", value), DIM));
    }

    @Test
    void theControlHoldsItsOptionAndAsksForANinetyPixelColumnWhateverTheFonts() {
        Option<String> option = valued("Text", "VALUE");
        clearInvocations(option);
        ReadOnlyStringControl control = new ReadOnlyStringControl(option);
        assertSame(option, control.getOption());
        assertEquals(90, control.getMaxWidth());
        assertEquals(90, control.getMaxWidth(new FakeDrawContext()));
        assertSame(String.class, control.getValueType());
        // Making and asking the control never touches the option
        verifyNoInteractions(option);
        NullPointerException refused = assertThrows(NullPointerException.class, () -> new ReadOnlyStringControl(null));
        assertEquals("Option must not be null", refused.getMessage());
    }

    @Test
    void eachCallMakesANewRowForTheOptionAtExactlyTheGivenBoundsAndRefusesMissingBounds() {
        Option<String> option = valued("Text", "VALUE");
        Mockito.doReturn(String.class).when(option).getValueType();
        ReadOnlyStringControl control = bound(new ReadOnlyStringControl(option));
        ControlElement<String> first = control.createElement(DIM);
        ControlElement<String> second = control.createElement(DIM);
        assertNotSame(first, second);
        assertSame(DIM, first.getDimensions());
        assertSame(option, first.getOption());
        // The strict factory the page tests use accepts it
        assertSame(DIM, Control.createElementFor(option, DIM).getDimensions());
        NullPointerException refused = assertThrows(NullPointerException.class, () -> control.createElement(null));
        assertEquals("Dimensions must not be null", refused.getMessage());
    }

    @Test
    void theRowNeverHoldsFocusSoItIsNeitherOutlinedNorHighlightedWhenToldTo() {
        Option<String> option = valued("Text", "VALUE");
        ControlElement<String> row = row(option, DIM);
        assertFalse(row.canFocus());
        row.setFocused(true);
        assertFalse(row.isFocused());
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        // Only the idle background, with no accent outline and so no lookup of the option's mod
        assertEquals(List.of("fill(0,0,200,18," + Integer.toHexString(DefaultColors.BACKGROUND_DEFAULT) + ")"),
                draw.calls.stream().filter(call -> call.startsWith("fill(")).toList());
        verify(option, never()).getId();
        // Under the cursor it still highlights, as every row does for its tooltip
        FakeDrawContext hovered = new FakeDrawContext();
        row.render(hovered, 100, 9, 0f);
        assertEquals("fill(0,0,200,18," + Integer.toHexString(DefaultColors.BACKGROUND_HOVERED) + ")", hovered.calls.getFirst());
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.canFocus());
        row.setFocused(true);
        assertFalse(row.isFocused());
        row.setFocused(false);
        assertFalse(row.isFocused());
    }

    @Test
    void clicksKeysDragsReleasesAndScrollsAreNeverHandledAndNeverReadOrWriteTheValue() {
        Option<String> option = valued("Text", "VALUE");
        ControlElement<String> row = row(option, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        for (boolean focused : new boolean[]{false, true}) {
            row.setFocused(focused);
            for (int button = 0; button < 3; button++) {
                assertFalse(row.mouseClicked(context, 100, 9, button));
                assertFalse(row.mouseReleased(context, 100, 9, button));
                assertFalse(row.mouseDragged(context, 120, 9, button, 20, 0));
            }
            assertFalse(row.mouseScrolled(context, 100, 9, 0, 1));
            assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
            assertFalse(row.keyTyped(context, '\r', Keys.RETURN));
            assertFalse(row.keyTyped(context, '\0', Keys.NUMPAD_ENTER));
            assertFalse(row.keyTyped(context, '\0', Keys.LEFT));
            assertFalse(row.keyTyped(context, '\0', Keys.RIGHT));
            assertFalse(row.keyTyped(context, '\t', Keys.TAB));
            assertFalse(row.keyTyped(context, 'a', 30));
        }
        assertEquals(0, context.clicks);
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
    }

    @Test
    void theValueIsDrawnInWhiteEndingAtTheRightInsetOnTheNamesLine() {
        assertEquals(List.of(text("Text", 6, 5), text("VALUE", 164, 5)), shown("VALUE"));
        // Offset rows keep the same layout relative to their own edges
        assertEquals(List.of(text("Text", 16, 25), text("VALUE", 174, 25)), texts(row(valued("Text", "VALUE"), new Dim2i(10, 20, 200, 18))));
        // An odd height centres on the lower middle line as the name does
        assertEquals(List.of(text("Text", 6, 6), text("VALUE", 164, 6)), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, 200, 21))));
    }

    @Test
    void anUnavailableValueIsGreyedStruckThroughAndDrawnInTheDisabledColourUntilItIsAvailableAgain() {
        Option<String> option = valued("Text", "VALUE");
        ControlElement<String> row = row(option, DIM);
        when(option.isAvailable()).thenReturn(false);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        Drawn value = draw.last();
        assertEquals(new TextComponent.Styled(TextComponent.literal("VALUE"), EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH)), value.component());
        assertEquals(DefaultColors.TEXT_DISABLED, value.color());
        assertEquals(164, value.x());
        when(option.isAvailable()).thenReturn(true);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(new Drawn(TextComponent.literal("VALUE"), "VALUE", 164, 5, WHITE), draw.last());
    }

    @Test
    void aValueTooWideForTheRoomBesideTheNameIsCutWithAnEllipsisAndStillEndsAtTheInset() {
        // Thirty letters are 180 wide against 158 of room, so 23 letters and the ellipsis make 156
        String cut = "X".repeat(23) + "...";
        assertEquals(List.of(text("Text", 6, 5), text(cut, 38, 5)), shown("X".repeat(30)));
        // A greyed cut keeps both its styles
        Option<String> option = valued("Text", "X".repeat(30));
        when(option.isAvailable()).thenReturn(false);
        Recording draw = new Recording();
        row(option, DIM).render(draw, 5000, 5000, 0f);
        assertEquals(new Drawn(new TextComponent.Styled(TextComponent.literal(cut), EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH)),
                cut, 38, 5, DefaultColors.TEXT_DISABLED), draw.last());
    }

    @Test
    void theValueUsesTheRoomTheDrawnNameLeavesRatherThanTheColumn() {
        // A one-letter name ends at 12, leaving 176 for the value, so 29 letters fit whole
        assertEquals(List.of(text("T", 6, 5), text("X".repeat(29), 20, 5)), texts(row(valued("T", "X".repeat(29)), DIM)));
        // With no name the value may start at the row's left edge, so 32 letters fit whole
        assertEquals(List.of(text("X".repeat(32), 2, 5)), texts(row(valued(null, "X".repeat(32)), DIM)));
        assertEquals(List.of(text("X".repeat(29) + "...", 2, 5)), texts(row(valued(null, "X".repeat(33)), DIM)));
    }

    @Test
    void narrowRowsShowAsMuchOfTheValueAsTheirRoomAllowsAndNothingWhenTheInsetLeavesNone() {
        // Rows at most 102 wide leave the name no room beside the 90 column, so the value's room is the width less the inset
        for (int width : new int[]{1, 5, 6, 7, 23}) {
            assertEquals(List.of(), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, width, 18))), "width " + width);
        }
        assertEquals(List.of(text("...", 0, 5)), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, 24, 18))));
        assertEquals(List.of(text("V...", 0, 5)), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, 30, 18))));
        assertEquals(List.of(text("VALUE", 0, 5)), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, 36, 18))));
    }

    @Test
    void aMissingValueDrawsOnlyTheNameWhetherOrNotTheOptionIsAvailable() {
        Option<String> option = valued("Text", null);
        ControlElement<String> row = row(option, DIM);
        assertEquals(List.of(text("Text", 6, 5)), texts(row));
        when(option.isAvailable()).thenReturn(false);
        assertEquals(1, texts(row).size());
    }

    @Test
    void anEmptyValueDrawsOnlyTheName() {
        assertEquals(List.of(text("Text", 6, 5)), shown(""));
        Option<String> option = valued("Text", "");
        when(option.isAvailable()).thenReturn(false);
        assertEquals(1, texts(row(option, DIM)).size());
    }

    @Test
    void blanksAndControlCharactersAtEitherEndAreDroppedSoTheValueStaysFlushWithTheInset() {
        for (String value : new String[]{"  VALUE", "VALUE  ", " VALUE ", "\tVALUE\r\n", "\u0000VALUE\u007f", " VALUE ", "\u0085VALUE\u001b", "\n \t VALUE \t \n"}) {
            assertEquals(List.of(text("Text", 6, 5), text("VALUE", 164, 5)), shown(value), value);
        }
    }

    @Test
    void aValueOfNothingButBlanksAndControlCharactersDrawsOnlyTheName() {
        for (String value : new String[]{" ", "   ", "\n", "\r\n", "\t", "\u0000", " \t\r\n\u0000  "}) {
            assertEquals(List.of(text("Text", 6, 5)), shown(value), value);
        }
    }

    @Test
    void lineBreaksAndOtherControlCharactersInsideTheValueShowAsOneSpacePerRun() {
        assertEquals(text("A B", 176, 5), shown("A\nB").getLast());
        assertEquals(text("A B", 176, 5), shown("A\r\nB").getLast());
        assertEquals(text("A B", 176, 5), shown("A\r\n\r\n\n\rB").getLast());
        assertEquals(text("A B C D E F", 128, 5), shown("A\tB\u0000C D\u001bE F").getLast());
        // Ordinary spaces inside are the value's own and are kept beside the run's space
        assertEquals(text("A   B", 164, 5), shown("A \n B").getLast());
        assertEquals(text("A  B", 170, 5), shown("A  B").getLast());
        assertEquals(text("A B", 176, 5), shown("A B").getLast());
        // Cleaned first, then cut to the room
        assertEquals(text("X".repeat(20) + " YY...", 38, 5), shown("X".repeat(20) + "\r\n" + "Y".repeat(20)).getLast());
        assertEquals(text("A B C", 164, 5), shown(" \nA\nB\u0000\u0001\u0002C\n ").getLast());
    }

    @Test
    void aValueThatNeedsNoCleaningIsDrawnFromTheSameStringWithoutACopy() {
        String value = new String("A VALUE");
        Recording draw = new Recording();
        row(valued("Text", value), DIM).render(draw, 5000, 5000, 0f);
        assertSame(value, ((TextComponent.Literal) draw.last().component()).text());
    }

    @Test
    void theValueIsFittedAgainOnlyWhenItsTextItsStateItsRoomOrItsFontsChange() {
        Option<String> option = valued("Text", "VALUE");
        ControlElement<String> row = row(option, DIM);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(1, draw.work("VALUE"));
        TextComponent fitted = draw.last().component();
        for (int frame = 0; frame < 4; frame++) {
            row.render(draw, 5000, 5000, 0f);
            assertSame(fitted, draw.last().component());
        }
        // A binding that builds a fresh but equal string every frame is not fitted again
        when(option.getValue()).thenAnswer(invocation -> new String("VALUE"));
        for (int frame = 0; frame < 3; frame++) {
            row.render(draw, 5000, 5000, 0f);
            assertSame(fitted, draw.last().component());
        }
        assertEquals(1, draw.work("VALUE"));

        // A new value is fitted once
        when(option.getValue()).thenReturn("OTHER");
        for (int frame = 0; frame < 3; frame++) {
            row.render(draw, 5000, 5000, 0f);
        }
        assertEquals(1, draw.work("OTHER"));
        // Greyed out and back are each fitted once
        when(option.isAvailable()).thenReturn(false);
        row.render(draw, 5000, 5000, 0f);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(2, draw.work("OTHER"));
        when(option.isAvailable()).thenReturn(true);
        row.render(draw, 5000, 5000, 0f);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(3, draw.work("OTHER"));
        // A new name of the same width leaves the room as it was, a wider one moves it
        when(option.getName()).thenReturn(TextComponent.literal("Tex2"));
        row.render(draw, 5000, 5000, 0f);
        assertEquals(3, draw.work("OTHER"));
        when(option.getName()).thenReturn(TextComponent.literal("Texts"));
        row.render(draw, 5000, 5000, 0f);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(4, draw.work("OTHER"));
        // A new text version of the same fonts, then new fonts, each fit it once more
        draw.version++;
        row.render(draw, 5000, 5000, 0f);
        row.render(draw, 5000, 5000, 0f);
        assertEquals(5, draw.work("OTHER"));
        Recording other = new Recording();
        row.render(other, 5000, 5000, 0f);
        row.render(other, 5000, 5000, 0f);
        assertEquals(1, other.work("OTHER"));
        assertEquals(new Drawn(TextComponent.literal("OTHER"), "OTHER", 164, 5, WHITE), other.last());
    }

    @Test
    void aValueThatNeedsCleaningIsCleanedAndFittedOnlyWhenItChanges() {
        Option<String> option = valued("Text", " A\nB ");
        ControlElement<String> row = row(option, DIM);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        TextComponent fitted = draw.last().component();
        assertEquals(TextComponent.literal("A B"), fitted);
        for (int frame = 0; frame < 3; frame++) {
            row.render(draw, 5000, 5000, 0f);
            assertSame(fitted, draw.last().component());
        }
        assertEquals(1, draw.work("A B"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aMistypedValueStillFailsItsCastSoTheFrameCanStandAPlaceholderInTheRow() {
        Option<String> option = (Option) valued("Text", 5);
        ControlElement<String> row = row(option, DIM);
        assertThrows(ClassCastException.class, () -> row.render(new FakeDrawContext(), 5000, 5000, 0f));
    }

    @Test
    void aValueThatCannotBeReadIsLeftForTheFrameToRecover() {
        Option<String> option = valued("Text", "VALUE");
        IllegalStateException failure = new IllegalStateException("gone");
        when(option.getValue()).thenThrow(failure);
        ControlElement<String> row = row(option, DIM);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> row.render(new FakeDrawContext(), 5000, 5000, 0f)));
    }

    @Test
    void theValueIsNeverReadForARowThatCannotShowText() {
        Dim2i[] undrawable = {new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, -200, 18), new Dim2i(0, 0, 200, -18),
                new Dim2i(Integer.MAX_VALUE - 10, 0, 200, 18), new Dim2i(0, Integer.MIN_VALUE, 200, 18)};
        for (Dim2i dim : undrawable) {
            Option<String> option = valued("Text", "VALUE");
            FakeDrawContext draw = new FakeDrawContext();
            row(option, dim).render(draw, 5000, 5000, 0f);
            assertEquals(List.of(), draw.calls, dim::toString);
            verify(option, never()).getValue();
        }
        // Rows shorter than a line of text draw their background only, height 9 is the least that holds one
        for (int height : new int[]{1, 7, 8}) {
            Option<String> option = valued("Text", "VALUE");
            FakeDrawContext draw = new FakeDrawContext();
            row(option, new Dim2i(0, 0, 200, height)).render(draw, 5000, 5000, 0f);
            assertEquals(List.of("fill(0,0,200," + height + "," + Integer.toHexString(DefaultColors.BACKGROUND_DEFAULT) + ")"), draw.calls);
            verify(option, never()).getValue();
        }
        assertEquals(List.of(text("Text", 6, 0), text("VALUE", 164, 0)), texts(row(valued("Text", "VALUE"), new Dim2i(0, 0, 200, 9))));
    }

    @Test
    void aCutNeverEndsOnHalfOfASurrogatePair() {
        // Sixty chars of thirty emoji against 158 of room: 23 chars would end on a high surrogate, so 11 whole emoji and the ellipsis are kept
        String drawn = shown("😀".repeat(30)).getLast();
        assertEquals(text("😀".repeat(11) + "...", 44, 5), drawn);
    }
}
