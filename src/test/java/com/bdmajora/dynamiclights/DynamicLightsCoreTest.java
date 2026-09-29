package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.DynamicLightHandler;
import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import com.bdmajora.dynamiclights.client.FluidHandler;
import com.bdmajora.dynamiclights.client.LightSourceSettings;
import com.bdmajora.dynamiclights.client.TileEntityLightTicker;
import com.bdmajora.dynamiclights.client.item.ItemLightSource;
import com.bdmajora.dynamiclights.client.item.ItemLightSources;
import com.bdmajora.dynamiclights.gui.DynamicLightsOptionsStorage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicLightsCoreTest {
    @TempDir
    Path dir;

    private Minecraft client;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        client = Mc.client();
        // The handler registry is static, so each test starts from an empty one
        for (String map : new String[] {"ENTITY_HANDLERS", "ENTITY_LOOKUP", "TILE_ENTITY_HANDLERS", "TILE_ENTITY_LOOKUP"}) {
            Mixins.<Map<?, ?>>get(DynamicLightHandlers.class, map).clear();
        }
        // Forge's Configuration resolves paths against the injected game directory
        com.bdmajora.testing.Statics.set(net.minecraftforge.fml.relauncher.FMLInjectionData.class,
                "minecraftHome", dir.toFile());
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
        DynamicLights.engine().clearLightSources();
    }

    // A tracked source at a fixed position with a fixed luminance
    private static DynamicLightSource source(World world, double x, double y, double z, int luminance) {
        DynamicLightSource source = mock(DynamicLightSource.class, Mockito.CALLS_REAL_METHODS);
        when(source.impetus$getDynamicLightWorld()).thenReturn(world);
        when(source.impetus$getDynamicLightX()).thenReturn(x);
        when(source.impetus$getDynamicLightY()).thenReturn(y);
        when(source.impetus$getDynamicLightZ()).thenReturn(z);
        when(source.impetus$getLuminance()).thenReturn(luminance);
        return source;
    }

    private static World clientWorld() {
        World world = mock(World.class);
        Mixins.set(world, "isRemote", true);
        return world;
    }

    @Test
    void modesDescribeTheirUpdateRate() {
        assertFalse(DynamicLightsMode.OFF.isEnabled());
        assertTrue(DynamicLightsMode.SLOW.isEnabled());
        assertTrue(DynamicLightsMode.SLOW.hasDelay());
        assertEquals(500, DynamicLightsMode.SLOW.getDelay());
        assertEquals(250, DynamicLightsMode.FAST.getDelay());
        assertFalse(DynamicLightsMode.REALTIME.hasDelay());
        assertTrue(DynamicLightsMode.REALTIME.isEnabled());
        assertEquals("impetus.options.dynamiclights.mode.off", DynamicLightsMode.OFF.translationKey());
        assertFalse(ExplosiveLightingMode.OFF.isEnabled());
        assertTrue(ExplosiveLightingMode.SIMPLE.isEnabled());
        assertTrue(ExplosiveLightingMode.FANCY.isEnabled());
        assertEquals("impetus.options.dynamiclights.explosive.fancy", ExplosiveLightingMode.FANCY.translationKey());
        assertEquals(DynamicLightsMode.values().length, DynamicLightsMode.valueOf("FAST").ordinal() + 2);
        assertEquals(ExplosiveLightingMode.SIMPLE, ExplosiveLightingMode.valueOf("SIMPLE"));
    }

    @Test
    void theConfigRoundTripsThroughAForgeFile() throws Exception {
        File file = dir.resolve("dynamiclights.cfg").toFile();
        DynamicLightsConfig config = DynamicLightsConfig.load(file);
        assertEquals(DynamicLightsMode.REALTIME, config.mode);
        assertTrue(config.selfLightSource);
        assertFalse(config.showDebugInfo);
        assertTrue(file.isFile());

        config.mode = DynamicLightsMode.SLOW;
        config.creeperLighting = ExplosiveLightingMode.OFF;
        config.showDebugInfo = true;
        config.entitiesLightSource = false;
        LightSourceSettings.getInstance().setEntityTypeEnabled("minecraft:creeper", false);
        config.writeChanges();
        String written = Files.readString(file.toPath());
        assertTrue(written.contains("mode=1"));
        assertTrue(written.contains("minecraft:creeper"));

        DynamicLightsConfig reloaded = DynamicLightsConfig.load(file);
        assertEquals(DynamicLightsMode.SLOW, reloaded.mode);
        assertEquals(ExplosiveLightingMode.OFF, reloaded.creeperLighting);
        assertTrue(reloaded.showDebugInfo);
        assertFalse(reloaded.entitiesLightSource);
        assertTrue(LightSourceSettings.getInstance().isEntityTypeDisabled("minecraft:creeper"));
        LightSourceSettings.getInstance().setEntityTypeEnabled("minecraft:creeper", true);

        // An unreadable file falls back to defaults and does not overwrite what is there
        File directory = dir.resolve("as-a-directory").toFile();
        assertTrue(directory.mkdir());
        DynamicLightsConfig broken = DynamicLightsConfig.load(directory);
        assertEquals(DynamicLightsMode.REALTIME, broken.mode);
        // A config that was never loaded has nothing to write to
        DynamicLightsConfig detached = new DynamicLightsConfig();
        detached.writeChanges();

        // The subsystem's own accessors: lazy load, replace, save
        Mixins.set(DynamicLights.class, "config", null);
        DynamicLights.initialize();
        assertNotNull(DynamicLights.options());
        assertSame(DynamicLights.options(), DynamicLights.options());
        assertSame(DynamicLightsEngine.get(), DynamicLights.engine());
        DynamicLights.save();
        assertNotNull(new DynamicLightsOptionsStorage().getData());
        new DynamicLightsOptionsStorage().save();

        // Client init registers the handlers, the reload listener and the block entity ticker
        net.minecraft.client.resources.IReloadableResourceManager resources =
                mock(net.minecraft.client.resources.IReloadableResourceManager.class);
        when(client.getResourceManager()).thenReturn(resources);
        DynamicLights.onClientInit();
        Mockito.verify(resources).registerReloadListener(any());
    }

    @Test
    void theEngineTracksSourcesAndFoldsThemIntoTheLightmap() {
        DynamicLightsEngine engine = DynamicLights.engine();
        World world = clientWorld();
        assertEquals(0, engine.getLightSourcesCount());
        assertEquals(0.0D, engine.getDynamicLightLevel(new BlockPos(0, 0, 0)));

        DynamicLightSource torch = source(world, 0.5D, 64.5D, 0.5D, 14);
        engine.addLightSource(torch);
        assertEquals(1, engine.getLightSourcesCount());
        assertTrue(engine.containsLightSource(torch));
        // Adding twice is a no-op, and a source in a server world is never tracked
        engine.addLightSource(torch);
        assertEquals(1, engine.getLightSourcesCount());
        World server = mock(World.class);
        Mixins.set(server, "isRemote", false);
        engine.addLightSource(source(server, 0, 0, 0, 14));
        engine.addLightSource(source(null, 0, 0, 0, 14));
        assertEquals(1, engine.getLightSourcesCount());
        assertFalse(engine.containsLightSource(source(null, 0, 0, 0, 14)));

        // Light falls off linearly to nothing at the maximum radius
        assertEquals(14.0D, engine.getDynamicLightLevel(new BlockPos(0, 64, 0)));
        assertTrue(engine.getDynamicLightLevel(new BlockPos(3, 64, 0)) < 14.0D);
        assertEquals(0.0D, engine.getDynamicLightLevel(new BlockPos(20, 64, 0)));
        assertEquals(0.0D, DynamicLightsEngine.maxDynamicLightLevel(0, 0, 0, source(world, 0, 0, 0, 0), 0.0D));

        // Only the block half of the lightmap is raised
        int lightmap = (7 << 20) | (2 << 4);
        assertEquals((7 << 20) | (9 << 4), engine.getLightmapWithDynamicLight(9.0D, lightmap));
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(1.0D, lightmap));
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(0.0D, lightmap));
        assertEquals((7 << 20) | (14 << 4), engine.getLightmapWithDynamicLight(new BlockPos(0, 64, 0), lightmap));

        Entity entity = mock(Entity.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        entity.posX = 0.5D;
        entity.posY = 64.0D;
        entity.posZ = 0.5D;
        when(((DynamicLightSource) entity).impetus$getLuminance()).thenReturn(3);
        assertEquals((7 << 20) | (14 << 4), engine.getLightmapWithDynamicLight(entity, lightmap));

        // Removal re-lights what the source was covering
        engine.removeLightSource(torch);
        assertEquals(0, engine.getLightSourcesCount());
        Mockito.verify(torch).impetus$scheduleTrackedChunksRebuild(any());
        assertEquals(lightmap, engine.getLightmapWithDynamicLight(entity, lightmap));

        // Bulk removal by kind
        EntityZombie zombie = mock(EntityZombie.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        when(((DynamicLightSource) zombie).impetus$getDynamicLightWorld()).thenReturn(world);
        when(((DynamicLightSource) zombie).impetus$getLuminance()).thenReturn(5);
        engine.addLightSource((DynamicLightSource) zombie);
        assertEquals(1, engine.getLightSourcesCount());
        engine.removeEntitiesLightSource();
        assertEquals(0, engine.getLightSourcesCount());
        TileEntityChest chest = mock(TileEntityChest.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        when(((DynamicLightSource) chest).impetus$getDynamicLightWorld()).thenReturn(world);
        engine.addLightSource((DynamicLightSource) chest);
        engine.removeBlockEntitiesLightSource();
        assertEquals(0, engine.getLightSourcesCount());
        EntityCreeper creeper = mock(EntityCreeper.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        when(((DynamicLightSource) creeper).impetus$getDynamicLightWorld()).thenReturn(world);
        engine.addLightSource((DynamicLightSource) creeper);
        engine.removeCreeperLightSources();
        assertEquals(0, engine.getLightSourcesCount());
        net.minecraft.entity.item.EntityTNTPrimed tnt = mock(net.minecraft.entity.item.EntityTNTPrimed.class,
                Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        when(((DynamicLightSource) tnt).impetus$getDynamicLightWorld()).thenReturn(world);
        engine.addLightSource((DynamicLightSource) tnt);
        engine.removeTntLightSources();
        assertEquals(0, engine.getLightSourcesCount());

        // Clearing drops everything and resets each source
        DynamicLightSource lit = source(world, 0, 0, 0, 5);
        engine.addLightSource(lit);
        engine.clearLightSources();
        assertEquals(0, engine.getLightSourcesCount());
        Mockito.verify(lit).impetus$resetDynamicLight();
    }

    @Test
    void theEngineUpdatesEverySourceOncePerTick() {
        DynamicLightsEngine engine = DynamicLights.engine();
        RenderGlobal renderer = mock(RenderGlobal.class);
        World world = clientWorld();
        DynamicLightSource source = source(world, 0, 0, 0, 5);
        when(source.impetus$updateDynamicLight(renderer)).thenReturn(true);

        // Nothing tracked, so the pass returns before taking the lock
        engine.updateAll(renderer);
        assertEquals(0, engine.getLastUpdateCount());
        engine.addLightSource(source);
        Mixins.set(engine, "lastUpdate", 0L);
        engine.updateAll(renderer);
        assertEquals(1, engine.getLastUpdateCount());
        // A second pass in the same tick is skipped
        engine.updateAll(renderer);
        assertEquals(1, engine.getLastUpdateCount());
        Mockito.verify(source, Mockito.times(1)).impetus$updateDynamicLight(renderer);
        // With the mode off nothing is updated at all
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        Mixins.set(engine, "lastUpdate", 0L);
        engine.updateAll(renderer);
        Mockito.verify(source, Mockito.times(1)).impetus$updateDynamicLight(renderer);
    }

    @Test
    void trackingFollowsLuminanceAcrossZero() {
        World world = clientWorld();
        DynamicLightSource source = source(world, 0, 0, 0, 0);
        DynamicLightsEngine.updateTracking(source);
        assertFalse(DynamicLights.engine().containsLightSource(source));
        when(source.impetus$getLuminance()).thenReturn(7);
        DynamicLightsEngine.updateTracking(source);
        assertTrue(DynamicLights.engine().containsLightSource(source));
        when(source.impetus$getLuminance()).thenReturn(0);
        DynamicLightsEngine.updateTracking(source);
        assertFalse(DynamicLights.engine().containsLightSource(source));
        // The shared tick tail drops a removed source and re-evaluates a live one
        source.impetus$tickDynamicLight(true);
        Mockito.verify(source, Mockito.atLeastOnce()).impetus$resetDynamicLight();
        source.impetus$tickDynamicLight(false);
        Mockito.verify(source).impetus$dynamicLightTick();
        assertFalse(source.impetus$isDynamicLightEnabled());
    }

    @Test
    void updateStampsRespectTheConfiguredRate() {
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        assertEquals(-1L, DynamicLightsEngine.nextUpdateStamp(0L));
        DynamicLights.options().mode = DynamicLightsMode.REALTIME;
        assertEquals(1234L, DynamicLightsEngine.nextUpdateStamp(1234L));
        DynamicLights.options().mode = DynamicLightsMode.SLOW;
        assertEquals(-1L, DynamicLightsEngine.nextUpdateStamp(System.currentTimeMillis()));
        assertTrue(DynamicLightsEngine.nextUpdateStamp(0L) > 0L);
    }

    @Test
    void litSectionsAreWalkedAndPackedAroundASource() {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet lit = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet old = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        // A source in the middle of a section still spills into the seven neighbouring ones
        DynamicLightsEngine.walkLitSections(null, 20.0D, 70.0D, -3.0D, old, lit);
        assertEquals(8, lit.size());
        assertTrue(lit.contains(DynamicLightsEngine.packChunkPos(new BlockPos(1, 4, -1))));
        // The other corner of a section walks the opposite neighbours
        lit.clear();
        DynamicLightsEngine.walkLitSections(null, 16.0D, 64.0D, 0.0D, null, lit);
        assertEquals(8, lit.size());
        assertTrue(lit.contains(DynamicLightsEngine.packChunkPos(new BlockPos(0, 3, -1))));

        long packed = DynamicLightsEngine.packChunkPos(new BlockPos(-5, 9, 7));
        assertEquals(-5, DynamicLightsEngine.unpackX(packed));
        assertEquals(9, DynamicLightsEngine.unpackY(packed));
        assertEquals(7, DynamicLightsEngine.unpackZ(packed));
        // Moving a position from one set to the other, and the no-op form
        old.clear();
        old.add(packed);
        lit.clear();
        DynamicLightsEngine.updateTrackedChunks(new BlockPos(-5, 9, 7), old, lit);
        assertTrue(old.isEmpty());
        assertTrue(lit.contains(packed));
        DynamicLightsEngine.updateTrackedChunks(new BlockPos(0, 0, 0), null, null);

        // Rebuild scheduling needs a world, and goes through the accessor Impetus overwrites
        RenderGlobal renderer = mock(RenderGlobal.class,
                Mockito.withSettings().extraInterfaces(com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor.class));
        DynamicLightsEngine.scheduleChunkRebuild(renderer, packed);
        Mixins.set(client, "world", mock(WorldClient.class));
        DynamicLightsEngine.scheduleChunkRebuild(renderer, new BlockPos(1, 2, 3));
        Mockito.verify((com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor) renderer)
                .impetus$markBlocksForUpdate(16, 32, 48, 31, 47, 63, false);
    }

    @Test
    void handlersResolveByTypeAndRespectTheGates() {
        DynamicLightHandlers.registerDefaultHandlers();
        EntityCreeper creeper = mock(EntityCreeper.class);
        when(creeper.getCreeperFlashIntensity(0.0F)).thenReturn(0.5F);
        DynamicLightHandler<EntityCreeper> creeperHandler = DynamicLightHandlers.getDynamicLightHandler(creeper);
        assertNotNull(creeperHandler);
        assertTrue(creeperHandler.isWaterSensitive(creeper));
        assertEquals(5, creeperHandler.getLuminance(creeper));
        DynamicLights.options().creeperLighting = ExplosiveLightingMode.SIMPLE;
        assertEquals(10, creeperHandler.getLuminance(creeper));
        DynamicLights.options().creeperLighting = ExplosiveLightingMode.OFF;
        assertEquals(0, creeperHandler.getLuminance(creeper));
        DynamicLights.options().creeperLighting = ExplosiveLightingMode.FANCY;
        when(creeper.getCreeperFlashIntensity(0.0F)).thenReturn(0.0F);
        assertEquals(0, creeperHandler.getLuminance(creeper));
        // A creeper handler can be combined with another one
        DynamicLightHandler<EntityCreeper> combined = DynamicLightHandler.makeCreeperEntityHandler(entity -> 4);
        assertEquals(4, combined.getLuminance(creeper));

        // A plain handler, and the living-entity wrapper that also counts held items
        DynamicLightHandler<EntityZombie> plain = DynamicLightHandler.makeHandler(zombie -> 3, zombie -> true);
        EntityZombie zombie = mock(EntityZombie.class);
        assertEquals(3, plain.getLuminance(zombie));
        assertTrue(plain.isWaterSensitive(zombie));
        World zombieWorld = clientWorld();
        when(zombieWorld.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        Mixins.set(zombie, "world", zombieWorld);
        when(zombie.getPositionEyes(Mockito.anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0, 0, 0));
        when(zombie.getHeldEquipment()).thenReturn(List.of(new ItemStack(Blocks.GLOWSTONE)));
        when(zombie.getArmorInventoryList()).thenReturn(List.of(ItemStack.EMPTY, new ItemStack(Blocks.TORCH)));
        DynamicLightHandler<EntityZombie> living = DynamicLightHandler.makeLivingEntityHandler(plain);
        assertEquals(14, living.getLuminance(zombie));
        assertFalse(living.isWaterSensitive(zombie));

        // Lookup walks up the hierarchy and memoises, including negative results
        assertNull(DynamicLightHandlers.getDynamicLightHandler(zombie));
        assertNull(DynamicLightHandlers.getDynamicLightHandler(zombie));
        EntityItem item = mock(EntityItem.class);
        when(item.getItem()).thenReturn(new ItemStack(Blocks.GLOWSTONE));
        assertEquals(14, DynamicLightHandlers.getLuminanceFrom((Entity) item));
        // Registering over an existing handler keeps the brighter answer
        DynamicLightHandlers.registerEntityHandler(EntityItem.class, entity -> 2);
        assertEquals(14, DynamicLightHandlers.getLuminanceFrom((Entity) item));

        // The gates: entity toggle, the self switch, the per-type switch and water sensitivity
        DynamicLights.options().entitiesLightSource = false;
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((Entity) item));
        DynamicLights.options().entitiesLightSource = true;
        LightSourceSettings.getInstance().setEntityTypeEnabled("minecraft:item", false);
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((Entity) item));
        LightSourceSettings.getInstance().setEntityTypeEnabled("minecraft:item", true);
        assertTrue(DynamicLightHandlers.canEntityLightUp(item));
        // The client's own player answers to the first-person switch rather than a per-type one
        net.minecraft.client.entity.EntityPlayerSP self = mock(net.minecraft.client.entity.EntityPlayerSP.class);
        Mixins.set(client, "player", self);
        DynamicLights.options().selfLightSource = false;
        assertFalse(DynamicLightHandlers.canEntityLightUp(self));
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((Entity) self));
        DynamicLights.options().selfLightSource = true;
        assertTrue(DynamicLightHandlers.canEntityLightUp(self));
        Mixins.set(client, "player", null);
        assertTrue(DynamicLightHandlers.canEntityLightUp(item));

        // Block entity handlers are opt-in, and gated on their own toggle
        TileEntityChest chest = mock(TileEntityChest.class);
        assertFalse(DynamicLightHandlers.hasTileEntityHandlers());
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((TileEntity) chest));
        DynamicLightHandlers.registerTileEntityHandler(TileEntityChest.class, new DynamicLightHandler<TileEntityChest>() {
            @Override
            public int getLuminance(TileEntityChest tile) {
                return 9;
            }

            @Override
            public boolean isWaterSensitive(TileEntityChest tile) {
                return true;
            }
        });
        assertTrue(DynamicLightHandlers.hasTileEntityHandlers());
        assertTrue(DynamicLightHandlers.canTileEntityLightUp(chest));
        assertEquals(9, DynamicLightHandlers.getLuminanceFrom((TileEntity) chest));
        World world = clientWorld();
        when(chest.getWorld()).thenReturn(world);
        when(world.getBlockState(any())).thenReturn(Blocks.WATER.getDefaultState());
        when(chest.getPos()).thenReturn(BlockPos.ORIGIN);
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((TileEntity) chest));
        DynamicLights.options().blockEntitiesLightSource = false;
        assertEquals(0, DynamicLightHandlers.getLuminanceFrom((TileEntity) chest));
    }

    @Test
    void fluidChecksSeeBlocksAndFluidBlocks() {
        World world = clientWorld();
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        assertFalse(FluidHandler.isFluid(world, BlockPos.ORIGIN));
        when(world.getBlockState(any())).thenReturn(Blocks.WATER.getDefaultState());
        assertTrue(FluidHandler.isFluid(world, BlockPos.ORIGIN));
        Entity entity = mock(Entity.class);
        Mixins.set(entity, "world", world);
        when(entity.getPositionEyes(Mockito.anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0, 0, 0));
        assertTrue(FluidHandler.isFluid(entity));
    }

    @Test
    void itemLightSourcesComeFromJsonOrTheBlockTheyPlace() {
        JsonObject json = new JsonParser().parse(
                "{\"item\":\"minecraft:torch\",\"luminance\":14,\"water_sensitive\":true}").getAsJsonObject();
        ResourceLocation id = new ResourceLocation("impetus", "dynamiclights/item/torch");
        Optional<ItemLightSource> parsed = ItemLightSource.fromJson(id, json);
        assertTrue(parsed.isPresent());
        ItemLightSource torch = parsed.get();
        assertEquals(id, torch.id());
        assertEquals(net.minecraft.item.Item.getItemFromBlock(Blocks.TORCH), torch.item());
        assertTrue(torch.waterSensitive());
        assertTrue(torch.toString().contains("water_sensitive=true"));
        ItemStack stack = new ItemStack(net.minecraft.item.Item.getItemFromBlock(Blocks.TORCH));
        assertEquals(14, torch.getLuminance(stack, false));
        assertEquals(0, torch.getLuminance(stack, true));
        DynamicLights.options().waterSensitiveCheck = false;
        assertEquals(14, torch.getLuminance(stack, true));
        DynamicLights.options().waterSensitiveCheck = true;

        // "block" mirrors the block the item places, and a named block mirrors that one
        ItemLightSource fromBlock = ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"minecraft:glowstone\",\"luminance\":\"block\"}").getAsJsonObject()).orElseThrow();
        assertEquals(15, fromBlock.getLuminance(new ItemStack(Blocks.GLOWSTONE)));
        ItemLightSource named = ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"minecraft:stick\",\"luminance\":\"minecraft:glowstone\"}").getAsJsonObject()).orElseThrow();
        assertEquals(15, named.getLuminance(new ItemStack(Items.STICK)));
        // A stack's BlockStateTag picks the state whose light value is read
        ItemStack torchStack = new ItemStack(Blocks.REDSTONE_TORCH);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound state = new NBTTagCompound();
        state.setString("facing", "up");
        tag.setTag("BlockStateTag", state);
        torchStack.setTagCompound(tag);
        assertEquals(7, ItemLightSource.BlockItemLightSource.getLuminance(torchStack,
                Blocks.REDSTONE_TORCH.getDefaultState()));

        // Malformed definitions are rejected rather than thrown
        assertFalse(ItemLightSource.fromJson(id, new JsonParser().parse("{}").getAsJsonObject()).isPresent());
        assertFalse(ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"nope:nothing\",\"luminance\":3}").getAsJsonObject()).isPresent());
        assertFalse(ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"minecraft:stick\",\"luminance\":\"block\"}").getAsJsonObject()).isPresent());
        assertFalse(ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"minecraft:stick\",\"luminance\":\"not:ablock\"}").getAsJsonObject()).isPresent());
        assertFalse(ItemLightSource.fromJson(id, new JsonParser()
                .parse("{\"item\":\"minecraft:stick\",\"luminance\":true}").getAsJsonObject()).isPresent());

        // The registry: code registrations win over pack ones and are not replaced
        assertEquals(0, ItemLightSources.getLuminance(ItemStack.EMPTY, false));
        assertEquals(15, ItemLightSources.getLuminance(new ItemStack(Blocks.GLOWSTONE), false));
        ItemLightSources.registerItemLightSource(torch);
        assertEquals(14, ItemLightSources.getLuminance(stack, false));
        ItemLightSources.registerItemLightSource(new ItemLightSource.StaticItemLightSource(id, net.minecraft.item.Item.getItemFromBlock(Blocks.TORCH), 3, false));
        assertEquals(14, ItemLightSources.getLuminance(stack, false));
        assertEquals(0, ItemLightSources.getLuminance(new ItemStack(Items.STICK), false));
    }

    @Test
    void itemDefinitionsAreReadFromEveryPack() throws Exception {
        net.minecraft.client.resources.IResourceManager manager =
                mock(net.minecraft.client.resources.IResourceManager.class);
        Mockito.doAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            String json = location.getPath().endsWith("index.json")
                    ? "{\"files\":[\"lantern.json\"]}"
                    : "{\"item\":\"minecraft:glowstone\",\"luminance\":15}";
            net.minecraft.client.resources.IResource resource =
                    mock(net.minecraft.client.resources.IResource.class);
            Mockito.doReturn(new java.io.ByteArrayInputStream(json.getBytes())).when(resource).getInputStream();
            return List.of(resource);
        }).when(manager).getAllResources(any());
        ItemLightSources.load(manager);
        // The index is read and its files loaded; an item no mod provides is simply skipped
        assertEquals(0, ItemLightSources.getLuminance(new ItemStack(Items.STICK), false));

        // A reload listener is registered when the manager supports it
        net.minecraft.client.resources.IReloadableResourceManager reloadable =
                mock(net.minecraft.client.resources.IReloadableResourceManager.class);
        when(client.getResourceManager()).thenReturn(reloadable);
        ItemLightSources.registerReloadListener();
        Mockito.verify(reloadable).registerReloadListener(any());
        // Otherwise the definitions are loaded once, right away
        when(client.getResourceManager()).thenReturn(manager);
        ItemLightSources.registerReloadListener();

        // Unreadable resources are logged rather than thrown
        Mockito.doThrow(new java.io.IOException("no pack")).when(manager).getAllResources(any());
        ItemLightSources.load(manager);
        // As is an index that is not the object it should be
        Mockito.doAnswer(invocation -> {
            net.minecraft.client.resources.IResource resource =
                    mock(net.minecraft.client.resources.IResource.class);
            Mockito.doReturn(new java.io.ByteArrayInputStream("[]".getBytes())).when(resource).getInputStream();
            return List.of(resource);
        }).when(manager).getAllResources(any());
        ItemLightSources.load(manager);
    }

    @Test
    void theSettingsRegistryFiltersAndListsTypes() {
        LightSourceSettings settings = LightSourceSettings.getInstance();
        EntityZombie zombie = mock(EntityZombie.class);
        assertTrue(settings.isEntityEnabled(zombie));
        settings.loadDisabledEntities(new String[] {"minecraft:zombie", "", null});
        assertTrue(settings.isEntityTypeDisabled("minecraft:zombie"));
        assertArrayEquals(new String[] {"minecraft:zombie"}, settings.getDisabledEntitiesArray());
        settings.setEntityTypeEnabled("minecraft:zombie", true);
        assertFalse(settings.isEntityTypeDisabled("minecraft:zombie"));

        TileEntityChest chest = mock(TileEntityChest.class);
        assertTrue(settings.isBlockEntityEnabled(chest));
        settings.loadDisabledBlockEntities(new String[] {"minecraft:chest"});
        assertTrue(settings.isBlockEntityTypeDisabled("minecraft:chest"));
        assertFalse(settings.isBlockEntityEnabled(chest));
        assertArrayEquals(new String[] {"minecraft:chest"}, settings.getDisabledBlockEntitiesArray());
        settings.setBlockEntityTypeEnabled("minecraft:chest", true);
        settings.loadDisabledEntities(new String[0]);
        settings.loadDisabledBlockEntities(new String[0]);

        // Listing types reads the registries; the block entity one is only reachable through the accessor mixin
        assertNotNull(LightSourceSettings.listEntityTypes());
        assertFalse(LightSourceSettings.listBlockEntityTypes().isEmpty());
        assertTrue(LightSourceSettings.listBlockEntityTypes().containsKey("minecraft:chest"));
        // The accessor's own body is the untransformed placeholder, which Mixin replaces in the game
        assertThrows(AssertionError.class,
                com.bdmajora.dynamiclights.mixin.TileEntityRegistryAccessor::impetus$getRegistry);
    }

    @Test
    void theBlockEntityTickerWalksTheWorldOncePerTick() {
        TickEvent.ClientTickEvent start = new TickEvent.ClientTickEvent(TickEvent.Phase.START);
        TickEvent.ClientTickEvent end = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
        Object ticker = TileEntityLightTicker.instance();
        assertSame(ticker, TileEntityLightTicker.instance());
        TileEntityLightTicker instance = (TileEntityLightTicker) ticker;
        // Only the end phase, and only with a block entity handler registered and a world loaded
        instance.onClientTick(start);
        instance.onClientTick(end);
        DynamicLightHandlers.registerTileEntityHandler(TileEntityChest.class, tile -> 5);
        instance.onClientTick(end);
        WorldClient world = mock(WorldClient.class);
        List<TileEntity> tiles = new ArrayList<>();
        TileEntity lit = mock(TileEntityChest.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        tiles.add(lit);
        tiles.add(mock(TileEntity.class));
        Mixins.set(world, "loadedTileEntityList", tiles);
        Mixins.set(client, "world", world);
        instance.onClientTick(end);
        Mockito.verify((DynamicLightSource) lit).impetus$dynamicLightTick();
        // A list that shrinks underneath the walk stops it rather than throwing
        List<TileEntity> shrinking = mock(List.class);
        when(shrinking.size()).thenReturn(4);
        when(shrinking.get(0)).thenThrow(new IndexOutOfBoundsException());
        Mixins.set(world, "loadedTileEntityList", shrinking);
        instance.onClientTick(end);
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        instance.onClientTick(end);
    }
}
