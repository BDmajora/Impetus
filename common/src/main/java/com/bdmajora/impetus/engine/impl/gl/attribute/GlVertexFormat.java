package com.bdmajora.impetus.engine.impl.gl.attribute;

import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

// Vertex layout of named attributes at byte offsets inside a vertex of fixed stride
public class GlVertexFormat {
    // Pointer value that places an attribute at the next aligned offset after the furthest attribute end
    public static final int NEXT_ALIGNED_POINTER = -1;
    // Alignment in bytes of an offset chosen for NEXT_ALIGNED_POINTER
    private static final int ATTRIBUTE_ALIGNMENT = 4;

    // Attributes keyed by name in declaration order
    private final Object2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributesKeyed;
    // Read only view of the attributes in declaration order
    private final Collection<GlVertexAttribute> attributes;
    // Size of one vertex in bytes
    private final int stride;

    // Creates a format over a copy of the given map and rejects a null map
    public GlVertexFormat(Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributesKeyed, int stride) {
        this(new Object2ReferenceLinkedOpenHashMap<>(Objects.requireNonNull(attributesKeyed, "Attributes must not be null")), stride);
    }

    // Creates a format that reads the given map without copying it
    private GlVertexFormat(Object2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributesKeyed, int stride) {
        this.attributesKeyed = attributesKeyed;
        this.attributes = Collections.unmodifiableCollection(attributesKeyed.values());
        this.stride = stride;
    }

    // Creates a builder of a format with the given stride
    public static Builder builder(int stride) {
        return new Builder(stride);
    }

    // Returns the attribute bound to the name and throws NullPointerException for a name the format does not hold
    public GlVertexAttribute getAttribute(String name) {
        GlVertexAttribute attribute = this.attributesKeyed.get(name);

        if (attribute == null) {
            throw new NullPointerException("No attribute exists for " + name);
        }

        return attribute;
    }

    // Returns a read only view of the attributes in declaration order
    public Collection<GlVertexAttribute> getAttributes() {
        return this.attributes;
    }

    // Returns the size of one vertex in bytes
    public int getStride() {
        return this.stride;
    }

    // Returns the attribute count and the stride as text
    @Override
    public String toString() {
        return "GlVertexFormat{attributes=" + this.attributesKeyed.size() + ",stride=" + this.stride + "}";
    }

    // Collects attributes and creates formats from them
    public static class Builder {
        // Order of attributes by ascending pointer
        private static final Comparator<GlVertexAttribute> BY_POINTER = (left, right) -> Integer.compare(left.getPointer(), right.getPointer());

        // Size of one vertex in bytes
        private final int stride;
        // Attributes keyed by name in insertion order
        private Object2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributes;
        // Flag set while a built format reads the attribute map
        private boolean shared;
        // Furthest end offset of any attribute in bytes
        private int end;
        // Attribute that sorts last by pointer
        private GlVertexAttribute top;
        // Later attribute of the first overlapping pair in pointer order
        private GlVertexAttribute overlapping;
        // Earlier attribute of the first overlapping pair in pointer order
        private GlVertexAttribute overlapped;
        // Flag set when an attribute arrived below the top pointer after the last overlap search
        private boolean unsorted;

        // Creates an empty builder of a format with the given stride
        public Builder(int stride) {
            this.attributes = new Object2ReferenceLinkedOpenHashMap<>();
            this.stride = stride;
        }

        // Adds a copy of every attribute of the other format
        public Builder addAllElements(GlVertexFormat otherFormat) {
            return this.addElements(otherFormat.getAttributes());
        }

        // Adds a copy of each attribute with the stride of this builder
        public Builder addElements(Collection<GlVertexAttribute> attributes) {
            for (GlVertexAttribute attribute : attributes) {
                this.addElement(new GlVertexAttribute(attribute.getFormat(), attribute.getName(), attribute.getCount(), attribute.isNormalized(), attribute.getPointer(), this.stride, attribute.isIntType()));
            }

            return this;
        }

        // Adds an attribute at the pointer or at the next aligned offset for NEXT_ALIGNED_POINTER
        public Builder addElement(String name, int pointer, GlVertexAttributeFormat format, int count, boolean normalized, boolean intType) {
            if (pointer == NEXT_ALIGNED_POINTER) {
                pointer = this.nextPointer();
            }

            return this.addElement(new GlVertexAttribute(format, name, count, normalized, pointer, this.stride, intType));
        }

        // Returns the furthest end rounded up to the alignment or the largest int when the rounding overflows
        private int nextPointer() {
            int next = (this.end + ATTRIBUTE_ALIGNMENT - 1) & -ATTRIBUTE_ALIGNMENT;
            return next < 0 ? Integer.MAX_VALUE : next;
        }

        // Adds the attribute and rejects one past the stride or one whose name the builder holds
        private Builder addElement(GlVertexAttribute attribute) {
            int pointer = attribute.getPointer();
            int end = pointer + attribute.getSize();

            if (end > this.stride) {
                throw this.outsideError(attribute);
            }

            // Copies the map before the first change after a build
            if (this.shared) {
                this.attributes = this.attributes.clone();
                this.shared = false;
            }

            if (this.attributes.putIfAbsent(attribute.getName(), attribute) != null) {
                throw new IllegalStateException("Generic attribute " + attribute.getName() + " already defined in vertex format");
            }

            this.end = Math.max(this.end, end);
            GlVertexAttribute top = this.top;

            if (top == null) {
                this.top = attribute;
            } else if (pointer < top.getPointer()) {
                // Leaves the overlap search to the next build
                this.unsorted = true;
            } else {
                // Records the first overlap with the attribute before it in pointer order
                if (this.overlapping == null && pointer < top.getPointer() + top.getSize()) {
                    this.overlapping = attribute;
                    this.overlapped = top;
                }

                this.top = attribute;
            }

            return this;
        }

        // Creates the exception for an attribute that starts or ends past the stride
        private IllegalArgumentException outsideError(GlVertexAttribute attribute) {
            if (attribute.getPointer() >= this.stride) {
                return new IllegalArgumentException("Element " + attribute.getName() + " starts outside vertex format (" + attribute.getPointer() + ", stride is " + this.stride + ")");
            }

            return new IllegalArgumentException("Element extends outside vertex format");
        }

        // Creates a format of the added attributes and rejects an empty or overlapping layout
        public GlVertexFormat build() {
            if (this.top == null) {
                throw new IllegalArgumentException("Attempted to construct vertex format with no attributes");
            }

            if (this.unsorted) {
                this.findOverlap();
            }

            if (this.overlapping != null) {
                throw new IllegalArgumentException("Attribute " + this.overlapping.getName() + " overlaps with " + this.overlapped.getName());
            }

            this.shared = true;
            return new GlVertexFormat(this.attributes, this.stride);
        }

        // Stores the first overlapping pair in pointer order from a sorted copy of the attributes
        private void findOverlap() {
            GlVertexAttribute[] sorted = this.attributes.values().toArray(new GlVertexAttribute[this.attributes.size()]);
            Arrays.sort(sorted, BY_POINTER);
            this.unsorted = false;
            this.overlapping = null;
            GlVertexAttribute previous = sorted[0];

            for (int index = 1; index < sorted.length; index++) {
                GlVertexAttribute current = sorted[index];

                if (current.getPointer() < previous.getPointer() + previous.getSize()) {
                    this.overlapping = current;
                    this.overlapped = previous;
                    return;
                }

                previous = current;
            }
        }
    }
}
