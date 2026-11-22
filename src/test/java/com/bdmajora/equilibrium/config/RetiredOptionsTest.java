package com.bdmajora.equilibrium.config;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.mixin.EquilibriumMixinPlugin;
import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Covers the Equilibrium options removed because FermiumMixins and VintageFix patch the same code.
class RetiredOptionsTest {
    @TempDir
    Path dir;

    // Options removed on 11-21 that old files still carry.
    private static final List<String> RETIRED = List.of("mixin.ai.pathfinding_chunk_load", "mixin.entity.xp_orb_merging", "mixin.chunk.no_validation");

    @AfterEach
    void forgetSharedConfig() {
        Mixins.set(Equilibrium.class, "config", null);
        Mixins.set(EquilibriumMixinPlugin.class, "config", null);
    }

    @Test
    void theRemovedOptionsAreGoneFromTheTree() {
        for (String retired : RETIRED) {
            assertNull(EquilibriumOptions.get(retired), retired);
        }
        // Their parents and siblings stay
        assertNotNull(EquilibriumOptions.get("mixin.ai"));
        assertNotNull(EquilibriumOptions.get("mixin.ai.goal_selector"));
        assertNotNull(EquilibriumOptions.get("mixin.chunk"));
        assertNotNull(EquilibriumOptions.get("mixin.chunk.tile_entity_map"));
        assertNotNull(EquilibriumOptions.get("mixin.entity.fast_hand_swing"));
    }

    @Test
    void noOptionDependsOnARemovedOne() {
        for (EquilibriumOptions.Entry entry : EquilibriumOptions.entries().values()) {
            for (Map.Entry<String, Boolean> dependency : entry.dependencies().entrySet()) {
                assertNotNull(EquilibriumOptions.get(dependency.getKey()), entry.name() + " needs " + dependency.getKey());
                assertFalse(RETIRED.contains(dependency.getKey()), entry.name());
            }
        }
    }

    @Test
    void noModConflictNamesARemovedOrMissingOption() throws Exception {
        List<?> conflicts = Mixins.get(ModCompatibility.class, "CONFLICTS");
        assertFalse(conflicts.isEmpty());
        for (Object conflict : conflicts) {
            String[] disables = Mixins.get(conflict, "disables");
            for (String option : disables) {
                assertNotNull(EquilibriumOptions.get(option.startsWith("mixin.") ? option : "mixin." + option), option);
                assertFalse(RETIRED.contains(option), option);
            }
        }
    }

    @Test
    void anOldFileWithTheRemovedKeysLoadsKeepsTheRestAndDropsThemOnSave() throws Exception {
        Path file = dir.resolve("equilibrium.properties");
        Files.writeString(file, String.join("\n",
                "mixin.ai.pathfinding_chunk_load=false",
                "mixin.entity.xp_orb_merging=true",
                "mixin.chunk.no_validation=maybe",
                "mixin.ai.goal_selector=false",
                ""));
        EquilibriumConfig config = EquilibriumConfig.load(file);
        for (String retired : RETIRED) {
            assertNull(config.getOption(retired), retired);
        }
        assertFalse(config.isOptionEnabled("mixin.ai.goal_selector"));
        assertTrue(config.getOption("mixin.ai.goal_selector").isUserDefined());
        // A stale mixin of a removed package is governed by its category, never by a ghost switch
        assertSame(config.getOption("mixin.ai"), config.getEffectiveOptionForMixin("ai.pathfinding_chunk_load.ChunkCacheMixin"));
        assertSame(config.getOption("mixin.entity"), config.getEffectiveOptionForMixin("entity.xp_orb_merging.EntityXPOrbMixin"));
        assertSame(config.getOption("mixin.chunk"), config.getEffectiveOptionForMixin("chunk.no_validation.ChunkMixin"));
        assertEquals(EquilibriumOptions.entries().size(), config.getOptionCount());

        config.save();
        String written = Files.readString(file);
        for (String retired : RETIRED) {
            assertFalse(written.contains(retired), retired);
        }
        assertTrue(written.contains("\nmixin.ai.goal_selector=false\n"));
        // The rewritten file loads to the same state
        EquilibriumConfig again = EquilibriumConfig.load(file);
        assertFalse(again.isOptionEnabled("mixin.ai.goal_selector"));
        again.save();
        assertEquals(written, Files.readString(file));
    }

    @Test
    void aFileWithOnlyRemovedKeysIsTheSameAsAFreshOne() throws Exception {
        Path old = dir.resolve("old.properties");
        Files.writeString(old, "mixin.ai.pathfinding_chunk_load=true\nmixin.entity.xp_orb_merging=false\n");
        EquilibriumConfig fromOld = EquilibriumConfig.load(old);
        fromOld.save();
        Path fresh = dir.resolve("fresh.properties");
        EquilibriumConfig.load(fresh).save();
        assertEquals(Files.readString(fresh), Files.readString(old));
        assertEquals(0, fromOld.getOptionOverrideCount() - EquilibriumConfig.load(fresh).getOptionOverrideCount());
    }
}
