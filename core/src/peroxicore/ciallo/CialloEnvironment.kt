package peroxicore.ciallo

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
    allowedPackages.isEmpty() ||
      allowedPackages.any { className == it || className.startsWith(it + ".") } ||
      (allowFunc?.invoke(className) ?: true)

  companion object {
    val SAFE: CialloSandbox = CialloSandbox()
    val FULL: CialloSandbox = CialloSandbox(allowJvmAccess = true, allowedPackages = cialloDefaultPackages)
  }
}

/*
/** Mutable world state produced by a world { ... } block. */
class CialloWorld {
  var width: Int = 0
  var height: Int = 0
  var fill: CialloValue? = null
}

/** Host-visible results of executing a Ciallo script. */
class CialloScene {
  var target: CialloValue? = null
  var world: CialloWorld? = null
  val builds: MutableList<CialloValue.BuildAction> = mutableListOf()
  val labels: MutableList<CialloValue.LabelAction> = mutableListOf()
  val frames: MutableList<CialloValue.FrameAction> = mutableListOf()
}
*/

/**
 * Host configuration for the interpreter.
 *
 * [symbols] are named host values (for example "Blocks.duo"), [natives] are
 * host functions.  The standard Ciallo builtins are installed automatically.
 */
class CialloEnvironment(
  val output: (String) -> Unit = { println(it) },
  val sandbox: CialloSandbox = CialloSandbox.SAFE,
) {
  /** Global variables mounted by scripts run in this environment. */
  val globalScope: CialloScope = CialloScope.global()

  val symbols: MutableMap<String, CialloValue> = linkedMapOf()
  val natives: MutableMap<String, CialloNative> = linkedMapOf()
//  val scene: CialloScene = CialloScene()

  init {
    CialloBuiltins.install(this)
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

  fun printLine(text: String) {
    output(text)
  }
}
