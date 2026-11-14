package peroxicore.util.reflect

import kotlin.reflect.*
import universe.UniverseActual.reflection
import universe.util.reflect.accessor.*

inline fun <reified T> KClass<*>.accessFieldStatic(name: String) =
  FieldAccessorStatic<T>(reflection.findStaticField(this, name))

inline fun <reified O : Any, reified T: Any?> accessFieldAny(name: String) =
  FieldAccessor<O, T>(reflection.findField(O::class, name))
