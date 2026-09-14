package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;

// The wire limits a large modpack outgrows, after TonimatasDEV's Packet Fixer: every value is read at the point vanilla would have used its constant, so the switch takes effect on the next packet
public final class NetworkLimits {
    // Vanilla's constants, returned unchanged when the switch is off
    public static final int VANILLA_FRAME_LENGTH_BYTES = 3;
    public static final int VANILLA_COMPRESSED_PACKET = 2097152;
    public static final long VANILLA_NBT = 2097152L;
    public static final int VANILLA_STRING = 32767;
    public static final int VANILLA_CHUNK_DATA = 2097152;
    public static final int VANILLA_CLIENTBOUND_PAYLOAD = 1048576;
    public static final int VANILLA_SERVERBOUND_PAYLOAD = 32767;

    // Five bytes carries any int, so the frame length is no longer the 21-bit bottleneck
    private static final int WIDE_FRAME_LENGTH_BYTES = 5;
    // Strings are checked as UTF-8 byte length times four, so the cap stays clear of int overflow
    private static final int WIDE_STRING = Integer.MAX_VALUE / 8;

    private NetworkLimits() {
    }

    private static ExtrasConfig.NetworkSettings settings() {
        return Extras.options().network;
    }

    public static int frameLengthBytes() {
        return settings().largePackets ? WIDE_FRAME_LENGTH_BYTES : VANILLA_FRAME_LENGTH_BYTES;
    }

    public static int compressedPacketLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_COMPRESSED_PACKET;
    }

    public static long nbtLimit() {
        return settings().largePackets ? Long.MAX_VALUE : VANILLA_NBT;
    }

    public static int stringLimit() {
        return settings().largePackets ? WIDE_STRING : VANILLA_STRING;
    }

    public static int chunkDataLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CHUNK_DATA;
    }

    public static int clientboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_CLIENTBOUND_PAYLOAD;
    }

    public static int serverboundPayloadLimit() {
        return settings().largePackets ? Integer.MAX_VALUE : VANILLA_SERVERBOUND_PAYLOAD;
    }

    public static int readTimeoutSeconds() {
        return settings().readTimeoutSeconds;
    }

    public static int loginTimeoutTicks() {
        return settings().loginTimeoutSeconds * 20;
    }

    public static long keepAliveTimeoutMillis() {
        return settings().keepAliveTimeoutSeconds * 1000L;
    }
}
