package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes RenderLivingBase.
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseMixin<T extends EntityLivingBase> extends Render<T> {
    // Creates a render living base mixin from its render manager.
    private RenderLivingBaseMixin(RenderManager renderManager) {
        super(renderManager);
    }

    // Runs at the start of doRender and can cancel it.
    @Inject(
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$doRender(T entity, double x, double y, double z,
                                  // Yaw and partial ticks.
                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (RenderBudgetController.shouldCullLivingEntity(entity)) {
            ci.cancel();
            return;
        }

        if (entity instanceof EntityArmorStand && !Extras.options().render.armorStands) {
            ci.cancel();

            if (this.canRenderName(entity)) {
                this.renderLivingLabel(entity, entity.getDisplayName().getFormattedText(), x, y, z, 64);
            }
        }
    }

    // Runs at the start of canRenderName and can cancel it.
    @Inject(
            method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$canRenderName(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractClientPlayer && !Extras.options().render.playerNameTag) {
            cir.setReturnValue(false);
        }
    }
}
