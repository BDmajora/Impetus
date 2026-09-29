package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void aRecycledTickEventKeepsItsOwnPhaseAndSide() {
        TickEventMixin event = Mixins.instance(TickEventMixin.class);
        assertNull(event.getPhase());
        event.setPhase(EventPriority.HIGH);
        assertEquals(EventPriority.HIGH, event.getPhase());
        // The bus only ever advances the phase, and a recycled event forgets it before the next post
        assertThrows(IllegalArgumentException.class, () -> event.setPhase(EventPriority.HIGHEST));
        event.coarctatio$resetPhase();
        assertNull(event.getPhase());

        event.coarctatio$refreshSide(Side.CLIENT);
        assertEquals(Side.CLIENT, Mixins.get(event, "side"));
    }

    @Test
    void eachTickEventRefreshesTheOneFieldItCarries() {
        PlayerTickEventMixin playerTick = Mixins.instance(PlayerTickEventMixin.class);
        EntityPlayer player = Mc.uninitialized(net.minecraft.entity.player.EntityPlayerMP.class);
        playerTick.coarctatio$refreshPlayer(player);
        assertSame(player, Mixins.get(playerTick, "player"));

        WorldTickEventMixin worldTick = Mixins.instance(WorldTickEventMixin.class);
        World world = mock(World.class);
        worldTick.coarctatio$refreshWorld(world);
        assertSame(world, Mixins.get(worldTick, "world"));

        RenderTickEventMixin renderTick = Mixins.instance(RenderTickEventMixin.class);
        renderTick.coarctatio$refreshRenderTime(0.75F);
        assertEquals(0.75F, Mixins.<Float>get(renderTick, "renderTickTime"));
    }

    @Test
    void aRecycledBlockEventRefreshesItsBlockAndNeighbours() {
        BlockEventMixin event = Mixins.instance(BlockEventMixin.class);
        assertNull(event.getPhase());
        event.setPhase(EventPriority.LOW);
        assertEquals(EventPriority.LOW, event.getPhase());
        event.coarctatio$resetPhase();
        assertNull(event.getPhase());

        World world = mock(World.class);
        event.coarctatio$refreshBlock(world, BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
        assertSame(world, Mixins.get(event, "world"));
        assertEquals(BlockPos.ORIGIN, Mixins.get(event, "pos"));
        assertEquals(Blocks.STONE.getDefaultState(), Mixins.get(event, "state"));

        NeighborNotifyEventMixin notify = Mixins.instance(NeighborNotifyEventMixin.class);
        EnumSet<EnumFacing> sides = EnumSet.of(EnumFacing.UP);
        notify.coarctatio$refreshNeighbors(sides, true);
        assertSame(sides, Mixins.get(notify, "notifiedSides"));
        assertTrue(Mixins.<Boolean>get(notify, "forceRedstoneUpdate"));
    }

    @Test
    void aRecycledCapabilityEventSwapsItsObjectAndDropsTheProvidersFromLastTime() {
        // Mockito cannot instrument the spliced event class, so the mixin gets a plain generated subclass instead
        AttachCapabilitiesEventMixin<TileEntity> event = Mixins.concrete(AttachCapabilitiesEventMixin.class);
        Map<net.minecraft.util.ResourceLocation, net.minecraftforge.common.capabilities.ICapabilityProvider> caps =
                new java.util.HashMap<>();
        caps.put(new net.minecraft.util.ResourceLocation("test:old"), mock(net.minecraftforge.common.capabilities.ICapabilityProvider.class));
        Mixins.set(event, "caps", caps);

        TileEntity tile = mock(TileEntity.class);
        event.coarctatio$refreshObject(tile);
        assertSame(tile, Mixins.get(event, "obj"));
        // The dispatcher copied the previous providers out before the event was released
        assertTrue(caps.isEmpty());

        event.setPhase(EventPriority.NORMAL);
        assertEquals(EventPriority.NORMAL, event.getPhase());
        event.coarctatio$resetPhase();
        assertNull(event.getPhase());
        // The constructor only exists to satisfy the superclass and is never invoked
        assertThrows(AssertionError.class, () -> Mixins.concrete(AttachCapabilitiesEventMixin.class, TileEntity.class));
    }

    @Test
    void theTickHooksHandTheBusARecycledEvent() {
        FMLCommonHandlerMixin handler = Mixins.instance(FMLCommonHandlerMixin.class);
        assertNotNull(Mixins.call(handler, "coarctatio$serverTick", TickEvent.Phase.START));
        assertNotNull(Mixins.call(handler, "coarctatio$clientTick", TickEvent.Phase.START));
        assertNotNull(Mixins.call(handler, "coarctatio$worldTick", Side.SERVER, TickEvent.Phase.START, mock(World.class)));
        assertNotNull(Mixins.call(handler, "coarctatio$renderTick", TickEvent.Phase.END, 0.5F));

        // Allocated rather than mocked: instrumenting EntityPlayer would undo the constructor the harness adds to it
        EntityPlayer player = Mc.uninitialized(net.minecraft.entity.player.EntityPlayerMP.class);
        Mixins.set(player, "world", mock(World.class));
        TickEvent.PlayerTickEvent tick = Mixins.call(handler, "coarctatio$playerTick", TickEvent.Phase.START, player);
        assertNotNull(tick);
        // The slot is freed as soon as the post returns, so the next tick gets the same instance back
        Mixins.call(handler, "coarctatio$releasePreTick", player, Mixins.ci());
        assertSame(tick, Mixins.call(handler, "coarctatio$playerTick", TickEvent.Phase.START, player));
        Mixins.call(handler, "coarctatio$releasePreTick", player, Mixins.ci());
        Mixins.call(handler, "coarctatio$playerTick", TickEvent.Phase.END, player);
        Mixins.call(handler, "coarctatio$releasePostTick", player, Mixins.ci());
    }

    @Test
    void theCapabilityGathersAndNeighbourNotifiesAreRecycledToo() {
        AttachCapabilitiesEvent<TileEntity> tile =
                Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$tileEvent", TileEntity.class, mock(TileEntity.class));
        assertNotNull(tile);
        Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$releaseEvent", tile, null, Mixins.cir());
        assertNotNull(Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$entityEvent", Entity.class, mock(Entity.class)));
        assertNotNull(Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$stackEvent", ItemStack.class, ItemStack.EMPTY));
        assertNotNull(Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$chunkEvent", Chunk.class, mock(Chunk.class)));

        World world = mock(World.class);
        EnumSet<EnumFacing> sides = EnumSet.of(EnumFacing.NORTH);
        BlockEvent.NeighborNotifyEvent first = Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$neighborEvent",
                world, BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), sides, false);
        assertNotNull(first);
        // The next notification on this thread reuses the instance, refreshed with the new block
        BlockEvent.NeighborNotifyEvent second = Mixins.call(ForgeEventFactoryMixin.class, "coarctatio$neighborEvent",
                world, new BlockPos(1, 2, 3), Blocks.DIRT.getDefaultState(), EnumSet.of(EnumFacing.UP), true);
        assertSame(first, second);
        assertFalse(second.isCanceled());
        assertNull(((RecyclableEvent) second).getClass() == null ? null : second.getPhase());
        assertNotNull(Mixins.instance(ForgeEventFactoryMixin.class));
    }
}
