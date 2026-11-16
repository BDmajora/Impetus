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

// Reusable instances of frequently posted events.
public final class RecycledEvents {
    // Client tick event of the start phase.
    private static final TickEvent.ClientTickEvent CLIENT_TICK_START = new TickEvent.ClientTickEvent(TickEvent.Phase.START);
    // Client tick event of the end phase.
    private static final TickEvent.ClientTickEvent CLIENT_TICK_END = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
    // Server tick event of the start phase.
    private static final TickEvent.ServerTickEvent SERVER_TICK_START = new TickEvent.ServerTickEvent(TickEvent.Phase.START);
    // Server tick event of the end phase.
    private static final TickEvent.ServerTickEvent SERVER_TICK_END = new TickEvent.ServerTickEvent(TickEvent.Phase.END);
    // World tick event of the start phase.
    private static final TickEvent.WorldTickEvent WORLD_TICK_START = new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, null);
    // World tick event of the end phase.
    private static final TickEvent.WorldTickEvent WORLD_TICK_END = new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, null);
    // Render tick event of the start phase.
    private static final TickEvent.RenderTickEvent RENDER_TICK_START = new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0F);
    // Render tick event of the end phase.
    private static final TickEvent.RenderTickEvent RENDER_TICK_END = new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0F);

    // Player tick event of the start phase for each thread.
    private static final ThreadLocal<Slot<TickEvent.PlayerTickEvent>> PLAYER_TICK_START =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new TickEvent.PlayerTickEvent(TickEvent.Phase.START, null)));
    // Player tick event of the end phase for each thread.
    private static final ThreadLocal<Slot<TickEvent.PlayerTickEvent>> PLAYER_TICK_END =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new TickEvent.PlayerTickEvent(TickEvent.Phase.END, null)));
    // Capability event for tile entities for each thread.
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<TileEntity>>> TILE_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(TileEntity.class, null)));
    // Capability event for entities for each thread.
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<Entity>>> ENTITY_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(Entity.class, null)));
    // Capability event for item stacks for each thread.
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<ItemStack>>> STACK_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(ItemStack.class, null)));
    // Capability event for chunks for each thread.
    private static final ThreadLocal<Slot<AttachCapabilitiesEvent<Chunk>>> CHUNK_CAPABILITIES =
            ThreadLocal.withInitial(() -> new Slot<>(() -> new AttachCapabilitiesEvent<>(Chunk.class, null)));

    // Prevents instantiation
    private RecycledEvents() {
    }

    // Returns the reset client tick event of the phase.
    public static TickEvent.ClientTickEvent clientTick(TickEvent.Phase phase) {
        return reset(phase == TickEvent.Phase.START ? CLIENT_TICK_START : CLIENT_TICK_END);
    }

    // Returns the reset server tick event of the phase.
    public static TickEvent.ServerTickEvent serverTick(TickEvent.Phase phase) {
        return reset(phase == TickEvent.Phase.START ? SERVER_TICK_START : SERVER_TICK_END);
    }

    // Returns the reset world tick event of the phase for the world.
    public static TickEvent.WorldTickEvent worldTick(TickEvent.Phase phase, World world) {
        TickEvent.WorldTickEvent event = reset(phase == TickEvent.Phase.START ? WORLD_TICK_START : WORLD_TICK_END);
        ((RecyclableEvent) event).coarctatio$refreshWorld(world);
        return event;
    }

    // Returns the reset render tick event of the phase with the render time.
    public static TickEvent.RenderTickEvent renderTick(TickEvent.Phase phase, float renderTickTime) {
        TickEvent.RenderTickEvent event = reset(phase == TickEvent.Phase.START ? RENDER_TICK_START : RENDER_TICK_END);
        ((RecyclableEvent) event).coarctatio$refreshRenderTime(renderTickTime);
        return event;
    }

    // Returns the player tick event of the phase for the player.
    public static TickEvent.PlayerTickEvent playerTick(TickEvent.Phase phase, EntityPlayer player) {
        Slot<TickEvent.PlayerTickEvent> slot = (phase == TickEvent.Phase.START ? PLAYER_TICK_START : PLAYER_TICK_END).get();
        TickEvent.PlayerTickEvent event = slot.acquire();
        RecyclableEvent recyclable = (RecyclableEvent) event;
        recyclable.coarctatio$refreshSide(player.world.isRemote ? Side.CLIENT : Side.SERVER);
        recyclable.coarctatio$refreshPlayer(player);
        return event;
    }

    // Marks the player tick event of the phase as free.
    public static void releasePlayerTick(TickEvent.Phase phase) {
        (phase == TickEvent.Phase.START ? PLAYER_TICK_START : PLAYER_TICK_END).get().release();
    }

    // Returns the capability event for the tile entity.
    public static AttachCapabilitiesEvent<TileEntity> tileCapabilities(TileEntity tile) {
        return capabilities(TILE_CAPABILITIES.get(), tile);
    }

    // Returns the capability event for the entity.
    public static AttachCapabilitiesEvent<Entity> entityCapabilities(Entity entity) {
        return capabilities(ENTITY_CAPABILITIES.get(), entity);
    }

    // Returns the capability event for the stack.
    public static AttachCapabilitiesEvent<ItemStack> stackCapabilities(ItemStack stack) {
        return capabilities(STACK_CAPABILITIES.get(), stack);
    }

    // Returns the capability event for the chunk.
    public static AttachCapabilitiesEvent<Chunk> chunkCapabilities(Chunk chunk) {
        return capabilities(CHUNK_CAPABILITIES.get(), chunk);
    }

    // Marks the capability event of its type as free.
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

    // Returns the reset capability event of the slot for the object.
    private static <T> AttachCapabilitiesEvent<T> capabilities(Slot<AttachCapabilitiesEvent<T>> slot, T object) {
        AttachCapabilitiesEvent<T> event = slot.acquire();
        ((RecyclableEvent) event).coarctatio$refreshObject(object);
        return event;
    }

    // Returns the event after restoring its default state.
    private static <E extends Event> E reset(E event) {
        ((RecyclableEvent) event).coarctatio$resetEventState();
        return event;
    }

    // Holder of one reusable event and a fallback factory for nested use.
    private static final class Slot<E extends Event> {
        // Creates a fresh event for nested use.
        private final Supplier<E> factory;
        // Reusable event.
        private final E instance;
        // Number of events handed out and not yet released.
        private int depth;

        // Creates a slot with one event from the factory.
        Slot(Supplier<E> factory) {
            this.factory = factory;
            this.instance = factory.get();
        }

        // Returns the reusable event when free or a fresh event when already in use.
        E acquire() {
            return this.depth++ == 0 ? reset(this.instance) : this.factory.get();
        }

        // Marks one handed out event as free.
        void release() {
            if (this.depth > 0) {
                this.depth--;
            }
        }
    }
}
