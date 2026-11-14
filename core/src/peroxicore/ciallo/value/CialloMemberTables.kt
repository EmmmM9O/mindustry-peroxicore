package peroxicore.ciallo.value

import peroxicore.ciallo.*

/**
 * A name -> handler table, the [CialloMembers] of one value type.
 *
 * Properties and call handlers live in separate maps, but [canCall] consults
 * both, so a value that answers a property can also answer it with arguments.
 * A table may declare fallbacks: that is how a container hands a miss down to
 * the members of the value it wraps, and how a value family installs a default
 * (the JVM bridge of [CialloJavaValue]).
 */
class CialloMemberTable(
  private val memberEntries: Map<String, CialloMemberFn> = emptyMap(),
  private val callEntries: Map<String, CialloCallFn> = emptyMap(),
  private val memberFallback: CialloMemberStep? = null,
  private val callFallback: CialloCallStep? = null,
  private val aliases: Map<String, String> = emptyMap(),
) : CialloMembers {
  val properties: Collection<String> get() = memberEntries.keys

  /** Every member this table advertises to completion and documentation. */
  fun exposedNames(): Collection<String> = memberEntries.keys + callEntries.keys + aliases.keys

  fun canRead(name: String): Boolean = name in memberEntries

  fun canCall(name: String): Boolean =
    name in callEntries || name in memberEntries || callEntries.containsKey(aliases[name])

  /** True when this table answers names it does not list, so callers must try it. */
  val open: Boolean get() = memberFallback != null || callFallback != null

  override fun member(context: CialloCallContext, value: CialloValue, name: String): CialloValue? =
    memberEntries[name]?.invoke(context, value) ?: memberFallback?.invoke(context, value, name)

  override fun call(
    context: CialloCallContext,
    value: CialloValue,
    name: String,
    args: List<CialloValue>,
  ): CialloValue? {
    (callEntries[name] ?: callEntries[aliases[name]])?.let { return it.invoke(context, value, args) }
    memberEntries[name]?.let { return it.invoke(context, value) }
    return callFallback?.invoke(context, value, name, args)
  }
}

fun interface CialloMemberFn {
  fun invoke(context: CialloCallContext, value: CialloValue): CialloValue?
}

fun interface CialloCallFn {
  fun invoke(context: CialloCallContext, value: CialloValue, args: List<CialloValue>): CialloValue?
}

fun interface CialloMemberStep {
  fun invoke(context: CialloCallContext, value: CialloValue, name: String): CialloValue?
}

fun interface CialloCallStep {
  fun invoke(context: CialloCallContext, value: CialloValue, name: String, args: List<CialloValue>): CialloValue?
}

fun CialloMembers.table(): CialloMemberTable? = this as? CialloMemberTable

/** The member table answering for [value], following [CialloValue.memberTarget]. */
fun CialloMembers.tableFor(value: CialloValue): CialloMemberTable? =
  (if (value.memberTarget() === value) this else value.memberTarget().members).table()

/** The properties a value exposes to completion and documentation. */
fun CialloValue.properties(): Collection<String> =
  (this as? CialloPropertySource)?.propertyNames() ?: members.tableFor(this)?.exposedNames() ?: emptyList()

inline fun memberTable(build: CialloMemberTableBuilder.() -> Unit): CialloMemberTable =
  CialloMemberTableBuilder().apply(build).build()

/**
 * Supports `object Members : CialloMembers by exposedTable { ... }`.
 *
 * The object's constructor runs the block and fills [table] before the object is
 * published, and the enclosing object's own initialisation runs before any other
 * object initialised with [exposedTable] can be touched.
 */
fun exposedTable(): CialloExposedTable = CialloExposedTable()

/** Kept for readability at call sites that build the delegate eagerly. */
operator fun CialloExposedTable.invoke(build: CialloMemberTableBuilder.() -> Unit): CialloExposedTable =
  apply(build)

open class CialloMemberTableBuilder {
  private val memberEntries = linkedMapOf<String, CialloMemberFn>()
  private val callEntries = linkedMapOf<String, CialloCallFn>()
  private val aliases = linkedMapOf<String, String>()
  private var memberFallback: CialloMemberStep? = null
  private var callFallback: CialloCallStep? = null

  /** `value.name`; also answers `value.name()` with no arguments. */
  fun property(name: String, block: CialloMemberFn) {
    memberEntries[name] = block
  }

  /** `value.name(args)`. */
  fun method(name: String, block: CialloCallFn) {
    callEntries[name] = block
  }

  /** A second callable name for an existing call entry. */
  fun alias(alias: String, target: String) {
    aliases[alias] = target
  }

  fun memberFallback(step: CialloMemberStep) {
    memberFallback = step
  }

  fun callFallback(step: CialloCallStep) {
    callFallback = step
  }

  open fun build(): CialloMemberTable =
    CialloMemberTable(memberEntries, callEntries, memberFallback, callFallback, aliases)
}

/**
 * The delegate of `object Members : CialloMembers by exposedTable { ... }`.
 *
 * The object's constructor collects the entries before the object is published,
 * and the enclosing object initialises before any object that uses
 * [CialloExposedTable.table] is touched, so the table is always complete.
 */
class CialloExposedTable : CialloMemberTableBuilder(), CialloMembers {
  val table: CialloMemberTable get() = build()

  override fun member(context: CialloCallContext, value: CialloValue, name: String): CialloValue? =
    (table as CialloMembers).member(context, value, name)

  override fun call(
    context: CialloCallContext,
    value: CialloValue,
    name: String,
    args: List<CialloValue>,
  ): CialloValue? = (table as CialloMembers).call(context, value, name, args)
}

/** Requires exactly [count] arguments; [name] is the member or function being called. */
fun requireArgs(name: String, args: List<CialloValue>, count: Int) {
  if (args.size != count) {
    throw CialloRuntimeException("'" + name + "' expects " + count + " argument(s), got " + args.size)
  }
}
