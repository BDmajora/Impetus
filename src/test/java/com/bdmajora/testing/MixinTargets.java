package com.bdmajora.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

// Resolves a mixin handler's injection points against the real, untransformed game bytecode the way Mixin does (slice from/to,
// INVOKE and FIELD targets with opcode, ordinal, several @At), so a test can assert a hook lands on exactly the calls it means.
public final class MixinTargets {
    private MixinTargets() {
    }

    // Every instruction the handler's @At (or @At array) selects in the target method.
    public static List<AbstractInsnNode> resolve(MethodNode target, AnnotationNode injector) {
        AbstractInsnNode start = target.instructions.getFirst();
        AbstractInsnNode end = null;
        Object slice = value(injector, "slice");
        if (slice != null) {
            AnnotationNode sliceNode = (AnnotationNode) single(slice);
            Object from = value(sliceNode, "from");
            Object to = value(sliceNode, "to");
            if (from != null) {
                start = matches((AnnotationNode) single(from), target.instructions.getFirst(), null).get(0);
            }
            if (to != null) {
                end = matches((AnnotationNode) single(to), start, null).get(0);
            }
        }
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AnnotationNode at : ats(injector)) {
            List<AbstractInsnNode> found = matches(at, start, end);
            Object ordinal = value(at, "ordinal");
            if (ordinal == null) {
                out.addAll(found);
            } else {
                out.add(found.get((Integer) ordinal));
            }
        }
        return out;
    }

    // The @At annotations of an injector, whether written as one or as an array.
    public static List<AnnotationNode> ats(AnnotationNode injector) {
        Object at = value(injector, "at");
        List<AnnotationNode> out = new ArrayList<>();
        if (at instanceof List<?> list) {
            for (Object item : list) {
                out.add((AnnotationNode) item);
            }
        } else if (at != null) {
            out.add((AnnotationNode) at);
        }
        return out;
    }

    // Instructions from start to end (inclusive, end null = to the last) that one @At names; HEAD and RETURN resolve too.
    public static List<AbstractInsnNode> matches(AnnotationNode at, AbstractInsnNode start, AbstractInsnNode end) {
        String kind = (String) value(at, "value");
        Object targetRef = value(at, "target");
        Object opcode = value(at, "opcode");
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AbstractInsnNode insn = start; insn != null; insn = insn.getNext()) {
            boolean hit = switch (kind) {
                case "HEAD" -> out.isEmpty() && insn.getOpcode() >= 0;
                case "RETURN" -> insn.getOpcode() >= 172 && insn.getOpcode() <= 177;
                case "INVOKE" -> insn instanceof MethodInsnNode call && ("L" + call.owner + ";" + call.name + call.desc).equals(targetRef);
                case "FIELD" -> insn instanceof FieldInsnNode field
                        && ("L" + field.owner + ";" + field.name + ":" + field.desc).equals(targetRef)
                        && (opcode == null || (Integer) opcode == field.getOpcode());
                default -> throw new AssertionError("unsupported @At " + kind);
            };
            if (hit) {
                out.add(insn);
            }
            if (insn == end) {
                break;
            }
        }
        if (out.isEmpty()) {
            throw new AssertionError("no match for " + kind + " " + targetRef);
        }
        return out;
    }

    // The injector annotation (any annotation with an "at" member) of a handler method in a mixin's class file.
    public static AnnotationNode handler(Class<?> mixin, String name) {
        MethodNode method = find(classNode(mixin), name, null);
        if (method == null) {
            throw new AssertionError(mixin.getSimpleName() + " has no " + name);
        }
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        for (AnnotationNode annotation : annotations) {
            if (value(annotation, "at") != null) {
                return annotation;
            }
        }
        throw new AssertionError(name + " has no injector");
    }

    // An annotation member's value, null when absent; enum constants come back as their name.
    public static Object value(AnnotationNode node, String name) {
        if (node.values == null) {
            return null;
        }
        for (int i = 0; i < node.values.size(); i += 2) {
            if (node.values.get(i).equals(name)) {
                Object v = node.values.get(i + 1);
                return v instanceof String[] enumValue ? enumValue[1] : v;
            }
        }
        return null;
    }

    // Strips the one-element list javac writes for an array member given a single value.
    public static Object single(Object value) {
        return value instanceof List<?> list && list.size() == 1 ? list.get(0) : value;
    }

    // The previous real instruction, skipping labels, line numbers and frames.
    public static AbstractInsnNode previous(AbstractInsnNode insn) {
        AbstractInsnNode at = insn.getPrevious();
        while (at != null && at.getOpcode() < 0) {
            at = at.getPrevious();
        }
        return at;
    }

    public static List<AbstractInsnNode> calls(MethodNode method, String owner, String name) {
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                out.add(insn);
            }
        }
        return out;
    }

    public static MethodNode method(Class<?> owner, String name, String desc) {
        MethodNode method = find(classNode(owner), name, desc);
        if (method == null) {
            throw new AssertionError(owner.getSimpleName() + "." + name + desc + " not found");
        }
        return method;
    }

    public static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                return method;
            }
        }
        return null;
    }

    // Reads the class file from the classpath as a resource, so no harness agent has transformed it.
    public static ClassNode classNode(Class<?> type) {
        ClassNode node = new ClassNode();
        try (InputStream in = type.getClassLoader().getResourceAsStream(Type.getInternalName(type) + ".class")) {
            if (in == null) {
                throw new AssertionError("no class file for " + type.getName());
            }
            new ClassReader(in).accept(node, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return node;
    }
}
