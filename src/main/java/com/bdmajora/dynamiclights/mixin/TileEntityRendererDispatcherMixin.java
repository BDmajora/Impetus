package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Mixin that changes TileEntityRendererDispatcher.
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {
    // Changes an argument of the call of World.getCombinedLight in render.
    @ModifyArgs(
            method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"))
    private void impetus$dynamicBlockEntityLight(Args args) {
        if (!DynamicLights.options().mode.isEnabled()) {
            return;
        }

        BlockPos pos = args.get(0);
        int vanilla = args.get(1);

        args.set(1, Math.max(vanilla, (int) DynamicLights.engine().getDynamicLightLevel(pos)));
    }
}
