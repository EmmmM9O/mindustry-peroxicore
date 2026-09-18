package peroxicore.mod

import arc.struct.*

class PXCModClassLoader(
  parent: ClassLoader,
) : ClassLoader(parent) {
  private val children = Seq<ClassLoader>()

  private val inChild = ThreadLocal.withInitial { false }

  fun addChild(child: ClassLoader) {
    children.add(child)
  }

  @Throws(ClassNotFoundException::class)
  override fun loadClass(
    name: String,
    resolve: Boolean,
  ): Class<*> =
    // synchronized(getClassLoadingLock(name)) {
    findLoadedClass(name)
      ?: if (name.isCoreClass) {
        parent.loadClass(name)
      } else {
        runCatching { findClass(name) }
          .getOrElse { parent.loadClass(name) }
      }
  // }

  @Throws(ClassNotFoundException::class)
  override fun findClass(name: String): Class<*> {
    // a child may try to delegate class loading to its parent, which is *this class loader* - do not let that happen
    if (inChild.get()) {
      inChild.set(false)
      throw ClassNotFoundException(name)
    }

    var last: ClassNotFoundException? = null
    val size = children.size

    // if it doesn't exist in the main class loader, try all the children
    for (i in 0 until size) {
      try {
        try {
          inChild.set(true)
          return children[i].loadClass(name)
        } finally {
          inChild.set(false)
        }
      } catch (e: ClassNotFoundException) {
        last = e
      }
    }

    throw (last ?: ClassNotFoundException(name))
  }

  private val String.isCoreClass: Boolean
    get() =
      startsWith("java.") ||
        startsWith("javax.") ||
        startsWith("jdk.") ||
        startsWith("android.") ||
        startsWith("sun.")
}
