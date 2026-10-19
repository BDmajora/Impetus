package com.bdmajora.impetus.engine.impl.gl.debug;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class GLDebugTest {
    @Test
    void labelsAndBalancedGroupsFollowTheDebugSwitch() {
        String before = System.getProperty("impetus.enableGLDebug");
        try {
            System.setProperty("impetus.enableGLDebug", "false");
            GLDebug.reloadDebugState();
            GLDebug.nameObject(1, 2, "disabled");
            verify(TestGl.gl(), never()).glObjectLabel(1, 2, "disabled");
            System.setProperty("impetus.enableGLDebug", "true");
            when(TestGl.gl().isOpenGLVersionSupported(4, 3)).thenReturn(true);
            GLDebug.reloadDebugState();
            GLDebug.nameObject(1, 2, "terrain");
            verify(TestGl.gl()).glObjectLabel(1, 2, "terrain");
            GLDebug.pushGroup(73, "coverage group");
            verify(TestGl.gl()).glPushDebugGroup(0x824A, 73, "coverage group");
            GLDebug.popGroup();
            GLDebug.popGroup();
            verify(TestGl.gl(), times(1)).glPopDebugGroup();
        } finally {
            if (before == null) System.clearProperty("impetus.enableGLDebug");
            else System.setProperty("impetus.enableGLDebug", before);
            GLDebug.reloadDebugState();
        }
    }
}
