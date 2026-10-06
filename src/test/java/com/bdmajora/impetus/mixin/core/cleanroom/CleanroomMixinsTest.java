package com.bdmajora.impetus.mixin.core.cleanroom;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.cleanroommc.kirino.config.KirinoConfigHub;
import com.cleanroommc.kirino.config.event.KirinoOneTimeConfigEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CleanroomMixinsTest {
    @Test
    void kirinoIsSwitchedOffFromItsOneTimeConfig() {
        KirinoConfigHub.RequiresRestart config = Mc.uninitialized(KirinoConfigHub.RequiresRestart.class);
        config.enable = true;
        KirinoOneTimeConfigEvent event = mock(KirinoOneTimeConfigEvent.class);
        when(event.getOneTimeConfig()).thenReturn(config);
        Mixins.call(KirinoCommonCoreMixin.class, "impetus$disableKirino", event, Mixins.ci());
        assertFalse(config.enable);
        assertNotNull(Mixins.instance(KirinoCommonCoreMixin.class));
    }
}
