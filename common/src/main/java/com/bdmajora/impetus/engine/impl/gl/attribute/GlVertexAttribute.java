package com.bdmajora.impetus.engine.impl.gl.attribute;

// Layout of one vertex attribute with its component type and its byte placement inside a vertex
public class GlVertexAttribute {
    // Component type and component size in bytes
    private final GlVertexAttributeFormat format;
    // Number of components
    private final int count;
    // Byte offset of the first component inside a vertex
    private final int pointer;
    // Size of all components in bytes
    private final int size;
    // Distance in bytes between the starts of consecutive vertices or zero for tightly packed vertices
    private final int stride;
    // Flag that maps integer data to between 0 and 1 or between negative 1 and 1
    private final boolean normalized;
    // Flag that keeps integer data integral through glVertexAttribIPointer
    private final boolean intType;
    // Name of the attribute
    private final String name;

    // Creates an attribute of count components and derives its size from the format
    public GlVertexAttribute(GlVertexAttributeFormat format, String name, int count, boolean normalized, int pointer, int stride, boolean intType) {
        // Reads the component size or zero for a null format
        int unit = format == null ? 0 : format.size();
        this(unit, (long) unit * count, format, name, count, normalized, pointer, stride, intType);
    }

    // Creates an attribute with the given size in bytes
    protected GlVertexAttribute(GlVertexAttributeFormat format, int size, int count, String name, boolean normalized, int pointer, int stride, boolean intType) {
        this(format == null ? 0 : format.size(), size, format, name, count, normalized, pointer, stride, intType);
    }

    // Stores the layout and rejects a null format or name or an invalid count or byte placement
    private GlVertexAttribute(int unit, long size, GlVertexAttributeFormat format, String name, int count, boolean normalized, int pointer, int stride, boolean intType) {
        // Reads true for a null argument or a count not between 1 and 4 or a negative offset or a size or end outside the int range or a misaligned offset
        if (name == null | unit <= 0 | count - 1 >>> 2 != 0 | (pointer | stride) < 0 | size <= 0 | pointer + size > Integer.MAX_VALUE
                || (pointer % unit | stride % unit) != 0) {
            throw layoutError(format, name, count, size, pointer, stride);
        }

        this.format = format;
        this.size = (int) size;
        this.count = count;
        this.normalized = normalized;
        this.pointer = pointer;
        this.stride = stride;
        this.intType = intType;
        this.name = name;
    }

    // Creates the exception for the first argument that the layout check refuses
    private static RuntimeException layoutError(GlVertexAttributeFormat format, String name, int count, long size, int pointer, int stride) {
        if (format == null) {
            return new NullPointerException("Format must not be null");
        }

        if (name == null) {
            return new NullPointerException("Name must not be null");
        }

        int unit = format.size();

        if (unit <= 0) {
            return new IllegalArgumentException("Format size must be positive");
        }

        if (count < 1 | count > 4) {
            return new IllegalArgumentException("Count must be between 1 and 4");
        }

        if (pointer < 0) {
            return new IllegalArgumentException("Pointer must not be negative");
        }

        if (stride < 0) {
            return new IllegalArgumentException("Stride must not be negative");
        }

        if (size <= 0) {
            return new IllegalArgumentException("Size must be positive");
        }

        if (size > Integer.MAX_VALUE) {
            return new IllegalArgumentException("Size must not exceed " + Integer.MAX_VALUE);
        }

        if (pointer + size > Integer.MAX_VALUE) {
            return new IllegalArgumentException("Attribute end must not exceed " + Integer.MAX_VALUE);
        }

        if (pointer % unit != 0) {
            return new IllegalArgumentException("Pointer must be a multiple of " + unit);
        }

        return new IllegalArgumentException("Stride must be a multiple of " + unit);
    }

    // Returns the component type and component size
    public GlVertexAttributeFormat getFormat() {
        return this.format;
    }

    // Returns the number of components
    public int getCount() {
        return this.count;
    }

    // Returns the byte offset of the first component inside a vertex
    public int getPointer() {
        return this.pointer;
    }

    // Returns the size of all components in bytes
    public int getSize() {
        return this.size;
    }

    // Returns the distance in bytes between the starts of consecutive vertices
    public int getStride() {
        return this.stride;
    }

    // Returns the name of the attribute
    public String getName() {
        return this.name;
    }

    // Returns true when integer data maps to between 0 and 1 or between negative 1 and 1
    public boolean isNormalized() {
        return this.normalized;
    }

    // Returns true when integer data stays integral through glVertexAttribIPointer
    public boolean isIntType() {
        return this.intType;
    }
}
