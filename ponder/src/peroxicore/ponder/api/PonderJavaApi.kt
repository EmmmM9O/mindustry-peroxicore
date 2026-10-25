@file:Suppress("unused", "UNCHECKED_CAST")

package peroxicore.ponder.api

import arc.scene.style.Drawable
import kotlin.jvm.JvmStatic
import mindustry.ctype.UnlockableContent
import peroxicore.ponder.api.dsl.BaseTimelineInitializerScope
import peroxicore.ponder.api.dsl.Keyframe
import peroxicore.ponder.api.java.PonderJavaWorld
import peroxicore.ponder.scene.BaseTimeline
import peroxicore.ponder.scene.BaseTimelineInitializer
import peroxicore.ponder.scene.PonderTag
import peroxicore.ponder.scene.TimeMark
import peroxicore.ponder.scene.Timeline
import peroxicore.ponder.scene.UnlockableContentPonder

/**
 * Java-friendly entry point for registering Ponder scenes.
 *
 * The action surface itself is generated from the Ponder templates by KSP into
 * [peroxicore.ponder.api.java]. This object only keeps the registration/lifecycle helpers.
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
  fun apply(scope: PonderJavaWorld)
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
        configurer.apply(PonderJavaWorld(BaseTimelineInitializerScope(this)))
      }
    return this
  }

  /** Adds a keyframe at [tick]. */
  fun frame(tick: Float, action: WorldConfigurer): TimelineScope = frame(tick, null, action)

  /** Adds a keyframe at [tick] with a [icon] shown in the timeline. */
  fun frame(tick: Float, icon: Drawable?, action: WorldConfigurer): TimelineScope {
    frames.add(
      Keyframe(tick, icon) {
        action.apply(PonderJavaWorld(this))
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
