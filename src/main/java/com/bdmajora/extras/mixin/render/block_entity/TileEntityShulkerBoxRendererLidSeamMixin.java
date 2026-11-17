package com.bdmajora.extras.mixin.render.block_entity;

import com.bdmajora.extras.client.ShutShulker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelShulker;
import net.minecraft.client.renderer.tileentity.TileEntityShulkerBoxRenderer;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes TileEntityShulkerBoxRenderer.
@Mixin(TileEntityShulkerBoxRenderer.class)
public abstract class TileEntityShulkerBoxRendererLidSeamMixin {
    // Reference to the field model of the target.
    @Shadow
    @Final
    private ModelShulker model;

    // Base of the closed box drawn as one piece with the lid.
    @Unique
    private ModelRenderer impetus$shutBase;

    // Lid of the closed box drawn as one piece with the base.
    @Unique
    private ModelRenderer impetus$shutLid;

    // Wraps the call of ModelRenderer.render in render.
    @WrapOperation(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", ordinal = 0))
    private void impetus$drawShutBase(ModelRenderer base, float scale, Operation<Void> original,
                                      @Local(argsOnly = true) TileEntityShulkerBox box,
                                      @Local(argsOnly = true, ordinal = 0) float partialTicks) {
        if (box.getProgress(partialTicks) != 0.0F) {
            original.call(base, scale);
            return;
        }
        if (this.impetus$shutBase == null) {
            this.impetus$shutBase = ShutShulker.split(base, this.model.lid);
        }
        this.impetus$shutBase.render(scale);
    }

    // Wraps the call of ModelRenderer.render in render.
    @WrapOperation(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", ordinal = 1))
    private void impetus$drawShutLid(ModelRenderer lid, float scale, Operation<Void> original,
                                     @Local(argsOnly = true) TileEntityShulkerBox box,
                                     @Local(argsOnly = true, ordinal = 0) float partialTicks) {
        if (box.getProgress(partialTicks) != 0.0F) {
            original.call(lid, scale);
            return;
        }
        if (this.impetus$shutLid == null) {
            this.impetus$shutLid = ShutShulker.split(lid, this.model.base);
        }
        this.impetus$shutLid.render(scale);
    }
}
