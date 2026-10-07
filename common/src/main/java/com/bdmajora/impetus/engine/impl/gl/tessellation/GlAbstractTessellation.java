package com.bdmajora.impetus.engine.impl.gl.tessellation;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

public abstract class GlAbstractTessellation implements GlTessellation {
    protected final TessellationBinding[] bindings;

    protected GlAbstractTessellation(TessellationBinding[] bindings) {
        this.bindings = bindings;
    }

    // Through the LWJGL abstraction
    private static void glVertexAttribIPointer(int index, int size, int type, int stride, long ptr) {
        LWJGL.glVertexAttribIPointer(index, size, type, stride, ptr);
    }

    // Issues a pointer call per attribute of every bound vertex buffer
    protected void bindAttributes(CommandList commandList) {
        for (TessellationBinding binding : this.bindings) {
            commandList.bindBuffer(binding.target(), binding.buffer());

            for (GlVertexAttributeBinding attrib : binding.attributeBindings()) {
                if (attrib.isIntType()) {
                    glVertexAttribIPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat().typeId(),
                            attrib.getStride(), attrib.getPointer());
                } else {
                    LWJGL.glVertexAttribPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat().typeId(), attrib.isNormalized(),
                            attrib.getStride(), attrib.getPointer());
                }
                LWJGL.glEnableVertexAttribArray(attrib.getIndex());

                if (attrib.getDivisor() != 0) {
                    LWJGL.glVertexAttribDivisor(attrib.getIndex(), attrib.getDivisor());
                }
            }
        }
    }
}
