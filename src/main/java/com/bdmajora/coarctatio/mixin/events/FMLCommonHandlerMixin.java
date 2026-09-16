package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecycledEvents;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hands the bus recycled tick events in place of the ten it would otherwise construct every tick, and the two per player. remap = false, Forge class
@Mixin(value = FMLCommonHandler.class, remap = false)
public abstract class FMLCommonHandlerMixin {
    @Redirect(method = {"onPreServerTick", "onPostServerTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$ServerTickEvent"))
    private TickEvent.ServerTickEvent coarctatio$serverTick(TickEvent.Phase phase) {
        return RecycledEvents.serverTick(phase);
    }

    @Redirect(method = {"onPreWorldTick", "onPostWorldTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$WorldTickEvent"))
    private TickEvent.WorldTickEvent coarctatio$worldTick(Side side, TickEvent.Phase phase, World world) {
        return RecycledEvents.worldTick(phase, world);
    }

    @Redirect(method = {"onPreClientTick", "onPostClientTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent"))
    private TickEvent.ClientTickEvent coarctatio$clientTick(TickEvent.Phase phase) {
        return RecycledEvents.clientTick(phase);
    }

    @Redirect(method = {"onRenderTickStart", "onRenderTickEnd"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$RenderTickEvent"))
    private TickEvent.RenderTickEvent coarctatio$renderTick(TickEvent.Phase phase, float timer) {
        return RecycledEvents.renderTick(phase, timer);
    }

    @Redirect(method = {"onPlayerPreTick", "onPlayerPostTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$PlayerTickEvent"))
    private TickEvent.PlayerTickEvent coarctatio$playerTick(TickEvent.Phase phase, EntityPlayer player) {
        return RecycledEvents.playerTick(phase, player);
    }

    // The player instance is only valid during the post, so the slot is freed as soon as it returns
    @Inject(method = "onPlayerPreTick", at = @At("RETURN"))
    private void coarctatio$releasePreTick(EntityPlayer player, CallbackInfo ci) {
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
    }

    @Inject(method = "onPlayerPostTick", at = @At("RETURN"))
    private void coarctatio$releasePostTick(EntityPlayer player, CallbackInfo ci) {
        RecycledEvents.releasePlayerTick(TickEvent.Phase.END);
    }
}
