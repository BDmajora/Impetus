package com.bdmajora.impetus.engine.impl.gl.tessellation;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;

import java.util.Objects;

// Buffer with the target it binds to and the attributes it feeds.
public record TessellationBinding(GlBufferTarget target,
                                  GlBuffer buffer,
                                  GlVertexAttributeBinding[] attributeBindings) {
    // Shared attribute array of element buffer bindings.
    private static final GlVertexAttributeBinding[] NO_ATTRIBUTES = new GlVertexAttributeBinding[0];

    // Rejects a null target or attribute array or attribute.
    public TessellationBinding {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(attributeBindings, "attributeBindings");

        for (GlVertexAttributeBinding attribute : attributeBindings) {
            Objects.requireNonNull(attribute, "attribute");
        }
    }

    // Creates a vertex buffer binding for explicit attribute bindings.
    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, attributes);
    }

    // Creates a vertex buffer binding for a format from index zero without a divisor.
    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexFormat format) {
        return forVertexBuffer(buffer, format, 0, 0);
    }

    // Creates a vertex buffer binding for a format with numbered attributes and a divisor.
    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexFormat format, int firstAttributeIndex, int divisor) {
        Objects.requireNonNull(format, "format");

        var attributes = format.getAttributes();
        var bindings = new GlVertexAttributeBinding[attributes.size()];
        int index = 0;

        for (var attribute : attributes) {
            bindings[index] = new GlVertexAttributeBinding(firstAttributeIndex + index, attribute, divisor);
            index++;
        }

        return forVertexBuffer(buffer, bindings);
    }

    // Creates an element buffer binding without attributes.
    public static TessellationBinding forElementBuffer(GlBuffer buffer) {
        return new TessellationBinding(GlBufferTarget.ELEMENT_BUFFER, buffer, NO_ATTRIBUTES);
    }
}
