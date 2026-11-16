package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;

// Mixin that changes RenderManager.
@Mixin(RenderManager.class)
public class RenderManagerEntityIdMixin {
    // Field entity id stack added to the target.
    @Unique
    private final IntArrayList impetus$entityIdStack = new IntArrayList();

    // Runs at the start of renderEntityStatic.
    @Inject(method = "renderEntityStatic(Lnet/minecraft/entity/Entity;FZ)V", at = @At("HEAD"))
    private void impetus$beginEntity(Entity entity, float partialTicks, boolean p_188388_3_, CallbackInfo ci) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        this.impetus$entityIdStack.push(state.getCurrentRenderedEntity());
        state.setCurrentRenderedEntity(WorldRenderingSettings.getEntityId(entity));
        Umbra.refreshDynamicUniforms();
    }

    // Runs before every return of renderEntityStatic.
    @Inject(method = "renderEntityStatic(Lnet/minecraft/entity/Entity;FZ)V", at = @At("RETURN"))
    private void impetus$endEntity(Entity entity, float partialTicks, boolean p_188388_3_, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedEntity(
                this.impetus$entityIdStack.isEmpty() ? -1 : this.impetus$entityIdStack.popInt());
        Umbra.refreshDynamicUniforms();
    }

}
