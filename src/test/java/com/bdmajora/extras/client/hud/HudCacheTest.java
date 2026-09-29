package com.bdmajora.extras.client.hud;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.hud.EntityRendererHudCacheMixin;
import com.bdmajora.extras.mixin.hud.FramebufferCaptureMixin;
import com.bdmajora.extras.mixin.hud.GlStateManagerCaptureMixin;
import com.bdmajora.extras.mixin.hud.GuiIngameCaptureMixin;
import com.bdmajora.extras.mixin.hud.GuiIngameForgeCaptureMixin;
import com.bdmajora.extras.mixin.hud.GuiIngameForgeInvoker;
import com.bdmajora.extras.mixin.hud.GuiIngameInvoker;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.GuiIngameForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HudCacheTest {
    private ExtrasConfig config;
    private Minecraft client;
    private Framebuffer main;
    private Framebuffer cache;
    private boolean framebufferSupported;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshClient() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        settings.fboEnable = true;
        settings.fancyGraphics = true;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        main = mock(Framebuffer.class);
        when(client.getFramebuffer()).thenReturn(main);
        cache = mock(Framebuffer.class);
        cache.framebufferWidth = 854;
        cache.framebufferHeight = 480;
        framebufferSupported = OpenGlHelper.framebufferSupported;
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(HudCache.class, "framebuffer", null);
        HudCache.capturing = false;
        OpenGlHelper.framebufferSupported = framebufferSupported;
    }

    @Test
    void withTheCacheOffTheOverlayDrawsStraightToScreen() {
        GuiIngame gui = mock(GuiIngame.class);
        Mixins.set(HudCache.class, "framebuffer", cache);
        HudCache.render(mock(EntityRenderer.class), gui, 0.5F);
        verify(gui).renderGameOverlay(0.5F);
        // Switching off frees the cache
        verify(cache).deleteFramebuffer();
        assertNull(Mixins.get(HudCache.class, "framebuffer"));
        // Without framebuffers, or without a player, it draws directly as well
        config.hud.cacheEnabled = true;
        OpenGlHelper.framebufferSupported = false;
        HudCache.render(mock(EntityRenderer.class), gui, 0.5F);
        OpenGlHelper.framebufferSupported = true;
        HudCache.render(mock(EntityRenderer.class), gui, 0.5F);
        verify(gui, times(3)).renderGameOverlay(0.5F);
        HudCache.bindCache(true);
    }

    @Test
    void theCacheIsCapturedAtItsRateAndCompositedEveryFrame() {
        config.hud.cacheEnabled = true;
        OpenGlHelper.framebufferSupported = true;
        client.player = mock(EntityPlayerSP.class);
        Mixins.set(HudCache.class, "framebuffer", cache);
        Mixins.set(HudCache.class, "lastCaptureNanos", 0L);
        GuiIngameForge gui = Mc.mock(GuiIngameForge.class, GuiIngameInvoker.class, GuiIngameForgeInvoker.class);
        List<Boolean> capturing = new ArrayList<>();
        doAnswer(invocation -> capturing.add(HudCache.capturing)).when(gui).renderGameOverlay(anyFloat());
        EntityRenderer renderer = mock(EntityRenderer.class);
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class);
             MockedStatic<GL14> gl14 = Mockito.mockStatic(GL14.class)) {
            HudCache.render(renderer, gui, 0.5F);
            // The overlay went into the cache with the hooks armed, and only for the length of the draw
            assertEquals(List.of(true), capturing);
            assertFalse(HudCache.capturing);
            verify(cache).framebufferClear();
            verify(cache).bindFramebuffer(false);
            verify(main).bindFramebuffer(false);
            verify(renderer).setupOverlayRendering();
            // The vignette and crosshair are drawn live, then the cache is laid over them
            verify((GuiIngameInvoker) gui).impetus$renderVignette(anyFloat(), any());
            verify((GuiIngameForgeInvoker) gui).impetus$renderCrosshairs(0.5F);
            verify(cache).bindFramebufferTexture();
            verify(cache).unbindFramebufferTexture();

            // Within the interval the cache is only composited
            Mixins.set(HudCache.class, "lastCaptureNanos", System.nanoTime() + 60_000_000_000L);
            HudCache.render(renderer, gui, 0.5F);
            assertEquals(1, capturing.size());
            verify(cache, times(2)).bindFramebufferTexture();

            // A window resize recaptures at once, at the new size
            client.displayWidth = 1280;
            HudCache.render(renderer, gui, 0.5F);
            assertEquals(2, capturing.size());
            verify(cache).createBindFramebuffer(1280, 480);

            // Fast graphics skip the vignette, and a vanilla GuiIngame has no Forge crosshair hook
            client.displayWidth = 854;
            client.gameSettings.fancyGraphics = false;
            GuiIngame vanilla = Mc.mock(GuiIngame.class, GuiIngameInvoker.class);
            HudCache.render(renderer, vanilla, 0.5F);
            verify((GuiIngameInvoker) vanilla, never()).impetus$renderVignette(anyFloat(), any());
        }
        // During capture a bind of the main framebuffer lands on the cache
        HudCache.bindCache(true);
        verify(cache).bindFramebuffer(true);
        HudCache.release();
        verify(cache).deleteFramebuffer();
        HudCache.bindCache(true);
    }

    private static boolean cancels(Object mixin, String handler, Object... leading) {
        CallbackInfo ci = Mixins.ci();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = ci;
        Mixins.call(mixin, handler, args);
        return ci.isCancelled();
    }

    @Test
    void theOverlayHooksOnlyActWhileCapturing() {
        GuiIngame gui = mock(GuiIngame.class);
        Mixins.call(Mixins.instance(EntityRendererHudCacheMixin.class), "impetus$renderThroughCache", gui, 0.25F);
        verify(gui).renderGameOverlay(0.25F);

        GuiIngameForgeCaptureMixin forge = Mixins.instance(GuiIngameForgeCaptureMixin.class);
        GuiIngameCaptureMixin vanilla = Mixins.instance(GuiIngameCaptureMixin.class);
        FramebufferCaptureMixin framebuffer = Mixins.instance(FramebufferCaptureMixin.class);
        Mixins.set(HudCache.class, "framebuffer", cache);
        assertFalse(cancels(forge, "impetus$skipCrosshairWhileCapturing", 0.5F));
        assertFalse(cancels(vanilla, "impetus$skipVignetteWhileCapturing", 1.0F, null));
        assertFalse(cancels(framebuffer, "impetus$redirectMainBind", true));
        HudCache.capturing = true;
        assertTrue(cancels(forge, "impetus$skipCrosshairWhileCapturing", 0.5F));
        assertTrue(cancels(vanilla, "impetus$skipVignetteWhileCapturing", 1.0F, null));
        // Only a bind of the main framebuffer is redirected
        assertFalse(cancels(framebuffer, "impetus$redirectMainBind", true));
        when(client.getFramebuffer()).thenReturn((Framebuffer) (Object) framebuffer);
        assertTrue(cancels(framebuffer, "impetus$redirectMainBind", true));
        verify(cache).bindFramebuffer(true);
    }

    @Test
    void blendingIntoTheCacheKeepsAlphaAsCoverage() {
        assertNotNull(Mixins.instance(GlStateManagerCaptureMixin.class));
        Class<?> hooks = GlStateManagerCaptureMixin.class;
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL14> gl14 = Mockito.mockStatic(GL14.class)) {
            // Not capturing, everything passes through
            assertFalse(cancels(hooks, "impetus$captureBlendFunc", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA));
            assertFalse(cancels(hooks, "impetus$captureBlendFuncSeparate", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO));
            cancels(hooks, "impetus$noteBlendOn");
            cancels(hooks, "impetus$noteBlendOff");
            assertFalse(cancels(hooks, "impetus$opaqueWhenUnblended", 1.0F, 1.0F, 1.0F, 0.5F));

            HudCache.capturing = true;
            // Colour factors are kept, alpha always accumulates
            assertTrue(cancels(hooks, "impetus$captureBlendFunc", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA));
            assertTrue(cancels(hooks, "impetus$captureBlendFuncSeparate", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO));
            assertFalse(cancels(hooks, "impetus$captureBlendFuncSeparate", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA));
            // A translucent colour with blending off lands opaque, as it would have on screen
            cancels(hooks, "impetus$noteBlendOff");
            assertTrue(cancels(hooks, "impetus$opaqueWhenUnblended", 1.0F, 1.0F, 1.0F, 0.5F));
            assertFalse(cancels(hooks, "impetus$opaqueWhenUnblended", 1.0F, 1.0F, 1.0F, 1.0F));
            cancels(hooks, "impetus$noteBlendOn");
            assertFalse(cancels(hooks, "impetus$opaqueWhenUnblended", 1.0F, 1.0F, 1.0F, 0.5F));
        }
    }
}
