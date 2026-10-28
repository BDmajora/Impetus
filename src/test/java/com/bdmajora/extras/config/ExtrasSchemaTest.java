package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

// Covers the whole Extras schema against real Forge files
class ExtrasSchemaTest {
    // Scratch game directory of each case
    @TempDir
    Path dir;

    // Boots the game classes that Forge configuration paths need
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    // Points both game directories at the scratch directory and empties the registry
    @BeforeEach
    void gameDirectory() {
        Statics.set(FMLInjectionData.class, "minecraftHome", dir.toFile());
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        RegistryState.clear();
    }

    // Empties the registry after each case
    @AfterEach
    void forgetRegistry() {
        RegistryState.clear();
    }

    // Returns the text of the file that a fresh config writes
    static String golden() throws Exception {
        try (InputStream in = ExtrasSchemaTest.class.getResourceAsStream("default-impetus-extras.cfg")) {
            assertNotNull(in, "golden file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Returns every setting group of the schema
    private static SettingGroup<?>[] groups() {
        return Mixins.get(ExtrasSchema.class, "GROUPS");
    }

    // Returns every settings group object of a config in field order
    static List<Object> groupObjects(ExtrasConfig options) throws IllegalAccessException {
        List<Object> objects = new ArrayList<>();
        for (Field field : ExtrasConfig.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
                objects.add(field.get(options));
            }
        }
        return objects;
    }

    // Returns every public instance field of a settings group
    static List<Field> settingFields(Object group) {
        List<Field> fields = new ArrayList<>();
        for (Field field : group.getClass().getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        return fields;
    }

    // Moves every setting field of the config one step away from its value
    static void changeEveryField(ExtrasConfig options) throws IllegalAccessException {
        for (Object group : groupObjects(options)) {
            for (Field field : settingFields(group)) {
                Class<?> type = field.getType();
                if (type == boolean.class) {
                    field.setBoolean(group, !field.getBoolean(group));
                } else if (type == int.class) {
                    field.setInt(group, field.getInt(group) + 1);
                } else if (type.isEnum()) {
                    Object[] constants = type.getEnumConstants();
                    field.set(group, constants[(((Enum<?>) field.get(group)).ordinal() + 1) % constants.length]);
                } else if (type == String[].class) {
                    field.set(group, new String[] {"changed:" + field.getName(), "second"});
                } else {
                    fail("unhandled field type " + type + " of " + field);
                }
            }
        }
    }

    // Asserts that every setting field of both configs holds the same value
    static void assertSameSettings(ExtrasConfig expected, ExtrasConfig actual) throws IllegalAccessException {
        List<Object> left = groupObjects(expected);
        List<Object> right = groupObjects(actual);
        for (int i = 0; i < left.size(); i++) {
            for (Field field : settingFields(left.get(i))) {
                Object a = field.get(left.get(i));
                Object b = field.get(right.get(i));
                if (a instanceof String[] array) {
                    assertArrayEquals(array, (String[]) b, field.toString());
                } else {
                    assertEquals(a, b, field.toString());
                }
            }
        }
    }

    // Checks that a fresh file matches the copy written before the split byte for byte
    @Test
    void aFreshFileMatchesTheGoldenCopy() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertEquals(golden(), Files.readString(file.toPath()));
    }

    // Checks that every group owns one distinct config field and one distinct category
    @Test
    void everyGroupOwnsOneFieldAndOneCategory() throws Exception {
        ExtrasConfig options = new ExtrasConfig();
        Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> categories = new HashSet<>();
        for (SettingGroup<?> group : groups()) {
            Function<ExtrasConfig, ?> owner = Mixins.get(group, "owner");
            assertTrue(owned.add(owner.apply(options)), "two groups share a field");
            assertTrue(categories.add(Mixins.get(group, "category")), "two groups share a category");
        }
        assertFalse(categories.contains(ParticleClassLists.CATEGORY));
        Set<Object> fields = Collections.newSetFromMap(new IdentityHashMap<>());
        fields.addAll(groupObjects(options));
        assertEquals(fields, owned);
    }

    // Checks that the schema holds every key the golden file holds and no other
    @Test
    void theSchemaKeysMatchTheGoldenKeys() throws Exception {
        Set<String> schema = new HashSet<>();
        for (SettingGroup<?> group : groups()) {
            String category = Mixins.get(group, "category");
            for (Setting<?> setting : Mixins.<Setting<?>[]>get(group, "settings")) {
                assertTrue(schema.add(category + "." + setting.key()), setting.key());
            }
        }
        schema.add(ParticleClassLists.CATEGORY + ".disabledClasses");
        schema.add(ParticleClassLists.CATEGORY + ".discoveredClasses");
        Set<String> golden = new HashSet<>();
        String category = null;
        for (String line : golden().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith("{")) {
                category = trimmed.substring(0, trimmed.length() - 1).trim();
            } else if (trimmed.length() > 2 && trimmed.charAt(1) == ':' && !trimmed.startsWith("#")) {
                int end = trimmed.indexOf('=') > 0 ? trimmed.indexOf('=') : trimmed.indexOf(' ');
                golden.add(category + "." + trimmed.substring(2, end));
            }
        }
        assertEquals(142, golden.size());
        assertEquals(golden, schema);
    }

    // Checks that every public setting field survives a write and a read
    @Test
    void everySettingFieldRoundTrips() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        ExtrasConfig options = new ExtrasConfig();
        changeEveryField(options);
        ExtrasSchema.write(new Configuration(file), options);
        ExtrasConfig reread = new ExtrasConfig();
        ExtrasSchema.read(new Configuration(file), reread);
        assertSameSettings(options, reread);
        assertThrows(AssertionError.class, () -> assertSameSettings(new ExtrasConfig(), reread));
    }

    // Checks that an empty file loads every field at its initial value
    @Test
    void anEmptyFileLoadsEveryInitialValue() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        Files.writeString(file.toPath(), "");
        ExtrasConfig options = new ExtrasConfig();
        changeEveryField(options);
        ExtrasSchema.read(new Configuration(file), options);
        assertSameSettings(new ExtrasConfig(), options);
    }

    // Checks that read saves only when a key was missing
    @Test
    void readSavesOnlyWhenAKeyWasMissing() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertTrue(file.setLastModified(1_000_000L));
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertEquals(1_000_000L, file.lastModified());

        String text = Files.readString(file.toPath()).replace("    B:water=true\n", "");
        Files.writeString(file.toPath(), text);
        assertTrue(file.setLastModified(1_000_000L));
        ExtrasSchema.read(new Configuration(file), new ExtrasConfig());
        assertNotEquals(1_000_000L, file.lastModified());
        assertEquals(golden(), Files.readString(file.toPath()));
    }

    // Checks that out of range and unreadable values fall back key by key
    @Test
    void badValuesFallBackKeyByKey() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        String text = golden()
                .replace("I:totalStars=1500", "I:totalStars=99999999999")
                .replace("I:fogStart=100", "I:fogStart=-7")
                .replace("I:cullingMode=0", "I:cullingMode=9")
                .replace("I:profile=1", "I:profile=-1")
                .replace("B:water=true", "B:water=perhaps")
                .replace("B:lava=true", "B:lava=FALSE");
        Files.writeString(file.toPath(), text);
        ExtrasConfig options = new ExtrasConfig();
        ExtrasSchema.read(new Configuration(file), options);
        assertEquals(ExtrasConfig.DetailSettings.STARS_DEFAULT, options.detail.totalStars);
        assertEquals(0, options.render.fogStart);
        assertEquals(ExtrasConfig.LeafCulling.DEPTH, options.leaves.cullingMode);
        assertEquals(ExtrasConfig.BudgetProfile.QUALITY, options.renderBudget.profile);
        assertTrue(options.animation.water);
        assertFalse(options.animation.lava);
        assertTrue(options.animation.fire);
    }

    // Checks that write saves the file even when nothing changed
    @Test
    void writeSavesEvenWithoutChanges() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        Configuration config = new Configuration(file);
        ExtrasConfig options = new ExtrasConfig();
        ExtrasSchema.read(config, options);
        assertTrue(file.delete());
        ExtrasSchema.write(config, options);
        assertEquals(golden(), Files.readString(file.toPath()));
        ExtrasSchema.write(config, options);
        assertEquals(golden(), Files.readString(file.toPath()));
    }

    // Checks that null arguments are rejected
    @Test
    void nullArgumentsAreRejected() {
        Configuration config = new Configuration(dir.resolve("impetus-extras.cfg").toFile());
        assertThrows(NullPointerException.class, () -> ExtrasSchema.read(null, new ExtrasConfig()));
        assertThrows(NullPointerException.class, () -> ExtrasSchema.read(config, null));
        assertThrows(NullPointerException.class, () -> ExtrasSchema.write(null, new ExtrasConfig()));
        assertThrows(NullPointerException.class, () -> ExtrasSchema.write(config, null));
    }

    // Checks that every private constructor of the schema classes runs
    @Test
    void theConstructorsRun() {
        assertNotNull(Mixins.construct(ExtrasSchema.class));
        assertNotNull(Mixins.construct(VisualSchema.class));
        assertNotNull(Mixins.construct(RenderSchema.class));
        assertNotNull(Mixins.construct(InterfaceSchema.class));
        assertNotNull(Mixins.construct(SystemSchema.class));
    }

    // Checks that the group table lists each themed group exactly once
    @Test
    void theGroupTableHoldsEveryThemedGroup() {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        for (SettingGroup<?> group : groups()) {
            assertNull(seen.put(group, true));
        }
        for (Object group : List.of(VisualSchema.ANIMATION, VisualSchema.PARTICLE, VisualSchema.DETAIL, VisualSchema.LEAVES,
                RenderSchema.RENDER, RenderSchema.RENDER_BUDGET, RenderSchema.GPU_BOOSTER, RenderSchema.BAKED_ENTITIES,
                RenderSchema.OCCLUSION, RenderSchema.ENTITY_MODELS, InterfaceSchema.EXTRA, InterfaceSchema.HUD,
                InterfaceSchema.TEXT, InterfaceSchema.ZOOM, SystemSchema.ASYNC, SystemSchema.NETWORK,
                SystemSchema.THREADS, SystemSchema.CLIENT_TICK, SystemSchema.LOADING)) {
            assertTrue(seen.containsKey(group));
        }
        assertEquals(19, seen.size());
    }
}
