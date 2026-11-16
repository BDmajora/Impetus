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

// Block and item and entity id mappings of a pack.
public final class IdMap {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Block properties.
    private static final AbsolutePackPath BLOCK_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/block.properties");
    // Item properties.
    private static final AbsolutePackPath ITEM_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/item.properties");
    // Entity properties.
    private static final AbsolutePackPath ENTITY_PROPERTIES = AbsolutePackPath.fromAbsolutePath("/entity.properties");

    private final Map<Integer, List<BlockEntry>> blockPropertiesMap;
    private final Map<NamespacedId, Integer> itemIdMap;
    private final Map<NamespacedId, Integer> entityIdMap;
    // Has block properties.
    private final boolean hasBlockProperties;
    private final Map<NamespacedId, BlockRenderLayer> blockRenderLayerMap;

    // Creates an id map from its sources and preprocessor defines.
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

    // Returns the block render layer map.
    public Map<NamespacedId, BlockRenderLayer> getBlockRenderLayerMap() {
        return this.blockRenderLayerMap;
    }

    // Parses the render layer overrides.
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
                String id = part.split(":(?=[^:]*=)")[0];
                overrides.put(new NamespacedId(id), layer);
            }
        }
        return overrides;
    }

    // Returns the layer of a name or null.
    private static BlockRenderLayer parseRenderLayer(String name) {
        return switch (name) {
            case "solid" -> BlockRenderLayer.SOLID;
            case "cutout" -> BlockRenderLayer.CUTOUT;
            case "cutout_mipped" -> BlockRenderLayer.CUTOUT_MIPPED;
            case "translucent" -> BlockRenderLayer.TRANSLUCENT;
            default -> null;
        };
    }

    // Game version used when the pack has no legacy branch.
    private static final String FALLBACK_MC_VERSION = "11300";

    // Parses the block map and retries with the modern branch when empty.
    private static Map<Integer, List<BlockEntry>> parseBlockMapWithModernFallback(
            // Block properties text and defines.
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

    // Parses the block map.
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

    // Splits ids that were written without spaces.
    private static List<String> separateRunTogetherIds(String token, int intId) {
        String[] parts = token.split(":");

        if (parts.length < 3) {
            return Collections.singletonList(token);
        }

        for (int i = 1; i <= parts.length - 2; i++) {
            if (parts[i].contains("=") || parts[i + 1].contains("=")) {
                continue;
            }

            String namespace = findTrailingNamespace(parts[i], parts[0]);

            if (namespace == null) {
                continue;
            }

            String path = parts[i].substring(0, parts[i].length() - namespace.length());
            String head = String.join(":", Arrays.copyOfRange(parts, 0, i)) + ":" + path;
            String tail = namespace + ":" + String.join(":", Arrays.copyOfRange(parts, i + 1, parts.length));

            List<String> recovered = new ArrayList<>();
            recovered.add(head);
            recovered.addAll(separateRunTogetherIds(tail, intId));

            LOGGER.warn("[Umbra] block.{} entry \"{}\" is missing a space between IDs; reading it as {}",
                    intId, token, recovered);

            return recovered;
        }

        return Collections.singletonList(token);
    }

    // Returns the namespace at the end of a segment or null.
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

    // Parses an item or entity id map.
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

    // Contract of the property consumer.
    private interface PropertyConsumer {
        // Records a value under an id.
        void accept(int intId, String value);
    }

    // Fors the each property.
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

    // Returns whether block properties.
    public boolean hasBlockProperties() {
        return this.hasBlockProperties;
    }

    // Returns the block properties.
    public Map<Integer, List<BlockEntry>> getBlockProperties() {
        return this.blockPropertiesMap;
    }

    // Returns the item id map.
    public Map<NamespacedId, Integer> getItemIdMap() {
        return this.itemIdMap;
    }

    // Returns the entity id map.
    public Map<NamespacedId, Integer> getEntityIdMap() {
        return this.entityIdMap;
    }
}
