package com.bdmajora.extras.network;

// Implemented on NetHandlerPlayClient: true once Minecraft.loadWorld(null) has cleaned the handler up, i.e. the connection is gone and its world with it. A plain interface rather than a reference to the client class so PacketThreadUtil's common mixin can test for it on a dedicated server
public interface RetiredNetHandler {
    boolean impetus$isRetired();
}
