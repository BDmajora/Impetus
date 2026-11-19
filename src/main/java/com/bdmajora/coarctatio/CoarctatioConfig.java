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

    // Default class prefixes excluded from state packing.
    private static final String DEFAULT_BLOCK_STATE_BLACKLIST = "jds.bibliocraft";
    // Default namespaces whose models load eagerly.
    private static final String DEFAULT_EAGER_NAMESPACES = "thebetweenlands,dynamictrees";

    // Note written above each key in the file.
    private static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("enabled", "Turns every memory feature on. Off keeps the settings below in the file but runs none of them."),
            Map.entry("deduplicateResourceLocations", "Shares the namespace and path text of every resource name."),
            Map.entry("deduplicateModelVariants", "Shares the variant text of every model name, such as normal and inventory."),
            Map.entry("compactNbtBackingMap", "Stores small NBT compounds in flat arrays and switches to a hash map only when one grows."),
            Map.entry("internNbtKeys", "Shares NBT key text such as id, Count and Damage between compounds."),
            Map.entry("nbtArrayMapThreshold", "Entry count at which an NBT compound moves from array storage to a hash map. 0 always uses hash maps."),
            Map.entry("poolQuadVertexData", "Shares identical baked quad vertex arrays. Quads that may be rewritten later are never shared."),
            Map.entry("compactBakedModels", "Swaps the growable lists and maps inside baked models for exactly sized ones."),
            Map.entry("canonicalizeMultipartConditions", "Flattens each multipart blockstate condition tree into one object and shares identical ones between blocks."),
            Map.entry("optimizeBlockStates", "Replaces each block state's lookup table with one packed number per state and one shared table per block."),
            Map.entry("blockStateBlacklist", "Class name prefixes of blocks that keep vanilla block state tables, separated by commas."),
            Map.entry("compactStateProperties", "Shares one property key array between every state of a block. Needs optimizeBlockStates."),
            Map.entry("compactModelGraph", "Swaps the model loader's hash maps for smaller ones."),
            Map.entry("stripChunkNbt", "Drops the parts of a loading chunk's data that have already been copied out."),
            Map.entry("dropEmptyChunkSections", "Skips all-air chunk sections whose light can be worked out again exactly."),
            Map.entry("weakenClassLoaderCache", "Lets the garbage collector free the class bytes the mod loader keeps after startup."),
            Map.entry("releaseSpriteData", "Frees each texture's pixel copy once the atlas is on the graphics card. Animated textures keep theirs. Turn this off if a texture mod breaks."),
            Map.entry("deduplicateModelTransforms", "Shares identical camera transforms and item override lists between baked models."),
            Map.entry("releaseBakeState", "Frees unbaked models and load errors after every mod has read them."),
            Map.entry("compactRuntimeCollections", "Swaps the hash maps behind registries, entity data and the chunk entity lookup for smaller ones."),
            Map.entry("clearPoolsOnWorldLeave", "Empties the NBT key and resource name pools when you return to the main menu."),
            Map.entry("lazySearchTrees", "Builds the creative search index on the first search instead of at startup. Never built when a recipe viewer replaces the search."),
            Map.entry("poolSizeLimit", "Most entries a shared pool holds before it stops adding new ones."),
            Map.entry("showDebugOverlay", "Adds a memory line to the F3 screen."),
            Map.entry("lazyItemStackCapabilities", "Builds an item stack's capabilities on the first capability query instead of when the stack is made."),
            Map.entry("poolNbtPrimitives", "Shares one tag per small number between every NBT compound and list."),
            Map.entry("cachePropertyHashes", "Remembers the hash of enum and integer block properties."),
            Map.entry("cacheStateHashes", "Remembers the hash of each block state."),
            Map.entry("resourceExistenceCache", "Indexes each resource pack once per reload and answers file lookups from the index."),
            Map.entry("stacklessResourceExceptions", "Throws missing file errors from the resource manager without a stack trace."),
            Map.entry("fastAtlasStitching", "Packs the texture atlas with a shelf layout instead of vanilla's slot search. The atlas is the same size or smaller."),
            Map.entry("softStructureTemplates", "Lets the garbage collector free structure templates world generation no longer needs. They load from disk again when asked for."),
            Map.entry("internLoaderStrings", "Shares the class, package and annotation name text the mod scan keeps."),
            Map.entry("modScanCache", "Keeps each mod jar's annotation scan between launches in impetus-cache/mod-scan.bin. A changed jar is scanned again."),
            Map.entry("dynamicModels", "Loads and bakes each model the first time it is drawn instead of all of them at startup."),
            Map.entry("dynamicModelsPrebakeItems", "With dynamic models on, bakes every item model on a background thread after a reload."),
            Map.entry("dynamicModelsEagerNamespaces", "Mod ids whose models always load at startup with dynamic models on, separated by commas."),
            Map.entry("fastItemLayerBaking", "Writes generated item quads straight into their vertex data instead of through Forge's quad builder."),
            Map.entry("parallelTextureLoad", "Reads every texture on all cores before the atlas is built. Textures with a custom loader keep their own path."),
            Map.entry("parallelModelLoad", "Reads every model file on all cores before the model loader runs. Models with a custom loader keep their own path."),
            Map.entry("primitiveOreDictionary", "Stores ore dictionary ids in primitive collections instead of boxed lists."),
            Map.entry("canonicalizeModelParts", "Shares UV arrays and texture names between the parts of unbaked models."),
            Map.entry("compactRemapperCaches", "Shares equal member maps in Forge's runtime deobfuscator and drops empty ones."),
            Map.entry("recycleEvents", "Reuses one instance per thread of tick, capability attach and neighbour notify events."),
            Map.entry("skipSoundDebugChecks", "Skips the two registry walks that log missing subtitles after sounds load."),
            Map.entry("plainMissingModels", "Uses the shared missing model instead of one per failed model. Off by default because a few mods need the per model one."),
            Map.entry("quietPrefixWarnings", "Skips Forge's prefix check for registry names that already carry a namespace."));

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
    // Switch of the deduplicateModelVariants feature.
    public boolean deduplicateModelVariants;
    // Switch of the compactNbtBackingMap feature.
    public boolean compactNbtBackingMap;
    // Switch of the internNbtKeys feature.
    public boolean internNbtKeys;
    // Value of the nbtArrayMapThreshold setting.
    public int nbtArrayMapThreshold;
    // Switch of the poolQuadVertexData feature.
    public boolean poolQuadVertexData;
    // Switch of the compactBakedModels feature.
    public boolean compactBakedModels;
    // Switch of the canonicalizeMultipartConditions feature.
    public boolean canonicalizeMultipartConditions;
    // Switch of the optimizeBlockStates feature.
    public boolean optimizeBlockStates;
    // List value of the blockStateBlacklist setting.
    public String[] blockStateBlacklist;
    // Switch of the compactStateProperties feature.
    public boolean compactStateProperties;
    // Switch of the compactModelGraph feature.
    public boolean compactModelGraph;
    // Switch of the stripChunkNbt feature.
    public boolean stripChunkNbt;
    // Switch of the dropEmptyChunkSections feature.
    public boolean dropEmptyChunkSections;
    // Switch of the weakenClassLoaderCache feature.
    public boolean weakenClassLoaderCache;
    // Switch of the releaseSpriteData feature.
    public boolean releaseSpriteData;
    // Switch of the deduplicateModelTransforms feature.
    public boolean deduplicateModelTransforms;
    // Switch of the releaseBakeState feature.
    public boolean releaseBakeState;
    // Switch of the clearPoolsOnWorldLeave feature.
    public boolean clearPoolsOnWorldLeave;
    // Switch of the lazySearchTrees feature.
    public boolean lazySearchTrees;
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
    // Switch of the cachePropertyHashes feature.
    public boolean cachePropertyHashes;
    // Switch of the cacheStateHashes feature.
    public boolean cacheStateHashes;
    // Switch of the resourceExistenceCache feature.
    public boolean resourceExistenceCache;
    // Switch of the stacklessResourceExceptions feature.
    public boolean stacklessResourceExceptions;
    // Switch of the fastAtlasStitching feature.
    public boolean fastAtlasStitching;
    // Switch of the softStructureTemplates feature.
    public boolean softStructureTemplates;
    // Switch of the internLoaderStrings feature.
    public boolean internLoaderStrings;
    // Switch of the modScanCache feature.
    public boolean modScanCache;
    // Switch of the dynamicModels feature.
    public boolean dynamicModels;
    // Switch of the dynamicModelsPrebakeItems feature.
    public boolean dynamicModelsPrebakeItems;
    // List value of the dynamicModelsEagerNamespaces setting.
    public String[] dynamicModelsEagerNamespaces;
    // Switch of the fastItemLayerBaking feature.
    public boolean fastItemLayerBaking;
    // Switch of the parallelTextureLoad feature.
    public boolean parallelTextureLoad;
    // Switch of the parallelModelLoad feature.
    public boolean parallelModelLoad;
    // Switch of the primitiveOreDictionary feature.
    public boolean primitiveOreDictionary;
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
    // Switch of the quietPrefixWarnings feature.
    public boolean quietPrefixWarnings;

    // Creates settings from the properties and applies the defaults.
    private CoarctatioConfig(PropertiesConfig props) {
        this.enabled = props.bool("enabled", true);
        this.deduplicateResourceLocations = props.bool("deduplicateResourceLocations", true);
        this.deduplicateModelVariants = props.bool("deduplicateModelVariants", true);
        this.compactNbtBackingMap = props.bool("compactNbtBackingMap", true);
        this.internNbtKeys = props.bool("internNbtKeys", true);
        this.nbtArrayMapThreshold = props.integer("nbtArrayMapThreshold", 12, 0, 1024);
        this.poolQuadVertexData = props.bool("poolQuadVertexData", true);
        this.compactBakedModels = props.bool("compactBakedModels", true);
        this.canonicalizeMultipartConditions = props.bool("canonicalizeMultipartConditions", true);
        this.optimizeBlockStates = props.bool("optimizeBlockStates", true);
        this.blockStateBlacklist = props.list("blockStateBlacklist", DEFAULT_BLOCK_STATE_BLACKLIST);
        this.compactStateProperties = props.bool("compactStateProperties", true);
        this.compactModelGraph = props.bool("compactModelGraph", true);
        this.stripChunkNbt = props.bool("stripChunkNbt", true);
        this.dropEmptyChunkSections = props.bool("dropEmptyChunkSections", true);
        this.weakenClassLoaderCache = props.bool("weakenClassLoaderCache", true);
        this.releaseSpriteData = props.bool("releaseSpriteData", true);
        this.deduplicateModelTransforms = props.bool("deduplicateModelTransforms", true);
        this.releaseBakeState = props.bool("releaseBakeState", true);
        this.compactRuntimeCollections = props.bool("compactRuntimeCollections", true);
        this.clearPoolsOnWorldLeave = props.bool("clearPoolsOnWorldLeave", true);
        this.lazySearchTrees = props.bool("lazySearchTrees", true);
        this.poolSizeLimit = props.integer("poolSizeLimit", 262144, 1024, Integer.MAX_VALUE);
        this.showDebugOverlay = props.bool("showDebugOverlay", true);
        this.lazyItemStackCapabilities = props.bool("lazyItemStackCapabilities", true);
        this.poolNbtPrimitives = props.bool("poolNbtPrimitives", true);
        this.cachePropertyHashes = props.bool("cachePropertyHashes", true);
        this.cacheStateHashes = props.bool("cacheStateHashes", true);
        this.resourceExistenceCache = props.bool("resourceExistenceCache", true);
        this.stacklessResourceExceptions = props.bool("stacklessResourceExceptions", true);
        this.fastAtlasStitching = props.bool("fastAtlasStitching", true);
        this.softStructureTemplates = props.bool("softStructureTemplates", true);
        this.internLoaderStrings = props.bool("internLoaderStrings", true);
        this.modScanCache = props.bool("modScanCache", true);
        this.dynamicModels = props.bool("dynamicModels", true);
        this.dynamicModelsPrebakeItems = props.bool("dynamicModelsPrebakeItems", true);
        this.dynamicModelsEagerNamespaces = props.list("dynamicModelsEagerNamespaces", DEFAULT_EAGER_NAMESPACES);
        this.fastItemLayerBaking = props.bool("fastItemLayerBaking", true);
        this.parallelTextureLoad = props.bool("parallelTextureLoad", true);
        this.parallelModelLoad = props.bool("parallelModelLoad", true);
        this.primitiveOreDictionary = props.bool("primitiveOreDictionary", true);
        this.canonicalizeModelParts = props.bool("canonicalizeModelParts", true);
        this.compactRemapperCaches = props.bool("compactRemapperCaches", true);
        this.recycleEvents = props.bool("recycleEvents", false);
        this.skipSoundDebugChecks = props.bool("skipSoundDebugChecks", true);
        this.plainMissingModels = props.bool("plainMissingModels", false);
        this.quietPrefixWarnings = props.bool("quietPrefixWarnings", true);
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
        values.put("deduplicateModelVariants", Boolean.toString(this.deduplicateModelVariants));
        values.put("compactNbtBackingMap", Boolean.toString(this.compactNbtBackingMap));
        values.put("internNbtKeys", Boolean.toString(this.internNbtKeys));
        values.put("nbtArrayMapThreshold", Integer.toString(this.nbtArrayMapThreshold));
        values.put("poolQuadVertexData", Boolean.toString(this.poolQuadVertexData));
        values.put("compactBakedModels", Boolean.toString(this.compactBakedModels));
        values.put("canonicalizeMultipartConditions", Boolean.toString(this.canonicalizeMultipartConditions));
        values.put("optimizeBlockStates", Boolean.toString(this.optimizeBlockStates));
        values.put("blockStateBlacklist", String.join(",", this.blockStateBlacklist));
        values.put("compactStateProperties", Boolean.toString(this.compactStateProperties));
        values.put("compactModelGraph", Boolean.toString(this.compactModelGraph));
        values.put("stripChunkNbt", Boolean.toString(this.stripChunkNbt));
        values.put("dropEmptyChunkSections", Boolean.toString(this.dropEmptyChunkSections));
        values.put("weakenClassLoaderCache", Boolean.toString(this.weakenClassLoaderCache));
        values.put("releaseSpriteData", Boolean.toString(this.releaseSpriteData));
        values.put("deduplicateModelTransforms", Boolean.toString(this.deduplicateModelTransforms));
        values.put("releaseBakeState", Boolean.toString(this.releaseBakeState));
        values.put("compactRuntimeCollections", Boolean.toString(this.compactRuntimeCollections));
        values.put("clearPoolsOnWorldLeave", Boolean.toString(this.clearPoolsOnWorldLeave));
        values.put("lazySearchTrees", Boolean.toString(this.lazySearchTrees));
        values.put("poolSizeLimit", Integer.toString(this.poolSizeLimit));
        values.put("showDebugOverlay", Boolean.toString(this.showDebugOverlay));
        values.put("lazyItemStackCapabilities", Boolean.toString(this.lazyItemStackCapabilities));
        values.put("poolNbtPrimitives", Boolean.toString(this.poolNbtPrimitives));
        values.put("cachePropertyHashes", Boolean.toString(this.cachePropertyHashes));
        values.put("cacheStateHashes", Boolean.toString(this.cacheStateHashes));
        values.put("resourceExistenceCache", Boolean.toString(this.resourceExistenceCache));
        values.put("stacklessResourceExceptions", Boolean.toString(this.stacklessResourceExceptions));
        values.put("fastAtlasStitching", Boolean.toString(this.fastAtlasStitching));
        values.put("softStructureTemplates", Boolean.toString(this.softStructureTemplates));
        values.put("internLoaderStrings", Boolean.toString(this.internLoaderStrings));
        values.put("modScanCache", Boolean.toString(this.modScanCache));
        values.put("dynamicModels", Boolean.toString(this.dynamicModels));
        values.put("dynamicModelsPrebakeItems", Boolean.toString(this.dynamicModelsPrebakeItems));
        values.put("dynamicModelsEagerNamespaces", String.join(",", this.dynamicModelsEagerNamespaces));
        values.put("fastItemLayerBaking", Boolean.toString(this.fastItemLayerBaking));
        values.put("parallelTextureLoad", Boolean.toString(this.parallelTextureLoad));
        values.put("parallelModelLoad", Boolean.toString(this.parallelModelLoad));
        values.put("primitiveOreDictionary", Boolean.toString(this.primitiveOreDictionary));
        values.put("canonicalizeModelParts", Boolean.toString(this.canonicalizeModelParts));
        values.put("compactRemapperCaches", Boolean.toString(this.compactRemapperCaches));
        values.put("recycleEvents", Boolean.toString(this.recycleEvents));
        values.put("skipSoundDebugChecks", Boolean.toString(this.skipSoundDebugChecks));
        values.put("plainMissingModels", Boolean.toString(this.plainMissingModels));
        values.put("quietPrefixWarnings", Boolean.toString(this.quietPrefixWarnings));

        return values;
    }
}
