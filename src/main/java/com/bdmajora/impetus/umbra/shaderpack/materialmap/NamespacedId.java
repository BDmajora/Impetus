package com.bdmajora.impetus.umbra.shaderpack.materialmap;

import java.util.Objects;

// Name with a namespace.
public final class NamespacedId {
    // Namespace of the id.
    private final String namespace;
    // Path of the id.
    private final String name;

    // Creates a namespaced id from its combined.
    public NamespacedId(String combined) {
        int colonIdx = combined.indexOf(':');
        if (colonIdx == -1) {
            this.namespace = "minecraft";
            this.name = combined;
        } else {
            this.namespace = combined.substring(0, colonIdx);
            this.name = combined.substring(colonIdx + 1);
        }
    }

    // Creates a namespaced id from its namespace and name.
    public NamespacedId(String namespace, String name) {
        this.namespace = Objects.requireNonNull(namespace);
        this.name = Objects.requireNonNull(name);
    }

    // Returns the namespace.
    public String getNamespace() {
        return this.namespace;
    }

    // Returns the name.
    public String getName() {
        return this.name;
    }

    // Returns whether it equals.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamespacedId that = (NamespacedId) o;
        return this.namespace.equals(that.namespace) && this.name.equals(that.name);
    }

    // Computes the hash of the code.
    @Override
    public int hashCode() {
        return 31 * (31 + this.namespace.hashCode()) + this.name.hashCode();
    }

    // Returns the namespace and path text.
    @Override
    public String toString() {
        return this.namespace + ":" + this.name;
    }
}
