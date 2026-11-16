package com.bdmajora.extras.mixin.animation;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

// Mixin that changes TextureMap.
@Mixin(TextureMap.class)
public abstract class TextureMapMixin {
    @Unique
    private Map<String, Predicate<ExtrasConfig.AnimationSettings>> impetus$categories;

    // Wraps the call of TextureAtlasSprite.updateAnimation in updateAnimations.
    @WrapOperation(
            method = "updateAnimations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;updateAnimation()V")
    )
    private void impetus$controlAnimation(TextureAtlasSprite sprite, Operation<Void> original) {
        if (impetus$shouldAnimate(sprite)) {
            original.call(sprite);
        }
    }

    // Helper method should animate added to the target.
    @Unique
    private boolean impetus$shouldAnimate(TextureAtlasSprite sprite) {
        ExtrasConfig.AnimationSettings settings = Extras.options().animation;

        if (!settings.all) {
            return false;
        }

        if (sprite == null) {
            return true;
        }

        String iconName = sprite.getIconName();
        if (iconName == null) {
            return true;
        }

        if (impetus$categories == null) {
            impetus$categories = impetus$buildCategories();
        }

        for (Map.Entry<String, Predicate<ExtrasConfig.AnimationSettings>> entry : impetus$categories.entrySet()) {
            if (iconName.contains(entry.getKey())) {
                return entry.getValue().test(settings);
            }
        }

        return settings.blockAnimations;
    }

    // Helper method build categories added to the target.
    @Unique
    private static Map<String, Predicate<ExtrasConfig.AnimationSettings>> impetus$buildCategories() {
        Map<String, Predicate<ExtrasConfig.AnimationSettings>> map = new java.util.LinkedHashMap<>();

        map.put("water_still", settings -> settings.water);
        map.put("water_flow", settings -> settings.water);
        map.put("water_overlay", settings -> settings.water);

        map.put("lava_still", settings -> settings.lava);
        map.put("lava_flow", settings -> settings.lava);

        map.put("fire_layer_0", settings -> settings.fire);
        map.put("fire_layer_1", settings -> settings.fire);

        map.put("portal", settings -> settings.portal);

        map.put("redstone", settings -> settings.redstone);
        map.put("explosion", settings -> settings.explosion);
        map.put("flame", settings -> settings.flame);
        map.put("smoke", settings -> settings.smoke);

        map.put("sculk_sensor", settings -> settings.sculkSensor);

        return map;
    }
}
