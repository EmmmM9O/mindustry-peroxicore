package peroxicore.util

import arc.struct.*
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import mindustry.*
import mindustry.ctype.*
import peroxicore.struct.*

class ObjectAttachedProperty<T : Any, V>(private val defaultValue: T.() -> V) :
  ReadWriteProperty<T, V> {
  private val map = WeakHashMap<T, V>()

  override fun getValue(thisRef: T, property: KProperty<*>): V =
    map.getOrPut(thisRef) { thisRef.defaultValue() }

  override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
    map[thisRef] = value
  }
}

class ContentAttachedProperty<T : Content, V>(private val defaultValue: T.() -> V) :
  ReadWriteProperty<T, V> {
  private val values = Seq<V?>()

  override fun getValue(thisRef: T, property: KProperty<*>): V {
    values.ensureCapacity(Vars.content.contentMap[thisRef.contentType.ordinal].size)
    return values.getOrPut(thisRef.id.toInt()) { thisRef.defaultValue() }!!
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
    values.set(thisRef.id.toInt(), value)
  }
}
