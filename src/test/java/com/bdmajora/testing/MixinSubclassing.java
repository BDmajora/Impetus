package com.bdmajora.testing;

import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// Re-parents mixin classes onto their targets for tests.
final class MixinSubclassing implements ClassFileTransformer {
    // Classes that get a generated no argument constructor.
    private final Set<String> needConstructor = new HashSet<>();

    // Classes whose only no argument constructor is private.
    private final Set<String> widenConstructor = new HashSet<>();

    // Extra constructor descriptors a target must carry.
    private final Map<String, Set<String>> extraConstructors = new HashMap<>();

    // Every mixin target.
    private final Set<String> targets = new HashSet<>();

    // Static accessor and invoker methods of mixin interfaces.
    private final Map<String, Static> statics = new HashMap<>();

    // One generated static accessor.
    private record Static(String target, String member, boolean field) {}

    // Members the accessors reach.
    private final Map<String, Set<String>> openMembers = new HashMap<>();

    // Private nested game classes a mixin reaches by handle.
    private static final Set<String> PUBLISHED = Set.of(
            "net/minecraftforge/client/model/ModelLoader$WeightedRandomModel",
            "net/minecraftforge/client/model/ModelLoader$MultipartModel");

    // LWJGL classes whose static initialiser is emptied.
    private static final Set<String> NATIVE_INITIALISERS = Set.of("org/lwjgl/opengl/Display");

    // Mod classes whose static finals tests swap.
    private static final Set<String> SWAPPED_STATICS = Set.of(
            "com/bdmajora/impetus/ImpetusVintage",
            "com/bdmajora/fulgor/lighting/DynamicLightsBridge",
            "com/bdmajora/equilibrium/mixin/EquilibriumMixinPlugin",
            "com/bdmajora/impetus/impl/compat/fluidlogged/FluidloggedCompat",
            "com/bdmajora/impetus/impl/compat/littletiles/LittleTilesCompat");

    MixinSubclassing() {
        Set<Path> roots = classRoots();
        for (Path root : roots) {
            scan(root);
        }
        for (Path root : roots) {
            findAccessorCallers(root);
        }
    }

    // Mod classes that call a static accessor.
    private final Set<String> accessorCallers = new HashSet<>();

    private void findAccessorCallers(Path root) {
        if (statics.isEmpty()) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class") && !path.toString().contains("mixin")
                            && root.relativize(path).toString().startsWith("com/bdmajora/")
                            && !root.relativize(path).toString().startsWith("com/bdmajora/testing/"))
                    .forEach(path -> {
                        try {
                            ClassReader reader = new ClassReader(Files.readAllBytes(path));
                            String name = reader.getClassName();
                            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                                    return new MethodVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean isInterface) {
                                            if (opcode == Opcodes.INVOKESTATIC && statics.containsKey(owner + "." + called + desc)) {
                                                accessorCallers.add(name);
                                            }
                                        }
                                    };
                                }
                            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        } catch (IOException | RuntimeException ignored) {
                            // A class file that cannot be read here is simply not preloaded
                        }
                    });
        } catch (IOException ignored) {
            // Nothing to scan
        }
    }

    // Returns the directories holding compiled mod classes.
    private static Set<Path> classRoots() {
        Set<Path> roots = new HashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            Path path = Path.of(entry);
            if (Files.isDirectory(path.resolve("com/bdmajora"))) {
                roots.add(path);
            }
        }
        return roots;
    }

    // Collects the targets and ancestors without a no argument constructor.
    private void scan(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class") && path.toString().contains("mixin"))
                    .forEach(path -> {
                        try {
                            ClassReader reader = new ClassReader(Files.readAllBytes(path));
                            if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                                collectStatics(reader);
                                return;
                            }
                            String target = target(reader);
                            if (target != null) {
                                targets.add(target);
                                requireConstructor(target);
                                for (String descriptor : constructors(reader)) {
                                    if (!"()V".equals(descriptor) && !declares(target, descriptor)) {
                                        extraConstructors.computeIfAbsent(target, key -> new HashSet<>()).add(descriptor);
                                    }
                                }
                            }
                        } catch (IOException | RuntimeException ignored) {
                            // A class file that cannot be read here simply gets no constructor added
                        }
                    });
        } catch (IOException ignored) {
            // Nothing to scan
        }
    }

    // Walks up from a target adding classes without a no argument constructor.
    private void requireConstructor(String internalName) {
        String name = internalName;
        while (name != null && !"java/lang/Object".equals(name) && !needConstructor.contains(name)) {
            byte[] bytes = read(name);
            if (bytes == null) {
                return;
            }
            ClassReader reader = new ClassReader(bytes);
            int existing = noArgConstructor(reader);
            if (existing == Opcodes.ACC_PRIVATE) {
                widenConstructor.add(name);
                return;
            }
            if (existing != -1) {
                return;
            }
            needConstructor.add(name);
            name = reader.getSuperName();
        }
    }

    // Records a mixin interface's static accessors.
    private void collectStatics(ClassReader reader) {
        String target = target(reader);
        if (target == null) {
            return;
        }
        String owner = reader.getClassName();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_STATIC) == 0 || (access & Opcodes.ACC_ABSTRACT) != 0) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        boolean field = "Lorg/spongepowered/asm/mixin/gen/Accessor;".equals(annotation);
                        if (!field && !"Lorg/spongepowered/asm/mixin/gen/Invoker;".equals(annotation)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String key, Object value) {
                                if ("value".equals(key)) {
                                    statics.put(owner + "." + name + descriptor,
                                            new Static(target, (String) value, field));
                                    openMembers.computeIfAbsent(target, key2 -> new HashSet<>()).add((String) value);
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    // The descriptors of the constructors a class declares
    private static Set<String> constructors(ClassReader reader) {
        Set<String> found = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<init>".equals(name)) {
                    found.add(descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found;
    }

    private static boolean declares(String internalName, String descriptor) {
        byte[] bytes = read(internalName);
        return bytes != null && constructors(new ClassReader(bytes)).contains(descriptor);
    }

    private static byte[] read(String internalName) {
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(internalName + ".class")) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    // Returns the access of the no argument constructor or -1.
    private static int noArgConstructor(ClassReader reader) {
        int[] found = {-1};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<init>".equals(name) && "()V".equals(descriptor)) {
                    found[0] = access & Opcodes.ACC_PRIVATE;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    // Loads every class that needs a generated constructor.
    void prepare() {
        Set<String> all = new HashSet<>(needConstructor);
        all.addAll(widenConstructor);
        all.addAll(extraConstructors.keySet());
        // Adds the published classes.
        all.addAll(PUBLISHED);
        all.addAll(accessorCallers);
        for (String name : all) {
            try {
                Class.forName(name.replace('/', '.'), false, MixinSubclassing.class.getClassLoader());
            } catch (Throwable ignored) {
                // A class that cannot be loaded here simply keeps whatever constructors it has
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> beingRedefined, ProtectionDomain domain, byte[] bytes) {
        if (name == null) {
            return null;
        }
        try {
            if (PUBLISHED.contains(name)) {
                return publishClass(bytes);
            }
            if (NATIVE_INITIALISERS.contains(name)) {
                return withoutStaticInitialiser(bytes);
            }
            // A mod class that is itself a mixin target still calls generated accessors of its own
            boolean modClass = name.startsWith("com/bdmajora/") && !name.startsWith("com/bdmajora/testing/")
                    && !name.contains("/mixin");
            // A class reached only through static accessors still needs those members opened
            boolean opens = targets.contains(name) || openMembers.containsKey(name) || SWAPPED_STATICS.contains(name);
            if (needConstructor.contains(name) || extraConstructors.containsKey(name)) {
                byte[] withConstructors = addConstructor(bytes, needConstructor.contains(name),
                        extraConstructors.getOrDefault(name, Set.of()), targets.contains(name));
                byte[] opened = opens
                        ? openStatics(withConstructors, openMembers.getOrDefault(name, Set.of()))
                        : withConstructors;
                return modClass ? pointedOr(opened) : opened;
            }
            if (widenConstructor.contains(name)) {
                byte[] widened = widenConstructor(bytes);
                return modClass ? pointedOr(widened) : widened;
            }
            if (opens) {
                byte[] opened = openStatics(bytes, openMembers.getOrDefault(name, Set.of()));
                return modClass ? pointedOr(opened) : opened;
            }
            if (!name.startsWith("com/bdmajora/") || name.startsWith("com/bdmajora/testing/")) {
                return null;
            }
            if (modClass) {
                return pointStaticsAtTheirMembers(bytes);
            }
            ClassReader reader = new ClassReader(bytes);
            if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                return null;
            }
            String target = target(reader);
            if (target == null || target.equals(reader.getSuperName())) {
                return null;
            }
            Class<?> targetClass = Class.forName(target.replace('/', '.'), false, loader);
            if (targetClass.isInterface() || Modifier.isFinal(targetClass.getModifiers())) {
                return null;
            }
            // Loads the declared superclass.
            Class<?> declared = Class.forName(reader.getSuperName().replace('/', '.'), false, loader);
            if (!declared.isAssignableFrom(targetClass)) {
                return null;
            }
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new Reparent(writer, reader.getSuperName(), target, placeholders(bytes)), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            // A mixin whose target cannot be resolved here is left exactly as it was
            return null;
        }
    }

    // Rewrites calls to generated static accessors.
    private byte[] pointStaticsAtTheirMembers(byte[] bytes) {
        if (statics.isEmpty()) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, method, descriptor, signature, exceptions);
                return visitor == null ? null : new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String called, String name, String desc, boolean isInterface) {
                        if (!rewriteStatic(this.mv, opcode, called, name, desc)) {
                            super.visitMethodInsn(opcode, called, name, desc, isInterface);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    // Returns the rewritten class or the given bytes.
    private byte[] pointedOr(byte[] bytes) {
        byte[] pointed = pointStaticsAtTheirMembers(bytes);
        return pointed == null ? bytes : pointed;
    }

    // Turns an accessor call into the field read or write or call it stands for.
    private boolean rewriteStatic(MethodVisitor visitor, int opcode, String owner, String name, String descriptor) {
        Static accessor = opcode == Opcodes.INVOKESTATIC ? statics.get(owner + "." + name + descriptor) : null;
        if (accessor == null) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(descriptor);
        Type returned = Type.getReturnType(descriptor);
        if (!accessor.field()) {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, accessor.target(), accessor.member(), descriptor, false);
        } else if (args.length == 0) {
            visitor.visitFieldInsn(Opcodes.GETSTATIC, accessor.target(), accessor.member(), returned.getDescriptor());
        } else {
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, accessor.target(), accessor.member(), args[0].getDescriptor());
        }
        return true;
    }

    // Drops final from the static fields of a mixin target.
    private static byte[] openStatics(byte[] bytes, Set<String> members) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            // Drops final from the class.
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, unfinalClass(access), name, signature, superName, interfaces);
            }

            @Override
            public net.bytebuddy.jar.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(members.contains(name) ? publish(unfinal(access)) : unfinal(access),
                        name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return super.visitMethod(members.contains(name) ? publish(access) : access,
                        name, descriptor, signature, exceptions);
            }
        }, 0);
        return writer.toByteArray();
    }

    // Returns the access made public.
    private static int publish(int access) {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }

    // Returns the class access without final.
    private static int unfinalClass(int access) {
        return access & ~Opcodes.ACC_FINAL;
    }

    // Returns the access of a static final field without final.
    private static int unfinal(int access) {
        boolean isStaticFinal = (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0;
        return isStaticFinal ? access & ~Opcodes.ACC_FINAL : access;
    }

    // Makes a nested class public in its InnerClasses entry.
    private static byte[] publishClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        String self = reader.getClassName();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access | Opcodes.ACC_PUBLIC, name, signature, superName, interfaces);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                super.visitInnerClass(name, outerName, innerName, self.equals(name) ? publish(access) : access);
            }
        }, 0);
        return writer.toByteArray();
    }

    // Replaces <clinit> with a bare return
    private static byte[] withoutStaticInitialiser(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!"<clinit>".equals(name)) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                visitor.visitCode();
                visitor.visitInsn(Opcodes.RETURN);
                visitor.visitMaxs(0, 0);
                visitor.visitEnd();
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }

    // Opens a private no argument constructor.
    private static byte[] widenConstructor(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean constructor = "<init>".equals(name) && "()V".equals(descriptor);
                return super.visitMethod(constructor ? access & ~Opcodes.ACC_PRIVATE | Opcodes.ACC_PUBLIC : access,
                        name, descriptor, signature, exceptions);
            }
        }, 0);
        return writer.toByteArray();
    }

    // Adds public constructors calling the superclass.
    private static byte[] addConstructor(byte[] bytes, boolean noArgs, Set<String> extra, boolean openStatics) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        String[] names = new String[2];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public net.bytebuddy.jar.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(openStatics ? unfinal(access) : access, name, descriptor, signature, value);
            }

            // Drops final from the class.
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                names[0] = name;
                names[1] = superName;
                super.visit(version, openStatics ? unfinalClass(access) : access, name, signature, superName, interfaces);
            }

            @Override
            public void visitEnd() {
                if (noArgs) {
                    MethodVisitor visitor = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "<init>", "()V", null, null);
                    visitor.visitCode();
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    // An enum's only superclass constructor takes a name and an ordinal
                    boolean isEnum = "java/lang/Enum".equals(names[1]);
                    if (isEnum) {
                        visitor.visitInsn(Opcodes.ACONST_NULL);
                        visitor.visitInsn(Opcodes.ICONST_M1);
                    }
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, names[1], "<init>", isEnum ? "(Ljava/lang/String;I)V" : "()V", false);
                    visitor.visitInsn(Opcodes.RETURN);
                    visitor.visitMaxs(0, 0);
                    visitor.visitEnd();
                }
                // Each extra shape ignores its arguments and chains to the no-argument one
                for (String descriptor : extra) {
                    MethodVisitor visitor = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "<init>", descriptor, null, null);
                    visitor.visitCode();
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, names[0], "<init>", "()V", false);
                    visitor.visitInsn(Opcodes.RETURN);
                    visitor.visitMaxs(0, 0);
                    visitor.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);
        return writer.toByteArray();
    }

    // Returns the single class named by Mixin or null.
    private static String target(ClassReader reader) {
        String[] found = new String[2];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String unused, Object value) {
                                found[1] = found[0] == null ? null : "many";
                                if (found[0] == null && value instanceof Type) {
                                    found[0] = ((Type) value).getInternalName();
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[1] == null ? found[0] : null;
    }

    // One shadow method placeholder and its bridge.
    private record Placeholder(String bridge, boolean isStatic) {}

    // Returns the placeholder bodies of the class.
    private static Map<String, Placeholder> placeholders(byte[] bytes) {
        Map<String, Placeholder> found = new HashMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(annotation)) {
                            found.put(name + descriptor,
                                    new Placeholder("stub$" + name + found.size(), (access & Opcodes.ACC_STATIC) != 0));
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found;
    }

    private final class Reparent extends ClassVisitor {
        private final String from;
        private final String to;
        private final Map<String, Placeholder> placeholders;
        private String owner;
        private boolean generic;

        Reparent(ClassVisitor next, String from, String to, Map<String, Placeholder> placeholders) {
            super(Opcodes.ASM9, next);
            this.from = from;
            this.to = to;
            this.placeholders = placeholders;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.owner = name;
            this.generic = signature != null && signature.startsWith("<");
            super.visit(version, access, name, null, to, interfaces);
        }

        // Drops the old superclass's signatures.
        @Override
        public net.bytebuddy.jar.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return super.visitField(access, name, descriptor, generic ? null : signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // Opens a private mixin constructor.
            boolean hidden = "<init>".equals(name) && (access & Opcodes.ACC_PRIVATE) != 0;
            int opened = hidden ? access & ~Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED : access;
            MethodVisitor visitor = super.visitMethod(opened, name, descriptor, generic ? null : signature, exceptions);
            return visitor == null ? null : new MethodVisitor(Opcodes.ASM9, visitor) {
                // Objects of the old superclass's type built here.
                private int pendingNew;

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.NEW && type.equals(from)) {
                        this.pendingNew++;
                    }
                    super.visitTypeInsn(opcode, type);
                }

                @Override
                public void visitMethodInsn(int opcode, String called, String method, String desc, boolean isInterface) {
                    if (rewriteStatic(this.mv, opcode, called, method, desc)) {
                        return;
                    }
                    if (opcode == Opcodes.INVOKESPECIAL && called.equals(from) && "<init>".equals(method) && this.pendingNew > 0) {
                        this.pendingNew--;
                        super.visitMethodInsn(opcode, called, method, desc, isInterface);
                        return;
                    }
                    // Redirects placeholder calls to the stub table.
                    Placeholder placeholder = called.equals(owner) ? placeholders.get(method + desc) : null;
                    if (placeholder != null) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, placeholder.bridge(),
                                placeholder.isStatic() ? desc : "(L" + owner + ";" + desc.substring(1), false);
                        return;
                    }
                    boolean superCall = opcode == Opcodes.INVOKESPECIAL && called.equals(from);
                    super.visitMethodInsn(opcode, superCall ? to : called, method, desc, isInterface);
                }
            };
        }

        // Adds one static bridge per placeholder.
        @Override
        public void visitEnd() {
            for (Map.Entry<String, Placeholder> entry : placeholders.entrySet()) {
                String signature = entry.getKey();
                String name = signature.substring(0, signature.indexOf('('));
                String descriptor = signature.substring(signature.indexOf('('));
                Placeholder placeholder = entry.getValue();
                bridge(placeholder.bridge(), name,
                        placeholder.isStatic() ? descriptor : "(L" + owner + ";" + descriptor.substring(1),
                        placeholder.isStatic());
            }
            super.visitEnd();
        }

        private void bridge(String bridgeName, String name, String descriptor, boolean isStatic) {
            Type[] args = Type.getArgumentTypes(descriptor);
            MethodVisitor visitor = cv.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    bridgeName, descriptor, null, null);
            visitor.visitCode();
            int first = isStatic ? 0 : 1;
            if (isStatic) {
                visitor.visitInsn(Opcodes.ACONST_NULL);
            } else {
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
            }
            visitor.visitLdcInsn(name);
            visitor.visitIntInsn(Opcodes.BIPUSH, args.length - first);
            visitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            int slot = first;
            for (int i = first; i < args.length; i++) {
                visitor.visitInsn(Opcodes.DUP);
                visitor.visitIntInsn(Opcodes.BIPUSH, i - first);
                visitor.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
                box(visitor, args[i]);
                visitor.visitInsn(Opcodes.AASTORE);
                slot += args[i].getSize();
            }
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/bdmajora/testing/ShadowStubs", "call",
                    "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            Type returned = Type.getReturnType(descriptor);
            switch (returned.getSort()) {
                case Type.VOID -> {
                    visitor.visitInsn(Opcodes.POP);
                    visitor.visitInsn(Opcodes.RETURN);
                }
                case Type.OBJECT, Type.ARRAY -> {
                    visitor.visitTypeInsn(Opcodes.CHECKCAST, returned.getInternalName());
                    visitor.visitInsn(Opcodes.ARETURN);
                }
                default -> {
                    String helper = switch (returned.getSort()) {
                        case Type.BOOLEAN -> "asBoolean";
                        case Type.BYTE -> "asByte";
                        case Type.SHORT -> "asShort";
                        case Type.CHAR -> "asChar";
                        case Type.INT -> "asInt";
                        case Type.LONG -> "asLong";
                        case Type.FLOAT -> "asFloat";
                        default -> "asDouble";
                    };
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/bdmajora/testing/ShadowStubs", helper,
                            "(Ljava/lang/Object;)" + returned.getDescriptor(), false);
                    visitor.visitInsn(returned.getOpcode(Opcodes.IRETURN));
                }
            }
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }

        private void box(MethodVisitor visitor, Type type) {
            String owner = switch (type.getSort()) {
                case Type.BOOLEAN -> "java/lang/Boolean";
                case Type.BYTE -> "java/lang/Byte";
                case Type.SHORT -> "java/lang/Short";
                case Type.CHAR -> "java/lang/Character";
                case Type.INT -> "java/lang/Integer";
                case Type.LONG -> "java/lang/Long";
                case Type.FLOAT -> "java/lang/Float";
                case Type.DOUBLE -> "java/lang/Double";
                default -> null;
            };
            if (owner != null) {
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf",
                        "(" + type.getDescriptor() + ")L" + owner + ";", false);
            }
        }
    }
}
