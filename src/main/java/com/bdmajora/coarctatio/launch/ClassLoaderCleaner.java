package com.bdmajora.coarctatio.launch;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.MemoryReport;
import com.google.common.cache.CacheBuilder;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

// Reclaims caches LaunchWrapper (Foundation, under Cleanroom) and FML hold all session (resourceCache alone keeps 100-300 MB of class bytes on a large pack); weakened rather than cleared, as FoamFix does, so late transformer/crash-reporter reads still hit
public final class ClassLoaderCleaner {
    // Latched so a second call is a no-op; the entry point is reachable from more than one load phase
    private static boolean done;

    private ClassLoaderCleaner() {
    }

    // Runs once, as late as is safe: after mod construction, when every class a coremod will ask for has been transformed
    public static void run() {
        if (done) {
            return;
        }
        done = true;

        CoarctatioConfig config = CoarctatioConfig.get();

        if (config.weakenClassLoaderCache) {
            weakenResourceCache();
        }
    }

    // Swaps LaunchClassLoader's byte[] cache for a weak-valued map via reflection; failure leaves vanilla behaviour
    @SuppressWarnings("unchecked")
    private static void weakenResourceCache() {
        LaunchClassLoader loader = Launch.classLoader;

        if (loader == null) {
            return;
        }

        try {
            Field field = cacheField(loader, "resourceCache");

            Map<String, byte[]> existing = (Map<String, byte[]>) field.get(loader);

            if (existing == null) {
                return;
            }

            int size = existing.size();
            long bytes = 0;

            for (byte[] value : existing.values()) {
                if (value != null) {
                    bytes += 16L + value.length;
                }
            }

            // Weak values rather than clear: coremods and crash-report identifiers still read this after load, and a weak map keeps serving them while the GC reclaims the bulk under pressure; Foundation declares the field final, which reflection may still set on Java 25
            Map<String, byte[]> weak = CacheBuilder.newBuilder().weakValues().<String, byte[]>build().asMap();
            weak.putAll(existing);
            field.set(loader, weak);

            MemoryReport.recordClassLoaderBytes(bytes);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Coarctatio.LOGGER.warn("Could not weaken the LaunchWrapper resource cache", e);
        }
    }

    // Size of LaunchWrapper's negative resource cache (names it already failed to find); unused today, returns -1 when unreachable, which callers treat as "unknown"
    @SuppressWarnings("unchecked")
    static int negativeResourceCacheSize() {
        try {
            Field field = cacheField(Launch.classLoader, "negativeResourceCache");
            return ((Set<String>) field.get(Launch.classLoader)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    // Foundation keeps LaunchWrapper's cache fields but declares them on its ActualClassLoader superclass, so the lookup walks up from the loader's own class
    static Field cacheField(LaunchClassLoader loader, String name) throws NoSuchFieldException {
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Not declared at this level; keep climbing
            }
        }
        throw new NoSuchFieldException(name);
    }
}
