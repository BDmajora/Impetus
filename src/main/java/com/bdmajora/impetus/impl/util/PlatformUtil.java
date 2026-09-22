package com.bdmajora.impetus.impl.util;

import net.minecraft.client.Minecraft;

import java.io.File;

public class PlatformUtil {

    // The game directory
    public static File getGameDir() {
        return Minecraft.getMinecraft().gameDir;
    }
}
