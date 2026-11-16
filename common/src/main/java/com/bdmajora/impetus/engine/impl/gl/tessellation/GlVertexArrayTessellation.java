package com.bdmajora.impetus.engine.impl.gl.tessellation;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.util.Objects;

// Tessellation that records its attribute layout into one vertex array.
public class GlVertexArrayTessellation extends GlAbstractTessellation {
    // Vertex array holding the recorded layout.
    private final GlVertexArray array;

    // Creates a tessellation for the array and rejects a null array.
    public GlVertexArrayTessellation(GlVertexArray array, TessellationBinding[] bindings) {
        super(bindings);

        this.array = Objects.requireNonNull(array, "array");
    }

    // Records the attribute bindings into the vertex array and unbinds it on every path.
    public void init(CommandList commandList) {
        this.bind(commandList);
        try {
            this.bindAttributes(commandList);
        } finally {
            this.unbind(commandList);
        }
    }

    // Deletes the vertex array.
    @Override
    public void delete(CommandList commandList) {
        commandList.deleteVertexArray(this.array);
    }

    // Binds the vertex array.
    @Override
    public void bind(CommandList commandList) {
        commandList.bindVertexArray(this.array);
    }

    // Unbinds the vertex array.
    @Override
    public void unbind(CommandList commandList) {
        commandList.unbindVertexArray();
    }
}
