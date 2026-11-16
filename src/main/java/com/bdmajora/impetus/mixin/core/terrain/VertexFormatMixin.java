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

// Mixin that changes VertexFormat.
@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin implements VertexFormatExtension {
    // Reference to the method get integer size of the target.
    @Shadow
    public abstract int getIntegerSize();

    // Reference to the method get color offset of the target.
    @Shadow
    public abstract int getColorOffset();

    // Reference to the method has uv offset of the target.
    @Shadow
    public abstract boolean hasUvOffset(int id);

    // Reference to the method get uv offset by id of the target.
    @Shadow
    public abstract int getUvOffsetById(int id);

    // Reference to the method get normal offset of the target.
    @Shadow
    public abstract int getNormalOffset();

    // Field stride added to the target.
    @Unique
    private int impetus$stride = -1;
    // Field color added to the target.
    @Unique
    private int impetus$color;
    // Field uv added to the target.
    @Unique
    private int impetus$uv;
    // Field light added to the target.
    @Unique
    private int impetus$light;
    // Field normal added to the target.
    @Unique
    private int impetus$normal;

    // Handles int stride.
    @Override
    public int impetus$intStride() {
        int stride = this.impetus$stride;
        return stride >= 0 ? stride : this.impetus$derive();
    }

    // Handles color index.
    @Override
    public int impetus$colorIndex() {
        this.impetus$intStride();
        return this.impetus$color;
    }

    // Handles uv index.
    @Override
    public int impetus$uvIndex() {
        this.impetus$intStride();
        return this.impetus$uv;
    }

    // Handles light index.
    @Override
    public int impetus$lightIndex() {
        this.impetus$intStride();
        return this.impetus$light;
    }

    // Handles normal index.
    @Override
    public int impetus$normalIndex() {
        this.impetus$intStride();
        return this.impetus$normal;
    }

    // Helper method derive added to the target.
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

    // Runs before every return of addElement.
    @Inject(method = "addElement", at = @At("RETURN"))
    private void impetus$onAddElement(VertexFormatElement element, CallbackInfoReturnable<VertexFormat> cir) {
        this.impetus$stride = -1;
    }

    // Runs before every return of clear.
    @Inject(method = "clear", at = @At("RETURN"))
    private void impetus$onClear(CallbackInfo ci) {
        this.impetus$stride = -1;
    }
}
