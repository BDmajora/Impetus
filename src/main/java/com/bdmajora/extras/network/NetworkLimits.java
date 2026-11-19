package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.fml.common.Loader;

// Size limits of network packets.
// Keep mixin hooks for vanilla limits optional. Coremods can replace those checks before Mixin runs.
public final class NetworkLimits {
    // Bytes of the vanilla frame length prefix.
    public static final int VANILLA_FRAME_LENGTH_BYTES = 3;
    // Vanilla's limit on a decompressed packet in bytes.
    public static final int VANILLA_COMPRESSED_PACKET = 2097152;
    // Vanilla's NBT read quota in bytes.
    public static final long VANILLA_NBT = 2097152L;
    // Vanilla's longest string.
    public static final int VANILLA_STRING = 32767;
    // Vanilla's limit on chunk data in one packet in bytes.
    public static final int VANILLA_CHUNK_DATA = 2097152;
    // Vanilla's limit on a clientbound custom payload in bytes.
    public static final int VANILLA_CLIENTBOUND_PAYLOAD = 1048576;
    // Vanilla's limit on a custom payload the client sends.
    public static final int VANILLA_SERVERBOUND_PAYLOAD = 32767;

    // Bytes of the widened frame length prefix.
    private static final int WIDE_FRAME_LENGTH_BYTES = 5;
    // Longest string once limits are widened.
    private static final int WIDE_STRING = Integer.MAX_VALUE / 8;

    // Whether LittleTiles is loaded or null before the first check.
    private static Boolean littleTiles;

    // Prevents instantiation
    private NetworkLimits() {
    }

    // Returns the network settings of the loaded config.
    private static ExtrasConfig.NetworkSettings settings() {
        return Extras.options().network;
    }

    // Returns the bytes of the frame length prefix in use.
    public static int frameLengthBytes() {
        return settings().largePackets ? WIDE_FRAME_LENGTH_BYTES : VANILLA_FRAME_LENGTH_BYTES;
    }

    // Returns the largest decompressed packet accepted.
    public static int compressedPacketLimit() {
        return settings().largePackets || littleTilesLoaded() ? Integer.MAX_VALUE : VANILLA_COMPRESSED_PACKET;
    }

    // Returns whether LittleTiles is installed.
    private static boolean littleTilesLoaded() {
        Boolean loaded = littleTiles;
        if (loaded == null) {
            loaded = Loader.isModLoaded("littletiles");
            littleTiles = loaded;
        }
        return loaded;
    }

    // Returns the NBT read quota of one packet.
    public static long nbtLimit() {
        return settings().largePackets ? Long.MAX_VALUE : VANILLA_NBT;
    }

    // Returns the longest string a packet may carry.
    public static int stringLimit() {
        return settings().largePackets ? WIDE_STRING : VANILLA_STRING;
    }

    // Returns the most chunk data one packet may carry.
    public static int chunkDataLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CHUNK_DATA;
    }

    // Returns the largest custom payload the server may send.
    public static int clientboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CLIENTBOUND_PAYLOAD;
    }

    // Returns the largest custom payload the client may send.
    public static int serverboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_SERVERBOUND_PAYLOAD;
    }

    // Reads the timeout seconds.
    public static int readTimeoutSeconds() {
        return settings().readTimeoutSeconds;
    }

    // Returns the ticks a login may take before the connection is dropped.
    public static int loginTimeoutTicks() {
        return settings().loginTimeoutSeconds * 20;
    }

    // Returns the milliseconds without a keep alive before the connection is dropped.
    public static long keepAliveTimeoutMillis() {
        return settings().keepAliveTimeoutSeconds * 1000L;
    }
}
