package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

// Stand-in for Distant Horizons' shader mod accessor.
public interface IIrisAccessor extends IModAccessor {
    boolean isShaderPackInUse();

    boolean isRenderingShadowPass();

    boolean isReverseZDuringShaders();

    int getFramebufferDepthTextureId(Object framebuffer);
}
