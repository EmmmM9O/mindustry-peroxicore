package peroxicore.ciallo

/** A map-like backing store for a scope, used by object-backed blocks. */
interface CialloScopeBacking {
  fun get(name: String): CialloValue?

  fun contains(name: String): Boolean

  fun set(name: String, value: CialloValue): Boolean
}

/** Exposes the fields of a Ciallo object as the variables of a scope. */
class CialloObjScopeBacking(private val obj: CialloValue.Obj) : CialloScopeBacking {
  override fun get(name: String): CialloValue? = obj.fields[name]

  override fun contains(name: String): Boolean = obj.fields.containsKey(name)

  override fun set(name: String, value: CialloValue): Boolean {
    if (!obj.fields.containsKey(name)) return false
    obj.fields[name] = value
    return true
  }
}

/** Exposes the fields of a host JVM object as the variables of a scope. */
class CialloJavaScopeBacking(
  private val target: Any,
  private val sandbox: CialloSandbox,
) : CialloScopeBacking {
  override fun get(name: String): CialloValue? =
    if (CialloJavaAccess.hasField(target, name)) {
      CialloJavaAccess.getField(target, name, sandbox)
    } else {
      null
    }

  override fun contains(name: String): Boolean = CialloJavaAccess.hasField(target, name)

  override fun set(name: String, value: CialloValue): Boolean {
    if (!CialloJavaAccess.hasField(target, name)) return false
    CialloJavaAccess.setField(target, name, value, sandbox)
    return true
  }
}

/**
 * Lexical scope used by the Ciallo interpreter.
 *
 * The root scope is the global scope.  [local] definitions live in the scope
 * where they are executed; [mount] definitions always go to the root.  A scope
 * may also be backed by an object, in which case its fields behave like local
 * variables for that block.
 */
class CialloScope private constructor(
  val parent: CialloScope?,
  val global: Boolean,
  private val backing: CialloScopeBacking?,
) {
  private val values = LinkedHashMap<String, CialloValue>()

  companion object {
    private val reserved = setOf("local", "mount", "set")

    fun global(): CialloScope = CialloScope(null, true, null)

    fun checkName(name: String) {
      if (name in reserved) {
        throw CialloRuntimeException("'$name' is a keyword and cannot be used as a variable name")
      }
    }
  }

  fun child(backing: CialloScopeBacking? = null): CialloScope = CialloScope(this, false, backing)

  fun root(): CialloScope {
    var scope: CialloScope = this
    while (scope.parent != null) {
      scope = scope.parent
    }
    return scope
  }

  fun containsLocal(name: String): Boolean =
    values.containsKey(name) || backing?.contains(name) == true

  fun lookup(name: String): CialloValue? {
    var scope: CialloScope? = this
    while (scope != null) {
      scope.values[name]?.let { return it }
      scope.backing?.get(name)?.let { return it }
      scope = scope.parent
    }
    return null
  }

  fun defineLocal(name: String, value: CialloValue) {
    checkName(name)
    if (containsLocal(name)) {
      throw CialloRuntimeException("local '$name' is already defined in this scope")
    }
    values[name] = value
  }

  fun defineGlobal(name: String, value: CialloValue) {
    checkName(name)
    val root = root()
    if (root.containsLocal(name)) {
      throw CialloRuntimeException("global '$name' is already defined")
    }
    root.values[name] = value
  }

  fun assign(name: String, value: CialloValue) {
    checkName(name)
    var scope: CialloScope? = this
    while (scope != null) {
      if (scope.values.containsKey(name)) {
        scope.values[name] = value
        return
      }
      val backing = scope.backing
      if (backing != null && backing.set(name, value)) {
        return
      }
      scope = scope.parent
    }
    throw CialloRuntimeException("cannot set undefined variable '$name'")
  }

  fun defineAll(values: Map<String, CialloValue>) {
    values.forEach { (name, value) ->
      if (containsLocal(name)) {
        throw CialloRuntimeException("binding '$name' is already defined in this scope")
      }
      this.values[name] = value
    }
  }

  fun localValues(): Map<String, CialloValue> = values
}
