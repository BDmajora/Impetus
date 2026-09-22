package com.bdmajora.extras.mixin;

import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;

// Announces the subsystem and gates nothing, since every Extras mixin reads its switch at call time; kept as a real plugin so the load-order log line sits beside the others. An earlier version gated the Panini mixins on ShaderGroup being loadable, which always failed here (Minecraft classes are unreachable at config load) and would have force-loaded a class ahead of its transformers, so PaniniProjection#shouldApply asks at runtime instead
public class ExtrasMixinPlugin extends SimpleMixinPlugin {
}
