package com.bdmajora.impetus.engine.impl.gl.attribute;

// Vertex attribute layout bound to a shader attribute location with an instancing divisor
public class GlVertexAttributeBinding extends GlVertexAttribute {
    // Shader attribute location
    private final int index;
    // Number of instances that share one attribute value or zero for one value per vertex
    private final int divisor;

    // Creates a binding that advances the attribute once per vertex
    public GlVertexAttributeBinding(int index, GlVertexAttribute attribute) {
        this(index, attribute, 0);
    }

    // Creates a binding from the layout that the attribute reports and rejects a null attribute or a negative index or divisor
    public GlVertexAttributeBinding(int index, GlVertexAttribute attribute, int divisor) {
        // Rejects the arguments before any accessor of the attribute is read
        if (attribute == null | (index | divisor) < 0) {
            throw argumentError(attribute, index);
        }

        super(attribute.getFormat(), attribute.getSize(), attribute.getCount(), attribute.getName(), attribute.isNormalized(), attribute.getPointer(), attribute.getStride(), attribute.isIntType());

        this.index = index;
        this.divisor = divisor;
    }

    // Creates the exception for the first argument that the binding check refuses
    private static RuntimeException argumentError(GlVertexAttribute attribute, int index) {
        if (attribute == null) {
            return new NullPointerException("Attribute must not be null");
        }

        if (index < 0) {
            return new IllegalArgumentException("Index must not be negative");
        }

        return new IllegalArgumentException("Divisor must not be negative");
    }

    // Returns the shader attribute location
    public int getIndex() {
        return this.index;
    }

    // Returns the number of instances that share one attribute value or zero for one value per vertex
    public int getDivisor() {
        return this.divisor;
    }
}
