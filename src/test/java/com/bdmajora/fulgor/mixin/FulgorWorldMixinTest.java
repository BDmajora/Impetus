package com.bdmajora.fulgor.mixin;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.api.LightUpdateProcessor;
import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.api.SectionLightInfo;
import com.bdmajora.fulgor.lighting.LightingEngine;
import com.bdmajora.fulgor.lighting.NeighborLightFlags;
import com.bdmajora.fulgor.mixin.client.BlockLightmapMixin;
import com.bdmajora.fulgor.mixin.client.ChunkCacheNeighborLightMixin;
import com.bdmajora.fulgor.mixin.client.MinecraftMixin;
import com.bdmajora.fulgor.mixin.client.RenderGlobalMixin;
import com.bdmajora.fulgor.mixin.client.WorldNeighborLightMixin;
import com.bdmajora.fulgor.mixin.network.SPacketChunkDataMixin;
import com.bdmajora.fulgor.mixin.world.AnvilChunkLoaderMixin;
import com.bdmajora.fulgor.mixin.world.ChunkProviderServerMixin;
import com.bdmajora.fulgor.mixin.world.ChunkSkylightMixin;
import com.bdmajora.fulgor.mixin.world.ExtendedBlockStorageMixin;
import com.bdmajora.fulgor.mixin.world.WorldMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FulgorWorldMixinTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
    }

    @Test
    void thePluginGatesEveryMixinOnItsSwitch() {
        FulgorMixinPlugin plugin = new FulgorMixinPlugin();
        plugin.onLoad("com.bdmajora.fulgor.mixin");
        // The async engine is the default, so the deferred engine's mixins stay out
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.async.world.WorldMixin"));
        assertFalse(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.WorldMixin"));
        assertFalse(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.AnvilChunkLoaderMixin"));
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.client.MinecraftMixin"));
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.ExtendedBlockStorageMixin"));
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.block.BlockMixin"));
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.client.RenderGlobalMixin"));
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.client.BlockLightmapMixin"));
        // An unlisted mixin is applied with a warning rather than silently dropped
        assertTrue(plugin.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.something.New"));
        assertTrue(plugin.shouldApplyMixin("", "some.other.Mixin"));

        // With the async engine off the deferred one's mixins apply instead
        FulgorConfig.get().asyncLightUpdates = false;
        FulgorMixinPlugin deferred = new FulgorMixinPlugin();
        deferred.onLoad("com.bdmajora.fulgor.mixin");
        assertTrue(deferred.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.WorldMixin"));
        assertTrue(deferred.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.AnvilChunkLoaderMixin"));
        assertFalse(deferred.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.async.world.ChunkMixin"));

        // Switched off entirely, nothing applies at all
        FulgorConfig.get().enabled = false;
        FulgorMixinPlugin off = new FulgorMixinPlugin();
        off.onLoad("com.bdmajora.fulgor.mixin");
        assertFalse(off.shouldApplyMixin("", "com.bdmajora.fulgor.mixin.world.WorldMixin"));
        assertFalse((boolean) Mixins.call(FulgorMixinPlugin.class, "isClassPresent", "no.such.Class"));
        assertTrue((boolean) Mixins.call(FulgorMixinPlugin.class, "isClassPresent", "java.lang.String"));
    }

    @Test
    void theWorldOwnsAnEngineAndDefersEveryCheck() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        World self = (World) (Object) world;
        Mixins.set(self, "isRemote", false);
        Mixins.set(self, "profiler", new Profiler());
        WorldProvider dimension = mock(WorldProvider.class);
        Mixins.set(self, "provider", dimension);
        Mixins.call(world, "fulgor$createLightingEngine", Mixins.ci());
        LightingEngine engine = world.fulgor$getLightingEngine();
        assertNotNull(engine);

        var cir = Mixins.<Boolean>cir();
        Mixins.call(world, "fulgor$scheduleLightUpdate", EnumSkyBlock.BLOCK, BlockPos.ORIGIN, cir);
        assertTrue(cir.getReturnValue());
        assertTrue(cir.isCancelled());
    }

    @Test
    void aSectionWithOnlyLightIsNoLongerEmpty() {
        ExtendedBlockStorageMixin section = Mixins.instance(ExtendedBlockStorageMixin.class);
        NibbleArray sky = new NibbleArray();
        java.util.Arrays.fill(sky.getData(), (byte) 0xFF);
        NibbleArray block = new NibbleArray();
        Mixins.set(section, "skyLight", sky);
        Mixins.set(section, "blockLight", block);
        Mixins.set(section, "blockRefCount", 0);
        // Full skylight and no block light is what an untouched section looks like
        assertTrue(section.isEmpty());
        assertTrue(section.fulgor$hasNoBlocks());
        // Carving light into it makes it worth sending, though it still holds no blocks
        section.setBlockLight(1, 2, 3, 7);
        assertFalse(section.isEmpty());
        assertTrue(section.fulgor$hasNoBlocks());
        section.setSkyLight(1, 2, 3, 0);
        assertFalse(section.isEmpty());
        // Replacing the arrays invalidates the cached answer as well
        section.setSkyLight(sky);
        section.setBlockLight(new NibbleArray());
        assertFalse(section.isEmpty());
        section.setSkyLight(new NibbleArray());
        assertFalse(section.isEmpty());
        // A section with blocks is never empty
        Mixins.set(section, "blockRefCount", 3);
        assertFalse(section.isEmpty());
        assertFalse(section.fulgor$hasNoBlocks());
    }

    @Test
    void savingAndUnloadingFlushWhatIsPending() {
        LightingEngine engine = mock(LightingEngine.class);
        WorldServer server = Mc.mock(WorldServer.class, LightingEngineProvider.class);
        when(((LightingEngineProvider) server).fulgor$getLightingEngine()).thenReturn(engine);
        ChunkProviderServerMixin provider = Mixins.instance(ChunkProviderServerMixin.class);
        Mixins.set(provider, "world", server);
        Mixins.call(provider, "fulgor$flushBeforeSave", true, Mixins.cir());
        Mixins.call(provider, "fulgor$flushBeforeUnload", Mixins.cir());
        Mockito.verify(engine, Mockito.times(2)).processLightUpdates();

        // The chunk loader flushes too, and carries Fulgor's two pieces of lighting state through NBT
        World world = Mc.mock(World.class, LightingEngineProvider.class);
        when(((LightingEngineProvider) world).fulgor$getLightingEngine()).thenReturn(engine);
        AnvilChunkLoaderMixin loader = Mixins.instance(AnvilChunkLoaderMixin.class);
        Chunk chunk = Mc.mock(Chunk.class, ChunkLightingData.class);
        Mixins.call(loader, "fulgor$flushBeforeSave", world, chunk, Mixins.ci());
        Mockito.verify(engine, Mockito.times(3)).processLightUpdates();

        short[] flags = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        flags[1] = 5;
        when(((ChunkLightingData) chunk).fulgor$getNeighborLightChecks()).thenReturn(flags);
        when(((ChunkLightingData) chunk).fulgor$isLightInitialized()).thenReturn(true);
        NBTTagCompound compound = new NBTTagCompound();
        Mixins.call(loader, "fulgor$writeLightingState", chunk, world, compound, Mixins.ci());
        assertTrue(compound.getBoolean("LightPopulated"));
        assertTrue(compound.hasKey(NeighborLightFlags.NBT_KEY, 9));

        Chunk loaded = Mc.mock(Chunk.class, ChunkLightingData.class);
        short[] restored = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        when(((ChunkLightingData) loaded).fulgor$getNeighborLightChecks()).thenReturn(restored);
        Mixins.call(loader, "fulgor$readLightingState", world, compound, Mixins.cir(loaded));
        assertEquals(5, restored[1]);
        Mockito.verify((ChunkLightingData) loaded).fulgor$setLightInitialized(true);

        // The chunk packet flushes before it copies the section light out
        Chunk packetChunk = Mc.mock(Chunk.class, LightingEngineProvider.class);
        when(((LightingEngineProvider) packetChunk).fulgor$getLightingEngine()).thenReturn(engine);
        Mixins.call(Mixins.instance(SPacketChunkDataMixin.class), "fulgor$flushBeforeSerialize",
                packetChunk, true, 0xFFFF, Mixins.cir());
        Mockito.verify(engine, Mockito.times(4)).processLightUpdates();
    }

    @Test
    void settingABlockNoLongerDoesVanillasSkylightWork() {
        ChunkSkylightMixin chunk = Mixins.instance(ChunkSkylightMixin.class);
        World world = mock(World.class);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(false);
        Mixins.set(world, "provider", dimension);
        Mixins.set(chunk, "world", world);
        ExtendedBlockStorage section = new ExtendedBlockStorage(64, true);
        assertSame(section, Mixins.call(chunk, "fulgor$seedNewSection", section));
        assertFalse((boolean) Mixins.call(chunk, "fulgor$suppressSkylightMapRegeneration", true));
        Mixins.call(chunk, "fulgor$skipSkylightOcclusion", null, 0, 0);
        assertEquals(0, (int) Mixins.call(chunk, "fulgor$skipLightQuery", null, EnumSkyBlock.SKY, BlockPos.ORIGIN));
    }

    @Test
    void theClientTickDrainsBothStagesInOrder() {
        MinecraftMixin client = Mixins.instance(MinecraftMixin.class);
        Mixins.set(client, "profiler", new Profiler());
        RenderGlobal renderer = Mc.mock(RenderGlobal.class, LightUpdateProcessor.class);
        Mixins.set(client, "renderGlobal", renderer);
        // With no world there is nothing to flush
        Mixins.call(client, "fulgor$processWorldLightUpdates", Mixins.ci());
        WorldClient world = Mc.mock(WorldClient.class, LightingEngineProvider.class);
        LightingEngine engine = mock(LightingEngine.class);
        when(((LightingEngineProvider) world).fulgor$getLightingEngine()).thenReturn(engine);
        Mixins.set(client, "world", world);
        Mixins.call(client, "fulgor$processWorldLightUpdates", Mixins.ci());
        Mockito.verify(engine).processLightUpdates();
        Mixins.call(client, "fulgor$processRenderLightUpdates", Mixins.ci());
        Mockito.verify((LightUpdateProcessor) renderer).fulgor$processLightUpdates();

        // A paused game skips both stages while the option is on
        Mixins.set(client, "isGamePaused", true);
        Mixins.call(client, "fulgor$processWorldLightUpdates", Mixins.ci());
        Mixins.call(client, "fulgor$processRenderLightUpdates", Mixins.ci());
        Mockito.verify(engine, Mockito.times(1)).processLightUpdates();
        FulgorConfig.get().skipUpdatesWhilePaused = false;
        Mixins.call(client, "fulgor$processWorldLightUpdates", Mixins.ci());
        Mockito.verify(engine, Mockito.times(2)).processLightUpdates();
    }

    @Test
    void theRendererDrainsItsOwnLightQueue() {
        RenderGlobalMixin renderer = Mixins.instance(RenderGlobalMixin.class);
        renderer.notifyLightSet(new BlockPos(5, 64, 7));
        renderer.notifyLightSet(new BlockPos(5, 64, 7));
        assertTrue((boolean) Mixins.call(renderer, "fulgor$disableVanillaDrain", java.util.Set.of()));
        renderer.fulgor$processLightUpdates();
        Mixins.verifyCall(renderer, "markBlocksForUpdate", 4, 63, 6, 6, 65, 8, false);
        // The same position may be queued again once the pass is over
        renderer.notifyLightSet(new BlockPos(5, 64, 7));
        renderer.fulgor$processLightUpdates();
    }

    @Test
    void neighbourBrightnessIsFaceAwareOnTheClient() {
        WorldNeighborLightMixin world = Mixins.instance(WorldNeighborLightMixin.class);
        World self = (World) (Object) world;
        Mixins.set(self, "isRemote", true);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(self, "provider", dimension);
        BlockPos pos = new BlockPos(0, 64, 0);
        Mockito.doReturn(Blocks.STONE_SLAB.getDefaultState()).when(world).getBlockState(any());
        Mockito.doReturn(true).when(world).isValid(any());
        Mockito.doReturn(true).when(world).isBlockLoaded(any());
        Mockito.doReturn(0).when(world).getLightFor(any(), any());
        Mockito.doReturn(12).when(world).getLightFor(EnumSkyBlock.BLOCK, pos.up());
        var lit = Mixins.<Integer>cir();
        Mixins.call(world, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, pos, lit);
        assertEquals(11, lit.getReturnValue());

        // Outside the world, in an unloaded chunk, or in a dimension without sky, the answer is fixed
        var unloaded = Mixins.<Integer>cir();
        Mockito.doReturn(false).when(world).isBlockLoaded(any());
        Mixins.call(world, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, pos, unloaded);
        assertEquals(0, unloaded.getReturnValue());
        var belowWorld = Mixins.<Integer>cir();
        Mixins.call(world, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, new BlockPos(0, -5, 0), belowWorld);
        assertEquals(0, belowWorld.getReturnValue());
        when(dimension.hasSkyLight()).thenReturn(false);
        var noSky = Mixins.<Integer>cir();
        Mixins.call(world, "fulgor$faceAwareNeighborLight", EnumSkyBlock.SKY, pos, noSky);
        assertEquals(0, noSky.getReturnValue());
        // The server keeps vanilla's answer
        Mixins.set(self, "isRemote", false);
        var server = Mixins.<Integer>cir();
        Mixins.call(world, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, pos, server);
        assertFalse(server.isCancelled());
    }

    @Test
    void theChunkCacheAndLightmapFollowTheSameRule() {
        ChunkCacheNeighborLightMixin cache = Mixins.instance(ChunkCacheNeighborLightMixin.class);
        World world = mock(World.class);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        Mixins.set(cache, "world", world);
        BlockPos pos = new BlockPos(0, 64, 0);
        Mockito.doReturn(Blocks.STONE_SLAB.getDefaultState()).when(cache).getBlockState(any());
        Mockito.doReturn(0).when(cache).getLightFor(any(), any());
        Mockito.doReturn(10).when(cache).getLightFor(EnumSkyBlock.BLOCK, pos.up());
        var lit = Mixins.<Integer>cir();
        Mixins.call(cache, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, pos, lit);
        assertEquals(9, lit.getReturnValue());
        // A block that is lit at its own position is left to vanilla
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(cache).getBlockState(any());
        var plain = Mixins.<Integer>cir();
        Mixins.call(cache, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, pos, plain);
        assertFalse(plain.isCancelled());
        var outside = Mixins.<Integer>cir();
        Mixins.call(cache, "fulgor$faceAwareNeighborLight", EnumSkyBlock.BLOCK, new BlockPos(0, 300, 0), outside);
        assertEquals(0, outside.getReturnValue());
        when(dimension.hasSkyLight()).thenReturn(false);
        var noSky = Mixins.<Integer>cir();
        Mixins.call(cache, "fulgor$faceAwareNeighborLight", EnumSkyBlock.SKY, pos, noSky);
        assertEquals(0, noSky.getReturnValue());

        // The lightmap no longer samples the block below a slab
        BlockLightmapMixin lightmap = Mixins.instance(BlockLightmapMixin.class);
        net.minecraft.world.IBlockAccess source = mock(net.minecraft.world.IBlockAccess.class);
        when(source.getCombinedLight(any(), Mockito.anyInt())).thenReturn(0xF000F0);
        var coords = Mixins.<Integer>cir();
        Mixins.call(lightmap, "fulgor$plainLightmapCoords", Blocks.STONE_SLAB.getDefaultState(), source, pos, coords);
        assertEquals(0xF000F0, coords.getReturnValue());
    }
}
