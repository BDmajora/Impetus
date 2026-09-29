package com.bdmajora.equilibrium.common;

import com.bdmajora.equilibrium.common.entity.EntityTickBudget;
import com.bdmajora.equilibrium.common.entity.ReducedRadiusQuery;
import com.bdmajora.equilibrium.common.hopper.HopperEntityLookup;
import com.bdmajora.equilibrium.common.hopper.HopperInventoryCache;
import com.bdmajora.equilibrium.common.world.ChunkAccess;
import com.bdmajora.equilibrium.common.world.ChunkSectionCursor;
import com.bdmajora.equilibrium.common.world.FastRayCaster;
import com.bdmajora.equilibrium.common.world.InventoryEntityTracker;
import com.bdmajora.equilibrium.common.world.TileEntityAccess;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquilibriumWorldTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void detachServer() {
        Mixins.set(FMLCommonHandler.instance(), "sidedDelegate", null);
        World.MAX_ENTITY_RADIUS = 2.0D;
    }

    // A world whose chunk (0,0) holds one real section with the given block at (x, y, z)
    private static World worldWith(IBlockState state, int x, int y, int z, boolean withAccess) {
        World world = withAccess ? Mc.mock(World.class, ChunkAccess.class) : mock(World.class);
        when(world.getWorldType()).thenReturn(WorldType.DEFAULT);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        ExtendedBlockStorage section = new ExtendedBlockStorage(y >> 4 << 4, true);
        section.set(x & 15, y & 15, z & 15, state);
        sections[y >> 4] = section;
        Chunk chunk = mock(Chunk.class);
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        if (withAccess) {
            ChunkAccess access = (ChunkAccess) world;
            when(access.equilibrium$getChunkCached(x >> 4, z >> 4)).thenReturn(chunk);
            when(access.equilibrium$getLoadedChunk(x >> 4, z >> 4)).thenReturn(chunk);
        }
        when(world.getBlockState(any())).thenAnswer(inv -> {
            BlockPos pos = inv.getArgument(0);
            return pos.getX() == x && pos.getY() == y && pos.getZ() == z ? state : Blocks.AIR.getDefaultState();
        });
        return world;
    }

    @Test
    void cursorReadsSectionsDirectlyOrFallsBackToTheWorld() {
        IBlockState stone = Blocks.STONE.getDefaultState();
        World world = worldWith(stone, 3, 20, 5, true);
        ChunkSectionCursor loading = new ChunkSectionCursor(world, true);
        assertSame(stone, loading.getBlockState(3, 20, 5));
        assertSame(stone, loading.getBlockState(3, 20, 5));
        assertEquals(Blocks.AIR.getDefaultState(), loading.getBlockState(4, 20, 5));
        assertEquals(Blocks.AIR.getDefaultState(), loading.getBlockState(3, 40, 5));
        assertEquals(Blocks.AIR.getDefaultState(), loading.getBlockState(3, -1, 5));
        assertEquals(Blocks.AIR.getDefaultState(), loading.getBlockState(3, 256, 5));
        // A neighbouring chunk is not loaded, and reading it never touches the world
        assertEquals(Blocks.AIR.getDefaultState(), loading.getBlockState(30, 20, 5));
        Mockito.verify(world, Mockito.never()).getBlockState(any());
        ChunkSectionCursor nonLoading = new ChunkSectionCursor(world, false);
        assertSame(stone, nonLoading.getBlockState(3, 20, 5));
        assertEquals(Blocks.AIR.getDefaultState(), nonLoading.getBlockState(3, 300, 5));

        // The null section sentinel is treated as empty
        ExtendedBlockStorage[] sections = ((ChunkAccess) world).equilibrium$getLoadedChunk(0, 0).getBlockStorageArray();
        sections[2] = Chunk.NULL_BLOCK_STORAGE;
        assertEquals(Blocks.AIR.getDefaultState(), new ChunkSectionCursor(world, true).getBlockState(3, 40, 5));

        // Without chunk access or in the debug world the plain lookup is used
        World plain = worldWith(stone, 3, 20, 5, false);
        assertSame(stone, new ChunkSectionCursor(plain, true).getBlockState(3, 20, 5));
        Mockito.verify(plain).getBlockState(new BlockPos(3, 20, 5));
        World debug = worldWith(stone, 3, 20, 5, true);
        when(debug.getWorldType()).thenReturn(WorldType.DEBUG_ALL_BLOCK_STATES);
        assertSame(stone, new ChunkSectionCursor(debug, true).getBlockState(3, 20, 5));
        Mockito.verify(debug).getBlockState(new BlockPos(3, 20, 5));
    }

    @Test
    void rayCasterStepsThroughBlocksLikeVanilla() {
        IBlockState stone = Blocks.STONE.getDefaultState();
        World world = worldWith(stone, 3, 5, 3, true);
        RayTraceResult hit = FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 5.5, 0.5), new Vec3d(6.5, 5.5, 6.5), false, false, false);
        assertNotNull(hit);
        assertEquals(RayTraceResult.Type.BLOCK, hit.typeOfHit);
        assertEquals(new BlockPos(3, 5, 3), hit.getBlockPos());
        // Starting inside the block hits it immediately
        RayTraceResult inside = FastRayCaster.rayTraceBlocks(world, new Vec3d(3.5, 5.5, 3.5), new Vec3d(6.5, 5.5, 6.5), false, false, false);
        assertNotNull(inside);
        assertEquals(new BlockPos(3, 5, 3), inside.getBlockPos());
        // Empty space gives nothing, or the last air position when asked for it
        assertNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 9.5, 0.5), new Vec3d(4.5, 9.5, 4.5), false, false, false));
        RayTraceResult miss = FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 9.5, 0.5), new Vec3d(4.5, 9.5, 4.5), false, false, true);
        assertNotNull(miss);
        assertEquals(RayTraceResult.Type.MISS, miss.typeOfHit);
        assertEquals(new BlockPos(4, 9, 4), miss.getBlockPos());
        assertNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 9.5, 0.5), new Vec3d(0.5, 9.5, 0.5), false, false, true));
        // Blocks without a collision box are skipped when asked, air included
        assertNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 9.5, 0.5), new Vec3d(4.5, 9.5, 4.5), false, true, false));
        assertNotNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 5.5, 0.5), new Vec3d(6.5, 5.5, 6.5), false, true, false));
        // Every axis direction steps correctly
        assertNotNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(6.5, 5.5, 3.5), new Vec3d(0.5, 5.5, 3.5), false, false, false));
        assertNotNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(3.5, 5.5, 6.5), new Vec3d(3.5, 5.5, 0.5), false, false, false));
        assertNotNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(3.5, 0.5, 3.5), new Vec3d(3.5, 8.5, 3.5), false, false, false));
        assertNotNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(3.5, 8.5, 3.5), new Vec3d(3.5, 0.5, 3.5), false, false, false));
        assertNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(Double.NaN, 5.5, 0.5), new Vec3d(6.5, 5.5, 6.5), false, false, false));
        assertNull(FastRayCaster.rayTraceBlocks(world, new Vec3d(0.5, 5.5, 0.5), new Vec3d(6.5, Double.NaN, 6.5), false, false, false));
        // Liquids only stop the ray when asked
        World water = worldWith(Blocks.WATER.getDefaultState(), 3, 5, 3, true);
        assertNull(FastRayCaster.rayTraceBlocks(water, new Vec3d(0.5, 5.5, 3.5), new Vec3d(6.5, 5.5, 3.5), false, false, false));
        assertNotNull(FastRayCaster.rayTraceBlocks(water, new Vec3d(0.5, 5.5, 3.5), new Vec3d(6.5, 5.5, 3.5), true, false, false));
        RayTraceResult through = FastRayCaster.rayTraceBlocks(water, new Vec3d(0.5, 5.5, 3.5), new Vec3d(6.5, 5.5, 3.5), false, false, true);
        assertEquals(RayTraceResult.Type.MISS, through.typeOfHit);
        assertEquals(EnumFacing.WEST, through.sideHit);
        // A shared cursor can be handed in for many rays
        ChunkSectionCursor cursor = new ChunkSectionCursor(world, true);
        assertNotNull(FastRayCaster.trace(world, cursor, new Vec3d(0.5, 5.5, 0.5), new Vec3d(6.5, 5.5, 6.5), false, false, false));
        assertNull(FastRayCaster.trace(world, cursor, new Vec3d(0.5, 9.5, 0.5), new Vec3d(4.5, 9.5, 4.5), false, false, false));
    }

    @Test
    void tickBudgetOnlyThrottlesFarOrdinaryEntitiesOnSlowTicks() {
        World world = mock(World.class);
        Mixins.set(world, "isRemote", false);
        Mixins.set(world, "playerEntities", new ArrayList<EntityPlayer>());
        when(world.getTotalWorldTime()).thenReturn(2L);
        EntityZombie zombie = mock(EntityZombie.class);
        when(zombie.isNonBoss()).thenReturn(true);
        // No server at all, or a server that is keeping up, means nothing is skipped
        IFMLSidedHandler headless = mock(IFMLSidedHandler.class);
        Mixins.set(FMLCommonHandler.instance(), "sidedDelegate", headless);
        assertFalse(EntityTickBudget.shouldSkip(world, zombie));
        MinecraftServer server = mock(MinecraftServer.class);
        Mixins.set(server, "tickTimeArray", new long[100]);
        when(server.getTickCounter()).thenReturn(1);
        IFMLSidedHandler delegate = mock(IFMLSidedHandler.class);
        when(delegate.getServer()).thenReturn(server);
        Mixins.set(FMLCommonHandler.instance(), "sidedDelegate", delegate);
        assertFalse(EntityTickBudget.shouldSkip(world, zombie));
        server.tickTimeArray[0] = 60_000_000L;
        assertTrue(EntityTickBudget.shouldSkip(world, zombie));
        // Odd ticks and client worlds are left alone
        when(world.getTotalWorldTime()).thenReturn(3L);
        assertFalse(EntityTickBudget.shouldSkip(world, zombie));
        when(world.getTotalWorldTime()).thenReturn(2L);
        Mixins.set(world, "isRemote", true);
        assertFalse(EntityTickBudget.shouldSkip(world, zombie));
        Mixins.set(world, "isRemote", false);
        // Players, mounts, bosses and non-living things are never throttled
        assertFalse(EntityTickBudget.shouldSkip(world, mock(EntityPlayerMP.class)));
        EntityZombie ridden = mock(EntityZombie.class);
        when(ridden.isNonBoss()).thenReturn(true);
        when(ridden.isBeingRidden()).thenReturn(true);
        assertFalse(EntityTickBudget.shouldSkip(world, ridden));
        EntityZombie riding = mock(EntityZombie.class);
        when(riding.isNonBoss()).thenReturn(true);
        when(riding.isRiding()).thenReturn(true);
        assertFalse(EntityTickBudget.shouldSkip(world, riding));
        assertFalse(EntityTickBudget.shouldSkip(world, mock(EntityZombie.class)));
        EntityTippedArrow arrow = mock(EntityTippedArrow.class);
        when(arrow.isNonBoss()).thenReturn(true);
        assertFalse(EntityTickBudget.shouldSkip(world, arrow));
        EntityItem item = mock(EntityItem.class);
        when(item.isNonBoss()).thenReturn(true);
        assertTrue(EntityTickBudget.shouldSkip(world, item));
        EntityXPOrb orb = mock(EntityXPOrb.class);
        when(orb.isNonBoss()).thenReturn(true);
        assertTrue(EntityTickBudget.shouldSkip(world, orb));
        // A nearby player spares the entity, a far one does not
        EntityPlayer near = mock(EntityPlayer.class);
        when(near.getDistanceSq(zombie)).thenReturn(50.0D);
        EntityPlayer far = mock(EntityPlayer.class);
        when(far.getDistanceSq(zombie)).thenReturn(50_000.0D);
        world.playerEntities.add(far);
        assertTrue(EntityTickBudget.shouldSkip(world, zombie));
        world.playerEntities.add(near);
        assertFalse(EntityTickBudget.shouldSkip(world, zombie));
    }

    // A vanilla-looking entity type outside the vanilla package
    static final class ModdedItem extends EntityItem {
        // Never called: instances come from Mc.uninitialized
        ModdedItem() {
            super(null);
        }
    }

    @Test
    void reducedRadiusAppliesToKnownVanillaEntitiesOnly() {
        assertFalse(ReducedRadiusQuery.applies());
        World.MAX_ENTITY_RADIUS = 8.0D;
        assertTrue(ReducedRadiusQuery.applies());
        // Classification is by exact type, so these are real instances with no constructor run
        EntityZombie zombie = Mc.uninitialized(EntityZombie.class);
        assertTrue(ReducedRadiusQuery.isEligible(zombie));
        assertTrue(ReducedRadiusQuery.isEligible(zombie));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityPlayerMP.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityWolf.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityHorse.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityDragon.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityWither.class)));
        assertTrue(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityItem.class)));
        assertTrue(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityXPOrb.class)));
        assertTrue(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityItemFrame.class)));
        assertTrue(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityPainting.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(EntityTippedArrow.class)));
        assertFalse(ReducedRadiusQuery.isEligible(Mc.uninitialized(ModdedItem.class)));

        World world = mock(World.class);
        IChunkProvider provider = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        Chunk chunk = mock(Chunk.class);
        when(provider.getLoadedChunk(0, 0)).thenReturn(chunk);
        Mockito.doAnswer(inv -> {
            inv.<List<Entity>>getArgument(2).add(zombie);
            return null;
        }).when(chunk).getEntitiesWithinAABBForEntity(any(), any(), any(), any());
        // The box spans chunks (0,0) to (1,1) once padded, but only (0,0) is loaded
        List<Entity> found = ReducedRadiusQuery.getEntitiesInAABBexcluding(world, null, new AxisAlignedBB(10, 0, 10, 15, 5, 15), e -> true);
        assertEquals(List.of(zombie), found);
        Mockito.verify(provider).getLoadedChunk(1, 1);
    }

    @Test
    void hopperCachesResolveTileEntitiesAndSkipEmptyEntityQueries() {
        World world = Mc.mock(World.class, TileEntityAccess.class, InventoryEntityTracker.class);
        Mixins.set(world, "rand", new Random(1));
        BlockPos furnacePos = new BlockPos(1, 2, 3);
        BlockPos chestPos = new BlockPos(1, 3, 3);
        BlockPos stonePos = new BlockPos(1, 4, 3);
        BlockPos airPos = new BlockPos(1, 5, 3);
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        when(world.getBlockState(furnacePos)).thenReturn(Blocks.FURNACE.getDefaultState());
        when(world.getBlockState(chestPos)).thenReturn(Blocks.CHEST.getDefaultState());
        when(world.getBlockState(stonePos)).thenReturn(Blocks.STONE.getDefaultState());
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        TileEntityChest chest = mock(TileEntityChest.class);
        TileEntityAccess access = (TileEntityAccess) world;
        when(access.equilibrium$getExistingTileEntity(furnacePos)).thenReturn(furnace);
        when(access.equilibrium$getExistingTileEntity(chestPos)).thenReturn(chest);
        when(world.getTileEntity(chestPos)).thenReturn(chest);

        HopperInventoryCache cache = new HopperInventoryCache();
        assertSame(furnace, cache.get(world, 1.2, 2.9, 3.5));
        assertSame(furnace, cache.get(world, 1.2, 2.9, 3.5));
        Mockito.verify(access, Mockito.times(1)).equilibrium$getExistingTileEntity(furnacePos);
        // An invalidated tile entity is looked up again, and a replacement is cached
        when(furnace.isInvalid()).thenReturn(true);
        TileEntityFurnace replacement = mock(TileEntityFurnace.class);
        when(access.equilibrium$getExistingTileEntity(furnacePos)).thenReturn(replacement);
        assertSame(replacement, cache.get(world, 1.2, 2.9, 3.5));
        // Chests are resolved every time through the block, never cached
        assertNotNull(cache.get(world, 1.5, 3.5, 3.5));
        assertNotNull(cache.get(world, 1.5, 3.5, 3.5));
        Mockito.verify(access, Mockito.times(2)).equilibrium$getExistingTileEntity(chestPos);
        when(access.equilibrium$getExistingTileEntity(chestPos)).thenReturn(null);
        assertNull(cache.get(world, 1.5, 3.5, 3.5));
        // Blocks without tile entities, or with one that is not an inventory, yield nothing and remember it
        assertNull(cache.get(world, 1.5, 4.5, 3.5));
        assertNull(cache.get(world, 1.5, 4.5, 3.5));
        when(access.equilibrium$getExistingTileEntity(furnacePos)).thenReturn(null);
        cache.clear();
        assertNull(cache.get(world, 1.2, 2.9, 3.5));
        assertNull(cache.get(world, 1.2, 2.9, 3.5));
        Mockito.verify(access, Mockito.times(3)).equilibrium$getExistingTileEntity(furnacePos);
        cache.clear();
        // Air falls through to the entity search, which is skipped while the world says it has no inventory entities
        assertNull(cache.get(world, 1.5, 5.5, 3.5));
        Mockito.verify(world, Mockito.never()).getEntitiesInAABBexcluding(any(), any(), any());
        when(((InventoryEntityTracker) world).equilibrium$mayHaveInventoryEntities()).thenReturn(true);
        when(world.getEntitiesInAABBexcluding(any(), any(), any())).thenReturn(new ArrayList<>());
        assertNull(cache.get(world, 1.5, 5.5, 3.5));
        Entity minecart = Mc.mock(Entity.class, IInventory.class);
        when(world.getEntitiesInAABBexcluding(any(), any(), any())).thenReturn(List.of(minecart));
        assertSame(minecart, HopperEntityLookup.findInventoryEntity(world, 1.5, 5.5, 3.5));

        // A plain world without the mixin interfaces uses the vanilla lookups
        World plain = mock(World.class);
        Mixins.set(plain, "rand", new Random(1));
        when(plain.getBlockState(any())).thenReturn(Blocks.FURNACE.getDefaultState());
        when(plain.getTileEntity(any())).thenReturn(furnace);
        when(plain.getEntitiesInAABBexcluding(any(), any(), any())).thenReturn(List.of(minecart));
        HopperInventoryCache plainCache = new HopperInventoryCache();
        assertSame(furnace, plainCache.get(plain, 0.5, 0.5, 0.5));
        assertSame(minecart, HopperEntityLookup.findInventoryEntity(plain, 0.5, 0.5, 0.5));
    }
}
