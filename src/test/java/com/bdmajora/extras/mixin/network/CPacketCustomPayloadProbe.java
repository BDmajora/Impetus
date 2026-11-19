package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.network.NetworkLimits;
import com.cleanroommc.common.CleanroomEnvironment;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.EnvironmentStateTweaker;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Proxy;
import top.outlands.foundation.boot.TransformerHolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Runs the real Mixin transformer and executes the resulting Minecraft packet methods.
public final class CPacketCustomPayloadProbe {
    static final String CONFIG = "mixins.extras.payload-test.json";
    private static final String PACKET = "net.minecraft.network.play.client.CPacketCustomPayload";
    private static final int VANILLA = NetworkLimits.VANILLA_SERVERBOUND_PAYLOAD;
    private static int modLimit;

    public static void main(String[] args) throws Exception {
        Proxy mixin = launchMixin();
        com.bdmajora.testing.Mixins.set(Extras.class, "config", new ExtrasConfig());
        vanillaLimits(mixin);
        replacedLimits(mixin);
        oneSidedReplacements(mixin);
        limitsFromDifferentMods(mixin);
        invalidChannels(mixin);
    }

    private static void vanillaLimits(Proxy mixin) throws Exception {
        Class<?> packet = transform(mixin, null, null);
        Extras.options().network.largePackets = false;
        roundTrip(packet, 0, "mod:empty");
        roundTrip(packet, VANILLA, "mod:vanilla");
        rejectsSend(packet, VANILLA + 1);
        rejectsRead(packet, VANILLA + 1);

        Extras.options().network.largePackets = true;
        roundTrip(packet, VANILLA + 1, "mod:widened");
    }

    private static void replacedLimits(Proxy mixin) throws Exception {
        Class<?> packet = transform(mixin, new LdcInsnNode(512), new LdcInsnNode(512));
        for (boolean largePackets : new boolean[]{false, true}) {
            Extras.options().network.largePackets = largePackets;
            roundTrip(packet, 512, "mod:narrow");
            rejectsSend(packet, 513);
            rejectsRead(packet, 513);
        }

        byte[] contents = new byte[512];
        new Random(contents.length).nextBytes(contents);
        ByteBuf backing = Unpooled.directBuffer().writeByte(42).writeBytes(contents).writeByte(43);
        try {
            roundTrip(packet, "mod:sliced", contents, backing.retainedSlice(1, contents.length));
        } finally {
            backing.release();
        }
    }

    private static void oneSidedReplacements(Proxy mixin) throws Exception {
        Extras.options().network.largePackets = true;
        Class<?> sendPatched = transform(mixin, new LdcInsnNode(512), null);
        acceptsSend(sendPatched, 512);
        rejectsSend(sendPatched, 513);
        acceptsRead(sendPatched, VANILLA + 1);

        Class<?> readPatched = transform(mixin, null, new LdcInsnNode(512));
        acceptsRead(readPatched, 512);
        acceptsSend(readPatched, VANILLA + 1);
        rejectsRead(readPatched, 513);
    }

    private static void limitsFromDifferentMods(Proxy mixin) throws Exception {
        Extras.options().network.largePackets = true;
        Class<?> packet = transform(mixin, new LdcInsnNode(65_535), new MethodInsnNode(Opcodes.INVOKESTATIC,
                Type.getInternalName(CPacketCustomPayloadProbe.class), "modLimit", "()I", false));
        modLimit = 65_535;
        roundTrip(packet, 65_535, "abcdefghijklmnopqrst");
        rejectsSend(packet, 65_536);
        rejectsRead(packet, 65_536);

        // A replacement call still owns the limit when it returns the old literal at runtime.
        modLimit = VANILLA;
        acceptsSend(packet, VANILLA + 1);
        rejectsRead(packet, VANILLA + 1);
        roundTrip(packet, VANILLA, "othermod:second");
    }

    public static int modLimit() {
        return modLimit;
    }

    private static void invalidChannels(Proxy mixin) throws Exception {
        Class<?> packet = transform(mixin, new LdcInsnNode(512), new LdcInsnNode(512));
        Packet<?> decoded = (Packet<?>) packet.getConstructor().newInstance();
        PacketBuffer wire = new PacketBuffer(Unpooled.buffer());
        try {
            wire.writeString("abcdefghijklmnopqrstu");
            assertThrows(DecoderException.class, () -> decoded.readPacketData(wire));
            assertNull(payload(decoded));

            wire.clear().writeByte(0x80);
            assertThrows(IndexOutOfBoundsException.class, () -> decoded.readPacketData(wire));
            assertNull(payload(decoded));
        } finally {
            wire.release();
        }
    }

    private static void roundTrip(Class<?> packet, int size, String channel) throws Exception {
        byte[] contents = new byte[size];
        new Random(size).nextBytes(contents);
        roundTrip(packet, channel, contents, Unpooled.wrappedBuffer(contents));
    }

    private static void roundTrip(Class<?> packet, String channel, byte[] contents, ByteBuf input) throws Exception {
        PacketBuffer source = new PacketBuffer(input);
        PacketBuffer wire = new PacketBuffer(Unpooled.buffer());
        try {
            wire.writeByte(42);
            send(packet, channel, source).writePacketData(wire);
            assertEquals(0, source.readerIndex());
            wire.skipBytes(1);
            Packet<?> decoded = (Packet<?>) packet.getConstructor().newInstance();
            decoded.readPacketData(wire);
            ByteBuf data = payload(decoded);
            try {
                assertEquals(channel, packet.getMethod("getChannelName").invoke(decoded));
                assertEquals(contents.length, data.readableBytes());
                byte[] received = new byte[contents.length];
                data.readBytes(received);
                assertArrayEquals(contents, received);
                assertFalse(wire.isReadable());
            } finally {
                data.release();
            }
        } finally {
            source.release();
            wire.release();
        }
    }

    private static void acceptsSend(Class<?> packet, int size) throws Exception {
        PacketBuffer source = new PacketBuffer(Unpooled.buffer(size).writeZero(size));
        try {
            assertSame(source, payload(send(packet, "mod:packet", source)));
        } finally {
            source.release();
        }
    }

    private static void rejectsSend(Class<?> packet, int size) {
        PacketBuffer source = new PacketBuffer(Unpooled.buffer(size).writeZero(size));
        try {
            var error = assertThrows(InvocationTargetException.class, () -> send(packet, "mod:packet", source));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        } finally {
            source.release();
        }
    }

    private static void acceptsRead(Class<?> packet, int size) throws Exception {
        Packet<?> decoded = (Packet<?>) packet.getConstructor().newInstance();
        PacketBuffer wire = wire(size);
        try {
            decoded.readPacketData(wire);
            ByteBuf data = payload(decoded);
            try {
                assertEquals(size, data.readableBytes());
            } finally {
                data.release();
            }
        } finally {
            wire.release();
        }
    }

    private static void rejectsRead(Class<?> packet, int size) throws Exception {
        Packet<?> decoded = (Packet<?>) packet.getConstructor().newInstance();
        PacketBuffer wire = wire(size);
        try {
            assertThrows(IOException.class, () -> decoded.readPacketData(wire));
            assertEquals(size, wire.readableBytes());
            assertNull(payload(decoded));
        } finally {
            wire.release();
        }
    }

    private static PacketBuffer wire(int size) {
        PacketBuffer result = new PacketBuffer(Unpooled.buffer());
        result.writeString("mod:packet").writeZero(size);
        return result;
    }

    private static Packet<?> send(Class<?> packet, String channel, PacketBuffer data) throws ReflectiveOperationException {
        return (Packet<?>) packet.getConstructor(String.class, PacketBuffer.class).newInstance(channel, data);
    }

    private static ByteBuf payload(Packet<?> packet) throws ReflectiveOperationException {
        return (ByteBuf) packet.getClass().getMethod("getBufferData").invoke(packet);
    }

    private static Proxy launchMixin() throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("forgeLaunchArgs", new HashMap<String, String>());
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.minecraftHome = new File(".");
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new));
        TransformerHolder.transformers = new ArrayList<>();
        CleanroomEnvironment.setSide(Side.SERVER);
        MixinBootstrap.init();
        Proxy mixin = new Proxy();
        MixinExtrasBootstrap.init();
        new EnvironmentStateTweaker().getLaunchArguments();
        Mixins.addConfiguration(CONFIG);
        return mixin;
    }

    private static Class<?> transform(Proxy mixin, AbstractInsnNode sendLimit, AbstractInsnNode readLimit) throws Exception {
        var target = new ClassNode();
        try (var original = CPacketCustomPayloadProbe.class.getResourceAsStream("/" + PACKET.replace('.', '/') + ".class")) {
            assertNotNull(original, "Minecraft packet class is missing");
            new ClassReader(original).accept(target, 0);
        }
        replaceLimit(target, "<init>", sendLimit);
        replaceLimit(target, "readPacketData", readLimit);
        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        byte[] patched = mixin.transform(PACKET, PACKET, writer.toByteArray());
        return new ClassLoader(CPacketCustomPayloadProbe.class.getClassLoader()) {
            Class<?> definePacket() {
                return defineClass(PACKET, patched, 0, patched.length);
            }
        }.definePacket();
    }

    private static void replaceLimit(ClassNode target, String name, AbstractInsnNode replacement) {
        int matches = 0;
        for (var method : target.methods) {
            if (!method.name.equals(name)) {
                continue;
            }
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof IntInsnNode constant && constant.operand == VANILLA) {
                    matches++;
                    if (replacement != null) {
                        method.instructions.set(instruction, replacement);
                    }
                }
            }
        }
        assertEquals(1, matches, name + " must have one original payload check");
    }
}
