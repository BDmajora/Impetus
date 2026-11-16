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

// Mixin that changes FMLCommonHandler.
@Mixin(value = FMLCommonHandler.class, remap = false)
public abstract class FMLCommonHandlerMixin {
    // Replaces the creation of ServerTickEvent in onPreServerTick and onPostServerTick.
    @Redirect(method = {"onPreServerTick", "onPostServerTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$ServerTickEvent"))
    private TickEvent.ServerTickEvent coarctatio$serverTick(TickEvent.Phase phase) {
        return RecycledEvents.serverTick(phase);
    }

    // Replaces the creation of WorldTickEvent in onPreWorldTick and onPostWorldTick.
    @Redirect(method = {"onPreWorldTick", "onPostWorldTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$WorldTickEvent"))
    private TickEvent.WorldTickEvent coarctatio$worldTick(Side side, TickEvent.Phase phase, World world) {
        return RecycledEvents.worldTick(phase, world);
    }

    // Replaces the creation of ClientTickEvent in onPreClientTick and onPostClientTick.
    @Redirect(method = {"onPreClientTick", "onPostClientTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent"))
    private TickEvent.ClientTickEvent coarctatio$clientTick(TickEvent.Phase phase) {
        return RecycledEvents.clientTick(phase);
    }

    // Replaces the creation of RenderTickEvent in onRenderTickStart and onRenderTickEnd.
    @Redirect(method = {"onRenderTickStart", "onRenderTickEnd"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$RenderTickEvent"))
    private TickEvent.RenderTickEvent coarctatio$renderTick(TickEvent.Phase phase, float timer) {
        return RecycledEvents.renderTick(phase, timer);
    }

    // Replaces the creation of PlayerTickEvent in onPlayerPreTick and onPlayerPostTick.
    @Redirect(method = {"onPlayerPreTick", "onPlayerPostTick"}, at = @At(value = "NEW", target = "net/minecraftforge/fml/common/gameevent/TickEvent$PlayerTickEvent"))
    private TickEvent.PlayerTickEvent coarctatio$playerTick(TickEvent.Phase phase, EntityPlayer player) {
        return RecycledEvents.playerTick(phase, player);
    }

    // Runs before every return of onPlayerPreTick.
    @Inject(method = "onPlayerPreTick", at = @At("RETURN"))
    private void coarctatio$releasePreTick(EntityPlayer player, CallbackInfo ci) {
        RecycledEvents.releasePlayerTick(TickEvent.Phase.START);
    }

    // Runs before every return of onPlayerPostTick.
    @Inject(method = "onPlayerPostTick", at = @At("RETURN"))
    private void coarctatio$releasePostTick(EntityPlayer player, CallbackInfo ci) {
        RecycledEvents.releasePlayerTick(TickEvent.Phase.END);
    }
}
