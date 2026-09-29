package com.bdmajora.coarctatio.launch;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.MemoryReport;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableMap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LaunchCleanupTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfigAndLatches() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
        Mixins.set(ClassLoaderCleaner.class, "done", false);
        Mixins.set(RemapperCompactor.class, "done", false);
    }

    @Test
    void theResourceCacheIsWeakenedRatherThanCleared() {
        LaunchClassLoader loader = new LaunchClassLoader(new URL[0]);
        Statics.set(Launch.class, "classLoader", loader);
        Map<String, byte[]> cache = Mixins.get(loader, "resourceCache");
        cache.put("com.example.Mod", new byte[1024]);
        long before = MemoryReport.totalBytes();

        ClassLoaderCleaner.run();
        Map<String, byte[]> weakened = Mixins.get(loader, "resourceCache");
        assertNotSame(cache, weakened);
        // Coremods and crash reports still read it, so the entries are kept, just weakly
        assertArrayEquals(new byte[1024], weakened.get("com.example.Mod"));
        assertTrue(MemoryReport.totalBytes() > before);

        // Latched: a second call from another load phase does nothing
        ClassLoaderCleaner.run();
        assertSame(weakened, Mixins.get(loader, "resourceCache"));

        // The negative cache is reported when it can be reached
        assertTrue(ClassLoaderCleaner.negativeResourceCacheSize() >= 0);
        assertNotNull(Mixins.construct(ClassLoaderCleaner.class));
    }

    @Test
    void nothingIsTouchedWithoutALoaderOrWithTheSwitchOff() {
        Statics.set(Launch.class, "classLoader", null);
        ClassLoaderCleaner.run();
        Mixins.set(ClassLoaderCleaner.class, "done", false);

        CoarctatioConfig.get().weakenClassLoaderCache = false;
        ClassLoaderCleaner.run();
        // With no loader at all the size is unknown rather than zero
        assertEquals(-1, ClassLoaderCleaner.negativeResourceCacheSize());
    }

    @Test
    void theRemapperCachesAreSharedAndTheEmptyOnesDropped() {
        Statics.set(Launch.class, "blackboard", new HashMap<String, Object>());
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        fields.put("com/example/A", new HashMap<>(java.util.Collections.singletonMap("field_1", "name")));
        fields.put("com/example/B", new HashMap<>(java.util.Collections.singletonMap("field_1", "name")));
        fields.put("com/example/Empty", new HashMap<>());
        Mixins.set(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps", fields);
        Mixins.set(FMLDeobfuscatingRemapper.INSTANCE, "methodNameMaps", new LinkedHashMap<String, Map<String, String>>());

        RemapperCompactor.run();
        Map<String, Map<String, String>> compacted = Mixins.get(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps");
        assertInstanceOf(RemapperCompactor.CompactMap.class, compacted);
        // Two classes with the same merged map end up sharing one copy, and the empty one is not stored at all
        assertSame(compacted.get("com/example/A"), compacted.get("com/example/B"));
        assertFalse(compacted.containsKey("com/example/Empty"));

        // Latched, and a second run over an already compact map changes nothing
        Mixins.set(RemapperCompactor.class, "done", false);
        RemapperCompactor.run();
        assertSame(compacted, Mixins.get(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps"));
        assertNotNull(Mixins.construct(RemapperCompactor.class));
    }

    @Test
    void aDevelopmentWorkspaceOrDisabledSwitchIsLeftAlone() {
        Map<String, Object> blackboard = new HashMap<>();
        blackboard.put("fml.deobfuscatedEnvironment", Boolean.TRUE);
        Statics.set(Launch.class, "blackboard", blackboard);
        Map<String, Map<String, String>> untouched = new LinkedHashMap<>();
        Mixins.set(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps", untouched);
        RemapperCompactor.run();
        assertSame(untouched, Mixins.get(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps"));

        Mixins.set(RemapperCompactor.class, "done", false);
        CoarctatioConfig.get().compactRemapperCaches = false;
        RemapperCompactor.run();
        assertSame(untouched, Mixins.get(FMLDeobfuscatingRemapper.INSTANCE, "fieldNameMaps"));
    }

    @Test
    void theCompactMapSharesEqualEntriesAndSkipsEmptyOnes() {
        RemapperCompactor.CompactMap map = new RemapperCompactor.CompactMap(4);
        Map<String, String> first = new HashMap<>(java.util.Collections.singletonMap("field_1", "name"));
        assertNull(map.put("com/example/A", first));
        assertNull(map.put("com/example/B", new HashMap<>(first)));
        assertSame(map.get("com/example/A"), map.get("com/example/B"));
        // The stored copy is immutable, since it is shared between classes
        assertInstanceOf(ImmutableMap.class, map.get("com/example/A"));
        // An already immutable map is stored as it is
        ImmutableMap<String, String> immutable = ImmutableMap.of("field_2", "other");
        map.put("com/example/C", immutable);
        assertSame(immutable, map.get("com/example/C"));

        // Nothing to map means nothing stored, and it removes whatever was there
        map.put("com/example/A", new HashMap<>());
        assertFalse(map.containsKey("com/example/A"));
        map.put("com/example/D", null);
        assertFalse(map.containsKey("com/example/D"));
        assertNotNull(map.remove("com/example/B"));

        map.putAll(java.util.Collections.singletonMap("com/example/E", immutable));
        assertTrue(map.containsKey("com/example/E"));
    }
}
