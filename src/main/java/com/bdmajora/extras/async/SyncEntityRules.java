package com.bdmajora.extras.async;

import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

// Entity types that must tick on the main thread.
public final class SyncEntityRules {
    // Identifiers of single types.
    private static volatile Set<String> exact = new HashSet<>();
    // Namespaces whose types all match.
    private static volatile Set<String> namespaces = new HashSet<>();

    // Prevents instantiation
    private SyncEntityRules() {
    }

    // Replaces the rules and ignores null and blank entries.
    public static void load(String[] entries) {
        Set<String> ids = new HashSet<>();
        Set<String> wildcards = new HashSet<>();
        for (String entry : entries == null ? new String[0] : entries) {
            if (entry == null) {
                continue;
            }
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

    // Returns whether the entity type matches a rule.
    public static boolean matches(ResourceLocation key) {
        return exact.contains(key.toString()) || namespaces.contains(key.getNamespace());
    }
}
