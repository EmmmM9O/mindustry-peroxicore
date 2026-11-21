package peroxicore.ciallopoet

/** The statement a chain spells, `build @Blocks.duo at(4, 3) config null`. */
fun CialloChainSpec.asStatement(): CialloCodeBlock = CialloCodeBlock.of("%L", this)

/** A chain that starts at [path] and is configured by [builderAction]. */
fun chain(path: String, builderAction: CialloChainSpec.Builder.() -> Unit): CialloChainSpec =
  CialloChainSpec.builder(path).apply(builderAction).build()

/** A chain that starts at the name literal [path], `@x`. */
fun symbolChain(path: String, builderAction: CialloChainSpec.Builder.() -> Unit): CialloChainSpec =
  CialloChainSpec.symbolBuilder(path).apply(builderAction).build()

/** `name` as a lone statement. */
fun statement(path: String): CialloCodeBlock = CialloCodeBlock.of("%L", CialloChainSpec.of(path))

/** `name args...` as a lone statement. */
fun callStatement(path: String, vararg args: Any?): CialloCodeBlock =
  CialloCodeBlock.of("%L", CialloChainSpec.call(path, *args))

/** `@name value` as a lone statement, such as `@x set 4`. */
fun setStatement(name: String, value: Any?): CialloCodeBlock =
  CialloCodeBlock.of("%A set %L", name, value)

/** `@name local value`. */
fun localStatement(name: String, value: Any?): CialloCodeBlock =
  CialloCodeBlock.of("%A local %L", name, value)

/** `@name mount value`. */
fun mountStatement(name: String, value: Any?): CialloCodeBlock =
  CialloCodeBlock.of("%A mount %L", name, value)
