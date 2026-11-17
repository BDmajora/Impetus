package com.bdmajora.extras.mixin.animation;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
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
    // Category of sprites no name rule matches.
    @Unique
    private static final Predicate<ExtrasConfig.AnimationSettings> IMPETUS$BLOCK_ANIMATIONS = settings -> settings.blockAnimations;

    // Sprite name fragments of each animation category.
    @Unique
    private Map<String, Predicate<ExtrasConfig.AnimationSettings>> impetus$categories;

    // Category matched for each sprite name.
    @Unique
    private Map<String, Predicate<ExtrasConfig.AnimationSettings>> impetus$categoryByName;

    // Lets a sprite advance its animation only when its category is on.
    @WrapWithCondition(
            method = "updateAnimations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;updateAnimation()V")
    )
    private boolean impetus$controlAnimation(TextureAtlasSprite sprite) {
        return impetus$shouldAnimate(sprite);
    }

    // Returns whether a sprite's animation category is switched on.
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

        return impetus$categoryOf(iconName).test(settings);
    }

    // Returns the category of a sprite name and matches the fragments on its first sighting.
    @Unique
    private Predicate<ExtrasConfig.AnimationSettings> impetus$categoryOf(String iconName) {
        Map<String, Predicate<ExtrasConfig.AnimationSettings>> byName = this.impetus$categoryByName;
        if (byName == null) {
            byName = new HashMap<>();
            this.impetus$categoryByName = byName;
        }
        Predicate<ExtrasConfig.AnimationSettings> category = byName.get(iconName);
        if (category == null) {
            category = impetus$matchCategory(iconName);
            byName.put(iconName, category);
        }
        return category;
    }

    // Returns the category of the first fragment the sprite name contains.
    @Unique
    private Predicate<ExtrasConfig.AnimationSettings> impetus$matchCategory(String iconName) {
        if (impetus$categories == null) {
            impetus$categories = impetus$buildCategories();
        }

        for (Map.Entry<String, Predicate<ExtrasConfig.AnimationSettings>> entry : impetus$categories.entrySet()) {
            if (iconName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return IMPETUS$BLOCK_ANIMATIONS;
    }

    // Builds the sprite name prefixes of each animation category.
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
