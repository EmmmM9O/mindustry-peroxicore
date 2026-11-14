package peroxicore.ciallo

import peroxicore.ciallo.ops.*
import peroxicore.ciallo.value.*

/**
 * Controls what a Ciallo script may reach.
 *
 * In safe mode scripts can only use builtins and symbols registered by the host.
 * Full access additionally allows JVM fields and methods through UniverseKit's
 * reflection layer.  [allowedPackages] limits which classes may be reflected on;
 * an empty list means "no package restriction" (full access only).
 */
private val cialloDefaultPackages =
  listOf("mindustry.content", "mindustry", "arc", "arc.util", "peroxicore")

data class CialloSandbox(
  val allowJvmAccess: Boolean = false,
  val allowedPackages: List<String> = cialloDefaultPackages,
  val allowFunc: ((String) -> Boolean)? = null
) {
  fun permitsClass(className: String): Boolean =
    (allowedPackages.isEmpty() ||
      allowedPackages.any { className == it || className.startsWith(it + ".") }) &&
      (allowFunc?.invoke(className) ?: true)

  companion object {
    val SAFE: CialloSandbox = CialloSandbox()
    val FULL: CialloSandbox = CialloSandbox(allowJvmAccess = true, allowedPackages = cialloDefaultPackages)
  }
}

/** How a [CialloJavaValue] reaches the host JVM.  Installed with [CialloEnvironment.javaBridge]. */
interface CialloJavaBridge {
  fun hasField(target: Any, name: String): Boolean

  fun getMember(target: Any, name: String, sandbox: CialloSandbox): CialloValue

  fun setMember(target: Any, name: String, value: CialloValue, sandbox: CialloSandbox)

  fun callMember(target: Any, name: String, args: List<CialloValue>, sandbox: CialloSandbox): CialloValue

  fun toJava(value: CialloValue): Any?

  fun fromJava(value: Any?): CialloValue

  /** Resolves `@com.example.Type.field`. */
  fun resolveStatic(sandbox: CialloSandbox, path: List<String>): CialloValue?

  fun applyBlock(
    context: CialloCallContext,
    target: Any,
    block: CialloBlockValue,
  ): CialloValue? = null
}

/**
 * Host configuration for the interpreter.
 *
 * [symbols] are named host values (for example "Blocks.duo"), [natives] are
 * host functions.  The standard Ciallo builtins are installed automatically.
 *
 * Extensibility hooks:
 * - [operations] holds the operator, free-function and name-resolution components;
 * - [values] maps a class to the value that wraps it (for host value families);
 * - [javaBridge] exposes host JVM objects.
 */
class CialloEnvironment(
  val output: (String) -> Unit = { println(it) },
  val sandbox: CialloSandbox = CialloSandbox.SAFE,
  /** Optional host components notified about the values a script produces. */
  val outputComp: CialloOutputComp? = null,
) {
  /** Global variables mounted by scripts run in this environment. */
  val globalScope: CialloScope = CialloScope.global()

  val symbols: MutableMap<String, CialloValue> = linkedMapOf()
  val natives: MutableMap<String, CialloNative> = linkedMapOf()

  /** The operator, free-function and resolution components of this environment. */
  val operations: CialloOperations = CialloOperations.standard()

  /** Host value families, consulted by [wrapHost]; the most specific registered type wins. */
  val values: MutableMap<Class<*>, (Any) -> CialloValue?> = linkedMapOf()

  /** Reads JVM members of [CialloJavaValue]s and resolves static names. */
  var javaBridge: CialloJavaBridge = CialloJavaAccess(this)

  init {
    operations.addAll(CialloBuiltins.components)
  }

  fun symbol(path: String, value: CialloValue) {
    symbols[path] = value
  }

  /** Registers an untyped overload; raw arguments are passed to [function]. */
  fun native(name: String, function: CialloNativeFunction) {
    natives.getOrPut(name) { CialloNative(name) }.add(function)
  }

  /** Registers a strict overload requiring exactly [parameters] after symbol resolution. */
  fun native(name: String, parameters: List<CialloType>, function: CialloNativeFunction) {
    natives.getOrPut(name) { CialloNative(name) }.add(parameters, function)
  }

  /** Seeds a global variable for scripts run in this environment. */
  fun global(name: String, value: CialloValue) {
    globalScope.defineGlobal(name, value)
  }

  fun global(name: String): CialloValue? = globalScope.lookup(name)

  /** Registers a component: operator, free function, member handler, suffix parser or resolver. */
  fun operation(component: CialloOperation) {
    operations.add(component)
  }

  /**
   * Wraps host objects of [type] in a custom value family.
   *
   * [wrapper] receives the host object; return `null` to fall back to
   * [CialloJavaValue].
   */
  fun <T : Any> value(type: Class<T>, wrapper: (T) -> CialloValue?) {
    values[type] = { target -> @Suppress("UNCHECKED_CAST") wrapper(target as T) }
  }

  /** Wraps a host value through the registered value families, else as [CialloJavaValue]. */
  fun fromJava(value: Any?): CialloValue = wrapHost(value) ?: CialloNil.INSTANCE

  /** The value family registered for the class of [value], if any. */
  fun wrapHost(value: Any?): CialloValue? {
    if (value == null) return null
    if (value is CialloValue) return value
    values.entries
      .lastOrNull { (type, _) -> type.isInstance(value) }
      ?.value
      ?.invoke(value)
      ?.let { return it }
    return null
  }

  fun printLine(text: String) {
    output(text)
    outputComp?.printed(text)
  }
}
