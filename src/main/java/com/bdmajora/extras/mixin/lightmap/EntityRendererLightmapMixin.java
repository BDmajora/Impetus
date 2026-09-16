package com.bdmajora.extras.mixin.lightmap;

import com.bdmajora.extras.Extras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

// The lightmap is 256 pixels of maths plus a texture upload, redone every tick because the torch flicker random-walks every tick (BadOptimizations' lightmap caching); everything else that feeds it changes rarely, so the upload is skipped while those inputs hold, and the flicker, a few percent of block-light brightness, is what is given up. Dimensions whose provider overrides Forge's lightmap hook are left alone since they may animate it themselves
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightmapMixin {
    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    private boolean lightmapUpdateNeeded;

    @Shadow
    private float bossColorModifier;

    @Shadow
    private float bossColorModifierPrev;

    private static final ConcurrentHashMap<Class<?>, Boolean> CUSTOM_LIGHTMAP_PROVIDERS = new ConcurrentHashMap<>();

    private long impetus$lightmapKey = Long.MIN_VALUE;
    private World impetus$lightmapWorld;

    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true)
    private void impetus$skipUnchanged(float partialTicks, CallbackInfo ci) {
        if (!this.lightmapUpdateNeeded || !Extras.options().clientTick.lightmapCaching) {
            return;
        }
        World world = this.mc.world;
        EntityPlayerSP player = this.mc.player;
        if (world == null || player == null) {
            return;
        }
        // Boss bars fade the lightmap per frame and modded providers may animate it; both fall back to vanilla
        if (this.bossColorModifier > 0.0F || this.bossColorModifierPrev > 0.0F || impetus$hasCustomLightmap(world.provider)) {
            this.impetus$lightmapKey = Long.MIN_VALUE;
            return;
        }
        long key = impetus$key(world, player);
        if (world == this.impetus$lightmapWorld && key == this.impetus$lightmapKey) {
            this.lightmapUpdateNeeded = false;
            ci.cancel();
            return;
        }
        this.impetus$lightmapWorld = world;
        this.impetus$lightmapKey = key;
    }

    // Everything updateLightmap reads apart from the flicker, packed into one long: sun brightness in 1/256 steps (a dusk crosses ~40 of them, one every few ticks), gamma in 1/256, night vision with its remaining ticks while it is fading, lightning, dimension
    private static long impetus$key(World world, EntityPlayerSP player) {
        long sun = (long) (world.getSunBrightness(1.0F) * 255.0F) & 0xFF;
        long gamma = (long) (Minecraft.getMinecraft().gameSettings.gammaSetting * 255.0F) & 0x1FF;
        long lightning = world.getLastLightningBolt() > 0 ? 1L : 0L;
        long dimension = world.provider.getDimensionType().getId() == 1 ? 1L : 0L;
        long nightVision = 0L;
        PotionEffect effect = player.getActivePotionEffect(MobEffects.NIGHT_VISION);
        if (effect != null) {
            int duration = effect.getDuration();
            // Full brightness above 200 ticks, then a per-tick fade that must keep updating
            nightVision = duration > 200 ? 0x3FFL : (0x200L | (duration & 0xFF));
        }
        return sun | gamma << 8 | lightning << 17 | dimension << 18 | nightVision << 19;
    }

    private static boolean impetus$hasCustomLightmap(WorldProvider provider) {
        return CUSTOM_LIGHTMAP_PROVIDERS.computeIfAbsent(provider.getClass(), type -> {
            try {
                return type.getMethod("getLightmapColors", float.class, float.class, float.class, float.class, float[].class).getDeclaringClass() != WorldProvider.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        });
    }
}
