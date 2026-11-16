package com.bdmajora.extras.mixin.render.entity;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.ItemFrameLodState;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.MapItemRenderer;
import net.minecraft.client.renderer.entity.RenderItemFrame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.world.storage.MapData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes RenderItemFrame.
@Mixin(RenderItemFrame.class)
public class RenderItemFrameMixin {
    // Runs at the start of doRender and can cancel it.
    @Inject(
            method = "doRender(Lnet/minecraft/entity/item/EntityItemFrame;DDDFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$doRender(EntityItemFrame entity, double x, double y, double z,
                                  // Yaw and partial ticks.
                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!Extras.options().render.itemFrames || RenderBudgetController.shouldCullItemFrame(entity)) {
            ci.cancel();
        }
    }

    // Runs at the start of renderName and can cancel it.
    @Inject(
            method = "renderName(Lnet/minecraft/entity/item/EntityItemFrame;DDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$renderName(EntityItemFrame entity, double x, double y, double z, CallbackInfo ci) {
        if (!Extras.options().render.itemFrameNameTag) {
            ci.cancel();
        }
    }

    // Runs at the start of renderItem.
    @Inject(method = "renderItem(Lnet/minecraft/entity/item/EntityItemFrame;)V", at = @At("HEAD"))
    private void impetus$beginLod(EntityItemFrame entity, CallbackInfo ci) {
        ExtrasConfig.RenderSettings settings = Extras.options().render;
        int distance = settings.itemFrameLodDistance;

        if (distance <= 0) {
            ItemFrameLodState.active = false;
            return;
        }

        Entity view = Minecraft.getMinecraft().getRenderManager().renderViewEntity;
        if (view == null) {
            ItemFrameLodState.active = false;
            return;
        }

        ItemFrameLodState.active = entity.getDistanceSq(view) > (double) distance * (double) distance;
    }

    // Runs before every return of renderItem.
    @Inject(method = "renderItem(Lnet/minecraft/entity/item/EntityItemFrame;)V", at = @At("RETURN"))
    private void impetus$endLod(EntityItemFrame entity, CallbackInfo ci) {
        ItemFrameLodState.active = false;
    }

    // Replaces the call of MapItemRenderer.renderMap in renderItem.
    @Redirect(
            method = "renderItem(Lnet/minecraft/entity/item/EntityItemFrame;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/MapItemRenderer;renderMap(Lnet/minecraft/world/storage/MapData;Z)V")
    )
    private void impetus$cullFramedMap(MapItemRenderer renderer, MapData data, boolean noOverlay) {
        if (!ItemFrameLodState.active) {
            renderer.renderMap(data, noOverlay);
        }
    }
}
