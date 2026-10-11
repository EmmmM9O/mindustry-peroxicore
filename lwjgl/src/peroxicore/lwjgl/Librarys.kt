package peroxicore.lwjgl

import java.nio.*
import org.lwjgl.system.*
import org.lwjgl.system.linux.*
import org.lwjgl.system.linux.DynamicLinkLoader.*

abstract class PLibrary(val name: String, val address: Long) {
  abstract fun path(): String?

  abstract fun functionAddress(functionName: ByteBuffer): Long

  abstract fun free()
}

class PLinuxLibrary : PLibrary {
  constructor(name: String) : super(name, loadLibrary(name))

  constructor(name: String, handle: Long) : super(name, handle)

  override fun path(): String? = SharedLibraryUtil.getLibraryPath(address)

  override fun functionAddress(functionName: ByteBuffer): Long = dlsym(address, functionName)

  override fun free() {
    dlclose(address)
  }

  companion object {
    private fun loadLibrary(name: String): Long {
      val handle: Long
      MemoryStack.stackPush().use { stack ->
        handle = dlopen(stack.UTF8(name), RTLD_GLOBAL or RTLD_LAZY)
      }
      if (handle == MemoryUtil.NULL) {
        throw UnsatisfiedLinkError(
          "Failed to dynamically load library: $name(error = ${dlerror()})"
        )
      }
      return handle
    }
  }
}
