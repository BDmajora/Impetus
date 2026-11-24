package com.bdmajora.extras.client;

import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandle;

// Test side door to LightmapGamma's package private seams, for tests that live next to the lightmap mixin.
public final class LightmapGammaAccess {
    public static final String ROTA_HOOKS = LightmapGamma.ROTA_HOOKS;
    public static final String ROTA_METHOD = LightmapGamma.ROTA_METHOD;

    private LightmapGammaAccess() {
    }

    public static MethodHandle bind(ClassLoader loader) {
        return LightmapGamma.bind(loader);
    }

    public static float read(Minecraft mc) {
        return LightmapGamma.read(mc);
    }

    public static MethodHandle installed() {
        return Statics.get(LightmapGamma.class, "rota");
    }

    public static void install(MethodHandle handle) {
        Statics.set(LightmapGamma.class, "rota", handle);
    }
}
