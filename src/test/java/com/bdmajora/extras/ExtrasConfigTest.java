package com.bdmajora.extras;

import com.bdmajora.extras.ExtrasConfig.AsyncSettings;
import com.bdmajora.extras.ExtrasConfig.BudgetProfile;
import com.bdmajora.extras.ExtrasConfig.CloudTranslucency;
import com.bdmajora.extras.ExtrasConfig.DetailSettings;
import com.bdmajora.extras.ExtrasConfig.ExtraSettings;
import com.bdmajora.extras.ExtrasConfig.FogShape;
import com.bdmajora.extras.ExtrasConfig.HudSettings;
import com.bdmajora.extras.ExtrasConfig.LeafCulling;
import com.bdmajora.extras.ExtrasConfig.LeafSettings;
import com.bdmajora.extras.ExtrasConfig.NetworkSettings;
import com.bdmajora.extras.ExtrasConfig.OcclusionSettings;
import com.bdmajora.extras.ExtrasConfig.OverlayCorner;
import com.bdmajora.extras.ExtrasConfig.RenderBudgetSettings;
import com.bdmajora.extras.ExtrasConfig.RenderSettings;
import com.bdmajora.extras.ExtrasConfig.TextContrast;
import com.bdmajora.extras.ExtrasConfig.ThreadSettings;
import com.bdmajora.extras.ExtrasConfig.TimeOverride;
import com.bdmajora.extras.ExtrasConfig.VerticalSync;
import com.bdmajora.extras.ExtrasConfig.WeatherOverride;
import com.bdmajora.extras.ExtrasConfig.ZoomSettings;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.FastMath;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.extras.client.particle.ParticleTicker;
import com.bdmajora.extras.config.ExtrasRuntime;
import com.bdmajora.extras.config.RegistryState;
import com.bdmajora.impetus.impl.gui.Localized;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.particle.ParticleFlame;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the Extras config facade and its enums and settings groups
class ExtrasConfigTest {
    // Scratch game directory of each case
    @TempDir
    Path dir;

    // Launch switch of parallel ticking before each case
    private boolean installed;

    // Boots the game classes that Forge configuration paths and thread tuning need
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    // Points both game directories at the scratch directory and forgets the module config
    @BeforeEach
    void gameDirectory() {
        Statics.set(FMLInjectionData.class, "minecraftHome", dir.toFile());
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(Extras.class, "config", null);
        RegistryState.clear();
        installed = ParallelProcessor.INSTALLED;
    }

    // Forgets the module config and puts the registry and the subsystems back to their defaults
    @AfterEach
    void forgetTheConfig() {
        Mixins.set(Extras.class, "config", null);
        RegistryState.clear();
        ExtrasRuntime.apply(new ExtrasConfig());
        ParallelProcessor.INSTALLED = installed;
    }

    // Returns the config file in the scratch directory
    private File file() {
        return dir.resolve("impetus-extras.cfg").toFile();
    }

    // Returns the text of the file that a fresh config writes
    private static String golden() throws Exception {
        return resource("/com/bdmajora/extras/config/default-impetus-extras.cfg");
    }

    // Returns the text of a classpath resource
    private static String resource(String path) throws Exception {
        try (InputStream in = ExtrasConfigTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Moves several subsystems away from the defaults
    private static void disturbSubsystems() {
        ParticleTicker.enabled = false;
        BakedEntities.chests = false;
        FastMath.enabled = true;
    }

    // Asserts that the subsystems moved by disturbSubsystems hold the defaults
    private static void assertSubsystemDefaults() {
        assertTrue(ParticleTicker.enabled);
        assertTrue(BakedEntities.chests);
        assertFalse(FastMath.enabled);
    }

    // Checks that a null file is rejected before any subsystem changes
    @Test
    void aNullFileIsRejectedBeforeAnythingIsApplied() {
        disturbSubsystems();
        assertThrows(NullPointerException.class, () -> ExtrasConfig.load(null));
        assertFalse(ParticleTicker.enabled);
    }

    // Checks that a missing file and its folders are created with every default and the defaults are applied
    @Test
    void aMissingFileIsCreatedWithEveryDefault() throws Exception {
        disturbSubsystems();
        File file = dir.resolve("a/b/impetus-extras.cfg").toFile();
        ExtrasConfig options = ExtrasConfig.load(file);
        assertEquals(golden(), Files.readString(file.toPath()));
        assertTrue(options.animation.all);
        assertEquals(OverlayCorner.TOP_LEFT, options.extra.overlayCorner);
        assertEquals(DetailSettings.STARS_DEFAULT, options.detail.totalStars);
        assertArrayEquals(AsyncSettings.DEFAULT_SYNCHRONIZED, options.async.synchronizedEntities);
        assertSubsystemDefaults();
        assertTrue(ParallelProcessor.INSTALLED);
    }

    // Checks that stored values are read and applied to the subsystems
    @Test
    void storedValuesAreReadAndApplied() throws Exception {
        String text = golden()
                .replace("B:parallelTick=true", "B:parallelTick=false")
                .replace("I:overlayCorner=0", "I:overlayCorner=3")
                .replace("I:totalStars=1500", "I:totalStars=2000")
                .replace("        minecraft:tnt\n", "        examplemod:*\n");
        Files.writeString(file().toPath(), text);
        ExtrasConfig options = ExtrasConfig.load(file());
        assertFalse(options.particle.parallelTick);
        assertFalse(ParticleTicker.enabled);
        assertEquals(OverlayCorner.BOTTOM_RIGHT, options.extra.overlayCorner);
        assertEquals(2000, options.detail.totalStars);
        assertEquals("examplemod:*", options.async.synchronizedEntities[0]);
        assertEquals(text, Files.readString(file().toPath()));
    }

    // Checks that a complete file is read without being written again
    @Test
    void aCompleteFileIsNotRewritten() throws Exception {
        ExtrasConfig.load(file());
        assertTrue(file().setLastModified(1_000_000L));
        ExtrasConfig first = ExtrasConfig.load(file());
        ExtrasConfig second = ExtrasConfig.load(file());
        assertNotSame(first, second);
        assertEquals(1_000_000L, file().lastModified());
    }

    // Checks that a directory in place of the file loads defaults without throwing
    @Test
    void aDirectoryInPlaceOfTheFileLoadsDefaults() {
        File folder = dir.resolve("folder.cfg").toFile();
        assertTrue(folder.mkdir());
        ExtrasConfig options = ExtrasConfig.load(folder);
        assertTrue(options.animation.all);
        assertDoesNotThrow(options::writeChanges);
        assertTrue(folder.isDirectory());
    }

    // Checks that a failed read returns unsaved defaults on the same file and applies them
    @Test
    void aFailedReadFallsBackToUnsavedDefaultsThatAreApplied() {
        disturbSubsystems();
        ParticleTicker.lightCache = false;
        try (MockedConstruction<Configuration> broken = mockConstruction(Configuration.class, (mock, context) ->
                when(mock.getBoolean(anyString(), anyString(), anyBoolean(), anyString())).thenThrow(new IllegalStateException("broken")))) {
            ExtrasConfig options = ExtrasConfig.load(file());
            Configuration config = broken.constructed().get(0);
            assertSame(config, Mixins.get(options, "config"));
            verify(config, never()).save();
            assertTrue(options.animation.water);
            assertTrue(options.particle.lightCache);
        }
        assertSubsystemDefaults();
        assertTrue(ParticleTicker.lightCache);
        assertFalse(file().exists());
    }

    // Checks that a detached config writes nothing and changes no subsystem
    @Test
    void aDetachedConfigWritesNothing() {
        disturbSubsystems();
        ExtrasConfig detached = new ExtrasConfig();
        detached.writeChanges();
        detached.writeChanges();
        assertFalse(ParticleTicker.enabled);
        assertEquals(0, dir.toFile().list().length);
    }

    // Checks that writeChanges persists the settings and the registry and applies the settings
    @Test
    void writeChangesPersistsAndApplies() throws Exception {
        ExtrasConfig options = ExtrasConfig.load(file());
        options.animation.water = false;
        options.extra.overlayCorner = OverlayCorner.BOTTOM_RIGHT;
        options.detail.totalStars = 2000;
        options.particle.parallelTick = false;
        options.async.synchronizedEntities = new String[] {"examplemod:*"};
        ParticleClassRegistry.getInstance().loadDisabledClasses(new String[] {"net.minecraft.client.particle.ParticleFlame"});
        ParticleClassRegistry.getInstance().recordClass(ParticleFlame.class);
        options.writeChanges();
        assertFalse(ParticleClassRegistry.getInstance().isDirty());
        assertFalse(ParticleTicker.enabled);

        ExtrasConfig reread = ExtrasConfig.load(file());
        assertFalse(reread.animation.water);
        assertEquals(OverlayCorner.BOTTOM_RIGHT, reread.extra.overlayCorner);
        assertEquals(2000, reread.detail.totalStars);
        assertArrayEquals(new String[] {"examplemod:*"}, reread.async.synchronizedEntities);
        String text = Files.readString(file().toPath());
        assertTrue(text.contains("        net.minecraft.client.particle.ParticleFlame\n"));
        assertTrue(text.contains("        net.minecraft.client.particle.ParticleFlame|ParticleFlame\n"));
    }

    // Checks that writeChanges writes a deleted file again and that repeated writes leave the same text
    @Test
    void writeChangesRecreatesTheFileAndRepeatsIdentically() throws Exception {
        ExtrasConfig options = ExtrasConfig.load(file());
        assertTrue(file().delete());
        options.writeChanges();
        String first = Files.readString(file().toPath());
        options.writeChanges();
        assertEquals(first, Files.readString(file().toPath()));
        assertEquals(golden(), first);
    }

    // Checks that a write puts back every comment missing from a stored file
    @Test
    void aWriteRestoresCommentsMissingFromTheFile() throws Exception {
        StringBuilder bare = new StringBuilder();
        for (String line : golden().split("\n", -1)) {
            if (!line.trim().startsWith("# ") || line.startsWith("# Configuration")) {
                bare.append(line).append('\n');
            }
        }
        Files.writeString(file().toPath(), bare.substring(0, bare.length() - 1));
        ExtrasConfig options = ExtrasConfig.load(file());
        options.writeChanges();
        assertFalse(bare.toString().contains("# Animate water textures"));
        assertEquals(golden(), Files.readString(file().toPath()));
    }

    // Checks that a null choice or list or list entry fails the write before the file changes
    @Test
    void aNullFieldFailsTheWriteBeforeTheFileChanges() throws Exception {
        ExtrasConfig options = ExtrasConfig.load(file());
        String before = Files.readString(file().toPath());
        options.animation.water = false;
        options.render.fogShape = null;
        assertThrows(NullPointerException.class, options::writeChanges);
        options.render.fogShape = FogShape.VANILLA;
        options.async.synchronizedEntities = null;
        assertThrows(NullPointerException.class, options::writeChanges);
        options.async.synchronizedEntities = new String[] {"minecraft:tnt", null};
        assertThrows(NullPointerException.class, options::writeChanges);
        assertEquals(before, Files.readString(file().toPath()));
        options.async.synchronizedEntities = new String[0];
        options.writeChanges();
        assertFalse(ExtrasConfig.load(file()).animation.water);
    }

    // Checks that a class discovered while the file is saved keeps the registry dirty
    @Test
    void aClassDiscoveredDuringTheSaveStaysDirty() {
        ExtrasConfig options = ExtrasConfig.load(file());
        Mixins.set(options, "config", new Configuration(file()) {
            // Records a class before the file is written
            @Override
            public void save() {
                ParticleClassRegistry.getInstance().recordClass(ParticleFlame.class);
                super.save();
            }
        });
        options.writeChanges();
        assertTrue(ParticleClassRegistry.getInstance().isDirty());
    }

    // Checks that the module loads its config once and saves it under the game directory
    @Test
    void theModuleLoadsItsConfigOnceAndSavesItUnderTheGameDirectory() {
        Extras.initialize();
        ExtrasConfig options = Extras.options();
        assertSame(options, Extras.options());
        options.detail.stars = false;
        Extras.save();
        assertTrue(Files.isRegularFile(dir.resolve("config/impetus-extras.cfg")));
        Mixins.set(Extras.class, "config", null);
        assertFalse(Extras.options().detail.stars);
        assertNotNull(Mixins.construct(Extras.class));
    }

    // Checks that every choice names a distinct lang key that the lang file defines
    @Test
    void everyChoiceNamesADistinctDefinedLangKey() throws Exception {
        Properties lang = new Properties();
        lang.load(new StringReader(resource("/assets/impetus/lang/en_US.lang")));
        for (Class<? extends Enum<?>> type : List.of(OverlayCorner.class, LeafCulling.class, TextContrast.class,
                CloudTranslucency.class, FogShape.class, TimeOverride.class, WeatherOverride.class, BudgetProfile.class,
                VerticalSync.class)) {
            Set<String> keys = new HashSet<>();
            for (Enum<?> constant : type.getEnumConstants()) {
                String key = ((Localized) constant).translationKey();
                assertFalse(key.isEmpty(), constant.name());
                assertTrue(keys.add(key), key);
                if (key.startsWith("impetus.")) {
                    assertTrue(lang.containsKey(key), key);
                }
            }
        }
        assertEquals("options.off", VerticalSync.OFF.translationKey());
        assertEquals("options.on", VerticalSync.ON.translationKey());
    }

    // Checks that every choice keeps the constant order the file stores by ordinal
    @Test
    void everyChoiceKeepsItsStoredOrder() {
        assertEquals("[TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT]", Arrays.toString(OverlayCorner.values()));
        assertEquals("[DEFAULT, CHECK, DEPTH]", Arrays.toString(LeafCulling.values()));
        assertEquals("[NONE, BACKGROUND, SHADOW]", Arrays.toString(TextContrast.values()));
        assertEquals("[DEFAULT, ALWAYS, NEVER]", Arrays.toString(CloudTranslucency.values()));
        assertEquals("[VANILLA, CYLINDRICAL, RADIAL, PLANAR]", Arrays.toString(FogShape.values()));
        assertEquals("[DEFAULT, DAY, NIGHT]", Arrays.toString(TimeOverride.values()));
        assertEquals("[DEFAULT, CLEAR, RAIN, THUNDER]", Arrays.toString(WeatherOverride.values()));
        assertEquals("[QUALITY, BALANCED, PERFORMANCE]", Arrays.toString(BudgetProfile.values()));
        assertEquals("[OFF, ON, ADAPTIVE]", Arrays.toString(VerticalSync.values()));
    }

    // Checks the edges of every overlay corner
    @Test
    void everyOverlayCornerReportsItsEdges() {
        assertFalse(OverlayCorner.TOP_LEFT.isBottom());
        assertFalse(OverlayCorner.TOP_LEFT.isRight());
        assertFalse(OverlayCorner.TOP_RIGHT.isBottom());
        assertTrue(OverlayCorner.TOP_RIGHT.isRight());
        assertTrue(OverlayCorner.BOTTOM_LEFT.isBottom());
        assertFalse(OverlayCorner.BOTTOM_LEFT.isRight());
        assertTrue(OverlayCorner.BOTTOM_RIGHT.isBottom());
        assertTrue(OverlayCorner.BOTTOM_RIGHT.isRight());
    }

    // Checks that every fog shape index matches the constant of the fog shader include
    @Test
    void everyFogShapeMatchesTheShaderConstant() throws Exception {
        Map<String, Integer> shader = new HashMap<>();
        Matcher matcher = Pattern.compile("const int FOG_SHAPE_(\\w+) = (\\d+);")
                .matcher(resource("/assets/impetus/shaders/include/fog.glsl"));
        while (matcher.find()) {
            shader.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        assertEquals(shader.get("SPHERICAL"), FogShape.VANILLA.shaderIndex());
        assertEquals(shader.get("CYLINDRICAL"), FogShape.CYLINDRICAL.shaderIndex());
        assertEquals(shader.get("RADIAL"), FogShape.RADIAL.shaderIndex());
        assertEquals(shader.get("PLANAR"), FogShape.PLANAR.shaderIndex());
        for (FogShape shape : FogShape.values()) {
            assertEquals(shape.ordinal(), shape.shaderIndex());
        }
    }

    // Checks that each more aggressive profile aims for a shorter positive frame time
    @Test
    void budgetProfilesAimForShorterFramesAsTheyGrowAggressive() {
        BudgetProfile[] profiles = BudgetProfile.values();
        for (int i = 0; i < profiles.length; i++) {
            assertTrue(Double.isFinite(profiles[i].targetFrameMillis));
            assertTrue(profiles[i].targetFrameMillis > 0);
            if (i > 0) {
                assertTrue(profiles[i].targetFrameMillis < profiles[i - 1].targetFrameMillis);
            }
        }
    }

    // Checks that every range constant holds its default
    @Test
    void everyRangeHoldsItsDefault() {
        assertRange(DetailSettings.STARS_MIN, DetailSettings.STARS_DEFAULT, DetailSettings.STARS_MAX);
        assertRange(RenderSettings.CLOUD_SCALE_MIN, RenderSettings.CLOUD_SCALE_VANILLA, RenderSettings.CLOUD_SCALE_MAX);
        assertRange(ExtraSettings.STEADY_HUD_REFRESH_MIN, ExtraSettings.STEADY_HUD_REFRESH_DEFAULT, ExtraSettings.STEADY_HUD_REFRESH_MAX);
        assertRange(ExtraSettings.AUTOSAVE_MIN_TICKS, ExtraSettings.AUTOSAVE_VANILLA_TICKS, ExtraSettings.AUTOSAVE_MAX_TICKS);
        assertRange(RenderBudgetSettings.PARTICLE_BUDGET_MIN, RenderBudgetSettings.PARTICLE_BUDGET_DEFAULT, RenderBudgetSettings.PARTICLE_BUDGET_MAX);
        assertRange(RenderBudgetSettings.ENTITY_DISTANCE_MIN, RenderBudgetSettings.ENTITY_DISTANCE_DEFAULT, RenderBudgetSettings.ENTITY_DISTANCE_MAX);
        assertRange(RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN, RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_DEFAULT,
                RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX);
        assertRange(NetworkSettings.TIMEOUT_MIN, NetworkSettings.READ_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MAX);
        assertRange(NetworkSettings.TIMEOUT_MIN, NetworkSettings.LOGIN_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MAX);
        assertRange(NetworkSettings.TIMEOUT_MIN, NetworkSettings.KEEP_ALIVE_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MAX);
        assertRange(ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_NORMAL, ThreadSettings.PRIORITY_MAX);
        assertRange(ThreadSettings.PRIORITY_MIN, ThreadSettings.CHUNK_BUILDER_DEFAULT, ThreadSettings.PRIORITY_MAX);
        assertRange(1, OcclusionSettings.MAX_ENTITY_SIZE_DEFAULT, 32);
        assertRange(1, OcclusionSettings.MAX_BLOCK_ENTITY_SIZE_DEFAULT, 32);
        assertRange(0, OcclusionSettings.SLACK_DEFAULT, 300);
        assertRange(HudSettings.CACHE_FPS_MIN, HudSettings.CACHE_FPS_DEFAULT, HudSettings.CACHE_FPS_MAX);
        assertRange(LeafSettings.DEPTH_MIN, LeafSettings.DEPTH_DEFAULT, LeafSettings.DEPTH_MAX);
        assertRange(ZoomSettings.LEVEL_MIN, ZoomSettings.LEVEL_DEFAULT, ZoomSettings.LEVEL_MAX);
        assertTrue(ZoomSettings.LEVEL_MAX < ZoomSettings.LEVEL_SCALE);
        assertTrue(ZoomSettings.LEVEL_MIN > 0);
        assertTrue(AsyncSettings.THREADS_MAX > 0);
        assertEquals(Thread.MIN_PRIORITY, ThreadSettings.PRIORITY_MIN);
        assertEquals(Thread.MAX_PRIORITY, ThreadSettings.PRIORITY_MAX);
    }

    // Asserts that the default lies inside the inclusive range
    private static void assertRange(int min, int defaultValue, int max) {
        assertTrue(min <= defaultValue && defaultValue <= max, min + " " + defaultValue + " " + max);
    }

    // Checks that every settings group starts at the defaults its constants name
    @Test
    void everySettingsGroupStartsAtItsNamedDefaults() {
        ExtrasConfig options = new ExtrasConfig();
        assertEquals(DetailSettings.STARS_DEFAULT, options.detail.totalStars);
        assertEquals(RenderSettings.CLOUD_SCALE_VANILLA, options.render.cloudScale);
        assertEquals(FogShape.VANILLA, options.render.fogShape);
        assertEquals(CloudTranslucency.DEFAULT, options.render.cloudTranslucency);
        assertEquals(ExtraSettings.STEADY_HUD_REFRESH_DEFAULT, options.extra.steadyDebugHudRefreshInterval);
        assertEquals(ExtraSettings.AUTOSAVE_VANILLA_TICKS, options.extra.autosaveInterval);
        assertEquals(TextContrast.SHADOW, options.extra.textContrast);
        assertEquals(BudgetProfile.BALANCED, options.renderBudget.profile);
        assertEquals(RenderBudgetSettings.PARTICLE_BUDGET_DEFAULT, options.renderBudget.particleBudget);
        assertEquals(RenderBudgetSettings.ENTITY_DISTANCE_DEFAULT, options.renderBudget.entityCullDistance);
        assertEquals(RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_DEFAULT, options.renderBudget.blockEntityDistance);
        assertEquals(NetworkSettings.READ_TIMEOUT_DEFAULT, options.network.readTimeoutSeconds);
        assertEquals(NetworkSettings.LOGIN_TIMEOUT_DEFAULT, options.network.loginTimeoutSeconds);
        assertEquals(NetworkSettings.KEEP_ALIVE_TIMEOUT_DEFAULT, options.network.keepAliveTimeoutSeconds);
        assertEquals(ThreadSettings.PRIORITY_NORMAL, options.threads.renderThreadPriority);
        assertEquals(ThreadSettings.PRIORITY_NORMAL, options.threads.serverThreadPriority);
        assertEquals(ThreadSettings.CHUNK_BUILDER_DEFAULT, options.threads.chunkBuilderPriority);
        assertEquals(OcclusionSettings.MAX_ENTITY_SIZE_DEFAULT, options.occlusion.maxEntitySize);
        assertEquals(OcclusionSettings.MAX_BLOCK_ENTITY_SIZE_DEFAULT, options.occlusion.maxBlockEntitySize);
        assertEquals(OcclusionSettings.SLACK_DEFAULT, options.occlusion.raycastSlack);
        assertEquals(HudSettings.CACHE_FPS_DEFAULT, options.hud.cacheFps);
        assertEquals(LeafSettings.DEPTH_DEFAULT, options.leaves.cullingDepth);
        assertEquals(LeafCulling.DEFAULT, options.leaves.cullingMode);
        assertEquals(ZoomSettings.LEVEL_DEFAULT, options.zoom.level);
        assertEquals(OverlayCorner.TOP_LEFT, options.extra.overlayCorner);
        assertEquals(TimeOverride.DEFAULT, options.extra.timeOverride);
        assertEquals(WeatherOverride.DEFAULT, options.extra.weatherOverride);
    }

    // Checks that every async settings instance owns a separate copy of the default entity list
    @Test
    void eachAsyncSettingsOwnsItsEntityList() {
        AsyncSettings first = new AsyncSettings();
        AsyncSettings second = new AsyncSettings();
        assertNotSame(first.synchronizedEntities, second.synchronizedEntities);
        assertNotSame(AsyncSettings.DEFAULT_SYNCHRONIZED, first.synchronizedEntities);
        assertArrayEquals(AsyncSettings.DEFAULT_SYNCHRONIZED, first.synchronizedEntities);
        first.synchronizedEntities[0] = "changed";
        assertEquals("minecraft:tnt", second.synchronizedEntities[0]);
        assertEquals("minecraft:tnt", AsyncSettings.DEFAULT_SYNCHRONIZED[0]);
    }

    // Checks that two fresh configs share no settings group
    @Test
    void freshConfigsShareNoGroup() {
        ExtrasConfig first = new ExtrasConfig();
        ExtrasConfig second = new ExtrasConfig();
        assertNotSame(first.animation, second.animation);
        assertNotSame(first.async, second.async);
        assertNull(Mixins.get(first, "config"));
    }
}
