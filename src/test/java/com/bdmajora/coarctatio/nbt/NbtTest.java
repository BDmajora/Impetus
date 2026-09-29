package com.bdmajora.coarctatio.nbt;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NbtTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @Test
    void aSmallCompoundStaysOnTheFlatArrayAndPromotesWhenItGrows() {
        CoarctatioConfig.get().nbtArrayMapThreshold = 3;
        TagMap map = new TagMap();
        assertInstanceOf(Object2ObjectArrayMap.class, Mixins.get(map, "delegate"));
        assertTrue(map.isEmpty());

        for (int i = 0; i < 3; i++) {
            assertNull(map.put("key" + i, new NBTTagInt(i)));
        }
        assertInstanceOf(Object2ObjectArrayMap.class, Mixins.get(map, "delegate"));
        // Overwriting a key the array already holds does not promote
        assertEquals(new NBTTagInt(0), map.put("key0", new NBTTagInt(9)));
        assertInstanceOf(Object2ObjectArrayMap.class, Mixins.get(map, "delegate"));
        // The key that would take it past the threshold does
        map.put("key3", new NBTTagInt(3));
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(map, "delegate"));
        assertEquals(4, map.size());
        assertEquals(new NBTTagInt(9), map.get("key0"));

        // Clearing demotes again, so a briefly large compound does not keep the table alive
        map.clear();
        assertInstanceOf(Object2ObjectArrayMap.class, Mixins.get(map, "delegate"));
        assertTrue(map.isEmpty());
    }

    @Test
    void aZeroThresholdStartsOnTheHashMap() {
        CoarctatioConfig.get().nbtArrayMapThreshold = 0;
        TagMap map = new TagMap();
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(map, "delegate"));
        map.put("a", new NBTTagInt(1));
        map.clear();
        assertInstanceOf(Object2ObjectOpenHashMap.class, Mixins.get(map, "delegate"));
    }

    @Test
    void keysAreInternedThroughTheSharedPoolWhenEnabled() {
        CoarctatioConfig.get().internNbtKeys = true;
        TagMap map = new TagMap();
        String pooled = StringPool.NBT_KEYS.deduplicate("Damage");
        map.put(new String("Damage"), new NBTTagShort((short) 1));
        assertSame(pooled, map.keySet().iterator().next());

        // With interning off the key is stored as handed in
        Mixins.set(CoarctatioConfig.class, "instance", null);
        CoarctatioConfig.get().internNbtKeys = false;
        TagMap plain = new TagMap();
        String own = new String("Damage");
        plain.put(own, new NBTTagShort((short) 1));
        assertSame(own, plain.keySet().iterator().next());
    }

    @Test
    void everythingElseReadsThroughToTheBackingMap() {
        CoarctatioConfig.get().nbtArrayMapThreshold = 12;
        TagMap map = new TagMap();
        Map<String, NBTBase> source = new HashMap<>();
        source.put("a", new NBTTagString("one"));
        source.put("b", new NBTTagString("two"));
        map.putAll(source);

        assertEquals(2, map.size());
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("z"));
        assertTrue(map.containsValue(new NBTTagString("one")));
        assertEquals(new NBTTagString("two"), map.get("b"));
        assertEquals(2, map.values().size());
        assertEquals(2, map.entrySet().size());
        assertEquals(new NBTTagString("one"), map.remove("a"));
        assertEquals(1, map.size());

        // Equality and printing are the backing map's, so a compound compares the same either way
        assertEquals(map, map);
        Map<String, NBTBase> same = new HashMap<>();
        same.put("b", new NBTTagString("two"));
        assertEquals(same, map);
        assertEquals(same.hashCode(), map.hashCode());
        // Printing is the backing map's own, which spells entries the fastutil way
        assertEquals(Mixins.<Map<String, NBTBase>>get(map, "delegate").toString(), map.toString());
    }

    @Test
    void smallNumericTagsComeFromTheSharedTable() {
        // Every byte is pooled
        assertSame(NbtPrimitivePool.of((byte) -128), NbtPrimitivePool.of((byte) -128));
        assertSame(NbtPrimitivePool.of((byte) 127), NbtPrimitivePool.of((byte) 127));
        assertEquals(5, NbtPrimitivePool.of((byte) 5).getByte());

        // The wider types are pooled over -128..1023 and allocated outside it
        assertSame(NbtPrimitivePool.of((short) 1023), NbtPrimitivePool.of((short) 1023));
        assertNotSame(NbtPrimitivePool.of((short) 1024), NbtPrimitivePool.of((short) 1024));
        assertEquals(1024, NbtPrimitivePool.of((short) 1024).getShort());
        assertSame(NbtPrimitivePool.of(-128), NbtPrimitivePool.of(-128));
        assertNotSame(NbtPrimitivePool.of(-129), NbtPrimitivePool.of(-129));
        assertEquals(-129, NbtPrimitivePool.of(-129).getInt());
        assertSame(NbtPrimitivePool.of(7L), NbtPrimitivePool.of(7L));
        assertNotSame(NbtPrimitivePool.of(9000L), NbtPrimitivePool.of(9000L));
        assertEquals(9000L, NbtPrimitivePool.of(9000L).getLong());

        // A freshly read tag is swapped for its pooled twin, anything else is handed back
        assertSame(NbtPrimitivePool.of((byte) 3), NbtPrimitivePool.canonical(new NBTTagByte((byte) 3)));
        assertSame(NbtPrimitivePool.of((short) 3), NbtPrimitivePool.canonical(new NBTTagShort((short) 3)));
        assertSame(NbtPrimitivePool.of(3), NbtPrimitivePool.canonical(new NBTTagInt(3)));
        assertSame(NbtPrimitivePool.of(3L), NbtPrimitivePool.canonical(new NBTTagLong(3L)));
        NBTTagString text = new NBTTagString("keep me");
        assertSame(text, NbtPrimitivePool.canonical(text));
        assertNotNull(Mixins.construct(NbtPrimitivePool.class));
    }
}
