package peroxicore.util.reflect

import universe.UniverseActual.reflection
import universe.util.reflect.*
import universe.util.reflect.accessor.*
import kotlin.reflect.*

inline fun <reified T> KClass<*>.accessFieldStatic(name: String) =
  FieldAccessorStatic<T>(reflection.findStaticField(this, name))
