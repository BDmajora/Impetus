<!--
  Placeholders to fill in once the project pages exist:
    CURSEFORGE_PROJECT_ID  numeric id from the "About Project" sidebar on the CurseForge page
    MODRINTH_SLUG          the project slug (or id) from the Modrinth page URL
  The page links below also assume the slug "impetus" on both sites.
-->

<img src="icons/impetus400.png" width="128">

# Impetus

[![Available on CurseForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg)](https://www.curseforge.com/minecraft/mc-mods/impetus)
[![Available on Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/mod/impetus)

[![CurseForge downloads](https://img.shields.io/curseforge/dt/CURSEFORGE_PROJECT_ID?style=for-the-badge&logo=curseforge&label=CurseForge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/impetus)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/MODRINTH_SLUG?style=for-the-badge&logo=modrinth&label=Modrinth&color=1BD96A)](https://modrinth.com/mod/impetus)

**Impetus makes Minecraft 1.12.2 run faster and look better.** It replaces the game's renderer with a modern one,
runs shader packs, fixes lighting lag, and cuts down on stutter and memory use, all in a single mod for
[Cleanroom](https://github.com/CleanroomMC/Cleanroom).

Under the hood, Impetus is a high-performance rendering and optimization project that modernizes the 1.12.2
graphics pipeline and takes CPU and GPU overhead out of it. It brings its own rendering infrastructure,
asynchronous chunk meshing and lighting, GPU-driven and mesh-shader terrain paths, and broad shader pack
compatibility. Beyond rendering, it also goes after world generation, memory management and server tick
bottlenecks.

Impetus is a hard fork of Celeritas by embeddedt, which descends from Embeddium and Oculus, and before them from
the last free-licensed releases of [Sodium](https://github.com/CaffeineMC/sodium) and
[Iris](https://github.com/IrisShaders/Iris). If you play on modern versions of the game, those are the mods you
want.

---

### ⚙️ What it does

- **A custom rendering architecture.** Terrain is drawn through Impetus's own chunk renderer, with dynamic
  translucency sorting, tree-based occlusion culling and a VAO cache, not vanilla's display lists.
- **GPU and CPU performance work.** Multi-draw indirect terrain on OpenGL 4.3, compact vertex formats,
  allocation-free hot paths, block state and model deduplication, and caches across vanilla systems that were
  doing the same work every tick.
- **Asynchronous chunk and lighting pipelines.** Chunks mesh on worker threads, and the Fulgor lighting engine
  lights them off the main thread, either with parallel Phosphor-style passes or a Starlight-style engine that
  persists its light.
- **Modern graphics API paths.** On NVIDIA Turing and newer, an opt-in mesh-shader terrain backend after Nvidium
  draws the world almost entirely on the GPU.
- **Shader pack compatibility.** The bundled Umbra pipeline loads OptiFine/Iris format shader packs, ports them
  to the mesh-shader backend on the fly, and shades Distant Horizons LODs the way Iris does.
- **Server-side optimizations.** World generation fast paths, crafting and furnace caches, cheaper advancement
  triggers and an opt-in tick budget.
- **Hardware compatibility.** Driver workarounds and probes for known bad GPU and driver combinations.

### 📥 Downloads

#### Stable builds

Releases of Impetus are published on our official [Modrinth](https://modrinth.com/mod/impetus) and
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/impetus) pages. If you download an Impetus `.jar` from
anywhere else, we can't support it and you do so at your own risk.

#### Development builds

The very latest changes can be built from source using the instructions below. These builds are mostly useful for
testing and come with no support or warranty. Expect rough edges: the mod is under active development and has
seen limited testing.

### 🖥️ Installation

Impetus targets **Minecraft 1.12.2** on **[Cleanroom](https://github.com/CleanroomMC/Cleanroom) 0.7.9-alpha**,
running on **Java 25** with LWJGL 3.4.3 and SDL3. Cleanroom 0.6.x is no longer supported. Drop the jar into your
`mods` folder and that's it.

Cleanroom ships Mixin (CleanMix) and MixinExtras itself, so Impetus bundles neither. It also ships its Kirino
render engine switched on; Impetus turns Kirino off at startup, since both would otherwise do terrain work every
frame.

Remove Phosphor and Alfheim if you have them. Impetus has its own lighting engine and suppresses theirs when it
finds them, because two lighting engines at once corrupt world lighting, but don't rely on that.

The last Forge / Java 8 / LWJGL 2 version of Impetus lives on the `legacy` branch.

### 📬 Reporting Issues

Please open an issue on the [project issue tracker](https://github.com/BDmajora/Impetus/issues). Crash reports and
the full launcher log are far more useful than a description on its own.

## Why Impetus?

1.12.2 is still one of the most played versions of Minecraft because of its modpacks, but it never got the
rendering work that modern versions did. Sodium and Iris moved on to newer versions, OptiFine is closed source and
fights with half the mods in a typical pack, and the forks that kept the old renderer alive each covered only part
of the problem.

Impetus is an attempt to close that gap: to bring the ideas behind the modern Sodium renderer, a working shader
pipeline and the best of the 1.12.2 performance mods together in one place, built to work with each other instead
of around each other.

## Goals

- **Performance.** Keep the GPU busy and the main thread free, on both low-end and high-end machines.
- **Correctness.** Look the same as vanilla unless you asked for something different.
- **Mod compatibility.** Work in large modpacks, not just in a clean instance.
- **Shader pack compatibility.** Existing OptiFine and Iris shader packs should load without changes.
- **Original implementations.** Features inspired by modern, non-free Sodium are written from scratch.
- **A codebase worth reading.** Tested, commented, and pleasant to work on.

## 🧩 What's included

Impetus ships as a single jar containing several subsystems, each with its own mixin configuration:

| | Module | Purpose |
| :-: | :----- | :------ |
| <img src="icons/impetus400.png" width="32"> | `impetus` | The core rendering engine and chunk pipeline, with GL 4.3 multi-draw indirect terrain and, on NVIDIA Turing and newer, a mesh-shader terrain backend after Nvidium, picked with the Graphics API option (OpenGL or Nvidium), which also runs shader packs: a pack loaded under Nvidium is ported automatically into `nvidium_shaderpacks/` with mesh-shader terrain, water and shadow programs, and re-ported whenever the pack changes |
| <img src="icons/umbra400.png" width="32"> | `umbra` | The bundled shader pipeline, descended from Iris / Oculus |
| <img src="icons/fulgor400.png" width="32"> | `fulgor` | Lighting engine, superseding Phosphor and Alfheim: a deferred Phosphor-style engine with parallel light passes in the manner of ScalableLux, and a Starlight-style asynchronous engine after Pulsar that lights chunks on worker threads and persists their light |
| <img src="icons/coarctatio400.png" width="32"> | `coarctatio` | Memory and allocation reductions across vanilla systems, including FerriteCore's block-state and model deduplication techniques, lazy item capabilities, resource lookup caches, a between-launch mod scan cache, parallel texture decoding and model reading, a primitive ore dictionary, compacted remapper caches and opt-in on-demand model loading with a pack-scan atlas |
| <img src="icons/equilibrium400.png" width="32"> | `equilibrium` | Chunk and world access caching, world generation fast paths, and the server-side tick optimizations: advancement triggers, crafting and furnace caches, ghost-chunk guards, spawn preload skipping and the opt-in tick budget |
| | `dynamiclights` | Dynamic light sources for held and dropped items |
| | `extras` | Optional feature toggles: the Render Budget and GPU Booster pair for weaker machines, parallel server ticking, parallel particles, baked block entities, ray-cast occlusion culling, HUD caching, text batching, network flush consolidation, wire limits, thread scheduling and a hold-to-zoom key |
| | `linuxextras` | Linux desktop fixes: starts the SDL window on the primary monitor (KDE's display order, or outputs named in `impetus-linuxextras.cfg`) and opens links and folders with `xdg-open`. Cleanroom/SDL now handle native Wayland selection and window icons |

## ✅ Hardware Compatibility

Impetus runs on any graphics card that can run Minecraft on Cleanroom. Some rendering paths need more:

- **Multi-draw indirect terrain** needs OpenGL 4.3 or `ARB_multi_draw_indirect`. Older cards fall back to the
  regular path automatically.
- **The Nvidium mesh-shader backend** needs an NVIDIA GeForce RTX 20 series (Turing) or newer card. It is opt-in
  through the Graphics API option.

Keep your graphics drivers up to date. Impetus works around a number of known driver bugs, but it can't work around
all of them. OpenGL translation layers (GL4ES, ANGLE and similar) are not supported.

## 🛠️ Building from sources

Impetus uses the [Gradle build tool](https://gradle.org/) with
[Unimined](https://github.com/unimined/unimined) (Cleanroom's fork) to set up Cleanroom's patched Minecraft.
The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is
provided and will download the correct Gradle version automatically.

```
./gradlew packageJar
```

The finished jar is written to `build/libs/<version>/`. A helper script is also provided:

```
./run.sh
```

which offers **Build**, **Test**, **Clean**, and **Quit**. It also works non-interactively, e.g. `./run.sh build`,
`./run.sh test` or `./run.sh clean`. Note that *Clean* removes every build output directory, not just `build/`,
so the next build re-runs the Cleanroom setup and takes several minutes.

### Build Requirements

- **JDK 17 or newer** to run Gradle; the JDK 25 compile toolchain is provisioned automatically when none is
  installed.
- No separate Gradle installation is needed; use the wrapper.

### LWJGL

Impetus is LWJGL 3 only. Engine GL calls go through one `LWJGLService` instance, which resolves the
version-dependent entry points once per context and is what the unit tests mock; native memory and stack
allocations use LWJGL's own `MemoryUtil` and `MemoryStack`, which on Java 25 run on the Foreign Function & Memory
API.

Vanilla code on Cleanroom still links against LWJGL 2 names, provided by the lwjgly bridge. Impetus uses
those names only where vanilla signatures require them (input events, key codes and `util.vector`). Window
controls go through Cleanroom's SDL window API, and rendering uses LWJGL 3 OpenGL.

Cleanroom 0.7.9 does not publish the userdev artifact Unimined needs. The build therefore keeps the 0.6.13
MCP workspace for Minecraft/Forge symbols, while compiling window code against a narrow API slice of the
real 0.7.9 universal jar, lwjgly 2.1.0 and LWJGL 3.4.3. These dependencies are supplied by Cleanroom at runtime
and are not bundled into Impetus. Unimined's development launch configurations still use the older workspace;
verify the packaged mod in a Cleanroom 0.7.9 instance.

The old Linux Extras `forceWayland` and `waylandWindowIcon` switches are obsolete because SDL handles both.
`waylandFullscreenMonitor`, `waylandMonitors` and `xdgOpen` retain their existing meanings.

### Project layout

| Path | Contents |
| :--- | :------- |
| `src/` | The Cleanroom 1.12.2 mod: engine glue, mixins and the coremod that registers them |
| `common/` | Platform-agnostic renderer code (Java 25, LWJGL 3) with no Minecraft dependency |
| `gradle/libs.versions.toml` | Every dependency version; the libraries Cleanroom ships are pinned to its versions |
| `gradle/wrapper/` | The Gradle wrapper; required by `gradlew` and intentionally committed |

## 🎨 Shader pack compatibility

The bundled shader pipeline originates from Oculus 1.7 / Iris. Iris-protocol identifiers (such as the
`IRIS_VERSION` define and `iris`-namespaced shader-format identifiers) are intentionally preserved so that
existing shader packs continue to load and function unchanged.

### Distant Horizons

With Distant Horizons installed, a pack's `dh_terrain`, `dh_water`, `dh_shadow` and `dh_generic` programs
shade DH's LODs the way they do under Iris: LODs render into the pack's gbuffers with DH's own depth exposed
as `dhDepthTex0`/`dhDepthTex1`, the `dhProjection`, `dhNearPlane`, `dhFarPlane` and `dhRenderDistance`
uniforms are live, the `DISTANT_HORIZONS` and `DH_BLOCK_*` defines are set, and `dhShadow.enabled` /
`dhClouds` in `shaders.properties` are honoured. DH must be a build that knows the `impetus` mod id (its
1.12.2 Cleanroom branch registers an Impetus accessor); without it DH renders its LODs with its own shaders
in front of the pack.

## 🔌 For mod developers

### Accent colour in the video settings screen

Mods that add option pages to the Impetus video settings screen get an accent colour for their tab and
controls. It is picked from a fixed palette unless the mod registers its own:

```java
ConfigThemeRegistry.register(ConfigTheme.builder("mymod").accentColor(0x3FA7FF).build());
```

Both classes are in `com.bdmajora.impetus.api.config.theme`. Register during client setup. The screen reads tab
colours when it opens, so a theme registered while it is open only fully applies the next time. Notes:

- The mod id must match the one your option pages use. Ids are matched ignoring case and surrounding
  whitespace.
- The colour is ARGB. A plain `0xRRGGBB` value (alpha 0) is treated as fully opaque.
- Registering again for the same mod replaces the earlier theme.

## 📜 License

Impetus is licensed under the [GNU Lesser General Public License version 3](LICENSE), as it only uses code
from Iris 1.7, Sodium 0.5.11 and earlier, Celeritas, and other FOSS projects.

This project does not include, and has no plans to include, any code from Sodium 0.6+ or 0.5.12+, as those
versions are not available under a free and open-source license. Features inspired by modern Sodium are
independent, original implementations.

## 🙏 Credits

Impetus stands on a lot of other people's work. This list is where the code and ideas came from; if a feature
reminds you of another mod, it is probably named here.

### Upstream projects

* **The CaffeineMC team and JellySquid**, for developing [Sodium](https://github.com/CaffeineMC/sodium) 0.5.11 and older, and making it open source
* **coderbot, IMS and the Iris contributors**, for [Iris](https://github.com/IrisShaders/Iris), and the **Oculus** port, the origin of the bundled shader pipeline
* **embeddedt**, for developing Celeritas and Embeddium, from which Impetus is forked
* **Asek3**, for developing Rubidium, the original port of Sodium 0.5 to Forge

### Art

* **ProprietaryCable** ([proprietarycables@gmail.com](mailto:proprietarycables@gmail.com)), for creating the Impetus icons, the module icons, and the designs that didn't make it in
* **CelestialAbyss**, for developing the Embeddium logo, and **input-Here** for visual touchups

### Code and ideas

* **MCRcortex**, for Nvidium (LGPL-3), whose mesh-shader terrain renderer the `impetus` module's Mesh Terrain backend is ported from
* **Rongmario**, for MixinBooter
* **Mumfrey**, for creating the Mixin bytecode patching system, and **CleanroomMC** for Cleanroom and CleanMix
* **LlamaLad7**, for MixinExtras
* **orf**, for GpuShift (MIT), whose adaptive render-budget design, CPU/GPU bottleneck detector included, the Extras page's Render Budget group reimplements for 1.12.2
* **Mr.Toad**, for GPUBooster, ported to 1.12.2 with permission as the Extras page's GPU Booster group
* **AxalotL** and the MCMT / JMT-MCMT authors, for Async (GPL-3), whose parallel entity ticking design the Extras page's Parallel Ticking group reimplements for 1.12.2
* **Harvey_Husky**, for AsyncParticles (LGPL-3), the model for the parallel particle ticking, light cache and off-screen culling
* **fzzyhmstrs**, for Particle Core (MIT), the model for the particle collision cache
* **FoundationGames** (Enhanced Block Entities, LGPL-3) and the **Better Block Entities** team (LGPL-3), whose baked block entity approach the Extras page's Baked Block Entities group reimplements from the vanilla entity models
* **malte0811**, for FerriteCore (MIT), whose block-state and model deduplication techniques Coarctatio carries
* **Steveplays28**, for Noisium (LGPL-3), the model for Equilibrium's world generation fast paths
* **TonimatasDEV**, for Packet Fixer (MIT), the model for the Extras page's Network group
* **Spottedleaf** (Starlight) and **ishland** (ScalableLux, LGPL-3), whose parallel light scheduling Fulgor adopts
* **Sumire Labs**, for Pulsar (LGPL-3), the Starlight-derived asynchronous lighting engine Fulgor's async mode is ported from, and the SuperNova lineage it descends from
* **wisecase2**, for StutterFix (MIT), the model for the Extras page's Thread Scheduling group
* **asie**, for FoamFix (MIT), whose ghost-chunk guards, spawner check cache and idle entity cleanup Equilibrium carries
* **Rongmario**, again, for Chibi / LoliASM (LGPL-3), the model for the lazy item capabilities, furnace recipe index, entity factory, loader string interning, remapper cache compaction, recycled events and the world-load and screenshot tweaks
* **Kasumi_Nova** and **Circulate233**, for StellarCore (MIT), the model for the numeric tag pool, the hash caches, the long-keyed tile entity map, the HUD caching approach, parallel texture decoding, the primitive ore dictionary and model part pooling
* **embeddedt**, again, for VintageFix (LGPL-3), the model for the mod scan cache, resource existence caches, stackless resource exceptions, shelf stitching, soft structure templates, fast item baking and the dynamic model loading design (with Runemoro, whose Dynamic Resources it descends from)
* **ACGaming** and the Universal Tweaks contributors (jchung01, Darkhax, EverNife, Barteks2x) (MIT), the model for the advancement trigger, crafting cache, pathfinding chunk guard, entity radius check, chunk generation limit, sound debug skip, quiet prefix check and plain missing models
* **Mephodio**, for Icterine (MIT), the origin of the advancement trigger optimisation
* **VidTu**, for Ksyxis (MIT), the model for skipping the spawn chunk preload
* **tr7zw** and **Meldexun**, for Entity Culling (MIT), the model for the ray-cast occlusion culling of entities and block entities, and tr7zw again for Exordium
* **Luna Lage (Desoroxxx)** and Red Studio, for Valkyrie (LGPL-3), the model for the ModelRenderer matrix path
* **Andrew Steinborn (astei)**, for Krypton (LGPL-3), the model for flush consolidation, the varint, frame decoder and compression paths
* **RaphiMC**, for ImmediatelyFast (LGPL-3), the model for text glyph batching
* **imthosea**, for BadOptimizations (LGPL-3), the model for lightmap and entity flag caching
* **fxmorin**, for More Culling (LGPL-3), the model for item frame LOD and leaf face culling, and **isXander** for Cull Less Leaves (MIT)
* **jaredlll08**, for Clumps (MIT), the model for experience orb merging
* **decce6** for Gnetum and **Moulberry** for HUDCaching, the family the HUD cache belongs to
* **LogicalGeekBoy** and the Logical Zoom contributors, for Logical Zoom (MIT), ported as the Extras page's Zoom group with its defaults

### Help along the way

* **Ven ([@basdxz](https://github.com/basdxz))**, for help with translucency sorting, suggesting the general approach for async occlusion culling, and other suggestions during development
* **XFactHD**, **Pepper**, and anyone else omitted here, for valuable code insights
