package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.frame.AbstractFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.BasicFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.ScrollableFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.Focusable;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.Keys;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// Tests tabs and the tab frame and the header widget.
class TabsTest {
    // Returns a page with one group of tick box rows.
    private static OptionPage page(String mod, String name, int rows) {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, name + "g"));
        for (int i = 0; i < rows; i++) {
            @SuppressWarnings("unchecked")
            Option<Boolean> option = Mockito.mock(Option.class);
            Mockito.when(option.getValue()).thenReturn(false);
            Mockito.when(option.isAvailable()).thenReturn(true);
            Mockito.when(option.getName()).thenReturn(TextComponent.literal(name + i));
            Mockito.when(option.getControl()).thenReturn(new TickBoxControl(option));
            group.add(option);
        }
        return new OptionPage(OptionIdentifier.create(mod, name), TextComponent.literal(name), List.of(group.build()));
    }

    // Builds a scrollable option frame from a page.
    @Test
    void tabFromPageBuildsAScrollableOptionFrame() {
        OptionPage page = page("impetus", "General", 30);
        AtomicReference<Integer> offset = new AtomicReference<>(0);
        Tab<ScrollableFrame> tab = Tab.from(page, o -> true, offset);
        assertSame(page, tab.page());
        assertEquals("General", tab.title().toString());
        assertSame(page.getId(), tab.id());
        assertSame(offset, tab.verticalScrollBarOffset());
        assertNotNull(tab.optionFilter());
        assertNull(tab.onSelectFunction());
        ScrollableFrame frame = tab.createFrame(new Dim2i(0, 0, 200, 100));
        assertNotNull(frame);
        assertTrue(frame.mouseScrolled(new FakeInteractionContext(), 5, 5, 0, -1));
        assertEquals(18, offset.get());
        Tab<?> bare = Tab.createBuilder().setTitle(TextComponent.literal("x")).build();
        assertNull(bare.createFrame(new Dim2i(0, 0, 1, 1)));
        assertEquals(tab, tab);
        assertNotEquals(tab, bare);
        assertNotNull(tab.toString());
        assertEquals(tab.hashCode(), tab.hashCode());
    }

    // Selects and folds groups and draws the frames.
    @Test
    void tabFrameSelectsFoldsAndRenders() {
        FakeDrawContext draw = new FakeDrawContext();
        AtomicInteger switches = new AtomicInteger();
        AtomicReference<TextComponent> selected = new AtomicReference<>(null);
        AtomicReference<Integer> sidebarOffset = new AtomicReference<>(0);
        Tab<ScrollableFrame> general = Tab.from(page("impetus", "General", 2), o -> true, new AtomicReference<>(0));
        Tab<ScrollableFrame> quality = Tab.from(page("impetus", "Quality", 2), o -> true, new AtomicReference<>(0));
        Tab<ScrollableFrame> other = Tab.from(page("extras", "Extras", 1), o -> true, new AtomicReference<>(0));
        Tab<AbstractFrame> refused = Tab.<AbstractFrame>builder().setTitle(TextComponent.literal("Refused")).setId(OptionIdentifier.create("extras", "refused")).setOnSelectFunction(() -> false).setFrameFunction(d -> BasicFrame.createBuilder().setDimension(d).build()).build();
        TabFrame frame = TabFrame.createBuilder()
                .setDimension(new Dim2i(0, 0, 400, 300))
                .shouldRenderOutline(false)
                .addTabs(map -> {
                    map.put("impetus", new ArrayList<>(List.of(general, quality)));
                    map.put("extras", new ArrayList<>(List.of(other, refused)));
                })
                .onSetTab(switches::incrementAndGet)
                .setTabSectionSelectedTab(selected)
                .setTabSectionScrollBarOffset(sidebarOffset)
                .build(draw);
        frame.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("impetus"));
        assertTrue(draw.strings.contains("General"));
        assertTrue(draw.strings.contains("Quality"));
        assertTrue(draw.strings.contains("-"));

        FakeInteractionContext ctx = new FakeInteractionContext();
        // Rows: impetus header 0-30, General 30-48, Quality 48-66, extras header 66-96, Extras 96-114, Refused 114-132
        assertTrue(frame.mouseClicked(ctx, 10, 57, 0));
        assertEquals("Quality", selected.get().toString());
        assertEquals(1, switches.get());
        assertTrue(frame.mouseClicked(ctx, 10, 123, 0));
        assertEquals("Quality", selected.get().toString());
        assertTrue(frame.mouseClicked(ctx, 10, 80, 0));
        draw.strings.clear();
        frame.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("+"));
        assertFalse(draw.strings.contains("Extras"));
        assertTrue(frame.mouseClicked(ctx, 10, 80, 0));
        draw.strings.clear();
        frame.render(draw, 5, 5, 0f);
        assertTrue(draw.strings.contains("Extras"));
        assertFalse(frame.mouseClicked(ctx, 500, 500, 0));
        int contentX = frame.getDimensions().width() - 20;
        assertFalse(frame.mouseScrolled(ctx, contentX, 50, 0, -1));
        assertFalse(frame.mouseScrolled(ctx, 10, 10, 0, -1));

        TabFrame remembered = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 300)).addTabs(map -> map.put("impetus", List.of(general, quality))).setTabSectionSelectedTab(new AtomicReference<>(TextComponent.literal("Quality"))).build(draw);
        remembered.render(draw, 5, 5, 0f);
        TabFrame empty = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 300)).addTabs(map -> {}).build(draw);
        empty.render(draw, 5, 5, 0f);
        assertFalse(empty.mouseScrolled(ctx, 300, 50, 0, -1));
        assertThrows(NullPointerException.class, () -> TabFrame.createBuilder().build(draw));

        List<Tab<?>> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(Tab.from(page("impetus", "Tab" + i, 1), o -> true, new AtomicReference<>(0)));
        }
        TabFrame scrolling = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 400, 100)).addTabs(map -> map.put("impetus", many)).build(draw);
        scrolling.render(draw, 5, 5, 0f);
        assertTrue(scrolling.mouseScrolled(ctx, 10, 10, 0, -1));
    }

    // Draws the icon and name and fold marker of a header.
    @Test
    void headerWidgetDrawsIconNameAndMarker() {
        FakeDrawContext draw = new FakeDrawContext();
        AtomicInteger toggles = new AtomicInteger();
        TabHeaderWidget header = new TabHeaderWidget(new Dim2i(0, 0, 100, 30), "impetus", true, toggles::incrementAndGet);
        header.render(draw, 5, 5, 0f);
        header.render(draw, 500, 500, 0f);
        assertTrue(draw.drew("blit(textures/misc/unknown_pack.png"));
        assertTrue(draw.strings.contains("+"));
        assertTrue(header.mouseClicked(new FakeInteractionContext(), 5, 5, 0));
        assertEquals(1, toggles.get());

        // Focused, it is washed as when hovered and outlined last in the mod's accent
        String accent = Integer.toHexString(draw.getModAccentColor("impetus"));
        FakeDrawContext focused = new FakeDrawContext();
        header.setFocused(true);
        header.render(focused, 500, 500, 0f);
        assertEquals("fill(0,0,100,30,30ffffff)", focused.calls.getFirst());
        assertEquals(List.of("fill(0,0,100,1," + accent + ")", "fill(0,29,100,30," + accent + ")", "fill(0,0,1,30," + accent + ")", "fill(99,0,100,30," + accent + ")"),
                focused.calls.subList(focused.calls.size() - 4, focused.calls.size()));
        assertTrue(header.keyTyped(new FakeInteractionContext(), ' ', Keys.SPACE));
        assertEquals(2, toggles.get());
    }

    // What the frame's focused control is, by kind and label
    private static String focus(TabFrame frame) {
        Focusable focused = frame.getFocusedControl();
        if (focused instanceof TabHeaderWidget) {
            return "header";
        }
        if (focused instanceof FlatButtonWidget button) {
            return button.getLabel().toString();
        }
        return focused instanceof ControlElement<?> row ? row.getOption().getName().toString() : String.valueOf(focused);
    }

    // Walks the sidebar into the page with the keyboard and keeps focus through a rebuild.
    @Test
    void theKeyboardWalksTheSidebarIntoThePageAndAPressedEntryKeepsTheFocusThroughTheRebuild() {
        Set<String> collapsed = Statics.get(TabFrame.class, "COLLAPSED_GROUPS");
        try {
            FakeDrawContext draw = new FakeDrawContext();
            AtomicReference<TextComponent> selected = new AtomicReference<>(null);
            Tab<ScrollableFrame> general = Tab.from(page("keys_a", "General", 2), o -> true, new AtomicReference<>(0));
            Tab<ScrollableFrame> quality = Tab.from(page("keys_a", "Quality", 2), o -> true, new AtomicReference<>(0));
            Tab<ScrollableFrame> other = Tab.from(page("keys_b", "Extras", 1), o -> true, new AtomicReference<>(0));
            TabFrame frame = TabFrame.createBuilder()
                    .setDimension(new Dim2i(0, 0, 400, 300))
                    .addTabs(map -> {
                        map.put("keys_a", List.of(general, quality));
                        map.put("keys_b", List.of(other));
                    })
                    .setTabSectionSelectedTab(selected)
                    .build(draw);
            FakeInteractionContext ctx = new FakeInteractionContext();
            assertEquals("null", focus(frame));

            // The sidebar in order, then the rows of the page shown, which is General until another is picked
            List<String> order = new ArrayList<>();
            while (frame.moveFocus(true)) {
                order.add(focus(frame));
            }
            assertEquals(List.of("header", "General", "Quality", "header", "Extras", "General0", "General1"), order);

            // Enter on Quality switches to it, and the rebuilt Quality entry has the focus
            for (int i = 0; i < 3; i++) {
                assertTrue(frame.dispatchKeyTyped(ctx, '\t', Keys.TAB));
            }
            assertEquals("Quality", focus(frame));
            Focusable before = frame.getFocusedControl();
            assertTrue(frame.dispatchKeyTyped(ctx, '\r', Keys.RETURN));
            assertEquals("Quality", selected.get().toString());
            assertEquals("Quality", focus(frame));
            assertNotSame(before, frame.getFocusedControl());
            assertEquals(1, ctx.clicks);

            // Shift+Tab back to the group header, where Space folds the group and the header keeps the focus
            ctx.held.add(InteractionContext.SpecialKey.SHIFT);
            assertTrue(frame.dispatchKeyTyped(ctx, '\t', Keys.TAB));
            assertTrue(frame.dispatchKeyTyped(ctx, '\t', Keys.TAB));
            ctx.held.clear();
            assertEquals("header", focus(frame));
            assertTrue(frame.dispatchKeyTyped(ctx, ' ', Keys.SPACE));
            assertTrue(collapsed.contains("keys_a"));
            assertFalse(collapsed.contains("keys_b"));
            assertEquals("header", focus(frame));
            draw.strings.clear();
            frame.render(draw, 500, 500, 0f);
            assertTrue(draw.strings.contains("+"));
            assertFalse(draw.strings.contains("General"));

            // The folded pages are passed over: on to the second header, then the rows of Quality, and round again
            order.clear();
            for (int i = 0; i < 5; i++) {
                assertTrue(frame.dispatchKeyTyped(ctx, '\t', Keys.TAB));
                order.add(focus(frame));
            }
            assertEquals(List.of("header", "Extras", "Quality0", "Quality1", "header"), order);

            // Space unfolds it again, still focused
            assertTrue(frame.dispatchKeyTyped(ctx, ' ', Keys.SPACE));
            assertFalse(collapsed.contains("keys_a"));
            assertEquals("header", focus(frame));

            // A click on an entry rebuilds the sidebar under it, and the entry it landed on is gone, so nothing keeps the focus; rows: header 0-30, General 30-48
            assertTrue(frame.mouseClicked(ctx, 10, 39, 0));
            assertEquals("General", selected.get().toString());
            assertEquals("null", focus(frame));
        } finally {
            collapsed.removeIf(id -> id.startsWith("keys_"));
        }
    }

    // Draws no page frame after switching to a tab without a frame factory.
    @Test
    void switchingToATabWithoutAFrameClearsThePage() {
        Set<String> collapsed = Statics.get(TabFrame.class, "COLLAPSED_GROUPS");
        collapsed.clear();
        int[] draws = {0};
        Tab<?> framed = Tab.<AbstractFrame>builder().setTitle(TextComponent.literal("framed"))
                .setFrameFunction(dim -> new AbstractFrame(dim, false) {
                    @Override
                    public void render(com.bdmajora.impetus.engine.impl.gui.framework.DrawContext drawContext, int mouseX, int mouseY, float delta) {
                        draws[0]++;
                    }
                }).build();
        Tab<?> bare = Tab.createBuilder().setTitle(TextComponent.literal("bare")).build();
        FakeDrawContext draw = new FakeDrawContext();
        TabFrame frame = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 300, 200))
                .addTabs(tabs -> tabs.put("impetus", new ArrayList<>(List.of(framed, bare))))
                .setTabSectionSelectedTab(new AtomicReference<>(null))
                .build(draw);
        frame.render(draw, 0, 0, 0f);
        assertEquals(1, draws[0]);
        frame.setTab(bare);
        frame.render(draw, 0, 0, 0f);
        assertEquals(1, draws[0]);
        assertFalse(frame.mouseScrolled(new FakeInteractionContext(), 250, 100, 0, -1));
    }

    // Unfolds only the group that holds the selected tab object.
    @Test
    void setTabUnfoldsTheGroupHoldingTheSameObject() {
        Set<String> collapsed = Statics.get(TabFrame.class, "COLLAPSED_GROUPS");
        collapsed.clear();
        Tab<?> first = Tab.createBuilder().setTitle(TextComponent.literal("same")).build();
        Tab<?> twin = Tab.createBuilder().setTitle(TextComponent.literal("same")).build();
        assertEquals(first, twin);
        FakeDrawContext draw = new FakeDrawContext();
        TabFrame frame = TabFrame.createBuilder().setDimension(new Dim2i(0, 0, 300, 200))
                .addTabs(tabs -> {
                    tabs.put("a", List.of(first));
                    tabs.put("b", List.of(twin));
                })
                .build(draw);
        collapsed.add("a");
        collapsed.add("b");
        frame.setTab(twin);
        assertTrue(collapsed.contains("a"));
        assertFalse(collapsed.contains("b"));
        assertThrows(NullPointerException.class, () -> frame.setTab(null));
        collapsed.clear();
    }
}
