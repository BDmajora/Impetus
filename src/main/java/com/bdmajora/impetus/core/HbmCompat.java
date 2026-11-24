package com.bdmajora.impetus.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.transformer.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// Withdraws NTM CE's vanilla terrain hook before Mixin prepares its config.
// NTM 2.6.x picks its vanilla terrain path unless it finds org/taumc/celeritas/CeleritasVintage.class, which Impetus has under its own name,
// so it injects into RenderGlobal.setupTerrain at Entity.setRenderDistanceWeight; Impetus overwrites setupTerrain and that call is gone.
// The hook only stamps vanilla RenderChunks for NTM's chunk spanning TESR pass, which also lives in vanilla code Impetus never runs.
public final class HbmCompat {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    static final String VANILLA_CONFIG = "hbm.vanilla.mixin.json";
    // Path inside the config package; NTM releases after 2.6.1.0 no longer ship it, so the withdrawal is then a no-op.
    static final String TERRAIN_HOOK = "MixinRenderGlobal";
    // Every list Mixin reads mixin names from.
    private static final String[] LISTS = {"mixinClasses", "mixinClassesClient", "mixinClassesServer"};

    private HbmCompat() {
    }

    // Looks the config up among every queued config; safe to call more than once.
    public static boolean withdrawTerrainHook() {
        try {
            Config config = Config.getAllConfigs().get(VANILLA_CONFIG);
            return config != null && withdraw(mixinConfig(config), TERRAIN_HOOK);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[Impetus] Could not withdraw NTM CE's vanilla setupTerrain hook; the game will crash while loading RenderGlobal", e);
            return false;
        }
    }

    // Config.get() is package private, so the wrapped MixinConfig is read from its field.
    static Object mixinConfig(Config config) throws ReflectiveOperationException {
        Field field = Config.class.getDeclaredField("config");
        field.setAccessible(true);
        return field.get(config);
    }

    // Removes the mixin from every list of an unprepared MixinConfig; true when it was listed.
    static boolean withdraw(Object mixinConfig, String mixin) throws ReflectiveOperationException {
        if (mixinConfig == null || mixin == null) {
            return false;
        }
        Class<?> type = mixinConfig.getClass();
        boolean found = false;
        for (String name : LISTS) {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            if (!(field.get(mixinConfig) instanceof List<?> list) || !list.contains(mixin)) {
                continue;
            }
            // Gson may hand back any list type, so a filtered copy replaces it instead of an in-place removal.
            List<Object> kept = new ArrayList<>(list);
            kept.removeIf(mixin::equals);
            field.set(mixinConfig, kept);
            found = true;
        }
        if (!found) {
            return false;
        }
        Field prepared = type.getDeclaredField("prepared");
        prepared.setAccessible(true);
        if (prepared.getBoolean(mixinConfig)) {
            // Mixin already built its MixinInfo list; editing the names now changes nothing.
            LOGGER.warn("[Impetus] NTM CE's {} was prepared before Impetus could withdraw {}", VANILLA_CONFIG, mixin);
            return false;
        }
        LOGGER.info("[Impetus] Withdrew NTM CE's {} from {}: Impetus overwrites RenderGlobal.setupTerrain", mixin, VANILLA_CONFIG);
        return true;
    }
}
