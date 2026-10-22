package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TickBoxControlTest {
    // The box from 184 to 194 over rows 4 to 14
    private static final Dim2i DIM = new Dim2i(0, 0, 200, 18);

    // A mock option holding a value that clicks and keys write back, available
    private static Option<Boolean> valued(Boolean initial) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class);
        Object[] holder = {initial};
        when(option.getValue()).thenAnswer(invocation -> holder[0]);
        Mockito.doAnswer(invocation -> holder[0] = invocation.getArgument(0)).when(option).setValue(any());
        when(option.getName()).thenReturn(TextComponent.literal("Tick"));
        when(option.isAvailable()).thenReturn(true);
        return option;
    }

    // A row at the bounds from a tick box bound to its option as the pages bind it
    private static ControlElement<Boolean> row(Option<Boolean> option, Dim2i dim) {
        TickBoxControl control = new TickBoxControl(option);
        when(option.getControl()).thenReturn(control);
        return control.createElement(dim);
    }

    @Test
    void aRowWithNoAreaOrTooFarOutToBeDrawnTakesNoClickOrKeyEvenWithTheCursorOnItsBoxOrFocusForcedOnIt() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, (1 << 30) + 1, 18), new Dim2i(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 18), new Dim2i(0, (1 << 30) - 10, 200, 18),
                new Dim2i(0, 0, 0, 18), new Dim2i(0, 0, -200, 18), new Dim2i(0, 0, 200, 0), new Dim2i(0, 0, 200, -18)}) {
            Option<Boolean> option = valued(false);
            ControlElement<Boolean> row = row(option, dim);
            clearInvocations(option);
            // Where the box would lie, 11 in from the right edge on the centre line, which the edges of a row too far out still hold
            double x = (double) dim.x() + dim.width() - 11;
            double y = dim.getCenterY();
            assertEquals(dim.width() > 0 && dim.height() > 0, dim.containsCursor(x, y), dim::toString);
            FakeInteractionContext context = new FakeInteractionContext();
            assertFalse(row.mouseClicked(context, x, y, 0), dim::toString);
            assertFalse(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertFalse(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertFalse(row.keyTyped(context, '\r', Keys.RETURN), dim::toString);
            assertFalse(row.keyTyped(context, '\0', Keys.NUMPAD_ENTER), dim::toString);
            verify(option, never()).isAvailable();
            verify(option, never()).getValue();
            verify(option, never()).setValue(any());
            assertEquals(0, context.clicks, dim::toString);
        }
    }

    @Test
    void aRowReachingExactlyToTheCoordinateLimitStillTogglesOnAClickAndAPress() {
        for (Dim2i dim : new Dim2i[]{new Dim2i(0, 0, 1 << 30, 18), new Dim2i(-(1 << 30), -(1 << 30), 200, 18), new Dim2i(0, (1 << 30) - 18, 200, 18)}) {
            Option<Boolean> option = valued(false);
            ControlElement<Boolean> row = row(option, dim);
            FakeInteractionContext context = new FakeInteractionContext();
            assertTrue(row.mouseClicked(context, (double) dim.x() + dim.width() - 11, dim.getCenterY(), 0), dim::toString);
            assertEquals(true, option.getValue(), dim::toString);
            assertTrue(row.canFocus(), dim::toString);
            row.setFocused(true);
            assertTrue(row.keyTyped(context, ' ', Keys.SPACE), dim::toString);
            assertEquals(false, option.getValue(), dim::toString);
            assertEquals(2, context.clicks, dim::toString);
        }
    }

    @Test
    void onlyALeftClickInsideOrAPressWhileFocusedAsksAboutTheOptionAndAnUnavailableOneTakesNeither() {
        Option<Boolean> option = valued(false);
        ControlElement<Boolean> row = row(option, DIM);
        FakeInteractionContext context = new FakeInteractionContext();
        clearInvocations(option);
        for (int button : new int[]{1, 2, -1, 7}) {
            assertFalse(row.mouseClicked(context, 189, 9, button), "button " + button);
        }
        // Just past each edge, the right and bottom edges themselves, and no place at all
        for (double[] at : new double[][]{{-0.01, 9}, {200, 9}, {189, -0.01}, {189, 18}, {Double.NaN, 9}, {189, Double.NaN}}) {
            assertFalse(row.mouseClicked(context, at[0], at[1], 0), at[0] + "," + at[1]);
        }
        // Unfocused, every press is someone else's
        assertFalse(row.keyTyped(context, ' ', Keys.SPACE));
        row.setFocused(true);
        // Letters, Tab by key and by character, Escape and both arrows
        char[] typed = {'a', '\t', '\0', '\0', '\0', '\0'};
        int[] codes = {30, 0, Keys.TAB, 1, Keys.LEFT, Keys.RIGHT};
        for (int i = 0; i < typed.length; i++) {
            assertFalse(row.keyTyped(context, typed[i], codes[i]), "key " + i);
        }
        verify(option, never()).isAvailable();
        verify(option, never()).getValue();
        when(option.isAvailable()).thenReturn(false);
        assertFalse(row.mouseClicked(context, 189, 9, 0));
        for (int code : new int[]{Keys.SPACE, Keys.RETURN, Keys.NUMPAD_ENTER}) {
            assertFalse(row.keyTyped(context, '\0', code), "code " + code);
        }
        verify(option, never()).getValue();
        verify(option, never()).setValue(any());
        assertEquals(0, context.clicks);
    }
}
