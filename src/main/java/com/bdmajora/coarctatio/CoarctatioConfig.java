package com.bdmajora.coarctatio;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// Plain Properties file because CoarctatioMixinPlugin reads it during coremod setup before Forge/MC classes are safe; Launch is the only outside class referenced
public final class CoarctatioConfig {
    private static final String FILE_NAME = "impetus-coarctatio.cfg";

    // BiblioCraft assumes the vanilla block state table exists; inherited from FoamFix, same wall.
    private static final String DEFAULT_BLOCK_STATE_BLACKLIST = "jds.bibliocraft";
    // Betweenlands and Dynamic Trees walk every one of their models at ModelBakeEvent (VintageFix's list).
    private static final String DEFAULT_EAGER_NAMESPACES = "thebetweenlands,dynamictrees";

    private static CoarctatioConfig instance;

    // Where save writes. Null only if the config directory could not be resolved.
    private Path file;

    // Interns the domain and path strings of every ResourceLocation.
    public boolean deduplicateResourceLocations;
    // Interns the variant string of every ModelResourceLocation.
    public boolean deduplicateModelVariants;
    // Replaces NBTTagCompound's HashMap with a compact array/hash hybrid.
    public boolean compactNbtBackingMap;
    // Interns NBT keys through a shared string pool. Requires compactNbtBackingMap.
    public boolean internNbtKeys;
    // Entry count at which an NBT compound switches from array storage to hash storage.
    public int nbtArrayMapThreshold;
    // Pools BakedQuad.vertexData arrays so identical geometry shares one array.
    public boolean poolQuadVertexData;
    // Replaces the model classes' growable collections with exact-sized immutable ones.
    public boolean compactBakedModels;
    // Flattens and interns the predicates produced by multipart blockstate conditions.
    public boolean canonicalizeMultipartConditions;
    // Replaces each state's property-value table with a packed int index into a shared per-block array; largest single saving, and a declined block falls back to vanilla states rather than crashing
    public boolean optimizeBlockStates;
    // Block implementation class prefixes that keep vanilla states regardless of the above.
    public String[] blockStateBlacklist;
    // Replaces each state's property ImmutableMap with a compact one sharing its key array; needs optimizeBlockStates and a JVM that allows defining into Guava's package, else degrades silently
    public boolean compactStateProperties;
    // Swaps the model graph's unordered hash maps for fastutil equivalents.
    public boolean compactModelGraph;
    // Strips a loaded chunk's NBT down to the tags entity loading still reads.
    public boolean stripChunkNbt;
    // Drops chunk sections holding no blocks and no light that could not be recomputed.
    public boolean dropEmptyChunkSections;
    // Swaps LaunchClassLoader's resource cache for one the GC can reclaim.
    public boolean weakenClassLoaderCache;
    // Releases the pixel data of static sprites once the atlas is on the GPU.
    public boolean releaseSpriteData;
    // Shares the camera transforms and override lists that every baked model carries.
    public boolean deduplicateModelTransforms;
    // Frees the model loader's unbaked models and load errors after baking.
    public boolean releaseBakeState;
    // Frees the play-scoped string pools when the player leaves a world or server.
    public boolean clearPoolsOnWorldLeave;
    // Defers building the creative search index until something actually searches.
    public boolean lazySearchTrees;
    // Compacts registry, entity data and chunk entity-lookup collections.
    public boolean compactRuntimeCollections;
    // Upper bound on any single deduplication pool, after which it stops accepting new entries.
    public int poolSizeLimit;
    // Adds a Coarctatio line to the F3 debug overlay.
    public boolean showDebugOverlay;
    // Builds an ItemStack's capability dispatcher on the first capability query instead of in every constructor.
    public boolean lazyItemStackCapabilities;
    // Shares one instance per small numeric NBT value across every compound and list.
    public boolean poolNbtPrimitives;
    // Caches the hash of enum and integer block properties, which vanilla recomputes from their value sets on every call.
    public boolean cachePropertyHashes;
    // Caches each block state's hash, which vanilla recomputes from its property map on every call.
    public boolean cacheStateHashes;
    // Indexes resource packs once per reload instead of a native or filesystem lookup per probe.
    public boolean resourceExistenceCache;
    // Throws the resource manager's file-not-found without filling a stack trace.
    public boolean stacklessResourceExceptions;
    // Packs the texture atlas with a shelf algorithm instead of vanilla's recursive slot search.
    public boolean fastAtlasStitching;
    // Lets the collector reclaim structure templates that world generation has moved past.
    public boolean softStructureTemplates;
    // Interns the class, package and annotation names Forge's mod scan keeps for the session.
    public boolean internLoaderStrings;
    // Keeps each mod jar's annotation scan between launches, keyed by the jar's size and modification time.
    public boolean modScanCache;
    // Loads and bakes models on first use instead of all of them at startup, with the atlas built from a scan of the packs; the switch to flip first if models misbehave, since it changes what mods see at ModelBakeEvent
    public boolean dynamicModels;
    // With dynamic models, bakes every item model on a background thread after a reload so the inventory never bakes on the render thread.
    public boolean dynamicModelsPrebakeItems;
    // Namespaces whose models are still loaded up front under dynamic models, for mods that expect all of theirs to exist at ModelBakeEvent.
    public String[] dynamicModelsEagerNamespaces;
    // Writes item-model quads directly in the default vertex format instead of through Forge's unpacking builder.
    public boolean fastItemLayerBaking;
    // Decodes every atlas sprite's PNG across the common pool before the atlas loop, which otherwise decodes them one at a time on the client thread (StellarCore).
    public boolean parallelTextureLoad;
    // Backs the ore dictionary's two lookup maps with primitive collections instead of boxed Integers and List<Integer> (StellarCore).
    public boolean primitiveOreDictionary;
    // Pools the UV arrays and texture-name strings of unbaked model parts, and stores each element's faces in an EnumMap (StellarCore).
    public boolean canonicalizeModelParts;
    // Shares the runtime deobfuscator's per-class member maps between classes with identical ones and drops the empty ones (Chibi's optimizeFMLRemapper).
    public boolean compactRemapperCaches;
    // Reuses one instance per thread of the tick, capability-attach and neighbour-notify events instead of constructing one per post (Chibi's makeEventsSingletons); experimental, off by default.
    public boolean recycleEvents;
    // Skips the sound handler's two debug walks over the whole sound registry after every resource reload (UniversalTweaks).
    public boolean skipSoundDebugChecks;
    // Uses the shared plain missing model for models that fail to load instead of a per-model one rendering the location as text (UniversalTweaks); off by default since a few mods rely on the fancy one.
    public boolean plainMissingModels;
    // Returns a registry name that already carries a namespace as given, without the per-name "alternative prefix" warning (UniversalTweaks).
    public boolean quietPrefixWarnings;

    private CoarctatioConfig(Properties props) {
        this.deduplicateResourceLocations = bool(props, "deduplicateResourceLocations", true);
        this.deduplicateModelVariants = bool(props, "deduplicateModelVariants", true);
        this.compactNbtBackingMap = bool(props, "compactNbtBackingMap", true);
        this.internNbtKeys = bool(props, "internNbtKeys", true);
        this.nbtArrayMapThreshold = integer(props, "nbtArrayMapThreshold", 12, 0, 1024);
        this.poolQuadVertexData = bool(props, "poolQuadVertexData", true);
        this.compactBakedModels = bool(props, "compactBakedModels", true);
        this.canonicalizeMultipartConditions = bool(props, "canonicalizeMultipartConditions", true);
        this.optimizeBlockStates = bool(props, "optimizeBlockStates", true);
        this.blockStateBlacklist = list(props, "blockStateBlacklist", DEFAULT_BLOCK_STATE_BLACKLIST);
        this.compactStateProperties = bool(props, "compactStateProperties", true);
        this.compactModelGraph = bool(props, "compactModelGraph", true);
        this.stripChunkNbt = bool(props, "stripChunkNbt", true);
        this.dropEmptyChunkSections = bool(props, "dropEmptyChunkSections", true);
        this.weakenClassLoaderCache = bool(props, "weakenClassLoaderCache", true);
        this.releaseSpriteData = bool(props, "releaseSpriteData", true);
        this.deduplicateModelTransforms = bool(props, "deduplicateModelTransforms", true);
        this.releaseBakeState = bool(props, "releaseBakeState", true);
        this.compactRuntimeCollections = bool(props, "compactRuntimeCollections", true);
        this.clearPoolsOnWorldLeave = bool(props, "clearPoolsOnWorldLeave", true);
        this.lazySearchTrees = bool(props, "lazySearchTrees", true);
        this.poolSizeLimit = integer(props, "poolSizeLimit", 262144, 1024, Integer.MAX_VALUE);
        this.showDebugOverlay = bool(props, "showDebugOverlay", true);
        this.lazyItemStackCapabilities = bool(props, "lazyItemStackCapabilities", true);
        this.poolNbtPrimitives = bool(props, "poolNbtPrimitives", true);
        this.cachePropertyHashes = bool(props, "cachePropertyHashes", true);
        this.cacheStateHashes = bool(props, "cacheStateHashes", true);
        this.resourceExistenceCache = bool(props, "resourceExistenceCache", true);
        this.stacklessResourceExceptions = bool(props, "stacklessResourceExceptions", true);
        this.fastAtlasStitching = bool(props, "fastAtlasStitching", true);
        this.softStructureTemplates = bool(props, "softStructureTemplates", true);
        this.internLoaderStrings = bool(props, "internLoaderStrings", true);
        this.modScanCache = bool(props, "modScanCache", true);
        this.dynamicModels = bool(props, "dynamicModels", true);
        this.dynamicModelsPrebakeItems = bool(props, "dynamicModelsPrebakeItems", true);
        this.dynamicModelsEagerNamespaces = list(props, "dynamicModelsEagerNamespaces", DEFAULT_EAGER_NAMESPACES);
        this.fastItemLayerBaking = bool(props, "fastItemLayerBaking", true);
        this.parallelTextureLoad = bool(props, "parallelTextureLoad", true);
        this.primitiveOreDictionary = bool(props, "primitiveOreDictionary", true);
        this.canonicalizeModelParts = bool(props, "canonicalizeModelParts", true);
        this.compactRemapperCaches = bool(props, "compactRemapperCaches", true);
        this.recycleEvents = bool(props, "recycleEvents", false);
        this.skipSoundDebugChecks = bool(props, "skipSoundDebugChecks", true);
        this.plainMissingModels = bool(props, "plainMissingModels", false);
        this.quietPrefixWarnings = bool(props, "quietPrefixWarnings", true);
    }

    // Loads on first use and caches; every reader shares the one instance
    public static CoarctatioConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the file if present, otherwise starts from defaults and writes them out
    private static CoarctatioConfig load() {
        Path file = configDirectory().resolve(FILE_NAME);

        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                Coarctatio.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            }
        }

        CoarctatioConfig config = new CoarctatioConfig(props);
        config.file = file;
        config.save();
        return config;
    }

    // Most switches take effect on next launch (read once by CoarctatioMixinPlugin); showDebugOverlay and the NBT map settings are read live
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
            Coarctatio.LOGGER.warn("Could not create {}, configuration will not persist", dir, e);
        }

        return dir;
    }

    // Rewrites every key so a user who never opens the file still discovers the switches; user-set values are preserved verbatim
    private void writeBack(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
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
        values.put("primitiveOreDictionary", Boolean.toString(this.primitiveOreDictionary));
        values.put("canonicalizeModelParts", Boolean.toString(this.canonicalizeModelParts));
        values.put("compactRemapperCaches", Boolean.toString(this.compactRemapperCaches));
        values.put("recycleEvents", Boolean.toString(this.recycleEvents));
        values.put("skipSoundDebugChecks", Boolean.toString(this.skipSoundDebugChecks));
        values.put("plainMissingModels", Boolean.toString(this.plainMissingModels));
        values.put("quietPrefixWarnings", Boolean.toString(this.quietPrefixWarnings));

        Properties out = new Properties();
        out.putAll(values);

        try (OutputStream stream = Files.newOutputStream(file)) {
            out.store(stream, "Impetus / Coarctatio memory subsystem. Delete a line to restore its default.");
        } catch (IOException e) {
            Coarctatio.LOGGER.warn("Could not write {}", file, e);
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

    // Comma-separated list; blank entries are dropped so a trailing comma is harmless.
    private static String[] list(Properties props, String key, String fallback) {
        String value = props.getProperty(key);

        if (value == null) {
            value = fallback;
        }

        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            entry = entry.trim();

            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }

        return entries.toArray(new String[0]);
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
