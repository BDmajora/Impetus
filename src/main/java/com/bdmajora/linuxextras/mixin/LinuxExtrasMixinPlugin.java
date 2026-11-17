package com.bdmajora.linuxextras.mixin;

import com.bdmajora.impetus.core.SimpleMixinPlugin;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;

// Class linux extras mixin plugin.
public class LinuxExtrasMixinPlugin extends SimpleMixinPlugin {
    // Applies the Linux mixins only on Linux.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return OsKind.current() == OsKind.LINUX;
    }
}
