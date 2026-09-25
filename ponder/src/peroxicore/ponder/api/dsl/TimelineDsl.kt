package peroxicore.ponder.api.dsl

import arc.scene.style.*
import peroxicore.ponder.*
import peroxicore.ponder.scene.*

@PonderDslMarker
abstract class TimelineScope : PositionScope {
  abstract fun loop(value: Boolean)

  abstract fun speed(value: Float)

  abstract fun duration(value: Float)
}

@PonderDslMarker
open class BaseTimelineScope : TimelineScope() {
  var loop_ = true
  var spped_ = 1f
  var duration_ = 600f
  var initializer_: TimelineInitializer? = null
  val initializerR
    get() = requireNotNull(initializer_) { "initializer must be set for scene" }

  override fun loop(value: Boolean) {
    loop_ = value
  }

  override fun speed(value: Float) {
    spped_ = value
  }

  override fun duration(value: Float) {
    duration_ = value
  }

  fun initializer(value: TimelineInitializer) {
    initializer_ = value
  }

  open fun build() =
    BaseTimeline(initializerR).apply {
      loop = loop_
      speed = spped_
      duration = duration_
    }
}

open class Keyframe(
  override val tick: Float,
  override val icon: Drawable?,
  val action: KeyframeScope.() -> Unit,
) : TimeMark {
  val scope: KeyframeScope
    get() = KeyframeScope.scope
  val context: TimelineContext
    get() = PonderCore

  override fun pass() {
    scope.action()
  }
}

@PonderDslMarker
open class KeyframeScope(
  context: TimelineContext,
) : WorldPonderActionScope(context) {
  companion object {
    val scope = KeyframeScope(PonderCore)
  }
}

@PonderDslMarker
open class KeyframeTimelineScope : BaseTimelineScope() {
  val frames = mutableListOf<Keyframe>()

  fun frame(
    tick: Float,
    icon: Drawable? = null,
    block: KeyframeScope.() -> Unit,
  ) {
    frames.add(Keyframe(tick, icon, block))
  }

  override fun build() =
    super.build().apply {
      @Suppress("UNCHECKED_CAST")
      (marks as MutableList<Keyframe>).addAll(frames)
    }
}

inline fun PonderSceneScope.baseline(block: BaseTimelineScope.() -> Unit) {
  timeline(BaseTimelineScope().apply(block).build())
}

inline fun PonderSceneScope.keyframes(block: KeyframeTimelineScope.() -> Unit) {
  timeline(KeyframeTimelineScope().apply(block).build())
}

@PonderDslMarker
class BaseTimelineInitializerScope(
  context: TimelineContext,
) : WorldPonderActionScope(context) {
  companion object {
    val scope = BaseTimelineInitializerScope(PonderCore)
  }
}

inline fun BaseTimelineScope.world(
  width: Int,
  height: Int,
  crossinline block: BaseTimelineInitializerScope.() -> Unit,
) {
  initializer(
    BaseTimelineInitializer(width, height) {
      val scope = BaseTimelineInitializerScope.scope
      scope.block()
    }
  )
}
