package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ControlValueFormatterTest {
    // The plain text of a literal, failing for any other kind of component
    private static String literal(TextComponent component) {
        return assertInstanceOf(TextComponent.Literal.class, component).text();
    }

    // A translation of the key with exactly these arguments, which an Integer argument only equals when the formatter also passed an Integer
    private static TextComponent translated(String key, Object... args) {
        return new TextComponent.Translatable(List.of(key), List.of(args));
    }

    // A quantity's singular form, which falls back to the plain key in a language without one
    private static TextComponent singular(String key, int value) {
        return new TextComponent.Translatable(List.of(key + ".one", key), List.of(value));
    }

    private static List<String> keys(TextComponent component) {
        return assertInstanceOf(TextComponent.Translatable.class, component).keys();
    }

    private static void refused(String message, Executable call) {
        assertEquals(message, assertThrows(NullPointerException.class, call).getMessage());
    }

    // One of every formatter, the capturing ones over short text
    private static List<ControlValueFormatter> everyFormatter() {
        return List.of(ControlValueFormatter.guiScale(), ControlValueFormatter.fpsLimit(), ControlValueFormatter.brightness(),
                ControlValueFormatter.biomeBlend(), ControlValueFormatter.translateVariable("key"), ControlValueFormatter.translateQuantity("key"),
                ControlValueFormatter.translateQuantityOrDisabled("key", "off"), ControlValueFormatter.percentage(),
                ControlValueFormatter.multiplier(), ControlValueFormatter.quantityOrDisabled("ms", "Off"), ControlValueFormatter.number());
    }

    @Test
    void everyFormatterShowsItsLabelsAndItsValues() {
        assertEquals(translated("options.guiScale.auto"), ControlValueFormatter.guiScale().format(0));
        assertEquals("3x", literal(ControlValueFormatter.guiScale().format(3)));
        assertEquals(translated("options.framerateLimit.max"), ControlValueFormatter.fpsLimit().format(260));
        assertEquals(translated("options.framerate", 60), ControlValueFormatter.fpsLimit().format(60));
        assertEquals(translated("options.gamma.min"), ControlValueFormatter.brightness().format(0));
        assertEquals(translated("options.gamma.max"), ControlValueFormatter.brightness().format(100));
        assertEquals("50%", literal(ControlValueFormatter.brightness().format(50)));
        assertEquals(translated("gui.none"), ControlValueFormatter.biomeBlend().format(0));
        assertEquals(singular("impetus.options.biome_blend.value", 1), ControlValueFormatter.biomeBlend().format(1));
        assertEquals(translated("impetus.options.biome_blend.value", 3), ControlValueFormatter.biomeBlend().format(3));
        assertEquals(translated("key", 7), ControlValueFormatter.translateVariable("key").format(7));
        assertEquals(translated("key", 7), ControlValueFormatter.translateQuantity("key").format(7));
        assertEquals(translated("off"), ControlValueFormatter.translateQuantityOrDisabled("key", "off").format(0));
        assertEquals("7%", literal(ControlValueFormatter.percentage().format(7)));
        assertEquals("7x", literal(ControlValueFormatter.multiplier().format(7)));
        assertEquals("off", literal(ControlValueFormatter.quantityOrDisabled("chunks", "off").format(0)));
        assertEquals("7 chunks", literal(ControlValueFormatter.quantityOrDisabled("chunks", "off").format(7)));
        assertEquals("7", literal(ControlValueFormatter.number().format(7)));
    }

    @Test
    void theFrameRateLimitReadsUnlimitedForEveryValueTheGameDoesNotLimitAt() {
        ControlValueFormatter fps = ControlValueFormatter.fpsLimit();
        // Vanilla limits only below 260, loads maxFps from options.txt unclamped, and its sync skips zero or less
        for (int unlimited : new int[]{260, 261, 270, 300, 1000, Integer.MAX_VALUE, 0, -1, -260, Integer.MIN_VALUE}) {
            assertEquals(translated("options.framerateLimit.max"), fps.format(unlimited), "maxFps " + unlimited);
        }
        assertEquals(translated("options.framerate", 259), fps.format(259));
        assertEquals(translated("options.framerate", 250), fps.format(250));
        assertEquals(translated("options.framerate", 10), fps.format(10));
        assertEquals(translated("options.framerate", 1), fps.format(1));
    }

    @Test
    void translatedQuantitiesTakeTheSingularFormOnlyAtOneAndMinusOne() {
        ControlValueFormatter quantity = ControlValueFormatter.translateQuantity("impetus.test.blocks");
        assertEquals(singular("impetus.test.blocks", 1), quantity.format(1));
        assertEquals(singular("impetus.test.blocks", -1), quantity.format(-1));
        // English plural rules go by the whole number, so 11, 21 and 0 stay plural
        for (int plural : new int[]{0, 2, -2, 11, 21, 101, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals(translated("impetus.test.blocks", plural), quantity.format(plural), "value " + plural);
        }
        // The key lists are built with the formatter and shared by every label
        assertSame(keys(quantity.format(1)), keys(quantity.format(-1)));
        assertSame(keys(quantity.format(2)), keys(quantity.format(5)));
    }

    @Test
    void translatedQuantitiesWithADisabledLabelShowItOnlyAtZero() {
        ControlValueFormatter quantity = ControlValueFormatter.translateQuantityOrDisabled("impetus.test.blocks", "impetus.test.off");
        assertEquals(translated("impetus.test.off"), quantity.format(0));
        assertSame(quantity.format(0), quantity.format(0));
        assertEquals(singular("impetus.test.blocks", 1), quantity.format(1));
        assertEquals(singular("impetus.test.blocks", -1), quantity.format(-1));
        assertEquals(translated("impetus.test.blocks", 2), quantity.format(2));
        assertEquals(translated("impetus.test.blocks", Integer.MIN_VALUE), quantity.format(Integer.MIN_VALUE));
        // Biome blend is one of these, with the none label at zero
        ControlValueFormatter blend = ControlValueFormatter.biomeBlend();
        assertEquals(translated("gui.none"), blend.format(0));
        assertEquals(singular("impetus.options.biome_blend.value", -1), blend.format(-1));
        assertEquals(translated("impetus.options.biome_blend.value", 7), blend.format(7));
    }

    @Test
    void specialLabelsStandOnlyForTheirExactValues() {
        assertEquals("1x", literal(ControlValueFormatter.guiScale().format(1)));
        assertEquals("-1x", literal(ControlValueFormatter.guiScale().format(-1)));
        assertEquals("1%", literal(ControlValueFormatter.brightness().format(1)));
        assertEquals("-1%", literal(ControlValueFormatter.brightness().format(-1)));
        assertEquals("99%", literal(ControlValueFormatter.brightness().format(99)));
        // Gamma past full from options.txt shows its real strength rather than the maximum label
        assertEquals("101%", literal(ControlValueFormatter.brightness().format(101)));
        assertEquals("1500%", literal(ControlValueFormatter.brightness().format(1500)));
        assertEquals(translated("impetus.options.biome_blend.value", 2), ControlValueFormatter.biomeBlend().format(2));
        assertEquals(translated("impetus.options.biome_blend.value", -2), ControlValueFormatter.biomeBlend().format(-2));
        assertEquals("1 ms", literal(ControlValueFormatter.quantityOrDisabled("ms", "Off").format(1)));
        assertEquals("-1 ms", literal(ControlValueFormatter.quantityOrDisabled("ms", "Off").format(-1)));
        // Zero is nothing special to the plain formatters, and one is nothing special to the untranslated ones
        assertEquals("0%", literal(ControlValueFormatter.percentage().format(0)));
        assertEquals("0x", literal(ControlValueFormatter.multiplier().format(0)));
        assertEquals("0", literal(ControlValueFormatter.number().format(0)));
        assertEquals(translated("key", 0), ControlValueFormatter.translateVariable("key").format(0));
        assertEquals(translated("key", 1), ControlValueFormatter.translateVariable("key").format(1));
        assertEquals(translated("key", 0), ControlValueFormatter.translateQuantity("key").format(0));
    }

    @Test
    void bothEndsOfTheIntRangeFormatWithoutWrapping() {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        assertEquals("-2147483648x", literal(ControlValueFormatter.guiScale().format(min)));
        assertEquals("2147483647x", literal(ControlValueFormatter.guiScale().format(max)));
        assertEquals(translated("options.framerateLimit.max"), ControlValueFormatter.fpsLimit().format(min));
        assertEquals(translated("options.framerateLimit.max"), ControlValueFormatter.fpsLimit().format(max));
        assertEquals("-2147483648%", literal(ControlValueFormatter.brightness().format(min)));
        assertEquals("2147483647%", literal(ControlValueFormatter.brightness().format(max)));
        assertEquals(translated("impetus.options.biome_blend.value", min), ControlValueFormatter.biomeBlend().format(min));
        assertEquals(translated("impetus.options.biome_blend.value", max), ControlValueFormatter.biomeBlend().format(max));
        assertEquals(translated("key", min), ControlValueFormatter.translateVariable("key").format(min));
        assertEquals(translated("key", max), ControlValueFormatter.translateVariable("key").format(max));
        assertEquals(translated("key", min), ControlValueFormatter.translateQuantity("key").format(min));
        assertEquals(translated("key", max), ControlValueFormatter.translateQuantity("key").format(max));
        assertEquals("-2147483648%", literal(ControlValueFormatter.percentage().format(min)));
        assertEquals("2147483647%", literal(ControlValueFormatter.percentage().format(max)));
        assertEquals("-2147483648x", literal(ControlValueFormatter.multiplier().format(min)));
        assertEquals("2147483647x", literal(ControlValueFormatter.multiplier().format(max)));
        assertEquals("-2147483648 ms", literal(ControlValueFormatter.quantityOrDisabled("ms", "Off").format(min)));
        assertEquals("2147483647 ms", literal(ControlValueFormatter.quantityOrDisabled("ms", "Off").format(max)));
        assertEquals("-2147483648", literal(ControlValueFormatter.number().format(min)));
        assertEquals("2147483647", literal(ControlValueFormatter.number().format(max)));
    }

    @Test
    void aBlankUnitLeavesTheValueAloneWithNoTrailingSpace() {
        assertEquals("7", literal(ControlValueFormatter.quantityOrDisabled("", "Off").format(7)));
        assertEquals("-7", literal(ControlValueFormatter.quantityOrDisabled("", "Off").format(-7)));
        assertEquals("7", literal(ControlValueFormatter.quantityOrDisabled("   ", "Off").format(7)));
        assertEquals("Off", literal(ControlValueFormatter.quantityOrDisabled("", "Off").format(0)));
        // A unit and disabled text with something in them are taken exactly as given
        assertEquals("7  ms ", literal(ControlValueFormatter.quantityOrDisabled(" ms ", " Off ").format(7)));
        assertEquals(" Off ", literal(ControlValueFormatter.quantityOrDisabled(" ms ", " Off ").format(0)));
        assertEquals("", literal(ControlValueFormatter.quantityOrDisabled("ms", "").format(0)));
    }

    @Test
    void formattersKeepNoStateSoAValueAlwaysGivesAnEqualLabel() {
        for (ControlValueFormatter formatter : everyFormatter()) {
            for (int value : new int[]{5, 1, -1}) {
                TextComponent first = formatter.format(value);
                // Other values in between change nothing, which the draw context's caches keyed on equal components rely on
                for (int other : new int[]{0, 100, 260, -1, 1, Integer.MIN_VALUE}) {
                    formatter.format(other);
                }
                assertEquals(first, formatter.format(value));
                assertEquals(first.hashCode(), formatter.format(value).hashCode());
            }
        }
        // Every zero of a quantity shares the one immutable label built with the formatter
        ControlValueFormatter quantity = ControlValueFormatter.quantityOrDisabled("ms", "Off");
        assertSame(quantity.format(0), quantity.format(0));
    }

    @Test
    void factoriesWithNothingToCaptureHandBackOneSharedFormatter() {
        // A lambda capturing nothing is made once at its call site, so these factories allocate nothing after their first call
        List<Supplier<ControlValueFormatter>> shared = List.of(ControlValueFormatter::guiScale, ControlValueFormatter::fpsLimit,
                ControlValueFormatter::brightness, ControlValueFormatter::percentage, ControlValueFormatter::multiplier, ControlValueFormatter::number);
        for (Supplier<ControlValueFormatter> factory : shared) {
            assertSame(factory.get(), factory.get());
        }
        // Those capturing their text keep their own, so formatters made from different text never mix
        ControlValueFormatter first = ControlValueFormatter.translateVariable("first");
        ControlValueFormatter second = ControlValueFormatter.translateVariable("second");
        assertEquals(translated("first", 1), first.format(1));
        assertEquals(translated("second", 1), second.format(1));
        ControlValueFormatter chunks = ControlValueFormatter.translateQuantityOrDisabled("chunks", "default");
        ControlValueFormatter blocks = ControlValueFormatter.translateQuantityOrDisabled("blocks", "off");
        assertEquals(singular("chunks", 1), chunks.format(1));
        assertEquals(translated("default"), chunks.format(0));
        assertEquals(singular("blocks", 1), blocks.format(1));
        assertEquals(translated("off"), blocks.format(0));
        ControlValueFormatter literalChunks = ControlValueFormatter.quantityOrDisabled("chunks", "Default");
        ControlValueFormatter literalBlocks = ControlValueFormatter.quantityOrDisabled("blocks", "Off");
        assertEquals("2 chunks", literal(literalChunks.format(2)));
        assertEquals("Default", literal(literalChunks.format(0)));
        assertEquals("2 blocks", literal(literalBlocks.format(2)));
        assertEquals("Off", literal(literalBlocks.format(0)));
    }

    @Test
    void factoriesRefuseMissingTextWhenMadeRatherThanWhenAValueIsFormatted() {
        refused("Key must not be null", () -> ControlValueFormatter.translateVariable(null));
        refused("Key must not be null", () -> ControlValueFormatter.translateQuantity(null));
        refused("Key must not be null", () -> ControlValueFormatter.translateQuantityOrDisabled(null, "off"));
        refused("Disabled key must not be null", () -> ControlValueFormatter.translateQuantityOrDisabled("key", null));
        refused("Unit must not be null", () -> ControlValueFormatter.quantityOrDisabled(null, "off"));
        refused("Disabled text must not be null", () -> ControlValueFormatter.quantityOrDisabled("chunks", null));
        // The quantity is checked first when both are missing
        refused("Key must not be null", () -> ControlValueFormatter.translateQuantityOrDisabled(null, null));
        refused("Unit must not be null", () -> ControlValueFormatter.quantityOrDisabled(null, null));
    }

    @Test
    void anIdleSliderShowsAFrameRateTheGameDoesNotLimitAtAsUnlimited() {
        // options.txt holding 300 or 0 runs unlimited, so the row says so rather than 300 fps or 0 fps
        for (int stored : new int[]{300, 0, -5}) {
            @SuppressWarnings("unchecked")
            Option<Integer> option = Mockito.mock(Option.class);
            when(option.getValue()).thenReturn(stored);
            when(option.getName()).thenReturn(TextComponent.literal("Max Framerate"));
            when(option.isAvailable()).thenReturn(true);
            SliderControl control = new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit());
            when(option.getControl()).thenReturn(control);
            FakeDrawContext draw = new FakeDrawContext();
            control.createElement(new Dim2i(0, 0, 400, 20)).render(draw, 5000, 5000, 0f);
            assertEquals(List.of("Max Framerate", "options.framerateLimit.max"), draw.strings, "maxFps " + stored);
        }
    }
}
