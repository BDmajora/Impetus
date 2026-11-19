Impetus uses Distant Horizons' native mod-accessor registration for Minecraft 1.12.2.
DH registers `ImpetusAccessor` from `ForgeMain.initializeModCompat()`, then publishes
that accessor through `IRIS_ACCESSOR` and `ModAccessorInjector`. Shader-pack and
shadow-pass queries use Impetus's public Iris API v0 interface. Depth queries use
DH's `IFramebufferDepthTexture` contract.

The matching DH changes are in `patches/distant-horizons-impetus.patch`. Apply them
to the sibling Distant Horizons checkout from the Impetus project directory:

```bash
git -C ../distant-horizons apply --check ../Impetus/patches/distant-horizons-impetus.patch
git -C ../distant-horizons apply ../Impetus/patches/distant-horizons-impetus.patch
```

Rebuild both projects and install their new JARs together:

```bash
./run.sh build
```

In the Distant Horizons project directory:

```bash
./gradlew -PmcVer=1.12.2 :forge112:test :forge112:build
```

DH detects the real Iris framebuffer interface rather than the shared Iris API
entry point. This keeps DH's depth-texture mixin active with Impetus while leaving
depth allocation to Actinium/Demonica when their framebuffer implementation is
present. Impetus no longer injects a proxy into DH's internal registry or queues
DH's framebuffer mixin configuration from its own coremod.

The API facade is an interface, matching existing Iris/Actinium binaries. It
implements the v0 shader-state, shadow-state, configuration and screen contracts.
The shader rendering bridge still uses DH's render events for LOD programs,
framebuffers, shadows and translucent rendering.

Validation: the modified classes compile, four DH accessor/mixin-selection tests
pass, and 21 Impetus/integration tests pass. The integration test runs DH code
compiled against Actinium's Iris interface with Impetus's actual implementation.
Packaged builds and an in-game rendering check still need to run outside the
restricted environment used for this change. Check shader toggling, LOD terrain
and water, LOD shadows, depth fading and framebuffer resizing in game.
