package com.creativemd.littletiles.client.render.cache;

import java.nio.ByteBuffer;

// Test stand-in for LittleTiles' view of one baked layer buffer
public interface IRenderDataCache {
    ByteBuffer byteBuffer();

    int length();

    int vertexCount();
}
