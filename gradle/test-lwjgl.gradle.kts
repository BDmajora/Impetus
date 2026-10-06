import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

// Shared by the root project and :common, applied with apply(from = ...). Registers prepareTestLwjgl, which writes
// build/lwjglx/lwjgl-tests.jar from the files in extra["testLwjgl.lwjgl3"] and, when set, extra["testLwjgl.bridge"]
buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm-commons:9.10.1") }
}

// The LWJGL that tests run against. First, where a bridge is given, Cleanroom's LWJGLTransformer replayed once over the jars instead of at
// every class load: each lwjglxx class (LWJGL2 signatures under org.lwjglx) has its missing members merged into the
// LWJGL3 class of the same name, or is renamed in when LWJGL3 has none, since that merged org.lwjgl is what vanilla's
// bytecode links against in game and tests have no LaunchClassLoader to build it. Second, every JNI method in
// org.lwjgl.opengl becomes a plain one throwing IllegalStateException: with no context a real call jumps through a
// null function pointer and takes the JVM down, where LWJGL2 threw; and a plain method is one Mockito can stub
abstract class PrepareTestLwjgl : DefaultTask() {
    @get:Classpath
    abstract val lwjgl3: ConfigurableFileCollection

    @get:Classpath
    abstract val bridge: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun prepare() {
        val lwjgl3Classes = LinkedHashMap<String, ByteArray>()
        lwjgl3.files.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }
                    .forEach { lwjgl3Classes.putIfAbsent(it.name, zip.getInputStream(it).readBytes()) }
            }
        }
        val remapper = object : Remapper() {
            override fun map(internalName: String): String =
                if (internalName.startsWith("org/lwjglx/")) "org/lwjgl/" + internalName.substring(11) else internalName
        }
        val bridged = LinkedHashMap<String, ByteArray>()
        bridge.files.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.startsWith("org/lwjglx/") && it.name.endsWith(".class") }.forEach { entry ->
                    val writer = ClassWriter(0)
                    ClassReader(zip.getInputStream(entry).readBytes()).accept(ClassRemapper(writer, remapper), 0)
                    bridged["org/lwjgl/" + entry.name.substring(11)] = writer.toByteArray()
                }
            }
        }
        val names = LinkedHashSet<String>()
        names += bridged.keys
        names += lwjgl3Classes.keys.filter { it.startsWith("org/lwjgl/opengl/") }
        JarOutputStream(output.get().asFile.outputStream()).use { out ->
            for (name in names) {
                val original = lwjgl3Classes[name]
                val extra = bridged[name]
                val node = ClassNode().also { ClassReader(original ?: extra).accept(it, 0) }
                if (original != null && extra != null) {
                    merge(node, ClassNode().also { ClassReader(extra).accept(it, 0) }, name)
                }
                if (name.startsWith("org/lwjgl/opengl/")) {
                    stubNatives(node)
                }
                out.putNextEntry(JarEntry(name))
                out.write(ClassWriter(0).also { node.accept(it) }.toByteArray())
                out.closeEntry()
            }
        }
    }

    // Members LWJGL3 lacks are copied across with their own frames; AL.destroy is the bridge's, as in Cleanroom
    private fun merge(node: ClassNode, extra: ClassNode, name: String) {
        val methods = node.methods.map { it.name + it.desc }.toSet()
        extra.methods.filter { it.name + it.desc !in methods }.forEach { node.methods.add(it) }
        val fields = node.fields.map { it.name + it.desc }.toSet()
        extra.fields.filter { it.name + it.desc !in fields }.forEach { node.fields.add(it) }
        if (name == "org/lwjgl/openal/AL.class") {
            node.methods.removeIf { it.name == "destroy" }
            extra.methods.filter { it.name == "destroy" }.forEach { node.methods.add(it) }
        }
    }

    private fun stubNatives(node: ClassNode) {
        for (method in node.methods.filter { it.access and Opcodes.ACC_NATIVE != 0 }) {
            method.access = method.access and Opcodes.ACC_NATIVE.inv()
            method.instructions = InsnList().apply {
                add(TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"))
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode("No OpenGL context in tests: " + node.name.substringAfterLast('/') + "." + method.name))
                add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false))
                add(InsnNode(Opcodes.ATHROW))
            }
            method.maxStack = 3
            method.maxLocals = (Type.getArgumentsAndReturnSizes(method.desc) shr 2) - (if (method.access and Opcodes.ACC_STATIC != 0) 1 else 0)
        }
    }
}

tasks.register<PrepareTestLwjgl>("prepareTestLwjgl") {
    lwjgl3.from(project.extra["testLwjgl.lwjgl3"]!!)
    if (project.extra.has("testLwjgl.bridge")) {
        bridge.from(project.extra["testLwjgl.bridge"]!!)
    }
    output = layout.buildDirectory.file("lwjglx/lwjgl-tests.jar")
}
