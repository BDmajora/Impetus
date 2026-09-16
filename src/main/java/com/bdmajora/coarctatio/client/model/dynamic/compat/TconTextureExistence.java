package com.bdmajora.coarctatio.client.model.dynamic.compat;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

// The answers TConstruct's texture creator got for its existence probes, held here so the reload can clear them; the mixin on the creator reads and writes it
public final class TconTextureExistence {
    private static final Object2BooleanMap<String> KNOWN = Object2BooleanMaps.synchronize(new Object2BooleanOpenHashMap<>());

    private TconTextureExistence() {
    }

    // Null when the key has not been probed this reload
    public static Boolean known(String key) {
        synchronized (KNOWN) {
            return KNOWN.containsKey(key) ? KNOWN.getBoolean(key) : null;
        }
    }

    public static void remember(String key, boolean exists) {
        KNOWN.put(key, exists);
    }

    public static void clear() {
        KNOWN.clear();
    }
}
