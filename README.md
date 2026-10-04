# Tinker's For Gregtech 6

Bridges GregTech 6 materials into Tinker's Construct: registers every GT6 tool material as a
TConstruct tool material and exposes GT6 material enchantments as an always-on material trait.

Based on the [GTNH ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) build template.

<!-- omit in toc -->
### Table of Contents

* [Tinker's For Gregtech 6](#tinkers-for-gregtech-6)
    * [Motivation](#motivation)
    * [Help! I'm stuck!](#help-im-stuck)
    * [Requirements](#requirements)
    * [Getting started](#getting-started)
    * [Features](#features)
    * [Files](#files)
    * [Forge's Access Transformers](#forges-access-transformers)
    * [Mixins](#mixins)
    * [Advanced](#advanced)
    * [Feedback wanted](#feedback-wanted)


### Motivation

We had our fair share in struggles with build scripts for Minecraft Forge. There are quite a few pitfalls from non-obvious error messages. This Example Project provides you a build system you can adapt to over 90% of Minecraft Forge mods and can easily be updated if need be.

### Help! I'm stuck!

We all have been there! Check out our [FAQ](docs/FAQ.md). If that doesn't help, please open an issue.

### Requirements

Install these next to `Tinker's For Gregtech 6` in the `mods` folder, in this order of loading:

| Mod | Version | Why |
| --- | --- | --- |
| [GregTech 6](https://gregtech.mechaenetia.com/) | 6.17.06 | Every material, stat and tool behaviour this addon forwards comes from GT6. Hard requirement: we depend on `gregapi`. |
| [Tinker's Construct](https://github.com/GTNewHorizons/TinkersConstruct) (GTNH fork) + Mantle | 1.14.117-GTNH / 0.5.4 | Consumes everything we register. Hard requirement, loads after us. |
| [UniMixins](https://github.com/LegacyModdingMC/UniMixins) | 0.3.1 or newer | **Hard requirement.** Two features - the GregTech battery upgrade and MazeBreaker's mining speed - reach into TConstruct classes, which only a mixin can do, and there is no fallback for either. |
| TwilightForest | any | Optional. Without it MazeBreaker has nothing to be fast on and the trait stays dormant. |
| Iguana Tweaks for Tinker's Construct | any | Optional. Adds eight harvest levels, which we scale GT6 material qualities up into. |

UniMixins ships no mod id, so Forge's dependency check cannot ask for it and would let you start without it - nothing
would even warn. We refuse the launch instead and say what is missing. It is declared in `dependencies.gradle` too, and
as a relation on CurseForge / Modrinth, so a pack install asks for it as well. Set `requireUniMixins=false` in
`config/tinkersforgt6.cfg` only if you really want to run without those two features; everything that can be done with
plain Forge events still works then.

### Getting started

Requires a JDK 8 toolchain (Corretto 1.8 is what this project is developed against).

1. CoFH's jars are not served by any live Maven repository, so they are resolved from `mavenLocal()`.
   On a fresh machine `dependencies.gradle` documents the `mvn install:install-file` commands needed
   to install `CoFHLib` and `CoFHCore` — without them the build fails with
   `Could not find cofh:CoFHLib:...`.
2. Spotless formats `mcmod.info` with prettier and needs npm. On Windows its auto-detection fails, so
   either put `npmExec=C:/Program Files/nodejs/npm.cmd` into `~/.gradle/gradle.properties` or pass
   `-PnpmExec=<path to npm.cmd>` to gradlew.
3. Run `./gradlew build` to produce `build/libs/tinkersforgt6-<version>.jar`.
4. `./gradlew runServer` starts a dedicated server, `./gradlew runClient` the game.
   Note that the client needs a working OpenGL install — see [FAQ](docs/FAQ.md).

### Features

 - Updatable: Replace [`build.gradle`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/build.gradle) with a newer version
 - Optional API artifact (.jar)
 - Optional version replacement in Java files
 - Optional shadowing of dependencies
 - Simplified setup of Mixin and example
 - Scala support (add sources under `src/main/scala/` instead of `src/main/java/`)
 - Optional named developer account for consistent player progression during testing
 - Boilerplate forge mod as starting point
 - Improved warnings for pitfalls
 - Git Tags integration for versioning
 - [Jitpack](https://jitpack.io) CI
 - GitHub CI:
   - Releasing your artifacts on new tags pushed. Push git tag named after version (e.g. 1.0.0) which will trigger a release of artifacts with according names.
   - Running smoke test for server startup. On any server crash occurring workflow will fail and print the crash log.

### Files
 - [`build.gradle`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/build.gradle): This is the core script of the build process. You should not need to tamper with it, unless you are trying to accomplish something out of the ordinary. __Do not touch this file! You will make a future update near impossible if you do so!__
 - [`gradle.properties`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/gradle.properties): The core configuration file. It includes
 - [`dependencies.gradle[.kts]`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/dependencies.gradle): Add your mod's dependencies in this file. This is separate from the main build script, so you may replace the [`build.gradle`](https://github.com/SinTh0r4s/ExampleMod1.7.10/blob/main/build.gradle) if an update is available.
 - [`repositories.gradle[.kts]`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/repositories.gradle): Add your dependencies' repositories. This is separate from the main build script, so you may replace the [`build.gradle`](https://github.com/SinTh0r4s/ExampleMod1.7.10/blob/main/build.gradle) if an update is available.
 - `addon.gradle[.kts]`: Any additional build logic. This is separate from the main build script, so you may replace the [`build.gradle`](https://github.com/SinTh0r4s/ExampleMod1.7.10/blob/main/build.gradle) if an update is available. See [Advanced](#advanced) for more details.
 - [`jitpack.yml`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/jitpack.yml): Ensures that your mod is available as import over [Jitpack](https://jitpack.io).
 - [`.github/workflows/gradle.yml`](https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/main/.github/workflows/gradle.yml): A simple CI script that will build your mod any time it is pushed to `master` or `main` and publish the result as release in your repository. This feature is free with GitHub if your repository is public.

### Forge's Access Transformers

You may activate Forge's Access Transformers by defining a configuration file in `gradle.properties`.

Check out the [`example-access-transformers`](https://github.com/GTNewHorizons/ExampleMod1.7.10/tree/example-access-transformers) branch for a working example!

> [!WARNING]
> Access Transformers are bugged and will deny you any sources for the decompiled Minecraft! Your development environment will still work, but you might face some inconveniences. For example, IntelliJ will not permit searches in dependencies without attached sources.

### Mixins

[Mixins](https://github.com/SpongePowered/Mixin) are used to modify vanilla or mod/library code during runtime without having to edit, recompile, and redistribute the original code. For example, mixins can change a hardcoded value, redirect a method call, inject additional code, access private fields/methods, make a class implement your interface, and more. Mixins are an advanced feature which most normal mods will not require.

Documentation about Mixin features can be found here: [Mixin Wiki](https://github.com/SpongePowered/Mixin/wiki) and [MixinExtras Wiki](https://github.com/LlamaLad7/MixinExtras/wiki)

There are many examples of mixins in these mods: [Hodgepodge](https://github.com/GTNewHorizons/Hodgepodge) and [Angelica](https://github.com/GTNewHorizons/Angelica)

To enable Mixins in your project, follow one of the example commits:
- use [normal mixins](https://github.com/GTNewHorizons/ExampleMod1.7.10/commit/beba55615fa8337b7639f0d5b18db6cc8d4826be) for basic and quick registration
- use [GTNH IMixins](https://github.com/GTNewHorizons/ExampleMod1.7.10/commit/055cd4f18765a421a86c706f53b62116988297e3) (recommended) for the same thing as below, but in a less verbose and more unified manner using the IMixins api
- use [GTNH Early/Late mixins](https://github.com/GTNewHorizons/ExampleMod1.7.10/commit/c4df59d92164775b69451f3e690239e93d1fc979) to have full control over the registration logic and check for presence of other mods during runtime to load your mixins

This mod takes the third route: UniMixins' GTNHMixins module discovers `com.tinkersgt6.mixin.TinkersGT6LateMixins`
through its `@LateMixin` annotation, which is the only phase whose targets include another mod's classes. That is also
why UniMixins is listed under [Requirements](#requirements) instead of a recommendation - see `dependencies.gradle` for
the dependency and `gradle.properties` (`usesMixins`) for why the build script's own mixin switch stays off.

The extra required dependencies are handled automatically after mixins are enabled.

### Advanced

If your project requires custom gradle commands you may add a `addon.gradle[.kts]` to your project. It will be added automatically to the build script. Although we recommend against it, it is sometimes required. When in doubt, feel free to ask us about it. You may break future updates of this build system!
If you need access to properties modified later in the buildscript, you can also use a `addon.late.gradle[.kts]`.
For local tweaks that you don't want to commit to Git, like adding extra JVM arguments for testing, use `addon[.late].local.gradle[.kts]`.

### Feedback wanted

Build system originally written by [SinTh0r4s](https://github.com/SinTh0r4s), [TheElan](https://github.com/TheElan) and [basdxz](https://github.com/basdxz).
