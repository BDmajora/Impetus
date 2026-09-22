package com.bdmajora.fulgor;

import com.bdmajora.impetus.booter.util.PropertiesConfig;

import java.util.Map;

// Feature switches for the lighting subsystem, a plain Properties file (see PropertiesConfig) since the mixin plugin reads it during coremod setup before Forge/Minecraft classes are safe
public final class FulgorConfig {
    private static final String FILE_NAME = "impetus-fulgor.cfg";

    private static FulgorConfig instance;

    // The backing file; set by load before the instance is published
    private PropertiesConfig file;

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
        PropertiesConfig props = new PropertiesConfig(Fulgor.LOGGER, FILE_NAME, "Impetus / Fulgor lighting subsystem. Delete a line to restore its default.");
        props.load();
        FulgorConfig config = new FulgorConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    // Every switch except the two diagnostics and skipUpdatesWhilePaused decides whether a mixin applies, so changes take effect next launch (the options screen flags those for restart)
    public void save() {
        this.file.save(values());
    }

    // Every key in declaration order, so a rewritten file lists every switch; user-set values are preserved verbatim
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
