package com.bdmajora.extras.client;

import com.bdmajora.impetus.impl.compat.OptionalHooks;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

// Gamma as updateLightmap reads it, for the lightmap cache key.
// Thaumcraft: Return of the Ancients ASMs updateLightmap's gammaSetting read into ASMHookRotA.hookGammaSetting(), which answers 0 in its
// Ancient World and taint biomes. A key built from gameSettings.gammaSetting stayed the same when the player walked into a taint biome,
// so the cache kept the bright lightmap until sun, lightning or night vision happened to change.
public final class LightmapGamma {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Extras");

    static final String ROTA_HOOKS = "com.artur114.thaumrota.asm.ASMHookRotA";
    static final String ROTA_METHOD = "hookGammaSetting";

    // Null without RotA or after its hook failed once.
    private static volatile MethodHandle rota = bind(LightmapGamma.class.getClassLoader());

    private LightmapGamma() {
    }

    static MethodHandle bind(ClassLoader loader) {
        return OptionalHooks.bind(loader, ROTA_HOOKS, ROTA_METHOD, MethodType.methodType(float.class));
    }

    // The gamma updateLightmap is about to use; falls back to the setting when no hook answers.
    public static float read(Minecraft mc) {
        MethodHandle handle = rota;
        if (handle != null) {
            try {
                return (float) handle.invokeExact();
            } catch (Throwable t) {
                rota = null;
                LOGGER.error("[Impetus] Thaumcraft: Return of the Ancients' gamma hook failed; the lightmap cache now keys on the plain setting", t);
            }
        }
        return mc.gameSettings.gammaSetting;
    }
}
