package com.bdmajora.impetus.umbra.shaderpack.materialmap;

import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The pack's block, item and entity .properties as descriptions, preprocessed first so option gates resolve; BlockMaterialMapping resolves them against the registry later, and no block.properties means raw block ids, as classic packs expect
public final class IdMap {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private static final AbsolutePackPath BLOCK_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/block.properties");
    private static final AbsolutePackPath ITEM_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/item.properties");
    private static final AbsolutePackPath ENTITY_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/entity.properties");

    // block.<id> entries in DECLARATION ORDER, which is significant: first match wins, matching OptiFine
    private final Map<Integer, List<BlockEntry>> blockPropertiesMap;
    // item.<id> entries. Parsed so a pack declaring them loads cleanly; nothing consumes them yet
    private final Map<NamespacedId, Integer> itemIdMap;
    // entity.<id> entries, likewise parsed but not yet consumed
    private final Map<NamespacedId, Integer> entityIdMap;
    private final boolean hasBlockProperties;
    // layer.<rendertype> = <block> ... from block.properties, the pack reassigning a block's chunk render layer (OptiFine's "Block render layers"); block id -> target BlockRenderLayer
    private final Map<NamespacedId, BlockRenderLayer> blockRenderLayerMap;

    public IdMap(Map<AbsolutePackPath, String> sources, Map<String, String> preprocessorDefines) {
        String blockProperties = sources.get(BLOCK_PROPERTIES);
        this.hasBlockProperties = blockProperties != null;
        this.blockPropertiesMap = blockProperties != null
                ? parseBlockMapWithModernFallback(blockProperties, preprocessorDefines)
                : Collections.emptyMap();

        String itemProperties = sources.get(ITEM_PROPERTIES);
        this.itemIdMap = itemProperties != null
                ? parseIdMap(PropertiesPreprocessor.preprocess(itemProperties, preprocessorDefines), "item.")
                : Collections.emptyMap();

        String entityProperties = sources.get(ENTITY_PROPERTIES);
        this.entityIdMap = entityProperties != null
                ? parseIdMap(PropertiesPreprocessor.preprocess(entityProperties, preprocessorDefines), "entity.")
                : Collections.emptyMap();

        this.blockRenderLayerMap = blockProperties != null
                ? parseRenderLayerMap(PropertiesPreprocessor.preprocess(blockProperties, preprocessorDefines))
                : Collections.emptyMap();
    }

    // The layer overrides, consumed by the mesher when it decides which layer each block belongs to
    public Map<NamespacedId, BlockRenderLayer> getBlockRenderLayerMap() {
        return this.blockRenderLayerMap;
    }

    // Parses layer.solid, layer.cutout, layer.cutout_mipped and layer.translucent (OptiFine's exact four, anything else is a pack error); tag entries are rejected like Iris since 1.12.2 cannot enumerate a tag
    private static Map<NamespacedId, BlockRenderLayer> parseRenderLayerMap(String preprocessed) {
        Map<NamespacedId, BlockRenderLayer> overrides = new LinkedHashMap<>();
        for (String rawLine : preprocessed.split("\r\n|\r|\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.charAt(0) == '#' || !line.startsWith("layer.")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String type = line.substring("layer.".length(), eq).trim();
            BlockRenderLayer layer = parseRenderLayer(type);
            if (layer == null) {
                LOGGER.warn("[Umbra] block.properties: invalid block render type \"{}\", ignoring it", type);
                continue;
            }
            for (String part : line.substring(eq + 1).trim().split("\\s+")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (part.startsWith("%")) {
                    LOGGER.warn("[Umbra] block.properties: cannot use the tag \"{}\" in a render-layer override", part);
                    continue;
                }
                // Strip any block-state qualifier (`minecraft:glass:color=red`); the layer applies to the block.
                String id = part.split(":(?=[^:]*=)")[0];
                overrides.put(new NamespacedId(id), layer);
            }
        }
        return overrides;
    }

    // layer.<name> value to a render layer; null when unknown
    private static BlockRenderLayer parseRenderLayer(String name) {
        return switch (name) {
            case "solid" -> BlockRenderLayer.SOLID;
            case "cutout" -> BlockRenderLayer.CUTOUT;
            case "cutout_mipped" -> BlockRenderLayer.CUTOUT_MIPPED;
            case "translucent" -> BlockRenderLayer.TRANSLUCENT;
            default -> null;
        };
    }

    // The MC_VERSION to claim when a pack has no 1.12.2 mapping; 11300 is the FIRST flattened version and selects the oldest, closest set of modern names
    private static final String FALLBACK_MC_VERSION = "11300";

    // Preprocesses block.properties honestly for 1.12.2, and only when that yields NO block IDs re-reads it as a 1.13+ pack translated through ModernBlockNames; Photon and most post-1.16 packs gate their map on `MC_VERSION >= 11300` with an empty #else, leaving every block mc_Entity.x == 0 (water flat, nothing emissive). A pack with a 1.12 branch is left alone
    private static Map<Integer, List<BlockEntry>> parseBlockMapWithModernFallback(
            String blockProperties, Map<String, String> preprocessorDefines) {
        Map<Integer, List<BlockEntry>> declared =
                parseBlockMap(PropertiesPreprocessor.preprocess(blockProperties, preprocessorDefines));
        if (!declared.isEmpty()) {
            return declared;
        }

        Map<String, String> modernDefines = new LinkedHashMap<>(preprocessorDefines);
        modernDefines.put("MC_VERSION", FALLBACK_MC_VERSION);
        Map<Integer, List<BlockEntry>> modern =
                parseBlockMap(PropertiesPreprocessor.preprocess(blockProperties, modernDefines));
        if (modern.isEmpty()) {
            return declared;
        }

        Map<Integer, List<BlockEntry>> translated = new LinkedHashMap<>();
        modern.forEach((intId, entries) -> {
            List<BlockEntry> legacy = new ArrayList<>(entries.size());
            for (BlockEntry entry : entries) {
                legacy.addAll(ModernBlockNames.translate(entry));
            }
            translated.put(intId, Collections.unmodifiableList(legacy));
        });
        return translated;
    }

    // Parses the `block.<id> = entry entry ...` lines out of already-preprocessed properties text
    private static Map<Integer, List<BlockEntry>> parseBlockMap(String preprocessed) {
        Map<Integer, List<BlockEntry>> entriesById = new LinkedHashMap<>();

        forEachProperty(preprocessed, "block.", (intId, value) -> {
            List<BlockEntry> entries = new ArrayList<>();
            for (String rawPart : value.split("\\s+")) {
                if (rawPart.isEmpty()) {
                    continue;
                }
                for (String part : separateRunTogetherIds(rawPart, intId)) {
                    try {
                        // A tag entry is dropped: 1.12.2 has no block tags, and packs only reference them behind MC_VERSION gates anyway
                        if (BlockEntry.parse(part) instanceof BlockEntry entry) {
                            entries.add(entry);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[Umbra] Unexpected error while parsing a block.properties entry for block.{}: {}",
                                intId, e.getMessage());
                    }
                }
            }
            entriesById.put(intId, Collections.unmodifiableList(entries));
        });

        return entriesById;
    }

    // Recovers two block IDs a pack ran together by omitting the space (BSL 10.1.3 ships minecraft:gold_oreminecraft:redstone_ore, silently losing both ores' material); safe because a legal token has at most one non-= segment after the namespace, so two consecutive non-= segments can only be a missing separator, split at the trailing embedded namespace
    private static List<String> separateRunTogetherIds(String token, int intId) {
        String[] parts = token.split(":");

        if (parts.length < 3) {
            return Collections.singletonList(token);
        }

        for (int i = 1; i <= parts.length - 2; i++) {
            if (parts[i].contains("=") || parts[i + 1].contains("=")) {
                continue;
            }

            // parts[i] is "<path><namespace>"; the namespace of the token we are already inside is by far the likeliest, since this is a typo in a list of same-namespace entries
            String namespace = findTrailingNamespace(parts[i], parts[0]);

            if (namespace == null) {
                continue;
            }

            String path = parts[i].substring(0, parts[i].length() - namespace.length());
            String head = String.join(":", Arrays.copyOfRange(parts, 0, i)) + ":" + path;
            String tail = namespace + ":" + String.join(":", Arrays.copyOfRange(parts, i + 1, parts.length));

            List<String> recovered = new ArrayList<>();
            recovered.add(head);
            // Recurse: three or more IDs can be run together by the same mistake.
            recovered.addAll(separateRunTogetherIds(tail, intId));

            LOGGER.warn("[Umbra] block.{} entry \"{}\" is missing a space between IDs; reading it as {}",
                    intId, token, recovered);

            return recovered;
        }

        return Collections.singletonList(token);
    }

    // The namespace this segment ends with, provided a non-empty path remains in front of it (which stops a bare namespace matching), or null
    private static String findTrailingNamespace(String segment, String enclosingNamespace) {
        for (String candidate : new String[] {enclosingNamespace, "minecraft"}) {
            if (candidate == null || candidate.isEmpty() || candidate.length() >= segment.length()) {
                continue;
            }

            if (segment.endsWith(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    // Parses the plain `<prefix><id> = name name ...` shape of item.properties and entity.properties, which carry no state predicates
    private static Map<NamespacedId, Integer> parseIdMap(String preprocessed, String prefix) {
        Map<NamespacedId, Integer> idMap = new LinkedHashMap<>();
        forEachProperty(preprocessed, prefix, (intId, value) -> {
            for (String part : value.split("\\s+")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (part.contains("=")) {
                    LOGGER.warn("[Umbra] State properties are not supported in {}<id> entries: {}", prefix, part);
                    continue;
                }
                idMap.put(new NamespacedId(part), intId);
            }
        });
        return Collections.unmodifiableMap(idMap);
    }

    private interface PropertyConsumer {
        void accept(int intId, String value);
    }

    // Walks the `<prefix><int> = value` lines in DECLARATION ORDER (first-match-wins depends on it), split by hand on the first = since java.util.Properties' backslash escapes would mangle paths and the preprocessor already joined continuations
    private static void forEachProperty(String preprocessed, String prefix, PropertyConsumer consumer) {
        for (String line : preprocessed.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith(prefix)) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            int intId;
            try {
                intId = Integer.parseInt(key.substring(prefix.length()));
            } catch (NumberFormatException e) {
                LOGGER.warn("[Umbra] Failed to parse properties line: invalid key {}", key);
                continue;
            }
            consumer.accept(intId, value);
        }
    }

    // Whether the pack ships a block.properties at all; without one raw 1.12.2 block IDs are the contract and the mesher emits those
    public boolean hasBlockProperties() {
        return this.hasBlockProperties;
    }

    // block.properties entries by pack id
    public Map<Integer, List<BlockEntry>> getBlockProperties() {
        return this.blockPropertiesMap;
    }

    // item.properties
    public Map<NamespacedId, Integer> getItemIdMap() {
        return this.itemIdMap;
    }

    // entity.properties
    public Map<NamespacedId, Integer> getEntityIdMap() {
        return this.entityIdMap;
    }
}
