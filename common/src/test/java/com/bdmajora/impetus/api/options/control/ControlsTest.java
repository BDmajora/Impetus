package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ControlsTest {
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 20);

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
        // Nothing to press or change, so Tab passes the row over even while the option is available
        Mockito.when(option.isAvailable()).thenReturn(true);
        assertFalse(element.canFocus());
    }

    @Test
    void aFocusedTickBoxTogglesOnEnterOrSpaceAndIsOutlined() {
        Option<Boolean> option = option(false, true, false);
        TickBoxControl control = new TickBoxControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        ControlElement<Boolean> element = control.createElement(DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(element.keyTyped(ctx, ' ', Keys.SPACE));
        assertEquals(false, option.getValue());
        assertTrue(element.canFocus());

        element.setFocused(true);
        assertTrue(element.keyTyped(ctx, ' ', Keys.SPACE));
        assertEquals(true, option.getValue());
        assertTrue(element.keyTyped(ctx, '\r', Keys.RETURN));
        assertTrue(element.keyTyped(ctx, '\0', Keys.NUMPAD_ENTER));
        assertEquals(true, option.getValue());
        assertEquals(3, ctx.clicks);
        assertFalse(element.keyTyped(ctx, 'a', 30));
        assertFalse(element.keyTyped(ctx, '\0', Keys.LEFT));
        assertFalse(element.keyTyped(ctx, '\t', Keys.TAB));

        // Drawn hovered and outlined in the accent though the cursor is elsewhere
        FakeDrawContext draw = new FakeDrawContext();
        element.render(draw, 500, 500, 0f);
        String accent = Integer.toHexString(DefaultColors.ELEMENT_ACTIVATED);
        assertEquals("fill(0,0,200,20," + Integer.toHexString(DefaultColors.BACKGROUND_HOVERED) + ")", draw.calls.getFirst());
        assertEquals("fill(0,0,200,1," + accent + ")", draw.calls.get(1));

        // An unavailable option ignores presses and Tab passes its row over
        Mockito.when(option.isAvailable()).thenReturn(false);
        assertFalse(element.canFocus());
        assertFalse(element.keyTyped(ctx, ' ', Keys.SPACE));
        assertEquals(true, option.getValue());
        assertEquals(3, ctx.clicks);
    }

    @Test
    void aFocusedSliderShowsItsTrackAndStepsOneIntervalPerArrowStoppingAtTheEnds() {
        Option<Integer> option = option(4, true, false);
        SliderControl control = new SliderControl(option, 0, 10, 2, ControlValueFormatter.number());
        Mockito.when(option.getControl()).thenReturn(control);
        ControlElement<Integer> element = control.createElement(DIM);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertFalse(element.keyTyped(ctx, '\0', Keys.RIGHT));
        assertEquals(4, option.getValue());

        // The track runs x 104..194 on line 9 and the thumb for 4 of 0..10 sits at x 138, both drawn only when the slider is hovered, held or focused
        FakeDrawContext idle = new FakeDrawContext();
        element.render(idle, 500, 500, 0f);
        assertFalse(idle.drew("fill(104,9,194,10,"));
        element.setFocused(true);
        FakeDrawContext focused = new FakeDrawContext();
        element.render(focused, 500, 500, 0f);
        assertTrue(focused.drew("fill(104,9,194,10,"));
        assertTrue(focused.drew("fill(138,5,142,15,"));

        for (int expected : new int[]{6, 8, 10, 10}) {
            assertTrue(element.keyTyped(ctx, '\0', Keys.RIGHT));
            assertEquals(expected, option.getValue());
        }
        for (int expected : new int[]{8, 6, 4, 2, 0, 0}) {
            assertTrue(element.keyTyped(ctx, '\0', Keys.LEFT));
            assertEquals(expected, option.getValue());
        }
        // A step past either end writes nothing
        Mockito.verify(option, Mockito.times(8)).setValue(Mockito.any());
        // Presses and other keys are not the slider's, and it never plays the click
        assertFalse(element.keyTyped(ctx, ' ', Keys.SPACE));
        assertFalse(element.keyTyped(ctx, '\r', Keys.RETURN));
        assertFalse(element.keyTyped(ctx, '\t', Keys.TAB));
        assertEquals(0, ctx.clicks);

        Mockito.when(option.isAvailable()).thenReturn(false);
        assertFalse(element.keyTyped(ctx, '\0', Keys.RIGHT));
        assertEquals(0, option.getValue());
        // An unavailable slider shows only its value, focused or not
        FakeDrawContext unavailable = new FakeDrawContext();
        element.render(unavailable, 500, 500, 0f);
        assertFalse(unavailable.drew("fill(104,9,194,10,"));
    }

    // The strings a tick box row draws, which are only its name since the box itself is fills; the box takes 30, so the name gets the width less 42
    private static java.util.List<String> nameDrawnAt(int width, String name, boolean available, boolean changed) {
        Option<Boolean> option = option(true, available, changed);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal(name));
        TickBoxControl control = new TickBoxControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        FakeDrawContext draw = new FakeDrawContext();
        control.createElement(new Dim2i(0, 0, width, 20)).render(draw, 0, 0, 0f);
        return draw.strings;
    }

    @Test
    void aNameThatFitsExactlyIsDrawnWholeAndOnePixelLessCutsIt() {
        // 15 characters, 90 pixels
        assertEquals(java.util.List.of("Render distance"), nameDrawnAt(132, "Render distance", true, false));
        // 89 less the 18 pixel ellipsis leaves 71, eleven characters
        assertEquals(java.util.List.of("Render dist..."), nameDrawnAt(131, "Render distance", true, false));
        assertEquals(java.util.List.of("Name"), nameDrawnAt(66, "Name", true, false));
    }

    @Test
    void thePendingChangeMarkerIsCountedInTheRoomAndKeptAfterTheEllipsis() {
        assertEquals(java.util.List.of("Name *"), nameDrawnAt(78, "Name", true, true));
        // One pixel less and the name alone would still fit, but not with its marker, so the name gives way and the marker stays
        assertEquals(java.util.List.of("... *"), nameDrawnAt(77, "Name", true, true));
        // 90 less the 30 pixel "... *" leaves 60, ten characters
        assertEquals(java.util.List.of("Render dis... *"), nameDrawnAt(132, "Render distance", true, true));
    }

    @Test
    void aRowWithNoRoomForTheEllipsisDrawsNoNameAtAll() {
        assertEquals(java.util.List.of("..."), nameDrawnAt(60, "Name", true, false));
        assertEquals(java.util.List.of(), nameDrawnAt(59, "Name", true, false));
        assertEquals(java.util.List.of("... *"), nameDrawnAt(72, "Name", true, true));
        assertEquals(java.util.List.of(), nameDrawnAt(71, "Name", true, true));
        for (int width : new int[]{42, 30, 0, -10}) {
            assertEquals(java.util.List.of(), nameDrawnAt(width, "Name", true, true), () -> "width " + width);
        }
        // An empty name draws nothing even with room to spare
        assertEquals(java.util.List.of(), nameDrawnAt(200, "", true, false));
        assertEquals(java.util.List.of(" *"), nameDrawnAt(200, "", true, true));
    }

    @Test
    void aCutNameDropsTheSpacesItEndsOn() {
        // 48 less the ellipsis leaves 30, five characters: "Ab   "
        assertEquals(java.util.List.of("Ab..."), nameDrawnAt(90, "Ab   cdefghijk", true, false));
        assertEquals(java.util.List.of("..."), nameDrawnAt(90, " ".repeat(20), true, false));
    }

    @Test
    void anUnavailableNameIsCutTheSameWayWithoutAskingAboutChanges() {
        Option<Boolean> option = option(true, false, true);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal("Render distance"));
        TickBoxControl control = new TickBoxControl(option);
        Mockito.when(option.getControl()).thenReturn(control);
        FakeDrawContext draw = new FakeDrawContext();
        control.createElement(new Dim2i(0, 0, 131, 20)).render(draw, 0, 0, 0f);
        assertEquals(java.util.List.of("Render dist..."), draw.strings);
        Mockito.verify(option, Mockito.never()).hasChanged();
    }

    @Test
    void theNameNeverReachesTheControlsColumnAtAnyWidth() {
        String[] names = {"", "N", "Name", "Render distance", "A very long option name that will not fit in the row", "a    b"};
        for (int width = -10; width <= 260; width++) {
            for (String name : names) {
                for (boolean changed : new boolean[]{false, true}) {
                    for (boolean available : new boolean[]{false, true}) {
                        java.util.List<String> drawn = nameDrawnAt(width, name, available, changed);
                        assertTrue(drawn.size() <= 1);
                        if (!drawn.isEmpty()) {
                            int w = width;
                            // Starts 6 in and must end 6 short of the 30 pixel box column
                            assertTrue(6 + drawn.getFirst().length() * 6 <= w - 30 - 6, () -> w + " " + drawn);
                        }
                    }
                }
            }
        }
    }
}
