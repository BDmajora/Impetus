package com.bdmajora.linuxextras.mixin;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.linuxextras.desktop.XdgOpen;
import com.bdmajora.linuxextras.wayland.WaylandWindow;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.Display;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinuxExtrasMixinsTest {
    private static final long WINDOW = 0xCAFEL;
    private final OsKind realOs = OsKind.current();

    @AfterEach
    void restore() {
        Statics.set(OsKind.class, "CURRENT", this.realOs);
    }

    @Test
    void thePluginAppliesOnlyOnLinux() {
        LinuxExtrasMixinPlugin plugin = new LinuxExtrasMixinPlugin();
        Statics.set(OsKind.class, "CURRENT", OsKind.WINDOWS);
        assertFalse(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.bdmajora.linuxextras.mixin.MinecraftWindowMixin"));
        Statics.set(OsKind.class, "CURRENT", OsKind.LINUX);
        assertTrue(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.bdmajora.linuxextras.mixin.MinecraftWindowMixin"));
    }

    @Test
    void theWaylandSwitchGoesBackEvenWhenTheWindowFails() {
        @SuppressWarnings("unchecked")
        Operation<Void> original = Mockito.mock(Operation.class);
        MinecraftWindowMixin mixin = Mixins.instance(MinecraftWindowMixin.class);
        Display.window = WINDOW;
        try (MockedStatic<LinuxExtras> linux = Mockito.mockStatic(LinuxExtras.class);
             MockedStatic<WaylandWindow> window = Mockito.mockStatic(WaylandWindow.class)) {
            Mixins.call(mixin, "linuxextras$createDisplay", original);
            verify(original).call();
            linux.verify(LinuxExtras::onWindowCreated);
            window.verify(() -> WaylandWindow.onWindowCreated(WINDOW));

            // A window that never came up gets no window fixes
            when(original.call()).thenThrow(new IllegalStateException("Failed to create Display window."));
            assertThrows(IllegalStateException.class, () -> Mixins.call(mixin, "linuxextras$createDisplay", original));
            linux.verify(LinuxExtras::onWindowCreated, times(2));
            window.verify(() -> WaylandWindow.onWindowCreated(anyLong()), times(1));
        }
    }

    @Test
    void fullscreenMovedOffCleanroomsOutputResizesTheGame() {
        @SuppressWarnings("unchecked")
        Operation<Void> original = Mockito.mock(Operation.class);
        MinecraftWindowMixin mixin = Mixins.instance(MinecraftWindowMixin.class);
        Minecraft client = (Minecraft) (Object) mixin;
        Mockito.doNothing().when(client).resize(anyInt(), anyInt());
        GLFWVidMode mode = Mockito.mock(GLFWVidMode.class);
        when(mode.width()).thenReturn(2560);
        when(mode.height()).thenReturn(1440);
        Display.window = WINDOW;
        try (MockedStatic<WaylandWindow> window = Mockito.mockStatic(WaylandWindow.class)) {
            // Leaving fullscreen is Cleanroom's alone, and so is a fullscreen already where it belongs
            Mixins.call(mixin, "linuxextras$fullscreenOnPrimary", false, original);
            verify(original).call(false);
            window.verify(() -> WaylandWindow.moveToFullscreenMonitor(anyLong()), never());
            Mixins.call(mixin, "linuxextras$fullscreenOnPrimary", true, original);
            verify(original).call(true);
            verify(client, never()).resize(anyInt(), anyInt());

            window.when(() -> WaylandWindow.moveToFullscreenMonitor(WINDOW)).thenReturn(mode);
            Mixins.call(mixin, "linuxextras$fullscreenOnPrimary", true, original);
            verify(client).resize(2560, 1440);
        }
    }

    @Test
    void waylandTakesTheIconsOverFromCleanroom() {
        // Each call's arguments, since Mockito unpacks an array passed through varargs
        List<Object[]> calls = new ArrayList<>();
        Operation<Integer> original = args -> {
            calls.add(args);
            return 0;
        };
        MinecraftWindowMixin mixin = Mixins.instance(MinecraftWindowMixin.class);
        ByteBuffer[] icons = {ByteBuffer.allocate(4)};
        try (MockedStatic<WaylandWindow> window = Mockito.mockStatic(WaylandWindow.class)) {
            assertEquals(0, (int) Mixins.call(mixin, "linuxextras$waylandIcon", icons, original));
            assertEquals(1, calls.size());
            assertSame(icons, calls.get(0)[0]);

            window.when(() -> WaylandWindow.takeIcon(icons)).thenReturn(true);
            assertEquals(0, (int) Mixins.call(mixin, "linuxextras$waylandIcon", icons, original));
            assertEquals(1, calls.size());
        }
    }

    @Test
    void linksAndFoldersOpenThroughXdgOpenAndFallBackWhenItCannot() {
        GuiScreenLinkMixin screen = Mixins.instance(GuiScreenLinkMixin.class);
        File folder = new File("resourcepacks").getAbsoluteFile();
        try (MockedStatic<XdgOpen> xdg = Mockito.mockStatic(XdgOpen.class)) {
            xdg.when(() -> XdgOpen.open("https://mcparks.us/audio?user=BDMajora")).thenReturn(true);
            xdg.when(() -> XdgOpen.open(folder.getAbsolutePath())).thenReturn(true);

            CallbackInfo link = Mixins.ci();
            Mixins.call(screen, "linuxextras$openWithXdg", URI.create("https://mcparks.us/audio?user=BDMajora"), link);
            assertTrue(link.isCancelled());
            // xdg-open missing or switched off: AWT's own attempt still runs and logs
            CallbackInfo unopenedLink = Mixins.ci();
            Mixins.call(screen, "linuxextras$openWithXdg", URI.create("https://example.com"), unopenedLink);
            assertFalse(unopenedLink.isCancelled());

            CallbackInfo opened = Mixins.ci();
            Mixins.call(OpenGlHelperOpenFileMixin.class, "linuxextras$openWithXdg", folder, opened);
            assertTrue(opened.isCancelled());
            CallbackInfo unopened = Mixins.ci();
            Mixins.call(OpenGlHelperOpenFileMixin.class, "linuxextras$openWithXdg", new File("saves").getAbsoluteFile(), unopened);
            assertFalse(unopened.isCancelled());
        }
        assertNotNull(Mixins.instance(OpenGlHelperOpenFileMixin.class));
    }
}
