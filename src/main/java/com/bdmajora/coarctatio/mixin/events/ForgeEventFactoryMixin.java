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

// Mixin that changes ForgeEventFactory.
@Mixin(value = ForgeEventFactory.class, remap = false)
public abstract class ForgeEventFactoryMixin {
    // Field coarctatio neighbor notify added to the target.
    @Unique
    private static final ThreadLocal<BlockEvent.NeighborNotifyEvent> COARCTATIO_NEIGHBOR_NOTIFY = new ThreadLocal<>();

    // Replaces the creation of AttachCapabilitiesEvent in gatherCapabilities.
    @Redirect(method = "gatherCapabilities(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<TileEntity> coarctatio$tileEvent(Class<?> type, Object tile) {
        return RecycledEvents.tileCapabilities((TileEntity) tile);
    }

    // Replaces the creation of AttachCapabilitiesEvent in gatherCapabilities.
    @Redirect(method = "gatherCapabilities(Lnet/minecraft/entity/Entity;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<Entity> coarctatio$entityEvent(Class<?> type, Object entity) {
        return RecycledEvents.entityCapabilities((Entity) entity);
    }

    // Replaces the creation of AttachCapabilitiesEvent in gatherCapabilities.
    @Redirect(method = "gatherCapabilities(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<ItemStack> coarctatio$stackEvent(Class<?> type, Object stack) {
        return RecycledEvents.stackCapabilities((ItemStack) stack);
    }

    // Replaces the creation of AttachCapabilitiesEvent in gatherCapabilities.
    @Redirect(method = "gatherCapabilities(Lnet/minecraft/world/chunk/Chunk;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At(value = "NEW", target = "net/minecraftforge/event/AttachCapabilitiesEvent"))
    private static AttachCapabilitiesEvent<Chunk> coarctatio$chunkEvent(Class<?> type, Object chunk) {
        return RecycledEvents.chunkCapabilities((Chunk) chunk);
    }

    // Runs before every return of gatherCapabilities.
    @Inject(method = "gatherCapabilities(Lnet/minecraftforge/event/AttachCapabilitiesEvent;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;",
            at = @At("RETURN"))
    private static void coarctatio$releaseEvent(AttachCapabilitiesEvent<?> event, ICapabilityProvider parent, CallbackInfoReturnable<CapabilityDispatcher> cir) {
        RecycledEvents.releaseCapabilities(event);
    }

    // Replaces the creation of NeighborNotifyEvent in onNeighborNotify.
    @Redirect(method = "onNeighborNotify", at = @At(value = "NEW", target = "net/minecraftforge/event/world/BlockEvent$NeighborNotifyEvent"))
    private static BlockEvent.NeighborNotifyEvent coarctatio$neighborEvent(World world, BlockPos pos, IBlockState state,
                                                                          // Notified sides and redstone flag.
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
