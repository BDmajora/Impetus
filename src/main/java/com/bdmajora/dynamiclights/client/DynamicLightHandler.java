package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.ExplosiveLightingMode;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;

import java.util.function.Function;

// Source of the light level of one kind of object.
public interface DynamicLightHandler<T> {
    // Returns the light level of the source.
    int getLuminance(T lightSource);

    // Returns whether the source goes out in a fluid.
    default boolean isWaterSensitive(T lightSource) {
        return false;
    }

    // Returns a handler built from a light function and a water function.
    static <T extends EntityLivingBase> DynamicLightHandler<T> makeHandler(
            Function<T, Integer> luminance, Function<T, Boolean> waterSensitive) {
        return new DynamicLightHandler<T>() {
            // Returns the light level of the source.
            @Override
            public int getLuminance(T lightSource) {
                return luminance.apply(lightSource);
            }

            // Returns whether the source goes out in a fluid.
            @Override
            public boolean isWaterSensitive(T lightSource) {
                return waterSensitive.apply(lightSource);
            }
        };
    }

    // Returns a handler that also counts held and worn items.
    static <T extends EntityLivingBase> DynamicLightHandler<T> makeLivingEntityHandler(
            DynamicLightHandler<T> handler) {
        return entity -> Math.max(
                DynamicLightsEngine.getLivingEntityLuminanceFromItems(entity),
                handler.getLuminance(entity));
    }

    // Returns a handler that lights a creeper while it flashes.
    static <T extends EntityCreeper> DynamicLightHandler<T> makeCreeperEntityHandler(
            DynamicLightHandler<T> handler) {
        return new DynamicLightHandler<T>() {
            // Returns the light of the flash combined with the wrapped handler.
            @Override
            public int getLuminance(T entity) {
                int luminance = 0;
                float flash = entity.getCreeperFlashIntensity(0.0F);

                if (flash > 0.001F) {
                    ExplosiveLightingMode mode = DynamicLights.options().creeperLighting;
                    if (mode == ExplosiveLightingMode.SIMPLE) {
                        luminance = 10;
                    } else if (mode == ExplosiveLightingMode.FANCY) {
                        luminance = (int) (flash * 10.0F);
                    }
                }

                if (handler != null) {
                    luminance = Math.max(luminance, handler.getLuminance(entity));
                }

                return luminance;
            }

            // Returns true.
            @Override
            public boolean isWaterSensitive(T lightSource) {
                return true;
            }
        };
    }
}
