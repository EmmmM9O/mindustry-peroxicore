package peroxicore.ponder

import arc.*
import arc.files.*
import arc.graphics.g2d.*
import arc.struct.*
import arc.util.*
import mindustry.*
import mindustry.ai.*
import mindustry.content.*
import mindustry.core.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.net.*
import mindustry.ui.*
import mindustry.world.*
import peroxicore.ponder.api.*
import peroxicore.ponder.input.*
import peroxicore.ponder.render.*
import peroxicore.ponder.scene.*
import peroxicore.ponder.ui.*
import universe.util.reflect.*
import kotlin.Unit
// import peroxicore.struct.AttachedProperty

// var Player.pondering: Boolean by AttachedProperty { false }

var Net.active by accessBoolean("active")
var Player.unit_: mindustry.gen.Unit? by accessField("unit")

fun scaleToMax(
  width: Float,
  height: Float,
  max: Float,
): Pair<Float, Float> {
  val scale = if (width > height) max / width else max / height
  return width * scale to height * scale
}

fun scaleToMin(
  width: Float,
  height: Float,
  min: Float,
): Pair<Float, Float> {
  val scale = if (width > height) min / height else min / width
  return width * scale to height * scale
}

class PonderTask(
  var id: Int,
  var duration: Float,
  var run: TimelineContext.() -> Unit,
  var endRun: (TimelineContext.() -> Unit)? = null,
)

fun PonderTask.end(block: TimelineContext.() -> Unit): PonderTask {
  endRun = block
  return this
}

inline fun PonderTask.endThen(crossinline block: TimelineContext.() -> Unit): PonderTask {
  val origin = endRun
  endRun = {
    origin?.invoke(this)
    block()
  }
  return this
}

fun Tiles.unitWidth() = Vars.tilesize * width

fun Tiles.unitHeight() = Vars.tilesize * height

object PonderCore : ApplicationListener, TimelineContext {
  lateinit var originIndexer: BlockIndexer
  var indexer = PonderBlockIndexer()
  lateinit var originTiles: Tiles

  lateinit var ponder: PonderDialog

  var sceneEnd = false
  var rendering = false

  var renderer: PonderRenderer = OriginPonderRenderer()
  override var input: PonderInput = DefaultPonderInput()

  override var tiles: Tiles = Vars.world.tiles
  override var currentTick = 0f

  override var speed = 1f

  override val team: Team
    get() = Vars.player?.team() ?: Team.sharded

  val state =
    GameState().apply {
      rules.infiniteResources = true
      rules.disableUnitCap = true
    }
  lateinit var originState: GameState

  var originDelta = 1f
  var originActive = false

  val tasks = Seq<PonderTask>()
  val freeTasks = IntQueue()

  var ponderer: mindustry.gen.Unit? = null
  var originUnit: mindustry.gen.Unit? = null
  override val builder by this::ponderer

  override val camera by renderer::camera

  override val camRect by input::camRect

  override var camSpeed by input::camSpeed

  override var camControl by input::camControl

  override val ratio
    get() = tiles.width.toFloat() / tiles.height

  override var isBuilding by input::camControl

  val view
    get() = ponder.image

  fun hovered(): Displayable? = input.hovered()

  var scene: PonderScene? = null
    set(value) {
      if (field != null && !sceneEnd) endScene()
      if (value != null) beginScene(value)
      field = value
    }

  override fun init() {}

  override fun resize(
    width: Int,
    height: Int,
  ) {}

  override fun pause() {}

  override fun resume() {}

  override fun dispose() {}

  override fun exit() {}

  override fun fileDropped(file: Fi) {}

  override fun task(
    duration: Float,
    block: TimelineContext.() -> Unit,
  ): PonderTask =
    if (freeTasks.isEmpty) {
      PonderTask(0, duration, block).also {
        tasks.add(it)
        it.id = tasks.size - 1
      }
    } else {
      val id = freeTasks.removeFirst()
      tasks.get(id).apply {
        this.duration = duration
        this.run = block
      }
    }

  fun updateTasks() {
    for (task in tasks) {
      if (task.duration >= 0f) {
        task.duration -= Time.delta
        task.run(this)
        if (task.duration <= 0f) {
          task.endRun?.let { it(this) }
          task.endRun = null
          freeTasks.addLast(task.id)
        }
      }
    }
  }

  fun reloadTasks() {
    tasks.clear()
    freeTasks.clear()
  }

  fun beginScene(scene: PonderScene) {
    currentTick = 0f
    speed = 1f
    scene.timeline.create()
    begin()
    Draw.flush()
    PonderGroups.clear()
    reloadTasks()
    PonderUI.reload()
    renderer.beforeWorld()
    input.beforeWorld()
    Vars.world.isGenerating = true
    scene.timeline.init()
    Vars.world.isGenerating = false
    for (build in PonderGroups.build) {
      build.checkAllowUpdate()
    }
    for (tile in tiles) {
      if (tile.build != null) {
        tile.build.updateProximity()
      }
    }
    PonderGroups.resize(
      -Vars.finalWorldBounds,
      -Vars.finalWorldBounds,
      tiles.width * Vars.tilesize + Vars.finalWorldBounds * 2,
      tiles.height * Vars.tilesize + Vars.finalWorldBounds * 2
    )
    renderer.reload()
    ponderer = UnitTypes.alpha.spawn(team, 16f, 16f)
    renderer.beginWorld()
    input.beginWorld()
    indexer.reload()
    Events.fire(PonderTrigger.worldLoad)
    end()
    sceneEnd = false
  }

  fun endScene() {
    val current = scene ?: return
    begin()
    current.timeline.end()
    renderer.endWorld()
    input.endWorld()
    end()
    PonderGroups.clear()
    currentTick = 0f
    sceneEnd = true
  }

  fun setup() {
    input.setup()
    renderer.setup()
    PonderGroups.setup()
  }

  fun initPonder() {
    input.init()
    renderer.init()
    ponder = PonderDialog()
    PonderRegistry.load()
    PonderUI.load()
    Events.fire(PonderTrigger.load)
    Time.run(1f) {
//      PonderRegistry.show(Blocks.electrolyzer)
    }
  }

  fun beginDialog() {
    restart()
  }

  fun endDialog() {
    endScene()
  }

  var skipDelta = 10f

  fun restart() {
    val current = scene ?: return
    if (!sceneEnd) endScene()
    beginScene(current)
  }

  fun next() {
    val current = scene ?: return
    if (sceneEnd) restart() else jumpTo(current.timeline.index + 1)
  }

  fun jumpTo(index: Int) {
    val current = scene ?: return
    val timeline = current.timeline
    if (timeline.index == index) return
    if (timeline.index > index) restart()
    if (index >= timeline.marks.size + 1 || index <= 0) {
      restart()
      return
    }
    val ori = Time.delta
    Time.delta = skipDelta
    begin()
    jump = true
    while (timeline.index < index) {
      update()
    }
    jump = false
    end()
    Time.delta = ori
  }

  fun previous() {
    val current = scene ?: return
    val index = current.timeline.index
    if (index <= 1) {
      restart()
    } else {
      if (sceneEnd) restart()
      jumpTo(index - 1)
    }
  }

  override var jump = false

  override fun update() {
    if (!this::ponder.isInitialized) return
    val current = scene
    if (!ponder.display || current == null) {
//      Vars.player.pondering = false
      return
    }
    if (rendering) return
//    Vars.player.pondering = true
    val timeline = current.timeline
    rendering = true
    if (!jump) {
      begin()
      originDelta = Time.delta
      Time.delta = originDelta * timeline.speed * speed
    }

    if (!sceneEnd) {
      currentTick += Time.delta

      PonderGroups.update()
      Events.fire(PonderTrigger.update)

      timeline.update()
    }
    updateTasks()
    ponderer?.apply {
      x = 16f
      y = 16f
    }

    input.update()
    input.render()
    renderer.render()

    rendering = false
    Draw.flush()
    if (!jump) {
      Time.delta = originDelta
      end()
    }

    if (!sceneEnd) {
      if (currentTick >= timeline.duration) {
        endScene()
        if (timeline.loop) {
          beginScene(current)
        }
      }
    }
  }

  fun begin() {
    if (Vars.state != state) originState = Vars.state
    if (Vars.world.tiles != tiles) originTiles = Vars.world.tiles
    if (Vars.player?.unit() != ponderer) originUnit = Vars.player?.unit()
    if (ponderer != null) Vars.player?.apply { unit_ = ponderer }
    if (Vars.indexer != indexer) originIndexer = Vars.indexer
    Vars.world.tiles = tiles
    Vars.indexer = indexer
    Vars.state = state
    originActive = Vars.net.active
    Vars.net.active = false
    input.begin()
    renderer.begin()
    PonderGroups.begin()
  }

  fun end() {
    Vars.state = originState
    Vars.world.tiles = originTiles
    Vars.indexer = originIndexer
    Vars.net.active = originActive
    Vars.player?.apply { unit_ = originUnit }
    input.end()
    renderer.end()
    PonderGroups.end()
  }
}
