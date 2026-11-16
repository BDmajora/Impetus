package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.fml.common.Loader;

// Size limits of network packets.
public final class NetworkLimits {
    // Constant vanilla frame length bytes.
    public static final int VANILLA_FRAME_LENGTH_BYTES = 3;
    // Constant vanilla compressed packet.
    public static final int VANILLA_COMPRESSED_PACKET = 2097152;
    // Constant vanilla nbt.
    public static final long VANILLA_NBT = 2097152L;
    // Constant vanilla string.
    public static final int VANILLA_STRING = 32767;
    // Constant vanilla chunk data.
    public static final int VANILLA_CHUNK_DATA = 2097152;
    // Constant vanilla clientbound payload.
    public static final int VANILLA_CLIENTBOUND_PAYLOAD = 1048576;
    // Constant vanilla serverbound payload.
    public static final int VANILLA_SERVERBOUND_PAYLOAD = 32767;

    // Constant wide frame length bytes.
    private static final int WIDE_FRAME_LENGTH_BYTES = 5;
    // Constant wide string.
    private static final int WIDE_STRING = Integer.MAX_VALUE / 8;

    // Choice of the little tiles option.
    private static Boolean littleTiles;

    // Prevents instantiation
    private NetworkLimits() {
    }

    // Handles the settings.
    private static ExtrasConfig.NetworkSettings settings() {
        return Extras.options().network;
    }

    // Handles the frame length bytes.
    public static int frameLengthBytes() {
        return settings().largePackets ? WIDE_FRAME_LENGTH_BYTES : VANILLA_FRAME_LENGTH_BYTES;
    }

    // Handles the compressed packet limit.
    public static int compressedPacketLimit() {
        return settings().largePackets || littleTilesLoaded() ? Integer.MAX_VALUE : VANILLA_COMPRESSED_PACKET;
    }

    // Handles the little tiles loaded.
    private static boolean littleTilesLoaded() {
        Boolean loaded = littleTiles;
        if (loaded == null) {
            loaded = Loader.isModLoaded("littletiles");
            littleTiles = loaded;
        }
        return loaded;
    }

    // Handles the nbt limit.
    public static long nbtLimit() {
        return settings().largePackets ? Long.MAX_VALUE : VANILLA_NBT;
    }

    // Handles the string limit.
    public static int stringLimit() {
        return settings().largePackets ? WIDE_STRING : VANILLA_STRING;
    }

    // Handles the chunk data limit.
    public static int chunkDataLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CHUNK_DATA;
    }

    // Handles the clientbound payload limit.
    public static int clientboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CLIENTBOUND_PAYLOAD;
    }

    // Handles the serverbound payload limit.
    public static int serverboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_SERVERBOUND_PAYLOAD;
    }

    // Reads the timeout seconds.
    public static int readTimeoutSeconds() {
        return settings().readTimeoutSeconds;
    }

    // Handles the login timeout ticks.
    public static int loginTimeoutTicks() {
        return settings().loginTimeoutSeconds * 20;
    }

    // Handles the keep alive timeout millis.
    public static long keepAliveTimeoutMillis() {
        return settings().keepAliveTimeoutSeconds * 1000L;
    }
}
