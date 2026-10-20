package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
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
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlElementTest {
    private static final String LONG = "A very long option name that will not fit in the row";
    private static final String MOD = "control_element_test";
    private static final int ACCENT = 0xFF123456;

    // A mock option with a name, available and unchanged
    private static Option<Boolean> option(String name) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class);
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A row whose control column the test picks, so no control is needed
    private static class Row extends ControlElement<Boolean> {
        int column;
        int measures;

        Row(Option<Boolean> option, Dim2i dim, int column) {
            super(option, dim);
            this.column = column;
        }

        @Override
        protected int measureColumnWidth(DrawContext drawContext) {
            this.measures++;
            return this.column;
        }
    }

    // Fake fonts counting the name work the row does itself, apart from what the fake's own drawString measures, and keeping every component drawn
    private static class Counting extends FakeDrawContext {
        final List<TextComponent> drawn = new ArrayList<>();
        int extracts;
        int measures;
        int cuts;
        // Makes the next cut throw, as a font source failing partway through a fit would
        boolean failCut;
        private boolean drawing;

        @Override
        public String extractString(TextComponent component) {
            if (!this.drawing) {
                this.extracts++;
            }
            return super.extractString(component);
        }

        @Override
        public int getStringWidth(TextComponent component) {
            if (!this.drawing) {
                this.measures++;
            }
            return super.getStringWidth(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.cuts++;
            if (this.failCut) {
                throw new IllegalStateException("cut");
            }
            return super.substrByWidth(str, maxWidth);
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

        int work() {
            return this.extracts + this.measures + this.cuts;
        }

        void reset() {
            this.drawn.clear();
            this.strings.clear();
            this.calls.clear();
            this.extracts = this.measures = this.cuts = 0;
        }
    }

    // Fake fonts answering every accent with one colour and keeping the mod ids asked for
    private static class Accented extends FakeDrawContext {
        final List<String> mods = new ArrayList<>();

        @Override
        public int getModAccentColor(String modId) {
            this.mods.add(modId);
            return ACCENT;
        }
    }

    // Styles of a drawn label
    private static EnumSet<TextFormattingStyle> styles(TextComponent label) {
        return label instanceof TextComponent.Styled styled ? styled.styles() : EnumSet.noneOf(TextFormattingStyle.class);
    }

    // The strings one frame of the row draws
    private static List<String> drawn(ControlElement<?> row) {
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 0, 0, 0f);
        return draw.strings;
    }

    @Test
    void aColumnWiderThanANarrowRowLeavesNoRoomRatherThanWrappingTheSubtraction() {
        for (int column : new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE - 5, 1 << 30, 500}) {
            for (int width = 0; width <= 20; width++) {
                int w = width;
                assertEquals(List.of(), drawn(new Row(option(LONG), new Dim2i(0, 0, width, 18), column)), () -> "width " + w + " column " + column);
            }
        }
    }

    @Test
    void aMissingOptionOrMissingBoundsAreRefusedWhenTheRowIsMadeRatherThanOnItsFirstFrame() {
        Dim2i dim = new Dim2i(0, 0, 200, 18);
        assertEquals("Option must not be null", assertThrows(NullPointerException.class, () -> new ControlElement<Boolean>(null, dim)).getMessage());
        assertEquals("Dimensions must not be null", assertThrows(NullPointerException.class, () -> new ControlElement<>(option("Name"), null)).getMessage());
        // The option is checked first, and subclasses get the same checks
        assertEquals("Option must not be null", assertThrows(NullPointerException.class, () -> new Row(null, null, 30)).getMessage());
    }

    @Test
    void anOptionWithNoNameDrawsNoNameButStillShowsItsPendingChange() {
        Option<Boolean> option = option("Unused");
        when(option.getName()).thenReturn(null);
        assertEquals(List.of(), drawn(new Row(option, new Dim2i(0, 0, 200, 18), 30)));
        when(option.hasChanged()).thenReturn(true);
        assertEquals(List.of(" *"), drawn(new Row(option, new Dim2i(0, 0, 200, 18), 30)));
        // The background is still drawn
        FakeDrawContext draw = new FakeDrawContext();
        new Row(option, new Dim2i(0, 0, 200, 18), 30).render(draw, 500, 500, 0f);
        assertTrue(draw.drew("fill(0,0,200,18,"));
    }

    @Test
    void aFontSourceThatGivesNoTextForTheNameDrawsNoNameRatherThanTheWordNull() {
        FakeDrawContext blank = new FakeDrawContext() {
            @Override
            public String extractString(TextComponent component) {
                return component instanceof TextComponent.Literal literal && literal.text().equals("Name") ? null : super.extractString(component);
            }
        };
        new Row(option("Name"), new Dim2i(0, 0, 200, 18), 30).render(blank, 0, 0, 0f);
        assertEquals(List.of(), blank.strings);
    }

    @Test
    void aCutNeverSplitsACharacterOutsideTheBasicPlaneInTwo() {
        // A 78 row less the 30 column and 12 of gaps leaves 36, 18 after the ellipsis, three chars: "Ab" and half the emoji
        assertEquals(List.of("Ab..."), drawn(new Row(option("Ab\uD83D\uDE00cdefgh"), new Dim2i(0, 0, 78, 18), 30)));
        // One more char of room keeps the whole emoji
        assertEquals(List.of("Ab\uD83D\uDE00..."), drawn(new Row(option("Ab\uD83D\uDE00cdefgh"), new Dim2i(0, 0, 84, 18), 30)));
        // A space before the split half is dropped too
        assertEquals(List.of("A..."), drawn(new Row(option("A \uD83D\uDE00cdefgh"), new Dim2i(0, 0, 78, 18), 30)));
        // A name of nothing but split characters keeps only the ellipsis
        assertEquals(List.of("..."), drawn(new Row(option("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"), new Dim2i(0, 0, 66, 18), 30)));
    }

    @Test
    void aFontSourceThatCutsToNullShowsTheEllipsisAlone() {
        FakeDrawContext draw = new FakeDrawContext() {
            @Override
            public String substrByWidth(String str, int maxWidth) {
                return null;
            }
        };
        new Row(option(LONG), new Dim2i(0, 0, 100, 18), 30).render(draw, 0, 0, 0f);
        assertEquals(List.of("..."), draw.strings);
    }

    @Test
    void aFontSourceThatCutsTooLittleShowsTheEllipsisAloneRatherThanRunningIntoTheColumn() {
        FakeDrawContext draw = new FakeDrawContext() {
            @Override
            public String substrByWidth(String str, int maxWidth) {
                return str;
            }
        };
        Option<Boolean> option = option(LONG);
        new Row(option, new Dim2i(0, 0, 100, 18), 30).render(draw, 0, 0, 0f);
        assertEquals(List.of("..."), draw.strings);
        when(option.hasChanged()).thenReturn(true);
        draw.strings.clear();
        new Row(option, new Dim2i(0, 0, 100, 18), 30).render(draw, 0, 0, 0f);
        assertEquals(List.of("... *"), draw.strings);
    }

    @Test
    void aRowThatHasDrawnReusesItsLabelWithoutExtractingMeasuringOrCuttingTheNameAgain() {
        for (String name : new String[]{"Name", LONG}) {
            Option<Boolean> option = option(name);
            Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
            Counting draw = new Counting();
            row.render(draw, 0, 0, 0f);
            assertTrue(draw.work() > 0);
            TextComponent first = draw.drawn.getFirst();
            for (int frame = 0; frame < 10; frame++) {
                draw.reset();
                row.render(draw, 0, 0, 0f);
                assertEquals(0, draw.work(), name);
                assertSame(first, draw.drawn.getFirst());
                // The background is still drawn every frame
                assertTrue(draw.drew("fill(0,0,200,18,"));
            }
            // The option is still asked every frame for what can change
            verify(option, times(11)).getName();
            verify(option, times(11)).isAvailable();
            verify(option, times(11)).hasChanged();
        }
    }

    @Test
    void aChangeOfStateIsShownTheFrameItHappensAndFittedOnce() {
        Option<Boolean> option = option("Render distance");
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicBoolean changed = new AtomicBoolean(false);
        when(option.isAvailable()).thenAnswer(invocation -> available.get());
        when(option.hasChanged()).thenAnswer(invocation -> changed.get());
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Render distance"), draw.strings);
        assertEquals(EnumSet.of(TextFormattingStyle.WHITE), styles(draw.drawn.getFirst()));
        changed.set(true);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Render distance *"), draw.strings);
        assertEquals(EnumSet.of(TextFormattingStyle.ITALIC), styles(draw.drawn.getFirst()));
        assertTrue(draw.work() > 0);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.work());
        // Unavailable hides the change, whatever the option says
        available.set(false);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Render distance"), draw.strings);
        assertEquals(EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(draw.drawn.getFirst()));
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.work());
        // And back to plain
        available.set(true);
        changed.set(false);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Render distance"), draw.strings);
        assertEquals(EnumSet.of(TextFormattingStyle.WHITE), styles(draw.drawn.getFirst()));
    }

    @Test
    void aRenamedOptionIsFittedAgainButAnEqualNameMadeAfreshIsNot() {
        Option<Boolean> option = option("Name");
        AtomicReference<String> name = new AtomicReference<>("Name");
        // A fresh but equal component every frame, as a getter that builds its name would give
        when(option.getName()).thenAnswer(invocation -> TextComponent.literal(name.get()));
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.work());
        assertEquals(List.of("Name"), draw.strings);
        name.set("Renamed");
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Renamed"), draw.strings);
        assertTrue(draw.extracts > 0);
        // A name that goes away draws nothing, and one that comes back is fitted again
        when(option.getName()).thenReturn(null);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of(), draw.strings);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.work());
        when(option.getName()).thenReturn(TextComponent.literal("Back"));
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Back"), draw.strings);
    }

    @Test
    void anotherFontSourceFitsTheNameAgainInItsOwnMeasure() {
        Row row = new Row(option(LONG), new Dim2i(0, 0, 100, 18), 30);
        Counting narrow = new Counting();
        row.render(narrow, 0, 0, 0f);
        // 58 of room less the 18 pixel ellipsis leaves 40, six characters
        assertEquals(List.of("A very..."), narrow.strings);
        // Characters half as wide fit 16 into the 49 left beside a 9 pixel ellipsis
        Counting wide = new Counting() {
            @Override
            public int getStringWidth(TextComponent component) {
                super.getStringWidth(component);
                return (extractString(component).length() * 3);
            }

            @Override
            public String substrByWidth(String str, int maxWidth) {
                this.cuts++;
                return str.substring(0, Math.min(str.length(), Math.max(0, maxWidth / 3)));
            }
        };
        row.render(wide, 0, 0, 0f);
        assertEquals(List.of("A very long opti..."), wide.strings);
        assertTrue(wide.cuts > 0);
        // Going back measures in the first source again
        narrow.reset();
        row.render(narrow, 0, 0, 0f);
        assertEquals(List.of("A very..."), narrow.strings);
    }

    @Test
    void aFitThatFailsPartwayIsTriedAgainOnTheNextFrameRatherThanLeavingAStaleLabel() {
        Option<Boolean> option = option(LONG);
        AtomicBoolean changed = new AtomicBoolean(false);
        when(option.hasChanged()).thenAnswer(invocation -> changed.get());
        Row row = new Row(option, new Dim2i(0, 0, 100, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("A very..."), draw.strings);
        changed.set(true);
        draw.failCut = true;
        draw.reset();
        assertThrows(IllegalStateException.class, () -> row.render(draw, 0, 0, 0f));
        assertEquals(List.of(), draw.strings);
        draw.failCut = false;
        draw.reset();
        row.render(draw, 0, 0, 0f);
        // 58 less the 30 pixel "... *" leaves 28, four characters
        assertEquals(List.of("A ve... *"), draw.strings);
        // The state that drew before is fitted again rather than mixed up with the failed one
        changed.set(false);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("A very..."), draw.strings);
    }

    @Test
    void aRowWithNoAreaDrawsNothingEvenFocusedRatherThanAMirroredBackgroundOutsideItself() {
        Dim2i[] empty = {new Dim2i(0, 0, 0, 18), new Dim2i(10, 10, -10, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, 200, -18),
                new Dim2i(-5, -5, -1, -1), new Dim2i(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE)};
        for (Dim2i dim : empty) {
            Option<Boolean> option = option("N");
            when(option.hasChanged()).thenReturn(true);
            Row row = new Row(option, dim, 0);
            row.setFocused(true);
            for (int frame = 0; frame < 3; frame++) {
                FakeDrawContext draw = new FakeDrawContext();
                row.render(draw, dim.x(), dim.y(), 0f);
                assertEquals(List.of(), draw.calls, dim::toString);
            }
            // Nor is anything read or measured for it
            assertEquals(0, row.measures, dim::toString);
            verify(option, never()).isAvailable();
            verify(option, never()).getName();
        }
        // One pixel each way is enough to draw
        FakeDrawContext draw = new FakeDrawContext();
        new Row(option("N"), new Dim2i(0, 0, 1, 1), 0).render(draw, 0, 0, 0f);
        assertTrue(draw.drew("fill(0,0,1,1,"));
    }

    @Test
    void aVisibleRowFillsItsBoundsThenDrawsItsNameSixInAndCentredInWhite() {
        Row row = new Row(option("Name"), new Dim2i(-30, 7, 200, 19), 30);
        FakeDrawContext idle = new FakeDrawContext();
        row.render(idle, 500, 500, 0f);
        // A 19 high row at 7 centres on 16, so text 8 high starts at 12
        assertEquals(List.of("fill(-30,7,170,26,90000000)", "text(Name,-24,12,ffffffff,false)"), idle.calls);
        // The top left corner is inside and turns the background to the hover colour
        FakeDrawContext hovered = new FakeDrawContext();
        row.render(hovered, -30, 7, 0f);
        assertEquals(List.of("fill(-30,7,170,26,e0202020)", "text(Name,-24,12,ffffffff,false)"), hovered.calls);
        // The right and bottom edges are outside
        for (int[] mouse : new int[][]{{170, 7}, {-30, 26}, {-31, 7}, {-30, 6}}) {
            FakeDrawContext outside = new FakeDrawContext();
            row.render(outside, mouse[0], mouse[1], 0f);
            assertEquals("fill(-30,7,170,26,90000000)", outside.calls.getFirst());
        }
    }

    @Test
    void aFocusedRowLooksHoveredAndIsOutlinedInItsModsAccentReadOnlyWhileFocused() {
        Option<Boolean> option = option("Name");
        doReturn(OptionIdentifier.create(MOD, "row", Boolean.class)).when(option).getId();
        Row row = new Row(option, new Dim2i(-30, 7, 200, 19), 30);
        Accented unfocused = new Accented();
        row.render(unfocused, 500, 500, 0f);
        assertEquals(List.of(), unfocused.mods);
        verify(option, never()).getId();
        row.setFocused(true);
        assertTrue(row.isFocused());
        for (int frame = 0; frame < 3; frame++) {
            Accented focused = new Accented();
            row.render(focused, 500, 500, 0f);
            assertEquals(List.of("fill(-30,7,170,26,e0202020)", "fill(-30,7,170,8,ff123456)", "fill(-30,25,170,26,ff123456)", "fill(-30,7,-29,26,ff123456)",
                    "fill(169,7,170,26,ff123456)", "text(Name,-24,12,ffffffff,false)"), focused.calls);
            assertEquals(List.of(MOD), focused.mods);
        }
        // The accent is read every focused frame, so a theme registered while the screen is open shows at once
        verify(option, times(3)).getId();
        row.setFocused(false);
        assertFalse(row.isFocused());
        Accented blurred = new Accented();
        row.render(blurred, 500, 500, 0f);
        assertEquals(List.of("fill(-30,7,170,26,90000000)", "text(Name,-24,12,ffffffff,false)"), blurred.calls);
    }

    @Test
    void anOptionWithNoIdAsksForTheDefaultAccent() {
        Option<Boolean> option = option("Name");
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        row.setFocused(true);
        Accented draw = new Accented();
        row.render(draw, 500, 500, 0f);
        assertEquals(java.util.Collections.singletonList(null), draw.mods);
        assertTrue(draw.drew("fill(0,0,200,1,ff123456)"));
        // The default context asks the theme, which answers an unowned row with the standard accent
        FakeDrawContext themed = new FakeDrawContext();
        row.render(themed, 500, 500, 0f);
        assertTrue(themed.drew("fill(0,0,200,1," + Integer.toHexString(DefaultColors.getModAccentColor(null)) + ")"));
    }

    @Test
    void aFocusedRowWhoseOptionGoesUnavailableKeepsItsOutlineOverTheGreyedName() {
        Option<Boolean> option = option("Name");
        AtomicBoolean available = new AtomicBoolean(true);
        when(option.isAvailable()).thenAnswer(invocation -> available.get());
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        assertTrue(row.canFocus());
        row.setFocused(true);
        available.set(false);
        // Tab passes over it from now on, but the outline still shows where the keys go until focus moves
        assertFalse(row.canFocus());
        Counting draw = new Counting();
        row.render(draw, 500, 500, 0f);
        assertTrue(draw.drew("fill(0,0,200,1,"));
        assertEquals(EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(draw.drawn.getFirst()));
        verify(option, never()).hasChanged();
    }

    @Test
    void aFailureWhileWorkingOutTheNameLeavesNothingHalfDrawnAndTheNextFrameStartsAfresh() {
        // The column measure
        Row measured = new Row(option("Name"), new Dim2i(0, 0, 200, 18), 30) {
            @Override
            protected int measureColumnWidth(DrawContext drawContext) {
                if (this.measures++ == 0) {
                    throw new IllegalStateException("measure");
                }
                return this.column;
            }
        };
        FakeDrawContext failed = new FakeDrawContext();
        assertThrows(IllegalStateException.class, () -> measured.render(failed, 0, 0, 0f));
        assertEquals(List.of(), failed.calls);
        FakeDrawContext next = new FakeDrawContext();
        measured.render(next, 0, 0, 0f);
        assertEquals(List.of("Name"), next.strings);
        assertEquals(2, measured.measures);
        // The name
        AtomicBoolean nameFails = new AtomicBoolean(true);
        Row named = new Row(option("Name"), new Dim2i(0, 0, 200, 18), 30) {
            @Override
            protected TextComponent getDisplayName() {
                if (nameFails.get()) {
                    throw new IllegalStateException("name");
                }
                return super.getDisplayName();
            }
        };
        FakeDrawContext noName = new FakeDrawContext();
        assertThrows(IllegalStateException.class, () -> named.render(noName, 0, 0, 0f));
        assertEquals(List.of(), noName.calls);
        nameFails.set(false);
        FakeDrawContext named2 = new FakeDrawContext();
        named.render(named2, 0, 0, 0f);
        assertEquals(List.of("Name"), named2.strings);
        // The change state
        Option<Boolean> option = option("Name");
        when(option.hasChanged()).thenThrow(new IllegalStateException("changed")).thenReturn(true);
        Row changed = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        FakeDrawContext noState = new FakeDrawContext();
        assertThrows(IllegalStateException.class, () -> changed.render(noState, 0, 0, 0f));
        assertEquals(List.of(), noState.calls);
        FakeDrawContext stated = new FakeDrawContext();
        changed.render(stated, 0, 0, 0f);
        assertEquals(List.of("Name *"), stated.strings);
    }

    @Test
    void theDisabledStyleHookIsAskedOncePerFitAndAMissingResultDrawsNoName() {
        Option<Boolean> option = option("Name");
        when(option.isAvailable()).thenReturn(false);
        AtomicReference<TextComponent> styled = new AtomicReference<>(TextComponent.literal("Styled"));
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30) {
            @Override
            protected TextComponent formatDisabledControlValue(TextComponent value) {
                asked.incrementAndGet();
                assertEquals(TextComponent.literal("Name"), value);
                return styled.get();
            }
        };
        Counting draw = new Counting();
        for (int frame = 0; frame < 5; frame++) {
            row.render(draw, 0, 0, 0f);
        }
        assertEquals(1, asked.get());
        assertEquals(5, draw.drawn.size());
        draw.drawn.forEach(drawn -> assertSame(styled.get(), drawn));
        // A hook that gives nothing back leaves the name off without failing the row
        styled.set(null);
        FakeDrawContext other = new FakeDrawContext();
        row.render(other, 0, 0, 0f);
        assertEquals(List.of(), other.strings);
        assertEquals(1, other.calls.size());
        assertEquals(2, asked.get());
    }

    @Test
    void theBuiltInDisabledStyleGreysAndStrikesThroughWithoutChangingTheValueGiven() {
        ControlElement<Boolean> row = new ControlElement<>(option("Name"), new Dim2i(0, 0, 200, 18));
        TextComponent value = TextComponent.literal("Value");
        TextComponent disabled = row.formatDisabledControlValue(value);
        assertEquals(new TextComponent.Styled(value, EnumSet.of(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH)), disabled);
        assertEquals(TextComponent.literal("Value"), value);
        assertEquals(DefaultColors.TEXT_DISABLED, row.getDisabledControlColor());
    }

    @Test
    void aNameCarryingItsOwnStylesIsDrawnInTheRowsStyleAlone() {
        Option<Boolean> option = option("Unused");
        when(option.getName()).thenReturn(TextComponent.literal("Gold").withStyle(TextFormattingStyle.GOLD, TextFormattingStyle.UNDERLINE));
        Counting draw = new Counting();
        new Row(option, new Dim2i(0, 0, 200, 18), 30).render(draw, 0, 0, 0f);
        assertEquals(new TextComponent.Styled(TextComponent.literal("Gold"), EnumSet.of(TextFormattingStyle.WHITE)), draw.drawn.getFirst());
    }

    @Test
    void theHoverTestTakesTheTopLeftEdgesAndLeavesTheBottomRightOnesAndAnythingNotANumber() {
        ControlElement<Boolean> row = new ControlElement<>(option("Name"), new Dim2i(10, 20, 100, 18));
        assertTrue(row.isMouseOver(10, 20));
        assertTrue(row.isMouseOver(109.999, 37.999));
        assertFalse(row.isMouseOver(110, 20));
        assertFalse(row.isMouseOver(10, 38));
        assertFalse(row.isMouseOver(9.999, 20));
        assertFalse(row.isMouseOver(10, 19.999));
        for (double odd : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(row.isMouseOver(odd, 25));
            assertFalse(row.isMouseOver(50, odd));
        }
        // A subclass's own hover test decides the background
        Row always = new Row(option("Name"), new Dim2i(0, 0, 200, 18), 30) {
            @Override
            public boolean isMouseOver(double x, double y) {
                return true;
            }
        };
        FakeDrawContext draw = new FakeDrawContext();
        always.render(draw, 5000, 5000, 0f);
        assertEquals("fill(0,0,200,18,e0202020)", draw.calls.getFirst());
    }

    @Test
    void aPlainRowTakesNoInputAndHandsBackItsOwnOptionAndBounds() {
        Option<Boolean> option = option("Name");
        Dim2i dim = new Dim2i(0, 0, 200, 18);
        ControlElement<Boolean> row = new ControlElement<>(option, dim);
        assertSame(option, row.getOption());
        assertSame(dim, row.getDimensions());
        FakeInteractionContext context = new FakeInteractionContext();
        row.setFocused(true);
        assertFalse(row.mouseClicked(context, 5, 5, 0));
        assertFalse(row.mouseReleased(context, 5, 5, 0));
        assertFalse(row.mouseDragged(context, 5, 5, 0, 1, 1));
        assertFalse(row.mouseScrolled(context, 5, 5, 0, 1));
        assertFalse(row.keyTyped(context, ' ', 57));
        assertEquals(0, context.clicks);
    }

    @Test
    void aRowTooNarrowForAnyOfItsNameDoesNoTextWorkAfterItsFirstFrame() {
        Row row = new Row(option(LONG), new Dim2i(0, 0, 40, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of(), draw.strings);
        for (int frame = 0; frame < 5; frame++) {
            draw.reset();
            row.render(draw, 0, 0, 0f);
            assertEquals(0, draw.work());
            assertEquals(List.of(), draw.strings);
            assertEquals(1, draw.calls.size());
        }
    }

    @Test
    void aHugeNameIsCutOnceAndAHugeRowHoldsItWhole() {
        String huge = "x".repeat(100_000);
        Row row = new Row(option(huge), new Dim2i(0, 0, 200, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        // 158 of room less the ellipsis leaves 140, 23 characters
        assertEquals(List.of("x".repeat(23) + "..."), draw.strings);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.work());
        FakeDrawContext wide = new FakeDrawContext();
        // A billion pixels is the widest row that is still drawn
        new Row(option(huge), new Dim2i(0, 0, 1 << 30, 18), 0).render(wide, 0, 0, 0f);
        assertEquals(huge, wide.strings.getFirst());
    }

    @Test
    void anEqualTranslatedNameMadeAfreshIsNotTranslatedAgain() {
        Option<Boolean> option = option("Unused");
        when(option.getName()).thenAnswer(invocation -> TextComponent.translatable("options.render_distance", 12));
        Row row = new Row(option, new Dim2i(0, 0, 300, 18), 30);
        Counting draw = new Counting();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("options.render_distance[12]"), draw.strings);
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(0, draw.extracts);
        // Another argument is another name
        when(option.getName()).thenReturn(TextComponent.translatable("options.render_distance", 16));
        draw.reset();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("options.render_distance[16]"), draw.strings);
    }

    // Fonts of uneven widths, one to seven pixels a character and four for each half of a pair, cutting as a real font does, by whole characters it can fit
    private static final class Uneven extends FakeDrawContext {
        static int width(char c) {
            return Character.isSurrogate(c) ? 4 : 1 + c % 7;
        }

        @Override
        public int getStringWidth(TextComponent component) {
            int width = 0;
            for (char c : extractString(component).toCharArray()) {
                width += width(c);
            }
            return width;
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            int width = 0;
            for (int i = 0; i < str.length(); i++) {
                width += width(str.charAt(i));
                if (width > maxWidth) {
                    return str.substring(0, i);
                }
            }
            return str;
        }
    }

    @Test
    void namesOfUnevenWidthsAlwaysFitTheirRoomKeepTheirWholeCharactersAndAreWholeWhenTheyFit() {
        Random random = new Random(0x5EED);
        char[] alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789     .-_%".toCharArray();
        // Rounds that drew nothing, the whole name and a cut one, so the run is known to reach every outcome
        int[] outcomes = new int[3];
        for (int round = 0; round < 3000; round++) {
            StringBuilder name = new StringBuilder();
            int length = random.nextInt(40);
            for (int i = 0; i < length; i++) {
                if (random.nextInt(8) == 0) {
                    name.append("\uD83D\uDE00");
                } else {
                    name.append(alphabet[random.nextInt(alphabet.length)]);
                }
            }
            int width = random.nextInt(320) - 20;
            int column = random.nextInt(140);
            boolean changed = random.nextBoolean();
            Option<Boolean> option = option(name.toString());
            when(option.hasChanged()).thenReturn(changed);
            Uneven draw = new Uneven();
            new Row(option, new Dim2i(0, 0, width, 18), column).render(draw, 0, 0, 0f);
            int room = Math.max(0, Math.max(0, width) - Math.min(column, Math.max(0, width)) - 12);
            String whole = changed ? name + " *" : name.toString();
            String context = "name '" + name + "' width " + width + " column " + column + " changed " + changed;
            if (draw.strings.isEmpty()) {
                // Nothing is drawn only when not even the suffix fits, or there is nothing to draw
                assertTrue(whole.isEmpty() || draw.getStringWidth(changed ? "... *" : "...") > room || width <= 0, context);
                outcomes[0]++;
                continue;
            }
            String drawn = draw.strings.getFirst();
            assertTrue(draw.getStringWidth(drawn) <= room, context);
            if (draw.getStringWidth(whole) <= room) {
                assertEquals(whole, drawn, context);
                outcomes[1]++;
                continue;
            }
            String suffix = changed ? "... *" : "...";
            assertTrue(drawn.endsWith(suffix), context);
            String kept = drawn.substring(0, drawn.length() - suffix.length());
            assertTrue(name.toString().startsWith(kept), context);
            assertEquals(kept.stripTrailing(), kept, context);
            assertFalse(!kept.isEmpty() && Character.isHighSurrogate(kept.charAt(kept.length() - 1)), context);
            outcomes[2]++;
        }
        for (int outcome : outcomes) {
            assertTrue(outcome > 300, () -> java.util.Arrays.toString(outcomes));
        }
    }

    // Fake fonts translating one key from text the test changes, with a version the test moves on when it does, as a language reload would
    private static final class Reloading extends FakeDrawContext {
        static final String KEY = "control_element_test.name";
        String translation = "Before";
        long version;
        int extracts;

        @Override
        public String extractString(TextComponent component) {
            if (component instanceof TextComponent.Translatable translatable && translatable.keys().getFirst().equals(KEY)) {
                this.extracts++;
                return this.translation;
            }
            return super.extractString(component);
        }

        // Not marked as an override so the fake also stands for a context written before versions existed
        public long textVersion() {
            return this.version;
        }
    }

    @Test
    void aLanguageReloadInTheSameFontSourceShowsOnTheNextFrameAndMeasuresTheColumnAgain() {
        Option<Boolean> option = option("Unused");
        when(option.getName()).thenReturn(TextComponent.translatable(Reloading.KEY));
        Row row = new Row(option, new Dim2i(0, 0, 200, 18), 30);
        Reloading draw = new Reloading();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("Before"), draw.strings);
        int extracts = draw.extracts;
        draw.strings.clear();
        row.render(draw, 0, 0, 0f);
        assertEquals(extracts, draw.extracts);
        assertEquals(1, row.measures);
        // The reload moves the version on, so the name is translated and the column measured again
        draw.translation = "After the reload";
        draw.version = 7;
        draw.strings.clear();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("After the reload"), draw.strings);
        assertEquals(2, row.measures);
        draw.strings.clear();
        row.render(draw, 0, 0, 0f);
        assertEquals(2, row.measures);
        // The version is what tells the row, so text changed without it is not looked for
        draw.translation = "Unseen";
        draw.strings.clear();
        row.render(draw, 0, 0, 0f);
        assertEquals(List.of("After the reload"), draw.strings);
        // Another font source is fitted in its own right but keeps the column, as before
        Reloading other = new Reloading();
        other.translation = "Other";
        row.render(other, 0, 0, 0f);
        assertEquals(List.of("Other"), other.strings);
        assertEquals(2, row.measures);
    }
}
