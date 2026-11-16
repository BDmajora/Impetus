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
        PropertiesConfig props = new PropertiesConfig(Fulgor.LOGGER, FILE_NAME, "Impetus / Fulgor lighting subsystem. Delete a line to restore its default.");
        props.load();
        FulgorConfig config = new FulgorConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    // Writes every value to the config file.
    public void save() {
        this.file.save(values());
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
