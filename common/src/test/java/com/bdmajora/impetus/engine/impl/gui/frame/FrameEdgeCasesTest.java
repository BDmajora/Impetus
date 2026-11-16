package com.bdmajora.impetus.engine.impl.gui.frame;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.Tab;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.TabFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

// Tests null handling and layout edge cases of the frames.
class FrameEdgeCasesTest {
    // Bounds shared by the tests.
    private static final Dim2i DIM = new Dim2i(0, 0, 100, 100);

    // Returns a frame with no children.
    private static AbstractFrame empty(Dim2i dim) {
        return BasicFrame.createBuilder().setDimension(dim).build();
    }

    // Rejects null bounds.
    @Test
    void framesRejectNullBounds() {
        assertThrows(NullPointerException.class, () -> new BasicFrame(null, false, List.of()));
        assertThrows(NullPointerException.class, () -> new ScrollableFrame(null, empty(DIM), false, null, null));
    }

    // Copies the factory list and rejects null factories and null children.
    @Test
    void basicFrameCopiesFactories() {
        List<Function<Dim2i, AbstractWidget>> factories = new ArrayList<>();
        factories.add(dim -> new FlatButtonWidget(dim, TextComponent.literal("a"), () -> {}));
        BasicFrame frame = new BasicFrame(DIM, false, factories);
        factories.add(dim -> new FlatButtonWidget(dim, TextComponent.literal("b"), () -> {}));
        frame.buildFrame();
        assertEquals(1, frame.interactableChildren().count());

        assertThrows(NullPointerException.class, () -> BasicFrame.createBuilder().addChild(null));
        List<Function<Dim2i, AbstractWidget>> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new BasicFrame(DIM, false, withNull));
        assertThrows(NullPointerException.class, () -> new BasicFrame(DIM, false, List.of(dim -> null)));
        assertThrows(NullPointerException.class, () -> new BasicFrame(DIM, false, null));
    }

    // Rebuilds without duplicating children.
    @Test
    void basicFrameRebuildIsIdempotent() {
        BasicFrame frame = BasicFrame.createBuilder().setDimension(DIM).addChild(dim -> new FlatButtonWidget(dim, TextComponent.literal("a"), () -> {})).build();
        frame.buildFrame();
        frame.buildFrame();
        assertEquals(1, frame.interactableChildren().count());
    }

    // Rejects a missing inner frame and tolerates null offset references.
    @Test
    void scrollableFrameNullArguments() {
        assertThrows(NullPointerException.class, () -> new ScrollableFrame(DIM, null, false, null, null));
        Dim2i viewport = new Dim2i(0, 0, 50, 50);
        AbstractFrame tall = empty(new Dim2i(0, 0, 200, 400));
        ScrollableFrame frame = new ScrollableFrame(viewport, tall, false, null, null);
        frame.render(new FakeDrawContext(), 1, 1, 0f);
        AtomicReference<Integer> nullValue = new AtomicReference<>(null);
        ScrollableFrame again = new ScrollableFrame(viewport, empty(new Dim2i(0, 0, 200, 400)), false, nullValue, nullValue);
        again.render(new FakeDrawContext(), 1, 1, 0f);
        assertTrue(again.isMouseOver(1, 1));
        assertFalse(again.isMouseOver(60, 1));
    }

    // Shows every option for a null filter and rejects a null page.
    @Test
    void optionPageFrameNullFilterAndPage() {
        OptionPage page = new OptionPage(OptionIdentifier.create("m", "p"), TextComponent.literal("Page"), List.of());
        OptionPageFrame frame = new OptionPageFrame(DIM, false, page, null);
        frame.render(new FakeDrawContext(), 0, 0, 0f);
        assertThrows(NullPointerException.class, () -> new OptionPageFrame(DIM, false, null, o -> true));
        assertEquals(18, frame.getDimensions().height());
    }

    // Rejects null arguments of the tab frame and a null tab.
    @Test
    void tabFrameNullArguments() {
        FakeDrawContext draw = new FakeDrawContext();
        assertThrows(NullPointerException.class, () -> new TabFrame(null, DIM, false, Map.of(), null, null, null));
        assertThrows(NullPointerException.class, () -> new TabFrame(draw, DIM, false, null, null, null, null));
        TabFrame frame = new TabFrame(draw, DIM, false, Map.of(), null, null, null);
        assertThrows(NullPointerException.class, () -> frame.setTab(null));
        frame.render(draw, 0, 0, 0f);
    }

    // Keeps the page area non negative when the labels are wider than the frame.
    @Test
    void narrowFrameKeepsPageAreaNonNegative() {
        FakeDrawContext draw = new FakeDrawContext();
        OptionPage page = new OptionPage(OptionIdentifier.create("impetus", "very long page name that never fits"), TextComponent.literal("very long page name that never fits"), List.of());
        Tab<?> tab = Tab.from(page, o -> true, new AtomicReference<>(0));
        TabFrame frame = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 10, 50)).addTabs(map -> map.put("impetus", List.of(tab))).build(draw);
        frame.render(draw, 0, 0, 0f);
        assertTrue(frame.getDimensions().width() >= 0);
    }

    // Returns null from a tab without a frame factory.
    @Test
    void tabWithoutFactoryCreatesNoFrame() {
        Tab<AbstractFrame> tab = Tab.<AbstractFrame>builder().setTitle(TextComponent.literal("t")).build();
        assertNull(tab.createFrame(DIM));
    }

    // Rebuilds a plain frame without duplicating drawables and keeps the children.
    @Test
    void abstractFrameRebuildReplacesDerivedLists() {
        int[] draws = {0};
        AbstractWidget child = new FlatButtonWidget(DIM, TextComponent.literal("a"), () -> {}) {
            @Override
            public void render(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext, int mouseX, int mouseY, float delta) {
                draws[0]++;
            }
        };
        AbstractFrame frame = new AbstractFrame(DIM, false) { };
        frame.children.add(child);
        for (int i = 0; i < 3; i++) {
            frame.buildFrame();
        }
        assertEquals(1, frame.drawable.size());
        assertEquals(1, frame.interactableChildren().count());
        frame.render(new FakeDrawContext(), 0, 0, 0f);
        assertEquals(1, draws[0]);
        frame.children.clear();
        frame.buildFrame();
        assertTrue(frame.drawable.isEmpty());
        assertTrue(frame.controlElements.isEmpty());
        assertSame(DIM, frame.getDimensions());
        assertNull(frame.getCapturedChild());
        frame.setCapturedChild(child);
        assertSame(child, frame.getCapturedChild());
    }

    // Keeps the inner bounds and bars stable across repeated setup calls.
    @Test
    void scrollableSetupIsRepeatable() {
        AbstractFrame inner = empty(new Dim2i(0, 0, 100, 400));
        ScrollableFrame frame = new ScrollableFrame(DIM, inner, false, null, null);
        Dim2i narrowed = inner.getDimensions();
        assertEquals(89, narrowed.width());
        AtomicReference<Integer> vertical = new AtomicReference<>(5);
        for (int i = 0; i < 3; i++) {
            frame.setupFrame(vertical, new AtomicReference<>(0));
            assertEquals(narrowed, inner.getDimensions());
        }
        frame.buildFrame();
        assertEquals(1, frame.interactableChildren().count());
    }

    // Pops the transform and the scissor when a child throws a fatal error.
    @Test
    void scrollableRenderRestoresDrawStateOnFailure() {
        AbstractWidget thrower = new FlatButtonWidget(new Dim2i(0, 0, 100, 400), TextComponent.literal("x"), () -> {}) {
            @Override
            public void render(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext, int mouseX, int mouseY, float delta) {
                throw new StackOverflowError("draw");
            }
        };
        AbstractFrame inner = BasicFrame.createBuilder().setDimension(new Dim2i(0, 0, 100, 400)).addChild(dim -> thrower).build();
        ScrollableFrame frame = new ScrollableFrame(DIM, inner, true, null, null);
        FakeDrawContext draw = new FakeDrawContext();
        assertThrows(StackOverflowError.class, () -> frame.render(draw, 10, 10, 0f));
        assertEquals(0, draw.matrixDepth);
        assertEquals(0, draw.scissorDepth);
    }

    // Draws a frame that fits without a viewport or bars.
    @Test
    void fittingScrollableFrameHasNoBars() {
        ScrollableFrame frame = new ScrollableFrame(DIM, empty(new Dim2i(0, 0, 50, 50)), true, null, null);
        FakeDrawContext draw = new FakeDrawContext();
        frame.render(draw, 0, 0, 0f);
        assertEquals(0, draw.scissorDepth);
        assertTrue(frame.isMouseOver(0, 0));
        assertFalse(frame.isMouseOver(-1, 0));
        assertFalse(frame.isMouseOver(100, 100));
    }
}
