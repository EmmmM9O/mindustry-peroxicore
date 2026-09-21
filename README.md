# PeroxiCore

[![GitHub release](https://img.shields.io/github/v/release/EmmmM9O/mindustry-peroxicore?sort=semver&label=release)](https://github.com/EmmmM9O/mindustry-peroxicore/releases)
[![GitHub issues](https://img.shields.io/github/issues/EmmmM9O/mindustry-peroxicore?label=issues)](https://github.com/EmmmM9O/mindustry-peroxicore/issues)
[![License](https://img.shields.io/github/license/EmmmM9O/mindustry-peroxicore?label=license)](https://github.com/EmmmM9O/mindustry-peroxicore/blob/main/LICENSE)
[![JitPack](https://jitpack.io/v/EmmmM9O/mindustry-peroxicore.svg)](https://jitpack.io/#EmmmM9O/mindustry-peroxicore)

**English** | [中文](README_zh_CN.md)

PeroxiCore — the Kotlin vanguard leading Mindustry modding into new frontiers. Raw power, boundless reach, and imagination aimed at the stars.

---

## Modules

| Module            | Description                                                                                                                                                     |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `annotations`     | The useful annotation set.                                                                                                                                      |
| `ksp`             | KSP processor.                                                                                                                                                  |
| `compiler-plugin` | A K2 compiler plugin (FIR + IR).                                                                                                                                |
| `core`            | Core module.                                                                                                                                                    |
| `ponder`          | [Ponder](https://create.fandom.com/wiki/Ponder) style in-game scenes: a declarative DSL for building, animating and explaining a block inside the running game. |

### Planned

| Module     | Description                                                            |
| ---------- | ---------------------------------------------------------------------- |
| `lwjgl`    | A lwjgl module.                                                        |
| `graphics` | Higher-level graphics and shader abstractions built on top of `lwjgl`. |

---

## Usage

### 1. Add JitPack

PeroxiCore is published through JitPack. Add the repository to your root build script:

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
```

Then depend on the modules you need:

```kotlin
dependencies {
  compileOnlyApi("com.github.emmmM9O:mindustry-peroxicore:core:main-SNAPSHOT")
  compileOnlyApi("com.github.emmmM9O:mindustry-peroxicore:ponder:main-SNAPSHOT")

  // compile time only
  ksp("com.github.emmmM9O.mindustry-peroxicore:ksp:main-SNAPSHOT")
  kotlinCompilerPluginClasspath("com.github.emmmM9O.mindustry-peroxicore:compiler-plugin:main-SNAPSHOT")
}
```

### 2. Declare the mod

`@ModConfig` removes the hand-written `mod.json` entirely — the KSP processor generates it from
the annotation, filling in `main`, version and `minGameVersion` for you.

```kotlin
@ModConfig(
  name = "mycore",
  displayName = "My Core",
  author = "You",
  description = "A PeroxiCore-based mod",
)
class MyMod : Mod() {
  override fun init() {}

  override fun loadContent() {}
}
```

### 3. Opt in to the isolated loader

Adding `@ImportPeroxiCore` on a mod class emits a `peroxicore.json` marker. When the game loads a
mod carrying that marker, `PXCPlatform` hands it an isolated class loader instead of the calling
parent.

```kotlin
@ModConfig(name = "mycore")
@ImportPeroxiCore
class MyMod : Mod()
```

### 4. Describe a block with Ponder

`ponder` provides a declarative scene DSL. The timeline is keyed by tick, and every keyframe is a
normal Kotlin lambda:

```kotlin
Blocks.electrolyzer.register {
  keyframes {
    speed(1f)
    world(13, 11) {
      fillFloor(Blocks.rhyolite)
      (4 at 3).around3().fill(Blocks.rhyoliteVent)
      (8 at 5).focus(5)
    }
    frame(50f) {
      (8 at 5).build(Blocks.electrolyzer)
      (9 at 5).label("Electrolyzer", 4f)
    }
    frame(200f) {
      (4 at 3).focus(5)
      (4 at 3).build(Blocks.ventCondenser).thenChain {
        (4 at 7).build(Blocks.turbineCondenser)
      }.thenChain {
        (4 at 5).build(Blocks.beamNode)
      }
    }
  }
}
```

Positions read as `(x at y)`, regions as `(range by range)` — see `PositionDsl` for the full set.
The scene runs in a sandboxed copy of the world (`PonderGroups` swaps every `Groups` table plus
`freeQueue`), so it never touches the player's actual save.


---

## Requirements

- JDK 25 (`jvmToolchain(25)`)
- Kotlin 2.4.0 (`kotlinVersion` in `gradle.properties`)
- Mindustry v160.4 as the `compileOnlyApi` target

---

## Documentation

- **TODO**: A written guide covering the module layout, the annotation pipeline and the Ponder DSL
  is still being worked on.
- **TODO**: The Java-facing API docs are not generated yet.

---

## License

See [LICENSE](LICENSE).

---

<div align="center">

Made for the Mindustry modding community.

</div>
