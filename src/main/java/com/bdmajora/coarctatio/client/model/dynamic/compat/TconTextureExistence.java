package com.bdmajora.coarctatio.client.model.dynamic.compat;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

// Remembers which texture files exist.
public final class TconTextureExistence {
    // Existence of each texture key.
    private static final Object2BooleanMap<String> KNOWN = Object2BooleanMaps.synchronize(new Object2BooleanOpenHashMap<>());

    // Prevents instantiation
    private TconTextureExistence() {
    }

    // Returns whether the texture exists or null when unknown.
    public static Boolean known(String key) {
        synchronized (KNOWN) {
            return KNOWN.containsKey(key) ? KNOWN.getBoolean(key) : null;
        }
    }

    // Stores whether the texture exists.
    public static void remember(String key, boolean exists) {
        KNOWN.put(key, exists);
    }

    // Forgets every texture.
    public static void clear() {
        KNOWN.clear();
    }
}
