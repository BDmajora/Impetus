package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;

// Announces the subsystem and gates nothing (same reasoning as ExtrasMixinPlugin): every mixin reads the mode at CALL time, so turning dynamic lights off leaves them applied and inert
public class DynamicLightsMixinPlugin extends SimpleMixinPlugin {
}
