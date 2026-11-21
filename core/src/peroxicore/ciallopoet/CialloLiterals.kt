package peroxicore.ciallopoet

import java.math.*
import peroxicore.ciallo.value.*

/**
 * Source spelling of Ciallo literals and names.
 *
 * Ciallo has no type system and no object literals, so [literal] only covers the values the
 * language can spell. Unsupported runtime values ([CialloObj], blocks, natives and host objects)
 * are rejected instead of being emitted as text that would not parse.
 */
object CialloLiterals {
  private val identifier = Regex("[a-zA-Z_][a-zA-Z0-9_]*")

  /** Tokens the lexer claims before the parser sees an identifier. */
  private val keywordTokens = setOf("null", "true", "false")

  /**
   * A double-quoted Ciallo string.
   *
   * Braces are escaped so a generated literal never turns into a string interpolation, and control
   * characters use the escapes the interpreter understands.
   */
  fun stringLiteral(value: String): String {
    val builder = StringBuilder(value.length + 2)
    builder.append('"')
    for (char in value) {
      when (char) {
        '\\' -> builder.append("\\\\")
        '"' -> builder.append("\\\"")
        '\n' -> builder.append("\\n")
        '\r' -> builder.append("\\r")
        '\t' -> builder.append("\\t")
        '\b' -> builder.append("\\b")
        '\u000C' -> builder.append("\\f")
        '{', '}' -> builder.append('\\').append(char)
        else ->
          if (char < ' ') {
            builder.append("\\u").append(char.code.toString(16).padStart(4, '0'))
          } else {
            builder.append(char)
          }
      }
    }
    builder.append('"')
    return builder.toString()
  }

  /**
   * A Ciallo number literal.
   *
   * Ciallo only has a 32-bit `Int` and a `Float`; integral values stay integral, everything else
   * keeps a decimal point so the lexer reads it as a float. A `Long` outside the `Int` range has no
   * faithful spelling and is rejected rather than silently becoming a float.
   */
  fun numberLiteral(value: Number): String =
    when (value) {
      is Int, is Short, is Byte -> value.toString()
      is Long -> {
        require(value >= Int.MIN_VALUE.toLong() && value <= Int.MAX_VALUE.toLong()) {
          "Ciallo has no literal for the Long $value"
        }
        value.toString()
      }
      else -> decimalLiteral(value.toString())
    }

  private fun decimalLiteral(text: String): String {
    val value = text.toDoubleOrNull() ?: throw IllegalArgumentException("'$text' is not a number")
    require(value.isFinite()) { "Ciallo has no literal for $text" }
    val plain = BigDecimal(text).stripTrailingZeros().toPlainString()
    return if ('.' in plain) plain else plain + ".0"
  }

  /** A bare name, or a dotted path such as `Blocks.duo`. */
  fun nameLiteral(path: String): String {
    val segments = path.split('.')
    require(segments.all { identifier.matches(it) } && segments.none { it in keywordTokens }) {
      "'$path' is not a Ciallo name"
    }
    return path
  }

  /** A name literal: `@x` or `@Blocks.duo`. */
  fun symbolLiteral(path: String): String = "@" + nameLiteral(path)

  /**
   * The source of [value] in a position that expects a value, such as a call argument.
   *
   * Strings become quoted literals here; use a [CialloCodeBlock] to splice raw code instead.
   */
  fun literal(value: Any?): String =
    when (value) {
      null -> "null"
      is Boolean -> value.toString()
      is Char -> stringLiteral(value.toString())
      is CharSequence -> stringLiteral(value.toString())
      is Number -> numberLiteral(value)
      is CialloValue -> valueLiteral(value)
      else -> throw IllegalArgumentException("$value has no Ciallo literal syntax")
    }

  /** The source of a runtime value; only the kinds the syntax can spell are accepted. */
  fun valueLiteral(value: CialloValue): String =
    when {
      value === CialloNil.INSTANCE -> "null"
      value is CialloBool -> value.value.toString()
      value is CialloInt -> numberLiteral(value.value)
      value is CialloFloat -> numberLiteral(value.value)
      value is CialloTime -> timeLiteral(value)
      value is CialloStr -> stringLiteral(value.value)
      value is CialloArr -> value.items.joinToString(", ", "[", "]") { valueLiteral(it) }
      value is CialloSymbol -> symbolLiteral(value.path.joinToString("."))
      else -> throw IllegalArgumentException("${value.kind.displayName} has no Ciallo literal syntax")
    }

  private fun timeLiteral(value: CialloTime): String {
    val amount = value.amount
    require(amount.isFinite() && amount >= 0.0) {
      "Ciallo has no literal for negative or non-finite time"
    }
    val number =
      if (amount <= Long.MAX_VALUE.toDouble() && amount == Math.floor(amount)) {
        amount.toLong().toString()
      } else {
        decimalLiteral(amount.toString())
      }
    return number + value.unit.suffix
  }
}
