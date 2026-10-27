package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.common.config.Configuration;

// Every setting group of the Extras config and the particle class lists
public final class ExtrasSchema {
    // Every setting group in the field order of the config
    private static final SettingGroup<?>[] GROUPS = {
            VisualSchema.ANIMATION, VisualSchema.PARTICLE, VisualSchema.DETAIL,
            RenderSchema.RENDER, InterfaceSchema.EXTRA, RenderSchema.RENDER_BUDGET, RenderSchema.GPU_BOOSTER,
            SystemSchema.ASYNC, RenderSchema.BAKED_ENTITIES, SystemSchema.NETWORK, SystemSchema.THREADS,
            RenderSchema.OCCLUSION, InterfaceSchema.HUD, InterfaceSchema.TEXT, SystemSchema.CLIENT_TICK,
            RenderSchema.ENTITY_MODELS, VisualSchema.LEAVES, SystemSchema.LOADING, InterfaceSchema.ZOOM
    };

    // Prevents instantiation
    private ExtrasSchema() {
    }

    // Reads every setting into the config and saves the file when a key was missing
    public static void read(Configuration config, ExtrasConfig options) {
        for (SettingGroup<?> group : GROUPS) {
            group.load(config, options);
        }
        ParticleClassLists.load(config);
        if (config.hasChanged()) {
            config.save();
        }
    }

    // Writes every setting of the config and saves the file
    public static void write(Configuration config, ExtrasConfig options) {
        for (SettingGroup<?> group : GROUPS) {
            group.save(config, options);
        }
        ParticleClassLists.save(config);
        config.save();
    }
}
