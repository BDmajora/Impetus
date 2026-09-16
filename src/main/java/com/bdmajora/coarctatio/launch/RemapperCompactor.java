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

// Chibi's optimizeFMLRemapper: the runtime deobfuscator keeps a merged field and method map per class it has ever remapped, and a mod class extending a Minecraft class gets a full private copy of its parent's; the two caches are swapped for maps that share content-equal copies and skip classes with nothing to map. Reflection since the remapper's package is excluded from transformation
public final class RemapperCompactor {
    private static boolean done;

    private RemapperCompactor() {
    }

    // Runs once at construction, after the SRG file is loaded and before most mod classes have been remapped; entries already made are rebuilt through the same filter
    public static void run() {
        if (done) {
            return;
        }
        done = true;

        if (!CoarctatioConfig.get().compactRemapperCaches) {
            return;
        }
        // In a development workspace the remapper has no mappings at all, and the caches stay empty
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

    // Swaps one cache, returning how many inner maps the old one held
    @SuppressWarnings("unchecked")
    private static long replace(String fieldName) throws ReflectiveOperationException {
        Field field = FMLDeobfuscatingRemapper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, Map<String, String>> existing = (Map<String, Map<String, String>>) field.get(FMLDeobfuscatingRemapper.INSTANCE);
        if (existing == null || existing instanceof CompactMap) {
            return 0;
        }
        CompactMap compact = new CompactMap(existing.size());
        // Under the remapper's own monitor: the transformer remaps from the class loading thread while mods construct on the main one
        synchronized (FMLDeobfuscatingRemapper.INSTANCE) {
            compact.putAll(existing);
            field.set(FMLDeobfuscatingRemapper.INSTANCE, compact);
        }
        return existing.size();
    }

    // The cache itself: a class whose merged map is empty is not stored (the remapper's negative cache then answers for it), and a non-empty map is replaced by the first content-equal one seen
    static final class CompactMap extends Object2ObjectOpenHashMap<String, Map<String, String>> {
        private final ObjectOpenHashSet<Map<String, String>> canonical = new ObjectOpenHashSet<>();

        CompactMap(int expected) {
            super(expected);
        }

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

        @Override
        public void putAll(Map<? extends String, ? extends Map<String, String>> map) {
            for (Map.Entry<? extends String, ? extends Map<String, String>> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public synchronized Map<String, String> get(Object key) {
            return super.get(key);
        }

        @Override
        public synchronized boolean containsKey(Object key) {
            return super.containsKey(key);
        }

        @Override
        public synchronized Map<String, String> remove(Object key) {
            return super.remove(key);
        }
    }
}
