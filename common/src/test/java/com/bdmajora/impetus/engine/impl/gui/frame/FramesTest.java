package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        assertFalse(none.mouseReleased(ctx, 5, 5, 0));
    }
}
