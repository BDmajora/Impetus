package com.bdmajora.impetus.impl.util;

import net.minecraft.client.Minecraft;

import java.io.File;

// Helpers for the operating system.
public class PlatformUtil {

    // Returns the game dir.
    public static File getGameDir() {
        return Minecraft.getMinecraft().gameDir;
    }
}
