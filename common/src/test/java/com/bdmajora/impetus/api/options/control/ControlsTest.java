package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ControlsTest {
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 20);

    private enum Mode { A, B, C }

    private enum Named implements TextProvider {
        ONE;

        @Override
        public TextComponent getLocalizedName() {
            return TextComponent.literal("one");
        }
    }

    // A mutable option whose value lives in a holder, with availability, change state and name all controllable
    private static <T> Option<T> option(T initial, boolean available, boolean changed) {
        @SuppressWarnings("unchecked")
        Option<T> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        Mockito.when(option.getValue()).thenAnswer(inv -> holder[0]);
        Mockito.doAnswer(inv -> holder[0] = inv.getArgument(0)).when(option).setValue(Mockito.any());
        Mockito.when(option.isAvailable()).thenReturn(available);
        Mockito.when(option.hasChanged()).thenReturn(changed);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal("Name"));
        Mockito.when(option.getImpact()).thenReturn(OptionImpact.LOW);
        return option;
    }

    @Test
    void tickBoxTogglesOnClickAndRendersBothStates() {
        Option<Boolean> option = option(false, true, false);
        TickBoxControl control = new TickBoxControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        assertSame(option, control.getOption());
        assertEquals(30, control.getMaxWidth());
        ControlElement<Boolean> element = control.createElement(DIM);
        assertSame(option, element.getOption());
        assertEquals(DIM, element.getDimensions());
        assertTrue(element.isMouseOver(5, 5));
        assertFalse(element.isMouseOver(500, 5));

        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(true, option.getValue());
        assertEquals(1, ctx.clicks);
        assertFalse(element.mouseClicked(ctx, 5, 5, 1));
        assertFalse(element.mouseClicked(ctx, 500, 5, 0));

        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("Name"));
        element.render(draw, 500, 500, 0f);
        Mockito.when(option.isAvailable()).thenReturn(false);
        element.render(draw, 5, 5, 0f);
        assertFalse(element.mouseClicked(ctx, 5, 5, 0));
        Mockito.when(option.isAvailable()).thenReturn(true);
        Mockito.when(option.hasChanged()).thenReturn(true);
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("Name *"));
        option.setValue(false);
        element.render(draw, 5, 5, 0f);
    }

    @Test
    void longLabelsAreEllipsisedAndAccentFollowsTheOptionId() {
        Option<Boolean> option = option(true, true, false);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal("A very long option name that will not fit in the row"));
        Mockito.when(option.getId()).thenReturn(OptionIdentifier.create("coarctatio", "long", Boolean.class));
        TickBoxControl control = new TickBoxControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        ControlElement<Boolean> element = control.createElement(new Dim2i(0, 0, 100, 20));
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.stream().anyMatch(s -> s.endsWith("...")));
        assertTrue(draw.drew(Integer.toHexString(DefaultColors.getModAccentColor("coarctatio"))));
    }

    @Test
    void cyclingControlStepsThroughValuesBothWays() {
        Option<Mode> option = option(Mode.A, true, false);
        CyclingControl<Mode> control = new CyclingControl<>(option, Mode.class);
        Mockito.when(option.getControl()).thenReturn(control);
        assertEquals(3, control.getNames().length);
        assertEquals("A", control.getNames()[0].toString());
        assertSame(option, control.getOption());
        assertEquals(70, control.getMaxWidth());
        ControlElement<Mode> element = control.createElement(DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(Mode.B, option.getValue());
        ctx.held.add(InteractionContext.SpecialKey.SHIFT);
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(Mode.C, option.getValue());
        assertFalse(element.mouseClicked(ctx, 5, 5, 2));
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("C"));
        Mockito.when(option.isAvailable()).thenReturn(false);
        element.render(draw, 5, 5, 0f);
        assertFalse(element.mouseClicked(ctx, 5, 5, 0));

        Option<Mode> unknown = option(null, true, false);
        CyclingControl<Mode> partial = new CyclingControl<>(unknown, Mode.class, new Mode[]{Mode.B, Mode.C});
        Mockito.when(unknown.getControl()).thenReturn(partial);
        FakeDrawContext draw2 = new FakeDrawContext();
        partial.createElement(DIM).render(draw2, 0, 0, 0f);
        assertTrue(draw2.strings.contains("B"));

        CyclingControl<Mode> named = new CyclingControl<>(option, Mode.class, new TextComponent[]{TextComponent.literal("x"), TextComponent.literal("y"), TextComponent.literal("z")});
        assertEquals("z", named.getNames()[2].toString());
        Option<Named> provided = option(Named.ONE, true, false);
        assertEquals("one", new CyclingControl<>(provided, Named.class).getNames()[0].toString());
        assertThrows(IllegalArgumentException.class, () -> new CyclingControl<>(option, Mode.values(), new TextComponent[1]));
        Option<Object> objects = option(new Object(), true, false);
        assertThrows(IllegalArgumentException.class, () -> new CyclingControl<>(objects, Object.class, new Object[]{new Object()}));
    }

    @Test
    void cyclingControlReadsTheOptionOncePerRenderAndClickWhereverTheValueSits() {
        // The old loop read the option once per allowed value, so the last value or a missing one cost a full pass
        for (Mode initial : new Mode[]{Mode.A, Mode.C, null}) {
            Option<Mode> option = option(initial, true, false);
            CyclingControl<Mode> control = new CyclingControl<>(option, Mode.class);
            Mockito.when(option.getControl()).thenReturn(control);
            ControlElement<Mode> element = control.createElement(DIM);
            Mockito.clearInvocations(option);
            element.render(new FakeDrawContext(), 5, 5, 0f);
            Mockito.verify(option, Mockito.times(1)).getValue();
            assertTrue(element.mouseClicked(new FakeInteractionContext(), 5, 5, 0));
            Mockito.verify(option, Mockito.times(2)).getValue();
        }
    }

    @Test
    void cyclingControlShowsAMissingValueAsTheFirstEntryAndStepsOnFromIt() {
        for (boolean reverse : new boolean[]{false, true}) {
            Option<Mode> option = option(null, true, false);
            CyclingControl<Mode> control = new CyclingControl<>(option, Mode.class);
            Mockito.when(option.getControl()).thenReturn(control);
            ControlElement<Mode> element = control.createElement(DIM);
            FakeDrawContext draw = new FakeDrawContext();
            element.render(draw, 5, 5, 0f);
            assertTrue(draw.strings.contains("A"));
            FakeInteractionContext ctx = new FakeInteractionContext();
            if (reverse) {
                ctx.held.add(InteractionContext.SpecialKey.SHIFT);
            }
            assertTrue(element.mouseClicked(ctx, 5, 5, 0));
            assertEquals(reverse ? Mode.C : Mode.B, option.getValue());
        }
        // A value outside a partial universe is treated the same way
        Option<Mode> outside = option(Mode.A, true, false);
        CyclingControl<Mode> partial = new CyclingControl<>(outside, Mode.class, new Mode[]{Mode.B, Mode.C});
        Mockito.when(outside.getControl()).thenReturn(partial);
        assertTrue(partial.createElement(DIM).mouseClicked(new FakeInteractionContext(), 5, 5, 0));
        assertEquals(Mode.C, outside.getValue());
    }

    private record Size(int blocks) { }

    @Test
    @SuppressWarnings("unchecked")
    void cyclingControlMatchesValuesByEqualityNotIdentity() {
        Size[] sizes = {new Size(8), new Size(16), new Size(32)};
        TextComponent[] names = {TextComponent.literal("8"), TextComponent.literal("16"), TextComponent.literal("32")};
        // Every read builds a fresh value, as an int field read through Integer does above 127
        int[] stored = {16};
        Option<Size> option = Mockito.mock(Option.class);
        Mockito.when(option.getValue()).thenAnswer(inv -> new Size(stored[0]));
        Mockito.doAnswer(inv -> stored[0] = inv.<Size>getArgument(0).blocks()).when(option).setValue(Mockito.any());
        Mockito.when(option.isAvailable()).thenReturn(true);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal("Name"));
        CyclingControl<Size> control = new CyclingControl<>(option, sizes, names);
        Mockito.when(option.getControl()).thenReturn(control);
        ControlElement<Size> element = control.createElement(DIM);
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("16"));
        assertFalse(draw.strings.contains("8"));
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(32, stored[0]);
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(8, stored[0]);
        ctx.held.add(InteractionContext.SpecialKey.SHIFT);
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(32, stored[0]);
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
        ControlElement<Mode> element = option.getControl().createElement(DIM);
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("A"));
        assertTrue(draw.strings.contains("Name"));
        assertTrue(element.mouseClicked(new FakeInteractionContext(), 5, 5, 0));
        assertEquals(Mode.B, option.getValue());
        // The next frame asks hasChanged while the stored value is still null
        FakeDrawContext next = new FakeDrawContext();
        element.render(next, 5, 5, 0f);
        assertTrue(next.strings.contains("Name *"));
        assertTrue(next.strings.contains("B"));
        option.applyChanges();
        assertEquals(Mode.B, field[0]);
        assertFalse(option.hasChanged());
    }

    @Test
    void sliderSnapsClampsAndDrags() {
        Option<Integer> option = option(4, true, false);
        SliderControl control = new SliderControl(option, 0, 10, 2, ControlValueFormatter.number());
        Mockito.when(option.getControl()).thenReturn(control);
        assertSame(option, control.getOption());
        assertEquals(130, control.getMaxWidth());
        ControlElement<Integer> element = control.createElement(DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        // The track spans x 104..193
        assertTrue(element.mouseClicked(ctx, 193, 10, 0));
        assertEquals(10, option.getValue());
        assertTrue(element.mouseDragged(ctx, 104, 10, 0, 0, 0));
        assertEquals(0, option.getValue());
        assertTrue(element.mouseDragged(ctx, 150, 10, 0, 0, 0));
        assertEquals(6, option.getValue());
        // A click beside the track does not grab it, so no drag after it moves the value
        assertTrue(element.mouseClicked(ctx, 5, 10, 0));
        assertFalse(element.mouseDragged(ctx, 5, 10, 0, 0, 0));
        assertFalse(element.mouseDragged(ctx, 120, 10, 0, 0, 0));
        assertEquals(6, option.getValue());
        // A held slider keeps following the cursor once it leaves the track, clamped to the ends
        assertTrue(element.mouseClicked(ctx, 150, 10, 0));
        assertTrue(element.mouseDragged(ctx, 500, 10, 0, 0, 0));
        assertEquals(10, option.getValue());
        FakeDrawContext held = new FakeDrawContext();
        element.render(held, 500, 500, 0f);
        assertTrue(element.mouseDragged(ctx, 150, 10, 0, 0, 0));
        assertEquals(6, option.getValue());
        assertFalse(element.mouseClicked(ctx, 5, 10, 1));
        assertFalse(element.mouseClicked(ctx, 500, 10, 0));
        assertFalse(element.mouseDragged(ctx, 150, 10, 1, 0, 0));

        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 150, 10, 0f);
        assertTrue(draw.strings.contains("6"));
        element.render(draw, 500, 500, 0f);
        Mockito.when(option.isAvailable()).thenReturn(false);
        element.render(draw, 150, 10, 0f);
        assertFalse(element.mouseClicked(ctx, 150, 10, 0));
        assertFalse(element.mouseDragged(ctx, 150, 10, 0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> new SliderControl(option, 10, 0, 1, ControlValueFormatter.number()));
        assertThrows(IllegalArgumentException.class, () -> new SliderControl(option, 0, 10, 0, ControlValueFormatter.number()));
        assertThrows(IllegalArgumentException.class, () -> new SliderControl(option, 0, 10, 3, ControlValueFormatter.number()));
        assertThrows(IllegalArgumentException.class, () -> new SliderControl(option, 0, 10, 2, null));
    }

    @Test
    void actionButtonRunsItsAction() {
        Option<Boolean> option = option(false, true, false);
        AtomicInteger runs = new AtomicInteger();
        ActionButtonControl control = new ActionButtonControl(option, runs::incrementAndGet);
        Mockito.when(option.getControl()).thenReturn(control);
        assertSame(option, control.getOption());
        assertEquals(70, control.getMaxWidth());
        ControlElement<Boolean> element = control.createElement(DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(element.mouseClicked(ctx, 5, 5, 0));
        assertEquals(1, runs.get());
        assertFalse(element.mouseClicked(ctx, 5, 5, 1));
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains(">"));
        Mockito.when(option.isAvailable()).thenReturn(false);
        element.render(draw, 5, 5, 0f);
        assertFalse(element.mouseClicked(ctx, 5, 5, 0));
        ActionButtonControl labelled = new ActionButtonControl(option, TextComponent.literal("Go"), runs::incrementAndGet);
        labelled.createElement(DIM).render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("Go"));
        assertThrows(NullPointerException.class, () -> new ActionButtonControl(null, runs::incrementAndGet));
        assertThrows(NullPointerException.class, () -> new ActionButtonControl(option, null));
        assertThrows(NullPointerException.class, () -> new ActionButtonControl(option, null, runs::incrementAndGet));
    }

    @Test
    void readOnlyStringShowsTheValue() {
        Option<String> option = option("v1.2", true, false);
        ReadOnlyStringControl control = new ReadOnlyStringControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        assertSame(option, control.getOption());
        assertEquals(90, control.getMaxWidth());
        ControlElement<String> element = control.createElement(DIM);
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("v1.2"));
        Mockito.when(option.isAvailable()).thenReturn(false);
        element.render(draw, 5, 5, 0f);
        assertFalse(element.mouseClicked(new FakeInteractionContext(), 5, 5, 0));
    }

    @Test
    void formattersProduceTheExpectedText() {
        assertEquals("options.guiScale.auto", ControlValueFormatter.guiScale().format(0).toString());
        assertEquals("3x", ControlValueFormatter.guiScale().format(3).toString());
        assertEquals("options.framerateLimit.max", ControlValueFormatter.fpsLimit().format(260).toString());
        assertEquals("options.framerate[60]", ControlValueFormatter.fpsLimit().format(60).toString());
        assertEquals("options.gamma.min", ControlValueFormatter.brightness().format(0).toString());
        assertEquals("options.gamma.max", ControlValueFormatter.brightness().format(100).toString());
        assertEquals("50%", ControlValueFormatter.brightness().format(50).toString());
        assertEquals("gui.none", ControlValueFormatter.biomeBlend().format(0).toString());
        assertEquals("impetus.options.biome_blend.value[3]", ControlValueFormatter.biomeBlend().format(3).toString());
        assertEquals("key[7]", ControlValueFormatter.translateVariable("key").format(7).toString());
        assertEquals("7%", ControlValueFormatter.percentage().format(7).toString());
        assertEquals("7x", ControlValueFormatter.multiplier().format(7).toString());
        assertEquals("off", ControlValueFormatter.quantityOrDisabled("chunks", "off").format(0).toString());
        assertEquals("7 chunks", ControlValueFormatter.quantityOrDisabled("chunks", "off").format(7).toString());
        assertEquals("7", ControlValueFormatter.number().format(7).toString());
    }
}
