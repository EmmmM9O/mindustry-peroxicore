package peroxicore.ciallopoet

import peroxicore.ciallo.*
import java.io.*
import java.nio.file.Path
import kotlin.io.path.*

/**
 * A Ciallo program: an optional header comment followed by top-level statements.
 *
 * Ciallo has no package, imports or declarations, so a file is simply the ordered statements a
 * script runs. [writeTo] writes the file as built; [formatted] normalises it with
 * [CialloFormatter] so output spliced in with raw `addCode` matches `ciallo fmt`.
 */
class CialloFileSpec
private constructor(
  private val comment: CialloCodeBlock,
  val name: String,
  private val body: CialloCodeBlock,
) {
  val isScript: Boolean get() = true

  /** The file extension written by [writeTo]; always `ciallo`. */
  val extension: String get() = "ciallo"

  val relativePath: String get() = name + "." + extension

  fun isEmpty(): Boolean = comment.isEmpty() && body.isEmpty()

  fun isNotEmpty(): Boolean = !isEmpty()

  fun toBuilder(): Builder = Builder(name).also { builder ->
    builder.comment.add(comment)
    builder.body.add(body)
  }

  /**
   * Emits the program without formatting.
   *
   * The result is already canonical for everything this builder produces, so [formatted] is only
   * needed when raw multi-line fragments were spliced in with `addCode`.
   */
  fun writeTo(out: Appendable) {
    val writer = CialloCodeWriter(out)
    if (comment.isNotEmpty()) writer.emitComment(comment)
    writer.emitCode(body)
    if (!writer.isLineStart) writer.emit("\n")
  }

  /**
   * Writes to [directory], creating it when needed, and returns the written path.
   *
   * The file holds exactly [toString]; call [formatted] when normalisation is wanted.
   */
  @Throws(IOException::class)
  fun writeTo(directory: Path): Path {
    require(directory.notExists() || directory.isDirectory()) {
      "path $directory exists but is not a directory"
    }
    directory.createDirectories()
    val outputPath = directory.resolve(relativePath)
    outputPath.writeText(toString())
    return outputPath
  }

  @Throws(IOException::class)
  fun writeTo(directory: File): File = writeTo(directory.toPath()).toFile()

  /** The program text, normalised by [CialloFormatter]. */
  fun formatted(indentUnit: String = "  "): String = CialloFormatter.format(toString(), indentUnit)

  override fun toString(): String = buildString { writeTo(this@buildString) }

  class Builder internal constructor(private val name: String) {
    internal val comment: CialloCodeBlock.Builder = CialloCodeBlock.builder()
    internal val body: CialloCodeBlock.Builder = CialloCodeBlock.builder()

    /** Sets the header comment shown at the top of the file. */
    fun addFileComment(format: String, vararg args: Any?): Builder = apply {
      comment.add(format, *args)
    }

    /** Appends one top-level statement. */
    fun addStatement(format: String, vararg args: Any?): Builder = apply {
      body.addStatement(format, *args)
    }

    /** Appends raw code, which may span several lines. */
    fun addCode(format: String, vararg args: Any?): Builder = apply { body.add(format, *args) }

    fun addCode(codeBlock: CialloCodeBlock): Builder = apply { body.add(codeBlock) }

    /** Appends [chain] as one statement, `build @Blocks.duo at(4, 3) config null`. */
    fun addStatement(chain: CialloChainSpec): Builder = apply { body.addStatement("%L", chain) }

    fun addComment(format: String, vararg args: Any?): Builder = apply {
      body.addComment(format, *args)
    }

    /** Appends a blank line. */
    fun addBlankLine(): Builder = apply { body.add("\n") }

    fun build(): CialloFileSpec = CialloFileSpec(comment.build(), name, body.build())
  }

  companion object {
    fun builder(name: String): Builder {
      require(name.isNotEmpty()) { "a Ciallo file needs a name" }
      require('.' !in name) { "name '$name' must not contain an extension" }
      return Builder(name)
    }

    /** A file called [name] holding [code]. */
    fun of(name: String, code: CialloCodeBlock): CialloFileSpec =
      builder(name).addCode(code).build()
  }
}

/** Builds a [CialloFileSpec] called [name] with [builderAction]. */
inline fun buildFile(
  name: String,
  builderAction: CialloFileSpec.Builder.() -> Unit,
): CialloFileSpec = CialloFileSpec.builder(name).apply(builderAction).build()
