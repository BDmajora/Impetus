package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import com.bdmajora.coarctatio.events.RecycledEvents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;

// Recycles the capability-attach event of the four object kinds constructed in bulk (tile entities, entities, item stacks, chunks) and the neighbour-notify event fired per block update. remap = false, Forge class
@Mixin(value = ForgeEventFactory.class, remap = false)
public abstract class ForgeEventFactoryMixin {
    // Neighbour notifications nest (a handler placing a block fires another), so the recycled instance is only reused once the outer caller has read its own; vanilla reads the cancel flag and sides before recursing
    @Unique
    private static final ThreadLocal<BlockEvent.NeighborNotifyEvent> COARCTATIO_NEIGHBOR_NOTIFY = new ThreadLocal<>();

    // The constructor is AttachCapabilitiesEvent(Class<T>, T), so its erased descriptor takes Object and a constructor redirect must declare exactly that (Mixin validates the handler against the erased signature); the typed object is cast back inside
    @Redirect(method = "gatherCapabilities(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<TileEntity> coarctatio$tileEvent(Class<?> type, Object tile) {
        return RecycledEvents.tileCapabilities((TileEntity) tile);
    }

    @Redirect(method = "gatherCapabilities(Lnet/minecraft/entity/Entity;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<Entity> coarctatio$entityEvent(Class<?> type, Object entity) {
        return RecycledEvents.entityCapabilities((Entity) entity);
    }

    @Redirect(method = "gatherCapabilities(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<ItemStack> coarctatio$stackEvent(Class<?> type, Object stack) {
        return RecycledEvents.stackCapabilities((ItemStack) stack);
    }

    @Redirect(method = "gatherCapabilities(Lnet/minecraft/world/chunk/Chunk;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<Chunk> coarctatio$chunkEvent(Class<?> type, Object chunk) {
        return RecycledEvents.chunkCapabilities((Chunk) chunk);
    }

    // The dispatcher has copied the providers out by the time the shared gather returns
    @Inject(method = "gatherCapabilities(Lnet/minecraftforge/event/AttachCapabilitiesEvent;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At("RETURN"))
    private static void coarctatio$releaseEvent(AttachCapabilitiesEvent<?> event, ICapabilityProvider parent, CallbackInfoReturnable<CapabilityDispatcher> cir) {
        RecycledEvents.releaseCapabilities(event);
    }

    @Redirect(method = "onNeighborNotify", at = @At(value = "NEW", target = "net/minecraftforge/event/world/BlockEvent$NeighborNotifyEvent"))
    private static BlockEvent.NeighborNotifyEvent coarctatio$neighborEvent(World world, BlockPos pos, IBlockState state,
                                                                          EnumSet<EnumFacing> notifiedSides, boolean forceRedstoneUpdate) {
        BlockEvent.NeighborNotifyEvent event = COARCTATIO_NEIGHBOR_NOTIFY.get();
        if (event == null) {
            event = new BlockEvent.NeighborNotifyEvent(world, pos, state, notifiedSides, forceRedstoneUpdate);
            COARCTATIO_NEIGHBOR_NOTIFY.set(event);
            return event;
        }
        RecyclableEvent recyclable = (RecyclableEvent) event;
        recyclable.coarctatio$resetEventState();
        recyclable.coarctatio$refreshBlock(world, pos, state);
        recyclable.coarctatio$refreshNeighbors(notifiedSides, forceRedstoneUpdate);
        return event;
    }
}
