package peroxicore.ciallopoet

import peroxicore.ciallo.value.*

private val noArgPlaceholders = setOf('⇥', '⇤', '«', '»', '·', '♢')

private const val argPlaceholders = "LSNA"

/**
 * A fragment of Ciallo source with `Formatter`-like placeholders.
 *
 * Ciallo has no types, imports or declarations, so the placeholder set is small:
 * - `%L` emits a literal with no escaping. Strings are emitted raw, so `%L` is how raw code is
 *   spliced; primitives, [CialloValue]s, [CialloCodeBlock]s, [CialloBlockSpec]s and
 *   [CialloChainSpec]s are emitted as source too.
 * - `%S` emits a [String] as a quoted Ciallo string, escaping quotes, backslashes and braces.
 * - `%N` emits a name or dotted path such as `label` or `Blocks.duo`.
 * - `%A` emits a name literal such as `@x` or `@Blocks.duo`.
 * - `%%` emits a percent sign.
 *
 * The writer is newline-sensitive because a newline ends a Ciallo statement, so unlike KotlinPoet
 * there is no soft line wrapping. `·` and `♢` both emit one space, `⇥` and `⇤` indent and unindent
 * the following lines, and `«`/`»` delimit a statement (they only check that statements balance;
 * block indentation is always explicit).
 */
class CialloCodeBlock
private constructor(
  internal val formatParts: List<String>,
  internal val args: List<Any?>,
) {
  fun isEmpty(): Boolean = formatParts.isEmpty()

  fun isNotEmpty(): Boolean = !isEmpty()

  fun toBuilder(): Builder {
    val builder = Builder()
    builder.formatParts.addAll(formatParts)
    builder.args.addAll(args)
    return builder
  }

  override fun equals(other: Any?): Boolean =
    other is CialloCodeBlock && other.toString() == toString()

  override fun hashCode(): Int = toString().hashCode()

  override fun toString(): String = buildString { CialloCodeWriter(this).emitCode(this@CialloCodeBlock) }

  class Builder {
    internal val formatParts: MutableList<String> = mutableListOf()
    internal val args: MutableList<Any?> = mutableListOf()

    fun isEmpty(): Boolean = formatParts.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Appends [format], replacing each placeholder with the matching argument.
     *
     * Placeholders are relative by default (`%L`, `%L`); an explicit one-based index such as `%2L`
     * reuses an argument and cannot be mixed with relative placeholders. Every argument must be
     * used.
     */
    fun add(format: String, vararg args: Any?): Builder = apply {
      var index = 0
      var relativeCount = 0
      var hasRelative = false
      var hasIndexed = false
      val indexedUses = IntArray(args.size)

      while (index < format.length) {
        val char = format[index]
        if (char in noArgPlaceholders) {
          formatParts += char.toString()
          index++
          continue
        }
        if (char != '%') {
          var next = index + 1
          while (next < format.length && format[next] != '%' && format[next] !in noArgPlaceholders) {
            next++
          }
          formatParts += format.substring(index, next)
          index = next
          continue
        }

        index++ // '%'
        val indexStart = index
        var placeholder: Char
        do {
          require(index < format.length) { "dangling % in '$format'" }
          placeholder = format[index++]
        } while (placeholder in '0'..'9')
        val indexEnd = index - 1

        if (placeholder == '%') {
          require(indexStart == indexEnd) { "%% may not have an index" }
          formatParts += "%%"
          continue
        }
        require(placeholder in argPlaceholders) { "unknown placeholder %$placeholder in '$format'" }

        val argumentIndex: Int
        if (indexStart < indexEnd) {
          hasIndexed = true
          argumentIndex = format.substring(indexStart, indexEnd).toInt() - 1
          require(argumentIndex in args.indices) {
            "index ${argumentIndex + 1} is not in range (received ${args.size} arguments)"
          }
          indexedUses[argumentIndex]++
        } else {
          hasRelative = true
          argumentIndex = relativeCount++
          require(argumentIndex in args.indices) {
            "placeholder %$placeholder is missing an argument (received ${args.size})"
          }
        }
        require(!(hasIndexed && hasRelative)) { "cannot mix indexed and relative placeholders" }

        addArgument(placeholder, args[argumentIndex])
        formatParts += "%$placeholder"
      }

      if (hasRelative) {
        require(relativeCount == args.size) {
          "unused arguments: expected $relativeCount, received ${args.size}"
        }
      }
      if (hasIndexed) {
        val unused = args.indices.filter { indexedUses[it] == 0 }.map { "%${it + 1}" }
        require(unused.isEmpty()) { "unused arguments: ${unused.joinToString(", ")}" }
      }
    }

    fun add(codeBlock: CialloCodeBlock): Builder = apply {
      formatParts.addAll(codeBlock.formatParts)
      args.addAll(codeBlock.args)
    }

    /** Appends one statement: [format] followed by a newline. */
    fun addStatement(format: String, vararg args: Any?): Builder = apply {
      add("«")
      add(format, *args)
      add("\n»")
    }

    /** Appends a `//` comment; [format] must not contain a newline. */
    fun addComment(format: String, vararg args: Any?): Builder = apply {
      add("// ")
      add(format, *args)
      add("\n")
    }

    fun indent(): Builder = apply { formatParts += "⇥" }

    fun unindent(): Builder = apply { formatParts += "⇤" }

    fun clear(): Builder = apply {
      formatParts.clear()
      args.clear()
    }

    fun build(): CialloCodeBlock = CialloCodeBlock(formatParts.toList(), args.toList())

    private fun addArgument(placeholder: Char, argument: Any?) {
      when (placeholder) {
        'L', 'S' -> args += argument
        'N' -> args += CialloLiterals.nameLiteral(argToName(argument))
        // The leading '@' is written by the writer, so only the name is stored here.
        'A' -> args += CialloLiterals.nameLiteral(argToName(argument))
        else -> throw IllegalArgumentException("unknown placeholder %$placeholder")
      }
    }

    private fun argToName(argument: Any?): String =
      when (argument) {
        is CharSequence -> argument.toString()
        is CialloSymbol -> argument.path.joinToString(".")
        else -> throw IllegalArgumentException("expected a name but was $argument")
      }
  }

  companion object {
    val EMPTY: CialloCodeBlock = CialloCodeBlock(emptyList(), emptyList())

    fun of(format: String, vararg args: Any?): CialloCodeBlock =
      Builder().add(format, *args).build()

    fun builder(): Builder = Builder()
  }
}

/** Builds a [CialloCodeBlock] with [builderAction]. */
inline fun buildCodeBlock(builderAction: CialloCodeBlock.Builder.() -> Unit): CialloCodeBlock =
  CialloCodeBlock.builder().apply(builderAction).build()

/** Runs [builderAction] one indentation level deeper. */
inline fun CialloCodeBlock.Builder.withIndent(
  builderAction: CialloCodeBlock.Builder.() -> Unit,
): CialloCodeBlock.Builder = indent().also(builderAction).unindent()

/** Joins the blocks in [this] with [separator] inside an optional [prefix] and [suffix]. */
fun Collection<CialloCodeBlock>.joinToCode(
  separator: CharSequence = ", ",
  prefix: CharSequence = "",
  suffix: CharSequence = "",
): CialloCodeBlock {
  val blocks = toTypedArray()
  val placeholders = Array(blocks.size) { "%L" }
  return CialloCodeBlock.of(placeholders.joinToString(separator, prefix, suffix), *blocks)
}

/** Applies [transform] to every element, then joins the results with [joinToCode]. */
fun <T> Collection<T>.joinToCode(
  separator: CharSequence = ", ",
  prefix: CharSequence = "",
  suffix: CharSequence = "",
  transform: (T) -> CialloCodeBlock,
): CialloCodeBlock = map(transform).joinToCode(separator, prefix, suffix)
