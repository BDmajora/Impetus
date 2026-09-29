package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.culling.EntityCullableMixin;
import com.bdmajora.extras.mixin.culling.RenderManagerCullMixin;
import com.bdmajora.extras.mixin.culling.TileEntityCullableMixin;
import com.bdmajora.extras.mixin.culling.TileEntityRendererDispatcherCullMixin;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CullingTest {
    private ExtrasConfig config;
    private Minecraft client;
    private WorldClient world;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void wallWorld() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        world = mock(WorldClient.class);
        // Every chunk has a stone plane where x & 15 == 5, the whole height; the camera stands at x 0.5 between two planes
        ExtendedBlockStorage section = new ExtendedBlockStorage(0, true);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                section.set(5, y, z, Blocks.STONE.getDefaultState());
            }
        }
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        java.util.Arrays.fill(sections, section);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        ChunkProviderClient provider = mock(ChunkProviderClient.class);
        when(provider.getLoadedChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(world.getChunkProvider()).thenReturn(provider);
        Mixins.set(world, "loadedEntityList", new ArrayList<Entity>());
        Mixins.set(world, "loadedTileEntityList", new ArrayList<TileEntity>());
        // A thread object that is never started, so the hooks do not spin one up
        Mixins.set(OcclusionCullingThread.class, "instance", Mixins.construct(OcclusionCullingThread.class));
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
    }

    @Test
    void theCellCacheAnswersEachCellOnce() {
        CellVisibilityCache cache = new CellVisibilityCache(2);
        AtomicInteger asked = new AtomicInteger();
        assertTrue(cache.isVisible(0, 0, 0, () -> asked.incrementAndGet() > 0));
        assertTrue(cache.isVisible(0, 0, 0, () -> asked.incrementAndGet() > 0));
        assertFalse(cache.isVisible(1, 1, 1, () -> asked.incrementAndGet() < 0));
        assertFalse(cache.isVisible(1, 1, 1, () -> asked.incrementAndGet() < 0));
        assertEquals(2, asked.get());
        // Outside the cached cube every question is asked
        cache.isVisible(5, 0, 0, () -> asked.incrementAndGet() > 0);
        cache.isVisible(-5, 0, 0, () -> asked.incrementAndGet() > 0);
        assertEquals(4, asked.get());
        cache.clear();
        cache.isVisible(0, 0, 0, () -> asked.incrementAndGet() > 0);
        assertEquals(5, asked.get());
    }

    @Test
    void raysStopAtOpaqueBlocksButNotAtTheLastStretch() {
        OcclusionRaycaster raycaster = new OcclusionRaycaster(world);
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 3.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(10.5, 64.5, 0.5, 0.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 70.5, 3.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 58.5, -3.5, 1.0));
        // The block the target is in never hides it
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 5.5, 64.5, 0.5, 1.0));
        // A degenerate ray, a ray along one axis, and one outside the world's height
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 0.5, 64.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 0.5, 80.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, 300.5, 0.5, 3.5, 300.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, -10.5, 0.5, 3.5, -10.5, 0.5, 0.0));
        // A ray that never ends is stopped by its guard
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 0.5, 64.5, 5000.5, 0.0));
        // Missing chunks and empty sections are not occluders
        ChunkProviderClient empty = mock(ChunkProviderClient.class);
        WorldClient unloaded = mock(WorldClient.class);
        when(unloaded.getChunkProvider()).thenReturn(empty);
        assertTrue(new OcclusionRaycaster(unloaded).isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 0.0));
        Chunk hollow = mock(Chunk.class);
        when(hollow.getBlockStorageArray()).thenReturn(new ExtendedBlockStorage[16]);
        when(empty.getLoadedChunk(anyInt(), anyInt())).thenReturn(hollow);
        assertTrue(new OcclusionRaycaster(unloaded).isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 0.0));
    }

    private EntityPig pig(double x, double y, double z) {
        EntityPig pig = Mc.mock(EntityPig.class, Cullable.class);
        Mixins.set(pig, "posX", x);
        Mixins.set(pig, "posY", y);
        Mixins.set(pig, "posZ", z);
        Mixins.set(pig, "width", 0.9F);
        Mixins.set(pig, "height", 0.9F);
        when(pig.isNonBoss()).thenReturn(true);
        when(pig.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(x - 0.45, y, z - 0.45, x + 0.45, y + 0.9, z + 0.45));
        return pig;
    }

    private static TileEntity chest(BlockPos pos) {
        TileEntity chest = Mc.mock(TileEntityChest.class, Cullable.class);
        when(chest.getPos()).thenReturn(pos);
        when(chest.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(pos));
        return chest;
    }

    private static boolean occluded(Object target) {
        return org.mockito.Mockito.mockingDetails(target).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("impetus$setOccluded") && (boolean) invocation.getArgument(0));
    }

    @Test
    void aPassMarksWhatTheWallHidesAndLeavesTheRestDrawn() {
        Entity view = mock(Entity.class);
        Mixins.set(view, "posX", 0.5);
        Mixins.set(view, "posY", 64.0);
        Mixins.set(view, "posZ", 0.5);
        when(view.getLook(anyFloat())).thenReturn(new Vec3d(1, 0, 0));
        List<Entity> entities = world.loadedEntityList;
        EntityPig hidden = pig(10, 64, 0.5);
        EntityPig inFront = pig(3, 64, 0.5);
        EntityPig behind = pig(-10, 64, 0.5);
        EntityPig tooFar = pig(200, 64, 0.5);
        EntityPig tooNear = pig(1, 64, 0.5);
        EntityPig boss = pig(10, 64, 0.5);
        when(boss.isNonBoss()).thenReturn(false);
        EntityPig huge = pig(10, 64, 0.5);
        Mixins.set(huge, "width", 8.0F);
        // A render box vanilla left unset falls back to the collision box
        EntityPig odd = pig(10, 64, 0.5);
        when(odd.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(Double.NaN, 0, 0, 0, 0, 0));
        // Standing inside the box counts as seeing it
        EntityPig around = pig(10, 64, 0.5);
        when(around.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(-1, 60, -1, 12, 70, 2));
        // One behind the wall seen from above, and one below, reach the up and down faces
        EntityPig overhead = pig(10, 60, 0.5);
        EntityPig underfoot = pig(10, 68, 0.5);
        EntityPig aside = pig(10, 64, 6.5);
        EntityPig asideNeg = pig(10, 64, -6.5);
        entities.addAll(List.of(hidden, inFront, behind, tooFar, tooNear, boss, huge, odd, around, overhead, underfoot, aside, asideNeg, view));
        entities.add(null);

        List<TileEntity> blockEntities = world.loadedTileEntityList;
        TileEntity hiddenChest = chest(new BlockPos(10, 64, 0));
        TileEntity nearChest = chest(new BlockPos(3, 64, 0));
        TileEntity behindChest = chest(new BlockPos(-10, 64, 0));
        TileEntity farChest = chest(new BlockPos(200, 64, 0));
        TileEntity invalid = chest(new BlockPos(10, 64, 0));
        when(invalid.isInvalid()).thenReturn(true);
        TileEntity infinite = chest(new BlockPos(10, 64, 0));
        when(infinite.getRenderBoundingBox()).thenReturn(TileEntity.INFINITE_EXTENT_AABB);
        TileEntity big = chest(new BlockPos(10, 64, 0));
        when(big.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(10, 64, 0, 20, 65, 1));
        blockEntities.addAll(List.of(hiddenChest, nearChest, behindChest, farChest, invalid, infinite, big));
        blockEntities.add(null);

        OcclusionCullingThread thread = Mixins.construct(OcclusionCullingThread.class);
        Mixins.call(thread, "pass", world, view, config.occlusion);
        assertTrue(occluded(hidden));
        assertTrue(occluded(odd));
        for (Entity visible : List.of(inFront, behind, tooFar, tooNear, boss, huge, around)) {
            assertFalse(occluded(visible));
        }
        assertTrue(occluded(hiddenChest));
        for (TileEntity visible : List.of(nearChest, behindChest, farChest, invalid, infinite, big)) {
            assertFalse(occluded(visible));
        }
        assertTrue(OcclusionCullingThread.lastEntitiesCulled >= 2);
        assertEquals(1, OcclusionCullingThread.lastBlockEntitiesCulled);

        // A second pass on the same world keeps its raycaster; with both kinds off nothing is walked
        config.occlusion.entities = false;
        config.occlusion.blockEntities = false;
        Mixins.call(thread, "pass", world, view, config.occlusion);
        assertEquals(0, OcclusionCullingThread.lastEntitiesCulled);

        // The thread's loop runs a pass and stops when interrupted
        Mixins.set(client, "world", world);
        when(client.getRenderViewEntity()).thenReturn(view);
        config.occlusion.entities = true;
        Thread.currentThread().interrupt();
        thread.run();
        assertFalse(Thread.interrupted());
        assertTrue(OcclusionCulling.status().startsWith("Occlusion: E "));
        Mixins.set(client, "world", null);
        assertEquals("", OcclusionCulling.status());
    }

    @Test
    void theHooksSkipOnlyFreshVerdictsOutsideTheShadowPass() {
        EntityCullableMixin entity = Mixins.instance(EntityCullableMixin.class);
        TileEntityCullableMixin blockEntity = Mixins.instance(TileEntityCullableMixin.class);
        RenderManagerCullMixin renderManager = Mixins.instance(RenderManagerCullMixin.class);
        TileEntityRendererDispatcherCullMixin dispatcher = Mixins.instance(TileEntityRendererDispatcherCullMixin.class);

        assertFalse(OcclusionCulling.shouldSkipEntity((Entity) (Object) entity));
        entity.impetus$setOccluded(true, System.nanoTime());
        blockEntity.impetus$setOccluded(true, System.nanoTime());
        assertTrue(entity.impetus$isOccluded());
        assertTrue(blockEntity.impetus$isOccluded());
        assertTrue(entity.impetus$occlusionStamp() > 0);
        assertTrue(blockEntity.impetus$occlusionStamp() > 0);

        CallbackInfoReturnable<Boolean> render = Mixins.cir();
        Mixins.call(renderManager, "impetus$skipOccluded", entity, null, 0.0D, 0.0D, 0.0D, render);
        assertEquals(false, render.getReturnValue());
        CallbackInfo tile = Mixins.ci();
        Mixins.call(dispatcher, "impetus$skipOccluded", blockEntity, 0.0F, -1, tile);
        assertTrue(tile.isCancelled());

        // A stale verdict is not trusted, and the shadow pass draws everything
        entity.impetus$setOccluded(true, System.nanoTime() - 5_000_000_000L);
        assertFalse(OcclusionCulling.shouldSkipEntity((Entity) (Object) entity));
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertFalse(OcclusionCulling.shouldSkipBlockEntity((TileEntity) (Object) blockEntity));
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        // Switched off, nothing is skipped
        config.occlusion.entities = false;
        config.occlusion.blockEntities = false;
        CallbackInfoReturnable<Boolean> off = Mixins.cir();
        Mixins.call(renderManager, "impetus$skipOccluded", entity, null, 0.0D, 0.0D, 0.0D, off);
        assertFalse(off.isCancelled());
        CallbackInfo tileOff = Mixins.ci();
        Mixins.call(dispatcher, "impetus$skipOccluded", blockEntity, 0.0F, -1, tileOff);
        assertFalse(tileOff.isCancelled());
        assertNotNull(Mixins.construct(OcclusionCulling.class));
    }
}
