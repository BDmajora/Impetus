package com.bdmajora.impetus.engine.impl.gl.tessellation;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.util.Objects;

// Base of tessellations that bind vertex buffers and set up their attributes.
public abstract class GlAbstractTessellation implements GlTessellation {
    // Private copy of the buffer bindings.
    protected final TessellationBinding[] bindings;

    // Stores a copy of the bindings and rejects a null array or null element.
    protected GlAbstractTessellation(TessellationBinding[] bindings) {
        Objects.requireNonNull(bindings, "bindings");
        this.bindings = bindings.clone();

        for (TessellationBinding binding : this.bindings) {
            Objects.requireNonNull(binding, "binding");
        }
    }

    // Binds every buffer and sets the pointer and divisor of each attribute.
    protected void bindAttributes(CommandList commandList) {
        for (TessellationBinding binding : this.bindings) {
            commandList.bindBuffer(binding.target(), binding.buffer());

            for (GlVertexAttributeBinding attrib : binding.attributeBindings()) {
                int index = attrib.getIndex();
                int type = attrib.getFormat().typeId();

                if (attrib.isIntType()) {
                    LWJGL.glVertexAttribIPointer(index, attrib.getCount(), type, attrib.getStride(), attrib.getPointer());
                } else {
                    LWJGL.glVertexAttribPointer(index, attrib.getCount(), type, attrib.isNormalized(), attrib.getStride(), attrib.getPointer());
                }
                LWJGL.glEnableVertexAttribArray(index);

                int divisor = attrib.getDivisor();
                if (divisor != 0) {
                    LWJGL.glVertexAttribDivisor(index, divisor);
                }
            }
        }
    }
}
