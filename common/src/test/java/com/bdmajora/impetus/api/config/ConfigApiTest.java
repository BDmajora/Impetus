package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.OptionGUIConstructionEvent;
import com.bdmajora.impetus.api.OptionGroupConstructionEvent;
import com.bdmajora.impetus.api.OptionPageConstructionEvent;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfigApiTest {
    @SuppressWarnings("unchecked")
    private static Option<?> option() {
        return Mockito.mock(Option.class);
    }

    @Test
    void registeredPagesGroupsAndOptionsArriveThroughTheEvents() {
        OptionPage page = Mockito.mock(OptionPage.class);
        ConfigApi.registerPage(() -> page);
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent.BUS.post(new OptionGUIConstructionEvent(pages));
        assertTrue(pages.contains(page));

        OptionIdentifier<Void> pageId = OptionIdentifier.create("api", "page");
        OptionGroup group = OptionGroup.createBuilder().build();
        ConfigApi.registerGroup(pageId, () -> group);
        OptionPageConstructionEvent hit = new OptionPageConstructionEvent(pageId, TextComponent.literal("p"));
        OptionPageConstructionEvent miss = new OptionPageConstructionEvent(OptionIdentifier.create("api", "other"), TextComponent.literal("o"));
        OptionPageConstructionEvent.BUS.post(hit);
        OptionPageConstructionEvent.BUS.post(miss);
        assertEquals(List.of(group), hit.getAdditionalGroups());
        assertTrue(miss.getAdditionalGroups().isEmpty());

        OptionIdentifier<Void> groupId = OptionIdentifier.create("api", "group");
        Option<?> option = option();
        ConfigApi.registerOption(groupId, () -> option);
        List<Option<?>> options = new ArrayList<>();
        OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(groupId, options));
        OptionGroupConstructionEvent.BUS.post(new OptionGroupConstructionEvent(OptionIdentifier.create("api", "other"), new ArrayList<>()));
        assertEquals(List.of(option), options);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> ConfigApi.registerPage(null));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerGroup(null, () -> null));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerGroup(OptionIdentifier.create("a", "b"), null));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerOption(null, ConfigApiTest::option));
        assertThrows(NullPointerException.class, () -> ConfigApi.registerOption(OptionIdentifier.create("a", "b"), null));
    }

    @Test
    void buildersProduceWiredPagesAndOptions() {
        AtomicInteger saves = new AtomicInteger();
        ConfigStorage<AtomicInteger> storage = ConfigBuilders.storage(new AtomicInteger(3), saves::incrementAndGet);
        OptionImpl<AtomicInteger, Integer> option = ConfigBuilders.option("api", "value", Integer.class, storage)
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> Mockito.mock(Control.class))
                .build();
        assertEquals("api:value", option.getId().toString());
        OptionImpl<AtomicInteger, Integer> byId = ConfigBuilders.option(option.getId(), Integer.class, storage)
                .setBinding(AtomicInteger::set, AtomicInteger::get)
                .setControl(o -> Mockito.mock(Control.class))
                .build();
        assertSame(option.getId(), byId.getId());
        OptionGroup group = ConfigBuilders.group("api", "grp").add(option).build();
        assertEquals("api:grp", group.getId().toString());
        OptionPage page = ConfigBuilders.page("api", "pg", TextComponent.literal("Page")).addGroup(group).build();
        assertEquals(List.of(group), page.getGroups());
        assertEquals(List.of(option), page.getOptions());
        OptionPage page2 = ConfigBuilders.page(page.getId(), TextComponent.literal("Again")).build();
        assertSame(page.getId(), page2.getId());
        assertThrows(NullPointerException.class, () -> ConfigBuilders.page("api", "x", null));
        assertThrows(NullPointerException.class, () -> ConfigBuilders.page("api", "x", TextComponent.literal("x")).addGroup(null));
    }

    @Test
    void storageForwardsSavesWithAndWithoutFlags() {
        AtomicReference<Set<OptionFlag>> seen = new AtomicReference<>();
        ConfigStorage<String> flagged = ConfigStorage.of("data", seen::set);
        assertEquals("data", flagged.getData());
        flagged.save();
        assertEquals(Set.of(), seen.get());
        flagged.save(Set.of(OptionFlag.REQUIRES_GAME_RESTART));
        assertEquals(Set.of(OptionFlag.REQUIRES_GAME_RESTART), seen.get());
        AtomicInteger runs = new AtomicInteger();
        ConfigStorage<String> plain = ConfigStorage.of("d", runs::incrementAndGet);
        plain.save(Set.of(OptionFlag.REQUIRES_ASSET_RELOAD));
        assertEquals(1, runs.get());
        assertThrows(NullPointerException.class, () -> ConfigStorage.of("d", (Runnable) null));
        assertThrows(NullPointerException.class, () -> ConfigStorage.of(null, runs::incrementAndGet));
    }
}
