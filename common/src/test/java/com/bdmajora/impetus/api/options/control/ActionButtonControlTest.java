package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.BasicFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.gui.widgets.SearchBarWidget;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActionButtonControlTest {
    // Right edge 210 and text line 26 (centre 30 less half the glyph height)
    private static final Dim2i DIM = new Dim2i(10, 20, 200, 20);
    private static final int LIMIT_X = 210;
    private static final int TEXT_Y = 26;
    private static final String DEFAULT_KEY = "impetus.options.buttons.open";
    private static final String MOD = "action_button_test";
    private static final Runnable NOTHING = () -> { };
    private static final String FAILURE_LOG = "Action button {} ({}) failed";

    // Records each string with the component it was drawn from; 6 pixels a character unless a width is pinned, and translation keys read from a table
    private static class RecordingDraw extends FakeDrawContext {
        final List<Text> texts = new ArrayList<>();
        final Map<String, Integer> widths = new HashMap<>();
        final Map<String, String> translations = new HashMap<>(Map.of(DEFAULT_KEY, "Open"));
        // How many times a label was cut, which a kept cut does not repeat
        int cuts;

        @Override
        public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
            this.texts.add(new Text(str, extractString(str), x, y, color, shadow));
            return super.drawString(str, x, y, color, shadow);
        }

        @Override
        public int getStringWidth(TextComponent component) {
            Integer pinned = this.widths.get(extractString(component));
            return pinned != null ? pinned : super.getStringWidth(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.cuts++;
            return super.substrByWidth(str, maxWidth);
        }

        // Moves on whenever the test changes a translation or pins a width, as a language or font reload moves a real context's version on
        @Override
        public long textVersion() {
            return 31L * this.translations.hashCode() + this.widths.hashCode();
        }

        @Override
        public String extractString(TextComponent component) {
            if (component instanceof TextComponent.Translatable translatable && this.translations.containsKey(translatable.keys().getFirst())) {
                return this.translations.get(translatable.keys().getFirst());
            }
            return super.extractString(component);
        }

        // The button's label is the one underlined string; the option name never is
        Text label() {
            List<Text> labels = this.texts.stream().filter(t -> styles(t.component()).contains(TextFormattingStyle.UNDERLINE)).toList();
            assertEquals(1, labels.size(), labels::toString);
            return labels.getFirst();
        }

        boolean drewNoLabel() {
            return this.texts.stream().noneMatch(t -> styles(t.component()).contains(TextFormattingStyle.UNDERLINE));
        }

        Text arrow() {
            Text last = this.texts.getLast();
            assertEquals(">", last.text());
            return last;
        }

        boolean drewNoArrow() {
            return this.texts.stream().noneMatch(t -> t.text().equals(">"));
        }

        void clear() {
            this.texts.clear();
            this.calls.clear();
            this.strings.clear();
        }
    }

    // Records like RecordingDraw while an underlined component measures what the operator makes of its plain width and length, keeping every width a cut was asked for
    private static class Widening extends RecordingDraw {
        final List<Integer> asked = new ArrayList<>();
        private final IntBinaryOperator underlined;

        Widening(IntBinaryOperator underlined) {
            this.underlined = underlined;
        }

        @Override
        public int getStringWidth(TextComponent component) {
            int plain = super.getStringWidth(component);
            return styles(component).contains(TextFormattingStyle.UNDERLINE) ? this.underlined.applyAsInt(plain, extractString(component).length()) : plain;
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.asked.add(maxWidth);
            return super.substrByWidth(str, maxWidth);
        }
    }

    private record Text(TextComponent component, String text, int x, int y, int color, boolean shadow) {}

    private static Set<TextFormattingStyle> styles(TextComponent component) {
        return component instanceof TextComponent.Styled styled ? styled.styles() : EnumSet.noneOf(TextFormattingStyle.class);
    }

    // A mock option whose availability the test flips, with the name the base element draws
    private static Option<Boolean> option(AtomicBoolean available) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class);
        when(option.isAvailable()).thenAnswer(invocation -> available.get());
        when(option.getName()).thenReturn(TextComponent.literal("Name"));
        return option;
    }

    private static ControlElement<Boolean> element(Option<Boolean> option, TextComponent label, Runnable action, Dim2i dim) {
        ActionButtonControl control = new ActionButtonControl(option, label, action);
        when(option.getControl()).thenReturn(control);
        return control.createElement(dim);
    }

    private static RecordingDraw render(ControlElement<Boolean> element) {
        RecordingDraw draw = new RecordingDraw();
        element.render(draw, 0, 0, 0f);
        return draw;
    }

    // Ten letters, sixty pixels plain, on a 66 wide row that leaves the label 48 of the column
    private static ControlElement<Boolean> tenInRoom48(AtomicBoolean available) {
        return element(option(available), TextComponent.literal("ABCDEFGHIJ"), NOTHING, new Dim2i(0, 0, 66, 20));
    }

    @Test
    void constructorsRefuseNullsInParameterOrder() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        TextComponent label = TextComponent.literal("Go");
        npe("Option must not be null", () -> new ActionButtonControl(null, NOTHING));
        // The short form fills in the default label, so its only other null is the action
        npe("Action must not be null", () -> new ActionButtonControl(option, null));
        npe("Option must not be null", () -> new ActionButtonControl(null, null));
        npe("Option must not be null", () -> new ActionButtonControl(null, label, NOTHING));
        npe("Action label must not be null", () -> new ActionButtonControl(option, null, NOTHING));
        npe("Action must not be null", () -> new ActionButtonControl(option, label, null));
        npe("Option must not be null", () -> new ActionButtonControl(null, null, null));
        npe("Action label must not be null", () -> new ActionButtonControl(option, null, null));
    }

    @Test
    void constructingTheControlAndItsElementsTouchesNeitherTheOptionNorTheAction() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        clearInvocations(option);
        AtomicInteger runs = new AtomicInteger();
        ActionButtonControl control = new ActionButtonControl(option, runs::incrementAndGet);
        new ActionButtonControl(option, TextComponent.literal("Go"), runs::incrementAndGet).createElement(DIM);
        control.createElement(DIM);
        control.createElement(DIM.withX(500));
        verifyNoInteractions(option);
        assertEquals(0, runs.get());
    }

    @Test
    void theControlHandsOutItsOptionAFixedWidthAndAFreshElementPerCall() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        ActionButtonControl control = new ActionButtonControl(option, NOTHING);
        assertTrue(Control.class.isAssignableFrom(ActionButtonControl.class));
        assertSame(option, control.getOption());
        assertEquals(70, control.getMaxWidth());
        assertEquals(70, control.getMaxWidth());
        Dim2i elsewhere = new Dim2i(-5, 300, 120, 18);
        ControlElement<Boolean> first = control.createElement(DIM);
        ControlElement<Boolean> second = control.createElement(elsewhere);
        assertNotSame(first, second);
        assertNotSame(first, control.createElement(DIM));
        assertSame(option, first.getOption());
        assertSame(option, second.getOption());
        assertSame(DIM, first.getDimensions());
        assertSame(elsewhere, second.getDimensions());
        // The element is the control's own private, final button
        Class<?> type = first.getClass();
        assertSame(ActionButtonControl.class, type.getEnclosingClass());
        assertTrue(Modifier.isPrivate(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
    }

    @Test
    void nullBoundsAreRefusedWhenTheElementIsMadeRatherThanOnItsFirstFrame() {
        ActionButtonControl control = new ActionButtonControl(option(new AtomicBoolean(true)), NOTHING);
        npe("Dimensions must not be null", () -> control.createElement(null));
    }

    @Test
    void theDefaultLabelIsOneSharedOpenKey() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        TextComponent shared = Statics.get(ActionButtonControl.class, "DEFAULT_LABEL");
        assertEquals(TextComponent.translatable(DEFAULT_KEY), shared);
        for (int i = 0; i < 2; i++) {
            ActionButtonControl control = new ActionButtonControl(option, NOTHING);
            when(option.getControl()).thenReturn(control);
            RecordingDraw draw = render(control.createElement(DIM));
            Text label = draw.label();
            assertEquals(shared, ((TextComponent.Styled) label.component()).inner());
            // Measured and placed by its translation, not its key
            assertEquals("Open", label.text());
            assertEquals(LIMIT_X - 24 - 18, label.x());
        }
    }

    @Test
    void aFrameDrawsTheBackgroundNameUnderlinedLabelAndArrowInThatOrder() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        RecordingDraw draw = render(element(option, TextComponent.literal("Go"), NOTHING, DIM));
        assertEquals(4, draw.calls.size(), draw.calls::toString);
        assertTrue(draw.calls.getFirst().startsWith("fill(10,20,210,40,"), draw.calls::toString);
        assertEquals(3, draw.texts.size());
        assertEquals("Name", draw.texts.getFirst().text());
        assertEquals(16, draw.texts.getFirst().x());

        Text label = draw.label();
        assertSame(draw.texts.get(1), label);
        assertEquals("Go", label.text());
        assertEquals(EnumSet.of(TextFormattingStyle.UNDERLINE), styles(label.component()));
        // Right-aligned 18 pixels in from the edge, on the same line as the name
        assertEquals(LIMIT_X - 12 - 18, label.x());
        assertEquals(TEXT_Y, label.y());
        assertEquals(TEXT_Y, draw.texts.getFirst().y());
        assertFalse(label.shadow());

        Text arrow = draw.arrow();
        assertEquals(LIMIT_X - 10, arrow.x());
        assertEquals(TEXT_Y, arrow.y());
        assertInstanceOf(TextComponent.Literal.class, arrow.component());
        assertFalse(arrow.shadow());
        assertEquals(label.color(), arrow.color());
    }

    @Test
    void textFollowsTheRowWhereverItIs() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        Dim2i odd = new Dim2i(-40, 7, 151, 15);
        RecordingDraw draw = render(element(option, TextComponent.literal("Go"), NOTHING, odd));
        // Right edge 111; centre 7 + 15 / 2 = 14 rounds down, then up 4
        assertEquals(111 - 12 - 18, draw.label().x());
        assertEquals(10, draw.label().y());
        assertEquals(101, draw.arrow().x());
        assertEquals(10, draw.arrow().y());
    }

    @Test
    void theAccentComesFromTheOptionsModOrTheDefaultWithoutAnId() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        assertEquals(DefaultColors.getModAccentColor(null), render(element).label().color());

        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.create(MOD, "accent", Boolean.class));
        RecordingDraw draw = render(element);
        assertEquals(DefaultColors.getModAccentColor(MOD), draw.label().color());
        assertEquals(DefaultColors.getModAccentColor(MOD), draw.arrow().color());

        // A platform's own accent wins over the theme's
        RecordingDraw platform = new RecordingDraw() {
            @Override
            public int getModAccentColor(String modId) {
                assertEquals(MOD, modId);
                return 0xFF123456;
            }
        };
        element.render(platform, 0, 0, 0f);
        assertEquals(0xFF123456, platform.label().color());
        assertEquals(0xFF123456, platform.arrow().color());
    }

    @Test
    void anUnavailableOptionGreysAndStrikesTheLabelAndGreysTheArrow() {
        AtomicBoolean available = new AtomicBoolean(false);
        Option<Boolean> option = option(available);
        when(option.getId()).thenAnswer(invocation -> OptionIdentifier.create(MOD, "greyed", Boolean.class));
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        RecordingDraw draw = render(element);
        Text label = draw.label();
        assertEquals("Go", label.text());
        assertEquals(EnumSet.of(TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(label.component()));
        assertEquals(DefaultColors.TEXT_DISABLED, label.color());
        assertEquals(DefaultColors.TEXT_DISABLED, draw.arrow().color());
        assertInstanceOf(TextComponent.Literal.class, draw.arrow().component());
        assertEquals(LIMIT_X - 12 - 18, label.x());

        // Availability is read every frame
        available.set(true);
        draw = render(element);
        assertEquals(EnumSet.of(TextFormattingStyle.UNDERLINE), styles(draw.label().component()));
        assertEquals(DefaultColors.getModAccentColor(MOD), draw.arrow().color());
        available.set(false);
        assertEquals(DefaultColors.TEXT_DISABLED, render(element).arrow().color());
    }

    @Test
    void aLabelThatFitsIsBuiltOnceAndReusedEveryFrame() {
        AtomicBoolean available = new AtomicBoolean(true);
        Option<Boolean> option = option(available);
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        TextComponent enabled = render(element).label().component();
        for (int frame = 0; frame < 3; frame++) {
            assertSame(enabled, render(element).label().component());
        }
        available.set(false);
        TextComponent disabled = render(element).label().component();
        assertNotSame(enabled, disabled);
        assertSame(disabled, render(element).label().component());
        available.set(true);
        assertSame(enabled, render(element).label().component());
        // Each element builds its own pair
        assertNotSame(enabled, render(element(option, TextComponent.literal("Go"), NOTHING, DIM)).label().component());
    }

    @Test
    void theCallersStylesAreKeptAndTheirLabelIsLeftAlone() {
        AtomicBoolean available = new AtomicBoolean(true);
        TextComponent italic = TextComponent.literal("Go").withStyle(TextFormattingStyle.ITALIC);
        ControlElement<Boolean> element = element(option(available), italic, NOTHING, DIM);
        assertEquals(EnumSet.of(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE), styles(render(element).label().component()));
        available.set(false);
        assertEquals(EnumSet.of(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH),
                styles(render(element).label().component()));
        assertEquals(EnumSet.of(TextFormattingStyle.ITALIC), styles(italic));
    }

    @Test
    void aLabelExactlyAsWideAsTheColumnAllowsIsDrawnWholeAndOnePixelMoreIsCut() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        ControlElement<Boolean> element = element(option, TextComponent.literal("Settings"), NOTHING, DIM);
        // 70 wide less the 18 kept for the arrow leaves 52
        RecordingDraw draw = new RecordingDraw();
        draw.widths.put("Settings", 52);
        element.render(draw, 0, 0, 0f);
        assertEquals("Settings", draw.label().text());
        assertEquals(LIMIT_X - 70, draw.label().x());

        draw = new RecordingDraw();
        draw.widths.put("Settings", 53);
        element.render(draw, 0, 0, 0f);
        // 52 less the 18 pixel ellipsis leaves 34 for text, five characters here
        assertEquals("Setti...", draw.label().text());
        assertEquals(LIMIT_X - 48 - 18, draw.label().x());
    }

    @Test
    void aLongLabelIsCutWithAnEllipsisInsideTheButtonsColumn() {
        AtomicBoolean available = new AtomicBoolean(true);
        Option<Boolean> option = option(available);
        ControlElement<Boolean> element = element(option, TextComponent.literal("Configure shaders").withStyle(TextFormattingStyle.ITALIC), NOTHING, DIM);
        RecordingDraw draw = render(element);
        Text label = draw.label();
        assertEquals("Confi...", label.text());
        // The cut keeps the label's own italic, underlined like the whole label
        assertEquals(TextComponent.literal("Confi...").withStyle(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE), label.component());
        assertEquals(LIMIT_X - 48 - 18, label.x());
        // Clear of the column's left edge, and so of the option name ControlElement keeps out of it
        assertTrue(label.x() >= LIMIT_X - 70);
        // ControlElement cuts the name to the row width less the column and a 12 pixel gap, starting 6 in
        assertTrue(DIM.x() + 6 + DIM.width() - 70 - 12 <= label.x());
        assertEquals(DefaultColors.getModAccentColor(null), label.color());

        available.set(false);
        label = render(element).label();
        assertEquals("Confi...", label.text());
        assertEquals(EnumSet.of(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), styles(label.component()));
        assertEquals(DefaultColors.TEXT_DISABLED, label.color());
    }

    @Test
    void aTranslatedLabelIsCutAgainWhenTheLanguageChanges() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        ControlElement<Boolean> element = element(option, TextComponent.translatable(DEFAULT_KEY), NOTHING, DIM);
        RecordingDraw draw = new RecordingDraw();
        element.render(draw, 0, 0, 0f);
        assertEquals("Open", draw.label().text());
        draw.clear();
        draw.translations.put(DEFAULT_KEY, "Öffnen und einrichten");
        element.render(draw, 0, 0, 0f);
        assertEquals("Öffne...", draw.label().text());
        draw.clear();
        draw.translations.put(DEFAULT_KEY, "Open");
        element.render(draw, 0, 0, 0f);
        assertEquals("Open", draw.label().text());
        assertEquals(LIMIT_X - 24 - 18, draw.label().x());
    }

    @Test
    void narrowRowsShrinkTheColumnAndDropALabelThatCannotFitEvenTheEllipsis() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        // 40 wide leaves 22: a short label fits, a long one cuts to the bare ellipsis
        assertEquals(40 - 12 - 18, render(element(option, TextComponent.literal("Go"), NOTHING, new Dim2i(0, 0, 40, 20))).label().x());
        Text bare = render(element(option, TextComponent.literal("Configure"), NOTHING, new Dim2i(0, 0, 40, 20))).label();
        assertEquals("...", bare.text());
        assertEquals(40 - 18 - 18, bare.x());
        // 36 wide leaves exactly the ellipsis, drawn at the row's left edge
        Text tight = render(element(option, TextComponent.literal("Configure"), NOTHING, new Dim2i(0, 0, 36, 20))).label();
        assertEquals("...", tight.text());
        assertEquals(0, tight.x());
        // One narrower and nothing of the label fits, so only the arrow is drawn
        RecordingDraw none = render(element(option, TextComponent.literal("Configure"), NOTHING, new Dim2i(0, 0, 35, 20)));
        assertTrue(none.drewNoLabel());
        assertEquals(25, none.arrow().x());
        assertEquals(35 - 12 - 18, render(element(option, TextComponent.literal("Go"), NOTHING, new Dim2i(0, 0, 35, 20))).label().x());
        // Ten wide is the narrowest row the arrow fits in, drawn at its left edge with no room left for a label
        RecordingDraw ten = render(element(option, TextComponent.literal("Go"), NOTHING, new Dim2i(0, 0, 10, 20)));
        assertTrue(ten.drewNoLabel());
        assertEquals(0, ten.arrow().x());
        // Any narrower, zero wide or even negative, and the button draws nothing at all outside the row
        for (int width : new int[]{9, 1, 0, -5}) {
            RecordingDraw nothing = render(element(option, TextComponent.literal("Go"), NOTHING, new Dim2i(0, 0, width, 20)));
            assertTrue(nothing.drewNoLabel(), () -> "width " + width);
            assertTrue(nothing.drewNoArrow(), () -> "width " + width);
            // Nor does ControlElement, which has no room for even the name's ellipsis
            assertEquals(0, nothing.texts.size(), () -> "width " + width);
        }
    }

    @Test
    void anEmptyLabelDrawsNothingVisibleButKeepsTheArrow() {
        RecordingDraw draw = render(element(option(new AtomicBoolean(true)), TextComponent.literal(""), NOTHING, DIM));
        assertEquals("", draw.label().text());
        assertEquals(LIMIT_X - 18, draw.label().x());
        assertEquals(LIMIT_X - 10, draw.arrow().x());
    }

    @Test
    void theArrowIsOneSharedComponentForEveryFrameAndButton() {
        TextComponent shared = Statics.get(ActionButtonControl.class, "ARROW");
        assertEquals(TextComponent.literal(">"), shared);
        Option<Boolean> option = option(new AtomicBoolean(true));
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        assertSame(shared, render(element).arrow().component());
        assertSame(shared, render(element).arrow().component());
        assertSame(shared, render(element(option, TextComponent.literal("Other"), NOTHING, DIM)).arrow().component());
    }

    @Test
    void aColouredLabelKeepsItsColourWhileEnabledAndLosesItWhileUnavailableSoTheGreyShows() {
        AtomicBoolean available = new AtomicBoolean(true);
        TextComponent red = TextComponent.literal("Go").withStyle(TextFormattingStyle.RED, TextFormattingStyle.ITALIC);
        ControlElement<Boolean> element = element(option(available), red, NOTHING, DIM);
        assertEquals(TextComponent.literal("Go").withStyle(TextFormattingStyle.RED, TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE),
                render(element).label().component());
        available.set(false);
        // Vanilla applies the set's colours in order, so a kept RED would land after GRAY and win
        assertEquals(TextComponent.literal("Go").withStyle(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH),
                render(element).label().component());
        // The caller's label is left as it was
        assertEquals(EnumSet.of(TextFormattingStyle.RED, TextFormattingStyle.ITALIC), styles(red));
    }

    @Test
    void layersThatHeldOnlyColoursAreDroppedFromTheDisabledLabel() {
        AtomicBoolean available = new AtomicBoolean(false);
        TextComponent.Styled colours = new TextComponent.Styled(
                new TextComponent.Styled(TextComponent.literal("Go"), EnumSet.of(TextFormattingStyle.RED)), EnumSet.of(TextFormattingStyle.GOLD));
        TextComponent label = render(element(option(available), colours, NOTHING, DIM)).label().component();
        assertEquals(new TextComponent.Styled(TextComponent.literal("Go"),
                EnumSet.of(TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH)), label);
        available.set(true);
        // Enabled, both layers stay, the outer one underlined
        assertEquals(new TextComponent.Styled(new TextComponent.Styled(TextComponent.literal("Go"), EnumSet.of(TextFormattingStyle.RED)),
                EnumSet.of(TextFormattingStyle.GOLD, TextFormattingStyle.UNDERLINE)), render(element(option(available), colours, NOTHING, DIM)).label().component());
    }

    @Test
    void translationArgumentsThatAreComponentsLoseTheirColoursWhileUnavailable() {
        AtomicBoolean available = new AtomicBoolean(false);
        TextComponent argument = TextComponent.literal("x").withStyle(TextFormattingStyle.RED, TextFormattingStyle.ITALIC);
        TextComponent label = TextComponent.translatable("impetus.test.with_argument", argument, 5, null);
        ControlElement<Boolean> element = element(option(available), label, NOTHING, DIM);
        RecordingDraw draw = new RecordingDraw();
        draw.translations.put("impetus.test.with_argument", "Go");
        element.render(draw, 0, 0, 0f);
        TextComponent inner = ((TextComponent.Styled) draw.label().component()).inner();
        // Plain arguments, nulls included, pass through untouched
        assertEquals(new TextComponent.Translatable(List.of("impetus.test.with_argument"),
                Arrays.asList(TextComponent.literal("x").withStyle(TextFormattingStyle.ITALIC), 5, null)), inner);
        // While enabled the translation is an equal copy of the caller's, arguments and all
        available.set(true);
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertEquals(label, ((TextComponent.Styled) draw.label().component()).inner());
        // A translation with no component arguments is kept as it is even while unavailable
        available.set(false);
        TextComponent plain = TextComponent.translatable(DEFAULT_KEY, 3);
        RecordingDraw other = new RecordingDraw();
        other.translations.put(DEFAULT_KEY, "Open");
        element(option(available), plain, NOTHING, DIM).render(other, 0, 0, 0f);
        assertEquals(plain, ((TextComponent.Styled) other.label().component()).inner());
    }

    @Test
    void aCutLabelKeepsItsColoursAndStylesWhileEnabledAndDropsOnlyTheColoursWhileUnavailable() {
        AtomicBoolean available = new AtomicBoolean(true);
        TextComponent label = TextComponent.literal("Configure shaders").withStyle(TextFormattingStyle.RED, TextFormattingStyle.ITALIC);
        ControlElement<Boolean> element = element(option(available), label, NOTHING, DIM);
        Text cut = render(element).label();
        assertEquals(TextComponent.literal("Confi...").withStyle(TextFormattingStyle.RED, TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE), cut.component());
        assertEquals(LIMIT_X - 48 - 18, cut.x());
        available.set(false);
        Text disabled = render(element).label();
        assertEquals(TextComponent.literal("Confi...").withStyle(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH),
                disabled.component());
        assertEquals(cut.x(), disabled.x());
    }

    @Test
    void aCutKeepsEveryStyleLayerInOrderAndCopiesTheirSets() {
        AtomicBoolean available = new AtomicBoolean(true);
        EnumSet<TextFormattingStyle> innerStyles = EnumSet.of(TextFormattingStyle.RED);
        TextComponent label = new TextComponent.Styled(new TextComponent.Styled(TextComponent.literal("Configure shaders"), innerStyles), EnumSet.of(TextFormattingStyle.ITALIC));
        ControlElement<Boolean> element = element(option(available), label, NOTHING, DIM);
        TextComponent cut = render(element).label().component();
        assertEquals(new TextComponent.Styled(new TextComponent.Styled(TextComponent.literal("Confi..."), EnumSet.of(TextFormattingStyle.RED)),
                EnumSet.of(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE)), cut);
        assertNotSame(innerStyles, ((TextComponent.Styled) ((TextComponent.Styled) cut).inner()).styles());
        // The red-only inner layer goes while unavailable, the italic one stays
        available.set(false);
        assertEquals(new TextComponent.Styled(TextComponent.literal("Confi..."),
                EnumSet.of(TextFormattingStyle.ITALIC, TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH)), render(element).label().component());
        // Changing the caller's set afterwards does not reach the button's own copies
        innerStyles.add(TextFormattingStyle.GOLD);
        available.set(true);
        assertEquals(EnumSet.of(TextFormattingStyle.RED), ((TextComponent.Styled) ((TextComponent.Styled) render(element).label().component()).inner()).styles());
    }

    @Test
    void aCutIsKeptAcrossFramesAndRedoneOnlyWhenTheTextOrFullWidthChanges() {
        AtomicBoolean available = new AtomicBoolean(true);
        ControlElement<Boolean> element = element(option(available), TextComponent.translatable("impetus.test.long"), NOTHING, DIM);
        RecordingDraw draw = new RecordingDraw();
        draw.translations.put("impetus.test.long", "Configure shaders");
        element.render(draw, 0, 0, 0f);
        TextComponent enabled = draw.label().component();
        assertEquals(1, draw.cuts);
        for (int frame = 0; frame < 3; frame++) {
            draw.clear();
            element.render(draw, 0, 0, 0f);
            assertSame(enabled, draw.label().component());
        }
        // The disabled look was cut at the same time
        available.set(false);
        draw.clear();
        element.render(draw, 0, 0, 0f);
        TextComponent disabled = draw.label().component();
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertSame(disabled, draw.label().component());
        available.set(true);
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertSame(enabled, draw.label().component());
        assertEquals(1, draw.cuts);
        // Another context is measured in its own fonts, so it cuts once for itself, here to the same label
        RecordingDraw next = new RecordingDraw();
        next.translations.put("impetus.test.long", "Configure shaders");
        element.render(next, 0, 0, 0f);
        assertEquals(enabled, next.label().component());
        assertEquals(1, next.cuts);

        // A language change alters the text, even one that keeps the same full width (17 characters both)
        draw.translations.put("impetus.test.long", "Shader einrichten");
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertEquals("Shade...", draw.label().text());
        assertEquals(2, draw.cuts);
        TextComponent beforeFontChange = draw.label().component();
        // A font change alters the full width while the text stays the same
        draw.widths.put("Shader einrichten", 120);
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertEquals(3, draw.cuts);
        assertNotSame(beforeFontChange, draw.label().component());
        assertEquals("Shade...", draw.label().text());
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertEquals(3, draw.cuts);
        // Back to a translation that fits, which needs no cut at all
        draw.translations.put("impetus.test.long", "Open");
        draw.clear();
        element.render(draw, 0, 0, 0f);
        assertEquals("Open", draw.label().text());
        assertEquals(3, draw.cuts);
    }

    @Test
    void aCutDropsTheSpacesItEndsOnSoTheEllipsisFollowsTheLastWord() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        // Five characters fit: "Open " loses its space
        Text word = render(element(option, TextComponent.literal("Open the pack list"), NOTHING, DIM)).label();
        assertEquals("Open...", word.text());
        assertEquals(LIMIT_X - 42 - 18, word.x());
        assertEquals("Ab...", render(element(option, TextComponent.literal("Ab   cdefghijk"), NOTHING, DIM)).label().text());
        // A label of nothing but spaces cuts to the bare ellipsis
        assertEquals("...", render(element(option, TextComponent.literal(" ".repeat(12)), NOTHING, DIM)).label().text());
        // Spaces inside the cut stay
        assertEquals("A b c...", render(element(option, TextComponent.literal("A b c d e f g"), NOTHING, DIM)).label().text());
    }

    @Test
    void aCutThatTheUnderlineWidensByAFixedAmountIsCutOnceMoreShorterAndGreyedFromThatCut() {
        AtomicBoolean available = new AtomicBoolean(true);
        ControlElement<Boolean> element = tenInRoom48(available);
        Widening fonts = new Widening((plain, length) -> plain + 12);
        element.render(fonts, 0, 0, 0f);
        // "ABCDE..." fills the 48 plain and 60 underlined, so cut again into the 36 the overflow leaves
        Text label = fonts.label();
        assertEquals(TextComponent.literal("ABC...").withStyle(TextFormattingStyle.UNDERLINE), label.component());
        assertEquals(0, label.x());
        assertEquals(List.of(30, 18), fonts.asked);
        // The disabled look is the same second cut, greyed without cutting again
        available.set(false);
        fonts.clear();
        element.render(fonts, 0, 0, 0f);
        label = fonts.label();
        assertEquals(TextComponent.literal("ABC...").withStyle(TextFormattingStyle.UNDERLINE, TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), label.component());
        assertEquals(0, label.x());
        assertEquals(List.of(30, 18), fonts.asked);
    }

    @Test
    void aCutThatTheUnderlineWidensForEachCharacterIsCutOnceMoreShorterToFit() {
        Widening fonts = new Widening((plain, length) -> plain + length);
        tenInRoom48(new AtomicBoolean(true)).render(fonts, 0, 0, 0f);
        // Eight characters overflow by eight, the six of the second cut need 42
        Text label = fonts.label();
        assertEquals("ABC...", label.text());
        assertEquals(66 - 42 - 18, label.x());
        assertEquals(List.of(30, 22), fonts.asked);
    }

    @Test
    void anUnderlinedCutMeasuredNegativeIsLeftOutWithoutCuttingAgain() {
        Widening fonts = new Widening((plain, length) -> -1);
        tenInRoom48(new AtomicBoolean(true)).render(fonts, 0, 0, 0f);
        assertTrue(fonts.drewNoLabel());
        assertEquals(66 - 10, fonts.arrow().x());
        assertEquals(List.of(30), fonts.asked);
    }

    @Test
    void anUnderlineThatOverflowsEveryCutLeavesTheLabelOutAfterTwoCutsAndKeepsItOut() {
        AtomicBoolean available = new AtomicBoolean(true);
        ControlElement<Boolean> element = tenInRoom48(available);
        // One pixel over whatever is cut, so the second cut is only a pixel shorter and still overflows
        Widening fonts = new Widening((plain, length) -> 49);
        element.render(fonts, 0, 0, 0f);
        assertTrue(fonts.drewNoLabel());
        assertEquals(66 - 10, fonts.arrow().x());
        assertEquals(List.of(30, 29), fonts.asked);
        assertEquals(2, fonts.cuts);
        // Nor is it cut again on later frames, enabled or not
        available.set(false);
        fonts.clear();
        element.render(fonts, 0, 0, 0f);
        assertTrue(fonts.drewNoLabel());
        assertEquals(2, fonts.cuts);
    }

    @Test
    void aLeftClickInsidePlaysTheSoundThenRunsTheActionOncePerClick() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        List<Integer> soundsAtEachRun = new ArrayList<>();
        ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> soundsAtEachRun.add(ctx.clicks), DIM);
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        // The sound has already played when the action runs, so an action that closes the screen does not leave it trailing
        assertEquals(List.of(1), soundsAtEachRun);
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        assertTrue(element.mouseClicked(ctx, 11.5, 21.5, 0));
        assertEquals(List.of(1, 2, 3), soundsAtEachRun);
        assertEquals(3, ctx.clicks);
    }

    @Test
    void theHitAreaIncludesTheTopLeftEdgesAndExcludesTheBottomRightOnes() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), runs::incrementAndGet, DIM);
        double[][] inside = {{10, 20}, {209.999, 39.999}, {10, 39.999}, {209.999, 20}, {110, 30}};
        for (double[] point : inside) {
            assertTrue(element.isMouseOver(point[0], point[1]), () -> point[0] + "," + point[1]);
            assertTrue(element.mouseClicked(ctx, point[0], point[1], 0), () -> point[0] + "," + point[1]);
        }
        assertEquals(inside.length, runs.get());
        double[][] outside = {{9.999, 30}, {210, 30}, {110, 19.999}, {110, 40}, {210, 40}, {9, 19},
                {Double.NaN, 30}, {110, Double.NaN}, {Double.POSITIVE_INFINITY, 30}, {Double.NEGATIVE_INFINITY, 30},
                {110, Double.POSITIVE_INFINITY}, {110, Double.NEGATIVE_INFINITY}, {-110, -30}};
        for (double[] point : outside) {
            assertFalse(element.isMouseOver(point[0], point[1]), () -> point[0] + "," + point[1]);
            assertFalse(element.mouseClicked(ctx, point[0], point[1], 0), () -> point[0] + "," + point[1]);
        }
        assertEquals(inside.length, runs.get());
        assertEquals(inside.length, ctx.clicks);
    }

    @Test
    void onlyTheLeftButtonClicksAndOtherButtonsDoNotEvenAskForAvailability() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), runs::incrementAndGet, DIM);
        clearInvocations(option);
        for (int button : new int[]{1, 2, 3, 4, 7, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertFalse(element.mouseClicked(ctx, 110, 30, button), () -> "button " + button);
        }
        // Nor does a left click outside
        assertFalse(element.mouseClicked(ctx, 500, 30, 0));
        verify(option, never()).isAvailable();
        assertEquals(0, runs.get());
        assertEquals(0, ctx.clicks);
    }

    @Test
    void availabilityIsCheckedAtEachClickSoAnActionCanDisableItsOwnButton() {
        AtomicBoolean available = new AtomicBoolean(false);
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option(available), TextComponent.literal("Go"), () -> {
            runs.incrementAndGet();
            available.set(false);
        }, DIM);
        assertFalse(element.mouseClicked(ctx, 110, 30, 0));
        assertEquals(0, ctx.clicks);
        available.set(true);
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        // The action greyed the option out, so the next click on the same spot is ignored
        assertFalse(element.mouseClicked(ctx, 110, 30, 0));
        available.set(true);
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        assertEquals(2, runs.get());
        assertEquals(2, ctx.clicks);
    }

    @Test
    void anActionThatFailsIsLoggedWithItsOptionAndTheClickIsStillClaimed() {
        withLogger(logger -> {
            AtomicReference<Throwable> armed = new AtomicReference<>();
            AtomicInteger runs = new AtomicInteger();
            Option<Boolean> option = option(new AtomicBoolean(true));
            OptionIdentifier<Boolean> id = OptionIdentifier.create(MOD, "failing", Boolean.class);
            when(option.getId()).thenReturn(id);
            ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), () -> {
                runs.incrementAndGet();
                Throwable failure = armed.get();
                if (failure != null) {
                    throw ActionButtonControlTest.<RuntimeException>sneak(failure);
                }
            }, DIM);
            FakeInteractionContext ctx = new FakeInteractionContext();
            // Unchecked, checked (thrown past javac), plain Errors including linkage failures from a missing mod class, and a bare Throwable
            List<Throwable> failures = List.of(new IllegalStateException("broken"), new IOException("gone"), new AssertionError("no"),
                    new NoClassDefFoundError("missing/Mod"), new ExceptionInInitializerError("init"), new Throwable("odd"));
            for (Throwable failure : failures) {
                armed.set(failure);
                assertTrue(element.mouseClicked(ctx, 110, 30, 0), failure::toString);
                verify(logger).error(eq(FAILURE_LOG), eq("Name"), eq(id), same(failure));
            }
            assertEquals(failures.size(), ctx.clicks);
            assertFalse(Thread.currentThread().isInterrupted());
            // The button keeps working afterwards
            armed.set(null);
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            assertEquals(failures.size() + 1, runs.get());
            Mockito.verifyNoMoreInteractions(logger);
        });
    }

    @Test
    void aFailingActionOnAnOptionWithoutAnIdLogsItsNameAndANullId() {
        withLogger(logger -> {
            IllegalStateException failure = new IllegalStateException("broken");
            ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> {
                throw failure;
            }, DIM);
            assertTrue(element.mouseClicked(new FakeInteractionContext(), 110, 30, 0));
            verify(logger).error(eq(FAILURE_LOG), eq("Name"), isNull(), same(failure));
        });
    }

    @Test
    void anInterruptedActionLeavesTheThreadInterrupted() {
        withLogger(logger -> {
            InterruptedException failure = new InterruptedException("stop");
            ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> {
                throw ActionButtonControlTest.<RuntimeException>sneak(failure);
            }, DIM);
            try {
                assertTrue(element.mouseClicked(new FakeInteractionContext(), 110, 30, 0));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                // Clears the flag so the rest of the suite runs uninterrupted
                Thread.interrupted();
            }
            verify(logger).error(eq(FAILURE_LOG), any(), any(), same(failure));
        });
    }

    @Test
    void virtualMachineErrorsAreNotCaughtOrLogged() {
        withLogger(logger -> {
            AtomicReference<Throwable> armed = new AtomicReference<>();
            FakeInteractionContext ctx = new FakeInteractionContext();
            ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> {
                throw ActionButtonControlTest.<RuntimeException>sneak(armed.get());
            }, DIM);
            for (Throwable fatal : List.of(new StackOverflowError(), new OutOfMemoryError("heap"), new InternalError("vm"))) {
                armed.set(fatal);
                assertSame(fatal, assertThrowsExactly(fatal.getClass(), () -> element.mouseClicked(ctx, 110, 30, 0)));
            }
            // The sound had already played each time
            assertEquals(3, ctx.clicks);
            verifyNoInteractions(logger);
            assertEquals(List.of(), toastTitles());
        });
    }

    @Test
    void anActionCanClickItsOwnButtonAgain() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicReference<ControlElement<Boolean>> self = new AtomicReference<>();
        List<String> calls = new ArrayList<>();
        self.set(element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> {
            calls.add("run " + ctx.clicks);
            if (calls.size() == 1) {
                assertTrue(self.get().mouseClicked(ctx, 110, 30, 0));
            }
        }, DIM));
        assertTrue(self.get().mouseClicked(ctx, 110, 30, 0));
        assertEquals(List.of("run 1", "run 2"), calls);
        assertEquals(2, ctx.clicks);
    }

    @Test
    void elementsOfOneControlShareTheActionButEachKeepsItsOwnBounds() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        AtomicInteger runs = new AtomicInteger();
        ActionButtonControl control = new ActionButtonControl(option, runs::incrementAndGet);
        ControlElement<Boolean> top = control.createElement(new Dim2i(0, 0, 100, 20));
        ControlElement<Boolean> bottom = control.createElement(new Dim2i(0, 20, 100, 20));
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(top.mouseClicked(ctx, 50, 10, 0));
        assertFalse(bottom.mouseClicked(ctx, 50, 10, 0));
        assertTrue(bottom.mouseClicked(ctx, 50, 30, 0));
        assertFalse(top.mouseClicked(ctx, 50, 30, 0));
        assertEquals(2, runs.get());
    }

    @Test
    void releasesDragsAndScrollsAreNotClaimed() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), runs::incrementAndGet, DIM);
        element.setFocused(true);
        assertFalse(element.mouseReleased(ctx, 110, 30, 0));
        assertFalse(element.mouseDragged(ctx, 110, 30, 0, 5, 5));
        assertFalse(element.mouseScrolled(ctx, 110, 30, 0, 1));
        assertEquals(0, runs.get());
        assertEquals(0, ctx.clicks);
    }

    // Enter by either character or key code, keypad Enter, and Space by either
    private static final Object[][] PRESS_KEYS = {{'\r', 28}, {'\r', 0}, {'\n', 0}, {'\0', 28}, {'\0', 156}, {' ', 57}, {' ', 0}, {'\0', 57}};

    @Test
    void anUnfocusedButtonLeavesThePressKeysAloneWithoutAskingForAvailability() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), runs::incrementAndGet, DIM);
        clearInvocations(option);
        assertFalse(element.isFocused());
        for (Object[] key : PRESS_KEYS) {
            assertFalse(element.keyTyped(ctx, (char) key[0], (int) key[1]), () -> key[0] + "/" + key[1]);
        }
        // Focus taken away again is the same as never having had it
        element.setFocused(true);
        element.setFocused(false);
        assertFalse(element.keyTyped(ctx, '\r', 28));
        verify(option, never()).isAvailable();
        assertEquals(0, runs.get());
        assertEquals(0, ctx.clicks);
    }

    @Test
    void enterKeypadEnterAndSpacePressAFocusedButtonSoundFirstLikeALeftClick() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        List<Integer> soundsAtEachRun = new ArrayList<>();
        ControlElement<Boolean> element = element(option(new AtomicBoolean(true)), TextComponent.literal("Go"), () -> soundsAtEachRun.add(ctx.clicks), DIM);
        element.setFocused(true);
        assertTrue(element.isFocused());
        for (Object[] key : PRESS_KEYS) {
            assertTrue(element.keyTyped(ctx, (char) key[0], (int) key[1]), () -> key[0] + "/" + key[1]);
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), soundsAtEachRun);
        assertEquals(PRESS_KEYS.length, ctx.clicks);
        // Still pressed by the mouse as before, and the key presses did not move the hit area
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        assertEquals(PRESS_KEYS.length + 1, soundsAtEachRun.size());
    }

    @Test
    void otherKeysDoNotPressAFocusedButtonNorAskForAvailability() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), runs::incrementAndGet, DIM);
        element.setFocused(true);
        clearInvocations(option);
        // Letters, Tab (left to the container to move focus), Escape (left to the screen), the arrows, Backspace, nothing, and a GLFW Enter
        Object[][] keys = {{'a', 30}, {'\t', 15}, {'\0', 15}, {(char) 27, 1}, {'\0', 1}, {'\0', 203}, {'\0', 205}, {'\b', 14}, {'\0', 0}, {'\0', 257}};
        for (Object[] key : keys) {
            assertFalse(element.keyTyped(ctx, (char) key[0], (int) key[1]), () -> key[0] + "/" + key[1]);
        }
        verify(option, never()).isAvailable();
        assertEquals(0, runs.get());
        assertEquals(0, ctx.clicks);
    }

    @Test
    void availabilityIsCheckedAtEachKeyPressSoAnActionCanDisableItsOwnFocusedButton() {
        AtomicBoolean available = new AtomicBoolean(false);
        FakeInteractionContext ctx = new FakeInteractionContext();
        AtomicInteger runs = new AtomicInteger();
        ControlElement<Boolean> element = element(option(available), TextComponent.literal("Go"), () -> {
            runs.incrementAndGet();
            available.set(false);
        }, DIM);
        element.setFocused(true);
        assertFalse(element.keyTyped(ctx, ' ', 57));
        assertEquals(0, ctx.clicks);
        available.set(true);
        assertTrue(element.keyTyped(ctx, ' ', 57));
        // The action greyed the option out, so the next press is ignored and Tab would now pass the row over
        assertFalse(element.keyTyped(ctx, '\r', 28));
        assertFalse(element.canFocus());
        available.set(true);
        assertTrue(element.canFocus());
        assertTrue(element.keyTyped(ctx, '\r', 28));
        assertEquals(2, runs.get());
        assertEquals(2, ctx.clicks);
    }

    @Test
    void anActionThatFailsWhenPressedFromTheKeyboardIsLoggedAndTheKeyClaimed() {
        withLogger(logger -> {
            IllegalStateException failure = new IllegalStateException("broken");
            Option<Boolean> option = option(new AtomicBoolean(true));
            OptionIdentifier<Boolean> id = OptionIdentifier.create(MOD, "keyed", Boolean.class);
            when(option.getId()).thenReturn(id);
            ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), () -> {
                throw failure;
            }, DIM);
            FakeInteractionContext ctx = new FakeInteractionContext();
            element.setFocused(true);
            assertTrue(element.keyTyped(ctx, '\0', 156));
            assertEquals(1, ctx.clicks);
            verify(logger).error(eq(FAILURE_LOG), eq("Name"), eq(id), same(failure));
            assertEquals(List.of("Button failed: Name"), toastTitles());
        });
    }

    @Test
    void aFailingActionShowsAnErrorToastNamingTheOptionAsLastDrawn() {
        withLogger(logger -> {
            IllegalStateException failure = new IllegalStateException("broken");
            Option<Boolean> option = option(new AtomicBoolean(true));
            when(option.getName()).thenReturn(TextComponent.translatable("impetus.test.name"));
            ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), () -> {
                throw failure;
            }, DIM);
            FakeInteractionContext ctx = new FakeInteractionContext();
            // Before any frame there is no context to translate the name with, so its key stands in
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            verify(logger).error(eq(FAILURE_LOG), eq("impetus.test.name"), isNull(), same(failure));
            assertEquals(List.of("Button failed: impetus.test.name"), toastTitles());
            clearToasts();

            RecordingDraw draw = new RecordingDraw();
            draw.translations.put("impetus.test.name", "Shader packs");
            element.render(draw, 0, 0, 0f);
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            verify(logger).error(eq(FAILURE_LOG), eq("Shader packs"), isNull(), same(failure));
            List<ImpetusNotification> toasts = ImpetusNotifications.getVisible();
            assertEquals(1, toasts.size());
            assertEquals(ImpetusNotification.Level.ERROR, toasts.getFirst().level());
            assertEquals("Button failed: Shader packs", toasts.getFirst().title());
            assertEquals(List.of("java.lang.IllegalStateException: broken"), toasts.getFirst().lines());
            // The same failure again refreshes that toast rather than stacking another
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            assertEquals(1, ImpetusNotifications.getVisible().size());
        });
    }

    @Test
    void aToastShowsOnlyTheFirstLineOfTheFailureAndCopesWithoutAMessageOrName() {
        withLogger(logger -> {
            AtomicReference<Throwable> armed = new AtomicReference<>();
            Option<Boolean> option = option(new AtomicBoolean(true));
            ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), () -> {
                throw ActionButtonControlTest.<RuntimeException>sneak(armed.get());
            }, DIM);
            FakeInteractionContext ctx = new FakeInteractionContext();
            armed.set(new IllegalStateException("first\nsecond\r\nthird"));
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            assertEquals(List.of("java.lang.IllegalStateException: first"), ImpetusNotifications.getVisible().getLast().lines());
            armed.set(new IllegalStateException());
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            assertEquals(List.of("java.lang.IllegalStateException"), ImpetusNotifications.getVisible().getLast().lines());
            // An option with no name, which a broken mod could hand over, still reports
            when(option.getName()).thenReturn(null);
            Throwable failure = new IllegalArgumentException("x");
            armed.set(failure);
            assertTrue(element.mouseClicked(ctx, 110, 30, 0));
            verify(logger).error(eq(FAILURE_LOG), eq("null"), isNull(), same(failure));
            assertEquals("Button failed: null", ImpetusNotifications.getVisible().getLast().title());
        });
    }

    @Test
    void aFocusedButtonLooksHoveredAndIsOutlinedInItsModsAccentUnderTheText() {
        Option<Boolean> option = option(new AtomicBoolean(true));
        when(option.getId()).thenReturn(OptionIdentifier.create(MOD, "focus", Boolean.class));
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        String accent = Integer.toHexString(DefaultColors.getModAccentColor(MOD));
        String hovered = Integer.toHexString(DefaultColors.BACKGROUND_HOVERED);
        RecordingDraw idle = render(element);
        assertEquals("fill(10,20,210,40," + Integer.toHexString(DefaultColors.BACKGROUND_DEFAULT) + ")", idle.calls.getFirst());
        assertEquals(1, idle.calls.stream().filter(c -> c.startsWith("fill")).count());

        element.setFocused(true);
        RecordingDraw focused = render(element);
        // The cursor is far away, yet the row is drawn hovered, then a one pixel outline on all four edges, then the name, label and arrow over it
        assertEquals(List.of("fill(10,20,210,40," + hovered + ")",
                "fill(10,20,210,21," + accent + ")", "fill(10,39,210,40," + accent + ")",
                "fill(10,20,11,40," + accent + ")", "fill(209,20,210,40," + accent + ")"), focused.calls.subList(0, 5));
        assertEquals(List.of("Name", "Go", ">"), focused.texts.stream().map(Text::text).toList());
    }

    @Test
    void tabReachesAButtonInAFrameAndEnterPressesItWhileTheFocusedSearchBarKeepsSpace() {
        FakeInteractionContext ctx = new FakeInteractionContext();
        List<String> queries = new ArrayList<>();
        List<String> presses = new ArrayList<>();
        AtomicBoolean secondAvailable = new AtomicBoolean(true);
        SearchBarWidget search = new SearchBarWidget(new Dim2i(0, 0, 200, 18), "", false, queries::add);
        ControlElement<Boolean> first = element(option(new AtomicBoolean(true)), TextComponent.literal("One"), () -> presses.add("one"), new Dim2i(0, 20, 200, 20));
        ControlElement<Boolean> second = element(option(secondAvailable), TextComponent.literal("Two"), () -> presses.add("two"), new Dim2i(0, 40, 200, 20));
        BasicFrame frame = BasicFrame.createBuilder()
                .setDimension(new Dim2i(0, 0, 200, 60))
                .addChild(d -> search)
                .addChild(d -> first)
                .addChild(d -> second)
                .build();

        // Tab starts at the search bar, where Space and Enter are typing, not presses
        assertTrue(frame.dispatchKeyTyped(ctx, '\t', 15));
        assertTrue(search.isFocused());
        assertTrue(frame.dispatchKeyTyped(ctx, ' ', 57));
        assertTrue(frame.dispatchKeyTyped(ctx, '\r', 28));
        assertEquals(List.of(" "), queries);
        assertEquals(List.of(), presses);

        // Tab leaves the search bar for the first row, and Space presses it
        assertTrue(frame.dispatchKeyTyped(ctx, '\t', 15));
        assertFalse(search.isFocused());
        assertTrue(first.isFocused());
        assertTrue(frame.dispatchKeyTyped(ctx, ' ', 57));
        // Typing a letter at a focused button reaches nobody, so the screen gets it
        assertFalse(frame.dispatchKeyTyped(ctx, 'a', 30));
        assertTrue(frame.dispatchKeyTyped(ctx, '\0', 15));
        assertTrue(second.isFocused());
        assertTrue(frame.dispatchKeyTyped(ctx, '\0', 156));
        assertEquals(List.of("one", "two"), presses);

        // Shift+Tab goes back; a row whose option is unavailable is skipped, and Tab wraps round past the end
        ctx.held.add(InteractionContext.SpecialKey.SHIFT);
        assertTrue(frame.dispatchKeyTyped(ctx, '\t', 15));
        assertTrue(first.isFocused());
        ctx.held.clear();
        secondAvailable.set(false);
        assertTrue(frame.dispatchKeyTyped(ctx, '\t', 15));
        assertTrue(search.isFocused());
        assertFalse(second.isFocused());

        // A click on the second row focuses it and leaves the search bar, so Space presses the row and types nothing
        secondAvailable.set(true);
        assertTrue(frame.mouseClicked(ctx, 100, 50, 0));
        assertTrue(second.isFocused());
        assertFalse(search.isFocused());
        assertTrue(frame.dispatchKeyTyped(ctx, ' ', 57));
        assertEquals(List.of("one", "two", "two", "two"), presses);
        assertEquals(List.of(" "), queries);
        // And a click back in the search bar takes Space away from the row
        assertTrue(frame.mouseClicked(ctx, 100, 5, 0));
        assertFalse(second.isFocused());
        assertTrue(frame.dispatchKeyTyped(ctx, ' ', 57));
        assertEquals(List.of(" ", "  "), queries);
        assertEquals(4, presses.size());
    }

    @Test
    void theOptionsValueIsNeverReadOrWritten() {
        AtomicBoolean available = new AtomicBoolean(true);
        Option<Boolean> option = option(available);
        ControlElement<Boolean> element = element(option, TextComponent.literal("Go"), NOTHING, DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        render(element);
        element.mouseClicked(ctx, 110, 30, 0);
        available.set(false);
        render(element);
        element.mouseClicked(ctx, 110, 30, 0);
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        verify(option, never()).applyChanges();
        verify(option, never()).reset();
    }

    @Test
    void aRealOptionBuiltLikeUmbrasPagesRunsItsActionOnlyWhileEnabled() {
        Object state = new Object();
        OptionStorage<Object> storage = () -> state;
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        OptionImpl<Object, Boolean> option = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(OptionIdentifier.create(MOD, "real", Boolean.class))
                .setName(TextComponent.literal("Shader packs"))
                .setTooltip(TextComponent.literal("Opens the pack list"))
                .setControl(o -> new ActionButtonControl(o, runs::incrementAndGet))
                .setBinding((s, value) -> writes.incrementAndGet(), s -> false)
                .setEnabledPredicate(enabled::get)
                .build();
        ControlElement<Boolean> element = option.getControl().createElement(DIM);
        assertSame(option, element.getOption());
        RecordingDraw draw = render(element);
        assertEquals("Shader packs", draw.texts.getFirst().text());
        assertEquals("Open", draw.label().text());
        assertEquals(DefaultColors.getModAccentColor(MOD), draw.label().color());
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(element.mouseClicked(ctx, 110, 30, 0));
        enabled.set(false);
        assertFalse(element.mouseClicked(ctx, 110, 30, 0));
        assertEquals(DefaultColors.TEXT_DISABLED, render(element).label().color());
        assertEquals(1, runs.get());
        // Nothing pending, so nothing is written back through the placeholder binding
        assertFalse(option.hasChanged());
        option.applyChanges();
        assertEquals(0, writes.get());
    }

    // Swaps the class's static final logger for a mock while the body runs
    private static void withLogger(Consumer<Logger> body) {
        // Reading it first runs the class initialiser, which would otherwise overwrite the swap
        Logger original = Statics.get(ActionButtonControl.class, "LOGGER");
        Logger mock = Mockito.mock(Logger.class);
        Statics.set(ActionButtonControl.class, "LOGGER", mock);
        clearToasts();
        try {
            body.accept(mock);
        } finally {
            Statics.set(ActionButtonControl.class, "LOGGER", original);
            clearToasts();
        }
    }

    // Titles of the error toasts this class raised, oldest first
    private static List<String> toastTitles() {
        return ImpetusNotifications.getVisible().stream().map(ImpetusNotification::title).filter(t -> t.startsWith("Button failed: ")).toList();
    }

    private static void clearToasts() {
        Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneak(Throwable failure) throws E {
        throw (E) failure;
    }

    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(NullPointerException.class, call).getMessage());
    }
}
