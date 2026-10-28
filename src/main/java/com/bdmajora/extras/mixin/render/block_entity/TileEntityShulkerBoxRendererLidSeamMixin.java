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

// Vanilla's shulker box shimmer: over the lid's lower rows base and lid share their side planes, the lid see-through where the base's teeth are solid, and the two differently sized quads disagree at the edges each frame the camera moves; a shut box draws both parts cut at each other's edges so those rows are one identical quad in each (this covers shulker box items too), a moving lid keeps vanilla's parts
@Mixin(TileEntityShulkerBoxRenderer.class)
public abstract class TileEntityShulkerBoxRendererLidSeamMixin {
    @Shadow
    @Final
    private ModelShulker model;

    // Built from this renderer's own model the first time a shut box is drawn
    @Unique
    private ModelRenderer impetus$shutBase;

    @Unique
    private ModelRenderer impetus$shutLid;

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
