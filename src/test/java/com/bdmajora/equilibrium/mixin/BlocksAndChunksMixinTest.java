package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.collections.BlockPosLongMap;
import com.bdmajora.equilibrium.common.crafting.CraftingCache;
import com.bdmajora.equilibrium.common.hopper.HopperInventoryCache;
import com.bdmajora.equilibrium.common.world.TileEntityAccess;
import com.bdmajora.equilibrium.mixin.ai.goal_selector.EntityAITasksMixin;
import com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load.ChunkCacheMixin;
import com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load.PathNavigateMixin;
import com.bdmajora.equilibrium.common.ai.NavigationChunkGuard;
import com.bdmajora.equilibrium.mixin.alloc.entity_tracker.EntityTrackerEntryMixin;
import com.bdmajora.equilibrium.mixin.block.crafting_cache.CraftingManagerMixin;
import com.bdmajora.equilibrium.mixin.block.furnace_recipes.FurnaceRecipesMixin;
import com.bdmajora.equilibrium.mixin.block.hopper.TileEntityHopperMixin;
import com.bdmajora.equilibrium.mixin.block.redstone_wire.BlockRedstoneWireMixin;
import com.bdmajora.equilibrium.mixin.block.spawner_check_cache.MobSpawnerBaseLogicMixin;
import com.bdmajora.equilibrium.mixin.chunk.no_validation.ChunkMixin;
import com.bdmajora.equilibrium.mixin.collections.mob_spawning.WorldEntitySpawnerMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlocksAndChunksMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void forgetStubs() {
        ShadowStubs.clear();
        NavigationChunkGuard.set(false);
    }

    @Test
    void aiAndTrackerSetsAreSwappedAndLookTasksDropped() {
        EntityAITasksMixin tasks = Mixins.instance(EntityAITasksMixin.class);
        Mixins.call(tasks, "equilibrium$useFastutilSets", Mixins.ci());
        assertInstanceOf(it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet.class, tasks.taskEntries);
        assertInstanceOf(it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet.class,
                Mixins.get(tasks, "executingTaskEntries"));

        EntityTrackerEntryMixin tracker = Mixins.instance(EntityTrackerEntryMixin.class);
        Mixins.call(tracker, "equilibrium$useFastutilSet", Mixins.ci());
        assertInstanceOf(it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet.class, tracker.trackingPlayers);

        WorldEntitySpawnerMixin spawner = Mixins.instance(WorldEntitySpawnerMixin.class);
        Mixins.call(spawner, "equilibrium$useFastutilSet", Mixins.ci());
        assertInstanceOf(ObjectOpenHashSet.class, Mixins.get(spawner, "eligibleChunksForSpawning"));

        var looks = Mixins.instance(com.bdmajora.equilibrium.mixin.ai.remove_look_tasks.EntityAITasksMixin.class);
        var watching = Mixins.ci();
        Mixins.call(looks, "equilibrium$dropLookTasks", 1, mock(EntityAIWatchClosest.class), watching);
        assertTrue(watching.isCancelled());
        var idle = Mixins.ci();
        Mixins.call(looks, "equilibrium$dropLookTasks", 1, mock(EntityAILookIdle.class), idle);
        assertTrue(idle.isCancelled());
        var other = Mixins.ci();
        Mixins.call(looks, "equilibrium$dropLookTasks", 1, mock(EntityAIBase.class), other);
        assertFalse(other.isCancelled());
    }

    @Test
    void pathfindingOnlySeesLoadedChunksWhileNavigating() {
        PathNavigateMixin navigate = Mixins.instance(PathNavigateMixin.class);
        ChunkCacheMixin cache = Mixins.instance(ChunkCacheMixin.class);
        World world = mock(World.class);
        Mixins.set(world, "isRemote", false);
        IChunkProvider provider = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        Chunk loaded = mock(Chunk.class);
        when(provider.getLoadedChunk(1, 2)).thenReturn(loaded);
        Chunk generated = mock(Chunk.class);

        // Outside a path search the original lookup runs
        int[] originals = new int[1];
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Chunk> original = args -> {
            originals[0]++;
            return generated;
        };
        assertSame(generated, Mixins.call(cache, "equilibrium$loadedOnlyWhileNavigating", world, 1, 2, original));
        // The navigation flag is set for the length of the ChunkCache construction
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<ChunkCache> building = args -> {
            assertTrue(NavigationChunkGuard.isNavigating());
            assertSame(loaded, Mixins.call(cache, "equilibrium$loadedOnlyWhileNavigating", world, 1, 2, original));
            assertNull(Mixins.call(cache, "equilibrium$loadedOnlyWhileNavigating", world, 3, 4, original));
            return null;
        };
        Mixins.call(navigate, "equilibrium$flagNavigationCache", world, BlockPos.ORIGIN, BlockPos.ORIGIN, 1, building);
        assertFalse(NavigationChunkGuard.isNavigating());
        assertEquals(1, originals[0]);
        // A client world keeps loading, since only the server path search is guarded
        Mixins.set(world, "isRemote", true);
        NavigationChunkGuard.set(true);
        assertSame(generated, Mixins.call(cache, "equilibrium$loadedOnlyWhileNavigating", world, 1, 2, original));
    }

    @Test
    void craftingLookupsGoThroughTheCache() {
        // The registry is swapped for an empty one; the harness opens up the static final so this takes effect
        RegistryNamespaced<ResourceLocation, IRecipe> original = CraftingManager.REGISTRY;
        RegistryNamespaced<ResourceLocation, IRecipe> registry = new RegistryNamespaced<>();
        Statics.set(CraftingManager.class, "REGISTRY", registry);
        try {
            Mixins.set(CraftingCache.class, "registrySize", -1);
            Mixins.set(CraftingCache.class, "lastMatch", null);
            Mixins.<Map<?, ?>>get(CraftingCache.class, "CACHE").clear();
            World world = mock(World.class);
            IRecipe recipe = mock(IRecipe.class);
            registry.register(0, new ResourceLocation("test", "one"), recipe);
            InventoryCrafting matrix = new InventoryCrafting(mock(Container.class), 2, 2);
            matrix.setInventorySlotContents(0, new ItemStack(Items.APPLE));
            when(recipe.matches(matrix, world)).thenReturn(true);
            NonNullList<ItemStack> leftovers = NonNullList.withSize(4, ItemStack.EMPTY);
            when(recipe.getRemainingItems(matrix)).thenReturn(leftovers);

            var match = Mixins.<IRecipe>cir();
            Mixins.call(CraftingManagerMixin.class, "equilibrium$cachedMatch", matrix, world, match);
            assertSame(recipe, match.getReturnValue());
            var remaining = Mixins.<NonNullList<ItemStack>>cir();
            Mixins.call(CraftingManagerMixin.class, "equilibrium$cachedRemaining", matrix, world, remaining);
            assertSame(leftovers, remaining.getReturnValue());

            // With no recipe the grid's own contents come back, which is what vanilla's fallback does
            when(recipe.matches(matrix, world)).thenReturn(false);
            Mixins.set(CraftingCache.class, "lastMatch", null);
            var empty = Mixins.<NonNullList<ItemStack>>cir();
            Mixins.call(CraftingManagerMixin.class, "equilibrium$cachedRemaining", matrix, world, empty);
            assertEquals(4, empty.getReturnValue().size());
            assertEquals(Items.APPLE, empty.getReturnValue().get(0).getItem());
        } finally {
            Statics.set(CraftingManager.class, "REGISTRY", original);
            Mixins.set(CraftingCache.class, "registrySize", -1);
            Mixins.set(CraftingCache.class, "lastMatch", null);
        }
    }

    @Test
    void furnaceLookupsGoThroughTheIndexes() {
        FurnaceRecipesMixin furnace = Mixins.instance(FurnaceRecipesMixin.class);
        Map<ItemStack, ItemStack> smelting = new LinkedHashMap<>();
        Map<ItemStack, Float> experience = new LinkedHashMap<>();
        ItemStack ore = new ItemStack(Blocks.IRON_ORE);
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        smelting.put(ore, ingot);
        experience.put(ore, 0.7F);
        Mixins.set(furnace, "smeltingList", smelting);
        Mixins.set(furnace, "experienceList", experience);
        Mixins.stub(furnace, "compareItemStacks", invocation -> invocation.<ItemStack>getArgument(0).getItem()
                == invocation.<ItemStack>getArgument(1).getItem());

        // Before the indexes exist every lookup falls through to vanilla
        var beforeInit = Mixins.<ItemStack>cir();
        Mixins.call(furnace, "equilibrium$indexedResult", new ItemStack(Blocks.IRON_ORE), beforeInit);
        assertFalse(beforeInit.isCancelled());
        Mixins.call(furnace, "equilibrium$invalidateOnAdd", ore, ingot, 0.7F, Mixins.ci());
        Mixins.call(furnace, "equilibrium$buildIndexes", Mixins.ci());

        var found = Mixins.<ItemStack>cir();
        Mixins.call(furnace, "equilibrium$indexedResult", new ItemStack(Blocks.IRON_ORE), found);
        assertSame(ingot, found.getReturnValue());
        var missing = Mixins.<ItemStack>cir();
        Mixins.call(furnace, "equilibrium$indexedResult", new ItemStack(Blocks.STONE), missing);
        assertSame(ItemStack.EMPTY, missing.getReturnValue());
        var emptyInput = Mixins.<ItemStack>cir();
        Mixins.call(furnace, "equilibrium$indexedResult", ItemStack.EMPTY, emptyInput);
        assertFalse(emptyInput.isCancelled());

        var reward = Mixins.<Float>cir();
        Mixins.call(furnace, "equilibrium$indexedExperience", new ItemStack(Blocks.IRON_ORE), reward);
        assertEquals(0.7F, reward.getReturnValue());
        var noReward = Mixins.<Float>cir();
        Mixins.call(furnace, "equilibrium$indexedExperience", new ItemStack(Blocks.STONE), noReward);
        assertEquals(0.0F, noReward.getReturnValue());
        var emptyReward = Mixins.<Float>cir();
        Mixins.call(furnace, "equilibrium$indexedExperience", ItemStack.EMPTY, emptyReward);
        assertFalse(emptyReward.isCancelled());
        // Forge lets the item answer first
        var itemAnswer = Mixins.<Float>cir();
        Mixins.call(furnace, "equilibrium$indexedExperience", new ItemStack(Items.GOLDEN_APPLE), itemAnswer);
        assertEquals(0.0F, itemAnswer.getReturnValue());
        // A new registration invalidates both indexes
        smelting.put(new ItemStack(Blocks.GOLD_ORE), new ItemStack(Items.GOLD_INGOT));
        experience.put(new ItemStack(Blocks.GOLD_ORE), 1.0F);
        Mixins.call(furnace, "equilibrium$invalidateOnAdd", ore, ingot, 1.0F, Mixins.ci());
        var gold = Mixins.<ItemStack>cir();
        Mixins.call(furnace, "equilibrium$indexedResult", new ItemStack(Blocks.GOLD_ORE), gold);
        assertEquals(Items.GOLD_INGOT, gold.getReturnValue().getItem());
    }

    @Test
    void hoppersResolveBothSidesThroughTheirCaches() {
        TileEntityHopperMixin hopper = Mixins.instance(TileEntityHopperMixin.class);
        assertSame(hopper.equilibrium$sourceCache(), hopper.equilibrium$sourceCache());
        assertSame(hopper.equilibrium$destinationCache(), hopper.equilibrium$destinationCache());
        assertNotSame(hopper.equilibrium$sourceCache(), hopper.equilibrium$destinationCache());

        World world = Mc.mock(World.class, TileEntityAccess.class);
        Mixins.set(world, "rand", new Random(1));
        when(world.getBlockState(any())).thenReturn(Blocks.FURNACE.getDefaultState());
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        when(((TileEntityAccess) world).equilibrium$getExistingTileEntity(any())).thenReturn(furnace);
        Mockito.doReturn(world).when(hopper).getWorld();
        Mockito.doReturn(2.0D).when(hopper).getXPos();
        Mockito.doReturn(3.0D).when(hopper).getYPos();
        Mockito.doReturn(4.0D).when(hopper).getZPos();
        // Metadata 2 faces north, so the destination is one block along -Z
        Mockito.doReturn(2).when(hopper).getBlockMetadata();
        assertSame(furnace, Mixins.call(hopper, "getInventoryForHopperTransfer"));
        assertSame(furnace, Mixins.call(TileEntityHopperMixin.class, "getSourceInventory", hopper));

        // A hopper without caches, such as a minecart, resolves uncached
        TileEntityHopper cartless = mock(TileEntityHopper.class);
        when(cartless.getWorld()).thenReturn(world);
        when(cartless.getXPos()).thenReturn(2.0D);
        when(cartless.getYPos()).thenReturn(3.0D);
        when(cartless.getZPos()).thenReturn(4.0D);
        when(world.getTileEntity(any())).thenReturn(furnace);
        assertSame(furnace, Mixins.call(TileEntityHopperMixin.class, "getSourceInventory", cartless));
        // A double chest resolves through the block so both halves are seen
        when(world.getBlockState(any())).thenReturn(Blocks.CHEST.getDefaultState());
        net.minecraft.tileentity.TileEntityChest chest = mock(net.minecraft.tileentity.TileEntityChest.class);
        when(world.getTileEntity(any())).thenReturn(chest);
        assertInstanceOf(net.minecraft.inventory.InventoryLargeChest.class,
                Mixins.call(TileEntityHopperMixin.class, "getSourceInventory", cartless));
        // Nothing there at all falls through to the entity search
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        when(world.getEntitiesInAABBexcluding(any(), any(), any())).thenReturn(java.util.List.of());
        assertNull(Mixins.call(TileEntityHopperMixin.class, "getSourceInventory", cartless));
        net.minecraft.entity.Entity minecart = Mc.mock(net.minecraft.entity.Entity.class, IInventory.class);
        when(world.getEntitiesInAABBexcluding(any(), any(), any())).thenReturn(java.util.List.of(minecart));
        assertSame(minecart, Mixins.call(TileEntityHopperMixin.class, "getSourceInventory", cartless));
    }

    @Test
    void redstoneWireReadsEachNeighbourOnce() {
        BlockRedstoneWireMixin wire = Mixins.instance(BlockRedstoneWireMixin.class);
        Mixins.set(wire, "blocksNeedingUpdate", new java.util.HashSet<BlockPos>());
        World world = mock(World.class);
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState solid = Blocks.STONE.getDefaultState();
        when(world.getBlockState(any())).thenReturn(air);
        IBlockState powered = Blocks.REDSTONE_WIRE.getDefaultState().withProperty(BlockRedstoneWire.POWER, 0);
        BlockPos pos = new BlockPos(0, 4, 0);
        when(world.getBlockState(pos)).thenReturn(powered);

        // No neighbour carries current, so the wire stays at zero and nothing is scheduled
        ShadowStubs.on(wire, "getMaxCurrentStrength", args -> args[2]);
        IBlockState result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.down(), powered);
        assertEquals(0, result.getValue(BlockRedstoneWire.POWER).intValue());
        assertTrue(Mixins.<java.util.Set<BlockPos>>get(wire, "blocksNeedingUpdate").isEmpty());

        // A neighbouring wire at strength 9 leaves 8 here, and every touched position is scheduled
        ShadowStubs.on(wire, "getMaxCurrentStrength", args -> Math.max((int) args[2], args[1].equals(pos.down()) ? 0 : 9));
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.down(), powered);
        assertEquals(8, result.getValue(BlockRedstoneWire.POWER).intValue());
        assertEquals(7, Mixins.<java.util.Set<BlockPos>>get(wire, "blocksNeedingUpdate").size());
        Mockito.verify(world).setBlockState(pos, result, 2);

        // Direct power from a neighbour block wins over the wire chain
        when(world.getRedstonePowerFromNeighbors(pos)).thenReturn(15);
        ShadowStubs.on(wire, "getMaxCurrentStrength", args -> args[2]);
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.down(), powered);
        assertEquals(15, result.getValue(BlockRedstoneWire.POWER).intValue());
        assertTrue(Mixins.<Boolean>get(wire, "canProvidePower"));

        // Solid neighbours route current over or under them depending on what is above the wire
        when(world.getRedstonePowerFromNeighbors(pos)).thenReturn(0);
        when(world.getBlockState(pos.north())).thenReturn(solid);
        when(world.getBlockState(pos.up())).thenReturn(air);
        ShadowStubs.on(wire, "getMaxCurrentStrength", args -> Math.max((int) args[2], args[1].equals(pos.north().up()) ? 9 : 0));
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.down(), powered);
        assertEquals(8, result.getValue(BlockRedstoneWire.POWER).intValue());
        when(world.getBlockState(pos.up())).thenReturn(solid);
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.down(), powered);
        assertEquals(0, result.getValue(BlockRedstoneWire.POWER).intValue());
        // A wire below an air neighbour is reached when the origin is at or above this wire
        when(world.getBlockState(pos.north())).thenReturn(air);
        ShadowStubs.on(wire, "getMaxCurrentStrength", args -> Math.max((int) args[2], args[1].equals(pos.north().down()) ? 9 : 0));
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.up(), powered);
        assertEquals(8, result.getValue(BlockRedstoneWire.POWER).intValue());
        // The write is skipped when the block changed underneath the calculation
        when(world.getBlockState(pos)).thenReturn(air);
        result = Mixins.call(wire, "calculateCurrentChanges", world, pos, pos.up(), powered);
        assertEquals(8, result.getValue(BlockRedstoneWire.POWER).intValue());
        Mockito.verify(world, Mockito.times(4)).setBlockState(any(), any(), Mockito.anyInt());
    }

    @Test
    void spawnersRememberWhetherAPlayerIsNear() {
        MobSpawnerBaseLogicMixin spawner = Mixins.instance(MobSpawnerBaseLogicMixin.class);
        Mixins.set(spawner, "equilibrium$cachedPlayerCount", -1);
        World world = mock(World.class);
        Mixins.set(world, "rand", new Random(4));
        Mixins.set(world, "playerEntities", new java.util.ArrayList<net.minecraft.entity.player.EntityPlayer>());
        Mockito.doReturn(world).when((net.minecraft.tileentity.MobSpawnerBaseLogic) (Object) spawner).getSpawnerWorld();
        when(world.getTotalWorldTime()).thenReturn(100L);

        // Nothing cached yet, so vanilla's check runs and its answer is kept
        var first = Mixins.<Boolean>cir();
        Mixins.call(spawner, "equilibrium$answerFromCache", first);
        assertFalse(first.isCancelled());
        var stored = Mixins.<Boolean>cir(true);
        Mixins.call(spawner, "equilibrium$remember", stored);
        var cached = Mixins.<Boolean>cir();
        Mixins.call(spawner, "equilibrium$answerFromCache", cached);
        assertTrue(cached.getReturnValue());

        // The answer expires after the interval, and a change in player count drops it early
        when(world.getTotalWorldTime()).thenReturn(200L);
        var expired = Mixins.<Boolean>cir();
        Mixins.call(spawner, "equilibrium$answerFromCache", expired);
        assertFalse(expired.isCancelled());
        when(world.getTotalWorldTime()).thenReturn(100L);
        Mixins.call(spawner, "equilibrium$remember", Mixins.<Boolean>cir(true));
        world.playerEntities.add(mock(net.minecraft.entity.player.EntityPlayer.class));
        var changed = Mixins.<Boolean>cir();
        Mixins.call(spawner, "equilibrium$answerFromCache", changed);
        assertFalse(changed.isCancelled());

        // A spawner with no world at all, as when its tile entity is not placed, is left alone
        Mockito.doReturn(null).when((net.minecraft.tileentity.MobSpawnerBaseLogic) (Object) spawner).getSpawnerWorld();
        var worldless = Mixins.<Boolean>cir();
        Mixins.call(spawner, "equilibrium$answerFromCache", worldless);
        assertFalse(worldless.isCancelled());
        Mixins.call(spawner, "equilibrium$remember", Mixins.<Boolean>cir(true));
    }

    @Test
    void chunkReadsSkipTheDebugAndValidationWork() {
        ChunkMixin chunk = Mixins.instance(ChunkMixin.class);
        World world = mock(World.class);
        when(world.getWorldType()).thenReturn(WorldType.DEFAULT);
        Mixins.set(chunk, "world", world);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        ExtendedBlockStorage section = new ExtendedBlockStorage(0, true);
        section.set(1, 2, 3, Blocks.STONE.getDefaultState());
        sections[0] = section;
        sections[1] = Chunk.NULL_BLOCK_STORAGE;
        Mixins.set(chunk, "storageArrays", sections);
        Mixins.call(chunk, "equilibrium$resolveWorldType", Mixins.ci());
        assertEquals(Blocks.STONE.getDefaultState(), chunk.getBlockState(1, 2, 3));
        assertEquals(Blocks.AIR.getDefaultState(), chunk.getBlockState(1, 18, 3));
        assertEquals(Blocks.AIR.getDefaultState(), chunk.getBlockState(1, -1, 3));
        assertEquals(Blocks.AIR.getDefaultState(), chunk.getBlockState(1, 300, 3));
        // A read that throws is still reported with vanilla's crash report
        Mixins.set(chunk, "storageArrays", null);
        assertThrows(net.minecraft.util.ReportedException.class, () -> chunk.getBlockState(1, 2, 3));

        // The debug world answers from its generator instead of the sections
        when(world.getWorldType()).thenReturn(WorldType.DEBUG_ALL_BLOCK_STATES);
        Mixins.call(chunk, "equilibrium$resolveWorldType", Mixins.ci());
        assertEquals(Blocks.BARRIER.getDefaultState(), chunk.getBlockState(0, 60, 0));
        assertEquals(Blocks.AIR.getDefaultState(), chunk.getBlockState(0, 50, 0));
        assertNotNull(chunk.getBlockState(0, 70, 0));

        // The tile entity map is swapped for the long-keyed one unless Cubic Chunks is present
        Mc.forge();
        var mapped = Mixins.instance(com.bdmajora.equilibrium.mixin.chunk.tile_entity_map.ChunkMixin.class);
        Mixins.set(mapped, "tileEntities", new HashMap<BlockPos, net.minecraft.tileentity.TileEntity>());
        Mixins.call(mapped, "equilibrium$useLongKeyedMap", world, 0, 0, Mixins.ci());
        assertInstanceOf(BlockPosLongMap.class, Mixins.get(mapped, "tileEntities"));
    }
}
