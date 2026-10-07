<img src="icons/impetus400.png" width="128">

# Impetus

Impetus is a free and open-source performance and shaders mod for the Minecraft 1.12.2 client. It is a hard
fork of Celeritas by embeddedt (itself a fork of Embeddium and Oculus 1.7, which in turn descend from the
last FOSS-licensed version of Sodium and from Iris 1.7).

Impetus takes the project in a new direction: closing the feature gap with the modern Sodium renderer,
dynamic translucency sorting, tree-based occlusion culling, and GPU driver workarounds, through independent,
original implementations, while keeping first-class support for legacy Minecraft versions and the bundled
shader pipeline.

---

### Downloads

**There are currently no official Impetus binary releases.** If you download a precompiled Impetus `.jar`
from any third-party source, we cannot provide support for it and you do so at your own risk. Expect bugs
and rough edges: the mod is under active development and has seen limited testing.

To use Impetus today, build it from source using the instructions below.

### Installation

Impetus targets **Minecraft 1.12.2** on **[Cleanroom](https://github.com/CleanroomMC/Cleanroom)** (developed
against 0.6.13-alpha) running on **Java 25** with LWJGL 3. Drop the jar into your `mods` folder.

Cleanroom ships Mixin (CleanMix) and MixinExtras itself, so Impetus bundles neither. It also ships its Kirino
render engine switched on; Impetus turns Kirino off at startup, since both would otherwise do terrain work
every frame.

The last Forge / Java 8 / LWJGL 2 version of Impetus lives on the `legacy` branch.

### Reporting Issues

Please open an issue on the [project issue tracker](https://github.com/bdmajora/impetus/issues). Crash
reports and the full launcher log are far more useful than a description alone.

## What's included

Impetus ships as a single jar containing several subsystems, each with its own mixin configuration:

| Module | Purpose |
| :----- | :------ |
| `impetus` | The core rendering engine and chunk pipeline, with GL 4.3 multi-draw indirect terrain and, on NVIDIA Turing and newer, a mesh-shader terrain backend after Nvidium |
| `umbra` | The bundled shader pipeline, descended from Iris / Oculus |
| `fulgor` | Lighting engine, superseding Phosphor and Alfheim: a deferred Phosphor-style engine with parallel light passes in the manner of ScalableLux, and a Starlight-style asynchronous engine after Pulsar that lights chunks on worker threads and persists their light |
| `coarctatio` | Memory and allocation reductions across vanilla systems, including FerriteCore's block-state and model deduplication techniques, lazy item capabilities, resource lookup caches, a between-launch mod scan cache, parallel texture decoding and model reading, a primitive ore dictionary, compacted remapper caches and opt-in on-demand model loading with a pack-scan atlas |
| `equilibrium` | Chunk and world access caching, world generation fast paths, and the server-side tick optimizations: advancement triggers, crafting and furnace caches, ghost-chunk guards, spawn preload skipping and the opt-in tick budget |
| `dynamiclights` | Dynamic light sources for held and dropped items |
| `extras` | Optional feature toggles: the Render Budget and GPU Booster pair for weaker machines, parallel server ticking, parallel particles, baked block entities, ray-cast occlusion culling, HUD caching, text batching, network flush consolidation, wire limits and thread scheduling |
| `linuxextras` | Linux desktop fixes: on a Wayland session the window runs natively instead of through XWayland, goes fullscreen on the primary monitor (KDE's display order, or outputs named in `impetus-linuxextras.cfg`) rather than the leftmost one, gets its icon through `xdg-toplevel-icon-v1`, and opens links and folders with `xdg-open` instead of offering only Copy to Clipboard |

Impetus suppresses the mixin configurations of superseded lighting mods (Phosphor, Alfheim) when it detects
them, since running two lighting engines at once corrupts world lighting. Remove those mods rather than
relying on the suppression.

## Building from sources

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

Vanilla code on Cleanroom still links against LWJGL 2 names, which Cleanroom provides by merging its lwjglx
bridge into LWJGL 3 at class load. Impetus only touches that bridge where vanilla's own signatures demand it
(input events, key codes, `util.vector`) and for `Display`, which owns the GLFW window; everything else is
plain LWJGL 3 and GLFW.

### Project layout

| Path | Contents |
| :--- | :------- |
| `src/` | The Cleanroom 1.12.2 mod: engine glue, mixins and the coremod that registers them |
| `common/` | Platform-agnostic renderer code (Java 25, LWJGL 3) with no Minecraft dependency |
| `gradle/libs.versions.toml` | Every dependency version; the libraries Cleanroom ships are pinned to its versions |
| `gradle/wrapper/` | The Gradle wrapper; required by `gradlew` and intentionally committed |

## Shader pack compatibility

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

## License

Impetus is licensed under the [GNU Lesser General Public License version 3](LICENSE), as it only uses code
from Iris 1.7, Sodium 0.5.11 and earlier, Celeritas, and other FOSS projects.

This project does not include, and has no plans to include, any code from Sodium 0.6+ or 0.5.12+, as those
versions are not available under a free and open-source license. Features inspired by modern Sodium are
independent, original implementations.

## Credits

* **embeddedt**, for developing Celeritas and Embeddium, from which Impetus is forked
* **The CaffeineMC team**, for developing Sodium 0.5.11 and older, and making it open source
* **The Iris project and the Oculus port**, the origin of the bundled shader pipeline
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
* **Asek3**, for developing Rubidium, the original port of Sodium 0.5 to Forge
* **CelestialAbyss**, for developing the Embeddium logo, and **input-Here** for visual touchups
* **Ven ([@basdxz](https://github.com/basdxz))**, for help with translucency sorting, suggesting the general approach for async occlusion culling, and other suggestions during development
* **XFactHD**, **Pepper**, and anyone else omitted here, for valuable code insights

[![YourKit logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
