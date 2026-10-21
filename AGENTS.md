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

Keep changes narrow, preserve behavior unless the task explicitly requires otherwise, and avoid style-only churn.

## Root Treemap

- `core/`, `compiler-plugin/`, `annotations/`, `ksp/`: main source modules.
- `lwjgl/`, `ponder/`: integration/render/utility modules.
- `img/`: image assets.
- `buildSrc/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`: build logic.
- `tools.lua`, `xmake.lua`: optional xmake tooling.
- `tmp/`: local scratch space and cached references (see below).
- `build/`: generated outputs. **Do not edit by hand.**

## Uncertain APIs

When you are not certain about an API's signature, behavior, or availability:
- "Certain" means you can cite the exact signature from memory or from a file already read in this session. If you would need to check anything, you are not certain.

1. **First**, look under `tmp/` for cached API references Search by class or method name.
2. **If found**, use that as the source of truth. Do not open `.jar` files directly.
3. **If not found**, **stop and ask the user** before proceeding. State which API you are unsure about and what you were trying to do.

**Never** inspect `.jar` files directly (no `unzip`, `jar tf`, `javap`, decompilation, or reading class files). If `tmp/` does not contain the answer, asking the user is the correct next step — do not guess and do not fall back to jar inspection.

Remember If there is real ambiguity, ask instead of guessing.

## Build Commands

You just need to make sure that the changes can be built.
Do not use `deploy`.Just use `compileKotlin`.

## Core rules

- Prefer minimal diffs. Do not rename, reorder, or reformat unrelated code.
- Prefer wildimport always for any number of symbols, even a single one unless a wildcard would introduce a name collision or ambiguity
- Leave unrelated user changes and untracked artifacts alone.

## Avoid

- Do not remove any file without agreement.
- Do not guess API signatures; follow the "Uncertain APIs" flow above.
