package com.bdmajora.fulgor.mixin.client;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.api.LightUpdateProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.profiler.Profiler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Flushes pending light updates once per client tick in two ordered stages: world engine first (writes light, queues renderer notifications), then renderer drain; reversed, the terrain renderer would copy a stale batch out of chunk sections. Runs before updateEntities and the world tick but after the packet drain and the player's own edits, so those are lit before the frame
@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Final
    public Profiler profiler;

    @Shadow
    public WorldClient world;

    @Shadow
    public RenderGlobal renderGlobal;

    @Shadow
    private boolean isGamePaused;

    // Stage one, just before the "levelRenderer" profiler section opens: opens "lighting" and lets vanilla's endStartSection close it, keeping the profiler balanced
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=levelRenderer"))
    private void fulgor$processWorldLightUpdates(CallbackInfo ci) {
        if (this.world == null || (this.isGamePaused && FulgorConfig.get().skipUpdatesWhilePaused)) {
            return;
        }

        this.profiler.endStartSection("lighting");

        Fulgor.processClientLightUpdates(this.world);
    }

    // Stage two, just before the "level" section, right after renderGlobal.updateClouds(), the call whose light-update drain Fulgor took over
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=level"))
    private void fulgor$processRenderLightUpdates(CallbackInfo ci) {
        if (this.isGamePaused && FulgorConfig.get().skipUpdatesWhilePaused) {
            return;
        }

        // instanceof rather than a cast: the renderer-side queue is a separate config switch, and with it off RenderGlobal keeps vanilla's drain and does not implement this
        if (this.renderGlobal instanceof LightUpdateProcessor) {
            ((LightUpdateProcessor) this.renderGlobal).fulgor$processLightUpdates();
        }
    }
}
