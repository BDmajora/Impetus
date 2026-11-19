package com.bdmajora.extras.mixin.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CPacketCustomPayloadTest {
    @Test
    void packetLimitsSurviveOtherTransformers(@TempDir Path work) throws Exception {
        Path configJar = work.resolve("payload-test.jar");
        writeConfig(configJar);
        Path log = work.resolve("probe.log");
        String classpath = configJar + File.pathSeparator + System.getProperty("impetus.test.classpath",
                System.getProperty("java.class.path"));

        // CleanMix and Foundation keep process-wide state.
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
                CPacketCustomPayloadProbe.class.getName())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), () -> "Packet probe timed out. See " + log);
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private static void writeConfig(Path output) throws Exception {
        var original = CPacketCustomPayloadTest.class.getResourceAsStream("/mixins.extras.json");
        assertNotNull(original, "Missing mixins.extras.json");
        try (var reader = new InputStreamReader(original, StandardCharsets.UTF_8);
             var jar = new JarOutputStream(Files.newOutputStream(output))) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            var packet = new JsonPrimitive("network.CPacketCustomPayloadMixin");
            assertTrue(config.getAsJsonArray("mixins").contains(packet));
            var mixins = new JsonArray();
            mixins.add(packet);
            config.add("mixins", mixins);
            config.remove("client");
            config.remove("server");
            jar.putNextEntry(new JarEntry(CPacketCustomPayloadProbe.CONFIG));
            jar.write(config.toString().getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }
}
