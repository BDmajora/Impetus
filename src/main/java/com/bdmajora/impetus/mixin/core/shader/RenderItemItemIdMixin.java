package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
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
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;

// Mixin that changes RenderItem.
@Mixin(RenderItem.class)
public class RenderItemItemIdMixin {
    // Field item id stack added to the target.
    @Unique
    private final IntArrayList impetus$itemIdStack = new IntArrayList();

    // Runs at the start of renderItem.
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"))
    private void impetus$beginItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        this.impetus$itemIdStack.push(state.getCurrentRenderedItem());
        state.setCurrentRenderedItem(WorldRenderingSettings.getItemId(stack));
        Umbra.refreshDynamicUniforms();

        GlState.resetVanillaVertexArrayState();

    }

    // Runs before every return of renderItem.
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("RETURN"))
    private void impetus$endItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(
                this.impetus$itemIdStack.isEmpty() ? -1 : this.impetus$itemIdStack.popInt());
        Umbra.refreshDynamicUniforms();
    }

}
