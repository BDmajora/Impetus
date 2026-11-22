package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.entity.EntityTickBudget;
import com.bdmajora.equilibrium.mixin.block.crafting_cache.CraftingManagerMixin;
import com.bdmajora.equilibrium.mixin.block.furnace_recipes.FurnaceRecipesMixin;
import com.bdmajora.equilibrium.mixin.entity.collisions.movement.WorldMixin;
import com.bdmajora.equilibrium.mixin.math.sine_lut.MathHelperMixin;
import com.bdmajora.equilibrium.mixin.world.block_entity_ticking.sleeping.brewing_stand.TileEntityBrewingStandMixin;
import com.bdmajora.equilibrium.mixin.world.block_entity_ticking.sleeping.furnace.TileEntityFurnaceMixin;
import com.bdmajora.equilibrium.mixin.world.explosions.block_raycast.ExplosionMixin;
import com.bdmajora.equilibrium.mixin.worldgen.int_cache.IntCacheMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeavyRewriteMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void forgetStubs() {
        ShadowStubs.clear();
    }

    @Test
    void placeholderShadowsAreNeverReachedInProduction() {
        // Checks the private stand-ins throw.
        var passengers = Mixins.instance(com.bdmajora.equilibrium.mixin.alloc.deep_passengers.EntityMixin.class);
        assertThrows(AssertionError.class,
                () -> Mixins.call(passengers, "getRecursivePassengersByType", Entity.class, new java.util.HashSet<>()));
        var brewing = Mixins.instance(TileEntityBrewingStandMixin.class);
        assertThrows(AssertionError.class, () -> Mixins.call(brewing, "canBrew"));
        // The statics-only mixins still have a constructor of their own
        assertNotNull(Mixins.instance(CraftingManagerMixin.class));
        assertNotNull(Mixins.instance(MathHelperMixin.class));
        assertNotNull(Mixins.instance(IntCacheMixin.class));
        assertNotNull(Mixins.instance(FurnaceRecipesMixin.class));
    }

    @Test
    void idleBlockEntitiesSleep() {
        TileEntityFurnaceMixin furnace = Mixins.instance(TileEntityFurnaceMixin.class);
        NonNullList<ItemStack> slots = NonNullList.withSize(4, ItemStack.EMPTY);
        Mixins.set(furnace, "furnaceItemStacks", slots);
        // Checks an idle furnace.
        var idle = Mixins.ci();
        Mixins.call(furnace, "equilibrium$sleepWhileIdle", idle);
        assertTrue(idle.isCancelled());
        slots.set(0, new ItemStack(Blocks.IRON_ORE));
        slots.set(1, new ItemStack(net.minecraft.init.Items.COAL));
        var ready = Mixins.ci();
        Mixins.call(furnace, "equilibrium$sleepWhileIdle", ready);
        assertFalse(ready.isCancelled());
        slots.set(1, ItemStack.EMPTY);
        var noFuel = Mixins.ci();
        Mixins.call(furnace, "equilibrium$sleepWhileIdle", noFuel);
        assertTrue(noFuel.isCancelled());
        Mixins.set(furnace, "furnaceBurnTime", 100);
        var burning = Mixins.ci();
        Mixins.call(furnace, "equilibrium$sleepWhileIdle", burning);
        assertFalse(burning.isCancelled());
        Mixins.set(furnace, "furnaceBurnTime", 0);
        Mixins.set(furnace, "cookTime", 50);
        var cooking = Mixins.ci();
        Mixins.call(furnace, "equilibrium$sleepWhileIdle", cooking);
        assertFalse(cooking.isCancelled());

        // A brewing stand with no ingredient never reaches the recipe registry
        TileEntityBrewingStandMixin stand = Mixins.instance(TileEntityBrewingStandMixin.class);
        NonNullList<ItemStack> bottles = NonNullList.withSize(5, ItemStack.EMPTY);
        Mixins.set(stand, "brewingItemStacks", bottles);
        assertFalse((boolean) Mixins.call(stand, "equilibrium$skipRecipeScanWhenEmpty", (Object) null));
        bottles.set(3, new ItemStack(net.minecraft.init.Items.NETHER_WART));
        ShadowStubs.on(stand, "canBrew", args -> true);
        assertTrue((boolean) Mixins.call(stand, "equilibrium$skipRecipeScanWhenEmpty", (Object) null));
    }

    @Test
    void farEntitiesSitOutTheirTickWhenTheServerIsBehind() {
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.world.entity_tick_budget.WorldMixin.class);
        World self = (World) (Object) world;
        Mixins.set(self, "isRemote", true);
        EntityZombie zombie = mock(EntityZombie.class);
        // Checks passengers and client worlds.
        var passenger = Mixins.ci();
        Mixins.call(world, "equilibrium$governFarEntities", zombie, false, passenger);
        assertFalse(passenger.isCancelled());
        var client = Mixins.ci();
        Mixins.call(world, "equilibrium$governFarEntities", zombie, true, client);
        assertFalse(client.isCancelled());
        assertFalse(EntityTickBudget.shouldSkip(self, zombie));
    }

    @Test
    void rayTracingGoesThroughTheAllocationFreeCaster() {
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.world.raycast.WorldMixin.class);
        World self = (World) (Object) world;
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(self).getWorldType();
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(self).getBlockState(any());
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(self).getBlockState(new BlockPos(3, 5, 3));
        RayTraceResult hit = world.rayTraceBlocks(new Vec3d(0.5, 5.5, 3.5), new Vec3d(6.5, 5.5, 3.5), false, false, false);
        assertNotNull(hit);
        assertEquals(new BlockPos(3, 5, 3), hit.getBlockPos());
        assertNull(world.rayTraceBlocks(new Vec3d(0.5, 9.5, 3.5), new Vec3d(6.5, 9.5, 3.5), false, false, false));
    }

    @Test
    void collisionBoxesAreGatheredThroughOneCursor() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        World self = (World) (Object) world;
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(self).getWorldType();
        Mockito.doReturn(new WorldBorder()).when(self).getWorldBorder();
        Mockito.doReturn(true).when(self).isBlockLoaded(any());
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(self).getBlockState(any());
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(self).getBlockState(new BlockPos(0, 0, 0));
        List<AxisAlignedBB> boxes = new ArrayList<>();
        AxisAlignedBB around = new AxisAlignedBB(0, 0, 0, 1, 2, 1);

        assertTrue((boolean) Mixins.call(world, "getCollisionBoxes", null, around, false, boxes));
        assertEquals(List.of(new AxisAlignedBB(0, 0, 0, 1, 1, 1)), boxes);
        boxes.clear();
        // Nothing solid nearby leaves the list empty
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(self).getBlockState(new BlockPos(0, 0, 0));
        assertFalse((boolean) Mixins.call(world, "getCollisionBoxes", null, around, false, boxes));
        assertTrue(boxes.isEmpty());
        // Checks an entity outside the border.
        Entity entity = mock(Entity.class);
        when(entity.isOutsideBorder()).thenReturn(true);
        Mockito.doReturn(true).when(self).isInsideWorldBorder(entity);
        WorldBorder border = mock(WorldBorder.class);
        when(border.contains(any(BlockPos.class))).thenReturn(false);
        Mockito.doReturn(border).when(self).getWorldBorder();
        assertTrue((boolean) Mixins.call(world, "getCollisionBoxes", entity, around, false, boxes));
        Mockito.verify(entity, Mockito.atLeastOnce()).setOutsideBorder(false);
        boxes.clear();
        // Checks the early out form.
        Mockito.doReturn(new WorldBorder()).when(self).getWorldBorder();
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(self).getBlockState(new BlockPos(0, 0, 0));
        assertTrue((boolean) Mixins.call(world, "getCollisionBoxes", null, around, true, boxes));
        assertFalse(boxes.isEmpty());
        boxes.clear();
        assertTrue((boolean) Mixins.call(world, "getCollisionBoxes", null,
                new AxisAlignedBB(30000000, 0, 0, 30000001, 2, 1), true, boxes));
        assertTrue(boxes.isEmpty());
    }

    @Test
    void explosionsReadEachBlockOnceAndDamageWhatTheyReach() {
        ExplosionMixin explosion = Mixins.instance(ExplosionMixin.class);
        World world = mock(World.class);
        Mixins.set(world, "rand", new Random(7));
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(world).getWorldType();
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        when(world.getBlockState(new BlockPos(0, 4, 0))).thenReturn(Blocks.STONE.getDefaultState());
        when(world.getBlockDensity(any(), any())).thenReturn(1.0F);
        Mixins.set(explosion, "world", world);
        Mixins.set(explosion, "size", 4.0F);
        Mixins.set(explosion, "x", 0.0D);
        Mixins.set(explosion, "y", 5.0D);
        Mixins.set(explosion, "z", 0.0D);
        List<BlockPos> affected = new ArrayList<>();
        Mixins.set(explosion, "affectedBlockPositions", affected);
        Map<EntityPlayer, Vec3d> knockback = new HashMap<>();
        Mixins.set(explosion, "playerKnockbackMap", knockback);

        EntityLivingBase caught = mock(EntityZombie.class);
        caught.posX = 1.0D;
        caught.posY = 5.0D;
        caught.posZ = 0.0D;
        when(caught.getDistance(0.0D, 5.0D, 0.0D)).thenReturn(1.0D);
        when(caught.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(0, 4, 0, 1, 6, 1));
        when(caught.getItemStackFromSlot(any())).thenReturn(ItemStack.EMPTY);
        Entity immune = mock(Entity.class);
        when(immune.isImmuneToExplosions()).thenReturn(true);
        Entity distant = mock(Entity.class);
        when(distant.getDistance(0.0D, 5.0D, 0.0D)).thenReturn(1000.0D);
        Entity centred = mock(Entity.class);
        centred.posY = 5.0D;
        when(centred.getDistance(0.0D, 5.0D, 0.0D)).thenReturn(1.0D);
        when(centred.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(0, 4, 0, 1, 6, 1));
        when(world.getEntitiesWithinAABBExcludingEntity(any(), any()))
                .thenReturn(List.of(caught, immune, distant, centred));

        explosion.doExplosionA();
        assertTrue(affected.contains(new BlockPos(0, 4, 0)));
        Mockito.verify(caught).attackEntityFrom(any(), Mockito.anyFloat());
        Mockito.verify(immune, Mockito.never()).attackEntityFrom(any(), Mockito.anyFloat());
        Mockito.verify(distant, Mockito.never()).attackEntityFrom(any(), Mockito.anyFloat());
        // Checks a centred entity.
        Mockito.verify(centred, Mockito.never()).attackEntityFrom(any(), Mockito.anyFloat());
        assertTrue(knockback.isEmpty());

        // Checks the knockback record.
        affected.clear();
        EntityPlayer player = mock(EntityPlayer.class);
        player.posX = 1.0D;
        player.posY = 5.0D;
        when(player.getDistance(0.0D, 5.0D, 0.0D)).thenReturn(1.0D);
        when(player.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(0, 4, 0, 1, 6, 1));
        when(player.getItemStackFromSlot(any())).thenReturn(ItemStack.EMPTY);
        when(player.isSpectator()).thenReturn(false);
        when(player.isCreative()).thenReturn(false);
        when(world.getEntitiesWithinAABBExcludingEntity(any(), any())).thenReturn(List.of(player));
        Entity exploder = mock(Entity.class);
        Mixins.set(explosion, "exploder", exploder);
        when(exploder.getExplosionResistance(any(), any(), any(), any())).thenReturn(0.0F);
        when(exploder.canExplosionDestroyBlock(any(), any(), any(), any(), Mockito.anyFloat())).thenReturn(true);
        explosion.doExplosionA();
        assertTrue(affected.contains(new BlockPos(0, 4, 0)));
        assertEquals(1, knockback.size());
        // A block the exploder refuses to break is left out
        affected.clear();
        when(exploder.canExplosionDestroyBlock(any(), any(), any(), any(), Mockito.anyFloat())).thenReturn(false);
        explosion.doExplosionA();
        assertTrue(affected.isEmpty());
    }

    @Test
    void explosionRaysLoseResistanceOnEveryStepLikeVanilla() {
        ExplosionMixin explosion = Mixins.instance(ExplosionMixin.class);
        World world = mock(World.class);
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(world).getWorldType();
        // Places a stone wall and wood.
        when(world.getBlockState(any())).thenAnswer(invocation -> {
            int x = ((BlockPos) invocation.getArgument(0)).getX();
            return x == 2 ? Blocks.STONE.getDefaultState() : x == 3 ? Blocks.PLANKS.getDefaultState() : Blocks.AIR.getDefaultState();
        });
        when(world.getEntitiesWithinAABBExcludingEntity(any(), any())).thenReturn(List.of());
        Mixins.set(explosion, "world", world);
        Mixins.set(explosion, "size", 4.0F);
        Mixins.set(explosion, "x", 0.5D);
        Mixins.set(explosion, "y", 64.5D);
        Mixins.set(explosion, "z", 0.5D);
        List<BlockPos> affected = new ArrayList<>();
        Mixins.set(explosion, "affectedBlockPositions", affected);
        Mixins.set(explosion, "playerKnockbackMap", new HashMap<EntityPlayer, Vec3d>());
        Mixins.set(world, "rand", new Random(11));
        explosion.doExplosionA();

        // Runs vanilla's loop.
        Random random = new Random(11);
        java.util.Set<BlockPos> expected = new java.util.HashSet<>();
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) {
                        continue;
                    }
                    double d0 = j / 15.0F * 2.0F - 1.0F;
                    double d1 = k / 15.0F * 2.0F - 1.0F;
                    double d2 = l / 15.0F * 2.0F - 1.0F;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    d0 /= d3;
                    d1 /= d3;
                    d2 /= d3;
                    float f = 4.0F * (0.7F + random.nextFloat() * 0.6F);
                    double d4 = 0.5D;
                    double d6 = 64.5D;
                    double d8 = 0.5D;
                    for (; f > 0.0F; f -= 0.22500001F) {
                        BlockPos pos = new BlockPos(d4, d6, d8);
                        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                        if (state.getMaterial() != net.minecraft.block.material.Material.AIR) {
                            float resistance = state.getBlock().getExplosionResistance(world, pos, null, (net.minecraft.world.Explosion) (Object) explosion);
                            f -= (resistance + 0.3F) * 0.3F;
                        }
                        if (f > 0.0F) {
                            expected.add(pos);
                        }
                        d4 += d0 * 0.30000001192092896D;
                        d6 += d1 * 0.30000001192092896D;
                        d8 += d2 * 0.30000001192092896D;
                    }
                }
            }
        }
        assertEquals(expected, new java.util.HashSet<>(affected));
        // Checks the stone holds.
        assertFalse(affected.contains(new BlockPos(4, 64, 0)));
    }

    @Test
    void exposureRaysShareOneCursor() {
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.world.explosions.entity_raycast.WorldMixin.class);
        World self = (World) (Object) world;
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(self).getWorldType();
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(self).getBlockState(any());
        // Checks every ray reaches.
        assertEquals(1.0F, world.getBlockDensity(new Vec3d(0.5, 5.5, 0.5), new AxisAlignedBB(0, 5, 0, 1, 6, 1)));
        // A solid wall between the samples and the centre blocks them all
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(self).getBlockState(any());
        assertEquals(0.0F, world.getBlockDensity(new Vec3d(4.5, 5.5, 0.5), new AxisAlignedBB(0, 5, 0, 1, 6, 1)));
        // A degenerate box has no samples to take
        assertEquals(0.0F, world.getBlockDensity(new Vec3d(0.5, 5.5, 0.5), new AxisAlignedBB(0, 0, 0, -1, -1, -1)));
    }

}
