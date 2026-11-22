package com.bdmajora.extras.mixin.misc;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.GlStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class FramebufferBlitBlendMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void theCopyTurnsBlendingOffInGlEvenWhenTheCacheSkippedTheCall() {
        FramebufferBlitBlendMixin blit = Mixins.instance(FramebufferBlitBlendMixin.class);
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            // The failure case: GL has blending on, the cache already reads off, so vanilla's disableBlend sends nothing
            GlStateManager.disableBlend();
            gl.clearInvocations();
            GlStateManager.disableBlend();
            gl.verify(() -> GL11.glDisable(GL11.GL_BLEND), never());
            CallbackInfo ci = Mixins.ci();
            Mixins.call(blit, "impetus$forceBlendOff", 854, 480, true, ci);
            gl.verify(() -> GL11.glDisable(GL11.GL_BLEND), times(1));
            // Never cancels the copy and touches nothing else
            assertFalse(ci.isCancelled());
            gl.verify(() -> GL11.glEnable(anyInt()), never());
            gl.verify(() -> GL11.glBlendFunc(anyInt(), anyInt()), never());
            // The cache still reads off, so the next cached enable really enables
            GlStateManager.enableBlend();
            gl.verify(() -> GL11.glEnable(GL11.GL_BLEND), times(1));
            GlStateManager.disableBlend();
        }
    }

    @Test
    void zeroAndHugeSizesAreFine() {
        FramebufferBlitBlendMixin blit = Mixins.instance(FramebufferBlitBlendMixin.class);
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            Mixins.call(blit, "impetus$forceBlendOff", 0, 0, true, Mixins.ci());
            Mixins.call(blit, "impetus$forceBlendOff", Integer.MAX_VALUE, Integer.MAX_VALUE, true, Mixins.ci());
            gl.verify(() -> GL11.glDisable(GL11.GL_BLEND), times(2));
        }
    }
}
