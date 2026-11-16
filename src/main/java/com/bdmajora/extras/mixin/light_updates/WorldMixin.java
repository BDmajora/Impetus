package com.bdmajora.extras.mixin.light_updates;

import com.bdmajora.extras.Extras;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes World.
@Mixin(World.class)
public class WorldMixin {
    // Runs at the start of checkLightFor and can cancel it.
    @Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true)
    private void impetus$checkLightFor(EnumSkyBlock lightType, BlockPos pos,
                                       CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (self.isRemote && !Extras.options().render.lightUpdates) {
            cir.setReturnValue(false);
        }
    }

    // Runs at the start of checkLight and can cancel it.
    @Inject(method = "checkLight", at = @At("HEAD"), cancellable = true)
    private void impetus$checkLight(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (self.isRemote && !Extras.options().render.lightUpdates) {
            cir.setReturnValue(false);
        }
    }
}
