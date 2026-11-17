package com.bdmajora.fulgor.async;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// Check of whether a world is the client world.
@SideOnly(Side.CLIENT)
public final class ClientWorldCheck {
    // Prevents instantiation
    private ClientWorldCheck() {
    }

    // Returns whether a world is the one the client renders.
    public static boolean isCurrentClientWorld(World world) {
        return Minecraft.getMinecraft().world == world;
    }
}
