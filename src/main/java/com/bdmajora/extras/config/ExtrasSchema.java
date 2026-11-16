package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.common.config.Configuration;

// Schema of every Extras setting group.
public final class ExtrasSchema {
    // Schema of the groups settings.
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

    // Reads the value.
    public static void read(Configuration config, ExtrasConfig options) {
        for (SettingGroup<?> group : GROUPS) {
            group.load(config, options);
        }
        ParticleClassLists.load(config);
        if (config.hasChanged()) {
            config.save();
        }
    }

    // Writes the value.
    public static void write(Configuration config, ExtrasConfig options) {
        for (SettingGroup<?> group : GROUPS) {
            group.save(config, options);
        }
        ParticleClassLists.save(config);
        config.save();
    }
}
