package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;

@Mixin(RenderItem.class)
public class RenderItemItemIdMixin {
    // Primitive stack: one push and pop per rendered object, so no boxing
    @Unique
    private final IntArrayList impetus$itemIdStack = new IntArrayList();

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"))
    private void impetus$beginItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        this.impetus$itemIdStack.push(state.getCurrentRenderedItem());
        state.setCurrentRenderedItem(WorldRenderingSettings.getItemId(stack));
        // Sends the id change to the bound program; setting it only on CapturedRenderingState leaves it in Java, since the uniform uploads at phase bind and one phase covers every object
        Umbra.refreshDynamicUniforms();

        // Item models use client arrays, which alias generic attribute slots 8..15; a generic array left enabled on slot 9 (gl_MultiTexCoord1, the lightmap) flattens it to one constant and items render fullbright, which is why custom item-frame scenery lights up while vanilla blocks beside it stay correct (see resetVanillaVertexArrayState)
        UmbraRenderingPipeline.resetVanillaVertexArrayState();

        // No phase is bound here, like OptiFine: an item model is always drawn inside an enclosing renderer whose program is already selected. Impetus does not yet select per object (block entities draw via gbuffers_entities); a per-object setPhase previously fired inside the shadow pass, now guarded, so it is safe to retry as its own change
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("RETURN"))
    private void impetus$endItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(
                this.impetus$itemIdStack.isEmpty() ? -1 : this.impetus$itemIdStack.popInt());
        // The restore matters as much as the set: an item model nested in an item frame or armor stand would otherwise leave its id live over the rest of the batch, making every later entity emissive
        Umbra.refreshDynamicUniforms();
    }

}
