package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.GL30;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Hands DH one of Umbra's framebuffers as its render target (Iris's DhFrameBufferWrapper): DH binds it for the LOD pass, and the attachment calls DH makes while (re)creating its textures are ignored since the compat attaches DH's depth itself and the colour targets are the pipeline's
public final class DhFramebufferWrapper implements IDhApiFramebuffer {
    private final UmbraFramebuffer framebuffer;

    public DhFramebufferWrapper(UmbraFramebuffer framebuffer) {
        this.framebuffer = framebuffer;
    }

    @Override
    public boolean overrideThisFrame() {
        return true;
    }

    @Override
    public void bind() {
        this.framebuffer.bind();
    }

    @Override
    public void addDepthAttachment(int textureId, boolean isCombinedStencil) {
        // DhCompatInternal.reconnectDHTextures attaches DH's depth where it belongs
    }

    @Override
    public int getId() {
        return this.framebuffer.getGlId();
    }

    @Override
    public int getStatus() {
        bind();
        return LWJGL.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    }

    @Override
    public void addColorAttachment(int textureIndex, int textureId) {
        // The colour targets are the pipeline's gbuffer textures, never DH's
    }

    @Override
    public void destroy() {
        // Owned by DhCompatInternal (or the shadow renderer), freed with the pipeline
    }
}
