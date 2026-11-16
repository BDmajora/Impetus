package com.bdmajora.impetus.impl.compat.modernui;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.stream.Stream;

// Hook that reads the Modern UI gui scale.
public class MuiGuiScaleHook {
    // Gui scale method of Modern UI or null.
    private static final Method calcGuiScalesMethod;

    static {
        calcGuiScalesMethod = Stream.of(
                "icyllis.modernui.forge.MForgeCompat",
                "icyllis.modernui.forge.MuiForgeApi",
                "icyllis.modernui.mc.forge.MuiForgeApi",
                "icyllis.modernui.mc.MuiModApi"
        ).flatMap(clzName -> {
            try {
                return Stream.of(Class.forName(clzName));
            } catch (Throwable e) {
                return Stream.of();
            }
        }).flatMap(clz -> {
            try {
                Method m = clz.getDeclaredMethod("calcGuiScales");
                m.setAccessible(true);
                return Stream.of(m);
            } catch (Throwable e) {
                return Stream.of();
            }
        }).findFirst().orElse(null);
    }

    // Returns the max gui scale.
    public static int getMaxGuiScale() {
        if (calcGuiScalesMethod != null) {
            try {
                return (int) calcGuiScalesMethod.invoke(null) & 0xf;
            } catch (Throwable e) {
                org.apache.logging.log4j.LogManager.getLogger("Impetus").warn("Could not read the Modern UI gui scale", e);
            }
        }
        boolean forceUnicode = Minecraft.getMinecraft().gameSettings.forceUnicodeFont;
        return calculateScale(0, forceUnicode);
    }

    // Returns the gui scale from Modern UI or the vanilla value.
    public static int calculateScale(int guiScale, boolean forceUnicode) {
        var framebuffer = Minecraft.getMinecraft().getFramebuffer();
        int width = framebuffer.framebufferWidth;
        int height = framebuffer.framebufferHeight;
        int i = 1;

        while (i != guiScale && i < width && i < height && width / (i + 1) >= 320 && height / (i + 1) >= 240) {
            i++;
        }

        if (forceUnicode && i % 2 != 0) {
            ++i;
        }

        return i;
    }

}
