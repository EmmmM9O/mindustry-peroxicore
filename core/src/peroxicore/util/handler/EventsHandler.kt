@file:Suppress("UNCHECKED_CAST")

package peroxicore.util.handler

import arc.*
import arc.func.*
import arc.struct.*
import universe.util.reflect.*

object EventsHandler {
  val events: ObjectMap<Any, Seq<Cons<*>>> by Events::class.accessField("events")

  fun removeLastByKey(key: Any): Cons<*>? =
    events.get(key)?.run {
      if (size == 0) return null
      return pop()
    }

  inline fun <reified E> removeLast() = removeLastByKey(E::class.java) as? Cons<E>

  fun <T : Enum<T>> removeLastRun(type: Enum<T>) = removeLastByKey(type) as? Cons<Any>

  fun setup() {}
}
