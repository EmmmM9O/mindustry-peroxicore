@file:Suppress("unused", "UNCHECKED_CAST")

package peroxicore.ponder.api

import arc.graphics.Color
import arc.math.geom.Point2
import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.scene.style.Drawable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import mindustry.ctype.UnlockableContent
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.WorldLabel
import mindustry.graphics.Pal
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import peroxicore.ponder.PonderCore
import peroxicore.ponder.api.dsl.BaseTimelineInitializerScope
import peroxicore.ponder.api.dsl.Keyframe
import peroxicore.ponder.api.dsl.PositionScope
import peroxicore.ponder.api.dsl.Promise
import peroxicore.ponder.api.dsl.WorldPonderActionScope
import peroxicore.ponder.scene.BaseTimeline
import peroxicore.ponder.scene.BaseTimelineInitializer
import peroxicore.ponder.scene.PonderTag
import peroxicore.ponder.scene.TimeMark
import peroxicore.ponder.scene.Timeline
import peroxicore.ponder.scene.UnlockableContentPonder

//TODO use ksp to automatic generate this.

/**
 * Java-friendly entry point for registering Ponder scenes.
 *
 * The underlying [peroxicore.ponder.api.dsl] package is a Kotlin DSL that relies on
 * lambda-with-receiver, infix operators and extension functions, none of which are usable
 * from Java. This object bridges it with a plain builder API that uses SAM interfaces, so
 * Java callers can write ordinary lambdas and plain coordinates.
 *

 */
object PonderJava {

  /** Registers [content] with a scene built by [configurer], then returns the built scene. */
  @JvmStatic
  fun register(content: UnlockableContent, configurer: SceneConfigurer): UnlockableContentPonder {
    val scene = build(content, configurer)
    PonderRegistry.contentScenes.put(content, scene)
    return scene
  }

  /** Builds a scene for [content] without registering it. */
  @JvmStatic
  fun build(content: UnlockableContent, configurer: SceneConfigurer): UnlockableContentPonder {
    val scope = SceneScope(content)
    configurer.apply(scope)
    return scope.build()
  }

  /** Whether [content] already has a registered scene. */
  @JvmStatic
  fun has(content: UnlockableContent): Boolean = PonderRegistry.has(content)

  /** Opens the Ponder dialog for [content]. */
  @JvmStatic
  fun show(content: UnlockableContent) = PonderRegistry.show(content)
}

/** SAM used as the top-level scene configurer. Call it with a Java lambda. */
fun interface SceneConfigurer {
  fun apply(scope: SceneScope)
}

/** SAM used to configure a keyframes/baseline timeline. */
fun interface TimelineConfigurer {
  fun apply(scope: TimelineScope)
}

/** SAM used to run world/frame actions. */
fun interface WorldConfigurer {
  fun apply(scope: WorldScope)
}

/**
 * Builds a [UnlockableContentPonder] for one content. Passed into [SceneConfigurer].
 *
 * A scene needs exactly one timeline; use [keyframes] (or [baseline]) inside [SceneConfigurer.apply].
 */
class SceneScope(private val content: UnlockableContent) {
  private val tagSet = mutableSetOf<PonderTag>()
  private var timeline: Timeline? = null

  /** Adds a display tag to the scene. */
  fun tag(name: String): SceneScope {
    tagSet.add(PonderTag(name))
    return this
  }

  /** Adds a display tag to the scene. */
  fun tag(tag: PonderTag): SceneScope {
    tagSet.add(tag)
    return this
  }

  /** Configures a timeline made of keyframes (the common case). */
  fun keyframes(configurer: TimelineConfigurer): SceneScope {
    timeline = TimelineScope().also(configurer::apply).build()
    return this
  }

  /** Configures a plain timeline without frames (only the world initializer runs). */
  fun baseline(configurer: TimelineConfigurer): SceneScope {
    timeline = TimelineScope().also(configurer::apply).build()
    return this
  }

  /** Builds the underlying scene. */
  fun build(): UnlockableContentPonder {
    val tl = requireNotNull(timeline) { "A keyframes/baseline timeline must be set for a scene" }
    return UnlockableContentPonder(content, tl, tagSet)
  }
}

/**
 * Builds a [peroxicore.ponder.scene.Timeline]. Passed into [TimelineConfigurer].
 *
 * Call [world] first to set up the grid, then [frame] to add keyframes. [loop]/[speed]/[duration]
 * mirror the DSL timeline settings.
 */
class TimelineScope {
  var loop: Boolean = true
  var speed: Float = 1f
  var duration: Float = 600f
  private var initializer: BaseTimelineInitializer? = null
  private val frames = mutableListOf<Keyframe>()

  /** Sets whether the timeline loops at its end. */
  fun loop(value: Boolean): TimelineScope {
    loop = value
    return this
  }

  /** Sets the playback speed multiplier. */
  fun speed(value: Float): TimelineScope {
    speed = value
    return this
  }

  /** Sets the total duration of the timeline (in ticks). */
  fun duration(value: Float): TimelineScope {
    duration = value
    return this
  }

  /** Sets up the world grid of [width]x[height] tiles and runs [configurer] on it. */
  fun world(width: Int, height: Int, configurer: WorldConfigurer): TimelineScope {
    initializer =
      BaseTimelineInitializer(width, height) {
        configurer.apply(WorldScope(BaseTimelineInitializerScope(PonderCore)))
      }
    return this
  }

  /** Adds a keyframe at [tick]. */
  fun frame(tick: Float, action: WorldConfigurer): TimelineScope = frame(tick, null, action)

  /** Adds a keyframe at [tick] with a [icon] shown in the timeline. */
  fun frame(tick: Float, icon: Drawable?, action: WorldConfigurer): TimelineScope {
    frames.add(
      Keyframe(tick, icon) {
        action.apply(WorldScope(this))
      }
    )
    return this
  }

  /** Builds the underlying [BaseTimeline]. */
  fun build(): Timeline {
    val init = requireNotNull(initializer) { "world(width, height, ...) must be called before building a timeline" }
    return BaseTimeline(init).apply {
      loop = this@TimelineScope.loop
      speed = this@TimelineScope.speed
      duration = this@TimelineScope.duration
      (marks as MutableList<TimeMark>).addAll(this@TimelineScope.frames)
    }
  }
}

/**
 * Wraps a [WorldPonderActionScope] and exposes its actions with plain Java-friendly
 * coordinates instead of the Kotlin DSL's `Point`/`Vec`/infix style. Passed into [WorldConfigurer]
 * for both `world(...)` setup and `frame(...)` actions.
 */
class WorldScope(private val inner: WorldPonderActionScope) {

  // --- text & labels -------------------------------------------------------

  /** Resolves a `$`/`@` prefixed bundle key, or returns [text] unchanged. */
  fun text(text: String): String = inner.text(text)

  /** Shows a label above tile ([x], [y]). */
  @JvmOverloads
  fun label(x: Int, y: Int, info: String, duration: Float = 2f, flags: Byte = WorldLabel.flagOutline) {
    inner.run { Point2(x, y).label(info, duration, flags) }
  }

  /** Shows a label at a tile [point]. */
  @JvmOverloads
  fun label(point: Point2, info: String, duration: Float = 2f, flags: Byte = WorldLabel.flagOutline) {
    inner.run { point.label(info, duration, flags) }
  }

  /** Shows a label at a world-space [point]. */
  @JvmOverloads
  fun label(point: Vec2, info: String, duration: Float = 2f, flags: Byte = WorldLabel.flagOutline) {
    inner.run { point.label(info, duration, flags) }
  }

  // --- floor & block placement ---------------------------------------------

  /** Fills the whole world grid with [floor]. */
  fun fillFloor(floor: Block) = inner.fillFloor(floor)

  /** Fills the rectangular region [[x1],[y1]]..[[x2],[y2]] with [block]. */
  @JvmOverloads
  fun fill(x1: Int, y1: Int, x2: Int, y2: Int, block: Block, rotation: Int = 0, team: Team? = null) {
    inner.run { PositionScope.Region(x1, y1, x2, y2).fill(block, rotation, team) }
  }

  /** Fills a [size]x[size] region centred on ([x], [y]) with [block]. */
  @JvmOverloads
  fun fillAround(x: Int, y: Int, size: Int, block: Block, rotation: Int = 0, team: Team? = null) {
    inner.run { Point2(x, y).around(size).fill(block, rotation, team) }
  }

  /** Fills a 3x3 region centred on ([x], [y]) with [block]. */
  @JvmOverloads
  fun fillAround3(x: Int, y: Int, block: Block, rotation: Int = 0, team: Team? = null) {
    inner.run { Point2(x, y).around3().fill(block, rotation, team) }
  }

  /** Sets the floor of a single tile. */
  fun setFloor(x: Int, y: Int, floor: Block) {
    inner.run { Point2(x, y).setFloor(floor as Floor) }
  }

  /** Sets a block on a single tile. */
  @JvmOverloads
  fun setBlock(x: Int, y: Int, block: Block, rotation: Int = 0, team: Team? = null) {
    inner.run { Point2(x, y).setBlock(block, rotation, team) }
  }

  /** Sets either a floor or a block on a single tile, depending on [block]. */
  @JvmOverloads
  fun set(x: Int, y: Int, block: Block, rotation: Int = 0, team: Team? = null) {
    inner.run { Point2(x, y).set(block, rotation, team) }
  }

  // --- playback / camera ----------------------------------------------------

  /** Sets the action scope speed multiplier. */
  fun speed(value: Float) = inner.speed(value)

  /** Sets whether building placement is active. */
  fun building(value: Boolean) = inner.building(value)

  /** Sets the camera speed. */
  fun camSpeed(value: Float) = inner.camSpeed(value)

  /** Sets the camera rectangle (world units). */
  fun camRect(value: Rect) = inner.camRect(value)

  /** Starts manual camera control. */
  fun beginCam() = inner.beginCam()

  /** Ends manual camera control. */
  fun endCam() = inner.endCam()

  /** Fits the camera to the whole world. */
  fun full() = inner.full()

  /** Returns the rect covering the whole world (world units). */
  fun fullTiles(): Rect = inner.fullTiles()

  /** Pans the camera to focus on tile ([x], [y]) within [range] tiles. */
  fun focus(x: Int, y: Int, range: Int) {
    inner.run { Point2(x, y).focus(range) }
  }

  // --- effects ---------------------------------------------------------------

  /** Plays a placement effect on tile ([x], [y]). */
  @JvmOverloads
  fun placeEffect(x: Int, y: Int, size: Int = -1) {
    inner.run { Point2(x, y).placeEffect(size) }
  }

  /** Plays a heal effect on tile ([x], [y]). */
  @JvmOverloads
  fun healEffect(x: Int, y: Int, color: Color = Pal.accent) {
    inner.run { Point2(x, y).healEffect(color) }
  }

  // --- building --- ----------------------------------------------------------

  /** Queues a build plan at tile ([x], [y]) for the builder. */
  @JvmOverloads
  fun plan(x: Int, y: Int, block: Block, rotation: Int = 0, config: Any? = null) {
    inner.run { Point2(x, y).plan(block, rotation, config) }
  }

  /**
   * Builds [block] at tile ([x], [y]) over [duration] ticks and returns a [Promise] that
   * resolves to the finished [Building] (or null on failure). Java callers may ignore the
   * return value.
   */
  @JvmOverloads
  fun build(
    x: Int,
    y: Int,
    block: Block,
    rotation: Int = 0,
    duration: Float = 30f,
    team: Team? = null,
    config: Any? = null,
  ): Promise<WorldPonderActionScope, Building?> =
    inner.run { Point2(x, y).build(block, rotation, duration, team, config) }

  /** Applies a configuration to a building at ([x], [y]). */
  fun configure(x: Int, y: Int, value: Any?) {
    inner.run { Point2(x, y).configure(value) }
  }
}
