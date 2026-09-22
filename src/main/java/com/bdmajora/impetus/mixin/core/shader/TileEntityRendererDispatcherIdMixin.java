package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
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

@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherIdMixin {
    // Primitive stack: one push and pop per rendered object, so no boxing
    @Unique
    private final IntArrayList impetus$blockEntityIdStack = new IntArrayList();

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("HEAD"))
    private void impetus$beginBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        this.impetus$blockEntityIdStack.push(state.getCurrentRenderedBlockEntity());
        state.setCurrentRenderedBlockEntity(WorldRenderingSettings.getBlockEntityId(tileEntity));
        // Sends the id change to the bound program; setting it only on CapturedRenderingState leaves it in Java, since the uniform uploads at phase bind and one phase covers every object
        Umbra.refreshDynamicUniforms();

        // Same hazard as RenderItem, worse: ModelRenderer vertex data has no lightmap element and compiles into a display list that dereferences the bound arrays at COMPILE time, so one poisoned compile stays fullbright all session; resetting before every dispatch keeps the first compile clean
        UmbraRenderingPipeline.resetVanillaVertexArrayState();
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("RETURN"))
    private void impetus$endBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedBlockEntity(
                this.impetus$blockEntityIdStack.isEmpty() ? -1 : this.impetus$blockEntityIdStack.popInt());
        // The restore matters as much as the set: without it the last block entity's id stays live over everything drawn after the batch
        Umbra.refreshDynamicUniforms();
    }

}
