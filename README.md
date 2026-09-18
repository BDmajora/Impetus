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

Impetus targets **Minecraft 1.12.2** on **Minecraft Forge** (developed against 14.23.5.2864) running on
**Java 8** with LWJGL 2. Drop the jar into your `mods` folder.

Impetus **does not require MixinBooter**. It bundles its own Mixin implementation and brings it up only when
no other Mixin provider is present. If MixinBooter (or any other Mixin service) is already installed, Impetus
detects it during coremod construction and defers to it entirely, contributing no `org.spongepowered.asm`
classes of its own. Either arrangement works with no configuration.

### Reporting Issues

Please open an issue on the [project issue tracker](https://github.com/bdmajora/impetus/issues). Crash
reports and the full launcher log are far more useful than a description alone.

## What's included

Impetus ships as a single jar containing several subsystems, each with its own mixin configuration:

| Module | Purpose |
| :----- | :------ |
| `impetus` | The core rendering engine and chunk pipeline |
| `umbra` | The bundled shader pipeline, descended from Iris / Oculus |
| `fulgor` | Lighting engine, superseding Phosphor and Alfheim: a deferred Phosphor-style engine with parallel light passes in the manner of ScalableLux, and a Starlight-style asynchronous engine after Pulsar that lights chunks on worker threads and persists their light |
| `coarctatio` | Memory and allocation reductions across vanilla systems, including FerriteCore's block-state and model deduplication techniques, lazy item capabilities, resource lookup caches, a between-launch mod scan cache, parallel texture decoding, a primitive ore dictionary, compacted remapper caches and opt-in on-demand model loading with a pack-scan atlas |
| `equilibrium` | Chunk and world access caching, world generation fast paths, and the server-side tick optimizations: advancement triggers, crafting and furnace caches, ghost-chunk guards, spawn preload skipping and the opt-in tick budget |
| `dynamiclights` | Dynamic light sources for held and dropped items |
| `extras` | Optional feature toggles: the Render Budget and GPU Booster pair for weaker machines, parallel server ticking, parallel particles, baked block entities, ray-cast occlusion culling, HUD caching, text batching, network flush consolidation, wire limits and thread scheduling |

Impetus suppresses the mixin configurations of superseded lighting mods (Phosphor, Alfheim) when it detects
them, since running two lighting engines at once corrupts world lighting. Remove those mods rather than
relying on the suppression.

## Building from sources

Impetus uses the [Gradle build tool](https://gradle.org/). The
[Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is
provided and will download the correct Gradle version automatically.

```
./gradlew packageJar
```

The finished jar is written to `build/libs/`. A helper script is also provided:

```
./run.sh
```

which offers **Build**, **Clean**, and **Quit**. It also works non-interactively as `./run.sh build` and
`./run.sh clean`. Note that *Clean* removes every build output directory, not just `build/`, so the next
build re-decompiles Minecraft and takes several minutes.

### Build Requirements

- **OpenJDK 21** to run the build. The mod itself is compiled to a Java 8 target via
  [Jabel](https://github.com/bsideup/jabel) and [JvmDowngrader](https://github.com/unimined/JvmDowngrader),
  so modern syntax is used in the sources while the output still runs on Java 8.
- No separate Gradle installation is needed; use the wrapper.

### Project layout

| Path | Contents |
| :--- | :------- |
| `src/` | The Forge 1.12.2 mod: engine glue, mixins, and the bundled Mixin bootstrap |
| `common/` | Platform-agnostic renderer code, compiled at Java 17 and downgraded to 8 |
| `common/build-logic/` | Gradle build logic: the mixin-aware reobfuscator and the LWJGL abstraction generator |
| `common/src/booterLibs/` | Service declarations for the bundled Mixin implementation |
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
* **Rongmario**, for MixinBooter, the basis of the bundled Mixin bootstrap
* **Mumfrey**, for creating the Mixin bytecode patching system, and **CleanroomMC** for CleanMix
* **LlamaLad7**, for MixinExtras
* **orf**, for GpuShift (MIT), whose adaptive render-budget design the Extras page's Render Budget group reimplements for 1.12.2
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
