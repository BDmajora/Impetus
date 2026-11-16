package com.bdmajora.impetus.umbra.compat.dh;

import org.lwjgl.opengl.GL30;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Distant Horizons view of a pipeline framebuffer that the pipeline keeps owning.
public final class DhFramebufferWrapper implements IDhApiFramebuffer {
    // Pipeline framebuffer that receives the Distant Horizons draws.
    private final UmbraFramebuffer framebuffer;

    // Wraps a pipeline framebuffer.
    public DhFramebufferWrapper(UmbraFramebuffer framebuffer) {
        this.framebuffer = framebuffer;
    }

    // Returns true so Distant Horizons draws into this framebuffer every frame.
    @Override
    public boolean overrideThisFrame() {
        return true;
    }

    // Binds the pipeline framebuffer.
    @Override
    public void bind() {
        this.framebuffer.bind();
    }

    // Ignores the attachment because the pipeline attaches its own depth texture.
    @Override
    public void addDepthAttachment(int textureId, boolean isCombinedStencil) {
    }

    // Returns the name of the pipeline framebuffer.
    @Override
    public int getId() {
        return this.framebuffer.getGlId();
    }

    // Binds the framebuffer and returns its completeness status.
    @Override
    public int getStatus() {
        bind();
        return LWJGL.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    }

    // Ignores the attachment because the pipeline attaches its own color textures.
    @Override
    public void addColorAttachment(int textureIndex, int textureId) {
    }

    // Leaves the framebuffer alive because the pipeline deletes it.
    @Override
    public void destroy() {
    }
}
