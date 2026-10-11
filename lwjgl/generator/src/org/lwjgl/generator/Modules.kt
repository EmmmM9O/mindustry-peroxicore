/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.generator

import java.io.*
import peroxicore.*

// Modifified
class Module(
  val key: String,
  val packageName: String,
  packageInfo: String,
  val opath: String? = null,
  val callingConvention: CallingConvention = CallingConvention.DEFAULT,
  val library: JNILibrary? = null,
  val arrayOverloads: Boolean = true,
) {

  val name =
    key
      .replace(Regex("([a-z])([A-Z])"), "$1_$2")
      .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
      .replace(".", "_")
      .uppercase()

  companion object {
    internal val CHECKS = !System.getProperty("binding.DISABLE_CHECKS", "false")!!.toBoolean()
    // Modifified
    val CORE = Module("core", "peroxicore.lwjgl", "", ".")
  }

  init {
    if (packageInfo.isNotEmpty()) {
      packageInfo(this, packageInfo)
    }

    if (library != null && enabled) {
      library.configure(this)
    }
  }

  val enabled
    get() = key.startsWith("core") || System.getProperty("binding.$key", "false")!!.toBoolean()

  val path = opath ?: if (name.startsWith("CORE_")) "core" else name.lowercase()
  val java = "$packageName"

  internal val packageKotlin
    get() = name.let {
      if (it.startsWith("CORE_")) {
        this.key
      } else {
        it.lowercase()
      }
    }

  @Suppress("LeakingThis") private val CALLBACK_RECEIVER = ANONYMOUS.nativeClass(this)

  fun callback(init: NativeClass.() -> FunctionType) = CALLBACK_RECEIVER.init()
}

interface JNILibrary {
  companion object {
    fun simple(expression: String? = null): JNILibrary = JNILibrarySimple(expression)

    fun create(
      className: String,
      libraryName: String? = null,
      custom: Boolean = false,
      setupAllocator: Boolean = false,
      cpp: Boolean = false,
    ): JNILibrary = JNILibraryWithInit(className, libraryName, custom, setupAllocator, cpp)
  }

  fun expression(module: Module): String

  fun configure(module: Module)
}

private class JNILibrarySimple(private val expression: String?) : JNILibrary {
  override fun expression(module: Module) =
    if (expression != null) expression else "lwjgl_${module.key}"

  override fun configure(module: Module) = Unit
}

class JNILibraryWithInit
constructor(
  private val className: String,
  private val libraryName: String?,
  private val custom: Boolean,
  private val setupAllocator: Boolean,
  private val cpp: Boolean,
) : JNILibrary {

  override fun expression(module: Module) = "$className.initialize();"

  override fun configure(module: Module) {
    if (custom) {
      return
    }

    Generator.register(
      object : GeneratorTargetNative(module, className) {
        init {
          this.access = Access.INTERNAL
          this.cpp = this@JNILibraryWithInit.cpp
          this.documentation = "Initializes the ${module.key} shared library."
          javaImport("org.lwjgl.system.*")
          if (setupAllocator) javaImport("static org.lwjgl.system.MemoryUtil.*")
          nativeDirective(
            """#define LWJGL_MALLOC_LIB $nativeFileNameJNI
#include "lwjgl_malloc.h""""
          )
        }

        override fun PrintWriter.generateJava() {
          generateJavaPreamble()
          println(
            """${access.modifier}final class $className {

    static {
        String libName = Platform.mapLibraryNameBundled(${libraryName ?: "\"lwjgl_${module.key}\""});
        Library.loadSystem(System::load, System::loadLibrary, $className.class, "${module.java}", libName);${if (setupAllocator) """

        MemoryAllocator allocator = getAllocator(Configuration.DEBUG_MEMORY_ALLOCATOR_INTERNAL.get(true));
        setupMalloc(
            allocator.getMalloc(),
            allocator.getCalloc(),
            allocator.getRealloc(),
            allocator.getFree(),
            allocator.getAlignedAlloc(),
            allocator.getAlignedFree()
        );""" else ""}
    }

    private $className() {
    }

    static void initialize() {
        // intentionally empty to trigger static initializer
    }${if (setupAllocator) """

    private static native void setupMalloc(
        long malloc,
        long calloc,
        long realloc,
        long free,
        long aligned_alloc,
        long aligned_free
    );""" else ""}

}"""
          )
        }

        override val skipNative
          get() = !setupAllocator

        override fun PrintWriter.generateNative() {
          generateNativePreamble()
        }
      }
    )
  }
}

fun String.dependsOn(vararg modules: Module): String? =
  if (modules.any { it.enabled }) this else null
