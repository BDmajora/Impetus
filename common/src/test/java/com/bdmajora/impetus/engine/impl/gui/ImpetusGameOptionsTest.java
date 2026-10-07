package com.bdmajora.impetus.engine.impl.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ImpetusGameOptionsTest {
    @Test
    void loadCreatesSavesAndReloadsTheFile() throws IOException {
        String name = "impetus-options-test-" + System.nanoTime() + ".json";
        Path path = Paths.get("config", name);
        Files.deleteIfExists(path);
        ImpetusGameOptions fresh = ImpetusGameOptions.load(name);
        assertTrue(Files.exists(path));
        assertEquals(name, fresh.getFileName());
        assertSame(fresh, fresh.getData());
        fresh.quality.cloudHeight = 200;
        fresh.notifications.forceDisableDonationPrompts = true;
        fresh.save();
        ImpetusGameOptions reloaded = ImpetusGameOptions.load(name);
        assertEquals(200, reloaded.quality.cloudHeight);
        assertFalse(reloaded.notifications.forceDisableDonationPrompts);
        assertFalse(reloaded.isReadOnly());
        reloaded.setReadOnly();
        assertTrue(reloaded.isReadOnly());
        assertThrows(IllegalStateException.class, () -> ImpetusGameOptions.writeToDisk(reloaded));
        assertThrows(RuntimeException.class, reloaded::save);
        Files.writeString(path, "{ not json");
        ImpetusGameOptions fallback = ImpetusGameOptions.load(name);
        assertEquals(128, fallback.quality.cloudHeight);
        assertEquals("{ not json", Files.readString(path));
        Files.delete(path);
        ImpetusGameOptions defaults = ImpetusGameOptions.defaults();
        assertEquals("impetus-options.json", defaults.getFileName());
        assertEquals(0, defaults.fullscreenResolution);
        assertEquals(ImpetusGameOptions.FullscreenMode.OFF, defaults.fullscreenMode);
    }

    @Test
    void writeRejectsAFileWhereTheDirectoryShouldBe() throws IOException, ReflectiveOperationException {
        Path blocker = Files.createTempFile("impetus-not-a-dir", "");
        ImpetusGameOptions options = new ImpetusGameOptions();
        var field = ImpetusGameOptions.class.getDeclaredField("configPath");
        field.setAccessible(true);
        field.set(options, blocker.resolve("x.json"));
        assertThrows(IOException.class, () -> ImpetusGameOptions.writeToDisk(options));
        Files.delete(blocker);
        ImpetusGameOptions plain = ImpetusGameOptions.load();
        assertEquals("impetus-options.json", plain.getFileName());
    }

    @Test
    void enumsAnswerTheirQueries() {
        assertTrue(ImpetusGameOptions.GraphicsQuality.FANCY.isFancy(false));
        assertTrue(ImpetusGameOptions.GraphicsQuality.DEFAULT.isFancy(true));
        assertFalse(ImpetusGameOptions.GraphicsQuality.DEFAULT.isFancy(false));
        assertFalse(ImpetusGameOptions.GraphicsQuality.FAST.isFancy(true));
        assertEquals("[options.gamma.default, generator.default]", ImpetusGameOptions.GraphicsQuality.DEFAULT.getLocalizedName().toString());
        assertFalse(ImpetusGameOptions.DeferChunkUpdatesMode.ZERO_FRAMES.defersInvisible());
        assertTrue(ImpetusGameOptions.DeferChunkUpdatesMode.ONE_FRAME.defersInvisible());
        assertFalse(ImpetusGameOptions.DeferChunkUpdatesMode.ONE_FRAME.defersVisible());
        assertTrue(ImpetusGameOptions.DeferChunkUpdatesMode.ALWAYS.defersVisible());
        assertNotNull(ImpetusGameOptions.DeferChunkUpdatesMode.ALWAYS.getLocalizedName());
        assertNotNull(ImpetusGameOptions.InactivityFpsLimit.AFK.getLocalizedName());
        assertTrue(ImpetusGameOptions.QuadSplittingMode.ENABLED.isEnabled());
        assertFalse(ImpetusGameOptions.QuadSplittingMode.DISABLED.isEnabled());
        assertNotNull(ImpetusGameOptions.QuadSplittingMode.ENABLED.getLocalizedName());
        assertNotNull(ImpetusGameOptions.PixelFilteringMode.LINEAR.getLocalizedName());
        assertEquals(1, ImpetusGameOptions.FluidloggingGuess.TOUCHING.minSides);
        assertEquals(Integer.MAX_VALUE, ImpetusGameOptions.FluidloggingGuess.OFF.minSides);
        assertNotNull(ImpetusGameOptions.FluidloggingGuess.SURROUNDED.getLocalizedName());
        assertNotNull(ImpetusGameOptions.FullscreenMode.EXCLUSIVE.getLocalizedName());
        assertNotNull(ImpetusGameOptions.MeshTranslucencySorting.QUADS.getLocalizedName());
        assertNotNull(ImpetusGameOptions.MeshStatistics.SECTIONS.getLocalizedName());
        assertTrue(ImpetusGameOptions.MeshStatistics.QUADS.includes(ImpetusGameOptions.MeshStatistics.REGIONS));
        assertFalse(ImpetusGameOptions.MeshStatistics.FRUSTUM.includes(ImpetusGameOptions.MeshStatistics.REGIONS));
        assertTrue(new ImpetusGameOptions().meshTerrain.enabled);
    }
}
