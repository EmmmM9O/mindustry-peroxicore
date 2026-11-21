package peroxicore.ciallopoet

/**
 * Assigns unique, valid Ciallo names.
 *
 * Ciallo variables may not reuse the reserved words `local`, `mount` and `set`, and a name must not
 * collide with `null`/`true`/`false` or with the names already handed out. Each suggestion keeps
 * the requested name when possible and otherwise gains a numeric suffix, so `x`, `x2`, `x3`.
 */
class CialloNameAllocator {
  private val used = mutableSetOf<String>()
  private val currentNames = mutableMapOf<Any, String>()

  /** The name allocated for [tag], or `null` when it has none. */
  fun get(tag: Any): String? = currentNames[tag]

  /**
   * Allocates a unique name based on [suggestion] and associates it with [tag].
   *
   * A tag is allocated once; asking again returns the same name.
   */
  fun newName(suggestion: String, tag: Any = suggestion): String {
    currentNames[tag]?.let { return it }
    var candidate = sanitize(suggestion)
    var suffix = 2
    while (candidate in used || candidate in reserved) {
      candidate = sanitize(suggestion) + suffix
      suffix++
    }
    used += candidate
    currentNames[tag] = candidate
    return candidate
  }

  /** The name allocated for [tag], failing when there is none. */
  fun nameFor(tag: Any): String = get(tag) ?: throw IllegalArgumentException("no name for $tag")

  private fun sanitize(suggestion: String): String {
    val builder = StringBuilder(suggestion.length)
    for (char in suggestion) {
      builder.append(if (char.isLetterOrDigit() || char == '_') char else '_')
    }
    if (builder.isEmpty() || !(builder[0].isLetter() || builder[0] == '_')) {
      builder.insert(0, '_')
    }
    return builder.toString()
  }

  private companion object {
    /** The variable operations plus the literal keywords the lexer claims first. */
    val reserved = setOf("local", "mount", "set", "null", "true", "false")
  }
}
