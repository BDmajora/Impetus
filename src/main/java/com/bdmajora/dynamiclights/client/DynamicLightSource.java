package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.World;

// Object that can emit a moving light.
public interface DynamicLightSource {
    // Returns the east position of the light.
    double impetus$getDynamicLightX();

    // Returns the height of the light.
    double impetus$getDynamicLightY();

    // Returns the south position of the light.
    double impetus$getDynamicLightZ();

    // Returns the world of the light.
    World impetus$getDynamicLightWorld();

    // Returns whether the source is tracked by the engine.
    default boolean impetus$isDynamicLightEnabled() {
        return DynamicLights.options().mode.isEnabled()
                && DynamicLights.engine().containsLightSource(this);
    }

    // Starts or stops tracking the source.
    default void impetus$setDynamicLightEnabled(boolean enabled) {
        this.impetus$resetDynamicLight();
        if (enabled) {
            DynamicLights.engine().addLightSource(this);
        } else {
            DynamicLights.engine().removeLightSource(this);
        }
    }

    // Forgets the last light level.
    void impetus$resetDynamicLight();

    // Returns the current light level.
    int impetus$getLuminance();

    // Recomputes the light level.
    void impetus$dynamicLightTick();

    // Recomputes the light level and updates tracking.
    default void impetus$tickDynamicLight(boolean removed) {
        if (removed) {
            impetus$setDynamicLightEnabled(false);
            return;
        }
        impetus$dynamicLightTick();
        DynamicLightsEngine.updateTracking(this);
    }

    // Returns whether enough time passed for an update.
    boolean impetus$shouldUpdateDynamicLight();

    // Applies a changed light and returns whether anything changed.
    boolean impetus$updateDynamicLight(RenderGlobal renderer);

    // Marks the lit chunks for a rebuild.
    void impetus$scheduleTrackedChunksRebuild(RenderGlobal renderer);
}
