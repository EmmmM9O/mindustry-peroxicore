package peroxicore.ponder.scene

import mindustry.world.*
import peroxicore.annotations.*
import peroxicore.ponder.world.*

interface TimelineInitializer {
  fun create(context: TimelineContext): Tiles

  fun init(context: TimelineContext)
}

interface InitializerComp {
  val initializer: TimelineInitializer

  @MethodEntry(entryMethod = "create", context = ["context"], insert = InsertPosition.HEAD)
  fun callCreate(context: TimelineContext) {
    context.tiles = initializer.create(context)
  }

  @MethodEntry(entryMethod = "init", context = ["context"], insert = InsertPosition.HEAD)
  fun callInit(context: TimelineContext) {
    initializer.init(context)
  }
}

open class BaseTimelineInitializer(
  val width: Int,
  val height: Int,
  private val initBlock: TimelineContext.() -> Unit,
) : TimelineInitializer {
  override fun create(context: TimelineContext) = Tiles(width, height).apply(Tiles::fillPonder)

  override fun init(context: TimelineContext) {
    context.apply(initBlock)
  }
}
