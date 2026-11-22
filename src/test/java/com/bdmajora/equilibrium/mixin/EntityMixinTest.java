package com.bdmajora.equilibrium.mixin;

import com.bdmajora.equilibrium.common.entity.FlagCache;
import com.bdmajora.equilibrium.common.entity.ReducedRadiusQuery;
import com.bdmajora.equilibrium.common.world.ChunkAccess;
import com.bdmajora.equilibrium.common.world.CountedInventoryEntity;
import com.bdmajora.equilibrium.mixin.alloc.deep_passengers.EntityMixin;
import com.bdmajora.equilibrium.mixin.alloc.enum_values.piston_block.BlockPistonBaseMixin;
import com.bdmajora.equilibrium.mixin.alloc.enum_values.piston_handler.BlockPistonStructureHelperMixin;
import com.bdmajora.equilibrium.mixin.alloc.enum_values.redstone_wire.BlockRedstoneWireMixin;
import com.bdmajora.equilibrium.mixin.entity.fast_hand_swing.EntityLivingBaseMixin;
import com.bdmajora.equilibrium.mixin.entity.fast_retrieval.WorldMixin;
import com.bdmajora.equilibrium.mixin.entity.fast_spawn_preparation.EntityEntryMixin;
import com.bdmajora.equilibrium.mixin.entity.flag_cache.EntityDataManagerMixin;
import com.bdmajora.equilibrium.mixin.entity.tracker_vertical_range.EntityTrackerEntryMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.EntityEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void reset() {
        ShadowStubs.clear();
        World.MAX_ENTITY_RADIUS = 2.0D;
    }

    @Test
    void enumValuesAreReusedRatherThanCloned() {
        assertSame(EnumFacing.VALUES, Mixins.call(Mixins.instance(BlockPistonBaseMixin.class), "equilibrium$reuseFacingArray"));
        assertSame(EnumFacing.VALUES, Mixins.call(Mixins.instance(BlockPistonStructureHelperMixin.class), "equilibrium$reuseFacingArray"));
        assertSame(EnumFacing.VALUES, Mixins.call(Mixins.instance(BlockRedstoneWireMixin.class), "equilibrium$reuseFacingArray"));
    }

    @Test
    void recursivePassengersSkipTheHashMap() {
        EntityMixin entity = Mixins.instance(EntityMixin.class);
        Mockito.doReturn(List.of()).when(entity).getPassengers();
        List<Object[]> collected = new ArrayList<>();
        ShadowStubs.on(entity, "getRecursivePassengersByType", args -> {
            collected.add(args);
            return null;
        });
        Collection<Entity> none = entity.getRecursivePassengers();
        assertTrue(none.isEmpty());
        assertEquals(Entity.class, collected.get(0)[0]);
        Entity passenger = mock(Entity.class);
        Mockito.doReturn(List.of(passenger)).when(entity).getPassengers();
        ShadowStubs.on(entity, "getRecursivePassengersByType", args -> {
            @SuppressWarnings("unchecked")
            Set<Entity> into = (Set<Entity>) args[1];
            into.add(passenger);
            return null;
        });
        assertEquals(List.of(passenger), new ArrayList<>(entity.getRecursivePassengers()));
        assertEquals(List.of(passenger), new ArrayList<>(entity.getRecursivePassengersByType(EntityZombie.class)));
        Mockito.doReturn(List.of()).when(entity).getPassengers();
        ShadowStubs.on(entity, "getRecursivePassengersByType", args -> null);
        assertTrue(entity.getRecursivePassengersByType(EntityZombie.class).isEmpty());
    }

    @Test
    void idleEntitiesSkipTheSwingUpdate() {
        EntityLivingBaseMixin living = Mixins.instance(EntityLivingBaseMixin.class);
        var idle = Mixins.ci();
        Mixins.call(living, "equilibrium$skipIdleSwing", idle);
        assertTrue(idle.isCancelled());
        living.isSwingInProgress = true;
        var swinging = Mixins.ci();
        Mixins.call(living, "equilibrium$skipIdleSwing", swinging);
        assertFalse(swinging.isCancelled());
        living.isSwingInProgress = false;
        living.swingProgressInt = 3;
        var finishing = Mixins.ci();
        Mixins.call(living, "equilibrium$skipIdleSwing", finishing);
        assertFalse(finishing.isCancelled());
        living.swingProgressInt = 0;
        living.swingProgress = 0.5F;
        var settling = Mixins.ci();
        Mixins.call(living, "equilibrium$skipIdleSwing", settling);
        assertFalse(settling.isCancelled());
    }

    @Test
    void entityQueriesResolveEachChunkOnce() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        Chunk chunk = mock(Chunk.class);
        Mockito.doReturn(chunk).when(world).equilibrium$getLoadedChunk(0, 0);
        EntityZombie zombie = mock(EntityZombie.class);
        Mockito.doAnswer(invocation -> {
            invocation.<List<Entity>>getArgument(2).add(zombie);
            return null;
        }).when(chunk).getEntitiesWithinAABBForEntity(any(), any(), any(), any());
        List<Entity> found = world.getEntitiesInAABBexcluding(null, new AxisAlignedBB(1, 0, 1, 2, 5, 2), e -> true);
        assertEquals(List.of(zombie), found);
        // Checks only the touched chunks are read.
        Mockito.verify(world).equilibrium$getLoadedChunk(-1, -1);
        Mockito.verify(world).equilibrium$getLoadedChunk(0, 0);
    }

    @Test
    void spawnPreparationSpinsAConstructorLambda() {
        EntityEntryMixin entry = Mixins.instance(EntityEntryMixin.class);
        // A concrete entity with a (World) constructor gets a direct factory
        Mixins.set(entry, "cls", EntityItem.class);
        var spun = Mixins.ci();
        Mixins.call(entry, "equilibrium$spinConstructorLambda", spun);
        assertTrue(spun.isCancelled());
        Function<World, ? extends Entity> factory = Mixins.get(entry, "factory");
        assertNotNull(factory);
        assertInstanceOf(EntityItem.class, factory.apply(null));

        // Checks the factory fallbacks.
        Mixins.set(entry, "factory", null);
        Mixins.set(entry, "cls", net.minecraft.entity.EntityLivingBase.class);
        var abstractEntity = Mixins.ci();
        Mixins.call(entry, "equilibrium$spinConstructorLambda", abstractEntity);
        assertFalse(abstractEntity.isCancelled());
        assertNull(Mixins.get(entry, "factory"));
        Mixins.set(entry, "cls", null);
        var missing = Mixins.ci();
        Mixins.call(entry, "equilibrium$spinConstructorLambda", missing);
        assertFalse(missing.isCancelled());
        Mixins.set(entry, "cls", NoWorldConstructor.class);
        var wrongShape = Mixins.ci();
        Mixins.call(entry, "equilibrium$spinConstructorLambda", wrongShape);
        assertFalse(wrongShape.isCancelled());
        assertNull(Mixins.get(entry, "factory"));
        assertNotNull(Mixins.call(EntityEntryMixin.class, "privateLookup", EntityEntry.class));
    }

    // An entity class Forge could register but which has no (World) constructor to bind
    public static final class NoWorldConstructor extends Entity {
        public NoWorldConstructor(World world, int unused) {
            super(world);
        }

        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}
    }

    @Test
    void entityFlagsAreCachedUntilTheDataManagerWrites() throws Exception {
        var entity = Mixins.instance(com.bdmajora.equilibrium.mixin.entity.flag_cache.EntityMixin.class);
        EntityDataManager manager = mock(EntityDataManager.class);
        DataParameter<Byte> flags = Mixins.get(com.bdmajora.equilibrium.mixin.entity.flag_cache.EntityMixin.class, "FLAGS");
        Mixins.set(entity, "dataManager", manager);
        when(manager.get(flags)).thenReturn((byte) 0b0000_0010);

        var sprinting = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 1, sprinting);
        assertTrue(sprinting.getReturnValue());
        var sneaking = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 0, sneaking);
        assertFalse(sneaking.getReturnValue());
        // The byte is read once and then answered from the cache
        Mockito.verify(manager, Mockito.times(1)).get(flags);
        when(manager.get(flags)).thenReturn((byte) 0b0000_0001);
        var stale = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 1, stale);
        assertTrue(stale.getReturnValue());

        // Any write through the data manager drops it
        EntityDataManagerMixin dataManager = Mixins.instance(EntityDataManagerMixin.class);
        Mixins.set(dataManager, "entity", entity);
        Mixins.call(dataManager, "equilibrium$invalidateOnSet", flags, (byte) 1, Mixins.ci());
        var refreshed = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 0, refreshed);
        assertTrue(refreshed.getReturnValue());
        when(manager.get(flags)).thenReturn((byte) 0);
        Mixins.call(dataManager, "equilibrium$invalidateOnSync", List.of(), Mixins.ci());
        var cleared = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 0, cleared);
        assertFalse(cleared.getReturnValue());

        // An entity whose data manager is not built yet falls through to vanilla
        Mixins.set(entity, "dataManager", null);
        ((FlagCache) entity).equilibrium$invalidateFlags();
        var unbuilt = Mixins.<Boolean>cir();
        Mixins.call(entity, "equilibrium$readCachedFlag", 0, unbuilt);
        assertFalse(unbuilt.isCancelled());

        // Checks both writes clear the cache.
        String notify = "Lnet/minecraft/entity/Entity;notifyDataManagerChange(Lnet/minecraft/network/datasync/DataParameter;)V";
        assertEquals(List.of("INVOKE " + notify, "RETURN"), injectionPoints(EntityDataManagerMixin.class, "equilibrium$invalidateOnSet"));
        assertEquals(List.of("INVOKE " + notify, "RETURN"), injectionPoints(EntityDataManagerMixin.class, "equilibrium$invalidateOnSync"));
    }

    // Reads the At points of a handler from the class file.
    private static List<String> injectionPoints(Class<?> mixin, String handler) throws java.io.IOException {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        try (var in = mixin.getResourceAsStream(mixin.getSimpleName() + ".class")) {
            new org.objectweb.asm.ClassReader(in).accept(node, 0);
        }
        List<String> points = new ArrayList<>();
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            if (!method.name.equals(handler)) {
                continue;
            }
            List<org.objectweb.asm.tree.AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) {
                annotations.addAll(method.visibleAnnotations);
            }
            if (method.invisibleAnnotations != null) {
                annotations.addAll(method.invisibleAnnotations);
            }
            for (org.objectweb.asm.tree.AnnotationNode inject : annotations) {
                for (int i = 0; inject.values != null && i < inject.values.size(); i += 2) {
                    if (!"at".equals(inject.values.get(i))) {
                        continue;
                    }
                    for (Object at : (List<?>) inject.values.get(i + 1)) {
                        List<Object> values = ((org.objectweb.asm.tree.AnnotationNode) at).values;
                        String value = (String) values.get(values.indexOf("value") + 1);
                        int target = values.indexOf("target");
                        points.add(target < 0 ? value : value + " " + values.get(target + 1));
                    }
                }
            }
        }
        return points;
    }

    @Test
    void trackingAlsoChecksTheVerticalDistance() {
        EntityTrackerEntryMixin tracker = Mixins.instance(EntityTrackerEntryMixin.class);
        Mixins.set(tracker, "range", 16);
        Mixins.set(tracker, "maxRange", 64);
        Mixins.set(tracker, "encodedPosY", 64L * 4096L);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.posY = 70.0D;
        assertTrue((boolean) Mixins.call(tracker, "equilibrium$alsoCheckVertical", true, player));
        player.posY = 200.0D;
        assertFalse((boolean) Mixins.call(tracker, "equilibrium$alsoCheckVertical", true, player));
        assertFalse((boolean) Mixins.call(tracker, "equilibrium$alsoCheckVertical", false, player));
    }

    @Test
    void collisionQueriesFallBackToTheVanillaRadius() {
        var living = Mixins.instance(com.bdmajora.equilibrium.mixin.entity.collisions.reduced_radius.EntityLivingBaseMixin.class);
        var world = Mixins.instance(com.bdmajora.equilibrium.mixin.entity.collisions.reduced_radius.WorldMixin.class);
        World target = mock(World.class);
        net.minecraft.world.chunk.IChunkProvider provider = mock(net.minecraft.world.chunk.IChunkProvider.class);
        when(target.getChunkProvider()).thenReturn(provider);
        EntityZombie zombie = Mc.uninitialized(EntityZombie.class);
        AxisAlignedBB box = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
        List<Entity> vanilla = List.of();
        int[] originals = new int[1];
        Operation<List<Entity>> original = args -> {
            originals[0]++;
            return vanilla;
        };

        // While no mod has raised the radius the original query is used
        assertSame(vanilla, Mixins.call(living, "equilibrium$collideWithVanillaRadius", target, zombie, box, null, original));
        assertSame(vanilla, Mixins.call(world, "equilibrium$excludingWithVanillaRadius", target, zombie, box, null, original));
        assertEquals(2, originals[0]);

        World.MAX_ENTITY_RADIUS = 8.0D;
        assertNotSame(vanilla, Mixins.call(living, "equilibrium$collideWithVanillaRadius", target, zombie, box, null, original));
        assertNotSame(vanilla, Mixins.call(world, "equilibrium$excludingWithVanillaRadius", target, zombie, box, null, original));
        assertEquals(2, originals[0]);
        // Checks the vanilla fallbacks.
        assertSame(vanilla, Mixins.call(living, "equilibrium$collideWithVanillaRadius", target, null, box, null, original));
        assertSame(vanilla, Mixins.call(world, "equilibrium$excludingWithVanillaRadius", target,
                Mc.uninitialized(EntityPlayerMP.class), box, null, original));
        assertEquals(4, originals[0]);
        assertTrue(ReducedRadiusQuery.applies());
    }

    @Test
    void inventoryEntitiesAreCountedPerWorld() {
        var entity = Mixins.instance(com.bdmajora.equilibrium.mixin.util.data_storage.EntityMixin.class);
        assertNull(entity.equilibrium$getCountedWorld());
        World world = mock(World.class);
        entity.equilibrium$setCountedWorld(world);
        assertSame(world, entity.equilibrium$getCountedWorld());

        var tracker = Mixins.instance(com.bdmajora.equilibrium.mixin.util.data_storage.WorldMixin.class);
        assertFalse(tracker.equilibrium$mayHaveInventoryEntities());
        Entity inventoryEntity = Mc.mock(Entity.class, net.minecraft.inventory.IInventory.class, CountedInventoryEntity.class);
        CountedInventoryEntity counted = (CountedInventoryEntity) inventoryEntity;
        Mockito.doAnswer(new org.mockito.stubbing.Answer<Object>() {
            private World held;

            @Override
            public Object answer(org.mockito.invocation.InvocationOnMock invocation) {
                if (invocation.getMethod().getName().endsWith("setCountedWorld")) {
                    held = invocation.getArgument(0);
                    return null;
                }
                return held;
            }
        }).when(counted).equilibrium$getCountedWorld();
        Mockito.doAnswer(invocation -> {
            Mockito.doReturn(invocation.<World>getArgument(0)).when(counted).equilibrium$getCountedWorld();
            return null;
        }).when(counted).equilibrium$setCountedWorld(any());

        Mixins.call(tracker, "equilibrium$countInventoryEntityAdded", inventoryEntity, Mixins.ci());
        assertTrue(tracker.equilibrium$mayHaveInventoryEntities());
        // Adding the same entity twice counts once
        Mixins.call(tracker, "equilibrium$countInventoryEntityAdded", inventoryEntity, Mixins.ci());
        Mixins.call(tracker, "equilibrium$countInventoryEntityRemoved", inventoryEntity, Mixins.ci());
        assertFalse(tracker.equilibrium$mayHaveInventoryEntities());
        // A removal with no matching addition cannot drive the count below zero
        Mixins.call(tracker, "equilibrium$countInventoryEntityRemoved", inventoryEntity, Mixins.ci());
        assertFalse(tracker.equilibrium$mayHaveInventoryEntities());
        // Entities that hold no inventory are ignored entirely
        Entity plain = mock(Entity.class);
        Mixins.call(tracker, "equilibrium$countInventoryEntityAdded", plain, Mixins.ci());
        Mixins.call(tracker, "equilibrium$countInventoryEntityRemoved", plain, Mixins.ci());
        assertFalse(tracker.equilibrium$mayHaveInventoryEntities());
        Entity uncounted = Mc.mock(Entity.class, net.minecraft.inventory.IInventory.class);
        Mixins.call(tracker, "equilibrium$countInventoryEntityAdded", uncounted, Mixins.ci());
        assertFalse(tracker.equilibrium$mayHaveInventoryEntities());
    }
}
