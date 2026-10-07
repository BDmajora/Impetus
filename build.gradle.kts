import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// Impetus for Cleanroom: Java 25 and LWJGL3 first. Unimined sets up Cleanroom's patched 1.12.2 in MCP
// names, the engine (:common) is shaded in, and the result is remapped to SRG with its mixin selectors
// written straight into the annotations, so no refmap is involved at runtime.
plugins {
    java
    `maven-publish`
    jacoco
    alias(libs.plugins.shadow)
    alias(libs.plugins.unimined)
}

val modId = providers.gradleProperty("mod_id").get()
group = "com.bdmajora"
version = providers.gradleProperty("mod_version").get()
base.archivesName = modId

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    withSourcesJar()
}

// SRG-named mod jars Impetus has compat for; Unimined remaps them to MCP for compilation
val modCompileOnly = configurations.create("modCompileOnly")
val modRuntimeOnly = configurations.create("modRuntimeOnly")
configurations.compileOnly { extendsFrom(modCompileOnly) }
configurations.runtimeOnly { extendsFrom(modRuntimeOnly) }
// Unit tests exercise the compat code directly, so the compileOnly mod APIs must be loadable there
configurations.testImplementation { extendsFrom(modCompileOnly) }

// The engine module, shaded into the mod jar
val engine = configurations.create("engine") { isTransitive = false }
// Cleanroom's LWJGL2 bridge, for the few vanilla signatures that still carry LWJGL2 types (see lwjglInterop)
val lwjglx = configurations.create("lwjglx") { isTransitive = false }

// Only the LWJGL2 types vanilla still exposes (input events, key codes, util.vector) and Display, which owns
// Cleanroom's GLFW window, are visible at compile time; the rest of the bridge would shadow LWJGL3's own
// org.lwjgl.opengl classes, which are the API Impetus uses (impl.platform.GameWindow is the only Display user)
val lwjglInteropTypes = listOf(
    "org/lwjgl/input/**", "org/lwjgl/util/vector/**", "org/lwjgl/LWJGLException.class",
    "org/lwjgl/opengl/Display*.class"
)
val lwjglInterop = tasks.register<Jar>("lwjglInterop") {
    archiveFileName = "lwjglx-interop.jar"
    destinationDirectory = layout.buildDirectory.dir("lwjglx")
    // Gradle does not see pattern edits inside a lazily sourced child spec, so the list is a declared input
    inputs.property("types", lwjglInteropTypes)
    from({ lwjglx.map { zipTree(it) } }) {
        include(lwjglInteropTypes)
    }
}

repositories {
    exclusiveContent {
        forRepository { maven("https://cursemaven.com") }
        filter { includeGroup("curse.maven") }
    }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    mavenCentral()
}

unimined.minecraft {
    version(libs.versions.minecraft.get())

    mappings {
        mcp(libs.versions.mcp.channel.get(), libs.versions.mcp.mappings.get())
    }

    cleanroom {
        loader(libs.versions.cleanroom.get())
        accessTransformer(file("src/main/resources/META-INF/impetus_at.cfg"))
    }

    runs.all {
        args("--username", "Developer")
        systemProperty("fml.coreMods.load", "com.bdmajora.impetus.core.ImpetusLoadingPlugin")
    }

    // The shaded jar is what gets remapped, not the plain one
    defaultRemapJar = false
    remap(tasks.named<ShadowJar>("shadowJar").get()) {
        mixinRemap {
            enableBaseMixin()
            enableMixinExtra()
            disableRefmap()
        }
    }

    mods {
        remap(modCompileOnly)
        remap(modRuntimeOnly)
    }
}

dependencies {
    implementation(project(":common"))
    engine(project(":common"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    lwjglx(libs.lwjglx)
    compileOnly(files(lwjglInterop))

    "modRuntimeOnly"("curse.maven:ae2-223794:2747063")
    modCompileOnly("maven.modrinth:fluidlogged-api:3.0.6")
    // Distant Horizons API, for the umbra DH compat (compat.dh); only loaded when DH is present
    modCompileOnly("maven.modrinth:distanthorizonsapi:7.0.0")
    // LittleTiles 1.5.87 and the CreativeCore 1.10.71 it extends, for the terrain compat (compat.littletiles); only loaded when LittleTiles is present
    modCompileOnly("curse.maven:littletiles-257818:5180387")
    modCompileOnly("curse.maven:creativecore-257814:4722163")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito)
    // The launcher and ByteBuddy are compiled against: Splice installs its agent from a launcher session listener
    testImplementation(libs.junit.launcher)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.versions.java.get().toInt()
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-parameters"))
}

// Tests run against LWJGL3 with Cleanroom's lwjglx bridge merged in and the GL natives stubbed (gradle/test-lwjgl.gradle.kts)
val mainRuntime = sourceSets.main.get().runtimeClasspath
extra["testLwjgl.lwjgl3"] = mainRuntime.filter { it.name.matches(Regex("lwjgl(-[a-z]+)?-3[0-9.]+\\.jar")) }
extra["testLwjgl.bridge"] = mainRuntime.filter { it.name.startsWith("lwjglxx-") }
apply(from = rootProject.file("gradle/test-lwjgl.gradle.kts"))
val prepareTestLwjgl = tasks.named("prepareTestLwjgl")

// The GL test mock lives in common so both modules' tests share one copy. Unimined wires Minecraft, Cleanroom and its
// libraries into main only, so tests take main's classpaths wholesale, with the test LWJGL ahead of the plain LWJGL3
// jars (test classes, and so the Display and Sys stand-ins, still come first)
sourceSets.test {
    java.srcDir("common/src/testShared/java")
    resources.srcDir("common/src/testShared/resources")
    compileClasspath = files(prepareTestLwjgl) + compileClasspath + sourceSets.main.get().compileClasspath
    runtimeClasspath = output + files(prepareTestLwjgl) + runtimeClasspath + mainRuntime
}

tasks.test {
    useJUnitPlatform()
    // run.sh test reports failures itself, and the coverage report must still be produced when tests fail
    ignoreFailures = true
    maxHeapSize = "2G"
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
    systemProperty("impetus.lwjgl.service", "com.bdmajora.testing.TestGl")
    // LWJGL3's GL classes load their JNI glue when a test mocks them; explicit init keeps them from also opening the system GL driver
    systemProperty("org.lwjgl.opengl.explicitInit", "true")
    // Compatibility warnings pop Swing dialogs; keep the run headless and silent
    systemProperty("java.awt.headless", "true")
    systemProperty("impetus.hideMessageBoxes", "true")
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// run.sh test reads the XML; this task is the gate for CI-style runs
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit { counter = "METHOD"; value = "COVEREDRATIO"; minimum = "1.0".toBigDecimal() }
            limit { counter = "CLASS"; value = "COVEREDRATIO"; minimum = "1.0".toBigDecimal() }
        }
    }
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("mcmod.info") {
        expand(mapOf("version" to version))
    }
}

tasks.jar {
    archiveClassifier = "dev-slim"
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "dev"
    configurations = listOf(engine)
    // Cleanroom ships JOML, fastutil, gson and ASM itself; only Impetus and its engine go in the jar
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
    // Service files must reach the merging transformer as duplicates instead of being dropped by the EXCLUDE strategy
    mergeServiceFiles()
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    from("LICENSE", "README.md")
    manifest {
        attributes(
            "FMLAT" to "impetus_at.cfg",
            "FMLCorePlugin" to "com.bdmajora.impetus.core.ImpetusLoadingPlugin",
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true"
        )
    }
}

// The remapped jar is the release; copy it to a stable, versioned location
val remapShadowJar = tasks.named<Jar>("remapShadowJar")
tasks.register<Copy>("packageJar") {
    group = "build"
    description = "Builds the release jar into build/libs/<version>/"
    from(remapShadowJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs/$version"))
    rename { "$modId-$version.jar" }
}
tasks.assemble { dependsOn(remapShadowJar) }

publishing {
    publications {
        create<MavenPublication>("mod") {
            artifactId = base.archivesName.get()
            artifact(remapShadowJar)
            artifact(tasks.named("sourcesJar"))
        }
    }
}
