package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.common.config.ConfigCategory;
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

    // Keys of removed settings as category and key; Forge keeps unknown keys, so these are dropped by hand.
    static final String[][] RETIRED = {
            {"parallel_ticking", "spawning"}
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
        boolean pruned = dropRetired(config);
        if (pruned || config.hasChanged()) {
            config.save();
        }
    }

    // Removes every retired key the file still holds and returns whether one was found.
    static boolean dropRetired(Configuration config) {
        boolean pruned = false;
        for (String[] retired : RETIRED) {
            if (!config.hasCategory(retired[0])) {
                continue;
            }
            ConfigCategory category = config.getCategory(retired[0]);
            // Forge marks a category changed on every remove, even of a missing key, which would save the file each launch
            if (category.containsKey(retired[1])) {
                category.remove(retired[1]);
                pruned = true;
            }
        }
        return pruned;
    }

    // Writes the value.
    public static void write(Configuration config, ExtrasConfig options) {
        for (SettingGroup<?> group : GROUPS) {
            group.save(config, options);
        }
        ParticleClassLists.save(config);
        dropRetired(config);
        config.save();
    }
}
