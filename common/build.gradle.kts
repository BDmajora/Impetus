// The engine: GL pipeline, chunk renderer and shader core with no Minecraft dependency, on Java 25 and LWJGL3.
// GL calls go through the one LWJGLService instance (the seam unit tests mock); memory and stacks are LWJGL's own
plugins {
    `java-library`
    `maven-publish`
    jacoco
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

// The test GL mock and fixtures are shared with the root project's tests through src/testShared
sourceSets.test {
    java.srcDir("src/testShared/java")
    resources.srcDir("src/testShared/resources")
}

// LWJGL's natives load for MemoryUtil and when a test mocks a GL class, so tests need this platform's
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

dependencies {
    provided(platform(libs.lwjgl.bom))
    provided(libs.lwjgl.core)
    provided(libs.lwjgl.opengl)
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
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(variantOf(libs.lwjgl.core) { classifier(lwjglNatives) })
    testRuntimeOnly(variantOf(libs.lwjgl.opengl) { classifier(lwjglNatives) })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.versions.java.get().toInt()
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-parameters"))
}

// Tests run against the same stubbed-native org.lwjgl.opengl the root project's do (gradle/test-lwjgl.gradle.kts), ahead of the real jar
extra["testLwjgl.lwjgl3"] = configurations["testRuntimeClasspath"].filter { it.name.matches(Regex("lwjgl(-[a-z]+)?-3[0-9.]+\\.jar")) }
apply(from = rootProject.file("gradle/test-lwjgl.gradle.kts"))
sourceSets.test { runtimeClasspath = output + files(tasks.named("prepareTestLwjgl")) + runtimeClasspath }

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

tasks.test {
    systemProperty("impetus.lwjgl.service", "com.bdmajora.testing.TestGl")
    // GL's class init would otherwise open the system GL driver
    systemProperty("org.lwjgl.opengl.explicitInit", "true")
    systemProperty("impetus.hideMessageBoxes", "true")
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

tasks.jar {
    from(rootProject.file("LICENSE"))
}

publishing {
    publications {
        create<MavenPublication>("engine") {
            artifactId = "impetus-engine"
            from(components["java"])
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
