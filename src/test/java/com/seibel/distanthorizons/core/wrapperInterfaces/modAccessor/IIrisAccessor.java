package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

// Stands in for Distant Horizons' internal shader mod accessor, which Impetus answers with a Proxy
public interface IIrisAccessor extends IModAccessor {
    boolean isShaderPackInUse();

    boolean isRenderingShadowPass();

    boolean isReverseZDuringShaders();

    int getFramebufferDepthTextureId(Object framebuffer);
}
