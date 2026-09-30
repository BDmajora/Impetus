package com.bdmajora.impetus.core;

import com.bdmajora.fulgor.Fulgor;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Registers Impetus' mixin configurations and suppresses superseded lighting engines, talking to Mixin directly so the same path works under an installed MixinBooter or the bundled copy
final class ImpetusMixinRegistrar {

    // Legacy Phosphor-lineage mods mapped to their configs; Fulgor replaces them and running both would corrupt the lighting
    private static final Map<String, String> SUPERSEDED_LIGHTING_MODS = supersededLightingMods();

    // Distant Horizons' mixin giving MC's framebuffer a depth texture; DH's connector skips it whenever the Iris API class exists, assuming Actinium supplies that texture, but Impetus ships the class and leaves the framebuffer alone
    private static final String DH_DEPTH_TEXTURE_CONFIG = "DistantHorizons.iris.mixins.json";

    private ImpetusMixinRegistrar() { }

    // Builds the immutable map of conflicting lighting mods to suppress
    private static Map<String, String> supersededLightingMods() {
        Map<String, String> mods = new LinkedHashMap<>();

        // Base Phosphor implementation
        mods.put("phosphor-lighting", "mixins.phosphor.json");

        // Alfheim lighting engine fork
        mods.put("alfheim", "mixins.alfheim.json");

        return Collections.unmodifiableMap(mods);
    }

    // Hijacks first so blacklists land before any config is queued
    static void apply() {
        hijackSupersededLighting();
        for (String config : mixinConfigs()) {
            Mixins.addConfiguration(config);
        }
        restoreDistantHorizonsDepthTexture();
    }

    // Order: Impetus/Umbra reserve early-load slots, Coarctatio must apply before vanilla NBT/ResourceLocation instantiation, Fulgor injects lighting fields into World/Chunk, Equilibrium loads last so Fulgor reads its chunk cache, Extras and Dynamic Lights are order-independent after those
    private static List<String> mixinConfigs() {
        return Arrays.asList("mixins.impetus.json", "mixins.umbra.json", "mixins.coarctatio.json",
                "mixins.fulgor.json", "mixins.equilibrium.json", "mixins.extras.json",
                "mixins.dynamiclights.json");
    }

    // DH's fade pass samples that depth texture through the accessor Impetus registers with DH; injectData runs after every coremod jar joined the classpath, so the resource lookup sees DH's
    private static void restoreDistantHorizonsDepthTexture() {
        if (Launch.classLoader.getResource(DH_DEPTH_TEXTURE_CONFIG) != null) {
            Mixins.addConfiguration(DH_DEPTH_TEXTURE_CONFIG);
        }
    }

    // Blacklists Phosphor and Alfheim configs when their json is on the classpath, since two lighting engines corrupt light
    private static void hijackSupersededLighting() {
        for (Map.Entry<String, String> mod : SUPERSEDED_LIGHTING_MODS.entrySet()) {
            String config = mod.getValue();

            // Presence is inferred from the config being on the classpath rather than a booter's mod index, keeping this independent of whichever booter is running
            if (Launch.classLoader.getResource(config) == null) {
                continue;
            }

            // Warn the user to physically remove the conflicting jar
            Fulgor.LOGGER.warn("{} was detected. Impetus' own lighting engine (Fulgor) replaces it "
                    + "entirely and its patches will be suppressed; you should remove it.", mod.getKey());

            Config.blacklist(config);
        }
    }

}
