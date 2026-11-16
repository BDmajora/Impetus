package com.bdmajora.impetus.engine.impl.gl.tessellation;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

// Vertex layout that can be bound for drawing.
public interface GlTessellation {
    // Deletes the GL objects of the tessellation.
    void delete(CommandList commandList);

    // Binds the tessellation for drawing.
    void bind(CommandList commandList);

    // Unbinds the tessellation.
    void unbind(CommandList commandList);
}
