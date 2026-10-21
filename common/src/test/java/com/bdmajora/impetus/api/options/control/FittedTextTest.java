package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.testing.FakeDrawContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class FittedTextTest {
    // Ten letters, sixty pixels in the fake fonts
    private static final String TEN = "ABCDEFGHIJ";

    // Fake fonts in which a styled component measures what the operator makes of its plain width and length, recording every width a cut was asked for
    private static class Widening extends FakeDrawContext {
        final List<Integer> asked = new ArrayList<>();
        private final IntBinaryOperator styled;

        Widening(IntBinaryOperator styled) {
            this.styled = styled;
        }

        @Override
        public int getStringWidth(TextComponent component) {
            int plain = super.getStringWidth(component);
            return component instanceof TextComponent.Styled ? this.styled.applyAsInt(plain, extractString(component).length()) : plain;
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.asked.add(maxWidth);
            return super.substrByWidth(str, maxWidth);
        }
    }

    // Fake fonts that fail any text work, for checking none is done
    private static final class Silent extends FakeDrawContext {
        @Override
        public int getStringWidth(TextComponent component) {
            throw new AssertionError("measured " + component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            throw new AssertionError("cut " + str);
        }

        @Override
        public String extractString(TextComponent component) {
            throw new AssertionError("extracted " + component);
        }
    }

    // The given styles as a set
    private static EnumSet<TextFormattingStyle> styles(TextFormattingStyle first, TextFormattingStyle... rest) {
        return EnumSet.of(first, rest);
    }

    // Text wrapped in the given number of one-style layers
    private static TextComponent layered(int layers) {
        TextComponent component = TextComponent.literal("x");
        for (int i = 0; i < layers; i++) {
            component = new TextComponent.Styled(component, styles(TextFormattingStyle.GOLD));
        }
        return component;
    }

    // Ten letters fitted underlined into 48 pixels in the given fonts
    private static FittedText underlinedTen(FakeDrawContext fonts) {
        FittedText text = new FittedText();
        text.fit(fonts, 1, null, 0, false, 48, TextComponent.literal(TEN).withStyle(TextFormattingStyle.UNDERLINE));
        return text;
    }

    @Test
    void aNewTextIsFittedForNoKeyNotEvenTheOneItsFieldsStartAt() {
        FittedText text = new FittedText();
        assertFalse(text.isFittedFor(0, null, 0, false, 0));
        assertFalse(text.isFittedFor(1, null, 0, false, 0));
        assertNull(text.text());
        assertEquals(0, text.width());
    }

    @Test
    void aFittedTextIsKeptForExactlyTheKeyItWasFittedFor() {
        FittedText text = new FittedText();
        String source = "SOURCE";
        text.fit(new FakeDrawContext(), 7, source, 3, true, 60, TextComponent.literal(source));
        assertTrue(text.isFittedFor(7, source, 3, true, 60));
        // An equal source made afresh counts as the same
        assertTrue(text.isFittedFor(7, new String("SOURCE"), 3, true, 60));
        assertFalse(text.isFittedFor(8, source, 3, true, 60));
        assertFalse(text.isFittedFor(6, source, 3, true, 60));
        assertFalse(text.isFittedFor(7, "OTHER", 3, true, 60));
        assertFalse(text.isFittedFor(7, null, 3, true, 60));
        assertFalse(text.isFittedFor(7, source, 4, true, 60));
        assertFalse(text.isFittedFor(7, source, 3, false, 60));
        assertFalse(text.isFittedFor(7, source, 3, true, 59));
        assertFalse(text.isFittedFor(7, source, 3, true, 61));
        assertEquals(TextComponent.literal(source), text.text());
        assertEquals(36, text.width());
    }

    @Test
    void aMissingSourceIsAKeyLikeAnyOtherThatOnlyAnotherMissingOneMatches() {
        FittedText text = new FittedText();
        text.fit(new FakeDrawContext(), 1, null, 0, false, 60, TextComponent.literal("A"));
        assertTrue(text.isFittedFor(1, null, 0, false, 60));
        assertFalse(text.isFittedFor(1, "", 0, false, 60));
        text.fit(new FakeDrawContext(), 1, "", 0, false, 60, TextComponent.literal("A"));
        assertTrue(text.isFittedFor(1, "", 0, false, 60));
        assertFalse(text.isFittedFor(1, null, 0, false, 60));
    }

    @Test
    void extremeGenerationsValuesAndRoomsAreKeysLikeAnyOther() {
        FittedText text = new FittedText();
        for (long generation : new long[]{Long.MIN_VALUE, -1, 0, Long.MAX_VALUE}) {
            for (int value : new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                for (int room : new int[]{Integer.MIN_VALUE, -5, 0, Integer.MAX_VALUE}) {
                    text.fit(new FakeDrawContext(), generation, "S", value, false, room, TextComponent.literal("A"));
                    assertTrue(text.isFittedFor(generation, "S", value, false, room));
                    // Neighbours wrap round at the ends and still differ
                    assertFalse(text.isFittedFor(generation + 1, "S", value, false, room));
                    assertFalse(text.isFittedFor(generation, "S", value + 1, false, room));
                    assertFalse(text.isFittedFor(generation, "S", value, false, room - 1));
                }
            }
        }
        // The largest room keeps the text whole
        assertEquals(TextComponent.literal("A"), text.text());
        assertEquals(6, text.width());
    }

    @Test
    void nothingFitsWithoutRoomOrTextAndTheFontSourceIsNeverAskedForIt() {
        FittedText text = new FittedText();
        for (int room : new int[]{0, -1, Integer.MIN_VALUE}) {
            text.fit(new Silent(), 1, null, 0, false, room, TextComponent.literal(TEN));
            assertNull(text.text());
            assertEquals(0, text.width());
            assertTrue(text.isFittedFor(1, null, 0, false, room));
        }
        // Not even an empty text, which would draw nothing anyway
        text.fit(new Silent(), 1, null, 0, false, 0, TextComponent.literal(""));
        assertNull(text.text());
        text.fit(new Silent(), 2, null, 0, false, 60, null);
        assertNull(text.text());
        assertEquals(0, text.width());
        assertTrue(text.isFittedFor(2, null, 0, false, 60));
    }

    @Test
    void aMissingDrawContextIsRefusedEvenWithNothingToFitAndKeepsThePreviousFit() {
        FittedText text = new FittedText();
        text.fit(new FakeDrawContext(), 1, null, 0, false, 60, TextComponent.literal("A"));
        NullPointerException refused = assertThrows(NullPointerException.class, () -> text.fit(null, 2, null, 0, false, 0, null));
        assertEquals("Draw context must not be null", refused.getMessage());
        assertTrue(text.isFittedFor(1, null, 0, false, 60));
        assertFalse(text.isFittedFor(2, null, 0, false, 0));
        assertEquals(TextComponent.literal("A"), text.text());
    }

    @Test
    void aTextExactlyAsWideAsTheRoomIsKeptWholeAndOnePixelLessCutsIt() {
        FittedText text = new FittedText();
        text.fit(new FakeDrawContext(), 1, null, 0, false, 60, TextComponent.literal(TEN));
        assertEquals(TextComponent.literal(TEN), text.text());
        assertEquals(60, text.width());
        // 59 leaves 41 after the ellipsis, six letters
        text.fit(new FakeDrawContext(), 1, null, 0, false, 59, TextComponent.literal(TEN));
        assertEquals(TextComponent.literal("ABCDEF..."), text.text());
        assertEquals(54, text.width());
    }

    @Test
    void anEmptyTextInRoomIsKeptAsAnEmptyTextOfNoWidth() {
        FittedText text = new FittedText();
        text.fit(new FakeDrawContext(), 1, null, 0, false, 1, TextComponent.literal(""));
        assertEquals(TextComponent.literal(""), text.text());
        assertEquals(0, text.width());
    }

    @Test
    void aWholeTextIsKeptAsACopyItsMakerCanNoLongerChangeWhilePlainTextIsKeptAsItIs() {
        FittedText text = new FittedText();
        TextComponent.Styled styled = new TextComponent.Styled(TextComponent.literal("AB"), styles(TextFormattingStyle.GOLD));
        text.fit(new FakeDrawContext(), 1, null, 0, false, 60, styled);
        styled.styles().add(TextFormattingStyle.UNDERLINE);
        assertEquals(new TextComponent.Styled(TextComponent.literal("AB"), styles(TextFormattingStyle.GOLD)), text.text());
        // A translation's arguments are its own too, though the array they came from is the maker's
        Object[] args = {"x", TextComponent.literal("y").withStyle(TextFormattingStyle.RED)};
        text.fit(new FakeDrawContext(), 2, null, 0, false, 600, TextComponent.translatable("key", args));
        args[0] = "changed";
        ((TextComponent.Styled) args[1]).styles().add(TextFormattingStyle.ITALIC);
        assertEquals(TextComponent.translatable("key", "x", TextComponent.literal("y").withStyle(TextFormattingStyle.RED)), text.text());
        // Text with nothing to change is not copied
        TextComponent literal = TextComponent.literal("AB");
        text.fit(new FakeDrawContext(), 3, null, 0, false, 60, literal);
        assertSame(literal, text.text());
    }

    @Test
    void aCutKeepsEveryStyleLayerInOrderWithSetsOfItsOwn() {
        FittedText text = new FittedText();
        // Two layers, which withStyle would have merged into one
        TextComponent.Styled inner = new TextComponent.Styled(TextComponent.literal(TEN), styles(TextFormattingStyle.GOLD));
        TextComponent.Styled outer = new TextComponent.Styled(inner, styles(TextFormattingStyle.UNDERLINE));
        text.fit(new FakeDrawContext(), 1, null, 0, false, 48, outer);
        inner.styles().add(TextFormattingStyle.ITALIC);
        outer.styles().add(TextFormattingStyle.STRIKETHROUGH);
        assertEquals(new TextComponent.Styled(new TextComponent.Styled(TextComponent.literal("ABCDE..."), styles(TextFormattingStyle.GOLD)),
                styles(TextFormattingStyle.UNDERLINE)), text.text());
        assertEquals(48, text.width());
    }

    @Test
    void aTextTheFontSourceHasNoTextForIsCutToTheEllipsisAloneWithoutCuttingNothing() {
        TextComponent styled = TextComponent.literal(TEN).withStyle(TextFormattingStyle.GOLD);
        for (String extracted : new String[]{null, ""}) {
            Widening blank = new Widening((plain, length) -> plain) {
                @Override
                public int getStringWidth(TextComponent component) {
                    return component.equals(styled) ? 500 : super.getStringWidth(component);
                }

                @Override
                public String extractString(TextComponent component) {
                    return component.equals(styled) ? extracted : super.extractString(component);
                }
            };
            FittedText text = new FittedText();
            text.fit(blank, 1, null, 0, false, 48, styled);
            assertEquals(TextComponent.literal("...").withStyle(TextFormattingStyle.GOLD), text.text());
            assertEquals(18, text.width());
            assertEquals(List.of(), blank.asked);
        }
    }

    @Test
    void aCutThatAStyleWidensByAFixedAmountIsCutOnceMoreShorterToFit() {
        Widening fonts = new Widening((plain, length) -> plain + 12);
        FittedText text = underlinedTen(fonts);
        // 48 wide, 60 underlined, so cut again into the 36 the overflow leaves
        assertEquals(TextComponent.literal("ABC...").withStyle(TextFormattingStyle.UNDERLINE), text.text());
        assertEquals(48, text.width());
        assertEquals(List.of(30, 18), fonts.asked);
    }

    @Test
    void aCutThatAStyleWidensForEachCharacterIsCutOnceMoreShorterToFit() {
        Widening fonts = new Widening((plain, length) -> plain + length);
        FittedText text = underlinedTen(fonts);
        // Eight characters overflow by eight, the six of the second cut need 42
        assertEquals(TextComponent.literal("ABC...").withStyle(TextFormattingStyle.UNDERLINE), text.text());
        assertEquals(42, text.width());
        assertEquals(List.of(30, 22), fonts.asked);
    }

    @Test
    void aStyleThatOverflowsEveryCutLeavesTheTextOutAfterTwoCutsAtMost() {
        // One pixel over whatever is cut, so the second cut is only a pixel shorter and still overflows
        Widening always = new Widening((plain, length) -> 49);
        assertNull(underlinedTen(always).text());
        assertEquals(List.of(30, 29), always.asked);
        // A hundred over leaves no room for a second cut
        Widening wide = new Widening((plain, length) -> plain + 100);
        FittedText text = underlinedTen(wide);
        assertNull(text.text());
        assertEquals(0, text.width());
        assertEquals(List.of(30), wide.asked);
    }

    @Test
    void aStyledCutMeasuredNegativeIsLeftOutWithoutCuttingAgain() {
        Widening negative = new Widening((plain, length) -> -1);
        assertNull(underlinedTen(negative).text());
        assertEquals(List.of(30), negative.asked);
    }

    @Test
    void aBadPlainWidthCanNeitherWrapTheSecondCutsRoomNorKeepItFromShrinking() {
        for (int bad : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            // The first cut measures right inside the cut and badly when measured again for the second
            Widening fonts = new Widening((plain, length) -> plain + 100) {
                private int measured;

                @Override
                public int getStringWidth(TextComponent component) {
                    if (component.equals(TextComponent.literal("ABCDE...")) && this.measured++ == 1) {
                        return bad;
                    }
                    return super.getStringWidth(component);
                }
            };
            assertNull(underlinedTen(fonts).text());
            // Far below zero stays there instead of wrapping to a huge room, and far above is held a pixel under the first
            assertEquals(bad < 0 ? List.of(30) : List.of(30, 29), fonts.asked);
        }
    }

    @Test
    void aFitThatThrowsKeepsThePreviousTextAndWhatItWasFittedFor() {
        FittedText text = new FittedText();
        text.fit(new FakeDrawContext(), 1, "A", 0, false, 60, TextComponent.literal("A"));
        IllegalStateException broken = new IllegalStateException("fonts");
        FakeDrawContext throwing = new FakeDrawContext() {
            @Override
            public int getStringWidth(TextComponent component) {
                throw broken;
            }
        };
        assertSame(broken, assertThrows(IllegalStateException.class, () -> text.fit(throwing, 2, "B", 1, true, 50, TextComponent.literal("B"))));
        // A style layer with no set fits in the fake fonts but cannot be copied
        TextComponent unset = new TextComponent.Styled(TextComponent.literal("B"), null);
        assertThrows(NullPointerException.class, () -> text.fit(new FakeDrawContext(), 2, "B", 1, true, 50, unset));
        assertTrue(text.isFittedFor(1, "A", 0, false, 60));
        assertFalse(text.isFittedFor(2, "B", 1, true, 50));
        assertEquals(TextComponent.literal("A"), text.text());
        assertEquals(6, text.width());
    }

    @Test
    void cutLeavesNothingWhenNotEvenTheSuffixFits() {
        FakeDrawContext fonts = new FakeDrawContext();
        for (int room : new int[]{17, 0, -1, Integer.MIN_VALUE}) {
            assertEquals("", FittedText.cut(fonts, TEN, "...", room));
        }
        // A suffix the font source measures negative never fits
        FakeDrawContext negative = new FakeDrawContext() {
            @Override
            public int getStringWidth(TextComponent component) {
                return -1;
            }
        };
        assertEquals("", FittedText.cut(negative, TEN, "...", 100));
    }

    @Test
    void cutGivesTheSuffixAloneWhenItOnlyJustFits() {
        FakeDrawContext fonts = new FakeDrawContext();
        assertEquals("...", FittedText.cut(fonts, TEN, "...", 18));
        // Five pixels after the suffix hold no letter
        assertEquals("...", FittedText.cut(fonts, TEN, "...", 23));
        assertEquals("A...", FittedText.cut(fonts, TEN, "...", 24));
    }

    @Test
    void cutGivesTheSuffixAloneForNoTextWithoutAskingTheFontSourceToCutIt() {
        Widening fonts = new Widening((plain, length) -> plain);
        assertEquals("...", FittedText.cut(fonts, null, "...", 100));
        assertEquals("...", FittedText.cut(fonts, "", "...", 100));
        assertEquals(List.of(), fonts.asked);
        // Still nothing where the suffix does not fit
        assertEquals("", FittedText.cut(fonts, null, "...", 17));
    }

    @Test
    void cutAsksForTheRoomLessTheSuffixEvenInTheLargestRoom() {
        Widening fonts = new Widening((plain, length) -> plain);
        assertEquals("ABCDEFGHIJ...", FittedText.cut(fonts, TEN, "...", 100));
        assertEquals("ABCDEFGHIJ...", FittedText.cut(fonts, TEN, "...", Integer.MAX_VALUE));
        assertEquals(List.of(82, Integer.MAX_VALUE - 18), fonts.asked);
    }

    @Test
    void cutKeepsTheSuffixAloneWhenTheFontSourceCutsNothingOrTooLittle() {
        for (String given : new String[]{null, TEN, TEN + TEN}) {
            FakeDrawContext bad = new FakeDrawContext() {
                @Override
                public String substrByWidth(String str, int maxWidth) {
                    return given;
                }
            };
            assertEquals("...", FittedText.cut(bad, TEN, "...", 48), String.valueOf(given));
        }
    }

    @Test
    void cutDropsHalfACharacterButKeepsAWholeOne() {
        FakeDrawContext fonts = new FakeDrawContext();
        // 35 leaves 17 for two chars, the second half of the emoji's pair
        assertEquals("A...", FittedText.cut(fonts, "A😀BCDEF", "...", 35));
        // 36 leaves room for both halves
        assertEquals("A😀...", FittedText.cut(fonts, "A😀BCDEF", "...", 36));
        // A lone second half is not half of anything the cut split
        assertEquals("A\uDE00...", FittedText.cut(fonts, "A\uDE00BCDEF", "...", 30));
    }

    @Test
    void cutDropsTheSpacesBeforeTheSuffixButNotOnesThatDoNotBreak() {
        FakeDrawContext fonts = new FakeDrawContext();
        assertEquals("AB...", FittedText.cut(fonts, "AB \tCDEFGH", "...", 42));
        assertEquals("AB...", FittedText.cut(fonts, "AB　CDEFGH", "...", 36));
        assertEquals("AB ...", FittedText.cut(fonts, "AB CDEFGH", "...", 36));
        // A half character then a space both go
        assertEquals("A...", FittedText.cut(fonts, "A 😀BCDEF", "...", 36));
        // A cut of nothing but spaces leaves the suffix alone
        assertEquals("...", FittedText.cut(fonts, "    ABCDEF", "...", 42));
    }

    @Test
    void fitsTakesOnlyAWidthFromZeroUpToTheRoom() {
        assertTrue(FittedText.fits(0, 0));
        assertTrue(FittedText.fits(5, 5));
        assertTrue(FittedText.fits(0, Integer.MAX_VALUE));
        assertTrue(FittedText.fits(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(FittedText.fits(6, 5));
        assertFalse(FittedText.fits(0, -1));
        assertFalse(FittedText.fits(-1, 10));
        assertFalse(FittedText.fits(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(FittedText.fits(Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void copiedLeavesNothingPlainTextAndUnknownComponentsAsTheyAre() {
        assertNull(FittedText.copied(null));
        TextComponent literal = TextComponent.literal("A");
        assertSame(literal, FittedText.copied(literal));
        TextComponent custom = new TextComponent() { };
        assertSame(custom, FittedText.copied(custom));
    }

    @Test
    void copiedGivesEveryStyleLayerASetOfItsOwn() {
        TextComponent.Styled inner = new TextComponent.Styled(TextComponent.literal("A"), styles(TextFormattingStyle.GOLD));
        TextComponent.Styled outer = new TextComponent.Styled(inner, styles(TextFormattingStyle.UNDERLINE, TextFormattingStyle.ITALIC));
        TextComponent.Styled copy = (TextComponent.Styled) FittedText.copied(outer);
        assertEquals(outer, copy);
        assertNotSame(outer.styles(), copy.styles());
        assertNotSame(inner.styles(), ((TextComponent.Styled) copy.inner()).styles());
        inner.styles().clear();
        outer.styles().add(TextFormattingStyle.STRIKETHROUGH);
        assertEquals(new TextComponent.Styled(new TextComponent.Styled(TextComponent.literal("A"), styles(TextFormattingStyle.GOLD)),
                styles(TextFormattingStyle.UNDERLINE, TextFormattingStyle.ITALIC)), copy);
        // A layer over nothing stays over nothing, and one with no set is refused
        assertEquals(new TextComponent.Styled(null, styles(TextFormattingStyle.GOLD)), FittedText.copied(new TextComponent.Styled(null, styles(TextFormattingStyle.GOLD))));
        assertThrows(NullPointerException.class, () -> FittedText.copied(new TextComponent.Styled(TextComponent.literal("A"), null)));
    }

    @Test
    void copiedGivesATranslationKeyAndArgumentListsOfItsOwnThatNothingCanChange() {
        List<String> keys = new ArrayList<>(List.of("first", "second"));
        Object kept = new Object();
        TextComponent.Styled styledArgument = new TextComponent.Styled(TextComponent.literal("y"), styles(TextFormattingStyle.RED));
        Object[] args = {kept, null, 5, styledArgument};
        TextComponent.Translatable original = new TextComponent.Translatable(keys, Arrays.asList(args));
        TextComponent.Translatable copy = (TextComponent.Translatable) FittedText.copied(original);
        assertEquals(original, copy);
        // Arguments that are not components are the same objects, nulls included
        assertSame(kept, copy.args().get(0));
        assertNull(copy.args().get(1));
        assertNotSame(styledArgument, copy.args().get(3));
        keys.set(0, "changed");
        args[2] = 6;
        styledArgument.styles().add(TextFormattingStyle.ITALIC);
        assertEquals(new TextComponent.Translatable(List.of("first", "second"),
                Arrays.asList(kept, null, 5, new TextComponent.Styled(TextComponent.literal("y"), styles(TextFormattingStyle.RED)))), copy);
        assertThrows(UnsupportedOperationException.class, () -> copy.keys().add("more"));
        assertThrows(UnsupportedOperationException.class, () -> copy.args().set(1, null));
        // A translation with no arguments is copied the same way and still equal
        TextComponent plain = TextComponent.translatable("key");
        assertEquals(plain, FittedText.copied(plain));
        // Arguments that are translations are copied all the way down
        Object[] deep = {"x"};
        TextComponent nested = TextComponent.translatable("outer", TextComponent.translatable("inner", deep));
        TextComponent nestedCopy = FittedText.copied(nested);
        deep[0] = "changed";
        assertEquals(TextComponent.translatable("outer", TextComponent.translatable("inner", "x")), nestedCopy);
    }

    @Test
    void copiedRefusesATranslationWithNoKeysNoArgumentsOrANullKey() {
        assertThrows(NullPointerException.class, () -> FittedText.copied(new TextComponent.Translatable(null, List.of())));
        assertThrows(NullPointerException.class, () -> FittedText.copied(new TextComponent.Translatable(List.of("key"), null)));
        assertThrows(NullPointerException.class, () -> FittedText.copied(new TextComponent.Translatable(Arrays.asList("key", null), List.of())));
    }

    @Test
    void copiedRefusesTextNestedPastItsDepthAndAnArgumentListHoldingItsOwnTranslation() {
        TextComponent deepest = layered(256);
        assertEquals(deepest, FittedText.copied(deepest));
        IllegalArgumentException tooDeep = assertThrows(IllegalArgumentException.class, () -> FittedText.copied(layered(257)));
        assertEquals("Text component nested more than 256 deep", tooDeep.getMessage());
        // A cycle a changed argument list made fails as a bad argument rather than overflowing the stack
        List<Object> args = new ArrayList<>();
        TextComponent.Translatable looped = new TextComponent.Translatable(List.of("key"), args);
        args.add(looped);
        assertThrows(IllegalArgumentException.class, () -> FittedText.copied(looped));
    }

    @Test
    void restyledWrapsTheTextInTheOriginalsLayersInOrderWithSetsOfItsOwn() {
        TextComponent.Styled inner = new TextComponent.Styled(TextComponent.literal("long"), styles(TextFormattingStyle.GOLD));
        TextComponent.Styled outer = new TextComponent.Styled(inner, styles(TextFormattingStyle.UNDERLINE));
        TextComponent text = TextComponent.literal("lo...");
        TextComponent.Styled restyled = (TextComponent.Styled) FittedText.restyled(outer, text);
        inner.styles().add(TextFormattingStyle.ITALIC);
        outer.styles().clear();
        assertEquals(new TextComponent.Styled(new TextComponent.Styled(text, styles(TextFormattingStyle.GOLD)), styles(TextFormattingStyle.UNDERLINE)), restyled);
        assertSame(text, ((TextComponent.Styled) restyled.inner()).inner());
    }

    @Test
    void restyledGivesTheTextAloneForAnOriginalWithNoStyleLayer() {
        TextComponent text = TextComponent.literal("lo...");
        assertSame(text, FittedText.restyled(TextComponent.literal("long"), text));
        assertSame(text, FittedText.restyled(TextComponent.translatable("key", TextComponent.literal("x").withStyle(TextFormattingStyle.RED)), text));
        assertSame(text, FittedText.restyled(null, text));
    }
}
