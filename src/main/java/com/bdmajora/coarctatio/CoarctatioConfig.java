package com.bdmajora.coarctatio;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Settings of the memory features loaded from the configuration file.
public final class CoarctatioConfig {
    // Name of the configuration file.
    private static final String FILE_NAME = "impetus-coarctatio.cfg";

    // Note written above each key in the file.
    private static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("enabled", "Turns every memory feature on. Off keeps the settings below in the file but runs none of them."),
            Map.entry("deduplicateResourceLocations", "Shares the namespace and path text of every resource name."),
            Map.entry("compactNbtBackingMap", "Stores small NBT compounds in flat arrays and switches to a hash map only when one grows."),
            Map.entry("internNbtKeys", "Shares NBT key text such as id, Count and Damage between compounds."),
            Map.entry("nbtArrayMapThreshold", "Entry count at which an NBT compound moves from array storage to a hash map. 0 always uses hash maps."),
            Map.entry("stripChunkNbt", "Drops the parts of a loading chunk's data that have already been copied out."),
            Map.entry("dropEmptyChunkSections", "Skips all-air chunk sections whose light can be worked out again exactly."),
            Map.entry("compactRuntimeCollections", "Swaps the hash maps behind registries, entity data and the chunk entity lookup for smaller ones."),
            Map.entry("clearPoolsOnWorldLeave", "Empties the NBT key and resource name pools when you return to the main menu."),
            Map.entry("poolSizeLimit", "Most entries a shared pool holds before it stops adding new ones."),
            Map.entry("showDebugOverlay", "Adds a memory line to the F3 screen."),
            Map.entry("lazyItemStackCapabilities", "Builds an item stack's capabilities on the first capability query instead of when the stack is made."),
            Map.entry("poolNbtPrimitives", "Shares one tag per small number between every NBT compound and list."),
            Map.entry("internLoaderStrings", "Shares the class, package and annotation name text the mod scan keeps."),
            Map.entry("canonicalizeModelParts", "Shares UV arrays and texture names between the parts of unbaked models."),
            Map.entry("compactRemapperCaches", "Shares equal member maps in Forge's runtime deobfuscator and drops empty ones."),
            Map.entry("recycleEvents", "Reuses one instance per thread of tick, capability attach and neighbour notify events."),
            Map.entry("skipSoundDebugChecks", "Skips the two registry walks that log missing subtitles after sounds load."),
            Map.entry("plainMissingModels", "Uses the shared missing model instead of one per failed model. Off by default because a few mods need the per model one."));

    // Fields of every feature switch.
    private static final List<Field> FEATURE_SWITCHES = featureSwitches();

    // Loaded settings or null before the first use.
    private static CoarctatioConfig instance;

    // File that backs the settings.
    private PropertiesConfig file;

    // Values read from the file while the master switch was off or null.
    private Map<String, String> heldValues;

    // Master switch of every feature.
    public boolean enabled;

    // Switch of the deduplicateResourceLocations feature.
    public boolean deduplicateResourceLocations;
    // Switch of the compactNbtBackingMap feature.
    public boolean compactNbtBackingMap;
    // Switch of the internNbtKeys feature.
    public boolean internNbtKeys;
    // Value of the nbtArrayMapThreshold setting.
    public int nbtArrayMapThreshold;
    // Switch of the stripChunkNbt feature.
    public boolean stripChunkNbt;
    // Switch of the dropEmptyChunkSections feature.
    public boolean dropEmptyChunkSections;
    // Switch of the clearPoolsOnWorldLeave feature.
    public boolean clearPoolsOnWorldLeave;
    // Switch of the compactRuntimeCollections feature.
    public boolean compactRuntimeCollections;
    // Value of the poolSizeLimit setting.
    public int poolSizeLimit;
    // Switch of the showDebugOverlay feature.
    public boolean showDebugOverlay;
    // Switch of the lazyItemStackCapabilities feature.
    public boolean lazyItemStackCapabilities;
    // Switch of the poolNbtPrimitives feature.
    public boolean poolNbtPrimitives;
    // Switch of the internLoaderStrings feature.
    public boolean internLoaderStrings;
    // Switch of the canonicalizeModelParts feature.
    public boolean canonicalizeModelParts;
    // Switch of the compactRemapperCaches feature.
    public boolean compactRemapperCaches;
    // Switch of the recycleEvents feature.
    public boolean recycleEvents;
    // Switch of the skipSoundDebugChecks feature.
    public boolean skipSoundDebugChecks;
    // Switch of the plainMissingModels feature.
    public boolean plainMissingModels;

    // Creates settings from the properties and applies the defaults.
    private CoarctatioConfig(PropertiesConfig props) {
        this.enabled = props.bool("enabled", true);
        this.deduplicateResourceLocations = props.bool("deduplicateResourceLocations", true);
        this.compactNbtBackingMap = props.bool("compactNbtBackingMap", true);
        this.internNbtKeys = props.bool("internNbtKeys", true);
        this.nbtArrayMapThreshold = props.integer("nbtArrayMapThreshold", 12, 0, 1024);
        this.stripChunkNbt = props.bool("stripChunkNbt", true);
        this.dropEmptyChunkSections = props.bool("dropEmptyChunkSections", true);
        this.compactRuntimeCollections = props.bool("compactRuntimeCollections", true);
        this.clearPoolsOnWorldLeave = props.bool("clearPoolsOnWorldLeave", true);
        this.poolSizeLimit = props.integer("poolSizeLimit", 262144, 1024, Integer.MAX_VALUE);
        this.showDebugOverlay = props.bool("showDebugOverlay", true);
        this.lazyItemStackCapabilities = props.bool("lazyItemStackCapabilities", true);
        this.poolNbtPrimitives = props.bool("poolNbtPrimitives", true);
        this.internLoaderStrings = props.bool("internLoaderStrings", true);
        this.canonicalizeModelParts = props.bool("canonicalizeModelParts", true);
        this.compactRemapperCaches = props.bool("compactRemapperCaches", true);
        this.recycleEvents = props.bool("recycleEvents", false);
        this.skipSoundDebugChecks = props.bool("skipSoundDebugChecks", true);
        this.plainMissingModels = props.bool("plainMissingModels", false);
    }

    // Returns the settings and loads them on first use.
    public static CoarctatioConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the file and writes it back with every setting present.
    private static CoarctatioConfig load() {
        PropertiesConfig props = new PropertiesConfig(Coarctatio.LOGGER, FILE_NAME, "Impetus memory features. Changes take effect on the next launch. Delete a line to restore its default.");
        props.load();
        CoarctatioConfig config = new CoarctatioConfig(props);
        config.file = props;
        if (!config.enabled) {
            config.holdFeatures();
        }
        config.save();
        return config;
    }

    // Keeps the file values and turns every feature off for this session.
    private void holdFeatures() {
        this.heldValues = values();
        try {
            for (Field field : FEATURE_SWITCHES) {
                field.setBoolean(this, false);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // Returns the public boolean feature switch fields without the master switch and the debug overlay line.
    private static List<Field> featureSwitches() {
        List<Field> switches = new ArrayList<>();
        for (Field field : CoarctatioConfig.class.getFields()) {
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers()) && !"showDebugOverlay".equals(field.getName()) && !"enabled".equals(field.getName())) {
                switches.add(field);
            }
        }
        return List.copyOf(switches);
    }

    // Writes the settings to the file.
    public void save() {
        Map<String, String> values = this.heldValues == null ? values() : new LinkedHashMap<>(this.heldValues);
        values.put("enabled", Boolean.toString(this.enabled));
        this.file.save(values, NOTES, new CoarctatioConfig(new PropertiesConfig(Coarctatio.LOGGER, FILE_NAME, "")).values());
    }

    // Returns every setting as text by name.
    private Map<String, String> values() {
        Map<String, String> values = PropertiesConfig.values();
        values.put("enabled", Boolean.toString(this.enabled));
        values.put("deduplicateResourceLocations", Boolean.toString(this.deduplicateResourceLocations));
        values.put("compactNbtBackingMap", Boolean.toString(this.compactNbtBackingMap));
        values.put("internNbtKeys", Boolean.toString(this.internNbtKeys));
        values.put("nbtArrayMapThreshold", Integer.toString(this.nbtArrayMapThreshold));
        values.put("stripChunkNbt", Boolean.toString(this.stripChunkNbt));
        values.put("dropEmptyChunkSections", Boolean.toString(this.dropEmptyChunkSections));
        values.put("compactRuntimeCollections", Boolean.toString(this.compactRuntimeCollections));
        values.put("clearPoolsOnWorldLeave", Boolean.toString(this.clearPoolsOnWorldLeave));
        values.put("poolSizeLimit", Integer.toString(this.poolSizeLimit));
        values.put("showDebugOverlay", Boolean.toString(this.showDebugOverlay));
        values.put("lazyItemStackCapabilities", Boolean.toString(this.lazyItemStackCapabilities));
        values.put("poolNbtPrimitives", Boolean.toString(this.poolNbtPrimitives));
        values.put("internLoaderStrings", Boolean.toString(this.internLoaderStrings));
        values.put("canonicalizeModelParts", Boolean.toString(this.canonicalizeModelParts));
        values.put("compactRemapperCaches", Boolean.toString(this.compactRemapperCaches));
        values.put("recycleEvents", Boolean.toString(this.recycleEvents));
        values.put("skipSoundDebugChecks", Boolean.toString(this.skipSoundDebugChecks));
        values.put("plainMissingModels", Boolean.toString(this.plainMissingModels));

        return values;
    }
}
