package com.bdmajora.linuxextras.mixin;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.linuxextras.desktop.XdgOpen;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.URI;

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
        Statics.set(OsKind.class, "detected", this.realOs);
    }

    @Test
    void thePluginAppliesOnlyOnLinux() {
        LinuxExtrasMixinPlugin plugin = new LinuxExtrasMixinPlugin();
        Statics.set(OsKind.class, "detected", OsKind.WINDOWS);
        assertFalse(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.bdmajora.linuxextras.mixin.MinecraftWindowMixin"));
        Statics.set(OsKind.class, "detected", OsKind.LINUX);
        assertTrue(plugin.shouldApplyMixin("net.minecraft.client.Minecraft", "com.bdmajora.linuxextras.mixin.MinecraftWindowMixin"));
    }

    @Test
    void theDisplayPriorityIsSetBeforeSdlVideoStarts() {
        try (MockedStatic<LinuxExtras> linux = Mockito.mockStatic(LinuxExtras.class)) {
            Mixins.call(Mixins.instance(MinecraftWindowMixin.class), "linuxextras$prepareWindow", Mixins.ci());
            linux.verify(LinuxExtras::prepareWindow);
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
