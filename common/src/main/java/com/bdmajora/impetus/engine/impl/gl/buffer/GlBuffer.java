package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL20;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;

public abstract class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        this.setHandle(LWJGL.glGenBuffers());
    }

    // The live mapping, or null
    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    // Set by the device on map and unmap
    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }

    // glDeleteBuffers
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteBuffers(this.handle());
    }
}
