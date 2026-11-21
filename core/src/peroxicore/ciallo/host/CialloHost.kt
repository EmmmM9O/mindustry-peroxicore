package peroxicore.ciallo.host

import peroxicore.ciallo.*
import peroxicore.ciallo.value.*

/**
 * How a host parameter accepts script values.
 *
 * The set is intentionally small: everything else is a [Host] parameter checked
 * with `is` against a real class.  That keeps dispatch free of reflection and
 * makes the sandbox irrelevant for host bindings.
 */
enum class CialloParamKind {
  Int,
  Float,
  Str,
  Bool,
  Byte,
  Any,
  Block,
  Host,
}

/**
 * One parameter of a host function.
 *
 * [default] is the value used when the caller omits the parameter; `null` means
 * it is required.  A `Float` parameter also accepts `Int` and [CialloTime] (in
 * game ticks), which is how `duration 1s` reaches a `Float` parameter.
 */
class CialloHostParam(
  val name: String,
  val kind: CialloParamKind,
  val hostType: Class<*>? = null,
  val nullable: Boolean = false,
  val default: (() -> Any?)? = null,
) {
  val hasDefault: Boolean get() = default != null

  /** `null` when [value] does not fit, `2` for an exact fit, `1` when coerced. */
  fun match(value: CialloValue): Int? =
    when (kind) {
      CialloParamKind.Int -> if (value is CialloInt) 2 else null
      CialloParamKind.Float ->
        when (value) {
          is CialloFloat -> 2
          is CialloInt -> 1
          is CialloTime -> 1
          else -> null
        }
      CialloParamKind.Str -> if (value is CialloStr) 2 else null
      CialloParamKind.Bool -> if (value is CialloBool) 2 else null
      CialloParamKind.Byte -> if (value is CialloInt) 2 else null
      CialloParamKind.Any -> 2
      CialloParamKind.Block -> if (value is CialloBlockValue) 2 else null
      CialloParamKind.Host ->
        when {
          value === CialloNil.INSTANCE -> if (nullable) 2 else null
          hostType != null && hostType.isInstance(value.hostValue) -> 2
          else -> null
        }
    }

  fun convert(value: CialloValue): Any? =
    when (kind) {
      CialloParamKind.Int -> (value as? CialloInt)?.value ?: mismatch(value)
      CialloParamKind.Float ->
        when (value) {
          is CialloFloat -> value.value
          is CialloInt -> value.value.toFloat()
          is CialloTime -> value.ticks
          else -> mismatch(value)
        }
      CialloParamKind.Str -> (value as? CialloStr)?.value ?: mismatch(value)
      CialloParamKind.Bool -> (value as? CialloBool)?.value ?: mismatch(value)
      CialloParamKind.Byte -> (value as? CialloInt)?.value?.toByte() ?: mismatch(value)
      CialloParamKind.Any -> unwrapped(value)
      CialloParamKind.Block -> value as? CialloBlockValue ?: mismatch(value)
      CialloParamKind.Host -> if (value === CialloNil.INSTANCE) null else value.hostValue
    }

  /** The raw JVM value of a script value, used by `Any` parameters. */
  private fun unwrapped(value: CialloValue): Any? =
    when (value) {
      CialloNil.INSTANCE -> null
      is CialloInt -> value.value
      is CialloFloat -> value.value
      is CialloStr -> value.value
      is CialloBool -> value.value
      is CialloTime -> value.ticks
      is CialloArr -> value.items.map { unwrapped(it) }
      else -> value.hostValue
    }

  private fun mismatch(value: CialloValue): Nothing =
    throw CialloRuntimeException("argument '$name' expects ${describe()}, got ${value.kind.displayName}")

  fun describe(): String =
    when (kind) {
      CialloParamKind.Int -> "Int"
      CialloParamKind.Float -> "Float or Time"
      CialloParamKind.Str -> "String"
      CialloParamKind.Bool -> "Bool"
      CialloParamKind.Byte -> "Int"
      CialloParamKind.Any -> "Any"
      CialloParamKind.Block -> "Block"
      CialloParamKind.Host -> hostType?.simpleName ?: "Host"
    }
}

/** Shorthands used by generated bindings. */
object CialloParams {
  fun int(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Int, default = default)

  fun float(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Float, default = default)

  fun str(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Str, default = default)

  fun bool(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Bool, default = default)

  fun byte(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Byte, default = default)

  fun any(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Any, default = default)

  fun block(name: String, default: (() -> Any?)? = null) =
    CialloHostParam(name, CialloParamKind.Block, default = default)

  fun host(
    name: String,
    type: Class<*>,
    nullable: Boolean = false,
    default: (() -> Any?)? = null,
  ) = CialloHostParam(name, CialloParamKind.Host, type, nullable, default)
}

/** One host overload: its parameters and how to call the host side with converted values. */
class CialloHostOverload(
  val params: List<CialloHostParam>,
  val invoke: (CialloCallContext, Any?, List<Any?>) -> Any?,
  /** Whether the call is a world action, so a recorder should defer it. */
  val record: Boolean = false,
)

/**
 * A world action captured by a [CialloActionRecorder].
 *
 * [replay] performs the host call later, against the scene that is live at that
 * moment.  Receiver and arguments are already resolved, so no re-parsing is
 * involved.
 */
fun interface CialloRecordedStep {
  fun replay(context: CialloCallContext): CialloValue
}

/** Receives the steps a script produces while it is being recorded. */
fun interface CialloActionRecorder {
  fun record(step: CialloRecordedStep)
}

/**
 * A named group of host overloads with Ciallo's resolution rules.
 *
 * Dispatch picks the exact match first (`Int` over `Float` for an `Int`
 * argument), then a coerced one, preferring the overload that exposes the most
 * parameters so optional modifiers stay available.
 *
 * An overload marked [pending] returns a [CialloPendingAction] instead of calling
 * the host: that is what lets `build @Blocks.duo config @Items.copper at(4, 4)`
 * be written as one sentence.
 */
class CialloHostFunctions(
  val name: String,
  private val wrap: (Any?) -> CialloValue,
) {
  private val overloads = mutableListOf<CialloHostOverload>()
  private val pending = mutableSetOf<Int>()

  /** Resolves the position receiver for a pending action. */
  var position: (() -> CialloHostFunctions)? = null

  val size: Int get() = overloads.size

  fun add(
    params: List<CialloHostParam>,
    record: Boolean = false,
    invoke: (CialloCallContext, Any?, List<Any?>) -> Any?,
  ): Int {
    overloads += CialloHostOverload(params, invoke, record)
    return overloads.size - 1
  }

  /** Non-null while actions are captured instead of run. */
  var recorder: CialloActionRecorder? = null

  fun markPending(index: Int) {
    pending += index
  }

  fun params(index: Int): List<CialloHostParam> = overloads[index].params

  /** Every parameter name across the overloads; used by pending-action modifiers. */
  fun paramNames(): Set<String> = overloads.flatMapTo(linkedSetOf()) { overload -> overload.params.map { it.name } }

  fun isPending(index: Int): Boolean = index in pending

  /**
   * Picks the overload for [args].
   *
   * [named] lists modifier names the caller intends to set, so an overload that declares them wins
   * even when the positional arguments alone would fit a shorter signature.  Remaining parameters
   * must have defaults.
   */
  fun select(
    context: CialloCallContext,
    args: List<CialloValue>,
    named: Set<String> = emptySet(),
  ): Int = selectResolved(args.map { context.resolve(it) }, named)

  private fun selectResolved(
    args: List<CialloValue>,
    named: Set<String>,
  ): Int {
    var best = -1
    var bestScore = -1
    var bestSize = -1
    overloads.forEachIndexed { index, overload ->
      if (args.size > overload.params.size) return@forEachIndexed
      var score = 0
      for (position in args.indices) {
        val match = overload.params[position].match(args[position]) ?: return@forEachIndexed
        score += match
      }
      for (position in args.size until overload.params.size) {
        val param = overload.params[position]
        if (param.name in named) continue
        if (!param.hasDefault) return@forEachIndexed
      }
      if (named.any { name -> overload.params.none { it.name == name } }) return@forEachIndexed
      if (score > bestScore || (score == bestScore && overload.params.size > bestSize)) {
        best = index
        bestScore = score
        bestSize = overload.params.size
      }
    }
    if (best < 0) throw CialloRuntimeException(noOverloadMessage(args))
    return best
  }

  private fun noOverloadMessage(args: List<CialloValue>): String {
    val actual = args.joinToString(", ") { it.kind.displayName }
    val expected =
      overloads.joinToString(" or ") { overload ->
        overload.params.joinToString(", ", "(", ")") { it.describe() }
      }
    val suffix = if (expected.isEmpty()) "" else "; expected $expected"
    return "function '$name' has no overload for ($actual)$suffix"
  }

  /** Calls an overload, filling [positional] and then param [named] overrides. */
  fun invokeRaw(
    context: CialloCallContext,
    receiver: Any?,
    positional: List<CialloValue>,
    named: Map<String, CialloValue> = emptyMap(),
    overload: Int = select(context, positional),
  ): Any? {
    val params = overloads[overload].params
    val resolved = positional.map { context.resolve(it) }
    val values = ArrayList<Any?>(params.size)
    params.forEachIndexed { index, param ->
      val override = named[param.name]
      val value = override ?: resolved.getOrNull(index)
      values +=
        when {
          value != null -> param.convert(context.resolve(value))
          param.default != null -> param.default.invoke()
          else -> throw CialloRuntimeException("'$name' is missing argument '${param.name}'")
        }
    }
    return overloads[overload].invoke(context, receiver, values)
  }

  fun invoke(
    context: CialloCallContext,
    receiver: Any?,
    positional: List<CialloValue>,
    named: Map<String, CialloValue> = emptyMap(),
    overload: Int = select(context, positional),
  ): CialloValue {
    if (isPending(overload)) {
      val position =
        position?.invoke()
          ?: throw CialloRuntimeException("'$name' cannot be positioned here")
      return CialloPendingAction(name, this, overload, positional, position)
    }
    return call(context, receiver, positional, named, overload)
  }

  /**
   * Runs an overload even when it is marked pending; used by member handlers and
   * [CialloPendingAction.invokeAt].
   *
   * With a [recorder] installed, an overload marked `record` is deferred instead.
   */
  fun call(
    context: CialloCallContext,
    receiver: Any?,
    positional: List<CialloValue>,
    named: Map<String, CialloValue> = emptyMap(),
    overload: Int = select(context, positional, named.keys),
  ): CialloValue {
    val positionalValues = positional.map { context.resolve(it) }
    val namedValues = named.mapValues { (_, value) -> context.resolve(value) }
    val recorder = recorder
    if (recorder != null && overloads[overload].record) {
      recorder.record { replay ->
        wrap(invokeRaw(replay, receiver, positionalValues, namedValues, overload))
      }
      return CialloNil.INSTANCE
    }
    return wrap(invokeRaw(context, receiver, positionalValues, namedValues, overload))
  }
}

/**
 * A deferred host call: values are collected first, then applied at a position.
 *
 * Calling a modifier returns the same pending action, so modifiers chain; `at`
 * resolves the position and finally runs the host call.  Reading it without a
 * position reports what is missing.
 */
class CialloPendingAction(
  val label: String,
  private val group: CialloHostFunctions,
  private val overload: Int,
  val positional: List<CialloValue>,
  private val position: CialloHostFunctions,
) : CialloValue {
  val named: MutableMap<String, CialloValue> = linkedMapOf()

  override val kind: CialloKind get() = CialloKind.Host

  override val members: CialloMembers get() = CialloPendingMembers

  override fun display(): String = "<action:$label>"

  fun accepts(name: String): Boolean = name in group.paramNames()

  fun modifierNames(): List<String> = group.paramNames().toList()

  /** `at(x, y)`: computes the receiver position, then runs the action on it. */
  fun invokeAt(
    context: CialloCallContext,
    args: List<CialloValue>,
  ): CialloValue {
    val receiver = position.invokeRaw(context, null, args)
    val selected = group.select(context, positional, named.keys)
    return group.call(context, receiver, positional, named, selected)
  }
}

/** Members of [CialloPendingAction]: its modifiers, plus `at`. */
object CialloPendingMembers : CialloMembers by pendingTable()

private fun pendingTable(): CialloMemberTable =
  memberTable {
    property("action") { _, value -> CialloStr((value as CialloPendingAction).label) }

    property("modifiers") { _, value ->
      CialloArr((value as CialloPendingAction).modifierNames().mapTo(mutableListOf()) { CialloStr(it) })
    }

    callFallback { context, value, name, args ->
      val pending = value as? CialloPendingAction ?: return@callFallback null
      if (name == "at") {
        if (args.size != 2) {
          throw CialloRuntimeException("'at' expects 2 arguments, got ${args.size}")
        }
        return@callFallback pending.invokeAt(context, args)
      }
      if (!pending.accepts(name)) return@callFallback null
      if (args.isEmpty()) return@callFallback value
      if (args.size != 1) {
        throw CialloRuntimeException("modifier '$name' takes exactly one argument")
      }
      pending.named[name] = args[0]
      value
    }
  }

/** Where a [CialloHostBinder] publishes its free functions. */
fun interface CialloNativeTarget {
  fun native(name: String, function: CialloNativeFunction)
}

/** Registers into an environment, so the names are visible to every script it runs. */
fun CialloEnvironment.nativeTarget(): CialloNativeTarget =
  CialloNativeTarget { name, function -> native(name, function) }

/**
 * Registers host functions, receiver members and deferred actions.
 *
 * Free functions go to a [CialloNativeTarget]; members travel with the host
 * values [wrap] produces, so the same binder serves either a whole environment
 * or one block scope.  Generated Ciallo bindings drive this class.
 */
class CialloHostBinder(
  private val target: CialloNativeTarget,
) {
  private val aliases = linkedMapOf<Class<*>, String>()
  private val tables = linkedMapOf<Class<*>, CialloMemberTableBuilder>()
  private val functions = linkedMapOf<String, CialloHostFunctions>()
  private val members = linkedMapOf<Class<*>, MutableMap<String, CialloHostFunctions>>()
  private val skipped = linkedSetOf<String>()

  /** Name of the free function a pending action uses to compute its position. */
  var positionFunction: String = "at"

  /**
   * Captures world actions instead of running them.
   *
   * Set while a scene body is read; the captured steps replay later against the
   * live scene.  Non-action functions (setting the timeline rate, tags, ...) still
   * run immediately.
   */
  var recorder: CialloActionRecorder? = null
    set(value) {
      field = value
      functions.values.forEach { it.recorder = value }
      members.values.forEach { groups -> groups.values.forEach { it.recorder = value } }
    }

  /**
   * Names the binding must not install.
   *
   * Let a host take over a name the template also declares (Ponder, for example, uses `speed` for
   * the timeline rate while the template's `speed` changes the live playback multiplier).
   */
  fun skip(vararg names: String) {
    skipped += names
  }

  fun alias(
    type: Class<*>,
    name: String = type.simpleName,
  ) {
    aliases[type] = name
    tables.getOrPut(type) { CialloMemberTableBuilder() }
  }

  private fun aliasName(type: Class<*>): String = aliases[type] ?: type.simpleName

  /** Member tables are frozen after [install], so wrapping per frame allocates no tables. */
  private val frozenTables = linkedMapOf<Class<*>, CialloMembers>()

  fun wrap(value: Any?): CialloValue {
    if (value == null) return CialloNil.INSTANCE
    if (value is CialloValue) return value
    val alias = aliases.entries.lastOrNull { (type, _) -> type.isInstance(value) }
    if (alias != null) {
      val table = frozenTables[alias.key] ?: tables.getValue(alias.key).build()
      return CialloHostValue(value, alias.value, table)
    }
    return CialloHostValues.of(value)
  }

  private fun functionGroup(name: String): CialloHostFunctions =
    functions.getOrPut(name) { CialloHostFunctions(name, ::wrap).also { it.recorder = recorder } }

  /**
   * Registers a free function overload, e.g. `at(Int, Int)`.
   *
   * [record] marks a world action: with a [recorder] installed the call is
   * deferred instead of executed, which is how a scene body stores its work.
   */
  fun free(
    name: String,
    params: List<CialloHostParam>,
    record: Boolean = false,
    body: (CialloCallContext, List<Any?>) -> Any?,
  ): Int = functionGroup(name).add(params, record) { context, _, args -> body(context, args) }

  /** Registers a member overload, e.g. `Point.setBlock(block)`. */
  fun member(
    type: Class<*>,
    name: String,
    params: List<CialloHostParam>,
    record: Boolean = false,
    body: (CialloCallContext, Any, List<Any?>) -> Any?,
  ): Int = memberGroup(type, name).add(params, record) { context, receiver, args ->
    body(context, receiver ?: throw CialloRuntimeException("'$name' needs a receiver"), args)
  }

  private fun memberGroup(
    type: Class<*>,
    name: String,
  ): CialloHostFunctions {
    alias(type)
    return members.getOrPut(type) { linkedMapOf() }.getOrPut(name) {
      CialloHostFunctions("$name on ${aliasName(type)}", ::wrap).also { it.recorder = recorder }
    }
  }

  /**
   * Registers a deferred action: callable as a free function that returns a
   * [CialloPendingAction], and directly as a member of [type].
   */
  fun action(
    type: Class<*>,
    name: String,
    params: List<CialloHostParam>,
    body: (CialloCallContext, Any, List<Any?>) -> Any?,
  ): Int {
    val free = functionGroup(name)
    val freeIndex =
      free.add(params, record = true) { context, receiver, args ->
        body(context, receiver ?: throw CialloRuntimeException("'$name' needs a receiver"), args)
      }
    free.markPending(freeIndex)
    if (free.position == null) {
      free.position = {
        functions[positionFunction]
          ?: throw CialloRuntimeException("host binding has no position function '$positionFunction'")
      }
    }
    return member(type, name, params, record = true, body = body)
  }

  /** Publishes every registered free function; member tables travel with wrapped values. */
  fun install() {
    tables.forEach { (type, builder) -> frozenTables[type] = builder.build() }
    functions.forEach { (name, group) ->
      if (name in skipped) return@forEach
      target.native(name) { context, args -> group.invoke(context, null, args) }
    }
    members.forEach { (type, groups) ->
      val table = tables.getValue(type)
      groups.forEach { (name, group) ->
        if (name in skipped) return@forEach
        table.method(name) { context, value, args ->
          val host =
            (value as? CialloHostValue)?.value
              ?: throw CialloRuntimeException("'$name' needs a ${aliasName(type)}")
          group.invoke(context, host, args)
        }
      }
    }
  }
}

/** Wraps plain returns; host objects fall back to an opaque [CialloHostValue]. */
object CialloHostValues {
  private val opaque: CialloMembers by lazy { memberTable {} }

  fun of(value: Any?): CialloValue =
    when (value) {
      null -> CialloNil.INSTANCE
      is CialloValue -> value
      is Boolean -> CialloBool(value)
      is Int -> CialloInt(value)
      is Float -> CialloFloat(value)
      is Double -> CialloFloat(value.toFloat())
      is Long -> CialloInt(value.toInt())
      is Short -> CialloInt(value.toInt())
      is Byte -> CialloInt(value.toInt())
      is String -> CialloStr(value)
      is Char -> CialloStr(value.toString())
      is Unit -> CialloNil.INSTANCE
      is CialloTime -> value
      is Iterable<*> -> CialloArr(value.map { of(it) }.toMutableList())
      else -> CialloHostValue(value, value.javaClass.simpleName, opaque)
    }
}
