package com.bdmajora.coarctatio.launch;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.MemoryReport;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

import java.lang.reflect.Field;
import java.util.Map;

// Shrinks the caches of the FML remapper.
public final class RemapperCompactor {
    // Whether the compaction ran.
    private static boolean done;

    // Prevents instantiation
    private RemapperCompactor() {
    }

    // Compacts the remapper caches once outside a development environment.
    public static void run() {
        if (done) {
            return;
        }
        done = true;

        if (!CoarctatioConfig.get().compactRemapperCaches) {
            return;
        }
        Object deobf = Launch.blackboard.get("fml.deobfuscatedEnvironment");
        if (deobf instanceof Boolean && (Boolean) deobf) {
            return;
        }

        try {
            long before = replace("fieldNameMaps") + replace("methodNameMaps");
            MemoryReport.recordRemapperEntries(before);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Coarctatio.LOGGER.warn("Could not compact the FML remapper caches", e);
        }
    }

    // Replaces the named cache with a compact map and returns its entry count.
    @SuppressWarnings("unchecked")
    private static long replace(String fieldName) throws ReflectiveOperationException {
        Field field = FMLDeobfuscatingRemapper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, Map<String, String>> existing = (Map<String, Map<String, String>>) field.get(FMLDeobfuscatingRemapper.INSTANCE);
        if (existing == null || existing instanceof CompactMap) {
            return 0;
        }
        // Creates a map sized for the expected number of entries.
        CompactMap compact = new CompactMap(existing.size());
        synchronized (FMLDeobfuscatingRemapper.INSTANCE) {
            compact.putAll(existing);
            field.set(FMLDeobfuscatingRemapper.INSTANCE, compact);
        }
        return existing.size();
    }

    // Map that shares equal inner maps and pooled class names.
    static final class CompactMap extends Object2ObjectOpenHashMap<String, Map<String, String>> {
        // Inner maps already stored.
        private final ObjectOpenHashSet<Map<String, String>> canonical = new ObjectOpenHashSet<>();

        CompactMap(int expected) {
            super(expected);
        }

        // Stores a shared copy of the inner map and drops the entry when it is null or empty.
        @Override
        public synchronized Map<String, String> put(String className, Map<String, String> inner) {
            if (inner == null || inner.isEmpty()) {
                return super.remove(className);
            }
            Map<String, String> shared = this.canonical.get(inner);
            if (shared == null) {
                shared = inner instanceof ImmutableMap ? inner : ImmutableMap.copyOf(inner);
                this.canonical.add(shared);
            }
            return super.put(StringPool.LOADER.deduplicate(className), shared);
        }

        // Stores every entry through put.
        @Override
        public void putAll(Map<? extends String, ? extends Map<String, String>> map) {
            for (Map.Entry<? extends String, ? extends Map<String, String>> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        // Returns the inner map of the class name.
        @Override
        public synchronized Map<String, String> get(Object key) {
            return super.get(key);
        }

        // Returns whether the class name is present.
        @Override
        public synchronized boolean containsKey(Object key) {
            return super.containsKey(key);
        }

        // Removes the class name and returns its inner map.
        @Override
        public synchronized Map<String, String> remove(Object key) {
            return super.remove(key);
        }
    }
}
