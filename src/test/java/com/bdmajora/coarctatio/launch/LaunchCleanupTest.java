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
        Mixins.set(RemapperCompactor.class, "done", false);
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
        // Checks the shared copies.
        assertSame(compacted.get("com/example/A"), compacted.get("com/example/B"));
        assertFalse(compacted.containsKey("com/example/Empty"));

        // Checks a second run.
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
        // Checks the stored copy is immutable.
        assertInstanceOf(ImmutableMap.class, map.get("com/example/A"));
        // An already immutable map is stored as it is
        ImmutableMap<String, String> immutable = ImmutableMap.of("field_2", "other");
        map.put("com/example/C", immutable);
        assertSame(immutable, map.get("com/example/C"));

        // Checks an empty map.
        map.put("com/example/A", new HashMap<>());
        assertFalse(map.containsKey("com/example/A"));
        map.put("com/example/D", null);
        assertFalse(map.containsKey("com/example/D"));
        assertNotNull(map.remove("com/example/B"));

        map.putAll(java.util.Collections.singletonMap("com/example/E", immutable));
        assertTrue(map.containsKey("com/example/E"));
    }
}
