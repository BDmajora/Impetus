package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CyclingControlTest {
    // Name "Cycle" at x 6, values ending 6 short of the right edge at 194, text on line 5 (centre 9 less half the glyph height)
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 18);
    private static final String OPTION = "Option must not be null";
    private static final String ENUM_TYPE = "Enum type must not be null";
    private static final int WHITE = 0xFFFFFFFF;

    private enum Mode { A, B, C }

    private enum Other { X }

    private enum Empty { }

    private enum Named implements TextProvider {
        ONE;

        @Override
        public TextComponent getLocalizedName() {
            return TextComponent.literal("one");
        }
    }

    private enum Unnamed implements TextProvider {
        ONE, TWO;

        @Override
        public TextComponent getLocalizedName() {
            return this == ONE ? TextComponent.literal("one") : null;
        }
    }

    private enum Failing implements TextProvider {
        ONE;

        @Override
        public TextComponent getLocalizedName() {
            throw new IllegalStateException("no translation");
        }
    }

    private enum Bodied {
        ONE {
            @Override
            public String toString() {
                return "one";
            }
        }
    }

    // A value type that names itself without being an enum
    private record Quality(String label) implements TextProvider {
        @Override
        public TextComponent getLocalizedName() {
            return TextComponent.literal(this.label);
        }
    }

    private record Size(int blocks) { }

    // A value only ever equal to itself, counting the equality checks made on it
    private static final class Counted {
        static int checks;

        @Override
        public boolean equals(Object other) {
            checks++;
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    // A value equal to any other of the same number, counting the equality checks made on it
    private static final class Tally {
        static int checks;
        final int id;

        Tally(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            checks++;
            return other instanceof Tally tally && tally.id == this.id;
        }

        @Override
        public int hashCode() {
            return this.id;
        }
    }

    // A piece of text drawn with the component it came from, where and in what colour
    private record Drawn(TextComponent component, String text, int x, int y, int color) { }

    // Fake fonts keeping every piece of text drawn and counting the text work done outside drawing
    private static class Recording extends FakeDrawContext {
        final List<Drawn> drawn = new ArrayList<>();
        int work;
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
                this.work++;
            }
            return super.getStringWidth(component);
        }

        @Override
        public String substrByWidth(String str, int maxWidth) {
            this.work++;
            return super.substrByWidth(str, maxWidth);
        }

        // The last piece of text drawn, which is the value's name
        Drawn value() {
            return this.drawn.getLast();
        }

        void clear() {
            this.drawn.clear();
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

    // A bound cycler's row at the default bounds
    private static <T> ControlElement<T> row(Option<T> option, T[] values, String... names) {
        return bound(new CyclingControl<>(option, values, names(names))).createElement(DIM);
    }

    private static TextComponent[] names(String... names) {
        TextComponent[] components = new TextComponent[names.length];
        for (int i = 0; i < names.length; i++) {
            components[i] = TextComponent.literal(names[i]);
        }
        return components;
    }

    // Asserts the call throws the given type with the given message
    private static <E extends Throwable> E refused(Class<E> type, String message, Executable call) {
        E thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        return thrown;
    }

    // The name of the value a row draws, or null when it draws none
    private static String shown(ControlElement<?> row) {
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5000, 5000, 0f);
        return draw.strings.size() < 2 ? null : draw.strings.getLast();
    }

    private static boolean click(ControlElement<?> row, boolean reverse) {
        FakeInteractionContext context = new FakeInteractionContext();
        if (reverse) {
            context.held.add(InteractionContext.SpecialKey.SHIFT);
        }
        return row.mouseClicked(context, 100, 9, 0);
    }

    @Test
    void everyConstructorRefusesAMissingOptionBeforeLookingAtAnythingElse() {
        TextComponent[] three = names("a", "b", "c");
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, Mode.class));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, (Class<Mode>) null));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, String.class));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, Mode.class, three));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, (Class<Mode>) null, (TextComponent[]) null));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, Mode.class, Mode.values()));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, (Class<Mode>) null, (Mode[]) null));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, Mode.values(), three));
        refused(NullPointerException.class, OPTION, () -> new CyclingControl<>(null, (Mode[]) null, null));
    }

    @Test
    void everyEnumConstructorRefusesAMissingTypeIncludingTheOneThatIsGivenItsValues() {
        Option<Mode> option = valued("Cycle", Mode.A);
        refused(NullPointerException.class, ENUM_TYPE, () -> new CyclingControl<>(option, (Class<Mode>) null));
        refused(NullPointerException.class, ENUM_TYPE, () -> new CyclingControl<>(option, (Class<Mode>) null, names("a", "b", "c")));
        // The type is checked before the values it would be checked against
        refused(NullPointerException.class, ENUM_TYPE, () -> new CyclingControl<>(option, (Class<Mode>) null, Mode.values()));
        refused(NullPointerException.class, ENUM_TYPE, () -> new CyclingControl<>(option, (Class<Mode>) null, (Mode[]) null));
        refused(NullPointerException.class, "Values must not be null", () -> new CyclingControl<>(option, Mode.class, (Mode[]) null));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aClassThatIsNotAnEnumIsRefusedByNameRatherThanFailingOnItsMissingConstants() {
        Option<String> strings = valued("Cycle", null);
        refused(IllegalArgumentException.class, "java.lang.String is not an enum", () -> new CyclingControl<>(strings, String.class));
        refused(IllegalArgumentException.class, "java.lang.String is not an enum", () -> new CyclingControl<>(strings, String.class, new TextComponent[0]));
        // Enum itself and the class of a constant with a body are not enums either
        Option enums = valued("Cycle", null);
        refused(IllegalArgumentException.class, "java.lang.Enum is not an enum", () -> new CyclingControl(enums, Enum.class));
        Class bodied = Bodied.ONE.getClass();
        refused(IllegalArgumentException.class, bodied.getName() + " is not an enum", () -> new CyclingControl(enums, bodied, names("one")));
    }

    @Test
    void aCyclerRefusesValuesItCouldNotShowWhenMade() {
        Option<Mode> option = valued("Cycle", Mode.A);
        TextComponent[] three = names("a", "b", "c");
        refused(NullPointerException.class, "Values must not be null", () -> new CyclingControl<>(option, (Mode[]) null, three));
        refused(NullPointerException.class, "Names must not be null", () -> new CyclingControl<>(option, Mode.values(), (TextComponent[]) null));
        refused(NullPointerException.class, "Names must not be null", () -> new CyclingControl<>(option, Mode.class, (TextComponent[]) null));
        refused(IllegalArgumentException.class, "A cycler needs at least one value", () -> new CyclingControl<>(option, new Mode[0], new TextComponent[0]));
        refused(IllegalArgumentException.class, "A cycler needs at least one value", () -> new CyclingControl<>(option, Mode.class, new Mode[0]));
        Option<Empty> none = valued("Cycle", null);
        refused(IllegalArgumentException.class, "A cycler needs at least one value", () -> new CyclingControl<>(none, Empty.class));
        refused(IllegalArgumentException.class, "A cycler needs at least one value", () -> new CyclingControl<>(none, Empty.class, new TextComponent[0]));
        refused(IllegalArgumentException.class, "Each value needs one name, but there are 3 values and 2 names",
                () -> new CyclingControl<>(option, Mode.values(), new TextComponent[]{three[0], three[1]}));
        refused(IllegalArgumentException.class, "Each value needs one name, but there are 3 values and 4 names",
                () -> new CyclingControl<>(option, Mode.class, names("a", "b", "c", "d")));
        refused(IllegalArgumentException.class, "Value B has no name", () -> new CyclingControl<>(option, Mode.values(), new TextComponent[]{three[0], null, three[2]}));
        refused(IllegalArgumentException.class, "Value C has no name", () -> new CyclingControl<>(option, Mode.class, new TextComponent[]{three[0], three[1], null}));
        Option<Unnamed> unnamed = valued("Cycle", Unnamed.ONE);
        refused(IllegalArgumentException.class, "Value TWO has no name", () -> new CyclingControl<>(unnamed, Unnamed.class));
        refused(IllegalArgumentException.class, "Value TWO has no name", () -> new CyclingControl<>(unnamed, Unnamed.class, new Unnamed[]{Unnamed.TWO}));
        // A value whose own name fails is left to fail as it does, for the option to turn into a placeholder
        Option<Failing> broken = valued("Cycle", null);
        refused(IllegalStateException.class, "no translation", () -> new CyclingControl<>(broken, Failing.class));
        refused(IllegalStateException.class, "no translation", () -> new CyclingControl<>(broken, Failing.class, new Failing[]{Failing.ONE}));
    }

    @Test
    void aValueListedTwiceIsRefusedSinceTheCyclerCouldNeverStepPastIt() {
        Option<Mode> option = valued("Cycle", Mode.A);
        refused(IllegalArgumentException.class, "Value A is listed at both 0 and 2", () -> new CyclingControl<>(option, new Mode[]{Mode.A, Mode.B, Mode.A}, names("a", "b", "c")));
        refused(IllegalArgumentException.class, "Value B is listed at both 0 and 1", () -> new CyclingControl<>(option, Mode.class, new Mode[]{Mode.B, Mode.B}));
        // Values are found by equality, so equal values made apart and two nulls are the same value too
        Option<Size> sizes = valued("Cycle", null);
        refused(IllegalArgumentException.class, "Value Size[blocks=8] is listed at both 1 and 2",
                () -> new CyclingControl<>(sizes, new Size[]{new Size(4), new Size(8), new Size(8)}, names("4", "8", "eight")));
        refused(IllegalArgumentException.class, "Value null is listed at both 0 and 1", () -> new CyclingControl<>(option, new Mode[]{null, null}, names("unset", "none")));
        // Values that only look alike stay apart, and so does one null among values
        assertEquals(3, new CyclingControl<>(sizes, new Size[]{new Size(8), null, new Size(16)}, names("8", "unset", "16")).getNames().length);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theValuesConstructorRefusesAValueNotOfItsTypeAndNamesTheRestAsTheyNameThemselves() {
        Option<Mode> option = valued("Cycle", Mode.A);
        refused(IllegalArgumentException.class, "Value null is not a " + Mode.class.getName(), () -> new CyclingControl<>(option, Mode.class, new Mode[]{Mode.A, null}));
        // A raw call could hand it another enum's constant, which the option could not hold
        refused(IllegalArgumentException.class, "Value X is not a " + Mode.class.getName(), () -> new CyclingControl(option, Mode.class, new Object[]{Mode.A, Other.X}));
        // A type that names its own values need not be an enum
        Option<Quality> qualities = valued("Cycle", null);
        CyclingControl<Quality> provided = new CyclingControl<>(qualities, Quality.class, new Quality[]{new Quality("Low"), new Quality("High")});
        assertEquals(List.of(TextComponent.literal("Low"), TextComponent.literal("High")), List.of(provided.getNames()));
        Option<Object> objects = valued("Cycle", null);
        refused(IllegalArgumentException.class, "Could not figure out name of object 5", () -> new CyclingControl<>(objects, Object.class, new Object[]{5}));
    }

    @Test
    void namesComeFromTheEnumOrTheValuesThemselvesOrTheCaller() {
        assertEquals(List.of(TextComponent.literal("A"), TextComponent.literal("B"), TextComponent.literal("C")), List.of(new CyclingControl<>(valued("Cycle", Mode.A), Mode.class).getNames()));
        assertEquals(List.of(TextComponent.literal("one")), List.of(new CyclingControl<>(valued("Cycle", Named.ONE), Named.class).getNames()));
        assertEquals(List.of(TextComponent.literal("C"), TextComponent.literal("A")), List.of(new CyclingControl<>(valued("Cycle", Mode.A), Mode.class, new Mode[]{Mode.C, Mode.A}).getNames()));
        assertEquals(List.of(TextComponent.literal("x"), TextComponent.literal("y"), TextComponent.literal("z")),
                List.of(new CyclingControl<>(valued("Cycle", Mode.A), Mode.class, names("x", "y", "z")).getNames()));
        // A constant with a body is named by its constant's name, not its toString
        assertEquals(List.of(TextComponent.literal("ONE")), List.of(new CyclingControl<>(valued("Cycle", Bodied.ONE), Bodied.class).getNames()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theValueTypeIsTheTypeGivenOrElseTheValuesArraysElementClass() {
        assertSame(Mode.class, new CyclingControl<>(valued("Cycle", Mode.A), Mode.class).getValueType());
        assertSame(Mode.class, new CyclingControl<>(valued("Cycle", Mode.A), Mode.class, names("a", "b", "c")).getValueType());
        assertSame(Mode.class, new CyclingControl<>(valued("Cycle", Mode.A), Mode.class, new Mode[]{Mode.B}).getValueType());
        assertSame(Bodied.class, new CyclingControl<>(valued("Cycle", Bodied.ONE), Bodied.class).getValueType());
        // An array made generically holds Objects, but the type given still says what they are
        assertSame(Mode.class, new CyclingControl(valued("Cycle", Mode.A), Mode.class, new Object[]{Mode.A, Mode.B}).getValueType());
        assertSame(Mode.class, new CyclingControl<>(valued("Cycle", Mode.A), new Mode[]{Mode.B}, names("b")).getValueType());
        assertSame(Object.class, new CyclingControl<>(valued("Cycle", null), new Object[]{"a"}, names("a")).getValueType());
        // So the strict path refuses an option of another type
        Option enumOption = valued("Cycle", Mode.A);
        when(enumOption.getValueType()).thenReturn(Other.class);
        CyclingControl generic = bound(new CyclingControl(enumOption, Mode.class, new Object[]{Mode.A, Mode.B}));
        assertSame(generic, enumOption.getControl());
        assertThrows(IllegalStateException.class, () -> Control.createElementFor(enumOption, DIM));
    }

    @Test
    void aCyclerKeepsItsOwnCopiesSoArraysChangedAfterwardsCannotReachIt() {
        Mode[] values = {Mode.A, Mode.B};
        TextComponent[] names = names("FIRST", "SECOND");
        Option<Mode> option = valued("Cycle", Mode.B);
        CyclingControl<Mode> control = bound(new CyclingControl<>(option, values, names));
        values[1] = Mode.C;
        names[1] = TextComponent.literal("CHANGED");
        control.getNames()[1] = TextComponent.literal("CHANGED");
        assertEquals(TextComponent.literal("SECOND"), control.getNames()[1]);
        assertNotSame(control.getNames(), control.getNames());
        assertSame(Mode.class, control.getValueType());
        assertEquals("SECOND", shown(control.createElement(DIM)));
        // The values given to the enum constructor are copied as well
        Mode[] some = {Mode.A, Mode.C};
        CyclingControl<Mode> partial = bound(new CyclingControl<>(valued("Cycle", Mode.C), Mode.class, some));
        some[1] = Mode.B;
        assertEquals("C", shown(partial.createElement(DIM)));
    }

    @Test
    void noStyleSetChangedOnANameGivenToTheCyclerOrHandedOutByItReachesItsRows() {
        EnumSet<TextFormattingStyle> styles = EnumSet.of(TextFormattingStyle.GOLD);
        TextComponent golden = new TextComponent.Styled(TextComponent.literal("GOLDEN"), styles);
        TextComponent expected = new TextComponent.Styled(TextComponent.literal("GOLDEN"), EnumSet.of(TextFormattingStyle.GOLD));
        Option<Mode> option = valued("Cycle", Mode.A);
        CyclingControl<Mode> control = bound(new CyclingControl<>(option, new Mode[]{Mode.A}, new TextComponent[]{golden}));
        ControlElement<Mode> early = control.createElement(DIM);
        styles.add(TextFormattingStyle.ITALIC);
        // Nor one changed on a name the cycler handed out
        ((TextComponent.Styled) control.getNames()[0]).styles().add(TextFormattingStyle.UNDERLINE);
        assertEquals(expected, control.getNames()[0]);
        for (ControlElement<Mode> row : List.of(early, control.createElement(DIM))) {
            Recording draw = new Recording();
            row.render(draw, 5000, 5000, 0f);
            assertEquals(expected, draw.value().component());
        }
    }

    @Test
    void theControlHandsOutItsOptionAFixedColumnAndAFreshRowAtExactlyTheBoundsGiven() {
        Option<Mode> option = valued("Cycle", Mode.A);
        CyclingControl<Mode> control = bound(new CyclingControl<>(option, Mode.class));
        assertSame(option, control.getOption());
        assertEquals(70, control.getMaxWidth());
        assertEquals(70, control.getMaxWidth(new FakeDrawContext()));
        Dim2i dim = new Dim2i(3, 4, 150, 18);
        ControlElement<Mode> first = control.createElement(dim);
        assertNotSame(first, control.createElement(dim));
        assertSame(option, first.getOption());
        assertSame(dim, first.getDimensions());
        refused(NullPointerException.class, "Dimensions must not be null", () -> control.createElement(null));
        // Nothing is read from the option until a row is drawn or used
        verify(option, never()).getValue();
    }

    @Test
    void theCurrentValuesNameIsDrawnRightAlignedSixShortOfTheEdgeOnTheNamesLine() {
        Option<Mode> option = valued("Cycle", Mode.B);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.values(), names("Off", "Fast", "Fancy"))).createElement(new Dim2i(10, 20, 200, 18));
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        // Centre 29 less 4, and the 24 pixel "Fast" ending at 204
        assertEquals(List.of(new Drawn(TextComponent.literal("Cycle").withStyle(TextFormattingStyle.WHITE), "Cycle", 16, 25, WHITE),
                new Drawn(TextComponent.literal("Fast"), "Fast", 180, 25, WHITE)), draw.drawn);
    }

    @Test
    void anUnavailableValueIsGreyedAndStruckThroughAndShownPlainAgainOnceAvailable() {
        Option<Mode> option = valued("Cycle", Mode.C);
        ControlElement<Mode> row = row(option, Mode.values(), "Off", "Fast", "Fancy");
        when(option.isAvailable()).thenReturn(false);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(new Drawn(TextComponent.literal("Fancy").withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH), "Fancy", 164, 5, DefaultColors.TEXT_DISABLED), draw.value());
        when(option.isAvailable()).thenReturn(true);
        draw.clear();
        row.render(draw, 5000, 5000, 0f);
        assertEquals(new Drawn(TextComponent.literal("Fancy"), "Fancy", 164, 5, WHITE), draw.value());
    }

    @Test
    void aMissingValueOrOneOutsideTheValuesIsShownAsTheFirstAndANamedNullIsAValueLikeAnyOther() {
        assertEquals("A", shown(row(valued("Cycle", (Mode) null), Mode.values(), "A", "B", "C")));
        assertEquals("B", shown(row(valued("Cycle", Mode.A), new Mode[]{Mode.B, Mode.C}, "B", "C")));
        assertEquals("UNSET", shown(row(valued("Cycle", (Mode) null), new Mode[]{Mode.B, null}, "B", "UNSET")));
        // A value of another type entirely, as a mispaired option would hand over, is missing too
        @SuppressWarnings({"unchecked", "rawtypes"})
        Option<Mode> mistyped = (Option) valued("Cycle", "B");
        assertEquals("A", shown(row(mistyped, Mode.values(), "A", "B", "C")));
    }

    @Test
    void aValueTooWideForTheRoomBesideTheNameIsCutToItAndRightAligned() {
        // "graphics quality" takes 96 of the 118 the 70 column leaves, so values may start at 6 + 96 + 6 = 108 and end 6 short of 200: 86 pixels
        Option<Mode> option = valued("graphics quality", Mode.B);
        Recording draw = new Recording();
        row(option, Mode.values(), "A", "A VERY LONG MODE NAME", "C").render(draw, 5000, 5000, 0f);
        // 86 less the 18 pixel ellipsis leaves 68, eleven characters, the space they end on dropped
        assertEquals(List.of("graphics quality", "A VERY LONG..."), draw.strings);
        assertEquals(110, draw.value().x());
        // A row too narrow for a name leaves the value the whole row less its inset: 14 pixels, too few for the ellipsis but enough for one letter
        for (Mode value : Mode.values()) {
            FakeDrawContext narrow = new FakeDrawContext();
            bound(new CyclingControl<>(valued("Cycle", value), Mode.values(), names("Fast", "B", "C"))).createElement(new Dim2i(0, 0, 20, 18)).render(narrow, 5000, 5000, 0f);
            assertEquals(value == Mode.A ? List.of() : List.of(value.name()), narrow.strings);
        }
    }

    @Test
    void aRowWithNoRoomForALineOrNoAreaDrawsNoValueAndNeverReadsTheOption() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 200, 8), new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, 200, -1), new Dim2i(0, 0, (1 << 30) + 1, 18)}) {
            Option<Mode> option = valued("Cycle", Mode.B);
            ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(dim);
            FakeDrawContext draw = new FakeDrawContext();
            row.render(draw, 5000, 5000, 0f);
            assertEquals(List.of(), draw.strings, dim::toString);
            verify(option, never()).getValue();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void valuesAreMatchedByEqualitySoOnesMadeAfreshOnEveryReadAreStillFound() {
        Size[] sizes = {new Size(8), new Size(16), new Size(32)};
        // Every read builds a fresh value, as an int field read through Integer does above 127
        int[] stored = {16};
        Option<Size> option = Mockito.mock(Option.class);
        when(option.getValue()).thenAnswer(invocation -> new Size(stored[0]));
        Mockito.doAnswer(invocation -> stored[0] = invocation.<Size>getArgument(0).blocks()).when(option).setValue(any());
        when(option.isAvailable()).thenReturn(true);
        when(option.getName()).thenReturn(TextComponent.literal("Name"));
        ControlElement<Size> row = row(option, sizes, "8", "16", "32");
        assertEquals("16", shown(row));
        assertTrue(click(row, false));
        assertEquals(32, stored[0]);
        assertEquals("32", shown(row));
        assertTrue(click(row, false));
        assertEquals(8, stored[0]);
        assertTrue(click(row, true));
        assertEquals(32, stored[0]);
    }

    @Test
    void theOptionIsReadOncePerFrameAndOncePerClickWhereverTheValueSits() {
        for (Mode initial : new Mode[]{Mode.A, Mode.C, null}) {
            Option<Mode> option = valued("Cycle", initial);
            ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
            clearInvocations(option);
            row.render(new FakeDrawContext(), 5000, 5000, 0f);
            verify(option, times(1)).getValue();
            assertTrue(click(row, false));
            verify(option, times(2)).getValue();
            verify(option, times(1)).setValue(any());
        }
    }

    @Test
    void theSameValueReadAgainIsFoundWithoutComparingItToAnyEntry() {
        Counted[] values = {new Counted(), new Counted(), new Counted(), new Counted()};
        Option<Counted> option = valued("Cycle", values[2]);
        ControlElement<Counted> row = row(option, values, "A", "B", "C", "D");
        Counted.checks = 0;
        assertEquals("C", shown(row));
        // The two entries before it, the third being the same object
        assertEquals(2, Counted.checks);
        for (int frame = 0; frame < 5; frame++) {
            assertEquals("C", shown(row));
        }
        assertEquals(2, Counted.checks);
        // The value just written is remembered where it was written, so the frame after the click compares nothing either
        assertTrue(click(row, false));
        assertSame(values[3], option.getValue());
        assertEquals("D", shown(row));
        assertEquals(2, Counted.checks);
    }

    @Test
    void anEqualValueMadeAfreshEachFrameIsCheckedAgainstItsOwnEntryAlone() {
        Tally[] values = {new Tally(0), new Tally(1), new Tally(2), new Tally(3)};
        int[] stored = {3};
        Option<Tally> option = valued("Cycle", null);
        when(option.getValue()).thenAnswer(invocation -> new Tally(stored[0]));
        ControlElement<Tally> row = row(option, values, "A", "B", "C", "D");
        Tally.checks = 0;
        assertEquals("D", shown(row));
        assertEquals(4, Tally.checks);
        for (int frame = 0; frame < 5; frame++) {
            Tally.checks = 0;
            assertEquals("D", shown(row));
            assertEquals(1, Tally.checks, "frame " + frame);
        }
    }

    @Test
    void aMissingValueMadeAfreshEachFrameIsKnownToBeMissingAfterOneCheck() {
        Tally[] values = {new Tally(0), new Tally(1), new Tally(2), new Tally(3)};
        Option<Tally> option = valued("Cycle", null);
        when(option.getValue()).thenAnswer(invocation -> new Tally(99));
        ControlElement<Tally> row = row(option, values, "A", "B", "C", "D");
        Tally.checks = 0;
        assertEquals("A", shown(row));
        assertEquals(4, Tally.checks);
        for (int frame = 0; frame < 5; frame++) {
            Tally.checks = 0;
            assertEquals("A", shown(row));
            assertEquals(1, Tally.checks, "frame " + frame);
        }
        // And still steps on from the first entry
        assertTrue(click(row, false));
        verify(option).setValue(values[1]);
    }

    @Test
    void aStepMovesOneEntryAndWrapsRoundAtEitherEndForAnyNumberOfValues() {
        for (int count = 2; count <= 6; count++) {
            Integer[] values = new Integer[count];
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                values[i] = 1000 + i;
                names[i] = "v" + i;
            }
            for (int start = 0; start < count; start++) {
                for (boolean reverse : new boolean[]{false, true}) {
                    // Boxed afresh, so the start is found by equality
                    Option<Integer> option = valued("Cycle", Integer.valueOf(1000 + start));
                    ControlElement<Integer> row = row(option, values, names);
                    assertTrue(click(row, reverse));
                    assertEquals(values[Math.floorMod(start + (reverse ? -1 : 1), count)], option.getValue(), count + " values from " + start + (reverse ? " back" : ""));
                }
            }
        }
    }

    @Test
    void clickingRoundEitherWayVisitsEveryValueOnceAndComesBackToTheStart() {
        for (boolean reverse : new boolean[]{false, true}) {
            Option<Mode> option = valued("Cycle", Mode.B);
            ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
            List<Mode> visited = new ArrayList<>();
            List<String> drawn = new ArrayList<>();
            for (int step = 0; step < 3; step++) {
                assertTrue(click(row, reverse));
                visited.add(option.getValue());
                drawn.add(shown(row));
            }
            assertEquals(reverse ? List.of(Mode.A, Mode.C, Mode.B) : List.of(Mode.C, Mode.A, Mode.B), visited);
            assertEquals(reverse ? List.of("A", "C", "B") : List.of("C", "A", "B"), drawn);
        }
    }

    @Test
    void aMissingValueStepsToTheSecondEntryForwardsAndTheLastBackwards() {
        for (boolean reverse : new boolean[]{false, true}) {
            Option<Mode> option = valued("Cycle", (Mode) null);
            ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
            assertEquals("A", shown(row));
            assertTrue(click(row, reverse));
            assertEquals(reverse ? Mode.C : Mode.B, option.getValue());
        }
        // A value outside a partial set of values is treated the same way
        Option<Mode> outside = valued("Cycle", Mode.A);
        assertTrue(click(row(outside, new Mode[]{Mode.B, Mode.C}, "B", "C"), false));
        assertEquals(Mode.C, outside.getValue());
    }

    @Test
    void aCyclerWithOneValueLeavesItAloneOnceHeldButStillSetsItOverAMissingOne() {
        for (boolean reverse : new boolean[]{false, true}) {
            Option<Mode> option = valued("Cycle", Mode.B);
            ControlElement<Mode> row = row(option, new Mode[]{Mode.B}, "B");
            FakeInteractionContext context = new FakeInteractionContext();
            if (reverse) {
                context.held.add(InteractionContext.SpecialKey.SHIFT);
            }
            // Handled with its click, but there is nothing else to step to
            assertTrue(row.mouseClicked(context, 100, 9, 0));
            row.setFocused(true);
            assertTrue(row.keyTyped(context, '\0', reverse ? Keys.LEFT : Keys.RIGHT));
            assertEquals(2, context.clicks);
            verify(option, never()).setValue(any());
            for (Mode missing : new Mode[]{null, Mode.A}) {
                Option<Mode> unset = valued("Cycle", missing);
                assertTrue(click(row(unset, new Mode[]{Mode.B}, "B"), reverse));
                verify(unset).setValue(Mode.B);
            }
        }
    }

    @Test
    void aWriteTheOptionDoesNotKeepIsNotTakenForTheValueItHolds() {
        // An option that drops every write
        Option<Mode> ignoring = valued("Cycle", Mode.A);
        doNothing().when(ignoring).setValue(any());
        ControlElement<Mode> row = bound(new CyclingControl<>(ignoring, Mode.class)).createElement(DIM);
        assertTrue(click(row, false));
        assertEquals("A", shown(row));
        assertTrue(click(row, false));
        verify(ignoring, times(2)).setValue(Mode.B);
        // One that keeps another value than the one written
        Option<Mode> clamping = valued("Cycle", Mode.A);
        Object[] held = {Mode.A};
        when(clamping.getValue()).thenAnswer(invocation -> held[0]);
        Mockito.doAnswer(invocation -> held[0] = Mode.C).when(clamping).setValue(any());
        ControlElement<Mode> clamped = bound(new CyclingControl<>(clamping, Mode.class)).createElement(DIM);
        assertTrue(click(clamped, false));
        verify(clamping).setValue(Mode.B);
        assertEquals("C", shown(clamped));
        assertTrue(click(clamped, false));
        verify(clamping).setValue(Mode.A);
    }

    @Test
    void aValueChangedElsewhereBetweenClicksIsSteppedFromRatherThanTheOneTheRowWrote() {
        Option<Mode> option = valued("Cycle", Mode.A);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
        assertTrue(click(row, false));
        assertEquals(Mode.B, option.getValue());
        // As Undo or another screen would set it
        option.setValue(Mode.C);
        assertTrue(click(row, false));
        assertEquals(Mode.A, option.getValue());
    }

    @Test
    void aWriteThatThrowsPlaysNoClickAndLeavesTheRowSteppingFromTheValueStillHeld() {
        Option<Mode> option = valued("Cycle", Mode.A);
        doThrow(new IllegalStateException("refused")).doNothing().when(option).setValue(any());
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        refused(IllegalStateException.class, "refused", () -> row.mouseClicked(context, 100, 9, 0));
        assertEquals(0, context.clicks);
        assertEquals("A", shown(row));
        assertTrue(row.mouseClicked(context, 100, 9, 0));
        verify(option, times(2)).setValue(Mode.B);
        assertEquals(1, context.clicks);
    }

    @Test
    void onlyALeftClickInsideTheRowOfAnAvailableOptionSteps() {
        Option<Mode> option = valued("Cycle", Mode.A);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(new Dim2i(10, 20, 200, 18));
        FakeInteractionContext context = new FakeInteractionContext();
        clearInvocations(option);
        // A right click is not a step back; Shift is
        for (int button : new int[]{1, 2, -1, 7}) {
            assertFalse(row.mouseClicked(context, 100, 25, button), "button " + button);
        }
        // Just past each edge, the right and bottom edges themselves, and no position at all
        for (double[] at : new double[][]{{9, 25}, {210, 25}, {100, 19}, {100, 38}, {Double.NaN, 25}, {100, Double.NaN}}) {
            assertFalse(row.mouseClicked(context, at[0], at[1], 0), at[0] + "," + at[1]);
        }
        verify(option, never()).getValue();
        verify(option, never()).isAvailable();
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.mouseClicked(context, 100, 25, 0));
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        assertEquals(0, context.clicks);
        when(option.isAvailable()).thenReturn(true);
        for (double[] at : new double[][]{{10, 20}, {209.5, 37.5}}) {
            assertTrue(row.mouseClicked(context, at[0], at[1], 0));
        }
        assertEquals(Mode.C, option.getValue());
        assertEquals(2, context.clicks);
    }

    @Test
    void aRowTooFarOutToBeDrawnTakesNoClickOrKeyEvenWithTheCursorInsideOrFocusForcedOnIt() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, (1 << 30) + 1, 18), new Dim2i(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 18)}) {
            Option<Mode> option = valued("Cycle", Mode.A);
            ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(dim);
            double x = dim.x() < 0 ? -5 : 100;
            assertTrue(dim.containsCursor(x, 9), dim::toString);
            FakeInteractionContext context = new FakeInteractionContext();
            assertFalse(row.mouseClicked(context, x, 9, 0), dim::toString);
            assertFalse(row.canFocus());
            row.setFocused(true);
            assertFalse(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertFalse(row.keyTyped(context, '\0', Keys.RIGHT), dim::toString);
            verify(option, never()).setValue(any());
            assertEquals(0, context.clicks);
        }
    }

    @Test
    void aFocusedCyclerStepsOnEveryPressWithShiftReversingAndOnTheArrowsTheWayTheyPoint() {
        Option<Mode> option = valued("Cycle", Mode.A);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        assertFalse(row.keyTyped(context, '\0', Keys.RIGHT));
        assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
        assertEquals(Mode.A, option.getValue());
        assertTrue(row.canFocus());
        row.setFocused(true);
        // Each press by its key code or by the character it types
        char[] typed = {'\0', '\0', '\0', ' ', '\r', '\n'};
        int[] codes = {Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER, 0, 0, 0};
        Mode expected = Mode.A;
        for (int i = 0; i < typed.length; i++) {
            assertTrue(row.keyTyped(context, typed[i], codes[i]));
            expected = Mode.values()[(expected.ordinal() + 1) % 3];
            assertEquals(expected, option.getValue(), "press " + i);
        }
        context.held.add(InteractionContext.SpecialKey.SHIFT);
        for (int i = 0; i < typed.length; i++) {
            assertTrue(row.keyTyped(context, typed[i], codes[i]));
            expected = Mode.values()[(expected.ordinal() + 2) % 3];
            assertEquals(expected, option.getValue(), "shifted press " + i);
        }
        // Shift turns a press round, not an arrow
        for (boolean shift : new boolean[]{true, false}) {
            if (!shift) {
                context.held.clear();
            }
            assertTrue(row.keyTyped(context, '\0', Keys.RIGHT));
            expected = Mode.values()[(expected.ordinal() + 1) % 3];
            assertEquals(expected, option.getValue());
            assertTrue(row.keyTyped(context, '\0', Keys.LEFT));
            assertTrue(row.keyTyped(context, '\0', Keys.LEFT));
            expected = Mode.values()[(expected.ordinal() + 1) % 3];
            assertEquals(expected, option.getValue());
        }
        assertEquals(18, context.clicks);
    }

    @Test
    void aKeyTheCyclerDoesNotTakeNeverAsksAboutTheOptionAndAnUnavailableOneTakesNone() {
        Option<Mode> option = valued("Cycle", Mode.A);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.class)).createElement(DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        clearInvocations(option);
        // Unfocused, every key is someone else's
        assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
        row.setFocused(true);
        // Letters, Tab by key and by character, Escape, Up and Down
        char[] typed = {'a', '\t', '\0', '\0', '\0', '\0'};
        int[] codes = {30, 0, Keys.TAB, 1, 200, 208};
        for (int i = 0; i < typed.length; i++) {
            assertFalse(row.keyTyped(context, typed[i], codes[i]), "key " + i);
        }
        verify(option, never()).isAvailable();
        verify(option, never()).getValue();
        when(option.isAvailable()).thenReturn(false);
        for (int code : new int[]{Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER, Keys.LEFT, Keys.RIGHT}) {
            assertFalse(row.keyTyped(context, '\0', code), "code " + code);
        }
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        assertEquals(0, context.clicks);
    }

    @Test
    void theValueIsFittedOnlyWhenItsValueItsStateOrItsFontSourceChanges() {
        Option<Mode> option = valued("Cycle", Mode.A);
        ControlElement<Mode> row = bound(new CyclingControl<>(option, Mode.values(), names("Off", "Fast", "Fancy"))).createElement(DIM);
        Recording draw = new Recording();
        row.render(draw, 5000, 5000, 0f);
        TextComponent first = draw.value().component();
        for (int frame = 0; frame < 4; frame++) {
            draw.clear();
            row.render(draw, 5000, 5000, 0f);
            assertEquals(0, draw.work, "frame " + frame);
            assertSame(first, draw.value().component());
        }
        for (Runnable change : List.<Runnable>of(() -> option.setValue(Mode.C), () -> when(option.isAvailable()).thenReturn(false), () -> when(option.isAvailable()).thenReturn(true))) {
            change.run();
            draw.clear();
            row.render(draw, 5000, 5000, 0f);
            assertTrue(draw.work > 0);
            draw.clear();
            row.render(draw, 5000, 5000, 0f);
            assertEquals(0, draw.work);
        }
        assertEquals("Fancy", draw.value().text());
        // A new font source fits the value again in its own fonts
        Recording other = new Recording();
        row.render(other, 5000, 5000, 0f);
        assertTrue(other.work > 0);
        assertEquals(new Drawn(TextComponent.literal("Fancy"), "Fancy", 164, 5, WHITE), other.value());
    }

    @Test
    void aNullStoredEnumStaysUsableThroughTheRealOptionUntilACycleWritesItBack() {
        // Gson leaves an enum field null when the saved name is no longer a constant
        Mode[] field = {null};
        OptionStorage<Mode[]> storage = () -> field;
        OptionImpl<Mode[], Mode> option = OptionImpl.createBuilder(Mode.class, storage)
                .setName(TextComponent.literal("Name"))
                .setTooltip(TextComponent.literal("Tip"))
                .setBinding((f, v) -> f[0] = v, f -> f[0])
                .setControl(o -> new CyclingControl<>(o, Mode.class))
                .build();
        ControlElement<Mode> row = option.getControl().createElement(DIM);
        FakeDrawContext draw = new FakeDrawContext();
        row.render(draw, 5, 5, 0f);
        assertEquals(List.of("Name", "A"), draw.strings);
        assertTrue(click(row, false));
        assertEquals(Mode.B, option.getValue());
        // The next frame asks hasChanged while the stored value is still null
        FakeDrawContext next = new FakeDrawContext();
        row.render(next, 5, 5, 0f);
        assertEquals(List.of("Name *", "B"), next.strings);
        option.applyChanges();
        assertEquals(Mode.B, field[0]);
        assertFalse(option.hasChanged());
    }
}
