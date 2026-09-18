import com.bdmajora.impetus.engine.gradle.mdg.remapper.ReobfuscateCodeAndMixinsTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gtnewhorizons.retrofuturagradle.mcp.ApplySourceAccessTransformersTask
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils

plugins {
    id("org.taumc.gradle.versioning")
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.8"
    id("com.gradleup.shadow") version "9.3.0"
    id("impetus-mdg-remapper")
    id("maven-publish")
}

group = "com.bdmajora"
version = tau.versioning.version(rootProject.properties["project_base_version"].toString(), rootProject.properties["release_channel"])

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

base.archivesName = "impetus-forge-mc12.2"

val modCompileOnly by configurations.creating
configurations.compileOnly.get().extendsFrom(modCompileOnly)
val modRuntimeOnly by configurations.creating
configurations.runtimeOnly.get().extendsFrom(modRuntimeOnly)

// CleanMix + MixinExtras, shaded into a jar that is nested inside the mod jar rather than unpacked
// into it. BooterBootstrap extracts it at runtime only when the install has no MixinBooter, so on
// installs that do have one Impetus contributes zero org.spongepowered.asm classes and the
// duplicate-class race that bundling normally causes cannot occur.
val booterLibs by configurations.creating

minecraft {
    mcVersion.set("1.12.2")
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.cleanroommc.com") }
        filter {
            includeGroup("zone.rong")
            includeGroup("com.cleanroommc")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://cursemaven.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    exclusiveContent {
        forRepository { maven("https://nexus.gtnewhorizons.com/repository/public/") }
        filter {
            includeGroupAndSubgroups("com.gtnewhorizons")
            includeGroup("com.github.GTNewHorizons")
        }
    }
    exclusiveContent {
        forRepository { maven("https://maven.taumc.org/releases") }
        filter {
            includeGroupAndSubgroups("org.taumc")
        }
    }
    mavenCentral()
}

configurations {
    named("shadow") {
        attributes {
            attribute(ModUtils.DEOBFUSCATOR_TRANSFORMED, true)
        }
    }
}

dependencies {
    val lombokVersion = rootProject.properties["lombok_version"].toString()
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    val jabelVersion = rootProject.properties["jabel_version"].toString()
    annotationProcessor("com.github.GTNewHorizons:jabel-javac-plugin:${jabelVersion}")
    compileOnly("com.github.GTNewHorizons:jabel-javac-plugin:${jabelVersion}")

    implementation(project(":common", configuration = "downgraded")) {
        isTransitive = false
    }
    shadow(project(":common", configuration = "downgraded")) {
        isTransitive = false
    }

    "shadow"("org.joml:joml:1.10.5")
    implementation("org.joml:joml:1.10.5")
    val cleanmixVersion = "0.7.2"
    val mixinExtrasVersion = "0.5.5"
    compileOnly("com.cleanroommc:cleanmix:${cleanmixVersion}")
    compileOnly("com.cleanroommc:mixinextras-common:${mixinExtrasVersion}")
    booterLibs("com.cleanroommc:cleanmix:${cleanmixVersion}")
    booterLibs("com.cleanroommc:mixinextras-common:${mixinExtrasVersion}")
    compileOnly("com.gtnewhorizons.retrofuturabootstrap:RetroFuturaBootstrap:1.0.11") {
        exclude(group = "org.apache.logging.log4j")
    }
    "modRuntimeOnly"("curse.maven:ae2-223794:2747063")
    modCompileOnly("maven.modrinth:fluidlogged-api:3.0.6")
    // Distant Horizons API, for the umbra DH compat (compat.dh); only loaded when DH is present
    modCompileOnly("maven.modrinth:distanthorizonsapi:7.0.0")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "21"
    options.release = 8
    // Targeting 8 is intentional (jabel lowers Java 21 syntax), so javac's "obsolete source/target"
    // notices are pure noise. This is the suppression javac itself recommends.
    options.compilerArgs.add("-Xlint:-options")
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named("reobfJar").configure {
    enabled = false
}

tasks.register<ReobfuscateCodeAndMixinsTask>("impetusRemapJar") {
    tsrgMappings = mcpTasks.srgFile("mcp-srg.srg")
    deobfMinecraftJar = mcpTasks.taskPackagePatchedMc.flatMap { it.archiveFile }
    classpath = sourceSets.main.get().compileClasspath
    archiveBaseName.set(base.archivesName)
    archiveClassifier.set("reobf")
    input = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(mcpTasks.taskGenerateForgeSrgMappings)
}

// Inlined from buildSrc's ShadowHelper, which existed only to hold this.
tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf()
}

tasks.register<ShadowJar>("shadowRemapJar") {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.getByName("shadow"))
    from(zipTree(tasks.named<Jar>("impetusRemapJar").get().archiveFile))
    manifest.inheritFrom(tasks.named<Jar>("jar").get().manifest)
    relocate("org.joml", "com.bdmajora.impetus.engine.impl.shadow.joml")
    mergeServiceFiles()
    from("LICENSE", "README.md")
}

tasks.named<ShadowJar>("shadowRemapJar") {
    // Forge 1.12.2 cannot scan Java 9 module descriptors.
    exclude("module-info.class")
    exclude("**/module-info.class")
    exclude("META-INF/versions/**/module-info.class")

    // Keep the release jar name stable.
    archiveFileName.set("impetus-${rootProject.properties["project_base_version"]}.0.jar")
}

tasks.named<ApplySourceAccessTransformersTask>("applySourceAccessTransformers") {
    accessTransformerFiles.from("src/main/resources/META-INF/impetus_at.cfg")
}

val booterLibsJar = tasks.register<ShadowJar>("booterLibsJar") {
    configurations = listOf(booterLibs)
    archiveClassifier.set("booter-libs")
    // The service declarations ship here rather than in the mod jar so that ServiceLoader cannot see
    // Impetus' Mixin service at all unless the bundled implementation was actually extracted.
    from("common/src/booterLibs/resources")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    exclude("module-info.class", "**/module-info.class", "**/LICENSE*")
    // Mixin's annotation processor and the hotswap agent are build-time only.
    exclude("org/spongepowered/tools/**")
    exclude("com/llamalad7/mixinextras/ap/**")
    exclude("META-INF/services/javax.annotation.processing.Processor")
    exclude("META-INF/services/org.spongepowered.tools.obfuscation.service.IObfuscationService")
}

tasks.named<Jar>("jar") {
    from(booterLibsJar) {
        into("com/bdmajora/impetus/booter")
        rename { "impetus-booter-libs.jar" }
    }
    manifest {
        attributes["FMLAT"] = "impetus_at.cfg"
        attributes["FMLCorePlugin"] = "com.bdmajora.impetus.core.ImpetusLoadingPlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
        attributes["ForceLoadAsMod"] = "true"
    }
}

tasks.register("packageJar", Copy::class) {
    from(tasks.named<ShadowJar>("shadowRemapJar").get().archiveFile)
    into("${rootProject.layout.buildDirectory.get()}/libs/${project.version}")
    dependsOn(tasks.named("shadowRemapJar"))
}

tasks.processResources.configure {
    inputs.property("version", version)
    filesMatching("mcmod.info") {
        expand(mapOf("version" to inputs.properties["version"]))
    }
}

publishing {
    publications {
        create<MavenPublication>("default") {
            artifactId = base.archivesName.get()
            artifact(tasks.named<ShadowJar>("shadowRemapJar").map { it.archiveFile })
            artifact(tasks.named("sourcesJar")) {
                classifier = "sources"
            }
        }
    }
}
