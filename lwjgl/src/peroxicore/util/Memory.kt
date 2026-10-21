package peroxicore.util

import java.io.*
import java.nio.*
import org.lwjgl.system.*

object Memory {
  inline fun <T> wrap(bytes: ByteArray, block: (ByteBuffer) -> T): T =
    AutoByteBuffer(bytes).use { auto ->
      block(auto.get())
    }
}

class AutoByteBuffer private constructor(private var buffer: ByteBuffer?) : AutoCloseable {
  constructor(size: Int) : this(MemoryUtil.memAlloc(size))

  constructor(bytes: ByteArray) : this(MemoryUtil.memAlloc(bytes.size).put(bytes).flip())

  fun get(): ByteBuffer = checkNotNull(buffer) { "buffer already closed" }

  override fun close() {
    buffer?.let {
      MemoryUtil.memFree(it)
      buffer = null
    }
  }

  operator fun invoke() = get()
}
