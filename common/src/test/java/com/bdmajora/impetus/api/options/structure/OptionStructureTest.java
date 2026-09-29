package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OptionStructureTest {
    private static OptionStorage<AtomicInteger> storage(int value) {
        AtomicInteger data = new AtomicInteger(value);
        return () -> data;
    }

    @SuppressWarnings("unchecked")
    private static OptionImpl.Builder<AtomicInteger, Integer> builder(OptionStorage<AtomicInteger> storage) {
        return OptionImpl.createBuilder(Integer.class, storage)
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> Mockito.mock(Control.class));
    }

    @Test
    void optionTracksPendingValueUntilApplied() {
        OptionStorage<AtomicInteger> storage = storage(1);
        OptionImpl<AtomicInteger, Integer> option = builder(storage)
                .setId(OptionIdentifier.create("t", "opt", Integer.class))
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
        assertEquals(1, option.getValue());
        assertFalse(option.hasChanged());
        option.setValue(1);
        assertFalse(option.hasChanged());
        option.setValue(2);
        assertTrue(option.hasChanged());
        assertEquals(2, option.getValue());
        option.reset();
        assertEquals(1, option.getValue());
        option.setValue(3);
        option.applyChanges();
        assertEquals(3, storage.getData().get());
        assertFalse(option.hasChanged());
        option.applyChanges();
        assertEquals(OptionImpact.HIGH, option.getImpact());
        assertEquals(Set.of(OptionFlag.REQUIRES_RENDERER_RELOAD), Set.copyOf(option.getFlags()));
        assertSame(storage, option.getStorage());
        assertNotNull(option.getControl());
        assertTrue(option.isAvailable());
        assertEquals("t.options.opt.name", option.getName().toString());
        assertEquals("t.options.opt.tooltip", option.getTooltip().toString());
    }

    @Test
    void builderInfersNothingWithoutAnIdAndValidatesRequiredParts() {
        OptionImpl<AtomicInteger, Integer> option = builder(storage(0))
                .setName(TextComponent.literal("n"))
                .setTooltip(TextComponent.literal("tip"))
                .setEnabled(false)
                .build();
        assertSame(OptionIdentifier.EMPTY, option.getId());
        assertFalse(option.isAvailable());
        assertEquals("n", option.getName().toString());
        assertThrows(NullPointerException.class, () -> builder(storage(0)).build());
        assertThrows(NullPointerException.class, () -> builder(storage(0)).setName(TextComponent.literal("n")).build());
        assertThrows(NullPointerException.class, () -> OptionImpl.createBuilder(Integer.class, storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t")).build());
        assertThrows(NullPointerException.class, () -> OptionImpl.createBuilder(Integer.class, storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t")).setBinding(AtomicInteger::set, AtomicInteger::get).build());
        OptionImpl.Builder<AtomicInteger, Integer> b = OptionImpl.createBuilder(Integer.class, storage(0));
        assertThrows(NullPointerException.class, () -> b.setId(null));
        assertThrows(NullPointerException.class, () -> b.setName(null));
        assertThrows(NullPointerException.class, () -> b.setTooltip(null));
        assertThrows(NullPointerException.class, () -> b.setBinding(null));
        assertThrows(NullPointerException.class, () -> b.setBinding(null, AtomicInteger::get));
        assertThrows(NullPointerException.class, () -> b.setBinding(AtomicInteger::set, null));
        assertThrows(NullPointerException.class, () -> b.setControl(null));
        OptionImpl<AtomicInteger, Integer> enabled = builder(storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t"))
                .setBinding(new com.bdmajora.impetus.api.options.binding.GenericBinding<>(AtomicInteger::set, AtomicInteger::get))
                .setEnabledPredicate(() -> true).setEnabled(true).build();
        assertTrue(enabled.isAvailable());
        Option<Integer> bare = Mockito.mock(Option.class, Mockito.CALLS_REAL_METHODS);
        assertNull(bare.getId());
    }

    @Test
    void groupsAndPagesCollectOptionsAndFireEvents() {
        OptionImpl<AtomicInteger, Integer> option = builder(storage(0)).setName(TextComponent.literal("n")).setTooltip(TextComponent.literal("t")).build();
        OptionGroup group = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("t", "grp"))
                .setName(TextComponent.literal("Group"))
                .add(option)
                .addConditionally(false, () -> option)
                .addConditionally(true, () -> option)
                .build();
        assertEquals(2, group.getOptions().size());
        assertEquals("Group", group.getName().toString());
        assertEquals("t:grp", group.getId().toString());
        assertSame(group.id, group.getId());
        OptionGroup unnamed = OptionGroup.createBuilder().build();
        assertSame(OptionGroup.DEFAULT_ID, unnamed.getId());
        assertNull(unnamed.getName());

        OptionIdentifier<Void> pageId = OptionIdentifier.create("t", "page");
        OptionGroup extra = OptionGroup.createBuilder().add(option).build();
        OptionPageConstructionEvent.BUS.addListener(e -> {
            if (e.getId().matches(pageId)) {
                e.addGroup(extra);
            }
        });
        OptionPage page = new OptionPage(pageId, TextComponent.literal("Page"), List.of(group));
        assertEquals(List.of(group, extra), page.getGroups());
        assertEquals(3, page.getOptions().size());
        assertSame(pageId, page.getId());
        assertEquals("Page", page.getName().toString());
        OptionPage plain = new OptionPage(OptionIdentifier.create("t", "plain"), TextComponent.literal("P"), List.of(group));
        assertEquals(List.of(group), plain.getGroups());
    }

    @Test
    void impactsCarryColouredLabels() {
        for (OptionImpact impact : OptionImpact.values()) {
            assertTrue(impact.getLocalizedName().toString().contains("impetus.option_impact."));
        }
        assertEquals(5, OptionFlag.values().length);
        OptionStorage<String> storage = () -> "s";
        storage.save();
        storage.save(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD));
        assertEquals("s", storage.getData());
        new StandardOptions();
        new StandardOptions.Group();
        new StandardOptions.Pages();
        new StandardOptions.Option();
        assertEquals("minecraft:rendering", StandardOptions.Group.RENDERING.toString());
        assertEquals("impetus:general", StandardOptions.Pages.GENERAL.toString());
        assertEquals("minecraft:vsync", StandardOptions.Option.VSYNC.toString());
    }
}
