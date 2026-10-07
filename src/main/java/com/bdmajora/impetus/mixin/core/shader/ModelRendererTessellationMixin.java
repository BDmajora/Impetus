package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla's model part paths replay display lists of GL_QUADS, which a tessellated pack program rejects; while one is bound the boxes draw immediately instead (Extras' matrix path calls the same helper itself)
@Mixin(ModelRenderer.class)
public abstract class ModelRendererTessellationMixin {
    @WrapOperation(method = {"render", "renderWithRotation"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"))
    private void impetus$tessellatedList(int list, Operation<Void> original, @Local(argsOnly = true) float scale) {
        if (ImmediateTessellation.isActive()) {
            ImmediateTessellation.drawModelBoxes((ModelRenderer) (Object) this, scale);
        } else {
            original.call(list);
        }
    }
}
