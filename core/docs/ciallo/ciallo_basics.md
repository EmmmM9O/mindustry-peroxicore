# Ciallo 基础设计与语法

Ciallo 是嵌在 `core` 里的脚本语言，用于在运行时驱动 Mindustry / Arc 对象。无类型、无声明、换行分句。

- 语法：`core/antlr/Ciallo.g4`
- 解释器：`core/src/peroxicore/ciallo/CialloInterpreter.kt`
- 值：`core/src/peroxicore/ciallo/value/`
- 格式化：`core/src/peroxicore/ciallo/CialloFormatter.kt`
- 生成：`core/src/peroxicore/ciallopoet/`

---

## 核心：链

一条语句就是一个**链**（chain），由若干段组成，折叠成调用树：

```
build @Blocks.duo at(4, 3) config null
└ build（头） └ @Blocks.duo（值） └ at(4,3)（调用） └ config（成员）+ null（值）
```

| 写法 | 含义 |
|---|---|
| `name` | 读名字：作用域变量 → 原生函数 → 注册符号 |
| `name(a, b)` | 调用 |
| `name { ... }` | 带尾随块调用 |
| `name value` | 单值参数，`label "x"` ≡ `label("x")` |
| `@x` | 名字字面量，延迟解析的引用 |
| `@x.m(a)` | 解析 `@x` 后调用其成员 |

**两条文法限制**（容易踩）：

1. 字面量后不能直接接成员：`5s.millis`、`"Hi".lower()`、`[1,2].size`、`1.add(2)` 都**语法错误**。先绑变量：`@t local 5s` 然后 `@t.millis`。
2. 一段完成后只能接成员/调用段，不能再接裸值：`label "a" "b"` 不合法，需换行分行。

## 词法与表达式

- 字面量：`42`、`3.14`、`1f`、`5s`/`1.5m`/`2h`、`"..."`、`"""..."""`、`true`/`false`/`null`、`[1, 2]`
- 字符串支持 `\n \t \" \\ \{ \} \uXXXX`，以及 `{expr}` 插值：`"x = {1 + 2}"` → `x = 3`。字面花括号用 `\{`。
- 优先级：比较 `== != < <= > >=` < `+ -` < `* / %` < 一元 `- !` < `( )` / 链。
- **拼接只在左侧是字符串时**：`"a" + 1 + 2` → `a12`；`1 + 2 + "a"` 报 `unknown operator '+'`。
- 比较左结合，`1 < 2 < 3` 会在运行时对 `true` 求 `<` 而报错，别连写。

## 变量操作

`local` / `mount` / `set` 是名字字面量仅有的成员，也是保留字：

```
@x local 1     // 定义在当前作用域
@x mount 1     // 定义在全局作用域
@x set 8       // 赋值（向外查找已有定义）
```

`@a.set(0, 9)` 有两个参数，因此**不是**变量操作，会派发到数组的 `set` 方法；只有恰好一个参数时才是赋值。

## 值类型

`Nil` `Bool` `Int` `Float` `Time` `Str` `Arr` `Symbol` `Obj` `Block` `Native` `Java`。

Ciallo 没有对象字面量；对象来自宿主注册或 `apply`。成员访问都需先经变量/符号。

- **数字**：`add/sub/mul/div/mod`、`less/lessEq/greater/greaterEq/eq/ne`、`negate`、`toInt/toFloat`，属性 `sign/isZero/isNaN`
- **字符串**：`size/length`、`upper/lower/trim`、`contains/split/startsWith/endsWith`、`get`、`toInt/toFloat`
- **数组**：`size/length/first/last/empty`、`add/get/set/remove/clear`、`join/contains/indexOf`、`map/filter/each`
- **时间**：`millis/seconds/amount/unit`、`toMillis()/toSeconds()`
- **块**：`run/then/apply`（最多一个参数，绑定为 `it`）

比较辅助只有成员形式；自由函数只有 `add/sub/mul/div`。

## 块与作用域

块是携带闭包的一等值，`{ ... }` 单独成句会立即执行；也可先绑定：`@b local { ... }` 再 `@b.run()`。

| 用法 | 绑定 |
|---|---|
| `then{...}` / `run{...}` | 无 |
| `run(v){...}` | `it = v` |
| `repeat(n){...}` / `loop(n){...}` | `it = 0..n-1` |
| `ifTrue(cond){...}` | 条件为真才执行 |
| `loop{...}` | 块返回真值则继续（上限 100 万次） |
| `arr.map{...}` / `filter{...}` / `each{...}` | `it`、`index`、`acc` |
| `obj.apply{...}` | 对象字段成为该作用域变量，可读可写 |

- `local` 定义在当前作用域，重复定义报错；`mount` 总是定义到全局，重复定义报错；`set` 向外查找，未定义报错。
- `loop` 需显式返回条件：

```
@i local 0
loop{
  @i set(@i add(1))
  @i less(3)
}
```

## 重载

内建自由函数：`print` `then` `run` `repeat` `ifTrue` `loop` `not`，以及 `add` `sub` `mul` `div`。

**同名的原生函数、自由函数、成员方法都可以有多个严格重载**，按 `CialloType` 精确匹配；无 `Any` 的更具体者优先；未命中再有精确诊断：

```
function 'at' has no overload for (Int, Float); expected (Int, Int) or (Float, Float)
```

```kotlin
env.native("at", listOf(CialloType.Int, CialloType.Int)) { _, _ -> CialloStr("point") }
env.native("at", listOf(CialloType.Float, CialloType.Float)) { _, _ -> CialloStr("vec2") }
// at(1, 2) -> point    at(1f, 2f) -> vec2    at(1, 2f) -> 报错
```

- 原生函数：`env.native(name, parameters) { ... }`
- 自由函数组件（含内建）的追加重载：`env.freeFunction(name, parameters) { ... }`，不会替换同名内建
- 成员方法：`memberTable { method("mix", listOf(Int, Int)) { ... } }`，未命中回落到无类型 `method`

## 扩展与接入

扩展点（都通过 `CialloOperations` 注册，解释器不按值类型分支）：

`CialloBinaryOperator` / `CialloPrefixOperator`（运算符）、`CialloFreeFunction`（顶层函数）、`CialloMemberTable` / `CialloMemberHandler`（成员）、`CialloNumberSuffixParser`（字面量后缀）、`CialloNameResolver`（兜底名字解析）。

```kotlin
val environment = CialloEnvironment(output = { println(it) })
environment.symbol("Blocks.duo", CialloStr("duo"))
CialloEngine(environment).execute("""print("duo = " + @Blocks.duo)""")
```

- `CialloSandbox.SAFE` 只有内建与注册符号；`FULL` 额外允许 `CialloJavaBridge` 反射 JVM。
- `CialloRuntime` 管理多个隔离环境（独立作用域、符号、沙箱）。

## 格式化与生成

`CialloFormatter.format(source)` 是唯一规范排版，CLI：`ciallo fmt [-w|-c] [--indent n] <file>`、`ciallo run <file|-e src>`、`ciallo console`。

规范风格：一句一行、两空格缩进、块前一个空格、调用括号前不加空格、逗号后一空格、空块写作 `then {}`、格式化幂等。

用 `ciallopoet` 生成脚本（`CialloCodeBlock` / `CialloBlockSpec` / `CialloChainSpec` / `CialloFileSpec` / `CialloLiterals` / `CialloNameAllocator`），输出天然符合上述风格：

```kotlin
val chain = CialloChainSpec.builder("build")
  .addValueArgument("%A", "Blocks.duo")   // %A 自带 @，传名字
  .addCall("at", 4, 3)
  .addMember("config")
  .addValue(null)
  .build()

val file = CialloFileSpec.builder("Demo")
  .addStatement("print %S", "start")      // %S 加引号，%L 原样
  .addStatement(chain)
  .build()
```

输出：

```
print "start"
build @Blocks.duo at(4, 3) config null
```
