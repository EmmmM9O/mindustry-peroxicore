package peroxicore.ponder.api.dsl

import mindustry.ctype.*
import peroxicore.ponder.scene.*

@DslMarker
annotation class PonderDslMarker

inline fun UnlockableContent.scene(block: UnlockableContentPonderScope.() -> Unit) =
  UnlockableContentPonderScope(this).apply(block).build()

@PonderDslMarker
open class PonderSceneScope {
  val tags = mutableSetOf<PonderTag>()
  var timeline_: Timeline? = null
  val timelineR
    get() = requireNotNull(timeline_) { "timeline must be set for scene" }

  fun tag(value: PonderTag) {
    tags.add(value)
  }

  fun timeline(value: Timeline) {
    timeline_ = value
  }
}

@PonderDslMarker
open class UnlockableContentPonderScope(
  val content: UnlockableContent,
) : PonderSceneScope() {
  fun build() = UnlockableContentPonder(content, timelineR, tags)
}
