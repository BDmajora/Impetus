package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FramesTest {
    private static Option<Boolean> option(String name, String mod) {
        @SuppressWarnings("unchecked")
        Option<Boolean> option = Mockito.mock(Option.class);
        Mockito.when(option.getValue()).thenReturn(true);
        Mockito.when(option.isAvailable()).thenReturn(true);
        Mockito.when(option.getName()).thenReturn(TextComponent.literal(name));
        Mockito.when(option.getTooltip()).thenReturn(TextComponent.literal("tooltip text for " + name));
        Mockito.when(option.getImpact()).thenReturn(OptionImpact.MEDIUM);
        Mockito.when(option.getId()).thenReturn(OptionIdentifier.create(mod, name, Boolean.class));
        Mockito.when(option.getControl()).thenReturn(new TickBoxControl(option));
        return option;
    }

    private static OptionPage page(String mod, boolean named) {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, "g")).add(option("alpha", mod)).add(option("beta", "otherMod"));
        if (named) {
            group.setName(TextComponent.literal("Heading"));
        }
        OptionGroup empty = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, "empty")).build();
        return new OptionPage(OptionIdentifier.create(mod, "page"), TextComponent.literal("Page"), List.of(empty, group.build(), OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, "g2")).add(option("gamma", mod)).build()));
    }

    @Test
    void basicFrameBuildsChildrenFromFactories() {
        AtomicInteger runs = new AtomicInteger();
        BasicFrame frame = BasicFrame.createBuilder()
                .setDimension(new Dim2i(0, 0, 100, 100))
                .shouldRenderOutline(true)
                .addChild(dim -> new FlatButtonWidget(dim, TextComponent.literal("b"), runs::incrementAndGet))
                .addChild(dim -> option("x", "m").getControl().createElement(dim))
                .build();
        assertEquals(new Dim2i(0, 0, 100, 100), frame.getDimensions());
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("b"));
        assertTrue(frame.mouseClicked(new FakeInteractionContext(), 5, 5, 0));
        assertEquals(1, runs.get());
        assertEquals(2, frame.interactableChildren().count());
        assertEquals(1, frame.controlElements.size());
        assertThrows(NullPointerException.class, () -> BasicFrame.createBuilder().build());
        BasicFrame nested = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 10, 10)).addChild(d -> frame).build();
        assertEquals(1, nested.controlElements.size());
    }

    @Test
    void optionPageFrameLaysOutGroupsAndShowsTooltips() throws ReflectiveOperationException {
        OptionPageFrame frame = OptionPageFrame.createBuilder()
                .setDimension(new Dim2i(0, 0, 200, 100))
                .setOptionPage(page("impetus", true))
                .setOptionFilter(o -> !o.getName().toString().equals("gamma"))
                .shouldRenderOutline(true)
                .build();
        // header 18 + heading 14 + two rows of 18
        assertEquals(68, frame.getDimensions().height());
        assertEquals(2, frame.controlElements.size());
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, 500, 500, 0f);
        assertTrue(draw.strings.contains("Heading"));
        assertTrue(draw.strings.contains("Page"));
        ControlElement<?> second = frame.controlElements.get(1);
        int mx = second.getDimensions().getCenterX();
        int my = second.getDimensions().getCenterY();
        frame.render(draw, mx, my, 0f);
        frame.render(draw, mx, my, 0f);
        assertFalse(draw.strings.stream().anyMatch(s -> s.startsWith("tooltip")));
        var lastTime = OptionPageFrame.class.getDeclaredField("lastTime");
        lastTime.setAccessible(true);
        lastTime.setLong(frame, 1L);
        frame.render(draw, mx, my, 0f);
        assertTrue(draw.strings.stream().anyMatch(s -> s.startsWith("tooltip")));
        assertTrue(draw.strings.stream().anyMatch(s -> s.startsWith("impetus.options.added_by_mod_string")));
        assertTrue(draw.strings.stream().anyMatch(s -> s.startsWith("impetus.options.performance_impact_string")));
        ControlElement<?> first = frame.controlElements.get(0);
        frame.render(draw, first.getDimensions().getCenterX(), first.getDimensions().getCenterY(), 0f);
        frame.render(draw, first.getDimensions().getCenterX(), first.getDimensions().getCenterY(), 0f);
        lastTime.setLong(frame, 1L);
        draw.strings.clear();
        frame.render(draw, first.getDimensions().getCenterX(), first.getDimensions().getCenterY(), 0f);
        assertFalse(draw.strings.stream().anyMatch(s -> s.startsWith("impetus.options.added_by_mod_string")));

        OptionPageFrame unnamed = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page("minecraft", false), o -> true);
        assertEquals(18 + 36 + 4 + 18, unnamed.getDimensions().height());
        assertEquals(3, unnamed.controlElements.size());
        ControlElement<?> low = unnamed.controlElements.get(2);
        unnamed.render(draw, low.getDimensions().getCenterX(), low.getDimensions().getCenterY(), 0f);
        unnamed.render(draw, low.getDimensions().getCenterX(), low.getDimensions().getCenterY(), 0f);
        lastTime.setLong(unnamed, 1L);
        unnamed.render(draw, low.getDimensions().getCenterX(), low.getDimensions().getCenterY(), 0f);
        Option<Boolean> impactless = option("delta", "m");
        Mockito.when(impactless.getImpact()).thenReturn(null);
        Mockito.when(impactless.getId()).thenReturn(null);
        OptionPage single = new OptionPage(OptionIdentifier.create("m", "single"), TextComponent.literal("S"), List.of(OptionGroup.createBuilder().add(impactless).build()));
        OptionPageFrame tiny = new OptionPageFrame(new Dim2i(0, 0, 200, 10), false, single, o -> true);
        ControlElement<?> only = tiny.controlElements.get(0);
        tiny.render(draw, only.getDimensions().getCenterX(), only.getDimensions().getCenterY(), 0f);
        tiny.render(draw, only.getDimensions().getCenterX(), only.getDimensions().getCenterY(), 0f);
        lastTime.setLong(tiny, 1L);
        tiny.render(draw, only.getDimensions().getCenterX(), only.getDimensions().getCenterY(), 0f);
        OptionPageFrame unfiltered = OptionPageFrame.createBuilder().setDimension(new Dim2i(0, 0, 200, 300)).setOptionPage(single).build();
        assertEquals(1, unfiltered.controlElements.size());
        assertThrows(NullPointerException.class, () -> OptionPageFrame.createBuilder().build());
        assertThrows(NullPointerException.class, () -> OptionPageFrame.createBuilder().setDimension(new Dim2i(0, 0, 1, 1)).build());
    }

    @Test
    void theImpactLineIsOneComponentPerImpactThatFollowsTheOptionsImpact() throws ReflectiveOperationException {
        Option<Boolean> option = option("rated", "impetus");
        OptionPage page = new OptionPage(OptionIdentifier.create("impetus", "rated_page"), TextComponent.literal("P"), List.of(OptionGroup.createBuilder().add(option).build()));
        OptionPageFrame frame = new OptionPageFrame(new Dim2i(0, 0, 200, 300), false, page, o -> true);
        ControlElement<?> row = frame.controlElements.get(0);
        int mx = row.getDimensions().getCenterX();
        int my = row.getDimensions().getCenterY();
        List<TextComponent> drawn = new ArrayList<>();
        FakeDrawContext draw = new FakeDrawContext() {
            @Override
            public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
                drawn.add(str);
                return super.drawString(str, x, y, color, shadow);
            }
        };
        frame.render(draw, mx, my, 0f);
        frame.render(draw, mx, my, 0f);
        var lastTime = OptionPageFrame.class.getDeclaredField("lastTime");
        lastTime.setAccessible(true);
        lastTime.setLong(frame, 1L);
        List<TextComponent> lines = new ArrayList<>();
        // The same row's impact changes between frames, and each frame shows the current one
        for (OptionImpact impact : List.of(OptionImpact.HIGH, OptionImpact.LOW, OptionImpact.VARIES, OptionImpact.MEDIUM, OptionImpact.HIGH)) {
            Mockito.when(option.getImpact()).thenReturn(impact);
            for (int frameNo = 0; frameNo < 2; frameNo++) {
                drawn.clear();
                frame.render(draw, mx, my, 0f);
                List<TextComponent> impactLines = drawn.stream().filter(c -> c.toString().startsWith("impetus.options.performance_impact_string")).toList();
                assertEquals(1, impactLines.size(), impact.name());
                TextComponent line = impactLines.getFirst();
                assertEquals(TextComponent.translatable("impetus.options.performance_impact_string", impact.getLocalizedName()).withStyle(TextFormattingStyle.GRAY), line);
                lines.add(line);
            }
        }
        // Every frame of one impact draws the very same line, and the impacts never share one
        assertSame(lines.get(0), lines.get(1));
        assertSame(lines.get(0), lines.get(8));
        assertSame(lines.get(0), lines.get(9));
        assertEquals(OptionImpact.values().length, lines.stream().map(System::identityHashCode).distinct().count());
        // A label someone changed after taking it from an impact never reaches the line
        ((TextComponent.Styled) OptionImpact.MEDIUM.getLocalizedName()).styles().add(TextFormattingStyle.RED);
        Mockito.when(option.getImpact()).thenReturn(OptionImpact.MEDIUM);
        drawn.clear();
        frame.render(draw, mx, my, 0f);
        assertTrue(drawn.contains(TextComponent.translatable("impetus.options.performance_impact_string", OptionImpact.MEDIUM.getLocalizedName()).withStyle(TextFormattingStyle.GRAY)));
    }

    @Test
    void scrollableFrameScrollsOnlyOverflowingAxes() {
        AtomicReference<Integer> vertical = new AtomicReference<>(0);
        AtomicReference<Integer> horizontal = new AtomicReference<>(0);
        FakeInteractionContext ctx = new FakeInteractionContext();
        FakeDrawContext draw = new FakeDrawContext();

        BasicFrame tall = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 400)).addChild(d -> new FlatButtonWidget(d, TextComponent.literal("t"), () -> {})).build();
        ScrollableFrame verticalOnly = ScrollableFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).setFrame(tall).setVerticalScrollBarOffset(vertical).setHorizontalScrollBarOffset(horizontal).shouldRenderOutline(true).setScrollBarAccentColor(0xFF112233).build();
        verticalOnly.render(draw, 5, 5, 0f);
        assertEquals(0, draw.scissorDepth);
        assertTrue(draw.drew("scissor"));
        assertTrue(verticalOnly.mouseScrolled(ctx, 5, 5, 0, -1));
        assertEquals(18, vertical.get());
        assertTrue(verticalOnly.mouseClicked(ctx, 95, 95, 0));
        assertEquals(300, vertical.get());
        assertTrue(verticalOnly.mouseClicked(ctx, 95, 85, 0));
        assertTrue(verticalOnly.mouseDragged(ctx, 95, 0, 0, 0, 0));
        assertEquals(0, vertical.get());
        assertFalse(verticalOnly.mouseReleased(ctx, 95, 5, 0));
        assertTrue(verticalOnly.isMouseOver(5, 5));
        assertFalse(verticalOnly.isMouseOver(500, 5));
        verticalOnly.render(draw, 500, 500, 0f);

        BasicFrame wide = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 100)).addChild(d -> new FlatButtonWidget(d, TextComponent.literal("w"), () -> {})).build();
        ScrollableFrame horizontalOnly = new ScrollableFrame(new Dim2i(0, 0, 100, 100), wide, false, new AtomicReference<>(0), new AtomicReference<>(0));
        horizontalOnly.render(draw, 5, 5, 0f);
        assertTrue(horizontalOnly.mouseScrolled(ctx, 5, 95, 0, -1));
        assertTrue(horizontalOnly.mouseClicked(ctx, 95, 95, 0));
        assertTrue(horizontalOnly.mouseClicked(ctx, 90, 95, 0));
        assertTrue(horizontalOnly.mouseDragged(ctx, 5, 95, 0, 0, 0));
        assertFalse(horizontalOnly.mouseReleased(ctx, 5, 95, 0));

        BasicFrame big = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 400)).addChild(d -> new FlatButtonWidget(d, TextComponent.literal("b"), () -> {})).build();
        ScrollableFrame both = new ScrollableFrame(new Dim2i(0, 0, 100, 100), big, false, new AtomicReference<>(0), new AtomicReference<>(0));
        both.render(draw, 5, 5, 0f);
        assertTrue(both.mouseClicked(ctx, 5, 5, 0));

        BasicFrame small = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 50, 50)).addChild(d -> new FlatButtonWidget(d, TextComponent.literal("s"), () -> {})).build();
        ScrollableFrame none = new ScrollableFrame(new Dim2i(0, 0, 100, 100), small, true, new AtomicReference<>(0), new AtomicReference<>(0));
        none.render(draw, 5, 5, 0f);
        assertTrue(none.mouseClicked(ctx, 5, 5, 0));
        assertFalse(none.mouseScrolled(ctx, 5, 5, 0, 1));
        assertFalse(none.mouseDragged(ctx, 5, 5, 0, 0, 0));
        // The button that took the click is handed the release, wherever the cursor ends up
        assertTrue(none.mouseReleased(ctx, 5, 5, 0));
        assertFalse(none.mouseReleased(ctx, 5, 5, 0));
    }

    private static FlatButtonWidget button(Dim2i dim) {
        return new FlatButtonWidget(dim, TextComponent.literal("b"), () -> {});
    }

    @Test
    void aTabStepIntoAScrollingFrameScrollsTheLeastThatShowsTheFocusedControl() {
        AtomicReference<Integer> vertical = new AtomicReference<>(0);
        // A 400 tall frame seen through a 100 tall view; the third button is taller than the view and the hidden one is never focused
        FlatButtonWidget top = button(new Dim2i(0, 0, 80, 20));
        FlatButtonWidget middle = button(new Dim2i(0, 150, 80, 20));
        FlatButtonWidget tall = button(new Dim2i(0, 200, 80, 150));
        FlatButtonWidget hidden = button(new Dim2i(0, 360, 80, 20));
        hidden.setVisible(false);
        FlatButtonWidget bottom = button(new Dim2i(0, 380, 80, 20));
        BasicFrame content = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 400))
                .addChild(d -> top).addChild(d -> middle).addChild(d -> tall).addChild(d -> hidden).addChild(d -> bottom).build();
        ScrollableFrame frame = ScrollableFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).setFrame(content).setVerticalScrollBarOffset(vertical).build();

        // Already in view; brought up from below by its bottom edge; the tall one by its top edge; the last one by the bottom, at the end of the range
        List<FlatButtonWidget> order = List.of(top, middle, tall, bottom);
        int[] offsets = {0, 70, 200, 300};
        for (int i = 0; i < order.size(); i++) {
            assertTrue(frame.moveFocus(true));
            assertSame(order.get(i), frame.getFocusedControl());
            assertEquals(offsets[i], vertical.get());
        }
        assertFalse(frame.moveFocus(true));
        assertEquals(300, vertical.get());
        // Backwards each comes down by its top edge, and one already in view leaves the scroll alone
        assertTrue(frame.moveFocus(false));
        assertEquals(300, vertical.get());
        int[] back = {200, 150, 0};
        for (int i = 0; i < back.length; i++) {
            assertTrue(frame.moveFocus(false));
            assertSame(order.get(2 - i), frame.getFocusedControl());
            assertEquals(back[i], vertical.get());
        }
        // Off the start nothing is focused, the next step comes back in at the top, and the frame draws the content shifted by the offset it scrolled to
        assertFalse(frame.moveFocus(false));
        assertNull(frame.getFocusedControl());
        assertTrue(frame.moveFocus(true));
        assertTrue(frame.moveFocus(true));
        assertEquals(70, vertical.get());
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, 500, 500, 0f);
        assertTrue(draw.drew("translate(0.0,-70.0,0.0)"));

        // Sideways the same, on the horizontal bar
        AtomicReference<Integer> horizontal = new AtomicReference<>(0);
        FlatButtonWidget left = button(new Dim2i(0, 0, 40, 20));
        FlatButtonWidget right = button(new Dim2i(300, 0, 40, 20));
        BasicFrame wide = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 100)).addChild(d -> left).addChild(d -> right).build();
        ScrollableFrame sideways = ScrollableFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).setFrame(wide).setHorizontalScrollBarOffset(horizontal).build();
        assertTrue(sideways.moveFocus(true));
        assertEquals(0, horizontal.get());
        assertTrue(sideways.moveFocus(true));
        assertEquals(240, horizontal.get());
        assertTrue(sideways.moveFocus(false));
        assertEquals(0, horizontal.get());

        // A frame that fits has nothing to scroll, and one with nothing focusable is passed through
        FlatButtonWidget only = button(new Dim2i(0, 0, 40, 20));
        ScrollableFrame still = new ScrollableFrame(new Dim2i(0, 0, 100, 100), BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 50, 50)).addChild(d -> only).build(), false, new AtomicReference<>(0), new AtomicReference<>(0));
        assertTrue(still.moveFocus(true));
        assertTrue(only.isFocused());
        assertFalse(still.moveFocus(true));
        assertFalse(only.isFocused());
        AtomicReference<Integer> unmoved = new AtomicReference<>(0);
        ScrollableFrame empty = ScrollableFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 100)).setFrame(BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 400)).addChild(d -> hidden).build()).setVerticalScrollBarOffset(unmoved).build();
        assertFalse(empty.moveFocus(true));
        assertFalse(empty.moveFocus(false));
        assertEquals(0, unmoved.get());
    }

    @Test
    void anOptionPageIsWalkedRowByRowAndSkipsUnavailableOnes() {
        OptionPage page = page("impetus", true);
        Option<?> beta = page.getOptions().stream().filter(o -> o.getName().toString().equals("beta")).findFirst().orElseThrow();
        Mockito.when(beta.isAvailable()).thenReturn(false);
        OptionPageFrame frame = OptionPageFrame.createBuilder().setDimension(new Dim2i(0, 0, 200, 300)).setOptionPage(page).build();
        List<String> names = new ArrayList<>();
        while (frame.moveFocus(true)) {
            names.add(((ControlElement<?>) frame.getFocusedControl()).getOption().getName().toString());
        }
        assertEquals(List.of("alpha", "gamma"), names);
        // The focused row is drawn outlined in its mod's accent over the hovered background
        assertTrue(frame.moveFocus(true));
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, 500, 500, 0f);
        ControlElement<?> alpha = frame.controlElements.getFirst();
        Dim2i row = alpha.getDimensions();
        assertTrue(draw.drew("fill(" + row.x() + "," + row.y() + "," + row.getLimitX() + "," + (row.y() + 1) + "," + Integer.toHexString(draw.getModAccentColor("impetus")) + ")"));
    }
}
