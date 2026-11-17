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

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightmapMixin {
    // Reference to the field mc of the target.
    @Shadow
    @Final
    private Minecraft mc;

    // Reference to the field lightmap update needed of the target.
    @Shadow
    private boolean lightmapUpdateNeeded;

    // Reference to the field boss color modifier of the target.
    @Shadow
    private float bossColorModifier;

    // Reference to the field boss color modifier prev of the target.
    @Shadow
    private float bossColorModifierPrev;

    private static final ConcurrentHashMap<Class<?>, Boolean> CUSTOM_LIGHTMAP_PROVIDERS = new ConcurrentHashMap<>();

    // Key of the inputs of the last lightmap.
    private long impetus$lightmapKey = Long.MIN_VALUE;
    // World of the last lightmap.
    private World impetus$lightmapWorld;

    // Runs at the start of updateLightmap and can cancel it.
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

    // Packs every input of the lightmap into one key.
    private static long impetus$key(World world, EntityPlayerSP player) {
        long sun = (long) (world.getSunBrightness(1.0F) * 255.0F) & 0xFF;
        long gamma = (long) (Minecraft.getMinecraft().gameSettings.gammaSetting * 255.0F) & 0x1FF;
        long lightning = world.getLastLightningBolt() > 0 ? 1L : 0L;
        long dimension = world.provider.getDimensionType().getId() == 1 ? 1L : 0L;
        long nightVision = 0L;
        PotionEffect effect = player.getActivePotionEffect(MobEffects.NIGHT_VISION);
        if (effect != null) {
            int duration = effect.getDuration();
            nightVision = duration > 200 ? 0x3FFL : (0x200L | (duration & 0xFF));
        }
        return sun | gamma << 8 | lightning << 17 | dimension << 18 | nightVision << 19;
    }

    // Returns whether a dimension supplies its own lightmap colors.
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
