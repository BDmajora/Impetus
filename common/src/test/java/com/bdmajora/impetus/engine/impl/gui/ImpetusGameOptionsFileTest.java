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
        Files.writeString(this.path, "\"not an options object\"");
        assertEquals(128, ImpetusGameOptions.load(this.name).quality.cloudHeight);
        assertEquals("\"not an options object\"", Files.readString(this.path));
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

    // The simulation distance round-trips, a missing key keeps the default, and out of range values are clamped where read.
    @Test
    void simulationDistanceSurvivesTheFileAndIsClampedOnRead() throws IOException {
        Files.createDirectories(this.path.getParent());
        Files.writeString(this.path, "{}");
        assertEquals(10, ImpetusGameOptions.load(this.name).getSimulationDistance());
        Files.writeString(this.path, "{\"simulation_distance\":12}");
        ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
        assertEquals(12, options.getSimulationDistance());
        options.setSimulationDistance(7);
        ImpetusGameOptions.writeToDisk(options);
        assertEquals(7, ImpetusGameOptions.load(this.name).getSimulationDistance());
        // A hand-edited value outside 5..32 loads, and every reader sees it clamped
        Files.writeString(this.path, "{\"simulation_distance\":1000}");
        assertEquals(ImpetusGameOptions.MAX_SIMULATION_DISTANCE, ImpetusGameOptions.load(this.name).getSimulationDistance());
        Files.writeString(this.path, "{\"simulation_distance\":-3}");
        assertEquals(ImpetusGameOptions.MIN_SIMULATION_DISTANCE, ImpetusGameOptions.load(this.name).getSimulationDistance());
        Files.writeString(this.path, "{\"simulation_distance\":" + Integer.MIN_VALUE + "}");
        assertEquals(ImpetusGameOptions.MIN_SIMULATION_DISTANCE, ImpetusGameOptions.load(this.name).getSimulationDistance());
    }

    // A simulation distance that is not an int falls back to every default and leaves the file alone.
    @Test
    void anUnreadableSimulationDistanceFallsBackWithoutRewriting() throws IOException {
        Files.createDirectories(this.path.getParent());
        for (String bad : new String[] {"\"far\"", "12.5", "99999999999", "true", "[10]", "{}"}) {
            String json = "{\"simulation_distance\":" + bad + ",\"quality\":{\"cloud_height\":300}}";
            Files.writeString(this.path, json);
            ImpetusGameOptions options = ImpetusGameOptions.load(this.name);
            assertEquals(10, options.getSimulationDistance(), bad);
            assertEquals(128, options.quality.cloudHeight, bad);
            assertEquals(json, Files.readString(this.path), bad);
        }
        // null for a primitive is Gson's "leave the default"
        Files.writeString(this.path, "{\"simulation_distance\":null}");
        assertEquals(10, ImpetusGameOptions.load(this.name).getSimulationDistance());
    }

    // Constants are never written to the file, and the written file names only instance settings.
    @Test
    void staticConstantsStayOutOfTheFile() throws IOException {
        Files.createDirectories(this.path.getParent());
        ImpetusGameOptions.writeToDisk(ImpetusGameOptions.load(this.name));
        String json = Files.readString(this.path);
        assertTrue(json.contains("\"simulation_distance\": 10"), json);
        assertFalse(json.contains("min_simulation_distance"), json);
        assertFalse(json.contains("max_simulation_distance"), json);
        // A file that still carries them (hand-edited, or from a build that wrote statics) loads without touching the constants
        Files.writeString(this.path, "{\"m_i_n__s_i_m_u_l_a_t_i_o_n__d_i_s_t_a_n_c_e\":1,\"min_simulation_distance\":1,\"max_simulation_distance\":2}");
        assertEquals(10, ImpetusGameOptions.load(this.name).getSimulationDistance());
        assertEquals(5, ImpetusGameOptions.MIN_SIMULATION_DISTANCE);
        assertEquals(32, ImpetusGameOptions.MAX_SIMULATION_DISTANCE);
    }

    // The setter clamps both ends and the edge values themselves are kept.
    @Test
    void theSetterClampsAtBothEnds() {
        ImpetusGameOptions options = new ImpetusGameOptions();
        for (int[] pair : new int[][] {{Integer.MIN_VALUE, 5}, {-1, 5}, {0, 5}, {4, 5}, {5, 5}, {6, 6}, {31, 31}, {32, 32}, {33, 32}, {Integer.MAX_VALUE, 32}}) {
            options.setSimulationDistance(pair[0]);
            assertEquals(pair[1], options.simulationDistance, () -> "set " + pair[0]);
            assertEquals(pair[1], options.getSimulationDistance());
        }
    }
}
