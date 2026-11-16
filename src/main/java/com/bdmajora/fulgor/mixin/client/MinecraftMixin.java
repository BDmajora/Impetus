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

// Mixin that changes Minecraft.
@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // Reference to the field profiler of the target.
    @Shadow
    @Final
    public Profiler profiler;

    // Reference to the field world of the target.
    @Shadow
    public WorldClient world;

    // Reference to the field render global of the target.
    @Shadow
    public RenderGlobal renderGlobal;

    // Reference to the field is game paused of the target.
    @Shadow
    private boolean isGamePaused;

    // Runs inside runTick.
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=levelRenderer"))
    private void fulgor$processWorldLightUpdates(CallbackInfo ci) {
        if (this.world == null || (this.isGamePaused && FulgorConfig.get().skipUpdatesWhilePaused)) {
            return;
        }

        this.profiler.endStartSection("lighting");

        Fulgor.processClientLightUpdates(this.world);
    }

    // Runs inside runTick.
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=level"))
    private void fulgor$processRenderLightUpdates(CallbackInfo ci) {
        if (this.isGamePaused && FulgorConfig.get().skipUpdatesWhilePaused) {
            return;
        }

        if (this.renderGlobal instanceof LightUpdateProcessor) {
            ((LightUpdateProcessor) this.renderGlobal).fulgor$processLightUpdates();
        }
    }
}
