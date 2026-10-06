package com.bdmajora.impetus.mixin.core.cleanroom;

import com.cleanroommc.kirino.KirinoCommonCore;
import com.cleanroommc.kirino.config.event.KirinoOneTimeConfigEvent;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Cleanroom ships its Kirino engine switched on: even with the render delegate off it runs ECS frame phases headlessly inside renderWorldPass and listens to every block and light update, all for terrain Impetus already meshes and draws
@Mixin(value = KirinoCommonCore.class, remap = false)
public class KirinoCommonCoreMixin {
    // Kirino's own one-time config listener, posted before mod discovery; turning enable off here keeps its mod containers, ECS runtime and render hooks from ever starting
    @Inject(method = "onKirinoOneTimeConfig", at = @At("TAIL"))
    private static void impetus$disableKirino(KirinoOneTimeConfigEvent event, CallbackInfo ci) {
        event.getOneTimeConfig().enable = false;
        LogManager.getLogger("Impetus").info("Disabled Cleanroom's Kirino engine; Impetus renders terrain itself");
    }
}
