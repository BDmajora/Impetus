package com.bdmajora.linuxextras.mixin;

import com.bdmajora.impetus.core.SimpleMixinPlugin;
import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;

// Linux Extras only touches Linux; every other platform runs Cleanroom's window untouched
public class LinuxExtrasMixinPlugin extends SimpleMixinPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return OsKind.current() == OsKind.LINUX;
    }
}
