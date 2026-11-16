package com.bdmajora.coarctatio;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.util.Map;

// Settings of the memory features loaded from the configuration file.
public final class CoarctatioConfig {
    // Name of the configuration file.
    private static final String FILE_NAME = "impetus-coarctatio.cfg";

    // Default class prefixes excluded from state packing.
    private static final String DEFAULT_BLOCK_STATE_BLACKLIST = "jds.bibliocraft";
    // Default namespaces whose models load eagerly.
    private static final String DEFAULT_EAGER_NAMESPACES = "thebetweenlands,dynamictrees";

    // Loaded settings or null before the first use.
    private static CoarctatioConfig instance;

    // File that backs the settings.
    private PropertiesConfig file;

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
        this.dynamicModels = props.bool("dynamicModels", false);
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
        PropertiesConfig props = new PropertiesConfig(Coarctatio.LOGGER, FILE_NAME, "Impetus / Coarctatio memory subsystem. Delete a line to restore its default.");
        props.load();
        CoarctatioConfig config = new CoarctatioConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    // Writes the settings to the file.
    public void save() {
        this.file.save(values());
    }

    // Returns every setting as text by name.
    private Map<String, String> values() {
        Map<String, String> values = PropertiesConfig.values();
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
