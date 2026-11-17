package com.bdmajora.impetus.mixin.features.render.tileentity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes TileEntityRendererDispatcher.
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRenderDispatcherMixin {
    // Treats a moving piston as valid.
    @ModifyExpressionValue(method = "getRenderer(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;isInvalid()Z"))
    private boolean allowSomeInvalidTESRs(boolean invalid, @Local(argsOnly = true) TileEntity te) {
        return invalid && !(te instanceof TileEntityPiston);
    }
}
