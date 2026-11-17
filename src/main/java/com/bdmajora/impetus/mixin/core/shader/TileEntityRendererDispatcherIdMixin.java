package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.pipeline.rendering.GlState;
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
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;

// Mixin that changes TileEntityRendererDispatcher.
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherIdMixin {
    // Block entity ids of nested draws.
    @Unique
    private final IntArrayList impetus$blockEntityIdStack = new IntArrayList();

    // Runs at the start of render.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("HEAD"))
    private void impetus$beginBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        this.impetus$blockEntityIdStack.push(state.getCurrentRenderedBlockEntity());
        state.setCurrentRenderedBlockEntity(WorldRenderingSettings.getBlockEntityId(tileEntity));
        Umbra.refreshDynamicUniforms();

        GlState.resetVanillaVertexArrayState();
    }

    // Runs before every return of render.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("RETURN"))
    private void impetus$endBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedBlockEntity(
                this.impetus$blockEntityIdStack.isEmpty() ? -1 : this.impetus$blockEntityIdStack.popInt());
        Umbra.refreshDynamicUniforms();
    }

}
