package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ControlsTest {
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
