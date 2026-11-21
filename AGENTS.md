---
project: Kotlin
languages: [Kotlin, Java]
build-system: Gradle
---

# AGENTS.md

PeroxiCore is a library for Mindustry Mod development containing:
- Core Basical functions
- Lwjgl 
- Ponder
- Ksp
- Compiler Plugin A K2 compiler plugin that deal with `@MethodEntry`
- Annotations

For Mindustry and its engine Arc and Lwjgl its source can be found in `tmp/`

`README.md` is useless for you.Do not read unless the task require.

In order to save token,do not read my source only to imitate my style.

## Root Treemap

- `core/`, `compiler-plugin/`, `annotations/`, `ksp/`: main source modules.
- `lwjgl/`, `ponder/`: integration/render/utility modules.
- `img/`: image assets.
- `buildSrc/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`: build logic.
- `tools.lua`, `xmake.lua`: optional xmake tooling.
- `tmp/`: local scratch space and cached references (see below).
- `build/`: generated outputs. **Do not edit by hand.**

## Module Instructions

Read a module's `AGENTS.md` only when the task touches that module.

- `core/AGENTS.md` — the `core` module: Ciallo (the embedded script language) and its
  `ciallopoet` code generator, `peroxicore.struct`, logging and other host wrappers.
  Read `core/docs/ciallo/ciallo_basics.md` only when the task is actually about Ciallo.

## Uncertain APIs

When you are not certain about an API's signature, behavior, or availability:
- "Certain" means you can cite the exact signature from memory or from a file already read in this session. If you would need to check anything, you are not certain.

1. **First**, look under `tmp/` for cached API references Search by class or method name.
2. **If found**, use that as the source of truth. Do not open `.jar` files directly.
3. **If not found**, **stop and ask the user** before proceeding. State which API you are unsure about and what you were trying to do.

**Never** inspect `.jar` files directly (no `unzip`, `jar tf`, `javap`, decompilation, or reading class files). If `tmp/` does not contain the answer, asking the user is the correct next step — do not guess and do not fall back to jar inspection.

Remember If there is real ambiguity, ask instead of guessing.

## UniverseKit Reflect

`universe.util.reflect.*`（UniverseKit 1.4）用来读写 Arc/Mindustry 的私有、final 成员。完整 API 见 `tmp/Reflection.kt`（`UniverseKit-1.4/reflection` 的副本）。

静态字段（`accessField` 返回 `FieldAccessorStatic`，可读可写）：

```kotlin
var events: ObjectMap<Any, Seq<Cons<*>>> by Events::class.accessField("events")
```

实例字段（返回 `FieldAccessor`，同样可读可写；注意是扩展属性，接收者是目标类型）：

```kotlin
var TargetType.field: T by accessField("field")
```

方法用 `accessMethod1..20`、构造用 `accessConstructor1..20`。

要点：

- **final 也能写。** 访问器在初始化时会对 final 字段调用 `makeMutable`，把修饰符里的 `FINAL` 去掉（desktop / desktop9 都是 `modifiers and FINAL.inv()`），所以把委托属性声明成 `var` 后可以直接赋值，`private static final` 也一样。`EventsHandler` 替换 `Events.events` 表就是这么做的。
- `private` 不受限制，但字段类型必须和声明的 `T` 兼容，否则构造访问器时直接抛 `IllegalArgumentException`。
- 静态和实例是两个不同的工厂：`KClass<*>.accessField(name)` 是静态版；`accessField<O, T>(name)` / `accessField(property)` 是需要接收者的实例版，只能写在带该接收者的类/对象里。用错会抛 `IllegalArgumentException`。
- 字段查找只在初始化时做一次，之后走缓存的访问器；但每次读写的成本仍高于直接访问，别放进每帧热路径。

## Build Commands

You just need to make sure that the changes can be built.
Do not use `deploy`.Just use `compileKotlin`.

## Core rules

- Prefer minimal diffs. Do not rename, reorder, or reformat unrelated code.
- Prefer wildimport always for any number of symbols, even a single one unless a wildcard would introduce a name collision or ambiguity
- Leave unrelated user changes and untracked artifacts alone.

## Avoid

- Write smoke test without request by user
- Do not remove any file without agreement.
- Do not guess API signatures; follow the "Uncertain APIs" flow above.
- **Never mutate source code to "verify" tests.** Do not deliberately break implementation files to confirm that tests catch it (mutation testing, fault injection, commenting out logic, flipping conditions, stubbing a branch). Validation means running the build and the test suite and reporting the actual result. If a test's effectiveness is genuinely in doubt, say so in prose or ask the user — never damage the working tree to find out.
