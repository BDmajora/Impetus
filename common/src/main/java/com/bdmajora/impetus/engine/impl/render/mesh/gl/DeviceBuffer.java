package com.bdmajora.impetus.engine.impl.render.mesh.gl;

// Buffer that shaders reach by a GPU address and that streams can copy into.
public interface DeviceBuffer {
    // Returns the GL name.
    int getId();

    // Returns the size in bytes.
    long getSize();

    // Returns the GPU address while the buffer is resident.
    long getDeviceAddress();

    // Deletes the buffer.
    void delete();
}
