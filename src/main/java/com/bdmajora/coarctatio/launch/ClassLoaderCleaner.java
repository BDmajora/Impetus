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

// Shrinks caches of the launch class loader.
public final class ClassLoaderCleaner {
    // Whether the cleanup ran.
    private static boolean done;

    // Prevents instantiation
    private ClassLoaderCleaner() {
    }

    // Runs the configured cleanups once.
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

    // Replaces the resource cache with a map that holds its values weakly.
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

            Map<String, byte[]> weak = CacheBuilder.newBuilder().weakValues().<String, byte[]>build().asMap();
            weak.putAll(existing);
            field.set(loader, weak);

            MemoryReport.recordClassLoaderBytes(bytes);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Coarctatio.LOGGER.warn("Could not weaken the LaunchWrapper resource cache", e);
        }
    }

    // Returns the size of the negative resource cache or minus one when it cannot be read.
    @SuppressWarnings("unchecked")
    static int negativeResourceCacheSize() {
        try {
            Field field = cacheField(Launch.classLoader, "negativeResourceCache");
            return ((Set<String>) field.get(Launch.classLoader)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    // Returns the accessible field of the loader or one of its superclasses.
    static Field cacheField(LaunchClassLoader loader, String name) throws NoSuchFieldException {
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
