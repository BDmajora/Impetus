package com.bdmajora.impetus.mixin;

import com.bdmajora.extras.network.NetworkLimits;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The release jar runs on SRG names and the build rewrites only selectors that remap; one with remap off keeps its MCP name, so on a Forge or mod
// class it must not name a method inherited from Minecraft (BlockFluidFinite.updateTick is func_180650_b at runtime, and the unremapped selector
// failed the class load and crashed the chunk builder on a server join). Minecraft targets are left out: Forge adds methods to them that rightly skip remapping
class MixinRemapAuditTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    // Methods Forge patches into Minecraft classes: no SRG name exists for them, so an unremapped selector is the right one
    private static final Set<String> FORGE_ADDED = Set.of("net/minecraft/block/state/BlockStateContainer.createState");

    private record Inherited(String owner, String name, String desc) {
    }

    // A selector's bare name and, when it spells one out, its descriptor
    record Selector(String name, String desc) {
    }

    // Per target, every method its Minecraft ancestors declare
    private final Map<String, List<Inherited>> inherited = new HashMap<>();

    @Test
    void unremappedSelectorsNeverNameMinecraftMethods() throws Exception {
        Path classes = Path.of(NetworkLimits.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> offenders = new ArrayList<>();
        int mixins = 0;
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                ClassNode node = read(Files.readAllBytes(file));
                AnnotationNode mixin = annotation(node.visibleAnnotations, node.invisibleAnnotations, MIXIN);
                if (mixin == null) {
                    continue;
                }
                mixins++;
                boolean classRemap = !Boolean.FALSE.equals(value(mixin, "remap"));
                for (String target : targets(mixin)) {
                    if (target.startsWith("net/minecraft/")) {
                        continue;
                    }
                    for (MethodNode method : node.methods) {
                        for (AnnotationNode member : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                            boolean remap = value(member, "remap") instanceof Boolean explicit ? explicit : classRemap;
                            if (remap) {
                                continue;
                            }
                            for (Selector selector : selectors(member, method)) {
                                if (this.namesMinecraft(target, selector)) {
                                    offenders.add(node.name + " -> " + target + "." + selector.name());
                                }
                            }
                        }
                    }
                }
            }
        }
        // The walk really found the mixins rather than an empty directory
        assertTrue(mixins > 100, "only " + mixins + " mixins under " + classes);
        assertEquals(List.of(), offenders);
    }

    @Test
    void theAuditSeesForgeOverridesOfMinecraftMethods() {
        // The selector that crashed, bare and described: updateTick is declared by Block
        String finite = "net/minecraftforge/fluids/BlockFluidFinite";
        assertTrue(this.namesMinecraft(finite, parse("updateTick")));
        assertTrue(this.namesMinecraft(finite, parse("updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V")));
        // A different overload of the same name is not Minecraft's, nor is a method only Forge's IFluidBlock declares
        assertFalse(this.namesMinecraft(finite, parse("updateTick(I)V")));
        assertFalse(this.namesMinecraft(finite, parse("getFluid")));
        // Forge's own addition to BlockStateContainer, overridden by ExtendedBlockState
        assertFalse(this.namesMinecraft("net/minecraftforge/common/property/ExtendedBlockState", parse("createState")));
        // A class the test classpath does not have contributes nothing rather than failing
        assertFalse(this.namesMinecraft("absent/ModClass", parse("updateTick")));

        assertEquals(new Selector("func_148837_a", "(Lnet/minecraft/network/PacketBuffer;)V"),
                parse("Lnet/minecraft/network/play/client/CPacketCustomPayload;func_148837_a(Lnet/minecraft/network/PacketBuffer;)V"));
        assertEquals(new Selector("init", null), parse("init*"));
        assertEquals("", parse("<init>").name());
        assertNull(parse("decode").desc());
    }

    private boolean namesMinecraft(String target, Selector selector) {
        for (Inherited method : this.minecraftMethods(target)) {
            if (method.name().equals(selector.name()) && (selector.desc() == null || selector.desc().equals(method.desc()))
                    && !FORGE_ADDED.contains(method.owner() + "." + method.name())) {
                return true;
            }
        }
        return false;
    }

    private List<Inherited> minecraftMethods(String target) {
        return this.inherited.computeIfAbsent(target, start -> {
            List<Inherited> methods = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                String owner = queue.pop();
                if (!seen.add(owner) || owner.startsWith("java/")) {
                    continue;
                }
                ClassNode node = load(owner);
                if (node == null) {
                    continue;
                }
                if (owner.startsWith("net/minecraft/")) {
                    node.methods.forEach(method -> methods.add(new Inherited(owner, method.name, method.desc)));
                }
                if (node.superName != null) {
                    queue.push(node.superName);
                }
                queue.addAll(node.interfaces);
            }
            return methods;
        });
    }

    // What the annotation points at: an injector's method selectors, an invoker's explicit name, or the overwritten or shadowed method itself
    private static List<Selector> selectors(AnnotationNode member, MethodNode method) {
        if (OVERWRITE.equals(member.desc)) {
            return List.of(new Selector(method.name, method.desc));
        }
        if (SHADOW.equals(member.desc)) {
            Object prefix = value(member, "prefix");
            String strip = prefix instanceof String declared ? declared : "shadow$";
            return List.of(new Selector(method.name.startsWith(strip) ? method.name.substring(strip.length()) : method.name, method.desc));
        }
        if (INVOKER.equals(member.desc)) {
            return value(member, "value") instanceof String name && !name.isEmpty() ? List.of(new Selector(name, null)) : List.of();
        }
        List<Selector> selectors = new ArrayList<>();
        if (value(member, "method") instanceof List<?> methods) {
            methods.forEach(selector -> selectors.add(parse((String) selector)));
        }
        return selectors;
    }

    // "name", "name(desc)", "name*" or "Lowner;name(desc)"; constructors and regex selectors come back with an empty name, which nothing declares
    static Selector parse(String selector) {
        String rest = selector;
        int owner = rest.indexOf(';');
        int paren = rest.indexOf('(');
        if (rest.startsWith("L") && owner > 0 && (paren < 0 || owner < paren)) {
            rest = rest.substring(owner + 1);
        }
        if (rest.startsWith("<") || rest.startsWith("/")) {
            return new Selector("", null);
        }
        int end = rest.length();
        for (char stop : new char[] {'(', ':', '*'}) {
            int at = rest.indexOf(stop);
            if (at >= 0) {
                end = Math.min(end, at);
            }
        }
        int open = rest.indexOf('(');
        return new Selector(rest.substring(0, end), open >= 0 ? rest.substring(open) : null);
    }

    private static List<String> targets(AnnotationNode mixin) {
        List<String> targets = new ArrayList<>();
        if (value(mixin, "value") instanceof List<?> types) {
            types.forEach(type -> targets.add(((Type) type).getInternalName()));
        }
        if (value(mixin, "targets") instanceof List<?> names) {
            names.forEach(name -> targets.add(((String) name).replace('.', '/')));
        }
        return targets;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    // Mixin's annotations are class-retained, MixinExtras' vary; both lists are read
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        for (AnnotationNode node : annotations(visible, invisible)) {
            if (desc.equals(node.desc)) {
                return node;
            }
        }
        return null;
    }

    private static ClassNode load(String internalName) {
        try (InputStream in = MixinRemapAuditTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            return in == null ? null : read(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }
}
