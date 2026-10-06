import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

buildscript {
    repositories { mavenCentral() }
    // For verifyLwjglNeutral, which reads the engine's bytecode
    dependencies { classpath("org.ow2.asm:asm:9.10.1") }
}

// The engine: GL pipeline, chunk renderer and shader core with no Minecraft dependency. It is written
// for Java 25 and LWJGL3; everything GL goes through LWJGLService so the LWJGL2 backend stays a working
// translation layer, and the optional legacy jar re-targets the whole module at Java 8.
plugins {
    `java-library`
    `maven-publish`
    jacoco
    alias(libs.plugins.jvmdowngrader)
}

group = "com.bdmajora.impetus"
version = rootProject.version

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    withSourcesJar()
}

repositories {
    mavenCentral()
}

// Libraries Cleanroom ships with the game: compiled against at the shipped versions, never bundled
val provided = configurations.create("provided")
configurations.compileOnly { extendsFrom(provided) }
configurations.testImplementation { extendsFrom(provided) }

sourceSets {
    // The LWJGLService contract and its provider; no LWJGL types at all
    val lwjglCommon = create("lwjglCommon")
    // First-class backend, selected whenever org.lwjgl.opengl.GL11C resolves (Cleanroom, any LWJGL3 runtime)
    val lwjgl3 = create("lwjgl3") {
        compileClasspath += lwjglCommon.output
    }
    // Translation layer for LWJGL2 runtimes, selected only when LWJGL3 is absent
    val lwjgl2 = create("lwjgl2") {
        compileClasspath += lwjglCommon.output
    }
    main {
        compileClasspath += lwjglCommon.output
    }
    // The test backend and LWJGL2 stand-ins are shared with the root project's tests through src/testShared
    test {
        java.srcDir("src/testShared/java")
        resources.srcDir("src/testShared/resources")
        compileClasspath += lwjglCommon.output + lwjgl2.output
        runtimeClasspath += lwjglCommon.output + lwjgl2.output
    }
    // The LWJGL3 backend's own tests: its org.lwjgl.opengl clashes with the LWJGL2 stand-ins above, so they get a classpath of their own
    create("lwjgl3Test") {
        compileClasspath += lwjglCommon.output + lwjgl3.output
        runtimeClasspath += lwjglCommon.output + lwjgl3.output
    }
}

listOf("lwjglCommon", "lwjgl2", "lwjgl3").forEach { configurations.named("${it}CompileOnly") { extendsFrom(provided) } }
configurations.named("lwjgl3TestImplementation") { extendsFrom(provided) }

// GL's class init loads the LWJGL natives even with every GL entry point stubbed, so the LWJGL3 tests need this platform's
val lwjglNatives = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in listOf("aarch64", "arm64")
    // "darwin" contains "win", so macOS is matched first
    when {
        os.contains("mac") || os.contains("darwin") -> if (arm) "natives-macos-arm64" else "natives-macos"
        os.contains("win") -> if (arm) "natives-windows-arm64" else "natives-windows"
        else -> if (arm) "natives-linux-arm64" else "natives-linux"
    }
}

// Separate handle on the plain LWJGL2 jar so tests can load an unsealed copy of it
val lwjgl2Unsealed = configurations.create("lwjgl2Unsealed") { isTransitive = false }

dependencies {
    // GL constants only: javac inlines them, so the compiled engine holds no org.lwjgl reference (verifyLwjglNeutral)
    compileOnly(platform(libs.lwjgl.bom))
    compileOnly(libs.lwjgl.opengl)

    "lwjgl3CompileOnly"(platform(libs.lwjgl.bom))
    "lwjgl3CompileOnly"(libs.lwjgl.core)
    "lwjgl3CompileOnly"(libs.lwjgl.opengl)

    "lwjgl2CompileOnly"(libs.lwjgl2)
    lwjgl2Unsealed(libs.lwjgl2)

    provided(libs.joml)
    provided(libs.fastutil)
    provided(libs.gson)
    provided(libs.log4j.api)
    provided(libs.asm)
    provided(libs.asm.tree)
    provided(libs.asm.util)
    provided(libs.jetbrains.annotations)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    testCompileOnly(libs.lwjgl2)
    testRuntimeOnly(libs.junit.launcher)
    // Engine sources name GL constants through org.lwjgl.opengl; tests compile them the same way
    testCompileOnly(platform(libs.lwjgl.bom))
    testCompileOnly(libs.lwjgl.opengl)

    "lwjgl3TestImplementation"(platform(libs.lwjgl.bom))
    "lwjgl3TestImplementation"(libs.lwjgl.core)
    "lwjgl3TestImplementation"(libs.lwjgl.opengl)
    "lwjgl3TestRuntimeOnly"(variantOf(libs.lwjgl.core) { classifier(lwjglNatives) })
    "lwjgl3TestRuntimeOnly"(variantOf(libs.lwjgl.opengl) { classifier(lwjglNatives) })
    "lwjgl3TestImplementation"(platform(libs.junit.bom))
    "lwjgl3TestImplementation"(libs.junit.jupiter)
    "lwjgl3TestImplementation"(libs.mockito)
    "lwjgl3TestRuntimeOnly"(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.versions.java.get().toInt()
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-parameters"))
}

// LWJGL2's manifest seals org.lwjgl.opengl, which would reject the Display and GLContext stand-ins; tests load an unsealed copy instead
val unsealLwjgl = tasks.register<Jar>("unsealLwjgl") {
    archiveFileName = "lwjgl2-unsealed.jar"
    destinationDirectory = layout.buildDirectory.dir("unsealed")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ lwjgl2Unsealed.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
sourceSets.test { runtimeClasspath += files(unsealLwjgl) }

// The LWJGL3 tests run against the same stubbed-native org.lwjgl.opengl the root project's do (gradle/test-lwjgl.gradle.kts),
// ahead of the real jar; no lwjglx bridge here, since nothing in the backend links against LWJGL2 signatures
extra["testLwjgl.lwjgl3"] = configurations["lwjgl3TestRuntimeClasspath"].filter { it.name.matches(Regex("lwjgl(-[a-z]+)?-3[0-9.]+\\.jar")) }
apply(from = rootProject.file("gradle/test-lwjgl.gradle.kts"))
val lwjgl3TestSources = sourceSets["lwjgl3Test"]
lwjgl3TestSources.runtimeClasspath = lwjgl3TestSources.output + files(tasks.named("prepareTestLwjgl")) + lwjgl3TestSources.runtimeClasspath

// The engine may only reach LWJGL through LWJGLService; one direct org.lwjgl link in main and the LWJGL2 backend
// breaks. Only what the JVM would actually link counts: javac also leaves a CONSTANT_Class entry for the class of
// every constant it inlined (for jdeps), and since no instruction uses those they are never resolved
val verifyLwjglNeutral = tasks.register("verifyLwjglNeutral") {
    group = "verification"
    description = "Fails when the engine's main classes link against org.lwjgl directly instead of through LWJGLService"
    val classes = sourceSets.main.get().output.classesDirs
    inputs.files(classes)
    doLast {
        val offenders = classes.asFileTree.matching { include("**/*.class") }.filter { file -> linksLwjgl(file.readBytes()) }
        if (!offenders.isEmpty) {
            throw GradleException("Engine classes link against org.lwjgl directly (route them through LWJGLService):\n" +
                    offenders.joinToString("\n") { "  " + it.relativeTo(projectDir) })
        }
    }
}

fun linksLwjgl(bytes: ByteArray): Boolean {
    var hit = false
    fun check(name: String?) {
        if (name != null && name.contains("org/lwjgl/")) hit = true
    }
    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
            check(superName)
            interfaces?.forEach(::check)
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            check(descriptor)
            return null
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor {
            check(descriptor)
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    check(owner)
                    check(descriptor)
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    check(owner)
                    check(descriptor)
                }

                override fun visitTypeInsn(opcode: Int, type: String) = check(type)

                override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = check(descriptor)

                override fun visitLdcInsn(value: Any?) {
                    if (value is Type) check(value.descriptor)
                }

                override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrap: Handle, vararg arguments: Any?) {
                    check(descriptor)
                    arguments.forEach { if (it is Handle) check(it.owner) }
                }
            }
        }
    }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return hit
}
tasks.check { dependsOn(verifyLwjglNeutral) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // run.sh test reports failures itself, and the coverage report must still be produced when tests fail
    ignoreFailures = true
    maxHeapSize = "1G"
    // Config code writes relative "config/" paths, so tests run from a scratch directory under build
    workingDir = layout.buildDirectory.dir("test-work").get().asFile
    doFirst { workingDir.mkdirs() }
    // Mockito's inline mock maker self-attaches a ByteBuddy agent; JDK 25 also wants native and Unsafe access granted up front
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
    // Compatibility warnings pop Swing dialogs; keep the run headless and silent
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val lwjgl3Test = tasks.register<Test>("lwjgl3Test") {
    group = "verification"
    description = "Runs the LWJGL3 backend's tests against LWJGL3 with its GL natives stubbed"
    testClassesDirs = lwjgl3TestSources.output.classesDirs
    classpath = lwjgl3TestSources.runtimeClasspath
    // GL's class init would otherwise open the system GL driver
    systemProperty("org.lwjgl.opengl.explicitInit", "true")
}

tasks.test {
    dependsOn(lwjgl3Test)
    systemProperty("impetus.lwjgl.service", "com.bdmajora.testing.TestGl")
    systemProperty("impetus.hideMessageBoxes", "true")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coveredSourceSets = listOf(sourceSets.main.get(), sourceSets["lwjglCommon"], sourceSets["lwjgl2"], sourceSets["lwjgl3"])

tasks.jacocoTestReport {
    dependsOn(tasks.test, lwjgl3Test)
    executionData(tasks.test.get(), lwjgl3Test.get())
    sourceSets(*coveredSourceSets.toTypedArray())
    reports {
        xml.required = true
        html.required = true
    }
}

// run.sh test reads the XML; this task is the gate for CI-style runs
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, lwjgl3Test)
    executionData(tasks.test.get(), lwjgl3Test.get())
    sourceSets(*coveredSourceSets.toTypedArray())
    violationRules {
        rule {
            limit { counter = "METHOD"; value = "COVEREDRATIO"; minimum = "1.0".toBigDecimal() }
            limit { counter = "CLASS"; value = "COVEREDRATIO"; minimum = "1.0".toBigDecimal() }
        }
    }
}

// Projects depending on :common compile against its class directories, so the extra source sets must be published there too
val extraSourceSets = listOf("lwjglCommon", "lwjgl2", "lwjgl3")
listOf("apiElements", "runtimeElements").forEach { elements ->
    configurations.named(elements) {
        outgoing.variants.named("classes") {
            extraSourceSets.forEach { set ->
                artifact(sourceSets[set].java.destinationDirectory) { builtBy(tasks.named(sourceSets[set].compileJavaTaskName)) }
            }
        }
    }
}

tasks.jar {
    from(rootProject.file("LICENSE"))
    from(sourceSets["lwjglCommon"].output)
    from(sourceSets["lwjgl2"].output)
    from(sourceSets["lwjgl3"].output)
}

tasks.named<Jar>("sourcesJar") {
    from(sourceSets["lwjglCommon"].allSource)
    from(sourceSets["lwjgl2"].allSource)
    from(sourceSets["lwjgl3"].allSource)
}

// Java 8 / LWJGL2 compatibility artifact for the legacy Forge line: the same engine, downgraded by JVM
// Downgrader with its Java 9+ API stubs shaded under the engine's own package so nothing leaks
val legacyDowngrade = tasks.register<DowngradeJar>("legacyDowngrade") {
    inputFile = tasks.jar.flatMap { it.archiveFile }
    downgradeTo = JavaVersion.VERSION_1_8
    archiveClassifier = "legacy-downgraded"
    classpath = sourceSets.main.get().compileClasspath + configurations["lwjgl2CompileClasspath"] + configurations["lwjgl3CompileClasspath"]
}
val legacyJar = tasks.register<ShadeJar>("legacyJar") {
    group = "build"
    description = "Builds the engine as a Java 8 jar for LWJGL2/Forge runtimes"
    inputFile = legacyDowngrade.flatMap { it.archiveFile }
    shadePath = "com/bdmajora/impetus/engine/jvmdg/"
    archiveClassifier = "legacy"
}
if (providers.gradleProperty("build_legacy_engine").orNull.toBoolean()) {
    tasks.assemble { dependsOn(legacyJar) }
}

publishing {
    publications {
        create<MavenPublication>("engine") {
            artifactId = "impetus-engine"
            from(components["java"])
            artifact(legacyJar)
        }
    }
    repositories {
        maven {
            name = "taumcRepository"
            url = uri("https://maven.taumc.org/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
