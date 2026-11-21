package peroxicore.ciallopoet

/**
 * A first-class Ciallo block, `{ ... }`.
 *
 * A block is a sequence of statements that the interpreter can run with `run`, `then`, `apply` and
 * friends. Ciallo separates statements with newlines, so [Builder.addStatement] is the common case;
 * [Builder.addCode] is for multi-line fragments that already carry their own newlines.
 */
class CialloBlockSpec
private constructor(private val body: CialloCodeBlock) {
  fun isEmpty(): Boolean = body.isEmpty()

  fun isNotEmpty(): Boolean = !isEmpty()

  fun toBuilder(): Builder = Builder().addCode(body)

  internal fun emit(writer: CialloCodeWriter) {
    if (body.isEmpty()) {
      writer.emit("{}")
      return
    }
    writer.emit("{\n")
    writer.indent()
    writer.emitCode(body)
    if (!writer.isLineStart) writer.emit("\n")
    writer.unindent()
    writer.emit("}")
  }

  override fun equals(other: Any?): Boolean =
    other is CialloBlockSpec && other.toString() == toString()

  override fun hashCode(): Int = toString().hashCode()

  override fun toString(): String =
    buildString { emit(CialloCodeWriter(this@buildString)) }

  class Builder {
    private val body: CialloCodeBlock.Builder = CialloCodeBlock.builder()

    fun isEmpty(): Boolean = body.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun addStatement(format: String, vararg args: Any?): Builder = apply {
      body.addStatement(format, *args)
    }

    fun addCode(format: String, vararg args: Any?): Builder = apply { body.add(format, *args) }

    fun addCode(codeBlock: CialloCodeBlock): Builder = apply { body.add(codeBlock) }

    fun addComment(format: String, vararg args: Any?): Builder = apply {
      body.addComment(format, *args)
    }

    /** Adds a blank line between statements. */
    fun addBlankLine(): Builder = apply { body.add("\n") }

    /** Adds [chain] as a statement. */
    fun addChain(chain: CialloChainSpec): Builder = apply { addStatement("%L", chain) }

    fun build(): CialloBlockSpec = CialloBlockSpec(body.build())
  }

  companion object {
    fun builder(): Builder = Builder()

    fun of(format: String, vararg args: Any?): CialloBlockSpec =
      Builder().addCode(format, *args).build()
  }
}

/** Builds a [CialloBlockSpec] with [builderAction]. */
inline fun buildBlock(builderAction: CialloBlockSpec.Builder.() -> Unit): CialloBlockSpec =
  CialloBlockSpec.builder().apply(builderAction).build()
