package com.bdmajora.impetus.core;

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

// Registrar of the mixin configs of every module.
final class ImpetusMixinRegistrar {

    // Fulgor logger.
    private static final Logger FULGOR_LOGGER = LogManager.getLogger("Fulgor");
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    private static final Map<String, String> SUPERSEDED_LIGHTING_MODS = supersededLightingMods();
    private static final String VANILLA_FRUSTUM_FIX = "mixins/bugfixes/mixins.world.culling.frustum.json";

    // Creates an impetus mixin registrar with default state.
    private ImpetusMixinRegistrar() { }

    // Returns the lighting mods that Fulgor replaces by mod id.
    private static Map<String, String> supersededLightingMods() {
        Map<String, String> mods = new LinkedHashMap<>();

        mods.put("phosphor-lighting", "mixins.phosphor.json");

        mods.put("alfheim", "mixins.alfheim.json");

        return Collections.unmodifiableMap(mods);
    }

    // Queues the Impetus mixin configs.
    static void queueImpetusConfigs() {
        for (String config : mixinConfigs()) {
            Mixins.addConfiguration(config);
        }
    }

    // Universal Tweaks redirects a Set.size() call in vanilla setupTerrain.
    static void suppressVanillaFrustumFix() {
        Config.blacklist(VANILLA_FRUSTUM_FIX);
        // Blacklisting also needs to cover a config that was queued by an earlier coremod.
        boolean queued = Mixins.getConfigs().removeIf(config -> VANILLA_FRUSTUM_FIX.equals(config.getName()));
        if (queued || ModDiscoverer.isModPresent("universaltweaks")) {
            LOGGER.info("Universal Tweaks' vanilla frustum patch is disabled because Impetus renders terrain");
        }
    }

    // Returns the mixin config names.
    private static List<String> mixinConfigs() {
        return Arrays.asList("mixins.impetus.json", "mixins.umbra.json", "mixins.coarctatio.json",
                "mixins.fulgor.json", "mixins.equilibrium.json", "mixins.extras.json",
                "mixins.dynamiclights.json", "mixins.linuxextras.json");
    }

    // Disables the lighting mods that Fulgor replaces.
    static void hijackSupersededLighting() {
        for (Map.Entry<String, String> mod : SUPERSEDED_LIGHTING_MODS.entrySet()) {
            String config = mod.getValue();

            if (!ModDiscoverer.isModPresent(mod.getKey())) {
                continue;
            }

            FULGOR_LOGGER.warn("{} was detected. Impetus' own lighting engine (Fulgor) replaces it "
                    + "entirely and its patches will be suppressed; you should remove it.", mod.getKey());

            Config.blacklist(config);
        }
    }

}
