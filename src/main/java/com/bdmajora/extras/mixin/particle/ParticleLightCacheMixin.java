package com.bdmajora.extras.mixin.particle;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.extras.client.particle.LightCachedParticle;
import com.bdmajora.extras.client.particle.ParticleTicker;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Particle.
@Mixin(Particle.class)
public abstract class ParticleLightCacheMixin implements LightCachedParticle {
    // Reference to the field world of the target.
    @Shadow
    protected World world;

    // Reference to the field pos x of the target.
    @Shadow
    protected double posX;

    // Reference to the field pos y of the target.
    @Shadow
    protected double posY;

    // Reference to the field pos z of the target.
    @Shadow
    protected double posZ;

    // Combined light sampled once this tick.
    @Unique
    private int impetus$cachedLight;

    // Tick the light was sampled on or -1 before the first sample.
    @Unique
    private int impetus$cachedLightTick = -1;

    // Samples the combined light at the particle once per tick.
    @Override
    public void impetus$sampleLight() {
        BlockPos pos = new BlockPos(this.posX, this.posY, this.posZ);
        this.impetus$cachedLight = this.world.isBlockLoaded(pos) ? this.world.getCombinedLight(pos, 0) : 0;
        this.impetus$cachedLightTick = ParticleTicker.tickCounter;
    }

    // Runs at the start of getBrightnessForRender and can cancel it.
    @Inject(method = "getBrightnessForRender", at = @At("HEAD"), cancellable = true)
    private void impetus$useCachedLight(float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (!ParticleTicker.lightCache || this.impetus$cachedLightTick != ParticleTicker.tickCounter) {
            return;
        }
        int light = this.impetus$cachedLight;
        if (DynamicLights.options().mode.isEnabled()) {
            int dynamic = (int) DynamicLights.engine().getDynamicLightLevel(
                    MathHelper.floor(this.posX), MathHelper.floor(this.posY), MathHelper.floor(this.posZ));
            int block = (light >> 4) & 15;
            if (block < dynamic) {
                light = (light & ~(15 << 4)) | (Math.min(15, dynamic) << 4);
            }
        }
        cir.setReturnValue(light);
    }
}
