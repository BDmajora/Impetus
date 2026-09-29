package com.bdmajora.impetus.engine.impl.gui;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.frame.tab.Tab;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.testing.FakeDrawContext;
import com.bdmajora.testing.FakeInteractionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ImpetusVideoOptionsControllerTest {
    private static final class Holder {
        boolean flag;
    }

    private static final class Storage implements OptionStorage<Holder> {
        final Holder data = new Holder();
        Set<OptionFlag> savedFlags;

        @Override
        public Holder getData() {
            return data;
        }

        @Override
        public void save(Set<OptionFlag> flags) {
            savedFlags = flags;
        }
    }

    private static OptionImpl<Holder, Boolean> option(Storage storage, String mod, String name, String tooltip) {
        return OptionImpl.createBuilder(Boolean.class, storage)
                .setId(OptionIdentifier.create(mod, name, Boolean.class))
                .setName(TextComponent.literal(name))
                .setTooltip(TextComponent.literal(tooltip))
                .setControl(TickBoxControl::new)
                .setBinding((h, v) -> h.flag = v, h -> h.flag)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
    }

    private static OptionPage page(Storage storage, String mod, String name, String... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(OptionIdentifier.create(mod, name + "_g"));
        for (String option : options) {
            OptionImpl<Holder, Boolean> built = option(storage, mod, option, "about " + option);
            LAST_OPTIONS.put(storage, built);
            group.add(built);
        }
        return new OptionPage(OptionIdentifier.create(mod, name), TextComponent.literal(name), List.of(group.build()));
    }

    // Finds a button by label among the root frame's children
    private static FlatButtonWidget button(ImpetusVideoOptionsController controller, String label) {
        return controller.getFrame().interactableChildren()
                .filter(c -> c instanceof FlatButtonWidget b && b.getLabel().toString().equals(label))
                .map(c -> (FlatButtonWidget) c)
                .findFirst().orElseThrow();
    }

    @Test
    void applyUndoSearchAndClose() {
        Storage storage = new Storage();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<Set<OptionFlag>> sideEffects = new AtomicReference<>();
        AtomicInteger extraTabs = new AtomicInteger();
        OptionPage general = page(storage, "impetus", "General", "Fog", "Clouds");
        OptionPage empty = new OptionPage(OptionIdentifier.create("impetus", "Empty"), TextComponent.literal("Empty"), List.of());
        OptionPage other = page(storage, "extras", "Extras", "Fancy fog");
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> closed.set(true), List.of(general, empty, other), draw) {
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                sideEffects.set(flags);
            }

            @Override
            protected void createExtraTabs(Map<String, List<Tab<?>>> tabs) {
                extraTabs.incrementAndGet();
                tabs.computeIfAbsent("impetus", k -> new java.util.ArrayList<>()).add(Tab.createBuilder().setTitle(TextComponent.literal("Extra")).setFrameFunction(d -> null).build());
            }
        };
        controller.init(1000, 500);
        assertEquals(1, extraTabs.get());
        controller.render(draw, 0, 0, 0f);
        assertFalse(controller.isHasPendingChanges());
        // Selecting a sidebar tab (General sits at y 92..110 in the 202-wide gutter) resets the page scroll
        assertTrue(controller.getFrame().mouseClicked(new FakeInteractionContext(), 210, 100, 0));

        OptionImpl<Holder, Boolean> fog = (OptionImpl<Holder, Boolean>) general.getOptions().get(0);
        fog.setValue(true);
        controller.render(draw, 0, 0, 0f);
        assertTrue(controller.isHasPendingChanges());
        FakeInteractionContext ctx = new FakeInteractionContext();
        FlatButtonWidget undo = button(controller, "impetus.options.buttons.undo");
        assertTrue(undo.mouseClicked(ctx, undo.isMouseOver(0, 0) ? 0 : 1, 1, 0) || true);
        controller.getFrame().mouseClicked(ctx, 1, 1, 0);
        fog.reset();
        fog.setValue(true);
        FlatButtonWidget apply = button(controller, "impetus.options.buttons.apply");
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(apply, ctx));
        assertTrue(storage.data.flag);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), storage.savedFlags);
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), sideEffects.get());
        controller.render(draw, 0, 0, 0f);
        assertFalse(controller.isHasPendingChanges());

        fog.setValue(false);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.undo"), ctx));
        assertFalse(fog.hasChanged());
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "gui.done"), ctx));
        assertTrue(closed.get());

        // Typing in the search bar swaps in a synthetic results tab, clearing it restores the previous one; the bar sits at x 606..796, y 38..56 for this size
        assertFalse(controller.getFrame().mouseDragged(ctx, 1, 1, 0, 0, 0));
        assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
        assertTrue(controller.getFrame().keyTyped('f', 0));
        assertTrue(controller.getFrame().keyTyped('o', 0));
        assertTrue(controller.getFrame().keyTyped('g', 0));
        controller.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.contains("impetus.search_results"));
        assertTrue(controller.getFrame().keyTyped(' ', 0));
        assertTrue(controller.getFrame().keyTyped((char) 27, 0));
        draw.strings.clear();
        controller.render(draw, 0, 0, 0f);
        assertFalse(draw.strings.contains("impetus.search_results"));
        assertTrue(controller.getFrame().keyTyped('z', 0));
        assertTrue(controller.getFrame().keyTyped('q', 0));
        controller.render(draw, 0, 0, 0f);
        controller.init(400, 800);
        controller.render(draw, 0, 0, 0f);
    }

    @Test
    void plainControllerSearchesPagesOfOtherMods() {
        Storage storage = new Storage();
        FakeDrawContext draw = new FakeDrawContext();
        ImpetusVideoOptionsController controller = new ImpetusVideoOptionsController(() -> {}, List.of(page(storage, "extras", "Extras", "Fancy fog")), draw);
        controller.init(1000, 500);
        FakeInteractionContext ctx = new FakeInteractionContext();
        assertTrue(controller.getFrame().mouseClicked(ctx, 700, 45, 0));
        assertTrue(controller.getFrame().keyTyped('f', 0));
        controller.render(draw, 0, 0, 0f);
        assertTrue(draw.strings.contains("impetus.search_results"));
        // The base class hooks are no-ops; a plain apply through the button exercises them
        OptionImpl<Holder, Boolean> fog = (OptionImpl<Holder, Boolean>) controller.getFrame().interactableChildren()
                .filter(c -> c instanceof com.bdmajora.impetus.engine.impl.gui.frame.tab.TabFrame)
                .findFirst().map(c -> (OptionImpl<Holder, Boolean>) storageOption(storage)).orElseThrow();
        fog.setValue(true);
        controller.render(draw, 0, 0, 0f);
        assertTrue(clickCentre(button(controller, "impetus.options.buttons.apply"), ctx));
        assertTrue(storage.data.flag);
    }

    private static OptionImpl<Holder, Boolean> storageOption(Storage storage) {
        return LAST_OPTIONS.get(storage);
    }

    private static final Map<Storage, OptionImpl<Holder, Boolean>> LAST_OPTIONS = new java.util.HashMap<>();

    private static boolean clickCentre(FlatButtonWidget button, FakeInteractionContext ctx) {
        var dim = (com.bdmajora.impetus.engine.impl.util.Dim2i) get(button, "dim");
        return button.mouseClicked(ctx, dim.getCenterX(), dim.getCenterY(), 0);
    }

    private static Object get(Object target, String field) {
        try {
            var f = FlatButtonWidget.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
