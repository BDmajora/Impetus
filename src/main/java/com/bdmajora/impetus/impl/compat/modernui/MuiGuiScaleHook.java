package com.bdmajora.impetus.impl.compat.modernui;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.stream.Stream;

// Hack to work around Modern UI overriding calculateScaleFactor and ignoring vanilla's "0 = auto" convention
public class MuiGuiScaleHook {
    // reflectively found across MUI's various package renames/versions; null if MUI isn't loaded or doesn't have it
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

    // Modern UI's scale ceiling when present, else vanilla's
    public static int getMaxGuiScale() {
        if (calcGuiScalesMethod != null) {
            try {
                // low nibble of MUI's packed result is the actual scale value
                return (int) calcGuiScalesMethod.invoke(null) & 0xf;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        boolean forceUnicode = Minecraft.getMinecraft().gameSettings.forceUnicodeFont;
        return calculateScale(0, forceUnicode);
    }

    // Modern UI's scale formula when present, else vanilla's
    public static int calculateScale(int guiScale, boolean forceUnicode) {
        var framebuffer = Minecraft.getMinecraft().getFramebuffer();
        int width = framebuffer.framebufferWidth;
        int height = framebuffer.framebufferHeight;
        int i = 1;

        // vanilla's algorithm: grow the scale until the next step would shrink the scaled resolution below 320x240
        while (i != guiScale && i < width && i < height && width / (i + 1) >= 320 && height / (i + 1) >= 240) {
            i++;
        }


        if (forceUnicode && i % 2 != 0) {
            ++i;
        }

        return i;
    }

}
