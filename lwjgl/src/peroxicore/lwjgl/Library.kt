package peroxicore.lwjgl

import arc.util.Log
import java.io.*
import java.net.*
import java.nio.channels.*
import java.util.function.*
import org.lwjgl.system.*
import org.lwjgl.system.Library as LLibrary
import universe.util.reflect.*

object Library {
  val getBundledPath: (String, String) -> String = LLibrary::class.accessMethod2("getBundledPath")
  val sload: (String, String, URL, Consumer<String>) -> FileChannel =
    Class.forName("org.lwjgl.system.SharedLibraryLoader").kotlin.accessMethod4("load")
  val loadSystemFromLibraryPath: (Consumer<String>, Class<*>, String, String, Boolean) -> Boolean =
    LLibrary::class.accessMethod5("loadSystemFromLibraryPath")

  val mapLibraryName: Platform.(String) -> String = accessMethod1("mapLibraryName")

  init {
    this.loadSystem("peroxicore", "peroxicore")
  }

  fun loadSystem(module: String, name: String) {
    val load = System::load
    val libName = Platform.get().mapLibraryName(name)
    val bundled = getBundledPath(module, libName)
    val url = this.javaClass.classLoader.getResource(bundled)
    sload(name, libName, url, load).use {
      if (loadSystemFromLibraryPath(load, Library::class.java, module, libName, true)) {}
    }
  }

  // load a already loaded in apk
  fun loadFind(name: String) {
    val libName = Platform.get().mapLibraryName(name)
    val path = LibraryUtil.findLibpath("($libName)")
    Log.info("Try Load $libName")
    try {
      createGlobalLibrary(path)
      Log.info("Loaded from $path")
    } catch (error: Throwable) {
      Log.err("Load Fail", error)
    }
  }

  fun createGlobalLibrary(name: String): PLibrary {
    return PLinuxLibrary(name)
  }

  @JvmStatic fun initialize() {
    loadFind("arc")
  }
}
