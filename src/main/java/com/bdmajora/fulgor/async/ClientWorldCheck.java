package com.bdmajora.fulgor.async;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// Whether a remote world is the one the client is actually in, since JEI and GregTech preview worlds are also World instances with isRemote set and must not get worker threads
@SideOnly(Side.CLIENT)
public final class ClientWorldCheck {
    private ClientWorldCheck() {
    }

    public static boolean isCurrentClientWorld(World world) {
        return Minecraft.getMinecraft().world == world;
    }
}
