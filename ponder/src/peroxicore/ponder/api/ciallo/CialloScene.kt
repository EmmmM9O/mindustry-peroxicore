package peroxicore.ponder.api.ciallo

import arc.scene.style.*
import mindustry.ctype.*
import mindustry.world.*
import peroxicore.ciallo.*
import peroxicore.ciallo.host.*
import peroxicore.ciallo.value.*
import peroxicore.ponder.api.dsl.*
import peroxicore.ponder.scene.*
import peroxicore.ponder.world.*

/**
 * A Ponder scene written in Ciallo.
 *
 * The body runs once, when the scene is registered, in **recording** mode:
 * timeline settings (`loop`, `speed`, `duration`, `tag`, `world`, `frame`) take
 * effect immediately, while world actions such as `build @Blocks.duo at(4, 4)`
 * are captured by the binder's recorder.  The captured steps, the `world` body
 * and every `frame` body replay later in **live** mode — when Ponder starts the
 * timeline or reaches a keyframe — against [PonderCore].
 *
 * This gives scripts the same lifecycle as the Kotlin DSL, while the action
 * surface stays generated from the Ponder templates by KSP.
 */
class CialloSceneScope(
  /** The content this scene documents; also the scene title. */
  val content: UnlockableContent,
  private val environment: CialloEnvironment,
) {
  /** The action surface, shared by the generated bindings and the recorded steps. */
  val api: WorldPonderActionScopeApi = WorldPonderActionScope(peroxicore.ponder.PonderCore)

  internal val natives = CialloNatives()
  private val binder = CialloHostBinder(natives.target())

  val tags: MutableSet<PonderTag> = linkedSetOf()
  var loop = true
  var speed = 1f
  var duration = 600f
  var width = 0
  var height = 0

  var worldBlock: CialloBlockValue? = null
  val frames = mutableListOf<CialloFrame>()

  /** World actions captured while the body ran, in source order. */
  val steps = mutableListOf<CialloRecordedStep>()

  /**
   * Installs the scene's surface: the generated template actions plus the
   * timeline functions the template does not declare.
   *
   * The timeline functions are registered last, so `speed`/`duration` here mean
   * the timeline settings, not the template's live playback multiplier.
   */
  fun prepare() {
    WorldPonderActionScopeCiallo.bind(api, binder)
    registerTimelineFunctions()
    binder.recorder = CialloActionRecorder { step -> steps += step }
  }

  /** Runs the scene body in recording mode, in a scope backed by [natives]. */
  fun define(body: CialloBlockValue) {
    prepare()
    environment.runInScope(body, natives)
  }

  /**
   * Runs a `world { }` or `frame { }` body in live mode: actions execute instead
   * of being recorded, because the timeline now drives the scene.
   */
  internal fun replay(action: CialloBlockValue) {
    binder.recorder = null
    try {
      environment.runInScope(action, natives)
    } finally {
      binder.recorder = CialloActionRecorder { step -> steps += step }
    }
  }

  /** Replays one recorded step against the live action surface. */
  internal fun replay(step: CialloRecordedStep) {
    val interpreter = CialloInterpreter(environment, environment.globalScope)
    step.replay(CialloCallContext(interpreter, environment, environment.globalScope))
  }

  private fun registerTimelineFunctions() {
    natives.function("tag") { context, args ->
      tags += PonderTag(context.resolve(args[0]).render())
      CialloNil.INSTANCE
    }

    natives.function("loop") { context, args ->
      loop = context.bool(args[0], "loop")
      CialloNil.INSTANCE
    }

    natives.function("speed") { context, args ->
      speed = context.floatOrTime(args[0], "speed")
      CialloNil.INSTANCE
    }

    natives.function("duration") { context, args ->
      duration = context.floatOrTime(args[0], "duration")
      CialloNil.INSTANCE
    }

    natives.function("world") { context, args ->
      val numbers = args.map { context.resolve(it) }.filterIsInstance<CialloNumber>()
      if (numbers.size < 2) {
        throw CialloRuntimeException("world expects <width> <height>, optionally with a block")
      }
      width = numbers[0].asDouble().toInt()
      height = numbers[1].asDouble().toInt()
      (args.lastOrNull() as? CialloBlockValue)?.let { worldBlock = it }
      CialloNil.INSTANCE
    }

    natives.function("frame") { context, args ->
      val block =
        args.lastOrNull() as? CialloBlockValue
          ?: throw CialloRuntimeException("frame expects a block")
      val tick = context.floatOrTime(args.first(), "frame")
      frames += CialloFrame(tick, block)
      CialloNil.INSTANCE
    }
  }

  /** Builds the Ponder scene. */
  fun build(): UnlockableContentPonder =
    UnlockableContentPonder(content, CialloTimeline(this), tags)
}

/** A number or a time in game ticks. */
internal fun CialloCallContext.floatOrTime(
  value: CialloValue,
  what: String,
): Float =
  when (val resolved = resolve(value)) {
    is CialloTime -> resolved.ticks
    is CialloInt -> resolved.value.toFloat()
    is CialloFloat -> resolved.value
    else -> throw CialloRuntimeException("$what expects a number or a time")
  }

/** One scripted keyframe. */
class CialloFrame(
  val tick: Float,
  val action: CialloBlockValue,
)

/**
 * The timeline of a [CialloSceneScope].
 *
 * `create` builds the grid, `init` replays the world body and the recorded steps,
 * and every mark replays one frame body.  `loop`/`speed`/`duration` read from
 * the scope, so the script's settings drive the real timeline.
 */
class CialloTimeline(
  private val scope: CialloSceneScope,
) : Timeline,
  InitializerComp {
  override val initializer: TimelineInitializer =
    object : TimelineInitializer {
      override fun create(context: TimelineContext): Tiles =
        Tiles(scope.width.coerceAtLeast(1), scope.height.coerceAtLeast(1)).apply { fillPonder() }

      override fun init(context: TimelineContext) {
        scope.worldBlock?.let { scope.replay(it) }
        scope.steps.toList().forEach { scope.replay(it) }
      }
    }

  override val marks: List<TimeMark> = scope.frames.map { CialloTimeMark(it, scope) }

  override var index = 0

  override var loop: Boolean
    get() = scope.loop
    set(value) {
      scope.loop = value
    }
  override val speed: Float get() = scope.speed
  override var duration: Float
    get() = scope.duration
    set(value) {
      scope.duration = value
    }

  override fun create() {
    callCreate(context)
  }

  override fun init() {
    index = 0
    callInit(context)
  }

  override fun update() {
    if (index >= marks.size) return
    val mark = marks[index]
    if (currentTick >= mark.tick) {
      mark.pass()
      index++
    }
  }

  override fun end() {}
}

/** A keyframe that replays its script block against the live scene. */
class CialloTimeMark(
  private val frame: CialloFrame,
  private val scope: CialloSceneScope,
) : TimeMark {
  override val tick: Float get() = frame.tick

  override val icon: Drawable? get() = null

  override fun pass() {
    scope.replay(frame.action)
  }
}
