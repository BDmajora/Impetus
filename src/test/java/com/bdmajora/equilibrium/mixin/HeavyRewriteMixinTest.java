package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.entity.EntityTickBudget;
import com.bdmajora.equilibrium.mixin.block.crafting_cache.CraftingManagerMixin;
import com.bdmajora.equilibrium.mixin.block.furnace_recipes.FurnaceRecipesMixin;
import com.bdmajora.equilibrium.mixin.entity.collisions.movement.WorldMixin;
import com.bdmajora.equilibrium.mixin.entity.xp_orb_merging.EntityXPOrbMixin;
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
        // Each of these stands in for a private method of the target class, so running one is a programming error
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
        // Nothing lit, nothing cooking and nothing to cook with: the tick is skipped
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
        // A passenger being dragged along is never governed, and a client world never is either
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
        // An entity outside the border sees the world filled with stone, and the flag is flipped once
        Entity entity = mock(Entity.class);
        when(entity.isOutsideBorder()).thenReturn(true);
        Mockito.doReturn(true).when(self).isInsideWorldBorder(entity);
        WorldBorder border = mock(WorldBorder.class);
        when(border.contains(any(BlockPos.class))).thenReturn(false);
        Mockito.doReturn(border).when(self).getWorldBorder();
        assertTrue((boolean) Mixins.call(world, "getCollisionBoxes", entity, around, false, boxes));
        Mockito.verify(entity, Mockito.atLeastOnce()).setOutsideBorder(false);
        boxes.clear();
        // The early-out form stops at the first box, and refuses coordinates beyond the world limit
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
        // An entity exactly at the centre has no direction to be pushed in, so it is skipped
        Mockito.verify(centred, Mockito.never()).attackEntityFrom(any(), Mockito.anyFloat());
        assertTrue(knockback.isEmpty());

        // A player in range is remembered for the knockback it receives, and an exploder answers for the blocks
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
    void exposureRaysShareOneCursor() {
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.world.explosions.entity_raycast.WorldMixin.class);
        World self = (World) (Object) world;
        Mockito.doReturn(net.minecraft.world.WorldType.DEFAULT).when(self).getWorldType();
        Mockito.doReturn(Blocks.AIR.getDefaultState()).when(self).getBlockState(any());
        // Nothing in the way, so every sample ray reaches the centre
        assertEquals(1.0F, world.getBlockDensity(new Vec3d(0.5, 5.5, 0.5), new AxisAlignedBB(0, 5, 0, 1, 6, 1)));
        // A solid wall between the samples and the centre blocks them all
        Mockito.doReturn(Blocks.STONE.getDefaultState()).when(self).getBlockState(any());
        assertEquals(0.0F, world.getBlockDensity(new Vec3d(4.5, 5.5, 0.5), new AxisAlignedBB(0, 5, 0, 1, 6, 1)));
        // A degenerate box has no samples to take
        assertEquals(0.0F, world.getBlockDensity(new Vec3d(0.5, 5.5, 0.5), new AxisAlignedBB(0, 0, 0, -1, -1, -1)));
    }

    @Test
    void orbMergingFiltersTheOrbsItAbsorbs() {
        EntityXPOrbMixin orb = Mixins.instance(EntityXPOrbMixin.class);
        EntityXPOrb self = (EntityXPOrb) (Object) orb;
        World world = mock(World.class);
        Mixins.set(world, "isRemote", false);
        Mixins.set(orb, "world", world);
        Mixins.set(orb, "xpValue", 1);
        self.ticksExisted = 20;
        Mockito.doReturn(new AxisAlignedBB(0, 0, 0, 1, 1, 1)).when(self).getEntityBoundingBox();
        EntityXPOrbMixin neighbour = Mixins.instance(EntityXPOrbMixin.class);
        EntityXPOrb other = (EntityXPOrb) (Object) neighbour;
        EntityXPOrb dead = (EntityXPOrb) (Object) Mixins.instance(EntityXPOrbMixin.class);
        dead.isDead = true;
        // The predicate handed to the world is what rejects the orb itself and anything already dead
        when(world.getEntitiesWithinAABB(Mockito.eq(EntityXPOrb.class), any(), any())).thenAnswer(invocation -> {
            com.google.common.base.Predicate<EntityXPOrb> filter = invocation.getArgument(2);
            List<EntityXPOrb> candidates = new ArrayList<>();
            for (EntityXPOrb candidate : List.of(self, other, dead)) {
                if (filter.apply(candidate)) {
                    candidates.add(candidate);
                }
            }
            return candidates;
        });
        Mixins.call(orb, "equilibrium$absorbNeighbours", Mixins.ci());
        assertEquals(1, (int) Mixins.get(orb, "xpValue"));
        Mockito.verify(other).setDead();
        Mockito.verify(dead, Mockito.never()).setDead();
    }
}
