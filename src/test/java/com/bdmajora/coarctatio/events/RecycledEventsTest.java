package com.bdmajora.coarctatio.events;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RecycledEventsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void theTickEventsAreOneInstancePerPhase() {
        assertSame(RecycledEvents.clientTick(TickEvent.Phase.START), RecycledEvents.clientTick(TickEvent.Phase.START));
        assertNotSame(RecycledEvents.clientTick(TickEvent.Phase.START), RecycledEvents.clientTick(TickEvent.Phase.END));
        assertEquals(TickEvent.Phase.END, RecycledEvents.clientTick(TickEvent.Phase.END).phase);

        assertSame(RecycledEvents.serverTick(TickEvent.Phase.START), RecycledEvents.serverTick(TickEvent.Phase.START));
        assertNotSame(RecycledEvents.serverTick(TickEvent.Phase.START), RecycledEvents.serverTick(TickEvent.Phase.END));

        World world = mock(World.class);
        assertSame(RecycledEvents.worldTick(TickEvent.Phase.START, world),
                RecycledEvents.worldTick(TickEvent.Phase.START, world));
        assertNotSame(RecycledEvents.worldTick(TickEvent.Phase.START, world),
                RecycledEvents.worldTick(TickEvent.Phase.END, world));

        assertSame(RecycledEvents.renderTick(TickEvent.Phase.START, 0.5F),
                RecycledEvents.renderTick(TickEvent.Phase.START, 0.25F));
        assertNotSame(RecycledEvents.renderTick(TickEvent.Phase.START, 0F),
                RecycledEvents.renderTick(TickEvent.Phase.END, 0F));
        assertNotNull(Mixins.construct(RecycledEvents.class));
    }

    @Test
    void aPlayerTickIsPerThreadAndNestedPostsGetTheirOwnEvent() {
        // Allocated rather than mocked: instrumenting EntityPlayer would undo the constructor the harness adds to it
        EntityPlayer player = Mc.uninitialized(net.minecraft.entity.player.EntityPlayerMP.class);
        Mixins.set(player, "world", mock(World.class));

        TickEvent.PlayerTickEvent outer = RecycledEvents.playerTick(TickEvent.Phase.START, player);
        // A handler that triggers the same event again must not be handed the event it is inside
        TickEvent.PlayerTickEvent nested = RecycledEvents.playerTick(TickEvent.Phase.START, player);
        assertNotSame(outer, nested);
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
        // Once both posts have returned the pooled instance is free again
        assertSame(outer, RecycledEvents.playerTick(TickEvent.Phase.START, player));
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
        // Releasing more often than acquiring cannot drive the count negative
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
        assertSame(outer, RecycledEvents.playerTick(TickEvent.Phase.START, player));
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);

        TickEvent.PlayerTickEvent end = RecycledEvents.playerTick(TickEvent.Phase.END, player);
        assertNotSame(outer, end);
        RecycledEvents.releasePlayerTick(TickEvent.Phase.END);
    }

    @Test
    void eachCapabilityTargetHasItsOwnRecycledEvent() {
        AttachCapabilitiesEvent<TileEntity> tile = RecycledEvents.tileCapabilities(mock(TileEntity.class));
        RecycledEvents.releaseCapabilities(tile);
        assertSame(tile, RecycledEvents.tileCapabilities(mock(TileEntity.class)));
        RecycledEvents.releaseCapabilities(tile);

        AttachCapabilitiesEvent<Entity> entity = RecycledEvents.entityCapabilities(mock(Entity.class));
        RecycledEvents.releaseCapabilities(entity);
        AttachCapabilitiesEvent<ItemStack> stack = RecycledEvents.stackCapabilities(ItemStack.EMPTY);
        RecycledEvents.releaseCapabilities(stack);
        AttachCapabilitiesEvent<Chunk> chunk = RecycledEvents.chunkCapabilities(mock(Chunk.class));
        RecycledEvents.releaseCapabilities(chunk);
        assertNotSame(tile, entity);
        assertNotSame(entity, stack);
        assertNotSame(stack, chunk);

        // The village and world variants are never recycled, so releasing one does nothing
        RecycledEvents.releaseCapabilities(new AttachCapabilitiesEvent<>(World.class, mock(World.class)));
    }

    @Test
    void aRecycledEventForgetsWhatTheBusWroteOnIt() {
        // Tick events are neither cancelable nor result-carrying, so resetting one only clears the dispatch phase
        TickEvent.ClientTickEvent event = RecycledEvents.clientTick(TickEvent.Phase.START);
        assertFalse(event.isCanceled());
        assertSame(event, RecycledEvents.clientTick(TickEvent.Phase.START));
        assertNull(event.getPhase());

        // The phase only ever advances, the way Forge's own bus expects
        assertEquals(EventPriority.NORMAL, RecyclableEvent.advancePhase(EventPriority.HIGH, EventPriority.NORMAL));
        assertEquals(EventPriority.HIGHEST, RecyclableEvent.advancePhase(null, EventPriority.HIGHEST));
        assertThrows(IllegalArgumentException.class,
                () -> RecyclableEvent.advancePhase(EventPriority.NORMAL, EventPriority.HIGH));
        assertThrows(NullPointerException.class,
                () -> RecyclableEvent.advancePhase(EventPriority.NORMAL, null));
    }

    @Test
    void everyRefreshIsANoOpUntilAnEventClassOverridesIt() {
        // The defaults exist so RecycledEvents can call them on any event; the per-class mixins do the real work
        RecyclableEvent event = (RecyclableEvent) RecycledEvents.clientTick(TickEvent.Phase.START);
        event.coarctatio$refreshSide(net.minecraftforge.fml.relauncher.Side.CLIENT);
        event.coarctatio$refreshPlayer(null);
        event.coarctatio$refreshWorld(null);
        event.coarctatio$refreshRenderTime(1F);
        event.coarctatio$refreshObject(null);
        event.coarctatio$refreshBlock(null, null, null);
        event.coarctatio$refreshNeighbors(null, false);
    }
}
