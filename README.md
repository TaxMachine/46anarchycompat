# AnarchyCompat — LWJGL3 / Java 21 shims (Forge 1.7.10)

By TaxMachine (`dev.taxmachine.anarchycompat`).

Template for per-mod compatibility shims for the 46Anarchy pack under the
experimental runtime (lwjgl3ify 3.0.38 + UniMixins + Java 21). Mirrors the
proven FS-Patches toolchain: **Gradle 4.6 + GTNewHorizons ForgeGradle 1.2.11
+ official Forge 10.13.4.1614 + Mixin 0.7.11** (refmap + reobf), Java 8.

## Prérequis

- **Java 8 JDK** to run Gradle/FG (`JAVA_HOME` → Java 8; the game itself
  uses whatever the launcher provisions).
- Drop the pack jars + `unimixins-all.jar` into `mods/` (see `mods/README.md`).

## Build

```sh
./gradlew setupDecompWorkspace   # one-time FG deobf setup (~5-10 min)
./gradlew build                  # → build/libs/anarchycompat-*.jar
```

Verified 2026-10-13: `setupDecompWorkspace` green in 58 s, `build` green
(jar contains `@Mod` + coremod + example mixin, `FMLCorePlugin` +
`MixinConfigs` manifest, refmap, expanded `mcmod.info`). Toolchain used:
Temurin 8u504 + Gradle 4.6 (wrapper generated, committed). One gotcha
already fixed in `buildscript/forge-1.7.gradle`: `mcmod.info` expansion
needs an explicit `project.ext.mcversion`.

Drop the built jar in the instance `mods/` (experimental runtime only —
it needs UniMixins + lwjgl3ify present).

## Releases

Publishing a GitHub release runs CI (`.github/workflows/release.yml`),
which rebuilds from scratch and attaches `anarchycompat-<version>.jar`
to that release. The 46Anarchy launcher pins per-release asset URLs, so
no code changes are needed on this side — just publish.

## Writing shims (lwjgl3ify test matrix)

P0 shims already in the template (verified green 2026-10-13):
- `CompatCorePlugin` + `ShadedMixinGuardTransformer` (P0-1): detects the
  shaded Mixin 0.6-era `MixinServiceLaunchWrapper` copies inside
  `PalaMixins`/`PalaJobs`/`fspatches`, warns on classic Java 8, and fails
  with an actionable message (not a cryptic CCE) when the legacy cast
  would load on Java 9+.
- `mixin/lwjgl3/MixinOptiFineConfigDisplay` (P0-2, optional config
  `anarchycompat-lwjgl3.json`, `required: false`): intercepts OptiFine
  `Config.checkDisplaySettings/checkDisplayMode` so the boot matrix can
  prove whether lwjgl3ify covers the `Display.create(PixelFormat)` path;
  promote to `@Redirect` if red. Needs `optifine-prod.jar` in `mods/`
  for the annotation processor to resolve `targets = "Config"`.
- No-touch rule: never transform `Minecraft`, `EntityRenderer`,
  `SoundManager`, `Shader` (owned by PalaMod/PalaMixins/fspatches mixins)
  or duplicate PalaKeybind's `GameSettings`/`KeyBinding` mixins.
- P0-4 (BLOCKED, upstream): PalaMod's `MixinLoader` reflective transformer
  grab cannot be neutralised at runtime (proven: mixin classes bypass
  `IClassTransformer`, plugin class defines too early). The
  `ShadedMixinGuardTransformer` keeps the rewrite code as documented
  defense-in-depth; the shippable fix is Paladium replacing the hack with
  `ILateMixinLoader`. See `../boottest/MATRIX.md`.
- Manifest hygiene: this jar declares `MixinConfigs` but deliberately NO
  `TweakClass` (ships no Mixin classes; UniMixins provides the tweaker).

1. Boot the experimental runtime, crash, read the first `Caused by`.
2. Classify it (see lwjgl3ify README "Fixing mods"):
   - `ClassCastException: AppClassLoader cannot be cast to URLClassLoader`
     → coremod transformer rewriting the cast to `getProtectionDomain()`
     access (register in `CompatCorePlugin#getASMTransformerClass`), or a
     Mixin redirect if the call site is patchable.
   - `IllegalAccessException` on `Field.modifiers` / final writes →
     Mixin `@Mutable` / accessor, or Unsafe bootstrap like lwjgl3ify docs.
   - `Display`/`Keyboard`/`Mouse` internals → redirect to the lwjgl3ify
     `org.lwjglx` shim surface; check the modlab graph
     (`../graphify-out/`) for every touch point first:
     `graphify query "which <Mod> classes reference org.lwjgl.Display"`.
   - `palaforge`-only behavior → compare against official 1614 sources;
     shim the delta, don't fork the fork.
3. High-risk mods first: `Paladium-UI`, `Zephyr-UI`, `PalaVanillaGui`,
   `PalaVoiceChat`, `PalaMixins`, `optifine-prod` (HD_U_E7), `fspatches`.
4. Keep each shim behind its own Mixin class + config entry so failures
   bisect to one file.

## Why UniMixins but Mixin 0.7.11 manifests

The jar keeps FS-Patches' manifest shape (`MixinConfigs` +
`MixinTweaker`, `FMLCorePluginContainsFMLMod`) so it loads on the classic
Java 8 path too; under UniMixins (experimental runtime) the same config
is hosted by UniMixins without changes. Never embed a Mixin jar —
duplicate Mixin copies break both hosts.
