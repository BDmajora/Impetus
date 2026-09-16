package com.bdmajora.fulgor;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

// Feature switches for the lighting subsystem, a plain Properties file since the mixin plugin reads it during coremod setup before Forge/Minecraft classes are safe
public final class FulgorConfig {
    private static final String FILE_NAME = "impetus-fulgor.cfg";

    private static FulgorConfig instance;

    // Where save() writes; null only if the config directory could not be resolved
    private Path file;

    // Off means unmodified vanilla lighting; kept separate from the individual switches so "is this Fulgor's fault" needs no understanding of the rest
    public boolean enabled;
    // The subsystem's whole point; everything else supports this or fixes a vanilla bug the batching exposes
    public boolean deferredLightUpdates;
    // Alfheim's headline change over Phosphor: bulk edits (worldgen, /fill, explosions) schedule the same position from several neighbours in one tick, otherwise each is evaluated separately
    public boolean deduplicateUpdates;
    // Phosphor's BlockStateLightInfo, adapted; Forge's position-aware getLightOpacity/getLightValue cost a virtual dispatch (and a redundant world lookup) though most answers are position-independent. Ignored under Dynamic Lights or Fluidlogged API
    public boolean cacheBlockLightInfo;
    // Fixes MC-3329: vanilla drops boundary light updates to unloaded neighbours and never revisits them
    public boolean fixChunkBoundaryLighting;
    // Fixes MC-116690: vanilla's emptiness test counts blocks only, so a carved-out section with real light data is skipped by the chunk packet and the client relights it wrong
    public boolean sendNonTrivialSectionLight;
    // Also fixes MC-80966: vanilla skips the render light-update drain while the chunk builder is busy, so changes can sit unrendered indefinitely under load
    public boolean optimizeRenderLightUpdates;
    // Skips light processing while the game is paused
    public boolean skipUpdatesWhilePaused;
    // Memory bound, not a performance knob: stops a pathological producer from growing a queue unbounded
    public int maxScheduledUpdates;
    // Inherited from Phosphor; always another mod's bug and the engine survives it either way, but loud by default
    public boolean warnOnIllegalThreadAccess;
    // Adds a Fulgor line to the F3 debug overlay.
    public boolean showDebugOverlay;
    // ScalableLux's idea: a batch of scheduled positions is split by chunk and run across a pool, with chunks whose reach could meet kept apart in time; same result, less wall time
    public boolean parallelLightUpdates;
    // Pool width for parallel passes; 0 picks a third of the cores like ScalableLux
    public int parallelLightThreads;
    // Batches below this many positions run on the calling thread, since the hand-off costs more than it saves
    public int parallelMinPositions;
    // Batches spread over fewer chunks than this run on the calling thread, since there is nothing to run side by side
    public int parallelMinChunks;
    // Pulsar's Starlight-style engine in place of the Phosphor-style one: per-chunk BFS with its own light storage, run on two worker threads on the server and from the tick on the client; initial chunk lighting leaves the server thread entirely
    public boolean asyncLightUpdates;
    // Pulsar's render-side lighting fixes: a slab or stair takes neighbour brightness only through its open face (MC-92), the slab lightmap hack that sampled the block below goes, and a level-one emitter keeps smooth lighting (MC-249343); read by the mesher and two client mixins
    public boolean fixRenderLighting;
    // Lets the server send a chunk before its initial pass has finished; wrong light sent to a 1.12.2 client stays wrong until a block changes, so off by default and it only delays freshly generated chunks by a few milliseconds
    public boolean asyncSendChunksWithoutLight;

    private FulgorConfig(Properties props) {
        this.enabled = bool(props, "enabled", true);
        this.deferredLightUpdates = bool(props, "deferredLightUpdates", true);
        this.deduplicateUpdates = bool(props, "deduplicateUpdates", true);
        this.cacheBlockLightInfo = bool(props, "cacheBlockLightInfo", true);
        this.fixChunkBoundaryLighting = bool(props, "fixChunkBoundaryLighting", true);
        this.sendNonTrivialSectionLight = bool(props, "sendNonTrivialSectionLight", true);
        this.optimizeRenderLightUpdates = bool(props, "optimizeRenderLightUpdates", true);
        this.skipUpdatesWhilePaused = bool(props, "skipUpdatesWhilePaused", true);
        this.maxScheduledUpdates = integer(props, "maxScheduledUpdates", 1 << 22, 1 << 12, Integer.MAX_VALUE);
        this.warnOnIllegalThreadAccess = bool(props, "warnOnIllegalThreadAccess", true);
        this.showDebugOverlay = bool(props, "showDebugOverlay", false);
        this.parallelLightUpdates = bool(props, "parallelLightUpdates", true);
        this.parallelLightThreads = integer(props, "parallelLightThreads", 0, 0, 64);
        this.parallelMinPositions = integer(props, "parallelMinPositions", 1024, 1, Integer.MAX_VALUE);
        this.parallelMinChunks = integer(props, "parallelMinChunks", 3, 2, Integer.MAX_VALUE);
        this.asyncLightUpdates = bool(props, "asyncLightUpdates", true);
        this.asyncSendChunksWithoutLight = bool(props, "asyncSendChunksWithoutLight", false);
        this.fixRenderLighting = bool(props, "fixRenderLighting", true);
    }

    // The pool width to use: the configured count, or a third of the cores with at least one
    public int parallelLightThreads() {
        if (this.parallelLightThreads > 0) {
            return this.parallelLightThreads;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    }

    // Loads on first use and caches; every reader shares the one instance
    public static FulgorConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the file if present, otherwise starts from defaults and writes them out
    private static FulgorConfig load() {
        Path file = configDirectory().resolve(FILE_NAME);

        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                Fulgor.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            }
        }

        FulgorConfig config = new FulgorConfig(props);
        config.file = file;
        config.save();
        return config;
    }

    // Every switch except the two diagnostics and skipUpdatesWhilePaused decides whether a mixin applies, so changes take effect next launch (the options screen flags those for restart)
    public void save() {
        if (this.file != null) {
            writeBack(this.file);
        }
    }

    // The config directory, created eagerly; falls back to the working directory when minecraftHome is unset
    private static Path configDirectory() {
        File home = Launch.minecraftHome;
        Path dir = (home == null ? Paths.get(".") : home.toPath()).resolve("config");

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Fulgor.LOGGER.warn("Could not create {}, configuration will not persist", dir, e);
        }

        return dir;
    }

    // Rewrites the file with every key so a user who never opened it still discovers the switches; user-set values are preserved verbatim
    private void writeBack(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.toString(this.enabled));
        values.put("deferredLightUpdates", Boolean.toString(this.deferredLightUpdates));
        values.put("deduplicateUpdates", Boolean.toString(this.deduplicateUpdates));
        values.put("cacheBlockLightInfo", Boolean.toString(this.cacheBlockLightInfo));
        values.put("fixChunkBoundaryLighting", Boolean.toString(this.fixChunkBoundaryLighting));
        values.put("sendNonTrivialSectionLight", Boolean.toString(this.sendNonTrivialSectionLight));
        values.put("optimizeRenderLightUpdates", Boolean.toString(this.optimizeRenderLightUpdates));
        values.put("skipUpdatesWhilePaused", Boolean.toString(this.skipUpdatesWhilePaused));
        values.put("maxScheduledUpdates", Integer.toString(this.maxScheduledUpdates));
        values.put("warnOnIllegalThreadAccess", Boolean.toString(this.warnOnIllegalThreadAccess));
        values.put("showDebugOverlay", Boolean.toString(this.showDebugOverlay));
        values.put("parallelLightUpdates", Boolean.toString(this.parallelLightUpdates));
        values.put("parallelLightThreads", Integer.toString(this.parallelLightThreads));
        values.put("parallelMinPositions", Integer.toString(this.parallelMinPositions));
        values.put("parallelMinChunks", Integer.toString(this.parallelMinChunks));
        values.put("asyncLightUpdates", Boolean.toString(this.asyncLightUpdates));
        values.put("asyncSendChunksWithoutLight", Boolean.toString(this.asyncSendChunksWithoutLight));
        values.put("fixRenderLighting", Boolean.toString(this.fixRenderLighting));

        Properties out = new Properties();
        out.putAll(values);

        try (OutputStream stream = Files.newOutputStream(file)) {
            out.store(stream, "Impetus / Fulgor lighting subsystem. Delete a line to restore its default.");
        } catch (IOException e) {
            Fulgor.LOGGER.warn("Could not write {}", file, e);
        }
    }

    // Lenient boolean parse; anything unrecognised keeps the default
    private static boolean bool(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                ? Boolean.parseBoolean(value)
                : fallback;
    }

    // Clamped integer parse; out-of-range and unparseable values keep the default
    private static int integer(Properties props, String key, int fallback, int min, int max) {
        String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
