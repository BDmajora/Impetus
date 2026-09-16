package com.bdmajora.extras.mixin.network;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

// The server's connection list, iterated once per tick to flush deferred writes
@Mixin(NetworkSystem.class)
public interface NetworkSystemAccessor {
    @Accessor("networkManagers")
    List<NetworkManager> impetus$networkManagers();
}
