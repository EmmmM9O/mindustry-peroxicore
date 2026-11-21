---
module: core
languages: [Kotlin, Java]
build-system: Gradle
---

# core/AGENTS.md

`core` 是 PeroxiCore 的主模块：基础工具、文件/图形/输入封装、UI、`peroxicore.struct`、日志系统，以及脚本语言 Ciallo。

根目录 `AGENTS.md` 的规则在这里同样适用（构建命令、Uncertain APIs、最小 diff、通配导入等）。

## 目录导航

- `src/peroxicore/ciallo/` — Ciallo 解释器、环境、作用域、格式化器、CLI。
- `src/peroxicore/ciallopoet/` — 生成 Ciallo 源码的 API。
- `src/peroxicore/struct/` — Arc 数据结构的 Kotlin 扩展。
- `src/peroxicore/util/` — 日志等通用工具。
- `src/peroxicore/{files,graphics,input,ui,mod}/` — 各类宿主封装。
- `antlr/Ciallo.g4` — Ciallo 语法（ANTLR 生成到 `peroxicore.parsers`）。
- `test/` — 测试源码（`*Test`）。
- `docs/` — 面向人的模块文档。

构建验证沿用根规则：`./gradlew :core:compileKotlin --offline --console=plain`。

## Ciallo

`core` 内嵌的脚本语言。**只有任务与 Ciallo 相关时**才需要了解它；其余任务（工具、结构、UI、日志等）请直接跳过本节，不要翻阅 Ciallo 代码。

语言设计、语法、值模型、作用域、重载与生成 API 全部写在 **`core/docs/ciallo/ciallo_basics.md`**，先读它。只有文档不足以回答时，再按需查看：

- `core/antlr/Ciallo.g4` — 精确语法
- `core/src/peroxicore/ciallo/CialloInterpreter.kt` — 求值语义
- `core/src/peroxicore/ciallo/value/` — 值成员表
- `core/src/peroxicore/ciallo/ops/` — 运算符与函数组件

约定：

- 扩展能力走组件注册（operator / free function / member table），不要在解释器里按值类型加 `when`。
- 改语法要同步 `Ciallo.g4`、解释器与 `CialloFormatter`（幂等性有测试保证）。
- 生成代码用 `ciallopoet`，不要手拼字符串。
- 改完跑：`./gradlew :core:test --tests 'peroxicore.ciallo.*' --tests 'peroxicore.ciallopoet.*' --offline --console=plain`。
