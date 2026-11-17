package com.bdmajora.fulgor;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.util.Map;

// Settings of the Fulgor module.
public final class FulgorConfig {
    // Name of the config file.
    private static final String FILE_NAME = "impetus-fulgor.cfg";

    // Loaded config or null before the first read.
    private static FulgorConfig instance;

    // Properties file of the config.
    private PropertiesConfig file;

    // Enabled state of the lighting engine.
    public boolean enabled;
    // Deferred light updates.
    public boolean deferredLightUpdates;
    // Deduplicate updates.
    public boolean deduplicateUpdates;
    // Cache block light info.
    public boolean cacheBlockLightInfo;
    // Fix chunk boundary lighting.
    public boolean fixChunkBoundaryLighting;
    // Send non trivial section light.
    public boolean sendNonTrivialSectionLight;
    // Optimize render light updates.
    public boolean optimizeRenderLightUpdates;
    // Skip updates while paused.
    public boolean skipUpdatesWhilePaused;
    // Max scheduled updates.
    public int maxScheduledUpdates;
    // Warn on illegal thread access.
    public boolean warnOnIllegalThreadAccess;
    // Show debug overlay.
    public boolean showDebugOverlay;
    // Parallel light updates.
    public boolean parallelLightUpdates;
    // Parallel light threads.
    public int parallelLightThreads;
    // Parallel min positions.
    public int parallelMinPositions;
    // Parallel min chunks.
    public int parallelMinChunks;
    // Async light updates.
    public boolean asyncLightUpdates;
    // Fix render lighting.
    public boolean fixRenderLighting;
    // Async send chunks without light.
    public boolean asyncSendChunksWithoutLight;

    // Note written above each key in the file.
    private static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("enabled", "Turns the whole lighting engine on. Off restores vanilla lighting. Turned off on its own when Cubic Chunks is installed."),
            Map.entry("deferredLightUpdates", "Records light changes and works them out in one batch the next time light is read, instead of on every block change."),
            Map.entry("deduplicateUpdates", "Drops repeat updates for the same block within one batch."),
            Map.entry("cacheBlockLightInfo", "Remembers which blocks always give and block the same amount of light and skips asking them again. Turned off on its own with Dynamic Lights or Fluidlogged API."),
            Map.entry("fixChunkBoundaryLighting", "Keeps light updates that reach an unloaded chunk and replays them when it loads. Fixes light seams along chunk borders."),
            Map.entry("sendNonTrivialSectionLight", "Sends the light of empty chunk sections inside caves and hollowed mountains, so the client does not show them as full daylight."),
            Map.entry("optimizeRenderLightUpdates", "Turns light changes into chunk redraws every tick through a queue that merges repeats."),
            Map.entry("skipUpdatesWhilePaused", "Stops working out light while the game is paused."),
            Map.entry("maxScheduledUpdates", "Most light updates one batch holds before it is worked out early."),
            Map.entry("warnOnIllegalThreadAccess", "Logs a warning with a stack trace when a mod changes lighting from the wrong thread."),
            Map.entry("showDebugOverlay", "Adds a lighting line to the F3 screen."),
            Map.entry("parallelLightUpdates", "Splits large batches by chunk and works them out on several threads."),
            Map.entry("parallelLightThreads", "Threads for parallel light updates. 0 uses a third of the CPU cores."),
            Map.entry("parallelMinPositions", "Fewest updates in a batch before it is split across threads."),
            Map.entry("parallelMinChunks", "Fewest chunks in a batch before it is split across threads."),
            Map.entry("asyncLightUpdates", "Lights chunks with a separate engine on worker threads. New chunks are lit off the server thread."),
            Map.entry("asyncSendChunksWithoutLight", "Sends new chunks before the worker threads finish lighting them. Faster, but wrong light stays until a nearby block changes."),
            Map.entry("fixRenderLighting", "Lights slabs and stairs only through their open side, and keeps smooth lighting for dim light sources."));

    // Creates a fulgor config from its props.
    private FulgorConfig(PropertiesConfig props) {
        this.enabled = props.bool("enabled", true);
        this.deferredLightUpdates = props.bool("deferredLightUpdates", true);
        this.deduplicateUpdates = props.bool("deduplicateUpdates", true);
        this.cacheBlockLightInfo = props.bool("cacheBlockLightInfo", true);
        this.fixChunkBoundaryLighting = props.bool("fixChunkBoundaryLighting", true);
        this.sendNonTrivialSectionLight = props.bool("sendNonTrivialSectionLight", true);
        this.optimizeRenderLightUpdates = props.bool("optimizeRenderLightUpdates", true);
        this.skipUpdatesWhilePaused = props.bool("skipUpdatesWhilePaused", true);
        this.maxScheduledUpdates = props.integer("maxScheduledUpdates", 1 << 22, 1 << 12, Integer.MAX_VALUE);
        this.warnOnIllegalThreadAccess = props.bool("warnOnIllegalThreadAccess", true);
        this.showDebugOverlay = props.bool("showDebugOverlay", false);
        this.parallelLightUpdates = props.bool("parallelLightUpdates", true);
        this.parallelLightThreads = props.integer("parallelLightThreads", 0, 0, 64);
        this.parallelMinPositions = props.integer("parallelMinPositions", 1024, 1, Integer.MAX_VALUE);
        this.parallelMinChunks = props.integer("parallelMinChunks", 3, 2, Integer.MAX_VALUE);
        this.asyncLightUpdates = props.bool("asyncLightUpdates", true);
        this.asyncSendChunksWithoutLight = props.bool("asyncSendChunksWithoutLight", false);
        this.fixRenderLighting = props.bool("fixRenderLighting", true);
    }

    // Returns the thread count limited to the available cores.
    public int parallelLightThreads() {
        if (this.parallelLightThreads > 0) {
            return this.parallelLightThreads;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    }

    // Returns the value.
    public static FulgorConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the config file and fills missing values with defaults.
    private static FulgorConfig load() {
        PropertiesConfig props = new PropertiesConfig(Fulgor.LOGGER, FILE_NAME, "Impetus lighting engine. Changes take effect on the next launch. Delete a line to restore its default.");
        props.load();
        FulgorConfig config = new FulgorConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    // Writes every value to the config file.
    public void save() {
        this.file.save(values(), NOTES, new FulgorConfig(new PropertiesConfig(Fulgor.LOGGER, FILE_NAME, "")).values());
    }

    // Returns every setting as text by key.
    private Map<String, String> values() {
        Map<String, String> values = PropertiesConfig.values();
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

        return values;
    }
}
