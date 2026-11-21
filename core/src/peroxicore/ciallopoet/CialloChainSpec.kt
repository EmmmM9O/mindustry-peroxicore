package peroxicore.ciallopoet

/**
 * The atom a chain starts from.
 *
 * Ciallo's grammar allows a chain to begin with a name, a name literal such as `@Blocks.duo`, a
 * literal, or a block. Everything after the first segment is a [CialloChainStep].
 */
sealed interface CialloChainBase {
  /** A possibly dotted name, `build` or `obj.apply`. */
  data class Name(val path: String) : CialloChainBase

  /** A name literal, `@x` or `@Blocks.duo`. */
  data class Symbol(val path: String) : CialloChainBase

  /** A value literal, `1`, `"text"`, `[1, 2]` or a [peroxicore.ciallo.value.CialloValue]. */
  data class Literal(val value: Any?) : CialloChainBase

  /** A block used as a value, `{ ... }`. */
  data class Block(val block: CialloBlockSpec) : CialloChainBase
}

/** One segment after the first: ` name`, ` name(args)`, ` name { ... }` or ` name value`. */
sealed interface CialloChainStep {
  /** A bare member read, `config` in `build @Blocks.duo config`. */
  data class Member(val name: String) : CialloChainStep

  /**
   * A call step.
   *
   * [bare] writes a lone argument without parentheses, the canonical `label "text"` shape, while
   * the default writes `label("text")`. [block] is a trailing `{ ... }`.
   */
  data class Call(
    val name: String,
    val args: List<CialloCodeBlock>,
    val block: CialloBlockSpec? = null,
    val bare: Boolean = false,
  ) : CialloChainStep
}

/**
 * One Ciallo expression: a head segment followed by member or call steps.
 *
 * This mirrors the interpreter's call tree, so `build @Blocks.duo at(4, 3) config null` is a single
 * chain: the head `build`, the value argument `@Blocks.duo`, the call `at(4, 3)`, then the member
 * `config` with the value `null`. [toString] emits the spelling Ciallo's formatter normalises to.
 */
class CialloChainSpec
private constructor(
  val base: CialloChainBase,
  val headArguments: List<CialloCodeBlock>,
  val headBlock: CialloBlockSpec?,
  val headBare: Boolean,
  val steps: List<CialloChainStep>,
) {
  fun toBuilder(): Builder = Builder(base).also { builder ->
    builder.headArguments += headArguments
    builder.headBlock = headBlock
    builder.headBare = headBare
    builder.steps += steps
  }

  internal fun emitInto(writer: CialloCodeWriter) {
    when (val head = base) {
      is CialloChainBase.Name -> writer.emit(CialloLiterals.nameLiteral(head.path))
      is CialloChainBase.Symbol -> writer.emit(CialloLiterals.symbolLiteral(head.path))
      is CialloChainBase.Literal -> writer.emit(CialloLiterals.literal(head.value))
      is CialloChainBase.Block -> head.block.emit(writer)
    }
    emitArguments(writer, headArguments, headBlock, headBare)

    for (step in steps) {
      when (step) {
        is CialloChainStep.Member -> writer.emit(" ").emit(CialloLiterals.nameLiteral(step.name))
        is CialloChainStep.Call -> {
          writer.emit(" ").emit(CialloLiterals.nameLiteral(step.name))
          emitArguments(writer, step.args, step.block, step.bare)
        }
      }
    }
  }

  private fun emitArguments(
    writer: CialloCodeWriter,
    arguments: List<CialloCodeBlock>,
    block: CialloBlockSpec?,
    bare: Boolean,
  ) {
    if (arguments.isNotEmpty()) {
      if (bare && arguments.size == 1 && block == null) {
        writer.emit(" ").emitCode(arguments.single())
      } else {
        writer.emit("(").emitCode(arguments.joinToCode()).emit(")")
      }
    }
    if (block != null) {
      writer.emit(" ")
      block.emit(writer)
    }
  }

  override fun equals(other: Any?): Boolean =
    other is CialloChainSpec && other.toString() == toString()

  override fun hashCode(): Int = toString().hashCode()

  override fun toString(): String =
    buildString { CialloCodeWriter(this@buildString).also { it.emitChain(this@CialloChainSpec) } }

  class Builder internal constructor(private val base: CialloChainBase) {
    internal val headArguments: MutableList<CialloCodeBlock> = mutableListOf()
    internal var headBlock: CialloBlockSpec? = null
    internal var headBare: Boolean = false
    internal val steps: MutableList<CialloChainStep> = mutableListOf()

    /** Adds a parenthesised head argument: `print(@x)`. */
    fun addArgument(format: String, vararg args: Any?): Builder = apply {
      headArguments += CialloCodeBlock.of(format, *args)
    }

    fun addArgument(argument: CialloCodeBlock): Builder = apply { headArguments += argument }

    /**
     * Adds a head argument in value position, `label "text"`.
     *
     * Only a name head takes arguments, and a bare value is a single segment: the grammar allows
     * one trailing value per segment. Use [addArgument] for a normal parenthesised call.
     */
    fun addValueArgument(value: Any?): Builder = apply {
      addValueArgumentCode(CialloCodeBlock.of("%L", value))
    }

    /** Adds a head value argument from a source fragment: `label %S`. */
    fun addValueArgument(format: String, vararg args: Any?): Builder = apply {
      addValueArgumentCode(CialloCodeBlock.of(format, *args))
    }

    fun addValueArgumentCode(value: CialloCodeBlock): Builder = apply {
      check(headArguments.isEmpty()) { "the head already takes parenthesised arguments" }
      headArguments += value
      headBare = true
    }

    /** Adds a trailing block to the head: `world { ... }`. */
    fun block(block: CialloBlockSpec): Builder = apply { headBlock = block }

    /** Adds a bare member read: ` name`. */
    fun addMember(name: String): Builder = apply {
      steps += CialloChainStep.Member(CialloLiterals.nameLiteral(name))
    }

    /** Adds a call whose [args] are emitted as literals: ` name(%L, %L)`. */
    fun addCall(name: String, vararg args: Any?): Builder = apply {
      steps +=
        CialloChainStep.Call(
          CialloLiterals.nameLiteral(name),
          args.map { CialloCodeBlock.of("%L", it) },
        )
    }

    /** Adds a call whose [args] are source fragments: ` name(f(@x), 2)`. */
    fun addCall(name: String, args: Iterable<CialloCodeBlock>): Builder = apply {
      steps += CialloChainStep.Call(CialloLiterals.nameLiteral(name), args.toList())
    }

    /** Adds a call with a trailing block: ` name { ... }`. */
    fun addCall(name: String, block: CialloBlockSpec): Builder = apply {
      steps += CialloChainStep.Call(CialloLiterals.nameLiteral(name), emptyList(), block)
    }

    /**
     * Attaches one value to the previous member step, turning ` name` into ` name value`.
     *
     * The grammar allows this only once per member: a value after a completed call has to be
     * written as an argument instead.
     */
    fun addValue(value: Any?): Builder = apply { addValueCode(CialloCodeBlock.of("%L", value)) }

    fun addValueCode(value: CialloCodeBlock): Builder = apply {
      val last = steps.lastOrNull() ?: throw IllegalStateException("a value needs a member before it")
      check(last is CialloChainStep.Member) {
        "cannot attach a value after a completed call; pass it as an argument instead"
      }
      steps[steps.lastIndex] = CialloChainStep.Call(last.name, listOf(value), bare = true)
    }

    fun build(): CialloChainSpec {
      validate()
      return CialloChainSpec(
        base,
        headArguments.toList(),
        headBlock,
        headBare,
        steps.toList(),
      )
    }

    private fun validate() {
      if (headArguments.isEmpty() && headBlock == null) return
      check(base is CialloChainBase.Name) {
        when (base) {
          is CialloChainBase.Symbol ->
            "@${base.path} cannot take arguments; call a member of it instead"
          is CialloChainBase.Literal -> "a literal cannot take arguments"
          is CialloChainBase.Block -> "a block cannot take arguments"
          is CialloChainBase.Name -> ""
        }
      }
      // `label "text"` works because a name segment can be followed by a value segment; a name
      // literal cannot, so a bare value argument would produce an unparseable trailing value.
      check(!headBare || base is CialloChainBase.Name) {
        "only a name call can take a bare value argument"
      }
    }
  }

  companion object {
    /** Starts a chain at a name such as `build` or `obj.apply`. */
    fun builder(path: String): Builder = Builder(CialloChainBase.Name(CialloLiterals.nameLiteral(path)))

    /** Starts a chain at a name literal, `@x`. */
    fun symbolBuilder(path: String): Builder =
      Builder(CialloChainBase.Symbol(CialloLiterals.nameLiteral(path)))

    /** Starts a chain at a literal value. */
    fun literalBuilder(value: Any?): Builder = Builder(CialloChainBase.Literal(value))

    /** Starts a chain at a block. */
    fun blockBuilder(block: CialloBlockSpec): Builder = Builder(CialloChainBase.Block(block))

    /** A chain that is just [path], `build`. */
    fun of(path: String): CialloChainSpec = builder(path).build()

    /** A chain that is just a name literal, `@x`. */
    fun symbol(path: String): CialloChainSpec = symbolBuilder(path).build()

    /** A parenthesised call, `label("text")`. */
    fun call(path: String, vararg args: Any?): CialloChainSpec =
      builder(path).apply { args.forEach { addArgument("%L", it) } }.build()

    /** A block call, `then { ... }`. */
    fun call(path: String, block: CialloBlockSpec): CialloChainSpec =
      builder(path).block(block).build()
  }
}
