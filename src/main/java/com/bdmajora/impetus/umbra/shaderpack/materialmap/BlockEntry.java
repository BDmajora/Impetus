package com.bdmajora.impetus.umbra.shaderpack.materialmap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Block name with optional state properties.
public final class BlockEntry implements Entry {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Name of the block.
    private final NamespacedId id;
    private final Map<String, String> propertyPredicates;

    // Creates a block entry from its id and property predicates.
    public BlockEntry(NamespacedId id, Map<String, String> propertyPredicates) {
        this.id = id;
        this.propertyPredicates = propertyPredicates;
    }

    // Parses a block entry.
    public static Entry parse(String entry) {
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("Called BlockEntry::parse with an empty string");
        }

        boolean isTag = entry.startsWith("%");
        if (isTag) {
            entry = entry.replace("%", "");
        }
        entry = entry.replaceAll(":{2,}", ":");

        String[] splitStates = entry.split(":");

        if (splitStates.length == 1) {
            NamespacedId id = new NamespacedId("minecraft", entry);
            return isTag ? new TagEntry(id, Collections.emptyMap()) : new BlockEntry(id, Collections.emptyMap());
        }

        if (splitStates.length == 2 && !splitStates[1].contains("=")) {
            NamespacedId id = new NamespacedId(splitStates[0], splitStates[1]);
            return isTag ? new TagEntry(id, Collections.emptyMap()) : new BlockEntry(id, Collections.emptyMap());
        }

        int statesStart;
        NamespacedId id;
        if (splitStates[1].contains("=")) {
            statesStart = 1;
            id = new NamespacedId("minecraft", splitStates[0]);
        } else {
            statesStart = 2;
            id = new NamespacedId(splitStates[0], splitStates[1]);
        }

        Map<String, String> map = new HashMap<>();
        for (int index = statesStart; index < splitStates.length; index++) {
            String[] propertyParts = splitStates[index].split("=");
            if (propertyParts.length != 2) {
                LOGGER.warn("[Umbra] The block ID map entry \"{}\" could not be fully parsed:", entry);
                LOGGER.warn("[Umbra] - Block state property filters must be of the form \"key=value\", but {} is not", splitStates[index]);
                continue;
            }
            map.put(propertyParts[0], propertyParts[1]);
        }

        return isTag ? new TagEntry(id, map) : new BlockEntry(id, map);
    }

    // Returns the id.
    public NamespacedId getId() {
        return this.id;
    }

    // Returns the property predicates.
    public Map<String, String> getPropertyPredicates() {
        return this.propertyPredicates;
    }
}
