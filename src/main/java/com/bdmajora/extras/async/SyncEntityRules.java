package com.bdmajora.extras.async;

import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

// The user's "keep these on the main thread" list: exact ids, or namespace:* for a whole mod
public final class SyncEntityRules {
    private static volatile Set<String> exact = new HashSet<>();
    private static volatile Set<String> namespaces = new HashSet<>();

    private SyncEntityRules() {
    }

    // Replaces both tables atomically enough for readers, which only ever see one complete set or the other
    public static void load(String[] entries) {
        Set<String> ids = new HashSet<>();
        Set<String> wildcards = new HashSet<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon > 0 && trimmed.substring(colon + 1).equals("*")) {
                wildcards.add(trimmed.substring(0, colon));
            } else {
                ids.add(colon < 0 ? "minecraft:" + trimmed : trimmed);
            }
        }
        exact = ids;
        namespaces = wildcards;
    }

    public static boolean matches(ResourceLocation key) {
        return exact.contains(key.toString()) || namespaces.contains(key.getNamespace());
    }
}
