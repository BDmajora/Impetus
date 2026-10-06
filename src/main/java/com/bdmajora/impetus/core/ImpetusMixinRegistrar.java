package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import zone.rong.mixinbooter.service.ModDiscoverer;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Registers Impetus' mixin configurations and suppresses superseded lighting engines, talking to Cleanroom's built-in Mixin (CleanMix) directly
final class ImpetusMixinRegistrar {

    // Fulgor's logger by name only: touching the Fulgor class from a coremod would link it, and with it game types, before any mixin is registered
    private static final Logger FULGOR_LOGGER = LogManager.getLogger("Fulgor");

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

    // Queues every Impetus config; from the coremod constructor, so they are in before any other coremod's injectData could load a target
    static void queueImpetusConfigs() {
        for (String config : mixinConfigs()) {
            Mixins.addConfiguration(config);
        }
    }

    // Order: Impetus/Umbra reserve early-load slots, Coarctatio must apply before vanilla NBT/ResourceLocation instantiation, Fulgor injects lighting fields into World/Chunk, Equilibrium loads last so Fulgor reads its chunk cache, Extras and Dynamic Lights are order-independent after those
    private static List<String> mixinConfigs() {
        return Arrays.asList("mixins.impetus.json", "mixins.umbra.json", "mixins.coarctatio.json",
                "mixins.fulgor.json", "mixins.equilibrium.json", "mixins.extras.json",
                "mixins.dynamiclights.json");
    }

    // DH's fade pass samples that depth texture through the accessor Impetus registers with DH; injectData runs after every coremod jar joined the classpath, so the resource lookup sees DH's
    static void restoreDistantHorizonsDepthTexture() {
        if (Launch.classLoader.getResource(DH_DEPTH_TEXTURE_CONFIG) != null) {
            Mixins.addConfiguration(DH_DEPTH_TEXTURE_CONFIG);
        }
    }

    // Blacklists Phosphor and Alfheim configs when either mod is installed, since two lighting engines corrupt light; runs from the coremod constructor, before CleanMix queues any MixinBooter early loader's configs
    static void hijackSupersededLighting() {
        for (Map.Entry<String, String> mod : SUPERSEDED_LIGHTING_MODS.entrySet()) {
            String config = mod.getValue();

            // Cleanroom's discoverer has read every jar's mod ids before the first coremod is built, while only coremod jars are on the classpath yet
            if (!ModDiscoverer.isModPresent(mod.getKey())) {
                continue;
            }

            // Warn the user to physically remove the conflicting jar
            FULGOR_LOGGER.warn("{} was detected. Impetus' own lighting engine (Fulgor) replaces it "
                    + "entirely and its patches will be suppressed; you should remove it.", mod.getKey());

            Config.blacklist(config);
        }
    }

}
