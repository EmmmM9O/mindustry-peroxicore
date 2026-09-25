package peroxicore.ponder.scene

import arc.graphics.*
import arc.math.geom.*
import arc.scene.style.*
import mindustry.game.*
import mindustry.world.*
import peroxicore.ponder.*
import peroxicore.ponder.input.*

interface TimelineContext {
  var tiles: Tiles
  var currentTick: Float
  var speed: Float
  var jump: Boolean
  val team: Team
  val builder: mindustry.gen.Unit?
  val camera: Camera
  val camRect: Rect
  var camSpeed: Float
  var camControl: Boolean
  val ratio: Float
  var isBuilding: Boolean
  val input: PonderInput

  fun task(
    duration: Float,
    block: TimelineContext.() -> Unit,
  ): PonderTask
}

interface TimeMark {
  val tick: Float
  val icon: Drawable?

  fun pass()
}

interface Timeline {
  val context: TimelineContext
    get() = PonderCore
  val tiles: Tiles
    get() = context.tiles
  val currentTick: Float
    get() = context.currentTick
  var duration: Float
  val marks: List<TimeMark>
  var index: Int
  var loop: Boolean
    get() = false
    set(value) {}
  val speed: Float
    get() = 1f

  fun create()

  fun init()

  fun update()

  fun end()
}

open class BaseTimeline(
  override val initializer: TimelineInitializer,
) : Timeline,
  InitializerComp {
  override var loop = true
  override var speed = 1f
  override var duration = 1200f
  override val marks: List<TimeMark> = mutableListOf()
  override var index = 0

  override fun create() {}

  override fun init() {
    index = 0
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
