package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotification;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaceholderControlElementTest {
    private static final Dim2i DIM = new Dim2i(10, 20, 400, 18);
    private static final String KEY = "impetus.options.control_failed";
    private static final String FAILURE_LOG = "Control of option {} failed, showing a placeholder row";
    private static final String OPTION_LOG = "Option {} failed while {}";
    // The label's text in the fake font, 30 characters at six pixels
    private static final int LABEL_WIDTH = 180;

    // A mock option with a plain name, available and unchanged
    private static Option<Boolean> option(String name) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class);
        when(option.getName()).thenReturn(TextComponent.literal(name));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A mock option whose name is the given component
    private static Option<Boolean> named(TextComponent name) {
        Option<Boolean> option = option("Unused");
        when(option.getName()).thenReturn(name);
        return option;
    }

    private static PlaceholderControlElement<Boolean> placeholder(Option<Boolean> option, Dim2i dim) {
        return new PlaceholderControlElement<>(option, dim, "described");
    }

    // The strings one frame of the row draws
    private static List<String> drawn(ControlElement<?> row) {
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, -1, -1, 0f);
        return draw.strings;
    }

    // A real option object, not a mock, so its identity, equality and reachability are its own
    private static class Plain implements Option<Boolean> {
        final String name;
        int controlReads;

        Plain(String name) {
            this.name = name;
        }

        @Override
        public TextComponent getName() {
            return TextComponent.literal(this.name);
        }

        @Override
        public TextComponent getTooltip() {
            return TextComponent.literal("");
        }

        @Override
        public OptionImpact getImpact() {
            return null;
        }

        @Override
        public Control<Boolean> getControl() {
            this.controlReads++;
            throw new IllegalStateException("no control");
        }

        @Override
        public Boolean getValue() {
            return false;
        }

        @Override
        public void setValue(Boolean value) {
        }

        @Override
        public void reset() {
        }

        @Override
        public OptionStorage<?> getStorage() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean hasChanged() {
            return false;
        }

        @Override
        public void applyChanges() {
        }

        @Override
        public Collection<OptionFlag> getFlags() {
            return List.of();
        }
    }

    // Equal to every other one, as an option comparing by its settings would be
    private static final class Alike extends Plain {
        Alike(String name) {
            super(name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Alike;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    // An option whose own equality is broken along with its control
    private static final class Hostile extends Plain {
        Hostile(String name) {
            super(name);
        }

        @Override
        public boolean equals(Object other) {
            throw new IllegalStateException("equals");
        }

        @Override
        public int hashCode() {
            throw new IllegalStateException("hashCode");
        }
    }

    // A font source of the given pixels per character and text version
    private static class Sized extends FakeDrawContext {
        int perChar;
        long version;

        Sized(int perChar) {
            this.perChar = perChar;
        }

        @Override
        public int getStringWidth(TextComponent component) {
            return this.extractString(component).length() * this.perChar;
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            return str.substring(0, Math.min(str.length(), Math.max(0, maxWidth / this.perChar)));
        }

        @Override
        public long textVersion() {
            return this.version;
        }
    }

    // Counts the label's measures and the reads of the given name outside drawing, since the fake measures inside its own drawString
    private static final class Counting extends Sized {
        final TextComponent name;
        int labelMeasures;
        int nameReads;
        private boolean drawing;

        Counting(int perChar, TextComponent name) {
            super(perChar);
            this.name = name;
        }

        @Override
        public int getStringWidth(TextComponent component) {
            if (!this.drawing && component.equals(TextComponent.translatable(KEY))) {
                this.labelMeasures++;
            }
            return super.getStringWidth(component);
        }

        @Override
        public String extractString(TextComponent component) {
            if (!this.drawing && component == this.name) {
                this.nameReads++;
            }
            return super.extractString(component);
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
    }

    // An argument that counts how often it is turned to text
    private static final class Counted {
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public String toString() {
            this.reads.incrementAndGet();
            return "counted";
        }
    }

    // A failure whose own text throws what it is given
    private static final class Unprintable extends RuntimeException {
        private final Throwable thrown;

        Unprintable(Throwable thrown) {
            this.thrown = thrown;
        }

        @Override
        public String toString() {
            throw PlaceholderControlElementTest.<RuntimeException>sneaky(this.thrown);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable thrown) throws E {
        throw (E) thrown;
    }

    // Swaps the placeholder's logger for a mock while the body runs and clears the toasts around it
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

    // The toasts placeholders raised, oldest first
    private static List<ImpetusNotification> toasts() {
        return ImpetusNotifications.getVisible().stream().filter(t -> t.title().startsWith("Option failed: ")).toList();
    }

    // The single line of the newest placeholder toast
    private static String toastLine() {
        return toasts().getLast().lines().getFirst();
    }

    @Test
    void theNameIsGreyedAndTheLabelRightAlignedInRedOnTheRowsCentre() {
        FakeDrawContext draw = new FakeDrawContext();
        placeholder(option("Broken"), DIM).render(draw, -1, -1, 0f);
        assertEquals(List.of("Broken", KEY), draw.strings);
        // The row's right edge, less the label and its inset, on the row's centre less four
        assertTrue(draw.drew("text(" + KEY + "," + (10 + 400 - LABEL_WIDTH - 6) + ",25,ffff5555,false)"), draw.calls::toString);
        assertTrue(draw.drew("text(Broken,16,25,"), draw.calls::toString);
    }

    @Test
    void theColumnIsTheLabelAndBothInsetsHeldToTheRow() {
        FakeDrawContext draw = new FakeDrawContext();
        assertEquals(LABEL_WIDTH + 12, placeholder(option("Column"), DIM).measureColumnWidth(draw));
        assertEquals(100, placeholder(option("Column"), DIM.withWidth(100)).measureColumnWidth(draw));
        assertEquals(0, placeholder(option("Column"), DIM.withWidth(0)).measureColumnWidth(draw));
        assertEquals(0, placeholder(option("Column"), DIM.withWidth(-50)).measureColumnWidth(draw));
        // A width that would wrap past the insets, and one below zero
        assertEquals(400, placeholder(option("Column"), DIM).measureColumnWidth(new Sized(1) {
            @Override
            public int getStringWidth(TextComponent component) {
                return Integer.MAX_VALUE;
            }
        }));
        assertEquals(0, placeholder(option("Column"), DIM).measureColumnWidth(new Sized(1) {
            @Override
            public int getStringWidth(TextComponent component) {
                return Integer.MIN_VALUE;
            }
        }));
    }

    @Test
    void theLabelIsDrawnOnlyWhenItFitsInsideTheRowsRightInset() {
        // A blank name leaves the whole row to the label
        Option<Boolean> blank = option("");
        FakeDrawContext exact = new FakeDrawContext();
        placeholder(blank, new Dim2i(0, 0, LABEL_WIDTH + 6, 18)).render(exact, -1, -1, 0f);
        assertTrue(exact.drew("text(" + KEY + ",0,5,"), exact.calls::toString);
        assertEquals(List.of(), drawn(placeholder(blank, new Dim2i(0, 0, LABEL_WIDTH + 5, 18))));
        // A font source that measures the label at a huge or negative width draws no label, with no wrapped position
        for (int width : new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, -1}) {
            FakeDrawContext odd = new FakeDrawContext() {
                @Override
                public int getStringWidth(TextComponent component) {
                    return component.equals(TextComponent.translatable(KEY)) ? width : super.getStringWidth(component);
                }
            };
            placeholder(option("Odd"), DIM).render(odd, -1, -1, 0f);
            assertFalse(odd.strings.contains(KEY), "width " + width);
        }
    }

    @Test
    void aRowWithNoAreaOrNoRoomForALineOfTextDrawsNoLabel() {
        for (Dim2i dim : new Dim2i[]{DIM.withWidth(0), DIM.withHeight(0), DIM.withWidth(-1), new Dim2i(1 << 30, 0, 400, 18)}) {
            FakeDrawContext draw = new FakeDrawContext();
            placeholder(option("Flat"), dim).render(draw, -1, -1, 0f);
            assertEquals(List.of(), draw.calls, dim::toString);
        }
        // Eight pixels cannot hold the nine of a line raised four above the centre: the background only
        FakeDrawContext low = new FakeDrawContext();
        placeholder(option("Low"), DIM.withHeight(8)).render(low, -1, -1, 0f);
        assertEquals(List.of(), low.strings);
        assertEquals(1, low.calls.size(), low.calls::toString);
    }

    @Test
    void aNewFontSourceMeasuresTheLabelAgainSoItNeverRunsOffTheRowOrOverTheName() {
        PlaceholderControlElement<Boolean> row = placeholder(option("Broken"), new Dim2i(0, 0, 400, 18));
        FakeDrawContext first = new FakeDrawContext();
        row.render(first, -1, -1, 0f);
        assertTrue(first.drew("text(" + KEY + ",214,"), first.calls::toString);
        // Twice as wide: 360 for the label would start at 34, over the 72 pixel name that ends at 84
        Sized wide = new Sized(12);
        row.render(wide, -1, -1, 0f);
        assertEquals(List.of("Broken"), wide.strings);
        // Half as wide: the 90 pixel label moves right to stay against the inset
        Sized narrow = new Sized(3);
        row.render(narrow, -1, -1, 0f);
        assertEquals(List.of("Broken", KEY), narrow.strings);
        assertTrue(narrow.drew("text(" + KEY + ",304,"), narrow.calls::toString);
    }

    @Test
    void aNewTextVersionOfTheSameFontSourceMeasuresTheLabelAgain() {
        PlaceholderControlElement<Boolean> row = placeholder(option("Broken"), new Dim2i(0, 0, 400, 18));
        Sized fonts = new Sized(6);
        row.render(fonts, -1, -1, 0f);
        assertTrue(fonts.drew("text(" + KEY + ",214,"), fonts.calls::toString);
        fonts.perChar = 3;
        fonts.version = 1;
        fonts.calls.clear();
        row.render(fonts, -1, -1, 0f);
        assertTrue(fonts.drew("text(" + KEY + ",304,"), fonts.calls::toString);
    }

    @Test
    void theLabelAndNameAreMeasuredOncePerFontSourceRatherThanEveryFrame() {
        PlaceholderControlElement<Boolean> row = placeholder(option("Broken"), DIM);
        Counting fonts = new Counting(6, row.getDisplayName());
        row.render(fonts, -1, -1, 0f);
        // The column's measure gives the label its place too
        assertEquals(1, fonts.labelMeasures);
        assertEquals(1, fonts.nameReads);
        for (int frame = 0; frame < 5; frame++) {
            row.render(fonts, 50, 25, 0f);
        }
        assertEquals(1, fonts.labelMeasures);
        assertEquals(1, fonts.nameReads);
        // A new font source measures the label once more, the column kept as every row keeps it
        Counting other = new Counting(6, row.getDisplayName());
        row.render(other, -1, -1, 0f);
        row.render(other, -1, -1, 0f);
        assertEquals(1, other.labelMeasures);
        assertEquals(1, other.nameReads);
        // A new text version of the same source measures the column again, which the label reuses
        other.version = 1;
        row.render(other, -1, -1, 0f);
        assertEquals(2, other.labelMeasures);
    }

    @Test
    void aColumnMeasuredOutsideAFrameIsNeverTakenForTheLabelsWidth() {
        PlaceholderControlElement<Boolean> row = placeholder(option("Broken"), new Dim2i(0, 0, 400, 18));
        row.render(new FakeDrawContext(), -1, -1, 0f);
        // Measured in a font twice as wide by someone other than the row's own draw
        assertEquals(372, row.measureColumnWidth(new Sized(12)));
        Sized narrow = new Sized(3);
        row.render(narrow, -1, -1, 0f);
        assertTrue(narrow.drew("text(" + KEY + ",304,"), narrow.calls::toString);
        // And a draw that throws partway leaves nothing behind for the next frame either
        PlaceholderControlElement<Boolean> failed = placeholder(option("Broken"), new Dim2i(0, 0, 400, 18));
        Sized throwing = new Sized(12) {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int color) {
                throw new IllegalStateException("fill");
            }
        };
        assertThrows(IllegalStateException.class, () -> failed.render(throwing, -1, -1, 0f));
        Sized after = new Sized(3);
        failed.render(after, -1, -1, 0f);
        assertTrue(after.drew("text(" + KEY + ",304,"), after.calls::toString);
    }

    @Test
    void aPlaceholderNeverTakesFocusSoItsOutlineNeverAsksTheBrokenOption() {
        Option<Boolean> option = option("Focused");
        when(option.getId()).thenThrow(new IllegalStateException("no id"));
        PlaceholderControlElement<Boolean> row = placeholder(option, DIM);
        row.setFocused(true);
        assertFalse(row.isFocused());
        assertFalse(row.canFocus());
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, -1, -1, 0f);
        assertEquals(List.of("Focused", KEY), draw.strings);
        // The background alone, no outline
        assertEquals(1, draw.calls.stream().filter(call -> call.startsWith("fill(")).count(), draw.calls::toString);
        row.setFocused(false);
        assertFalse(row.isFocused());
    }

    @Test
    void drawingHoveringAndMeasuringNeverCallIntoTheOption() {
        Option<Boolean> option = option("Quiet");
        PlaceholderControlElement<Boolean> row = placeholder(option, DIM);
        clearInvocations(option);
        row.setFocused(true);
        for (int frame = 0; frame < 3; frame++) {
            row.render(new FakeDrawContext(), 50, 25, 0f);
            row.render(new Sized(3), -1, -1, 0f);
        }
        assertTrue(row.isMouseOver(50, 25));
        assertFalse(row.canFocus());
        assertFalse(row.isOptionAvailable());
        row.measureColumnWidth(new FakeDrawContext());
        row.getDisplayName();
        verifyNoInteractions(option);
        // Unavailable whatever the option says
        when(option.isAvailable()).thenReturn(true);
        assertFalse(row.isOptionAvailable());
    }

    @Test
    void theNameIsReadOnceAndKeptAsTheSameComponent() {
        Option<Boolean> option = option("Once");
        PlaceholderControlElement<Boolean> row = placeholder(option, DIM);
        verify(option, times(1)).getName();
        TextComponent name = row.getDisplayName();
        assertEquals(TextComponent.literal("Once"), name);
        assertSame(name, row.getDisplayName());
    }

    @Test
    void aWellFormedNameIsCopiedPartForPartWithItsArgumentsAsText() {
        EnumSet<TextFormattingStyle> styles = EnumSet.of(TextFormattingStyle.UNDERLINE);
        TextComponent inner = TextComponent.translatable("impetus.test.name", 5, TextComponent.literal("x"), null);
        TextComponent name = new TextComponent.Styled(inner, styles);
        PlaceholderControlElement<Boolean> row = placeholder(named(name), DIM);
        TextComponent copy = row.getDisplayName();
        assertEquals(new TextComponent.Styled(new TextComponent.Translatable(List.of("impetus.test.name"), List.of("5", TextComponent.literal("x"), "null")), EnumSet.of(TextFormattingStyle.UNDERLINE)), copy);
        assertNotSame(name, copy);
        assertEquals(List.of("impetus.test.name[5, x, null]", KEY), drawn(row));
        // A name nested 200 deep is still the option's own
        TextComponent deep = TextComponent.literal("Deep");
        for (int i = 0; i < 200; i++) {
            deep = new TextComponent.Styled(deep, EnumSet.of(TextFormattingStyle.ITALIC));
        }
        assertEquals("Deep", drawn(placeholder(named(deep), DIM)).getFirst());
    }

    @Test
    void laterChangesToTheOptionsNamePartsNeverReachTheRow() {
        List<Object> args = new ArrayList<>(List.of("before"));
        EnumSet<TextFormattingStyle> styles = EnumSet.of(TextFormattingStyle.UNDERLINE);
        TextComponent name = new TextComponent.Styled(new TextComponent.Translatable(new ArrayList<>(List.of("impetus.test.key")), args), styles);
        PlaceholderControlElement<Boolean> row = placeholder(named(name), DIM);
        assertEquals("impetus.test.key[before]", drawn(row).getFirst());
        // The option rewrites its argument, adds a style, and later adds an argument that cannot be turned to text
        args.set(0, "after");
        styles.add(TextFormattingStyle.ITALIC);
        args.add(new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("unprintable");
            }
        });
        assertEquals(List.of("impetus.test.key[before]", KEY), drawn(row));
        TextComponent.Styled kept = assertInstanceOf(TextComponent.Styled.class, row.getDisplayName());
        assertEquals(EnumSet.of(TextFormattingStyle.UNDERLINE), kept.styles());
    }

    @Test
    void anArgumentIsTurnedToTextOnceSoItsOwnCodeNeverRunsWhileDrawing() {
        Counted counted = new Counted();
        PlaceholderControlElement<Boolean> row = placeholder(named(TextComponent.translatable("impetus.test.key", counted)), DIM);
        assertEquals(1, counted.reads.get());
        for (int frame = 0; frame < 3; frame++) {
            assertEquals("impetus.test.key[counted]", drawn(row).getFirst());
        }
        row.render(new Sized(3), -1, -1, 0f);
        assertEquals(1, counted.reads.get());
    }

    @Test
    void aNameOfAKindTheScreenCannotDrawShowsTheDescription() {
        TextComponent foreign = new TextComponent() {
            @Override
            public String toString() {
                throw new IllegalStateException("foreign");
            }
        };
        assertEquals(List.of("described", KEY), drawn(placeholder(named(foreign), DIM)));
        // Inside a style, or as an argument
        assertEquals("described", drawn(placeholder(named(foreign.withStyle(TextFormattingStyle.UNDERLINE)), DIM)).getFirst());
        assertEquals("described", drawn(placeholder(named(TextComponent.translatable("impetus.test.key", foreign)), DIM)).getFirst());
    }

    @Test
    void aTranslationWithNoKeysOrUnreadablePartsShowsTheDescription() {
        List<TextComponent> broken = List.of(
                new TextComponent.Translatable(List.of(), List.of()),
                new TextComponent.Translatable(Arrays.asList("impetus.test.key", null), List.of()),
                new TextComponent.Translatable(null, List.of()),
                new TextComponent.Translatable(List.of("impetus.test.key"), null),
                new TextComponent.Styled(TextComponent.literal("Styled"), null),
                TextComponent.literal(null),
                TextComponent.translatable("impetus.test.key", new Object() {
                    @Override
                    public String toString() {
                        throw new IllegalStateException("unprintable");
                    }
                }));
        for (int i = 0; i < broken.size(); i++) {
            assertEquals(List.of("described", KEY), drawn(placeholder(named(broken.get(i)), DIM)), "name " + i);
        }
    }

    @Test
    void aCyclicOrTooDeeplyNestedNameShowsTheDescription() {
        List<Object> args = new ArrayList<>();
        TextComponent cyclic = new TextComponent.Translatable(List.of("impetus.test.key"), args);
        args.add(cyclic);
        assertEquals(List.of("described", KEY), drawn(placeholder(named(cyclic), DIM)));
        TextComponent deep = TextComponent.literal("Deep");
        for (int i = 0; i < 300; i++) {
            deep = new TextComponent.Styled(deep, EnumSet.of(TextFormattingStyle.ITALIC));
        }
        assertEquals("described", drawn(placeholder(named(deep), DIM)).getFirst());
    }

    @Test
    void aVirtualMachineErrorWhileCopyingTheNameIsNotHeldButAnInterruptionIsKept() {
        Option<Boolean> overflowing = named(TextComponent.translatable("impetus.test.key", new Object() {
            @Override
            public String toString() {
                throw new StackOverflowError();
            }
        }));
        assertThrows(StackOverflowError.class, () -> placeholder(overflowing, DIM));
        Option<Boolean> interrupted = named(TextComponent.translatable("impetus.test.key", new Object() {
            @Override
            public String toString() {
                throw PlaceholderControlElementTest.<RuntimeException>sneaky(new InterruptedException("named"));
            }
        }));
        try {
            assertEquals("described", drawn(placeholder(interrupted, DIM)).getFirst());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void aMissingOptionBoundsOrDescriptionIsRefused() {
        NullPointerException description = assertThrows(NullPointerException.class, () -> new PlaceholderControlElement<>(option("Named"), DIM, null));
        assertEquals("Description must not be null", description.getMessage());
        assertThrows(NullPointerException.class, () -> new PlaceholderControlElement<>(null, DIM, "described"));
        assertThrows(NullPointerException.class, () -> new PlaceholderControlElement<>(option("Named"), null, "described"));
    }

    @Test
    void anOptionIsMarkedByIdentitySoAnEqualOptionIsNotTakenForIt() {
        withLogger(logger -> {
            Alike first = new Alike("First");
            Alike second = new Alike("Second");
            assertEquals(first, second);
            PlaceholderControlElement.report(first, "First", new IllegalStateException("first"));
            assertTrue(PlaceholderControlElement.isBroken(first));
            assertFalse(PlaceholderControlElement.isBroken(second));
            PlaceholderControlElement.report(second, "Second", new IllegalStateException("second"));
            verify(logger).error(eq(FAILURE_LOG), eq("First"), any(Throwable.class));
            verify(logger).error(eq(FAILURE_LOG), eq("Second"), any(Throwable.class));
            assertEquals(2, toasts().size());
        });
    }

    @Test
    void anOptionWhoseOwnEqualityThrowsIsStillMarkedReportedOnceAndShownAsAPlaceholder() {
        withLogger(logger -> {
            Hostile option = new Hostile("Hostile");
            assertFalse(PlaceholderControlElement.isBroken(option));
            ControlElement<Boolean> row = Control.createElementOrPlaceholder(option, DIM);
            assertInstanceOf(PlaceholderControlElement.class, row);
            assertTrue(PlaceholderControlElement.isBroken(option));
            assertEquals(1, option.controlReads);
            // The next rebuild makes the placeholder without asking the control again
            assertInstanceOf(PlaceholderControlElement.class, Control.createElementOrPlaceholder(option, DIM));
            assertEquals(1, option.controlReads);
            Control.reportBroken(option, "applying", new IllegalStateException("again"));
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Hostile"), any(Throwable.class));
            verify(logger, Mockito.never()).error(eq(OPTION_LOG), any(), any(), any(Throwable.class));
        });
    }

    @Test
    void theTwoReportsShareOneMarkAndEachLogsItsOwnMessage() {
        withLogger(logger -> {
            Option<Boolean> option = option("Shared");
            IllegalStateException failure = new IllegalStateException("scan");
            PlaceholderControlElement.reportOption(option, "Shared", "scanning", failure);
            verify(logger).error(OPTION_LOG, "Shared", "scanning", failure);
            assertEquals(List.of("java.lang.IllegalStateException: scan"), toasts().getFirst().lines());
            assertEquals("Option failed: Shared", toasts().getFirst().title());
            PlaceholderControlElement.report(option, "Shared", new IllegalStateException("row"));
            PlaceholderControlElement.reportOption(option, "Shared", "scanning", failure);
            verify(logger, Mockito.never()).error(eq(FAILURE_LOG), any(), any(Throwable.class));
            verify(logger, times(1)).error(eq(OPTION_LOG), any(), any(), any(Throwable.class));
            assertEquals(1, toasts().size());
            // A row failure first marks it for the option reporter too
            Option<Boolean> other = option("Other");
            IllegalStateException drawn = new IllegalStateException("drawn");
            PlaceholderControlElement.report(other, "Other", drawn);
            verify(logger).error(FAILURE_LOG, "Other", drawn);
            PlaceholderControlElement.reportOption(other, "Other", "scanning", failure);
            verify(logger, times(1)).error(eq(OPTION_LOG), any(), any(), any(Throwable.class));
        });
    }

    @Test
    void aMissingOptionIsRefusedRatherThanMarkedForever() {
        withLogger(logger -> {
            assertThrows(NullPointerException.class, () -> PlaceholderControlElement.isBroken(null));
            assertThrows(NullPointerException.class, () -> PlaceholderControlElement.report(null, "None", new IllegalStateException("none")));
            assertThrows(NullPointerException.class, () -> PlaceholderControlElement.reportOption(null, "None", "scanning", new IllegalStateException("none")));
            verifyNoInteractions(logger);
            assertEquals(List.of(), toasts());
        });
    }

    @Test
    void manyThreadsReportingOneOptionReportItOnce() throws InterruptedException {
        Logger original = Statics.get(PlaceholderControlElement.class, "LOGGER");
        Logger logger = Mockito.mock(Logger.class);
        Statics.set(PlaceholderControlElement.class, "LOGGER", logger);
        try {
            Option<Boolean> option = option("Raced");
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    PlaceholderControlElement.report(option, "Raced", new IllegalStateException("raced"));
                });
                thread.start();
                threads.add(thread);
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
            verify(logger, times(1)).error(eq(FAILURE_LOG), eq("Raced"), any(Throwable.class));
            assertTrue(PlaceholderControlElement.isBroken(option));
        } finally {
            Statics.set(PlaceholderControlElement.class, "LOGGER", original);
            clearToasts();
        }
    }

    @Test
    void aFailureWhoseOwnTextThrowsIsShownByItsClass() {
        withLogger(logger -> {
            Unprintable failure = new Unprintable(new IllegalStateException("text"));
            PlaceholderControlElement.report(option("Unprintable"), "Unprintable", failure);
            verify(logger).error(eq(FAILURE_LOG), eq("Unprintable"), same(failure));
            assertEquals(Unprintable.class.getName(), toastLine());
            PlaceholderControlElement.reportOption(option("Unprintable option"), "Unprintable option", "scanning", failure);
            assertEquals(Unprintable.class.getName(), toastLine());
            // An interruption keeps its flag, and a virtual machine error is not held
            Unprintable interrupted = new Unprintable(new InterruptedException("text"));
            try {
                PlaceholderControlElement.report(option("Interrupted"), "Interrupted", interrupted);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertEquals(Unprintable.class.getName(), toastLine());
            Option<Boolean> overflowing = option("Overflowing");
            assertThrows(StackOverflowError.class, () -> PlaceholderControlElement.report(overflowing, "Overflowing", new Unprintable(new StackOverflowError())));
            // Marked before the text was asked for, so it is not reported a second time
            assertTrue(PlaceholderControlElement.isBroken(overflowing));
        });
    }

    @Test
    void theToastShowsOnlyTheFirstLineOfTheFailureAndTheName() {
        withLogger(logger -> {
            String[][] cases = {{"first\nsecond", "first"}, {"first\r\nsecond", "first"}, {"first\rsecond", "first"}, {"\nsecond", ""}, {"", ""}};
            for (String[] each : cases) {
                PlaceholderControlElement.report(option("Lines"), "Lines", new IllegalStateException(each[0]));
                assertEquals("java.lang.IllegalStateException: " + each[1], toastLine(), each[0]);
            }
            PlaceholderControlElement.report(option("Bare"), "Bare", new IllegalStateException());
            assertEquals("java.lang.IllegalStateException", toastLine());
            PlaceholderControlElement.report(option("Name"), "First line\nsecond line", new IllegalStateException("named"));
            assertEquals("Option failed: First line", toasts().getLast().title());
            PlaceholderControlElement.report(option("Null"), null, new IllegalStateException("null"));
            assertEquals("Option failed: null", toasts().getLast().title());
        });
    }

    @Test
    void theToastTextIsCutToTheLimitWithoutSplittingACharacter() {
        withLogger(logger -> {
            String prefix = "java.lang.IllegalStateException: ";
            IllegalStateException huge = new IllegalStateException("x".repeat(100_000));
            PlaceholderControlElement.report(option("Huge"), "Huge", huge);
            assertEquals((prefix + "x".repeat(256)).substring(0, 256), toastLine());
            // A line break past the limit is never reached
            PlaceholderControlElement.report(option("Late break"), "Late break", new IllegalStateException("y".repeat(300) + "\nz"));
            assertEquals(256, toastLine().length());
            // Exactly the limit is kept whole
            String exact = "w".repeat(256 - prefix.length());
            PlaceholderControlElement.report(option("Exact"), "Exact", new IllegalStateException(exact));
            assertEquals(prefix + exact, toastLine());
            // A surrogate pair across the limit is left out whole
            String pair = "x".repeat(255 - prefix.length()) + "😀tail";
            PlaceholderControlElement.report(option("Pair"), "Pair", new IllegalStateException(pair));
            String line = toastLine();
            assertEquals(255, line.length());
            assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
            // A pair that ends at the limit stays
            String ending = "x".repeat(254 - prefix.length()) + "😀tail";
            PlaceholderControlElement.report(option("Ending"), "Ending", new IllegalStateException(ending));
            assertEquals(prefix + ending.substring(0, 256 - prefix.length()), toastLine());
            // The name in the title too
            PlaceholderControlElement.report(option("Long"), "n".repeat(1000), new IllegalStateException("long"));
            assertEquals("Option failed: " + "n".repeat(256), toasts().getLast().title());
            // The log keeps the whole failure
            verify(logger).error(eq(FAILURE_LOG), eq("Huge"), same(huge));
        });
    }
}
