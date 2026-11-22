package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.mixin.CoarctatioMixinPlugin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CoarctatioConfigEdgeTest {
    @TempDir
    Path dir;

    // Keys of the features removed for VintageFix and FermiumMixins that old files still carry.
    private static final List<String> RETIRED = List.of("deduplicateModelVariants", "poolQuadVertexData", "compactBakedModels",
            "canonicalizeMultipartConditions", "optimizeBlockStates", "blockStateBlacklist", "compactStateProperties",
            "compactModelGraph", "weakenClassLoaderCache", "releaseSpriteData", "deduplicateModelTransforms", "releaseBakeState",
            "lazySearchTrees", "cachePropertyHashes", "cacheStateHashes", "resourceExistenceCache", "stacklessResourceExceptions",
            "fastAtlasStitching", "softStructureTemplates", "modScanCache", "dynamicModels", "dynamicModelsPrebakeItems",
            "dynamicModelsEagerNamespaces", "fastItemLayerBaking", "parallelTextureLoad", "parallelModelLoad",
            "primitiveOreDictionary", "quietPrefixWarnings");

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @AfterEach
    void forget() {
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    private Path file() {
        return dir.resolve("config/impetus-coarctatio.cfg");
    }

    private static Properties read(Path file) throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return props;
    }

    private static List<String> publicSettings() {
        List<String> names = new ArrayList<>();
        for (Field field : CoarctatioConfig.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private void writeOldFile(String... extra) throws Exception {
        Files.createDirectories(file().getParent());
        StringBuilder text = new StringBuilder("# a 1.0.3 file\n");
        for (String key : RETIRED) {
            text.append(key).append(key.equals("blockStateBlacklist") || key.equals("dynamicModelsEagerNamespaces") ? "=a,b" : "=false").append('\n');
        }
        for (String line : extra) {
            text.append(line).append('\n');
        }
        Files.writeString(file(), text.toString());
    }

    @Test
    void noRemovedSettingIsLeftOnTheConfig() {
        List<String> settings = publicSettings();
        for (String key : RETIRED) {
            assertFalse(settings.contains(key), key);
        }
        // What remains is exactly the set the file is written from
        assertEquals(new TreeSet<>(List.of("enabled", "deduplicateResourceLocations", "compactNbtBackingMap", "internNbtKeys",
                "nbtArrayMapThreshold", "stripChunkNbt", "dropEmptyChunkSections", "compactRuntimeCollections", "clearPoolsOnWorldLeave",
                "poolSizeLimit", "showDebugOverlay", "lazyItemStackCapabilities", "poolNbtPrimitives", "internLoaderStrings",
                "canonicalizeModelParts", "compactRemapperCaches", "recycleEvents", "skipSoundDebugChecks", "plainMissingModels")),
                new TreeSet<>(settings));
    }

    @Test
    void everySettingIsWrittenWithANoteAndADefaultAndNothingElseIs() throws Exception {
        CoarctatioConfig.get();
        Properties written = read(file());
        assertEquals(new TreeSet<>(publicSettings()), new TreeSet<>(written.stringPropertyNames()));
        Map<String, String> notes = Mixins.get(CoarctatioConfig.class, "NOTES");
        assertEquals(new TreeSet<>(publicSettings()), new TreeSet<>(notes.keySet()));
        String text = Files.readString(file());
        for (String key : publicSettings()) {
            assertTrue(text.contains("# Default: " + written.getProperty(key) + "\n" + key + "="), key);
        }
    }

    @Test
    void anOldFileWithTheRemovedKeysLoadsAndDropsThemOnSave() throws Exception {
        writeOldFile("stripChunkNbt=false", "poolSizeLimit=4096");
        CoarctatioConfig config = CoarctatioConfig.get();
        // The kept keys are still read
        assertFalse(config.stripChunkNbt);
        assertEquals(4096, config.poolSizeLimit);
        assertTrue(config.canonicalizeModelParts);
        // The file is rewritten without a single removed key
        Properties written = read(file());
        for (String key : RETIRED) {
            assertNull(written.getProperty(key), key);
        }
        assertFalse(Files.readString(file()).contains("a 1.0.3 file"));
        assertEquals("false", written.getProperty("stripChunkNbt"));
        assertEquals("4096", written.getProperty("poolSizeLimit"));
        // Loading the rewritten file again changes nothing
        String once = Files.readString(file());
        Mixins.set(CoarctatioConfig.class, "instance", null);
        CoarctatioConfig.get();
        assertEquals(once, Files.readString(file()));
    }

    @Test
    void anOldFileWithTheMasterSwitchOffKeepsOnlyTheCurrentKeys() throws Exception {
        writeOldFile("enabled=false", "stripChunkNbt=false");
        CoarctatioConfig config = CoarctatioConfig.get();
        assertFalse(config.enabled);
        for (Field field : CoarctatioConfig.class.getFields()) {
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers()) && !field.getName().equals("showDebugOverlay")) {
                assertFalse(field.getBoolean(config), field.getName());
            }
        }
        assertTrue(config.showDebugOverlay);
        Properties written = read(file());
        assertEquals("false", written.getProperty("enabled"));
        // The held file values come back on save, the removed ones do not
        assertEquals("false", written.getProperty("stripChunkNbt"));
        assertEquals("true", written.getProperty("canonicalizeModelParts"));
        for (String key : RETIRED) {
            assertNull(written.getProperty(key), key);
        }
        config.save();
        assertEquals(written, read(file()));
    }

    @Test
    void numbersOutsideTheirRangeOrUnreadableFallBackToTheDefault() throws Exception {
        Files.createDirectories(file().getParent());
        for (String[] bad : new String[][] {{"nbtArrayMapThreshold", "-1"}, {"nbtArrayMapThreshold", "1025"}, {"nbtArrayMapThreshold", "twelve"},
                {"nbtArrayMapThreshold", ""}, {"nbtArrayMapThreshold", "99999999999"}, {"poolSizeLimit", "1023"}, {"poolSizeLimit", "-5"}}) {
            Files.writeString(file(), bad[0] + "=" + bad[1] + "\n");
            Mixins.set(CoarctatioConfig.class, "instance", null);
            CoarctatioConfig config = CoarctatioConfig.get();
            assertEquals(12, config.nbtArrayMapThreshold, String.join("=", bad));
            assertEquals(262144, config.poolSizeLimit, String.join("=", bad));
        }
        // The edges of each range are kept
        Files.writeString(file(), "nbtArrayMapThreshold=0\npoolSizeLimit=1024\n");
        Mixins.set(CoarctatioConfig.class, "instance", null);
        assertEquals(0, CoarctatioConfig.get().nbtArrayMapThreshold);
        assertEquals(1024, CoarctatioConfig.get().poolSizeLimit);
        Files.writeString(file(), "nbtArrayMapThreshold= 1024 \npoolSizeLimit=2147483647\n");
        Mixins.set(CoarctatioConfig.class, "instance", null);
        assertEquals(1024, CoarctatioConfig.get().nbtArrayMapThreshold);
        assertEquals(Integer.MAX_VALUE, CoarctatioConfig.get().poolSizeLimit);
    }

    @Test
    void switchesReadOnlyTrueOrFalseInAnyCase() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "stripChunkNbt=FALSE\nrecycleEvents= True \ncompactNbtBackingMap=no\nplainMissingModels=1\ninternNbtKeys=\n");
        CoarctatioConfig config = CoarctatioConfig.get();
        assertFalse(config.stripChunkNbt);
        assertTrue(config.recycleEvents);
        // Anything else is the default
        assertTrue(config.compactNbtBackingMap);
        assertFalse(config.plainMissingModels);
        assertTrue(config.internNbtKeys);
    }

    @Test
    void theMasterSwitchHoldsEveryFeatureButNotTheOverlay() {
        List<Field> switches = Mixins.get(CoarctatioConfig.class, "FEATURE_SWITCHES");
        Set<String> names = switches.stream().map(Field::getName).collect(Collectors.toSet());
        assertFalse(names.contains("enabled"));
        assertFalse(names.contains("showDebugOverlay"));
        // Numbers are never held
        assertFalse(names.contains("poolSizeLimit"));
        assertFalse(names.contains("nbtArrayMapThreshold"));
        assertEquals(15, names.size(), names.toString());
    }

    @Test
    void everyListedMixinTurnsOffWithItsSwitch() throws Exception {
        CoarctatioMixinPlugin plugin = new CoarctatioMixinPlugin();
        plugin.onLoad("com.bdmajora.coarctatio.mixin");
        CoarctatioConfig config = Mixins.get(plugin, "config");
        for (Field field : Mixins.<List<Field>>get(CoarctatioConfig.class, "FEATURE_SWITCHES")) {
            field.setBoolean(config, false);
        }
        List<String> listed = listed();
        assertFalse(listed.isEmpty());
        for (String mixin : listed) {
            // A mixin that reaches the unwired fallback would answer true here
            assertFalse(plugin.shouldApplyMixin("x", "com.bdmajora.coarctatio.mixin." + mixin), mixin);
        }
        // And each one comes back with its switch alone
        for (Field field : Mixins.<List<Field>>get(CoarctatioConfig.class, "FEATURE_SWITCHES")) {
            field.setBoolean(config, true);
        }
        for (String mixin : listed) {
            String switchName = switchOf(plugin, config, mixin);
            assertNotNull(switchName, mixin + " ignores every switch");
        }
    }

    // Returns the switch that alone turns the mixin on, or null.
    private static String switchOf(CoarctatioMixinPlugin plugin, CoarctatioConfig config, String mixin) throws Exception {
        List<Field> switches = Mixins.get(CoarctatioConfig.class, "FEATURE_SWITCHES");
        String found = null;
        for (Field only : switches) {
            for (Field field : switches) {
                field.setBoolean(config, field == only);
            }
            if (plugin.shouldApplyMixin("x", "com.bdmajora.coarctatio.mixin." + mixin)) {
                found = only.getName();
            }
        }
        for (Field field : switches) {
            field.setBoolean(config, true);
        }
        return found;
    }

    private static List<String> listed() throws Exception {
        try (InputStream in = CoarctatioConfigEdgeTest.class.getResourceAsStream("/mixins.coarctatio.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> mixins = new ArrayList<>();
            for (String section : List.of("mixins", "client", "server")) {
                if (json.has(section)) {
                    json.getAsJsonArray(section).forEach(element -> mixins.add(element.getAsString()));
                }
            }
            return mixins;
        }
    }

    @Test
    void theMixinListAndTheMixinClassesMatchExactly() throws Exception {
        Set<String> listed = new HashSet<>(listed());
        assertEquals(listed.size(), listed().size(), "a mixin is listed twice");
        // The guarded mixins are handed out by the plugin instead of the list
        Set<String> guarded = Set.of("forge.ASMDataMixin", "forge.ModCandidateMixin", "events.TickEventMixin", "events.PlayerTickEventMixin",
                "events.WorldTickEventMixin", "events.RenderTickEventMixin", "events.AttachCapabilitiesEventMixin", "events.BlockEventMixin",
                "events.NeighborNotifyEventMixin", "events.FMLCommonHandlerMixin", "events.ForgeEventFactoryMixin");
        Set<String> classes = new HashSet<>();
        Path root = mixinRoot();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".class") && !p.getFileName().toString().contains("$"))
                    .map(p -> root.relativize(p).toString().replace('/', '.').replace(".class", ""))
                    .filter(name -> !name.equals("CoarctatioMixinPlugin"))
                    .forEach(classes::add);
        }
        Set<String> expected = new HashSet<>(listed);
        expected.addAll(guarded);
        assertEquals(new TreeSet<>(expected), new TreeSet<>(classes));
        assertTrue(listed.stream().noneMatch(guarded::contains), "a guarded mixin is also listed");
    }

    private static Path mixinRoot() throws URISyntaxException {
        return Paths.get(CoarctatioMixinPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("com/bdmajora/coarctatio/mixin");
    }

    @Test
    void theRemovedMixinNamesAreTreatedAsUnknown() {
        CoarctatioMixinPlugin plugin = new CoarctatioMixinPlugin();
        plugin.onLoad("com.bdmajora.coarctatio.mixin");
        // Nothing removed is wired to a switch any more, so a stale entry would hit the logged fallback
        for (String removed : List.of("forge.OreDictionaryMixin", "client.SearchTreeMixin", "state.StateImplementationHashMixin",
                "client.model.dynamic.ModelManagerDynamicMixin", "client.resources.FallbackResourceManagerMixin")) {
            CoarctatioConfig config = CoarctatioConfig.get();
            for (Field field : Mixins.<List<Field>>get(CoarctatioConfig.class, "FEATURE_SWITCHES")) {
                try {
                    field.setBoolean(config, false);
                } catch (IllegalAccessException e) {
                    fail(e);
                }
            }
            assertTrue(plugin.shouldApplyMixin("x", "com.bdmajora.coarctatio.mixin." + removed), removed);
        }
    }
}
