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

// Serves the light sampled at tick time (AsyncParticles' light cache): vanilla walks chunk light for every particle every frame, this does it once per tick on the ticking thread; dynamic lights are folded back in here since their hook sits on the call this bypasses
@Mixin(Particle.class)
public abstract class ParticleLightCacheMixin implements LightCachedParticle {
    @Shadow
    protected World world;

    @Shadow
    protected double posX;

    @Shadow
    protected double posY;

    @Shadow
    protected double posZ;

    @Unique
    private int impetus$cachedLight;

    // The tick the sample was taken on; anything older is ignored so a particle ticked on the vanilla path never renders with stale light
    @Unique
    private int impetus$cachedLightTick = -1;

    @Override
    public void impetus$sampleLight() {
        BlockPos pos = new BlockPos(this.posX, this.posY, this.posZ);
        this.impetus$cachedLight = this.world.isBlockLoaded(pos) ? this.world.getCombinedLight(pos, 0) : 0;
        this.impetus$cachedLightTick = ParticleTicker.tickCounter;
    }

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
