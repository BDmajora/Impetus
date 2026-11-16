package com.bdmajora.impetus.engine.impl.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

// Tests loading and writing of damaged config files.
class ImpetusGameOptionsFileTest {
    // Name of the config file used by the current test.
    private final String name = "impetus-options-file-test-" + System.nanoTime() + ".json";
    // Path of the config file used by the current test.
    private final Path path = Paths.get("config", this.name);

    // Removes the config and its temporary file.
    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(this.path);
        Files.deleteIfExists(this.path.resolveSibling(this.name + ".tmp"));
    }

    // Uses the defaults for an empty file and for a json null.
    @Test
    void emptyAndNullFilesUseDefaults() throws IOException {
        Files.createDirectories(this.path.getParent());
        Files.writeString(this.path, "");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
        Files.writeString(this.path, "null");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
        Files.writeString(this.path, "   ");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
    }

    // Replaces null groups and unknown enum values with the defaults.
    @Test
    void nullsAndUnknownEnumsAreRestored() throws IOException {
        Files.createDirectories(this.path.getParent());
        Files.writeString(this.path, "{\"quality\":null,\"performance\":{\"defer_chunk_updates_mode\":\"SIDEWAYS\",\"chunk_builder_threads\":3},\"graphics_api\":\"VULKAN\",\"fullscreen_mode\":null}");
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        assertNotNull(options.quality);
        assertEquals(128, options.quality.cloudHeight);
        assertEquals(ImpetusGameOptions.DeferChunkUpdatesMode.ONE_FRAME, options.performance.deferChunkUpdatesMode);
        assertEquals(3, options.performance.chunkBuilderThreads);
        assertEquals(ImpetusGameOptions.GraphicsApi.OPENGL, options.graphicsApi);
        assertEquals(ImpetusGameOptions.FullscreenMode.OFF, options.fullscreenMode);
    }

    // Keeps a wrongly typed file untouched and falls back to the defaults.
    @Test
    void wrongTypesFallBackWithoutRewriting() throws IOException {
        Files.createDirectories(this.path.getParent());
        Files.writeString(this.path, "{\"quality\":{\"cloud_height\":\"high\"}}");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
        assertEquals("{\"quality\":{\"cloud_height\":\"high\"}}", Files.readString(this.path));
        Files.writeString(this.path, "[1,2,3]");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
    }

    // Reads a file with a byte order free utf 8 text and invalid bytes.
    @Test
    void invalidBytesDoNotCrash() throws IOException {
        Files.createDirectories(this.path.getParent());
        Files.write(this.path, new byte[]{'{', '"', 'x', (byte) 0xFF, '"', ':', '1', '}'});
        assertNotNull(ImpetusGameOptions.load(this.name));
        Files.writeString(this.path, "{\"quality\":{\"cloud_height\":300}}", StandardCharsets.UTF_8);
        assertEquals(300, ImpetusGameOptions.load(this.name).quality.cloudHeight);
    }

    // Rejects a null name and a null config.
    @Test
    void nullArguments() {
        assertThrows(NullPointerException.class, () -> ImpetusGameOptions.load(null));
        assertThrows(NullPointerException.class, () -> ImpetusGameOptions.writeToDisk(null));
    }

    // Rejects an options object that never got a file.
    @Test
    void unsavedConfigHasNoFile() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        assertThrows(IllegalStateException.class, () -> ImpetusGameOptions.writeToDisk(options));
        assertThrows(IllegalStateException.class, options::getFileName);
    }

    // Leaves no temporary file after a write.
    @Test
    void writeLeavesNoTemporaryFile() throws IOException {
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        options.save();
        options.save();
        assertTrue(Files.exists(this.path));
        assertFalse(Files.exists(this.path.resolveSibling(this.name + ".tmp")));
    }

    // Rejects writes of a read only config and keeps the file unchanged.
    @Test
    void readOnlyConfigIsNotWritten() throws IOException {
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        String before = Files.readString(this.path);
        assertFalse(options.isReadOnly());
        options.setReadOnly();
        options.setReadOnly();
        assertTrue(options.isReadOnly());
        options.quality.cloudHeight = 7;
        assertThrows(IllegalStateException.class, options::save);
        assertThrows(IllegalStateException.class, () -> ImpetusGameOptions.writeToDisk(options));
        assertEquals(before, Files.readString(this.path));
        assertSame(options, options.getData());
        assertEquals(this.name, options.getFileName());
    }

    // Removes the temporary file when the move fails.
    @Test
    void failedMoveRemovesTemporaryFile() throws IOException {
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        Files.delete(this.path);
        Files.createDirectories(this.path);
        Files.writeString(this.path.resolve("child"), "x");
        try {
            assertThrows(RuntimeException.class, options::save);
            assertFalse(Files.exists(this.path.resolveSibling(this.name + ".tmp")));
        } finally {
            Files.delete(this.path.resolve("child"));
        }
    }

    // Writes values that load back unchanged at the int limits.
    @Test
    void intLimitsRoundTrip() {
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        options.quality.cloudHeight = Integer.MIN_VALUE;
        options.meshTerrain.maxGeometryMemory = Integer.MAX_VALUE;
        options.fullscreenResolution = -1;
        options.save();
        ImpetusGameOptions loaded = ImpetusGameOptions.load(this.name);
        assertEquals(Integer.MIN_VALUE, loaded.quality.cloudHeight);
        assertEquals(Integer.MAX_VALUE, loaded.meshTerrain.maxGeometryMemory);
        assertEquals(-1, loaded.fullscreenResolution);
    }

    // Answers every pair of statistics levels by order.
    @Test
    void statisticsIncludesEveryPair() {
        ImpetusGameOptions.MeshStatistics[] levels = ImpetusGameOptions.MeshStatistics.values();
        for (ImpetusGameOptions.MeshStatistics a : levels) {
            for (ImpetusGameOptions.MeshStatistics b : levels) {
                assertEquals(a.ordinal() >= b.ordinal(), a.includes(b));
            }
        }
        assertThrows(NullPointerException.class, () -> ImpetusGameOptions.MeshStatistics.NONE.includes(null));
    }
}
