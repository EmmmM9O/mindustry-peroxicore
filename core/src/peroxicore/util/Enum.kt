package peroxicore.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T> anyToEnumOr(value: String): T? {
  val javaClass = T::class.java
  return if (javaClass.isEnum) {
    javaClass.enumConstants.firstOrNull { it.toString() == value }
  } else {
    null
  }
}

inline fun <reified T : Enum<T>> toEnumOr(name: String): T? =
  try {
    enumValueOf<T>(name)
  } catch (e: IllegalArgumentException) {
    null
  }

abstract class EnumMember
protected constructor(
  val name: String,
  val ordinal: Int,
) {
  override fun toString(): String = name

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = System.identityHashCode(this)
}

abstract class EnumRegistry<T : EnumMember> {
  private val entries = mutableListOf<T>()

  @Synchronized
  internal fun obtain(name: String, factory: (ordinal: Int) -> T): T {
    entries
      .firstOrNull { it.name == name }
      ?.let {
        return it
      }
    return factory(entries.size).also { entries += it }
  }

  fun define(name: String, factory: (ordinal: Int) -> T): T = obtain(name, factory)

  fun entry(name: String, factory: (ordinal: Int) -> T): ReadOnlyProperty<Any?, T> =
    object : ReadOnlyProperty<Any?, T> {
      private var cached: T? = null

      override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        cached ?: obtain(name, factory).also { cached = it }
    }

  fun values(): List<T> = synchronized(this) { entries.toList() }

  fun valueOfOrNull(name: String): T? =
    synchronized(this) { entries.firstOrNull { it.name == name } }

  fun valueOf(name: String): T =
    valueOfOrNull(name)
      ?: throw IllegalArgumentException("No enum member '$name' in ${this::class.simpleName}")

  @Suppress("UNUSED_PARAMETER")
  fun prewarm(vararg members: T) {
    /* no-op */
  }
}
