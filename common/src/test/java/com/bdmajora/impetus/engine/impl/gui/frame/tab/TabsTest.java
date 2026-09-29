package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.frame.AbstractFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.BasicFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.ScrollableFrame;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
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

class TabsTest {
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
    }
}
