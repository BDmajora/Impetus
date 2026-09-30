package com.bdmajora.extras.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkLimitsTest {
    private ExtrasConfig config;

    @BeforeEach
    void installConfig() throws ReflectiveOperationException {
        var ctor = ExtrasConfig.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        config = ctor.newInstance();
        Mixins.set(Extras.class, "config", config);
    }

    @AfterEach
    void removeConfig() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(NetworkLimits.class, "littleTiles", null);
    }

    @Test
    void largePacketsWidenEveryLimit() {
        config.network.largePackets = true;
        assertEquals(5, NetworkLimits.frameLengthBytes());
        assertEquals(Integer.MAX_VALUE, NetworkLimits.compressedPacketLimit());
        assertEquals(Long.MAX_VALUE, NetworkLimits.nbtLimit());
        assertEquals(Integer.MAX_VALUE / 8, NetworkLimits.stringLimit());
        assertEquals(Integer.MAX_VALUE, NetworkLimits.chunkDataLimit());
        assertEquals(Integer.MAX_VALUE, NetworkLimits.clientboundPayloadLimit());
        assertEquals(Integer.MAX_VALUE, NetworkLimits.serverboundPayloadLimit());
    }

    @Test
    void switchOffReturnsVanillaConstants() {
        config.network.largePackets = false;
        Mixins.set(NetworkLimits.class, "littleTiles", false);
        assertEquals(NetworkLimits.VANILLA_FRAME_LENGTH_BYTES, NetworkLimits.frameLengthBytes());
        assertEquals(NetworkLimits.VANILLA_COMPRESSED_PACKET, NetworkLimits.compressedPacketLimit());
        assertEquals(NetworkLimits.VANILLA_NBT, NetworkLimits.nbtLimit());
        assertEquals(NetworkLimits.VANILLA_STRING, NetworkLimits.stringLimit());
        assertEquals(NetworkLimits.VANILLA_CHUNK_DATA, NetworkLimits.chunkDataLimit());
        assertEquals(NetworkLimits.VANILLA_CLIENTBOUND_PAYLOAD, NetworkLimits.clientboundPayloadLimit());
        assertEquals(NetworkLimits.VANILLA_SERVERBOUND_PAYLOAD, NetworkLimits.serverboundPayloadLimit());
    }

    @Test
    void littleTilesKeepsTheCompressedPacketCapLifted() {
        config.network.largePackets = false;
        // LittleTiles already took the cap out of vanilla's decode, so the switch cannot bring it back
        Mc.forge("littletiles");
        assertEquals(Integer.MAX_VALUE, NetworkLimits.compressedPacketLimit());
        assertEquals(NetworkLimits.VANILLA_NBT, NetworkLimits.nbtLimit());
        // Looked up once and remembered
        Mc.forge();
        assertEquals(Integer.MAX_VALUE, NetworkLimits.compressedPacketLimit());
        Mixins.set(NetworkLimits.class, "littleTiles", null);
        assertEquals(NetworkLimits.VANILLA_COMPRESSED_PACKET, NetworkLimits.compressedPacketLimit());
    }

    @Test
    void timeoutsScaleFromSeconds() {
        config.network.readTimeoutSeconds = 7;
        config.network.loginTimeoutSeconds = 3;
        config.network.keepAliveTimeoutSeconds = 9;
        assertEquals(7, NetworkLimits.readTimeoutSeconds());
        assertEquals(60, NetworkLimits.loginTimeoutTicks());
        assertEquals(9000L, NetworkLimits.keepAliveTimeoutMillis());
    }
}
