package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Mixin that changes Particle.
@Mixin(Particle.class)
public abstract class ParticleMixin {
    // Changes an argument of the call of World.getCombinedLight in getBrightnessForRender.
    @ModifyArgs(
            method = "getBrightnessForRender",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"))
    private void impetus$dynamicParticleLight(Args args) {
        if (!DynamicLights.options().mode.isEnabled()) {
            return;
        }

        BlockPos pos = args.get(0);
        int vanilla = args.get(1);

        args.set(1, Math.max(vanilla, (int) DynamicLights.engine().getDynamicLightLevel(pos)));
    }
}
