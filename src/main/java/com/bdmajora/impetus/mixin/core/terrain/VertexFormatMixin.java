package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.impl.extensions.VertexFormatExtension;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Caches the int-index layout BakedQuadMixin reads per vertex; a format is mutable through addElement/clear, so the cache is dropped whenever either runs
@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin implements VertexFormatExtension {
    @Shadow
    public abstract int getIntegerSize();

    @Shadow
    public abstract int getColorOffset();

    @Shadow
    public abstract boolean hasUvOffset(int id);

    @Shadow
    public abstract int getUvOffsetById(int id);

    @Shadow
    public abstract int getNormalOffset();

    // -1 stride marks the layout as not yet derived
    @Unique
    private int impetus$stride = -1;
    @Unique
    private int impetus$color;
    @Unique
    private int impetus$uv;
    @Unique
    private int impetus$light;
    @Unique
    private int impetus$normal;

    // Ints per vertex
    @Override
    public int impetus$intStride() {
        int stride = this.impetus$stride;
        return stride >= 0 ? stride : this.impetus$derive();
    }

    // Int index of the colour element, or -1
    @Override
    public int impetus$colorIndex() {
        this.impetus$intStride();
        return this.impetus$color;
    }

    // Int index of the first UV set
    @Override
    public int impetus$uvIndex() {
        this.impetus$intStride();
        return this.impetus$uv;
    }

    // Int index of the lightmap UV set, or -1
    @Override
    public int impetus$lightIndex() {
        this.impetus$intStride();
        return this.impetus$light;
    }

    // Int index of the normal element, or -1
    @Override
    public int impetus$normalIndex() {
        this.impetus$intStride();
        return this.impetus$normal;
    }

    // Byte offsets to int indices, once per format (or per mutation)
    @Unique
    private int impetus$derive() {
        int colorOffset = this.getColorOffset();
        int normalOffset = this.getNormalOffset();

        this.impetus$color = colorOffset >= 0 ? colorOffset >> 2 : -1;
        this.impetus$uv = this.hasUvOffset(0) ? this.getUvOffsetById(0) >> 2 : 0;
        this.impetus$light = this.hasUvOffset(1) ? this.getUvOffsetById(1) >> 2 : -1;
        this.impetus$normal = normalOffset >= 0 ? normalOffset >> 2 : -1;

        return this.impetus$stride = this.getIntegerSize();
    }

    // The layout changed
    @Inject(method = "addElement", at = @At("RETURN"))
    private void impetus$onAddElement(VertexFormatElement element, CallbackInfoReturnable<VertexFormat> cir) {
        this.impetus$stride = -1;
    }

    // The layout changed
    @Inject(method = "clear", at = @At("RETURN"))
    private void impetus$onClear(CallbackInfo ci) {
        this.impetus$stride = -1;
    }
}
