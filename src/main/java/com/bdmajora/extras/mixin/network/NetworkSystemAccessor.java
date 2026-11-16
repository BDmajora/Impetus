package com.bdmajora.extras.mixin.network;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

// Mixin that changes NetworkSystem.
@Mixin(NetworkSystem.class)
public interface NetworkSystemAccessor {
    // Gives access to the field network managers.
    @Accessor("networkManagers")
    List<NetworkManager> impetus$networkManagers();
}
