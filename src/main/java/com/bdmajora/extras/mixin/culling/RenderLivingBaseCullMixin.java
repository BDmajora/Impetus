package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.OcclusionCulling;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that draws only the name of a hidden living entity.
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseCullMixin<T extends EntityLivingBase> extends Render<T> {
    // Creates the mixin from its render manager.
    private RenderLivingBaseCullMixin(RenderManager renderManager) {
        super(renderManager);
    }

    // Skips the model of a hidden entity and keeps its name.
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void impetus$drawOnlyName(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (OcclusionCulling.isLabelOnly(entity)) {
            ci.cancel();
            if (!this.renderOutlines) {
                this.renderName(entity, x, y, z);
            }
        }
    }
}
