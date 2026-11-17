package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes ModelRenderer.
@Mixin(ModelRenderer.class)
public abstract class ModelRendererTessellationMixin {
    // Draws the boxes as patches while a tessellation program is bound.
    @WrapWithCondition(method = {"render", "renderWithRotation"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"))
    private boolean impetus$tessellatedList(int list, @Local(argsOnly = true) float scale) {
        if (!ImmediateTessellation.isActive()) {
            return true;
        }
        ImmediateTessellation.drawModelBoxes((ModelRenderer) (Object) this, scale);
        return false;
    }
}
