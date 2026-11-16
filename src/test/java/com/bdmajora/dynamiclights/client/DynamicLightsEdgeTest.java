package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.dynamiclights.DynamicLightsMode;
import com.bdmajora.dynamiclights.client.item.ItemLightSource;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DynamicLightsEdgeTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
        Mc.client();
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
    }

    @Test
    void sectionPositionsSurviveAPackAndUnpack() {
        int[] xs = {0, 1, -1, 1000, -1000, 1_000_000, -1_000_000, (1 << 25) - 1, -(1 << 25)};
        int[] ys = {0, 1, 15, 4095};
        int[] zs = {0, -1, 7, 33_554_431, -33_554_432};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    long packed = DynamicLightsEngine.packChunkPos(new BlockPos(x, y, z));
                    assertEquals(x, DynamicLightsEngine.unpackX(packed), "x " + x);
                    assertEquals(y, DynamicLightsEngine.unpackY(packed), "y " + y);
                    assertEquals(z, DynamicLightsEngine.unpackZ(packed), "z " + z);
                }
            }
        }
    }

    @Test
    void trackedSetsHandleNullsAndMoves() {
        LongOpenHashSet old = new LongOpenHashSet();
        LongOpenHashSet next = new LongOpenHashSet();
        BlockPos pos = new BlockPos(3, 4, 5);
        long packed = DynamicLightsEngine.packChunkPos(pos);
        old.add(packed);
        DynamicLightsEngine.updateTrackedChunks(pos, old, next);
        assertTrue(old.isEmpty());
        assertTrue(next.contains(packed));
        DynamicLightsEngine.updateTrackedChunks(pos, null, null);
        DynamicLightsEngine.updateTrackedChunks(pos, null, next);
        DynamicLightsEngine.updateTrackedChunks(pos, next, null);
        assertTrue(next.isEmpty());
    }

    @Test
    void lightmapsKeepSkyLightAndOnlyRaiseBlockLight() {
        DynamicLightsEngine engine = DynamicLightsEngine.get();
        int lightmap = (12 << 20) | (3 << 4);
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(0.0, lightmap));
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(-5.0, lightmap));
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(2.9, lightmap));
        assertEquals((12 << 20) | (9 << 4), engine.getLightmapWithDynamicLight(9.9, lightmap));
        assertEquals((12 << 20) | (15 << 4), engine.getLightmapWithDynamicLight(15.0, lightmap));
        assertEquals(0.0, engine.getDynamicLightLevel(0, 0, 0));
    }

    @Test
    void updateStampsSurviveAClockThatMovedBackwards() {
        DynamicLightsConfig options = DynamicLights.options();
        DynamicLightsMode saved = options.mode;
        try {
            options.mode = DynamicLightsMode.OFF;
            assertEquals(-1L, DynamicLightsEngine.nextUpdateStamp(0L));
            options.mode = DynamicLightsMode.REALTIME;
            assertEquals(123L, DynamicLightsEngine.nextUpdateStamp(123L));
            options.mode = DynamicLightsMode.SLOW;
            long future = System.currentTimeMillis() + 3_600_000L;
            assertTrue(DynamicLightsEngine.nextUpdateStamp(future) > 0);
            long recent = System.currentTimeMillis();
            assertEquals(-1L, DynamicLightsEngine.nextUpdateStamp(recent));
            assertTrue(DynamicLightsEngine.nextUpdateStamp(recent - 10_000L) >= recent);
        } finally {
            options.mode = saved;
        }
    }

    @Test
    void combinedHandlersKeepWaterSensitivityOfEither() {
        Map<?, ?> handlers = Mixins.get(DynamicLightHandlers.class, "ENTITY_HANDLERS");
        Map<?, ?> lookup = Mixins.get(DynamicLightHandlers.class, "ENTITY_LOOKUP");
        handlers.clear();
        lookup.clear();
        DynamicLightHandlers.registerEntityHandler(EntityBlaze.class, DynamicLightHandler.makeHandler(b -> 4, b -> true));
        DynamicLightHandlers.registerEntityHandler(EntityBlaze.class, b -> 9);
        EntityBlaze blaze = mock(EntityBlaze.class);
        DynamicLightHandler<EntityBlaze> handler = DynamicLightHandlers.getDynamicLightHandler(blaze);
        assertEquals(9, handler.getLuminance(blaze));
        assertTrue(handler.isWaterSensitive(blaze));
        handlers.clear();
        lookup.clear();
        assertNull(DynamicLightHandlers.getDynamicLightHandler(blaze));
    }

    @Test
    void disabledTypeListsToleratNullAndBlankEntries() {
        LightSourceSettings settings = LightSourceSettings.getInstance();
        settings.loadDisabledEntities(new String[] {"minecraft:zombie", null, ""});
        assertArrayEquals(new String[] {"minecraft:zombie"}, settings.getDisabledEntitiesArray());
        settings.loadDisabledEntities(null);
        assertEquals(0, settings.getDisabledEntitiesArray().length);
        settings.loadDisabledBlockEntities(null);
        assertEquals(0, settings.getDisabledBlockEntitiesArray().length);
    }

    @Test
    void malformedItemDefinitionsAreRefusedNotThrown() {
        ResourceLocation id = new ResourceLocation("impetus", "test");
        assertFalse(ItemLightSource.fromJson(id, new JsonObject()).isPresent());
        JsonObject noLuminance = JsonParser.parseString("{\"item\":\"minecraft:torch\"}").getAsJsonObject();
        assertFalse(ItemLightSource.fromJson(id, noLuminance).isPresent());
        JsonObject unknown = JsonParser.parseString("{\"item\":\"nope:none\",\"luminance\":5}").getAsJsonObject();
        assertFalse(ItemLightSource.fromJson(id, unknown).isPresent());
        JsonObject bool = JsonParser.parseString("{\"item\":\"minecraft:torch\",\"luminance\":true}").getAsJsonObject();
        assertFalse(ItemLightSource.fromJson(id, bool).isPresent());
        Optional<ItemLightSource> good = ItemLightSource.fromJson(id,
                JsonParser.parseString("{\"item\":\"minecraft:torch\",\"luminance\":14}").getAsJsonObject());
        assertTrue(good.isPresent());
        assertEquals(14, good.get().getLuminance(net.minecraft.item.ItemStack.EMPTY));
        assertEquals(0, ItemLightSources.getLuminance(net.minecraft.item.ItemStack.EMPTY, false));
    }
}
