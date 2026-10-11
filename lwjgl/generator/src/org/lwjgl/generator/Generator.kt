/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.generator

import java.io.*
import java.lang.reflect.*
import java.nio.*
import java.nio.file.*
import java.nio.file.attribute.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

// Deleted Generator

// File management

private val moduleLastModifiedMap: MutableMap<Module, Long> = ConcurrentHashMap()

internal val Path.lastModified
  get() = if (Files.isRegularFile(this)) Files.getLastModifiedTime(this).toMillis() else 0L

private val KOTLIN_PATH_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**/*.kt")

internal fun Path.lastModified(
  maxDepth: Int = Int.MAX_VALUE,
  glob: String? = null,
  matcher: PathMatcher =
    if (glob == null) KOTLIN_PATH_MATCHER
    else FileSystems.getDefault().getPathMatcher("glob:$glob"),
): Long {
  check(Files.isDirectory(this)) {
    "$this is not a directory"
  }

  return Files.find(this, maxDepth, { path, _ -> matcher.matches(path) })
    .mapToLong(Path::lastModified)
    .reduce(0L, Math::max)
}

private fun ensurePath(path: Path) {
  val parent =
    path.parent ?: throw IllegalArgumentException("The given path has no parent directory.")

  if (!Files.isDirectory(parent)) {
    println("\tMKDIR: $parent")
    Files.createDirectories(parent)
  }
}

private fun readFile(file: Path) =
  Files.newByteChannel(file).use {
    val bytesTotal = it.size().toInt()
    val buffer = ByteBuffer.allocateDirect(bytesTotal)

    var bytesRead = 0
    do {
      bytesRead += it.read(buffer)
    } while (bytesRead < bytesTotal)

    buffer.flip()
    buffer
  }

private fun <T> generateOutput(
  target: T,
  file: Path,
  /** If not null, the file timestamp will be updated if no change occured since last generation. */
  lmt: Long? = null,
  generate: T.(PrintWriter) -> Unit,
) {
  // TODO: Add error handling

  ensurePath(file)

  if (Files.isRegularFile(file)) {
    // Generate in-memory
    val baos = ByteArrayOutputStream(4 * 1024)
    PrintWriter(OutputStreamWriter(baos, Charsets.UTF_8)).use {
      target.generate(it)
    }

    // Compare the existing file content with the generated content.
    val before = readFile(file)
    val after = baos.toByteArray()

    fun somethingChanged(before: ByteBuffer, after: ByteArray): Boolean {
      if (before.remaining() != after.size) return true

      return (0 until before.limit()).any { before.get(it) != after[it] }
    }

    if (somethingChanged(before, after)) {
      println("\tUPDATING: $file")
      // Overwrite
      Files.newOutputStream(file).use {
        it.write(after)
      }
    } else if (lmt != null) {
      // Update the file timestamp
      Files.setLastModifiedTime(file, FileTime.fromMillis(lmt + 1))
    }
  } else {
    println("\tWRITING: $file")
    PrintWriter(Files.newBufferedWriter(file, Charsets.UTF_8)).use {
      target.generate(it)
    }
  }
}

/** Returns true if the array was empty. */
internal inline fun <T> Array<out T>.forEachWithMore(apply: (T, Boolean) -> Unit): Boolean {
  for (i in 0..this.lastIndex) apply(this[i], 0 < i)
  return this.isEmpty()
}

/** Returns true if the collection was empty. */
internal fun <T> Collection<T>.forEachWithMore(
  moreOverride: Boolean = false,
  apply: (T, Boolean) -> Unit,
): Boolean = this.asSequence().forEachWithMore(moreOverride, apply)

/** Returns true if the sequence was empty. */
internal fun <T> Sequence<T>.forEachWithMore(
  moreOverride: Boolean = false,
  apply: (T, Boolean) -> Unit,
): Boolean {
  var more = moreOverride
  for (item in this) {
    apply(item, more)
    if (!more) more = true
  }
  return more
}

/** Returns the string with the first letter uppercase. */
internal val String.upperCaseFirst
  get() =
    if (this.length <= 1) this.uppercase()
    else "${Character.toUpperCase(this[0])}${this.substring(1)}"
