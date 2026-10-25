package peroxicore.annotations

/**
 * Marks an interface as an API template.
 *
 * A KSP processor generates an interface in [targetPackage] named [name] which extends the template
 * and injects the action helpers described by [Actionable], [ActionEnd] and [ToAction]. The
 * template remains the compiled implementation, so method bodies and overloads stay the single
 * source of truth. This is intentionally independent from Ponder so the same mechanism can be
 * reused for other generated APIs.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ApiTemplate(
  /** Package the generated API is written to. Required. */
  val targetPackage: String,
  /** Name of the generated API. Defaults to the template name without the trailing "Template". */
  val name: String = "",
  /** Also generate a Java friendly facade next to the Kotlin API. */
  val generateJava: Boolean = false,
  /** Package of the generated Java friendly facade. */
  val javaPackage: String = "",
  /** Name of the generated Java world entry point. */
  val javaName: String = "",
  /** Fully qualified annotations copied onto the generated Kotlin interface. */
  val annotations: Array<String> = [],
)

/**
 * Marks a type alias that can be used as an action receiver.
 *
 * For "typealias Vec = Vec2" the generator adds "typealias VecAction<Res> = Vec.() -> Res" to the
 * generated API and a Java handle named [javaName] when a Java facade is requested.
 */
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
annotation class Actionable(
  /** Name of the generated Java handle class. Empty means the type is not exposed to Java. */
  val javaName: String = ""
)

/**
 * Marks a function that can end an action.
 *
 * For a function without an extension receiver whose return type is [Actionable], the generator
 * emits "fun <Res> XAction<Res>.name(...)", which runs the action at the produced position. This is
 * what allows "build(block).at(x, y)".
 */
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.BINARY) annotation class ActionEnd

/**
 * Marks an action function that should also be callable before its position is known.
 *
 * For "fun Point.label(text: String)" the generator emits "fun label(text: String):
 * PointAction<Unit> = { this.label(text) }", so callers can write "label(text).at(x, y)".
 */
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.BINARY) annotation class ToAction
