package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableSetMultimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.bdmajora.testing.MixinTargets.calls;
import static com.bdmajora.testing.MixinTargets.handler;
import static com.bdmajora.testing.MixinTargets.method;
import static com.bdmajora.testing.MixinTargets.resolve;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationMixinsTest {
    // Simulation distance used throughout: the minimum, so "in range" is chunks -5..5 around the player
    private static final int R = ImpetusGameOptions.MIN_SIMULATION_DISTANCE;
    private static final long NOW = 1_000L;

    private ImpetusGameOptions options;
    private boolean installed;
    private int previous;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void options() throws ClassNotFoundException {
        Class.forName(ImpetusVintage.class.getName());
        if (ImpetusVintage.options() == null) {
            Statics.set(ImpetusVintage.class, "CONFIG", ImpetusGameOptions.defaults());
            installed = true;
        }
        options = ImpetusVintage.options();
        previous = options.simulationDistance;
        options.simulationDistance = R;
    }

    @AfterEach
    void restore() {
        options.simulationDistance = previous;
        if (installed) {
            Statics.set(ImpetusVintage.class, "CONFIG", null);
        }
    }

    // ---- scheduled ticks ----

    @Test
    void dueInRangeTicksRunInOrderAndOutOfRangeOnesStayParkedUntouched() {
        WorldServerMixin world = server(false, player(0, 0));
        Pending pending = new Pending();
        NextTickListEntry far1 = pending.add(chunk(20, 0), 10, 0);
        NextTickListEntry near1 = pending.add(chunk(0, 0), 11, 0);
        NextTickListEntry far2 = pending.add(chunk(-20, 3), 12, 0);
        NextTickListEntry near2 = pending.add(chunk(R, -R), 13, 0);
        assertEquals(List.of(near1, near2), pending.clean(world, false, 65536));
        // Parked entries are the same objects, in both sets, with their time untouched
        assertEquals(List.of(far1, far2), new ArrayList<>(pending.tree));
        assertEquals(Set.of(far1, far2), pending.hash);
        assertEquals(10, far1.scheduledTime);
    }

    @Test
    void aBacklogOfParkedTicksNoLongerStarvesInRangeOnes() {
        // The old take-out-and-put-back version spent the per-tick budget on the parked backlog, which is always oldest
        WorldServerMixin world = server(false, player(0, 0));
        Pending pending = new Pending();
        // Distinct positions: vanilla never schedules the same block twice, and the hash set would merge them
        for (int i = 0; i < 500; i++) {
            pending.add(new BlockPos(480 + i, 64, 480), i, 0);
        }
        NextTickListEntry a = pending.add(chunk(1, 1), 900, 0);
        NextTickListEntry b = pending.add(chunk(-1, -1), 901, 0);
        // Budget of 2 (vanilla's 65536 cap scaled down): both in-range ticks still run
        assertEquals(List.of(a, b), pending.clean(world, false, 2));
        assertEquals(500, pending.tree.size());
        assertEquals(pending.tree.size(), pending.hash.size());
    }

    @Test
    void theLoopStopsAtTheFirstEntryNotYetDueEvenPastParkedOnes() {
        WorldServerMixin world = server(false, player(0, 0));
        Pending pending = new Pending();
        pending.add(chunk(40, 0), NOW - 5, 0);
        NextTickListEntry dueNow = pending.add(chunk(0, 0), NOW, 0);
        NextTickListEntry later = pending.add(new BlockPos(1, 64, 1), NOW + 1, 0);
        // "Due" is scheduledTime <= now, exactly like vanilla's break test
        assertEquals(List.of(dueNow), pending.clean(world, false, 65536));
        assertTrue(pending.tree.contains(later));
    }

    @Test
    void whenOnlyParkedTicksAreLeftTheLoopEndsCleanly() {
        WorldServerMixin world = server(false, player(0, 0));
        Pending pending = new Pending();
        pending.add(chunk(40, 0), 1, 0);
        pending.add(chunk(41, 0), 2, 0);
        assertEquals(List.of(), pending.clean(world, false, 65536));
        // The end marker never ends up in the sets
        assertEquals(2, pending.tree.size());
        NextTickListEntry marker = Statics.get(WorldServerMixin.class, "impetus$NO_DUE_TICK");
        assertEquals(Long.MAX_VALUE, marker.scheduledTime);
        assertFalse(pending.tree.contains(marker));
    }

    @Test
    void equalTimesKeepPriorityThenSchedulingOrder() {
        WorldServerMixin world = server(false, player(0, 0));
        Pending pending = new Pending();
        NextTickListEntry low = pending.add(chunk(0, 0), 5, 1);
        pending.add(chunk(50, 0), 5, -5);
        NextTickListEntry high = pending.add(new BlockPos(1, 64, 1), 5, -1);
        NextTickListEntry tie = pending.add(new BlockPos(2, 64, 2), 5, -1);
        assertEquals(List.of(high, tie, low), pending.clean(world, false, 65536));
    }

    @Test
    void parkedTicksRunOnceAPlayerComesNear() {
        EntityPlayer player = player(0, 0);
        WorldServerMixin world = server(false, player);
        Pending pending = new Pending();
        NextTickListEntry far = pending.add(chunk(20, 0), 1, 0);
        assertEquals(List.of(), pending.clean(world, false, 65536));
        // Next tick the player stands next to it: a fresh call starts from the front again
        player.posX = 20 * 16 + 8;
        assertEquals(List.of(far), pending.clean(world, false, 65536));
        assertTrue(pending.tree.isEmpty());
    }

    @Test
    void runAllPendingAndDedicatedServersTickEverything() {
        Pending pending = new Pending();
        NextTickListEntry far = pending.add(chunk(20, 0), 1, 0);
        NextTickListEntry notDue = pending.add(chunk(21, 0), NOW + 100, 0);
        // runAllPending (world save / unload flush) ignores both the range and the time
        assertEquals(List.of(far, notDue), pending.clean(server(false, player(0, 0)), true, 65536));
        Pending dedicated = new Pending();
        NextTickListEntry remote = dedicated.add(chunk(1000, 1000), 1, 0);
        assertEquals(List.of(remote), dedicated.clean(server(true), false, 65536));
    }

    @Test
    void withNoPlayersOnlyForcedChunksKeepTheirScheduledTicks() {
        WorldServerMixin world = server(false);
        Mockito.doReturn(ImmutableSetMultimap.of(new ChunkPos(9, 9), mock(net.minecraftforge.common.ForgeChunkManager.Ticket.class)))
                .when((World) (Object) world).getPersistentChunks();
        Pending pending = new Pending();
        pending.add(chunk(0, 0), 1, 0);
        NextTickListEntry forced = pending.add(chunk(9, 9), 2, 0);
        assertEquals(List.of(forced), pending.clean(world, false, 65536));
    }

    // ---- random ticks and weather ----

    @Test
    void randomTicksAndWeatherOnlyRunInsideTheDistance() {
        WorldServerMixin world = server(false, player(0, 0));
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        Mc.Recorded<ExtendedBlockStorage[]> storage = Mc.operation(sections);
        Mc.Recorded<Boolean> lightning = Mc.operation(true);
        Chunk near = chunkAt(R, -R);
        Chunk far = chunkAt(R + 1, 0);
        assertSame(sections, Mixins.call(world, "impetus$limitRandomTicks", near, storage));
        ExtendedBlockStorage[] none = Mixins.call(world, "impetus$limitRandomTicks", far, storage);
        assertEquals(0, none.length);
        // The empty array is shared, never a fresh allocation per chunk
        assertSame(none, Mixins.call(world, "impetus$limitRandomTicks", chunkAt(-40, 0), storage));
        assertEquals(1, storage.count());
        WorldProvider provider = mock(WorldProvider.class);
        assertTrue((boolean) Mixins.call(world, "impetus$limitWeatherTicks", provider, near, lightning));
        assertFalse((boolean) Mixins.call(world, "impetus$limitWeatherTicks", provider, far, lightning));
        // Out of range never asks the provider (short circuit), in range keeps the provider's own "no"
        assertEquals(1, lightning.count());
        assertFalse((boolean) Mixins.call(world, "impetus$limitWeatherTicks", provider, near, Mc.operation(false)));
    }

    // ---- entities and block entities ----

    @Test
    void entityTicksSkipOnlyNonPlayersOutsideTheDistanceAndOnlyForcedUpdates() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        try (MockedStatic<SimulationDistance> distance = Mockito.mockStatic(SimulationDistance.class)) {
            distance.when(() -> SimulationDistance.shouldTick(any(World.class), anyInt(), anyInt())).thenReturn(false);
            Entity mob = mock(Entity.class);
            CallbackInfo skipped = Mixins.ci();
            Mixins.call(world, "impetus$limitEntityTicks", mob, true, skipped);
            assertTrue(skipped.isCancelled());
            // Players always tick (they carry the simulation with them)
            CallbackInfo player = Mixins.ci();
            Mixins.call(world, "impetus$limitEntityTicks", mock(EntityPlayer.class), true, player);
            assertFalse(player.isCancelled());
            // A non-forced update (vanilla's chunk bookkeeping call) is never blocked
            CallbackInfo unforced = Mixins.ci();
            Mixins.call(world, "impetus$limitEntityTicks", mob, false, unforced);
            assertFalse(unforced.isCancelled());
            // Coordinates are floored before the shift: -0.5 is chunk -1, -16.0 is chunk -1, -16.01 is chunk -2
            mob.posX = -0.5;
            mob.posZ = -16.01;
            Mixins.call(world, "impetus$limitEntityTicks", mob, true, Mixins.ci());
            distance.verify(() -> SimulationDistance.shouldTick((World) (Object) world, -1, -2));
            mob.posX = -16.0;
            mob.posZ = 15.99;
            Mixins.call(world, "impetus$limitEntityTicks", mob, true, Mixins.ci());
            distance.verify(() -> SimulationDistance.shouldTick((World) (Object) world, -1, 0));
        }
    }

    @Test
    void aClientWorldNeverSkipsEntities() {
        WorldMixin world = Mixins.instance(WorldMixin.class);
        Entity mob = mock(Entity.class);
        mob.posX = 1e7;
        CallbackInfo ci = Mixins.ci();
        Mixins.call(world, "impetus$limitEntityTicks", mob, true, ci);
        assertFalse(ci.isCancelled());
    }

    @Test
    void blockEntityTicksAskTheRangeFirstAndKeepVanillasLoadedCheck() {
        WorldMixin mixin = Mixins.instance(WorldMixin.class);
        WorldServer world = plainServer(false, player(0, 0));
        Mc.Recorded<Boolean> loaded = Mc.operation(true);
        assertTrue((boolean) Mixins.call(mixin, "impetus$limitBlockEntityTicks", world, new BlockPos(16 * R + 15, 70, 0), false, loaded));
        assertFalse((boolean) Mixins.call(mixin, "impetus$limitBlockEntityTicks", world, new BlockPos(16 * (R + 1), 70, 0), false, loaded));
        assertEquals(1, loaded.count());
        assertEquals(false, loaded.last()[2]);
        // In range but vanilla says unloaded: stays unloaded
        assertFalse((boolean) Mixins.call(mixin, "impetus$limitBlockEntityTicks", world, BlockPos.ORIGIN, false, Mc.operation(false)));
    }

    // ---- natural spawning and the server's chunk distance ----

    @Test
    void chunksOutsideTheDistanceReadAsAlreadySeenSoTheyAreNeitherCountedNorEligible() {
        WorldEntitySpawnerMixin spawner = Mixins.instance(WorldEntitySpawnerMixin.class);
        WorldServer world = plainServer(false, player(0, 0));
        Set<ChunkPos> eligible = new HashSet<>();
        Mc.Recorded<Boolean> contains = Mc.operation(false);
        // In range: vanilla's own answer (not seen yet), so it is counted and may become eligible
        assertFalse((boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, new ChunkPos(R, -R), contains, world));
        // Out of range: "already seen", skipping vanilla's ++i and its eligibility checks, without consulting the set
        assertTrue((boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, new ChunkPos(R + 1, 0), contains, world));
        assertTrue((boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, new ChunkPos(0, -R - 1), contains, world));
        assertEquals(1, contains.count());
        // In range and really already seen (shared by two players): vanilla's true stands
        assertTrue((boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, new ChunkPos(0, 0), Mc.operation(true), world));
        // Dedicated servers: every chunk goes to vanilla
        Mc.Recorded<Boolean> dedicated = Mc.operation(false);
        assertFalse((boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, new ChunkPos(500, 500), dedicated, plainServer(true)));
        assertEquals(1, dedicated.count());
    }

    @Test
    void theVanillaLoopCountsOnlySimulatedChunksTowardTheCap() {
        // Vanilla's 17x17 loop around one player, run through the hook: at distance 5 only 11x11 chunks are counted
        WorldEntitySpawnerMixin spawner = Mixins.instance(WorldEntitySpawnerMixin.class);
        WorldServer world = plainServer(false, player(0.5, 0.5));
        Set<ChunkPos> eligible = new HashSet<>();
        Operation<Boolean> contains = args -> ((Set<?>) args[0]).contains(args[1]);
        int counted = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                ChunkPos pos = new ChunkPos(dx, dz);
                if (!(boolean) Mixins.call(spawner, "impetus$skipUnsimulatedChunks", eligible, pos, contains, world)) {
                    counted++;
                    if (dx != -8 && dx != 8 && dz != -8 && dz != 8) {
                        eligible.add(pos);
                    }
                }
            }
        }
        assertEquals((2 * R + 1) * (2 * R + 1), counted);
        assertEquals((2 * R + 1) * (2 * R + 1), eligible.size());
        assertTrue(eligible.stream().allMatch(pos -> Math.abs(pos.x) <= R && Math.abs(pos.z) <= R));
        // Monster cap at this distance is 70 * 121 / 289 = 29, not vanilla's 70 for a full square
        assertEquals(29, 70 * counted / 289);
    }

    @Test
    void theIntegratedServerKeepsTheLargerOfViewAndSimulationDistance() {
        IntegratedServerMixin server = Mixins.instance(IntegratedServerMixin.class);
        assertEquals(12, (int) Mixins.call(server, "impetus$serverChunkDistance", 12));
        assertEquals(R, (int) Mixins.call(server, "impetus$serverChunkDistance", 2));
        assertEquals(R, (int) Mixins.call(server, "impetus$serverChunkDistance", R));
        options.simulationDistance = 30;
        assertEquals(30, (int) Mixins.call(server, "impetus$serverChunkDistance", 12));
        // A raw out-of-range setting is clamped before it can ask the server for 1000 chunks
        options.simulationDistance = 1000;
        assertEquals(ImpetusGameOptions.MAX_SIMULATION_DISTANCE, (int) Mixins.call(server, "impetus$serverChunkDistance", 2));
    }

    // ---- the hooks land where they mean to in the real game code ----

    @Test
    void everySimulationHookResolvesToExactlyTheIntendedCalls() {
        MethodNode tickUpdates = method(WorldServer.class, "tickUpdates", "(Z)Z");
        List<AbstractInsnNode> first = resolve(tickUpdates, handler(WorldServerMixin.class, "impetus$nextSimulatedTick"));
        assertEquals(1, first.size());
        // It is the cleaning loop's first(), before the batch is iterated
        int iterate = tickUpdates.instructions.indexOf(calls(tickUpdates, "java/util/List", "iterator").get(0));
        assertTrue(tickUpdates.instructions.indexOf(first.get(0)) < iterate);

        MethodNode updateBlocks = method(WorldServer.class, "updateBlocks", "()V");
        assertEquals(1, resolve(updateBlocks, handler(WorldServerMixin.class, "impetus$limitRandomTicks")).size());
        assertEquals(2, resolve(updateBlocks, handler(WorldServerMixin.class, "impetus$limitWeatherTicks")).size());

        MethodNode updateEntities = method(World.class, "updateEntities", "()V");
        List<AbstractInsnNode> loaded = resolve(updateEntities, handler(WorldMixin.class, "impetus$limitBlockEntityTicks"));
        assertEquals(1, loaded.size());
        // It is the block entity loop's check: the TileEntity.getPos() just before feeds it
        AbstractInsnNode pos = loaded.get(0);
        while (!(pos instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("getPos"))) {
            pos = pos.getPrevious();
        }
        assertEquals("net/minecraft/tileentity/TileEntity", ((org.objectweb.asm.tree.MethodInsnNode) pos).owner);
        assertEquals(1, resolve(method(World.class, "updateEntityWithOptionalForce", "(Lnet/minecraft/entity/Entity;Z)V"),
                handler(WorldMixin.class, "impetus$limitEntityTicks")).size());

        List<AbstractInsnNode> seen = resolve(method(WorldEntitySpawner.class, "findChunksForSpawning", null),
                handler(WorldEntitySpawnerMixin.class, "impetus$skipUnsimulatedChunks"));
        assertEquals(1, seen.size());
        // It is the check that guards the mob-cap chunk count: IFNE past an IINC right after it
        AbstractInsnNode jump = seen.get(0).getNext();
        while (jump.getOpcode() < 0) {
            jump = jump.getNext();
        }
        assertEquals(org.objectweb.asm.Opcodes.IFNE, jump.getOpcode());
        AbstractInsnNode count = jump.getNext();
        while (count.getOpcode() < 0) {
            count = count.getNext();
        }
        assertEquals(org.objectweb.asm.Opcodes.IINC, count.getOpcode());
        // Every read of the view distance in tick is widened, so the "changed?" test and the value set agree
        assertEquals(3, resolve(method(IntegratedServer.class, "tick", "()V"),
                handler(IntegratedServerMixin.class, "impetus$serverChunkDistance")).size());
    }

    // ---- fixtures ----

    // Both vanilla tick sets plus the cleaning loop of WorldServer.tickUpdates, run around the hook.
    private static final class Pending {
        final TreeSet<NextTickListEntry> tree = new TreeSet<>();
        final Set<NextTickListEntry> hash = new HashSet<>();

        NextTickListEntry add(BlockPos pos, long time, int priority) {
            NextTickListEntry entry = new NextTickListEntry(pos, Blocks.STONE).setScheduledTime(time);
            entry.setPriority(priority);
            tree.add(entry);
            hash.add(entry);
            return entry;
        }

        List<NextTickListEntry> clean(WorldServerMixin world, boolean runAllPending, int budget) {
            int count = Math.min(tree.size(), budget);
            LocalRef<NextTickListEntry> cursor = Mc.ref(null);
            Operation<Object> first = args -> ((TreeSet<?>) args[0]).first();
            List<NextTickListEntry> batch = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                NextTickListEntry entry = Mixins.call(world, "impetus$nextSimulatedTick", tree, first, runAllPending, cursor);
                if (!runAllPending && entry.scheduledTime > NOW) {
                    break;
                }
                tree.remove(entry);
                hash.remove(entry);
                batch.add(entry);
            }
            assertEquals(tree.size(), hash.size(), "TickNextTick list out of synch");
            return batch;
        }
    }

    private static WorldServerMixin server(boolean dedicated, EntityPlayer... players) {
        WorldServerMixin mixin = Mixins.instance(WorldServerMixin.class);
        WorldServer world = (WorldServer) (Object) mixin;
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isDedicatedServer()).thenReturn(dedicated);
        Mockito.doReturn(server).when(world).getMinecraftServer();
        Mockito.doReturn(NOW).when(world).getTotalWorldTime();
        Mockito.doReturn(ImmutableSetMultimap.of()).when(world).getPersistentChunks();
        Mixins.set(world, "playerEntities", new ArrayList<>(List.of(players)));
        return mixin;
    }

    private static WorldServer plainServer(boolean dedicated, EntityPlayer... players) {
        WorldServer world = mock(WorldServer.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isDedicatedServer()).thenReturn(dedicated);
        when(world.getMinecraftServer()).thenReturn(server);
        when(world.getPersistentChunks()).thenReturn(ImmutableSetMultimap.of());
        Mixins.set(world, "playerEntities", new ArrayList<>(List.of(players)));
        return world;
    }

    private static EntityPlayer player(double x, double z) {
        EntityPlayer player = mock(EntityPlayer.class);
        player.posX = x;
        player.posZ = z;
        return player;
    }

    private static BlockPos chunk(int chunkX, int chunkZ) {
        return new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
    }

    private static Chunk chunkAt(int x, int z) {
        Chunk chunk = Mc.uninitialized(Chunk.class);
        Mixins.set(chunk, "x", x);
        Mixins.set(chunk, "z", z);
        return chunk;
    }

    @Test
    void theStaticOnlyBitsConstruct() {
        assertNotNull(Mixins.instance(IntegratedServerMixin.class));
        assertArrayEquals(new ExtendedBlockStorage[0], Statics.get(WorldServerMixin.class, "impetus$NO_RANDOM_TICK_SECTIONS"));
    }
}
