<!--
  Placeholders to fill in once the project pages exist:
    CURSEFORGE_PROJECT_ID  numeric id from the "About Project" sidebar on the CurseForge page
    MODRINTH_SLUG          the project slug (or id) from the Modrinth page URL
  The page links below also assume the slug "impetus" on both sites.
  The total downloads badge is filled in by .github/workflows/download-badge.yaml, which needs the same two values
  as the repository variables MODRINTH_PROJECT and CURSEFORGE_PROJECT_ID.
-->

<img src="icons/impetus400.png" width="128">

# Impetus

[![Available on CurseForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg)](https://www.curseforge.com/minecraft/mc-mods/impetus)
[![Available on Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/mod/impetus)

[![Total downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2FBDmajora%2FImpetus%2Fbadges%2Fdownloads.json&style=for-the-badge&label=Total%20downloads)](https://github.com/BDmajora/Impetus/releases)
[![GitHub downloads](https://img.shields.io/github/downloads/BDmajora/Impetus/total?style=for-the-badge&logo=github&label=GitHub&color=24292F)](https://github.com/BDmajora/Impetus/releases)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/CURSEFORGE_PROJECT_ID?style=for-the-badge&logo=curseforge&label=CurseForge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/impetus)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/MODRINTH_SLUG?style=for-the-badge&logo=modrinth&label=Modrinth&color=1BD96A)](https://modrinth.com/mod/impetus)
[![GitHub stars](https://img.shields.io/github/stars/BDmajora/Impetus?style=for-the-badge&logo=github&label=Stars)](https://github.com/BDmajora/Impetus/stargazers)

Impetus is a rendering engine and optimization mod for Minecraft 1.12.2 which improves frame rates, cuts down on
stutter and lets you run OptiFine shader packs, while staying compatible with large modpacks.

Most of the work happens in the renderer. Impetus replaces vanilla's chunk rendering with its own pipeline. Chunks
are meshed on worker threads and drawn in large batches with multi-draw indirect, and on recent NVIDIA cards the
terrain can be drawn with mesh shaders instead. Lighting is calculated off the main thread by a separate engine.
The rest of the mod deals with the slow parts of 1.12.2 that have nothing to do with graphics: memory use, model
loading, world generation and server ticks.

Impetus is a hard fork of [Celeritas](https://git.taumc.org/embeddedt/celeritas), and through it a descendant of
the last free-licensed versions of [Sodium](https://github.com/CaffeineMC/sodium) and
[Iris](https://github.com/IrisShaders/Iris). If you play on a modern version of the game, those are the mods you
should be using.

**If Impetus helps your game run better, please consider starring this repository.** It takes one click, and it's
the easiest way to help other players find the mod.

---

### Downloads

#### Stable builds

Official releases of Impetus are published only on our [Modrinth](https://modrinth.com/mod/impetus) and
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/impetus) pages. If you got your copy from anywhere else,
we can't help you with it.

#### Development builds

You can build the latest code yourself by following the instructions further down. Development builds are meant
for testing. They haven't been checked as carefully as a release and come with no support or warranty.

### Installation

Impetus needs **Minecraft 1.12.2** running on **[Cleanroom](https://github.com/CleanroomMC/Cleanroom) 0.7.9-alpha**
with **Java 25**. Cleanroom 0.6.x is not supported. Put the jar in your `mods` folder.

You don't need to install Mixin or MixinExtras, since Cleanroom provides both. Cleanroom also turns on its own
Kirino render engine by default. Impetus switches Kirino off at startup so the two don't both draw terrain.

If you have Phosphor or Alfheim installed, remove them. Impetus brings its own lighting engine. It does try to
disable theirs when it finds them, because two lighting engines running at once will corrupt your world's lighting,
but please don't count on that.

Looking for the old Forge / Java 8 / LWJGL 2 version? It lives on the `legacy` branch.

### Reporting Issues

Bugs, crashes and compatibility problems go on the [issue tracker](https://github.com/BDmajora/Impetus/issues).
Please attach the crash report and the full launcher log. A description on its own usually isn't enough to find
the problem.

## Why did you make Impetus?

1.12.2 still has some of the biggest and best modpacks ever made, and it runs them badly. Sodium and Iris never
supported it. OptiFine does, but it's closed source and has a long history of breaking mods. The performance mods
that do exist for 1.12.2 are good, but each one fixes a single area, and installing ten of them means ten sets of
patches stepping on each other.

Celeritas showed that a modern, Sodium-style renderer could run on 1.12.2, and that was the starting point I needed.
Impetus began as a fork of it. The shader pipeline came over from Oculus. After that I started working through the
1.12.2 performance mods I was already running, along with ideas from newer versions of the game, and rebuilding
them inside one codebase where they are written to work together.

I make Impetus because it's the mod I want to play with. If it makes your packs run better too, that's a bonus.

## Goals

* **Performance.** Impetus should keep frame times low and steady, from older laptops up to high-end desktops, and
  keep as much work as possible off the main thread.
* **Correctness.** With the default settings, the game should look and behave the way vanilla does. Anything that
  trades accuracy for speed should be a setting you choose to turn on.
* **Mod compatibility.** Impetus is built for large modpacks. Working in a clean instance is the bare minimum.
* **Shader pack compatibility.** Packs written for OptiFine and Iris should load and work without changes.
* **Original implementations.** Features inspired by Sodium 0.6 and later, which isn't free software, are written
  from scratch.
* **A readable codebase.** The code is tested and commented so that other people can find their way around it.

## What's the current state of development?

Impetus is alpha software. The renderer, shader pipeline and lighting engine all work, and the test suite covers a
large part of the code, but several of the newer systems have seen very little time in real modpacks. Expect bugs,
and please report them when you find them.

## How can I help?

* Star the repository. It really does help people find Impetus.
* Report bugs, with logs. Crash reports from big modpacks are especially useful.
* Pull requests are welcome. If you're planning something large, open an issue first so we can talk it through
  before you put the time in.

## Star History

<a href="https://www.star-history.com/#BDmajora/Impetus&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=BDmajora/Impetus&type=Date&theme=dark">
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=BDmajora/Impetus&type=Date">
    <img alt="Star history of BDmajora/Impetus" src="https://api.star-history.com/svg?repos=BDmajora/Impetus&type=Date">
  </picture>
</a>

Thank you to everyone who has starred the project so far.

## What's included

Impetus ships as a single jar made up of several modules, each with its own mixin configuration.

| | Module | What it does |
| :-: | :----- | :----------- |
| <img src="icons/impetus400.png" width="32"> | `impetus` | The core renderer. Chunk meshing, draw submission, multi-draw indirect terrain on OpenGL 4.3, and an optional mesh-shader backend based on Nvidium. Shader packs loaded under the mesh-shader backend are ported to it automatically and saved in `nvidium_shaderpacks/`. |
| <img src="icons/umbra400.png" width="32"> | `umbra` | The shader pipeline, descended from Iris and Oculus. |
| <img src="icons/fulgor400.png" width="32"> | `fulgor` | The lighting engine, replacing Phosphor and Alfheim. It has a Phosphor-style engine with parallel light passes and an asynchronous Starlight-style engine, ported from Pulsar, that lights chunks on worker threads. |
| <img src="icons/coarctatio400.png" width="32"> | `coarctatio` | Memory use and loading time. Block state and model deduplication, lazy item capabilities, resource lookup caches, a mod scan cache kept between launches, parallel texture decoding and optional on-demand model loading. |
| <img src="icons/equilibrium400.png" width="32"> | `equilibrium` | World and server performance. Chunk access caching, faster world generation, cheaper advancement triggers, crafting and furnace caches, ghost-chunk guards and an optional tick budget. |
| | `dynamiclights` | Light from held and dropped items. |
| | `extras` | Optional features that don't fit anywhere else, among them a render budget and GPU Booster for weaker machines, parallel entity ticking, parallel particles, baked block entities, ray-cast entity culling, HUD caching, text batching, network tweaks and a zoom key. |
| | `linuxextras` | Linux desktop fixes. Opens the game window on your primary monitor and uses `xdg-open` for links and folders. |

## Hardware Compatibility

If your graphics card can run Minecraft on Cleanroom, it can run Impetus. Two rendering paths need more than that:

* **Multi-draw indirect terrain** needs OpenGL 4.3 or the `ARB_multi_draw_indirect` extension. Cards without it fall
  back to the regular path on their own.
* **The mesh-shader backend** needs an NVIDIA GeForce RTX 20 series (Turing) card or newer. It's off by default and
  can be turned on with the Graphics API option in video settings.

Keep your graphics drivers up to date. Impetus has workarounds for a number of known driver bugs, but not for all
of them.

#### OpenGL Compatibility Layers

Translation layers such as GL4ES and ANGLE are not supported.

## Building from sources

Impetus uses the [Gradle build tool](https://gradle.org/), with [Unimined](https://github.com/unimined/unimined)
setting up Cleanroom's patched Minecraft. The
[Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is included and
downloads the right version of Gradle for you.

```
./gradlew packageJar
```

The finished jar ends up in `build/libs/<version>/`. There's also a helper script:

```
./run.sh
```

It has a menu with Build, Test, Clean and Quit, or you can skip the menu with `./run.sh build`, `./run.sh test` or
`./run.sh clean`. Be aware that Clean removes every build output, not only `build/`, so the next build has to set
up Cleanroom again and takes several minutes.

### Build Requirements

- JDK 17 or newer to run Gradle. If you don't have JDK 25 installed, the build downloads it for compiling.
- Nothing else. Use the wrapper rather than a separate Gradle install.

### LWJGL

Impetus uses LWJGL 3 only. All of the engine's GL calls go through a single `LWJGLService` instance, which looks
up the version-dependent entry points once per context and is what the unit tests mock. Native memory and stack
allocations use LWJGL's `MemoryUtil` and `MemoryStack`, which run on the Foreign Function & Memory API on Java 25.

Vanilla code on Cleanroom still uses LWJGL 2 names, which the lwjgly bridge provides. Impetus only uses those where
a vanilla method signature requires it, for input events, key codes and `util.vector`. Window controls go through
Cleanroom's SDL window API.

Cleanroom 0.7.9 doesn't publish the userdev artifact that Unimined needs. To get around this, the build uses the
0.6.13 MCP workspace for Minecraft and Forge symbols and compiles the window code against a small API slice of the
real 0.7.9 universal jar, lwjgly 2.1.0 and LWJGL 3.4.3. Cleanroom provides all of these at runtime, so Impetus
doesn't bundle them. Unimined's development launch configurations still run on the older workspace, so test the
packaged jar in a real Cleanroom 0.7.9 instance.

### Project layout

| Path | Contents |
| :--- | :------- |
| `src/` | The Cleanroom 1.12.2 mod: engine glue, mixins and the coremod that registers them |
| `common/` | Renderer code with no Minecraft dependency (Java 25, LWJGL 3) |
| `gradle/libs.versions.toml` | Every dependency version. Libraries that Cleanroom ships are pinned to its versions |
| `gradle/wrapper/` | The Gradle wrapper, committed on purpose |

## Shader packs

The shader pipeline comes from Oculus 1.7 and Iris. Identifiers that belong to the Iris protocol, such as the
`IRIS_VERSION` define and the `iris` shader format namespace, are kept as they are so existing packs keep working.

### Distant Horizons

With [Distant Horizons](https://gitlab.com/distant-horizons-team/distant-horizons) installed, a pack's
`dh_terrain`, `dh_water`, `dh_shadow` and `dh_generic` programs shade DH's LODs the same way they do under Iris.
LODs render into the pack's gbuffers, DH's depth is available as `dhDepthTex0` and `dhDepthTex1`, the
`dhProjection`, `dhNearPlane`, `dhFarPlane` and `dhRenderDistance` uniforms are filled in, the `DISTANT_HORIZONS`
and `DH_BLOCK_*` defines are set, and `dhShadow.enabled` and `dhClouds` in `shaders.properties` are respected.

You need a DH build that recognizes the `impetus` mod id. Its 1.12.2 Cleanroom branch does. With an older build,
DH draws its LODs with its own shaders in front of the shader pack.

## For mod developers

### Accent colour in the video settings screen

Mods that add option pages to the Impetus video settings screen get an accent colour for their tab and controls.
By default it's picked from a fixed palette, but you can register your own:

```java
ConfigThemeRegistry.register(ConfigTheme.builder("mymod").accentColor(0x3FA7FF).build());
```

Both classes are in `com.bdmajora.impetus.api.config.theme`. Register during client setup. The screen reads tab
colours when it opens, so a theme registered while it's open won't fully apply until the next time.

- The mod id has to match the one your option pages use. Case and surrounding whitespace are ignored.
- Colours are ARGB. A plain `0xRRGGBB` value with no alpha is treated as fully opaque.
- Registering a second theme for the same mod replaces the first.

## Credits

This is where the code and ideas in Impetus came from. If a feature reminds you of another mod, you'll probably
find it here.

### Upstream projects

* **JellySquid and the CaffeineMC team**, for [Sodium](https://github.com/CaffeineMC/sodium), and for releasing 0.5.11
  and earlier as free software
* **coderbot, IMS and the Iris contributors**, for [Iris](https://github.com/IrisShaders/Iris), where the shader
  pipeline comes from
* **embeddedt**, for [Celeritas](https://git.taumc.org/embeddedt/celeritas), which Impetus is forked from, and
  [Embeddium](https://github.com/embeddedt/embeddium) before it
* **Asek3**, for [Rubidium](https://github.com/Asek3/Rubidium), the original Forge port of Sodium, and
  [Oculus](https://github.com/Asek3/Oculus), the Forge port of Iris
* **Mumfrey**, for [Mixin](https://github.com/SpongePowered/Mixin), and **CleanroomMC** for
  [Cleanroom](https://github.com/CleanroomMC/Cleanroom) and CleanMix
* **Rongmario**, for [MixinBooter](https://github.com/CleanroomMC/MixinBooter)
* **LlamaLad7**, for [MixinExtras](https://github.com/LlamaLad7/MixinExtras)

### Art

* **ProprietaryCable** ([proprietarycables@gmail.com](mailto:proprietarycables@gmail.com)), for drawing the Impetus
  and module icons, plus a lot of designs that didn't end up getting used
* **CelestialAbyss**, for the Embeddium logo, and **input-Here** for touching it up

### Code and ideas

* **MCRcortex**, for [Nvidium](https://github.com/MCRcortex/nvidium) (LGPL-3). The mesh-shader terrain backend is
  ported from it.
* **orf**, for [GpuShift](https://modrinth.com/mod/gpushift) (MIT). The Render Budget group reimplements its adaptive
  render budget for 1.12.2, CPU/GPU bottleneck detector included.
* **Mr.Toad**, for [GPUBooster](https://github.com/ITsMrToad/GPUBooster), ported to 1.12.2 with permission as the GPU
  Booster group
* **AxalotL**, for [Async](https://github.com/AxalotLDev/Async) (GPL-3), and the authors of MCMT and
  [JMT-MCMT](https://github.com/jediminer543/JMT-MCMT) it builds on. The Parallel Ticking group reimplements its
  parallel entity ticking for 1.12.2.
* **Harvey_Husky**, for [AsyncParticles](https://github.com/Harveykang/AsyncParticles) (LGPL-3). Parallel particle
  ticking, the particle light cache and off-screen particle culling are based on it.
* **fzzyhmstrs**, for [Particle Core](https://github.com/fzzyhmstrs/pc) (MIT), the source of the particle collision
  cache
* **FoundationGames**, for [Enhanced Block Entities](https://github.com/FoundationGames/EnhancedBlockEntities)
  (LGPL-3), and **Ceeden**, for [Better Block Entities](https://github.com/EdeenMC/betterblockentities) (LGPL-3).
  The Baked Block Entities group rebuilds their approach from the vanilla entity models.
* **malte0811**, for [FerriteCore](https://github.com/malte0811/FerriteCore) (MIT). Coarctatio uses its block state
  and model deduplication techniques.
* **Steveplays28**, for [Noisium](https://github.com/Steveplays28/noisium) (LGPL-3), the basis for Equilibrium's
  world generation fast paths
* **TonimatasDEV**, for [Packet Fixer](https://github.com/TonimatasDEV/PacketFixer) (MIT), the basis for the Network
  group
* **Spottedleaf**, for [Starlight](https://github.com/PaperMC/Starlight), and **ishland**, for
  [ScalableLux](https://github.com/RelativityMC/ScalableLux) (LGPL-3). Fulgor schedules its light passes the way they
  do.
* **Sumire Labs**, for [Pulsar](https://github.com/Sumire-Labs/Pulsar) (LGPL-3). Fulgor's async mode is ported from
  it. Pulsar itself grew out of [SuperNova](https://github.com/GTNewHorizons/SuperNova) by GTNewHorizons.
* **wisecase2**, for [StutterFix](https://github.com/wisecase2/StutterFix) (MIT), the basis for the Thread Scheduling
  group
* **asie**, for [FoamFix](https://github.com/asiekierka/FoamFix) (MIT). Equilibrium carries its ghost-chunk guards,
  spawner check cache and idle entity cleanup.
* **Rongmario**, again, for Chibi and [LoliASM](https://github.com/LoliKingdom/LoliASM) (LGPL-3). The lazy item
  capabilities, furnace recipe index, entity factory, loader string interning, remapper cache compaction, recycled
  events and the world load and screenshot tweaks are based on them.
* **Kasumi_Nova** and **Circulate233**, for [StellarCore](https://github.com/NovaEngineering-Source/StellarCore)
  (MIT). The numeric tag pool, hash caches, long-keyed tile entity map, HUD caching approach, parallel texture
  decoding, primitive ore dictionary and model part pooling all started there.
* **embeddedt**, again, for [VintageFix](https://github.com/embeddedt/VintageFix) (LGPL-3). The mod scan cache,
  resource existence caches, stackless resource exceptions, shelf stitching, soft structure templates, fast item
  baking and dynamic model loading are based on it. Dynamic model loading goes back further, to **Runemoro**'s
  dynamic resources in [VanillaFix](https://github.com/DimensionalDevelopment/VanillaFix).
* **ACGaming** and the [Universal Tweaks](https://github.com/ACGaming/UniversalTweaks) contributors (jchung01,
  Darkhax, EverNife, Barteks2x) (MIT), for the advancement trigger, crafting cache, pathfinding chunk guard, entity
  radius check, chunk generation limit, sound debug skip, quiet prefix check and plain missing models
* **Mephodio**, for [Icterine](https://github.com/Mephodio/Icterine) (MIT), where the advancement trigger
  optimisation first appeared
* **VidTu**, for [Ksyxis](https://github.com/VidTu/Ksyxis) (MIT), which showed how to skip the spawn chunk preload
* **tr7zw**, for [Entity Culling](https://github.com/tr7zw/EntityCulling) and
  [Exordium](https://github.com/tr7zw/Exordium), and **Meldexun**, for the 1.12.2
  [Entity Culling](https://github.com/Meldexun/EntityCulling) (MIT). Ray-cast occlusion culling of entities and block
  entities is based on their work.
* **Luna Lage (Desoroxxx)** and Red Studio, for [Valkyrie](https://github.com/Red-Studio-Ragnarok/Valkyrie)
  (LGPL-3), the basis for the ModelRenderer matrix path
* **Andrew Steinborn (astei)**, for [Krypton](https://github.com/astei/krypton) (LGPL-3). Flush consolidation and the
  varint, frame decoder and compression paths come from it.
* **RaphiMC**, for [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast) (LGPL-3), the basis for text glyph
  batching
* **imthosea**, for [BadOptimizations](https://github.com/imthosea/BadOptimizations) (LGPL-3), the basis for
  lightmap and entity flag caching
* **fxmorin**, for [More Culling](https://github.com/fxmorin/MoreCulling) (LGPL-3), the basis for item frame LOD and
  leaf face culling, and **isXander** for [Cull Less Leaves](https://github.com/isXander/CullLessLeaves) (MIT)
* **jaredlll08**, for [Clumps](https://github.com/jaredlll08/Clumps) (MIT), the basis for experience orb merging
* **decce6**, for [Gnetum](https://github.com/decce6/Gnetum), and **Moulberry**, for
  [HUDCaching](https://github.com/Moulberry/MCHUDCaching). The HUD cache follows the same idea.
* **LogicalGeekBoy** and the Logical Zoom contributors, for [Logical Zoom](https://github.com/LogicalGeekBoy/logical_zoom)
  (MIT), ported with its defaults as the Zoom group

### Help along the way

* **Ven ([@basdxz](https://github.com/basdxz))**, for help with translucency sorting, for suggesting the general
  approach behind async occlusion culling, and for plenty of other suggestions during development
* **XFactHD**, **Pepper**, and anyone else I've missed, for valuable code insights

## License

Impetus is licensed under the [GNU Lesser General Public License version 3](LICENSE). It only uses code from Iris
1.7, Sodium 0.5.11 and earlier, Celeritas, and other free and open-source projects.

Impetus does not contain, and will not contain, any code from Sodium 0.5.12 or 0.6 and later, because those
versions are not available under a free and open-source license. Features inspired by modern Sodium are
independent implementations written from scratch.
