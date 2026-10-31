package peroxicore.ciallo

import universe.UniverseActual.reflection
import java.lang.reflect.*

/**
 * JVM bridge for the full-access sandbox.
 *
 * The language itself never routes normal builtins through reflection; only
 * host Java objects reached through a [CialloValue.JavaValue] do.  UniverseKit's
 * reflection handle is used so that module boundaries and final fields behave on
 * both desktop and Android.
 */
internal object CialloJavaAccess {
  fun resolveStatic(sandbox: CialloSandbox, path: List<String>): CialloValue? {
    if (!sandbox.allowJvmAccess) return null
    if (path.size < 2) return null

    val className = path.dropLast(1).joinToString(".")
    val fieldName = path.last()
    val candidates = linkedSetOf<String>()
    candidates += className
    sandbox.allowedPackages.forEach { candidates += it + "." + className }

    for (candidate in candidates) {
      if (!sandbox.permitsClass(candidate)) continue
      val clazz =
        try {
          Class.forName(candidate)
        } catch (_: ClassNotFoundException) {
          continue
        } catch (_: LinkageError) {
          continue
        }

      val field =
        try {
          reflection.findStaticField(clazz.kotlin, fieldName)
        } catch (_: NoSuchFieldException) {
          continue
        }

      reflection.makeAccessible(field)
      return fromJava(reflection.obtainFieldAccessor(field).getStatic())
    }
    return null
  }

  fun hasField(target: Any, name: String): Boolean = findField(target, name) != null

  fun getField(target: Any, name: String, sandbox: CialloSandbox): CialloValue {
    requireJvmAccess(sandbox, target, "field '" + name + "' on " + target.javaClass.name)
    val field = findField(target, name)
      ?: throw CialloRuntimeException("no field '" + name + "' on " + target.javaClass.name)
    reflection.makeAccessible(field)
    return fromJava(reflection.obtainFieldAccessor(field).get(target))
  }

  fun setField(target: Any, name: String, value: CialloValue, sandbox: CialloSandbox) {
    requireJvmAccess(sandbox, target, "field '" + name + "' on " + target.javaClass.name)
    val field = findField(target, name)
      ?: throw CialloRuntimeException("no field '" + name + "' on " + target.javaClass.name)
    reflection.makeAccessible(field)
    reflection.makeMutable(field)
    reflection.obtainFieldAccessor(field).set(target, toJava(value))
  }

  private fun findField(target: Any, name: String): Field? {
    var clazz: Class<*>? = target.javaClass
    while (clazz != null) {
      try {
        return reflection.findField(clazz.kotlin, name)
      } catch (_: NoSuchFieldException) {
        // continue with the superclass
      }
      clazz = clazz.superclass
    }
    return null
  }

  fun callMethod(
    target: Any,
    name: String,
    args: List<CialloValue>,
    sandbox: CialloSandbox,
  ): CialloValue {
    requireJvmAccess(sandbox, target, "method '" + name + "' on " + target.javaClass.name)
    val javaArgs = args.map { toJava(it) }.toTypedArray()

    val methods = linkedSetOf<Method>()
    var clazz: Class<*>? = target.javaClass
    while (clazz != null) {
      clazz.declaredMethods
        .filter { it.name == name && it.parameterCount == javaArgs.size }
        .forEach { methods += it }
      clazz = clazz.superclass
    }
    target.javaClass.methods
      .filter { it.name == name && it.parameterCount == javaArgs.size }
      .forEach { methods += it }

    for (method in methods) {
      if (!compatible(method.parameterTypes, javaArgs)) continue
      try {
        reflection.makeAccessible(method)
        return fromJava(method.invoke(target, *javaArgs))
      } catch (_: IllegalAccessException) {
        continue
      } catch (_: IllegalArgumentException) {
        continue
      } catch (exception: InvocationTargetException) {
        throw CialloRuntimeException(
          "JVM method " + target.javaClass.name + "." + name + " threw " + exception.targetException,
          cause = exception.targetException,
        )
      }
    }

    throw CialloRuntimeException(
      "no matching method '" + name + "' with " + args.size + " argument(s) on " + target.javaClass.name,
    )
  }

  private fun requireJvmAccess(sandbox: CialloSandbox, target: Any, what: String) {
    if (!sandbox.allowJvmAccess) {
      throw CialloSandboxException("access to " + what)
    }
    if (!sandbox.permitsClass(target.javaClass.name)) {
      throw CialloSandboxException("class " + target.javaClass.name + " is not allowed")
    }
  }

  private fun compatible(types: Array<Class<*>>, args: Array<Any?>): Boolean {
    if (types.size != args.size) return false
    for (index in types.indices) {
      val argument = args[index]
      if (argument == null) {
        if (types[index].isPrimitive) return false
        continue
      }
      if (!boxed(types[index]).isInstance(argument)) return false
    }
    return true
  }

  private fun boxed(type: Class<*>): Class<*> =
    when (type) {
      Int::class.javaPrimitiveType -> Int::class.javaObjectType
      Long::class.javaPrimitiveType -> Long::class.javaObjectType
      Float::class.javaPrimitiveType -> Float::class.javaObjectType
      Double::class.javaPrimitiveType -> Double::class.javaObjectType
      Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
      Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
      Short::class.javaPrimitiveType -> Short::class.javaObjectType
      Char::class.javaPrimitiveType -> Char::class.javaObjectType
      else -> type
    }

  fun toJava(value: CialloValue): Any? =
    when (value) {
      CialloValue.Nil -> null
      is CialloValue.Bool -> value.value
      is CialloValue.IntValue -> value.value
      is CialloValue.FloatValue -> value.value
      is CialloValue.TimeValue -> value.millis
      is CialloValue.Str -> value.value
      is CialloValue.Arr -> value.items.map { toJava(it) }
      is CialloValue.Obj -> value.fields.mapValues { toJava(it.value) }
      is CialloValue.Symbol -> value.name
      is CialloValue.JavaValue -> value.value
      is CialloValue.BlockValue -> value
      is CialloValue.NativeValue -> value
    }

  fun fromJava(value: Any?): CialloValue =
    when (value) {
      null -> CialloValue.Nil
      is CialloValue -> value
      is Boolean -> CialloValue.Bool(value)
      is Byte -> CialloValue.IntValue(value.toInt())
      is Short -> CialloValue.IntValue(value.toInt())
      is Int -> CialloValue.IntValue(value)
      is Long ->
        if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) CialloValue.IntValue(value.toInt())
        else CialloValue.FloatValue(value.toFloat())
      is Float -> CialloValue.FloatValue(value)
      is Double -> CialloValue.FloatValue(value.toFloat())
      is Char -> CialloValue.Str(value.toString())
      is String -> CialloValue.Str(value)
      is CharSequence -> CialloValue.Str(value.toString())
      is Array<*> -> CialloValue.Arr(value.map { fromJava(it) }.toMutableList())
      is Iterable<*> -> CialloValue.Arr(value.map { fromJava(it) }.toMutableList())
      else -> CialloValue.JavaValue(value)
    }
}
