package com.bdmajora.coarctatio.events;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Supplier;

// Chibi's makeEventsSingletons, made safe for a game that ticks entities and builds item stacks on several threads: one instance per event type per thread, handed out only when the previous post on that thread has finished, so a handler that triggers the same event again gets a fresh one
public final class RecycledEvents {
    private static final TickEvent.ClientTickEvent CLIENT_TICK_START = new TickEvent.ClientTickEvent(TickEvent.Phase.START);
    private static final TickEvent.ClientTickEvent CLIENT_TICK_END = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
    private static final TickEvent.ServerTickEvent SERVER_TICK_START = new TickEvent.ServerTickEvent(TickEvent.Phase.START);
    private static final TickEvent.ServerTickEvent SERVER_TICK_END = new TickEvent.ServerTickEvent(TickEvent.Phase.END);
    private static final TickEvent.WorldTickEvent WORLD_TICK_START = new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, null);
    private static final TickEvent.WorldTickEvent WORLD_TICK_END = new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, null);
    private static final TickEvent.RenderTickEvent RENDER_TICK_START = new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0F);
    private static final TickEvent.RenderTickEvent RENDER_TICK_END = new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0F);

    private static final ThreadLocal<Slot<TickEvent.PlayerTickEvent>> PLAYER_TICK_START =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new TickEvent.PlayerTickEvent(TickEvent.Phase.START, null)));
    private static final ThreadLocal<Slot<TickEvent.PlayerTickEvent>> PLAYER_TICK_END =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new TickEvent.PlayerTickEvent(TickEvent.Phase.END, null)));
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<TileEntity>>> TILE_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(TileEntity.class, null)));
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<Entity>>> ENTITY_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(Entity.class, null)));
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<ItemStack>>> STACK_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(ItemStack.class, null)));
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<Chunk>>> CHUNK_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(Chunk.class, null)));

    private RecycledEvents() {
    }

    // The tick events carry nothing but their phase, so one instance per phase serves for the session; each is only ever posted from one thread
    public static TickEvent.ClientTickEvent clientTick(TickEvent.Phase phase) {
        return reset(phase == TickEvent.Phase.START ? CLIENT_TICK_START : CLIENT_TICK_END);
    }

    public static TickEvent.ServerTickEvent serverTick(TickEvent.Phase phase) {
        return reset(phase == TickEvent.Phase.START ? SERVER_TICK_START : SERVER_TICK_END);
    }

    public static TickEvent.WorldTickEvent worldTick(TickEvent.Phase phase, World world) {
        TickEvent.WorldTickEvent event = reset(phase == TickEvent.Phase.START ? WORLD_TICK_START : WORLD_TICK_END);
        ((RecyclableEvent) event).coarctatio$refreshWorld(world);
        return event;
    }

    public static TickEvent.RenderTickEvent renderTick(TickEvent.Phase phase, float renderTickTime) {
        TickEvent.RenderTickEvent event = reset(phase == TickEvent.Phase.START ? RENDER_TICK_START : RENDER_TICK_END);
        ((RecyclableEvent) event).coarctatio$refreshRenderTime(renderTickTime);
        return event;
    }

    // Players tick on the client thread, the server thread and, under parallel ticking, worker threads, so these are per thread; the side is set from the player since the same instance serves both
    public static TickEvent.PlayerTickEvent playerTick(TickEvent.Phase phase, EntityPlayer player) {
        Slot<TickEvent.PlayerTickEvent> slot = (phase == TickEvent.Phase.START ? PLAYER_TICK_START : PLAYER_TICK_END).get();
        TickEvent.PlayerTickEvent event = slot.acquire();
        RecyclableEvent recyclable = (RecyclableEvent) event;
        recyclable.coarctatio$refreshSide(player.world.isRemote ? Side.CLIENT : Side.SERVER);
        recyclable.coarctatio$refreshPlayer(player);
        return event;
    }

    // Released as soon as the bus returns, since the handler's only view of the player is during the post
    public static void releasePlayerTick(TickEvent.Phase phase) {
        (phase == TickEvent.Phase.START ? PLAYER_TICK_START : PLAYER_TICK_END).get().release();
    }

    public static AttachCapabilitiesEvent<TileEntity> tileCapabilities(TileEntity tile) {
        return capabilities(TILE_CAPABILITIES.get(), tile);
    }

    public static AttachCapabilitiesEvent<Entity> entityCapabilities(Entity entity) {
        return capabilities(ENTITY_CAPABILITIES.get(), entity);
    }

    public static AttachCapabilitiesEvent<ItemStack> stackCapabilities(ItemStack stack) {
        return capabilities(STACK_CAPABILITIES.get(), stack);
    }

    public static AttachCapabilitiesEvent<Chunk> chunkCapabilities(Chunk chunk) {
        return capabilities(CHUNK_CAPABILITIES.get(), chunk);
    }

    // Released once the dispatcher has copied the capabilities out of the event; the village and world events are never recycled
    public static void releaseCapabilities(AttachCapabilitiesEvent<?> event) {
        java.lang.reflect.Type type = event.getGenericType();
        if (type == TileEntity.class) {
            TILE_CAPABILITIES.get().release();
        } else if (type == Entity.class) {
            ENTITY_CAPABILITIES.get().release();
        } else if (type == ItemStack.class) {
            STACK_CAPABILITIES.get().release();
        } else if (type == Chunk.class) {
            CHUNK_CAPABILITIES.get().release();
        }
    }

    private static <T> AttachCapabilitiesEvent<T> capabilities(Slot<AttachCapabilitiesEvent<T>> slot, T object) {
        AttachCapabilitiesEvent<T> event = slot.acquire();
        ((RecyclableEvent) event).coarctatio$refreshObject(object);
        return event;
    }

    private static <E extends Event> E reset(E event) {
        ((RecyclableEvent) event).coarctatio$resetEventState();
        return event;
    }

    // One thread's instance of an event type and how many posts of it are in flight; the outermost gets the instance, a nested one a throwaway, and the count unwinds as they return
    private static final class Slot<E extends Event> {
        private final Supplier<E> factory;
        private final E instance;
        private int depth;

        Slot(Supplier<E> factory) {
            this.factory = factory;
            this.instance = factory.get();
        }

        E acquire() {
            return this.depth++ == 0 ? reset(this.instance) : this.factory.get();
        }

        void release() {
            if (this.depth > 0) {
                this.depth--;
            }
        }
    }
}
