# PeroxiCore

[![GitHub release](https://img.shields.io/github/v/release/EmmmM9O/mindustry-peroxicore?sort=semver&label=release)](https://github.com/EmmmM9O/mindustry-peroxicore/releases)
[![GitHub issues](https://img.shields.io/github/issues/EmmmM9O/mindustry-peroxicore?label=issues)](https://github.com/EmmmM9O/mindustry-peroxicore/issues)
[![License](https://img.shields.io/github/license/EmmmM9O/mindustry-peroxicore?label=license)](https://github.com/EmmmM9O/mindustry-peroxicore/blob/main/LICENSE)
[![JitPack](https://jitpack.io/v/EmmmM9O/mindustry-peroxicore.svg)](https://jitpack.io/#EmmmM9O/mindustry-peroxicore)

[English](README.md) | **中文**

PeroxiCore —— 领航 Mindustry 模组开发的 Kotlin 先锋。以强悍之力拓界，让每一分想象力直抵星辰。

---

## 模块

| 模块 | 说明 |
| --- | --- |
| `annotations` | 实用用户的注解集合 |
| `ksp` | KSP 处理器。 |
| `compiler-plugin` | K2 编译器插件（FIR + IR）。 |
| `core` | Core 模块。 |
| `ponder` | [Ponder](https://create.fandom.com/wiki/Ponder) 风格的游戏内场景：用声明式 DSL 在运行的游戏中搭建并讲解方块。 |

### 计划中

| 模块 | 说明 |
| --- | --- |
| `lwjgl` | LWJGL 模块。 |
| `graphics` | 构建在 `lwjgl` 之上的高层图形与着色器抽象。 |

---

## 使用

### 1. 添加 JitPack 仓库

PeroxiCore 通过 JitPack 发布，在根构建脚本中加入：

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
```

然后按需引入模块：

```kotlin
dependencies {
  compileOnly("com.github.emmmM9O.mindustry-peroxicore:core:main-SNAPSHOT")
  compileOnly("com.github.emmmM9O.mindustry-peroxicore:ponder:main-SNAPSHOT")

  // 仅编译期
  ksp("com.github.emmmM9O.mindustry-peroxicore:ksp:main-SNAPSHOT")
  kotlinCompilerPluginClasspath("com.github.emmmM9O.mindustry-peroxicore:compiler-plugin:main-SNAPSHOT")
}
```


### 2. 声明模组

`@ModConfig` 让你完全不用手写 `mod.json`——KSP 处理器会从注解生成，自动补上 `main`、版本号和
`minGameVersion`。

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

### 3. 启用隔离类加载器

在模组类上加 `@ImportPeroxiCore`，会生成一个 `peroxicore.json` 标记。游戏加载带该标记的模组时，
`PXCPlatform` 会给它一个隔离类加载器，优先加载peroxide里面加载的类，而不是沿用调用方的 parent。

```kotlin
@ModConfig(name = "mycore")
@ImportPeroxiCore
class MyMod : Mod()
```

### 4. 用 Ponder 描述一个方块

`ponder` 提供声明式的场景 DSL。时间线以 tick 为索引，每个关键帧就是一个普通的 Kotlin lambda：

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
      (9 at 5).label("电解机", 4f)
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

坐标写作 `(x at y)`，区域写作 `(range by range)`，完整语法见 `PositionDsl`。场景运行在世界的一份
沙盒副本上（`PonderGroups` 会替换全部 `Groups` 表以及 `freeQueue`），因此不会碰到玩家真正的存档。

---

## 环境要求

- JDK 25（`jvmToolchain(25)`）
- Kotlin 2.4.0（`gradle.properties` 中的 `kotlinVersion`）
- 以 Mindustry v160.4 作为 `compileOnlyApi` 目标

---

## 文档

- **TODO**：模块结构、注解流水线与 Ponder DSL 的完整文字教程仍在编写中。
- **TODO**：面向 Java 的 API 文档尚未生成。

---

## 许可

见 [LICENSE](LICENSE)。

---

<div align="center">

为 Mindustry 模组社区而做。

</div>
