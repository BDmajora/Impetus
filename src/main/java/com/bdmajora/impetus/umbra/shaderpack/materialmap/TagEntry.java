package com.bdmajora.impetus.umbra.shaderpack.materialmap;

import java.util.Map;

// Tag reference in a material map.
public final class TagEntry implements Entry {
    // Id of the tag.
    private final NamespacedId id;
    private final Map<String, String> propertyPredicates;

    // Creates a tag entry from its id and property predicates.
    public TagEntry(NamespacedId id, Map<String, String> propertyPredicates) {
        this.id = id;
        this.propertyPredicates = propertyPredicates;
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
