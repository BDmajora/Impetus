package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

// Covers the Extras settings removed because FermiumMixins patches the same code, and their keys in old files.
class RetiredSettingsTest {
    @TempDir
    Path dir;

    // Line an old file holds for the removed parallel spawner.
    private static final String OLD_SPAWNING = "    # Evaluate natural mob spawn attempts in parallel per chunk [default: true]\n    B:spawning=true\n\n";

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    @BeforeEach
    void gameDirectory() {
        Statics.set(FMLInjectionData.class, "minecraftHome", dir.toFile());
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        RegistryState.clear();
    }

    @AfterEach
    void forgetRegistry() {
        RegistryState.clear();
    }

    // Returns the golden file with the removed spawning key put back where 1.0.3 wrote it.
    private static String oldFile() throws Exception {
        String golden = ExtrasSchemaTest.golden();
        String anchor = "    B:randomTicks=true\n\n";
        assertTrue(golden.contains(anchor));
        return golden.replace(anchor, anchor + OLD_SPAWNING);
    }

    @Test
    void readingAnOldFileDropsTheRemovedKeyAndLeavesTheGoldenFile() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        Files.writeString(file.toPath(), oldFile());
        assertTrue(file.setLastModified(1_000_000L));
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertNotEquals(1_000_000L, file.lastModified());
        String text = Files.readString(file.toPath());
        assertFalse(text.contains("spawning"));
        assertEquals(ExtrasSchemaTest.golden(), text);
        // Once clean, reading leaves the file alone
        assertTrue(file.setLastModified(1_000_000L));
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertEquals(1_000_000L, file.lastModified());
        assertEquals(ExtrasSchemaTest.golden(), Files.readString(file.toPath()));
    }

    @Test
    void theValuesAroundTheRemovedKeyAreKept() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        Files.writeString(file.toPath(), oldFile().replace("B:randomTicks=true", "B:randomTicks=false").replace("B:spawning=true", "B:spawning=false"));
        ExtrasConfig options = new ExtrasConfig();
        ExtrasSchema.read(new Configuration(file), options);
        assertFalse(options.async.randomTicks);
        assertTrue(options.async.entities);
        String text = Files.readString(file.toPath());
        assertTrue(text.contains("B:randomTicks=false"));
        assertFalse(text.contains("spawning"));
    }

    @Test
    void writingAConfigLoadedFromAnOldFileDropsTheKeyToo() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        Files.writeString(file.toPath(), oldFile());
        Configuration config = new Configuration(file);
        assertTrue(config.getCategory("parallel_ticking").containsKey("spawning"));
        ExtrasSchema.write(config, new ExtrasConfig());
        assertFalse(config.getCategory("parallel_ticking").containsKey("spawning"));
        String text = Files.readString(file.toPath());
        assertFalse(text.contains("spawning"));
        // Its neighbours are written as they were
        assertTrue(text.contains("B:randomTicks=true"));
        assertTrue(text.contains("B:entities=true"));
    }

    @Test
    void droppingFindsNothingInAFileWithoutTheCategoryOrTheKey() throws Exception {
        Configuration empty = new Configuration(dir.resolve("empty.cfg").toFile());
        assertFalse(ExtrasSchema.dropRetired(empty));
        // Looking does not create the category
        assertFalse(empty.hasCategory("parallel_ticking"));

        Configuration written = new Configuration(dir.resolve("clean.cfg").toFile());
        written.get("parallel_ticking", "entities", true);
        written.save();
        Configuration clean = new Configuration(dir.resolve("clean.cfg").toFile());
        assertFalse(clean.hasChanged());
        assertFalse(ExtrasSchema.dropRetired(clean));
        assertTrue(clean.getCategory("parallel_ticking").containsKey("entities"));
        // Finding nothing must not mark the file changed, or it would be saved on every launch
        assertFalse(clean.hasChanged());

        // A retired key in another category is not the retired setting
        Configuration elsewhere = new Configuration(dir.resolve("elsewhere.cfg").toFile());
        elsewhere.get("network", "spawning", true);
        assertFalse(ExtrasSchema.dropRetired(elsewhere));
        assertTrue(elsewhere.getCategory("network").containsKey("spawning"));

        // Dropping twice reports the key once
        Configuration twice = new Configuration(dir.resolve("twice.cfg").toFile());
        twice.get("parallel_ticking", "spawning", true);
        assertTrue(ExtrasSchema.dropRetired(twice));
        assertFalse(ExtrasSchema.dropRetired(twice));
    }

    @Test
    void everyRetiredKeyNamesARealCategoryAndNoCurrentSetting() {
        SettingGroup<?>[] groups = Mixins.get(ExtrasSchema.class, "GROUPS");
        for (String[] retired : ExtrasSchema.RETIRED) {
            assertEquals(2, retired.length);
            SettingGroup<?> group = Arrays.stream(groups).filter(g -> retired[0].equals(Mixins.get(g, "category"))).findFirst().orElse(null);
            assertNotNull(group, retired[0]);
            for (Setting<?> setting : Mixins.<Setting<?>[]>get(group, "settings")) {
                assertNotEquals(retired[1], setting.key(), retired[0] + "." + retired[1] + " is a live setting again");
            }
        }
    }

    @Test
    void theParallelSpawnerLeftNoSwitchBehind() {
        for (Field field : ExtrasConfig.AsyncSettings.class.getFields()) {
            assertNotEquals("spawning", field.getName());
        }
        for (Field field : ParallelProcessor.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("spawn"), field.getName());
        }
        for (Method method : ParallelProcessor.class.getDeclaredMethods()) {
            assertFalse(method.getName().toLowerCase().contains("spawn"), method.getName());
        }
        assertNull(RetiredSettingsTest.class.getResource("/com/bdmajora/extras/async/ParallelSpawner.class"));
        // The master switch text no longer promises mob spawning
        assertFalse(ExtrasSchemaTestText.master().contains("spawn"));
    }

    // Reads the master switch comment of the parallel group.
    private static final class ExtrasSchemaTestText {
        static String master() {
            try {
                String golden = ExtrasSchemaTest.golden();
                int start = golden.indexOf("parallel_ticking {");
                return golden.substring(start, golden.indexOf("B:enabled", start));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
