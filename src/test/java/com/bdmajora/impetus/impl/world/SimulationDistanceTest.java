package com.bdmajora.impetus.impl.world;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.google.common.collect.ImmutableSetMultimap;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationDistanceTest {
    private ImpetusGameOptions options;
    private boolean installed;
    private int previous;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void options() throws ClassNotFoundException {
        // Edits whatever options object is live instead of swapping the static (the JIT may have folded it)
        Class.forName(ImpetusVintage.class.getName());
        if (ImpetusVintage.options() == null) {
            Statics.set(ImpetusVintage.class, "CONFIG", ImpetusGameOptions.defaults());
            installed = true;
        }
        options = ImpetusVintage.options();
        previous = options.simulationDistance;
        options.simulationDistance = ImpetusGameOptions.MIN_SIMULATION_DISTANCE;
    }

    @AfterEach
    void restore() {
        options.simulationDistance = previous;
        if (installed) {
            Statics.set(ImpetusVintage.class, "CONFIG", null);
        }
    }

    @Test
    void clientWorldsAndDedicatedServersAlwaysTick() {
        assertTrue(SimulationDistance.shouldTick(mock(WorldClient.class), 1_000_000, -1_000_000));
        WorldServer dedicated = server(true);
        assertTrue(SimulationDistance.shouldTick(dedicated, 1_000_000, -1_000_000));
        // A dedicated server never looks at players or forced chunks
        verify(dedicated, never()).getPersistentChunks();
    }

    @Test
    void theRadiusIsASquareOfChunksAroundEachPlayerInclusive() {
        WorldServer world = server(false, player(0.5, 0.5));
        int r = ImpetusGameOptions.MIN_SIMULATION_DISTANCE;
        assertTrue(SimulationDistance.shouldTick(world, 0, 0));
        assertTrue(SimulationDistance.shouldTick(world, r, 0));
        assertTrue(SimulationDistance.shouldTick(world, -r, -r));
        assertTrue(SimulationDistance.shouldTick(world, r, -r));
        assertFalse(SimulationDistance.shouldTick(world, r + 1, 0));
        assertFalse(SimulationDistance.shouldTick(world, 0, -r - 1));
        assertFalse(SimulationDistance.shouldTick(world, r, r + 1));
    }

    @Test
    void negativePositionsFloorIntoTheRightChunk() {
        // -0.5 is chunk -1, not 0: a truncating cast would let chunk 5 tick and stop chunk -6
        WorldServer world = server(false, player(-0.5, -0.5));
        int r = ImpetusGameOptions.MIN_SIMULATION_DISTANCE;
        assertTrue(SimulationDistance.shouldTick(world, -1 - r, -1 - r));
        assertFalse(SimulationDistance.shouldTick(world, r, 0));
        // The BlockPos form shifts block coordinates the same way
        assertTrue(SimulationDistance.shouldTick(world, new BlockPos(-16 * (r + 1), 64, -1)));
        assertFalse(SimulationDistance.shouldTick(world, new BlockPos(16 * r, 64, 0)));
    }

    @Test
    void anyOnePlayerInRangeIsEnough() {
        WorldServer world = server(false, player(0, 0), player(16 * 100, 16 * 100));
        assertTrue(SimulationDistance.shouldTick(world, 100, 100));
        assertTrue(SimulationDistance.shouldTick(world, 0, 0));
        assertFalse(SimulationDistance.shouldTick(world, 50, 50));
    }

    @Test
    void withNoPlayersOnlyForcedChunksTick() {
        WorldServer world = server(false);
        assertFalse(SimulationDistance.shouldTick(world, 0, 0));
        ForgeChunkManager.Ticket ticket = mock(ForgeChunkManager.Ticket.class);
        when(world.getPersistentChunks()).thenReturn(ImmutableSetMultimap.of(new ChunkPos(20, -20), ticket));
        assertTrue(SimulationDistance.shouldTick(world, 20, -20));
        assertFalse(SimulationDistance.shouldTick(world, 20, 20));
        assertFalse(SimulationDistance.shouldTick(world, -20, -20));
    }

    @Test
    void theSettingIsClampedBeforeUse() {
        WorldServer world = server(false, player(0, 0));
        // A hand-edited config below the minimum still ticks the minimum square
        options.simulationDistance = 0;
        assertTrue(SimulationDistance.shouldTick(world, ImpetusGameOptions.MIN_SIMULATION_DISTANCE, 0));
        options.simulationDistance = 10_000;
        assertEquals(ImpetusGameOptions.MAX_SIMULATION_DISTANCE, SimulationDistance.getLocalDistance());
        assertTrue(SimulationDistance.shouldTick(world, ImpetusGameOptions.MAX_SIMULATION_DISTANCE, 0));
        assertFalse(SimulationDistance.shouldTick(world, ImpetusGameOptions.MAX_SIMULATION_DISTANCE + 1, 0));
        assertNotNull(Mixins.construct(SimulationDistance.class));
    }

    @Test
    void extremeChunkCoordinatesDoNotWrapIntoRange() {
        // int subtraction would wrap: MIN_VALUE - 0 stays MIN_VALUE and abs(MIN_VALUE) is negative, i.e. "in range"
        WorldServer world = server(false, player(0.5, 0.5));
        assertFalse(SimulationDistance.shouldTick(world, Integer.MIN_VALUE, 0));
        assertFalse(SimulationDistance.shouldTick(world, 0, Integer.MIN_VALUE));
        assertFalse(SimulationDistance.shouldTick(world, Integer.MAX_VALUE, Integer.MAX_VALUE));
        // And from the other side: a player at the far negative end, chunk MAX far away
        WorldServer edge = server(false, player(-30_000_000.0, -30_000_000.0));
        assertFalse(SimulationDistance.shouldTick(edge, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(SimulationDistance.shouldTick(edge, -1_875_000 + ImpetusGameOptions.MIN_SIMULATION_DISTANCE, -1_875_000));
    }

    @Test
    void playersAtTheWorldBorderStillTickTheirSurroundings() {
        WorldServer world = server(false, player(29_999_999.0, -29_999_999.0));
        int r = ImpetusGameOptions.MIN_SIMULATION_DISTANCE;
        assertTrue(SimulationDistance.shouldTick(world, 1_874_999, -1_875_000));
        assertTrue(SimulationDistance.shouldTick(world, 1_874_999 - r, -1_875_000 + r));
        assertFalse(SimulationDistance.shouldTick(world, 1_874_999 - r - 1, -1_875_000));
        assertTrue(SimulationDistance.shouldTick(world, new BlockPos(29_999_999, 0, -29_999_999)));
    }

    @Test
    void spectatorsAndLanGuestsCountLikeAnyPlayer() {
        // Every entry of playerEntities carries the simulation, as with vanilla chunk loading
        EntityPlayer spectator = player(16 * 50, 0);
        when(spectator.isSpectator()).thenReturn(true);
        WorldServer world = server(false, player(0, 0), spectator, player(0, 16 * 80));
        assertTrue(SimulationDistance.shouldTick(world, 50, 0));
        assertTrue(SimulationDistance.shouldTick(world, 0, 80));
        assertFalse(SimulationDistance.shouldTick(world, 25, 40));
    }

    @Test
    void forcedChunksAddToThePlayersSquares() {
        WorldServer world = server(false, player(0, 0));
        when(world.getPersistentChunks()).thenReturn(ImmutableSetMultimap.of(new ChunkPos(100, 100), mock(ForgeChunkManager.Ticket.class)));
        // Players in range answer without even asking for the forced chunks
        assertTrue(SimulationDistance.shouldTick(world, 0, 0));
        verify(world, never()).getPersistentChunks();
        assertTrue(SimulationDistance.shouldTick(world, 100, 100));
        // A forced chunk does not extend a radius around itself
        assertFalse(SimulationDistance.shouldTick(world, 101, 100));
        verify(world, org.mockito.Mockito.times(2)).getPersistentChunks();
    }

    @Test
    void heightPlaysNoPart() {
        WorldServer world = server(false, player(0, 0));
        assertTrue(SimulationDistance.shouldTick(world, new BlockPos(0, -10_000, 0)));
        assertTrue(SimulationDistance.shouldTick(world, new BlockPos(0, 10_000, 0)));
        assertFalse(SimulationDistance.shouldTick(world, new BlockPos(16 * 40, 64, 0)));
    }

    private static WorldServer server(boolean dedicated, EntityPlayer... players) {
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
}
