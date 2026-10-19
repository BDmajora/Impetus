package com.bdmajora.impetus.mixin.core.cleanroom;

import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.*;

class CleanroomMixinsTest {
    @Test
    void kirinosEnabledGateIsCancelledBeforeItsRuntimeCanStart() {
        var callback = new CallbackInfoReturnable<Boolean>("isEnabled", true, true);
        Mixins.call(KirinoConfigMixin.class, "impetus$disableKirino", callback);
        assertNotNull(new KirinoConfigMixin());
        assertTrue(callback.isCancelled());
        assertFalse(callback.getReturnValue());
    }
}
