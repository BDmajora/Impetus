package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.ExtrasConfig.LeafCulling;
import com.bdmajora.extras.ExtrasConfig.LeafSettings;
import com.bdmajora.testing.Mixins;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Covers the setting group and its builder
class SettingGroupTest {
    private static final String CATEGORY = "box";

    // Group with one field of every setting type
    static final class Box {
        boolean flag = true;
        int number = 3;
        LeafCulling mode = LeafCulling.CHECK;
        String[] names = {"x"};
    }

    // Returns a builder over a box group that ignores the config it is handed
    private static SettingGroup.Builder<Box> builder(Box live) {
        return SettingGroup.builder(CATEGORY, c -> live, Box::new);
    }

    // Returns a group with one setting of every type bound to the live box
    private static SettingGroup<Box> everyType(Box live) {
        return builder(live)
                .bool("flag", "Flag", b -> b.flag, (b, v) -> b.flag = v)
                .integer("number", 0, 9, "Number", b -> b.number, (b, v) -> b.number = v)
                .choice("mode", "Mode", b -> b.mode, (b, v) -> b.mode = v)
                .list("names", "Names", b -> b.names, (b, v) -> b.names = v)
                .build();
    }

    // Checks that the builder rejects every null part
    @Test
    void theBuilderRejectsNullParts() {
        assertThrows(NullPointerException.class, () -> SettingGroup.builder(null, c -> new Box(), Box::new));
        assertThrows(NullPointerException.class, () -> SettingGroup.<Box>builder(CATEGORY, null, Box::new));
        assertThrows(NullPointerException.class, () -> SettingGroup.<Box>builder(CATEGORY, c -> new Box(), null));
        assertThrows(NullPointerException.class, () -> SettingGroup.<Box>builder(CATEGORY, c -> new Box(), () -> null));
    }

    // Checks that the factory runs once and supplies every default
    @Test
    void theFactoryRunsOnceAndSuppliesEveryDefault() {
        AtomicInteger made = new AtomicInteger();
        Box live = new Box();
        SettingGroup<Box> group = SettingGroup.<Box>builder(CATEGORY, c -> live, () -> {
                    made.incrementAndGet();
                    Box defaults = new Box();
                    defaults.flag = false;
                    defaults.number = 8;
                    defaults.mode = LeafCulling.DEPTH;
                    defaults.names = new String[] {"d"};
                    return defaults;
                })
                .bool("flag", "Flag", b -> b.flag, (b, v) -> b.flag = v)
                .integer("number", 0, 9, "Number", b -> b.number, (b, v) -> b.number = v)
                .choice("mode", "Mode", b -> b.mode, (b, v) -> b.mode = v)
                .list("names", "Names", b -> b.names, (b, v) -> b.names = v)
                .build();
        assertEquals(1, made.get());
        group.load(new Configuration(), null);
        assertFalse(live.flag);
        assertEquals(8, live.number);
        assertEquals(LeafCulling.DEPTH, live.mode);
        assertArrayEquals(new String[] {"d"}, live.names);
    }

    // Checks that a duplicate key is rejected with the category and key
    @Test
    void aDuplicateKeyIsRejectedWithTheCategoryAndKey() {
        SettingGroup.Builder<Box> builder = builder(new Box()).bool("flag", "Flag", b -> b.flag, (b, v) -> b.flag = v);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> builder.integer("flag", 0, 9, "Number", b -> b.number, (b, v) -> b.number = v));
        assertEquals("box already holds flag", e.getMessage());
        assertDoesNotThrow(() -> builder.integer("number", 0, 9, "Number", b -> b.number, (b, v) -> b.number = v));
    }

    // Checks that an integer default outside its range is rejected
    @Test
    void aDefaultOutsideTheRangeOfAnIntegerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> builder(new Box()).integer("number", 4, 9, "Number", b -> b.number, (b, v) -> b.number = v));
    }

    // Checks that a group without settings is rejected
    @Test
    void anEmptyGroupIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> builder(new Box()).build());
        assertEquals("box holds no settings", e.getMessage());
    }

    // Checks that every setting is loaded into and saved from the group
    @Test
    void everySettingIsLoadedIntoAndSavedFromTheGroup() {
        Box live = new Box();
        SettingGroup<Box> group = everyType(live);
        Configuration config = new Configuration();
        live.flag = false;
        live.number = 0;
        live.mode = LeafCulling.DEFAULT;
        live.names = new String[0];
        group.load(config, null);
        assertTrue(live.flag);
        assertEquals(3, live.number);
        assertEquals(LeafCulling.CHECK, live.mode);
        assertArrayEquals(new String[] {"x"}, live.names);
        assertEquals(List.of("flag", "mode", "names", "number"), new ArrayList<>(config.getCategory(CATEGORY).keySet()));

        live.flag = false;
        live.number = 9;
        live.mode = LeafCulling.DEPTH;
        live.names = new String[] {"y", "z"};
        group.save(config, null);
        Box reread = new Box();
        everyType(reread).load(config, null);
        everyType(reread).load(config, null);
        assertFalse(reread.flag);
        assertEquals(9, reread.number);
        assertEquals(LeafCulling.DEPTH, reread.mode);
        assertArrayEquals(new String[] {"y", "z"}, reread.names);
    }

    // Checks that the owner picks the group out of the whole config
    @Test
    void theOwnerPicksTheGroupOutOfTheWholeConfig() {
        SettingGroup<LeafSettings> group = SettingGroup.builder("leaves", c -> c.leaves, LeafSettings::new)
                .integer("cullingDepth", LeafSettings.DEPTH_MIN, LeafSettings.DEPTH_MAX, "Depth",
                        s -> s.cullingDepth, (s, v) -> s.cullingDepth = v)
                .build();
        ExtrasConfig options = new ExtrasConfig();
        options.leaves.cullingDepth = LeafSettings.DEPTH_MAX;
        Configuration config = new Configuration();
        group.save(config, options);
        ExtrasConfig reread = new ExtrasConfig();
        group.load(config, reread);
        assertEquals(LeafSettings.DEPTH_MAX, reread.leaves.cullingDepth);
    }

    // Checks that a null config or a null owner result is rejected
    @Test
    void aNullConfigOrOwnerResultIsRejected() {
        SettingGroup<LeafSettings> group = SettingGroup.builder("leaves", c -> c.leaves, LeafSettings::new)
                .integer("cullingDepth", 1, 4, "Depth", s -> s.cullingDepth, (s, v) -> s.cullingDepth = v)
                .build();
        Configuration config = new Configuration();
        assertThrows(NullPointerException.class, () -> group.load(config, null));
        assertThrows(NullPointerException.class, () -> group.save(config, null));
        assertFalse(config.hasCategory("leaves"));
        assertThrows(NullPointerException.class, () -> group.load(null, new ExtrasConfig()));
        assertThrows(NullPointerException.class, () -> group.save(null, new ExtrasConfig()));
    }

    // Checks that the built group keeps its settings in declaration order
    @Test
    void theBuiltGroupKeepsItsSettingsInDeclarationOrder() {
        Setting<?>[] settings = Mixins.get(everyType(new Box()), "settings");
        List<String> keys = new ArrayList<>();
        for (Setting<?> setting : settings) {
            keys.add(setting.key());
        }
        assertEquals(List.of("flag", "number", "mode", "names"), keys);
    }
}
