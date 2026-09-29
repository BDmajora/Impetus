package com.bdmajora.extras;

import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.impetus.impl.gui.Localized;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtrasConfigTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.client();
    }

    @BeforeEach
    void gameDirectory() {
        // Forge's Configuration resolves paths against the injected game directory, the config helper against the launch one
        Statics.set(FMLInjectionData.class, "minecraftHome", dir.toFile());
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(Extras.class, "config", null);
    }

    @AfterEach
    void forgetTheConfig() {
        Mixins.set(Extras.class, "config", null);
        ParticleClassRegistry.getInstance().loadDisabledClasses(new String[0]);
    }

    @Test
    void everySettingIsReadFromAndWrittenBackToTheFile() throws Exception {
        File file = dir.resolve("impetus-extras.cfg").toFile();
        ExtrasConfig config = ExtrasConfig.load(file);
        // A fresh file gets every key written out with its default
        assertTrue(file.isFile());
        assertTrue(config.animation.all);
        assertEquals(ExtrasConfig.OverlayCorner.TOP_LEFT, config.extra.overlayCorner);
        assertEquals(ExtrasConfig.DetailSettings.STARS_DEFAULT, config.detail.totalStars);

        config.animation.water = false;
        config.extra.overlayCorner = ExtrasConfig.OverlayCorner.BOTTOM_RIGHT;
        config.detail.totalStars = 2000;
        config.async.synchronizedEntities = new String[] {"examplemod:*"};
        ParticleClassRegistry.getInstance().loadDisabledClasses(new String[] {"net.minecraft.client.particle.ParticleFlame"});
        config.writeChanges();
        assertFalse(ParticleClassRegistry.getInstance().isDirty());

        ExtrasConfig reread = ExtrasConfig.load(file);
        assertFalse(reread.animation.water);
        assertEquals(ExtrasConfig.OverlayCorner.BOTTOM_RIGHT, reread.extra.overlayCorner);
        assertEquals(2000, reread.detail.totalStars);
        assertArrayEquals(new String[] {"examplemod:*"}, reread.async.synchronizedEntities);
        assertTrue(Files.readString(file.toPath()).contains("net.minecraft.client.particle.ParticleFlame"));

        // A config that never had a file behind it has nothing to write to
        new ExtrasConfig().writeChanges();
    }

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

    @Test
    void everyChoiceNamesItsLangKey() {
        for (List<? extends Localized> values : List.of(
                List.of(ExtrasConfig.OverlayCorner.values()), List.of(ExtrasConfig.LeafCulling.values()),
                List.of(ExtrasConfig.TextContrast.values()), List.of(ExtrasConfig.CloudTranslucency.values()),
                List.of(ExtrasConfig.FogShape.values()), List.of(ExtrasConfig.TimeOverride.values()),
                List.of(ExtrasConfig.WeatherOverride.values()), List.of(ExtrasConfig.BudgetProfile.values()),
                List.of(ExtrasConfig.VerticalSync.values()))) {
            for (Localized value : values) {
                assertFalse(value.translationKey().isEmpty());
            }
        }
        assertTrue(ExtrasConfig.OverlayCorner.BOTTOM_LEFT.isBottom());
        assertFalse(ExtrasConfig.OverlayCorner.TOP_RIGHT.isBottom());
        assertTrue(ExtrasConfig.OverlayCorner.TOP_RIGHT.isRight());
        assertFalse(ExtrasConfig.OverlayCorner.BOTTOM_LEFT.isRight());
        // The shape's ordinal is the shader's uniform value
        assertEquals(2, ExtrasConfig.FogShape.RADIAL.shaderIndex());
        assertTrue(ExtrasConfig.BudgetProfile.PERFORMANCE.targetFrameMillis < ExtrasConfig.BudgetProfile.QUALITY.targetFrameMillis);
    }
}
