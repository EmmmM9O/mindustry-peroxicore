package peroxicore.ponder.input

import arc.*
import arc.func.*
import arc.input.*
import arc.input.GestureDetector.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import mindustry.*
import mindustry.Vars.*
import mindustry.content.*
import mindustry.core.*
import mindustry.entities.units.*
import mindustry.input.*
import mindustry.input.InputHandler.*
import mindustry.ui.Displayable
import mindustry.world.*
import peroxicore.ponder.*
import universe.util.reflect.*

var InputHandler.playerPlanTree: QuadTree<BuildPlan> by accessField("playerPlanTree")
var InputHandler.selectPlanTree: QuadTree<BuildPlan> by accessField("selectPlanTree")

var InputHandler.allPlans: Eachable<BuildPlan> by accessField("allPlans")
var InputHandler.allSelectLines: Eachable<BuildPlan> by accessField("allSelectLines")
var InputHandler.allRenderPlansConfig: Eachable<BuildPlan> by accessField("allRenderPlansConfig")

class DefaultPonderInput : PonderInput {
  val tmpVec = Vec2()
  override val camRect = Rect()
  override var camSpeed = 0.1f
  override var camControl = false

  override var active = false
  override var isBuilding = false

  lateinit var input: InputHandler
  lateinit var inputHandler: DefaultPonderInputHandler
  val taskPool = Pools.get(InputTask::class.java, ::InputTask)!!
  val tasks = Seq<InputTask>()

  fun task(
    x: Float,
    y: Float,
    func: InputTask.Function,
  ) {
    tasks.add(
      taskPool.obtain().apply {
        this.input = inputHandler
        this.x = x
        this.y = y
        this.function = func
      }
    )
  }

  fun updateTasks() {
    tasks.each {
      it.invoke()
      taskPool.free(it)
    }
    tasks.clear()
  }

  override fun hovered(): Displayable? {
    if (!inputHandler.hit) return null
    val pos = inputHandler.mouseWorld
    val hoverTile = PonderCore.tiles.get(World.toTile(pos.x), World.toTile(pos.y))
    if (hoverTile != null && hoverTile.inMapArea()) {
      if (hoverTile.build != null && hoverTile.build.displayable() && hoverTile.build.inMapArea()) {
        return hoverTile.build
      }
      if (
        (hoverTile.drop() != null && hoverTile.block() == Blocks.air) ||
        hoverTile.wallDrop() != null ||
        hoverTile.floor().liquidDrop != null
      ) {
        return hoverTile
      }
    }
    return null
  }

  override fun setup() {}

  override fun init() {
    input = Vars.control.input
    inputHandler = DefaultPonderInputHandler(this)
    inputHandler.add()
  }

  override fun render() {}

  override fun update() {
    if (camControl) {
      tmpVec.set(camRect.x + camRect.width / 2f, camRect.y + camRect.height / 2f)
      val f1 = camera.position.dst2(tmpVec) <= 1f
      camera.position.lerpDelta(tmpVec, camSpeed)
      tmpVec.set(camera.width, camera.height)
      val f2 = tmpVec.dst2(camRect.width, camRect.height) <= 1f
      tmpVec.lerpDelta(camRect.width, camRect.height, camSpeed)
      camera.width = tmpVec.x
      camera.height = tmpVec.y
      if (f1 && f2) {
        camControl = false
      }
    }
    inputHandler.update()
    updateTasks()
  }

  override fun begin() {
    active = true
    inputHandler.begin()
  }

  override fun end() {
    active = false
    inputHandler.end()
  }

  override fun beginWorld() {}

  override fun beforeWorld() {
    camControl = false
    camRect.set(-4f, -4f, camera.width, camera.height)
  }

  override fun endWorld() {
    inputHandler.endWorld()
  }

  override fun drawBottom() {
    input.drawBuildPlans()
  }

  override fun drawTop() {}

  override fun validPlace(
    x: Int,
    y: Int,
    type: Block,
    rotation: Int,
  ) = input.validPlace(x, y, type, rotation)
}

open class InputTask : Pool.Poolable {
  typealias Function = DefaultPonderInputHandler.(Float, Float) -> Unit

  lateinit var input: DefaultPonderInputHandler
  var x = 0f
  var y = 0f
  var function: Function? = null

  fun invoke() {
    function?.invoke(input, x, y)
  }

  override fun reset() {
    function = null
  }
}

class DefaultPonderInputHandler(
  val input: DefaultPonderInput,
) : InputProcessor,
  GestureListener {
  lateinit var detector: GestureDetector
  val mouseWorld = Vec2()
  val mouseVec = Vec2()
  var hit = false
  val camera
    get() = PonderCore.camera

  val view
    get() = PonderCore.view

  val tiles
    get() = PonderCore.tiles

  val active
    get() = PonderCore.ponder.display

  val origin = input.input

  val linePlans = Seq<BuildPlan>()
  val selectPlans = Seq<BuildPlan>()
  val lastPlans = Queue<BuildPlan>()
  var playerPlanTree = QuadTree<BuildPlan>(Rect())
  var selectPlanTree = QuadTree<BuildPlan>(Rect())
  lateinit var allPlans: Eachable<BuildPlan>
  lateinit var allSelectLines: Eachable<BuildPlan>
  lateinit var allRenderPlansConfig: Eachable<BuildPlan>

  lateinit var originLinePlans: Seq<BuildPlan>
  lateinit var originSelectPlans: Seq<BuildPlan>
  lateinit var originLastPlans: Queue<BuildPlan>
  lateinit var originPlayerPlanTree: QuadTree<BuildPlan>
  lateinit var originSelectPlanTree: QuadTree<BuildPlan>
  lateinit var originAllPlans: Eachable<BuildPlan>
  lateinit var originAllSelectLines: Eachable<BuildPlan>
  lateinit var originAllRenderPlansConfig: Eachable<BuildPlan>

  var needReset = true

  init {
    reset()
    needReset = true
  }

  fun endWorld() {
    needReset = true
  }

  fun begin() {
    if (needReset) {
      reset()
    }
    originLinePlans = origin.linePlans
    originSelectPlans = origin.selectPlans
    originLastPlans = origin.lastPlans
    originPlayerPlanTree = origin.playerPlanTree
    originSelectPlanTree = origin.selectPlanTree
    originAllPlans = origin.allPlans
    originAllSelectLines = origin.allSelectLines
    originAllRenderPlansConfig = origin.allRenderPlansConfig

    origin.linePlans = linePlans
    origin.selectPlans = selectPlans
    origin.lastPlans = lastPlans
    origin.playerPlanTree = playerPlanTree
    origin.selectPlanTree = selectPlanTree
    origin.allPlans = allPlans
    origin.allSelectLines = allSelectLines
    origin.allRenderPlansConfig = allRenderPlansConfig
  }

  fun end() {
    origin.linePlans = originLinePlans
    origin.selectPlans = originSelectPlans
    origin.lastPlans = originLastPlans
    origin.playerPlanTree = originPlayerPlanTree
    origin.selectPlanTree = originSelectPlanTree
    origin.allPlans = originAllPlans
    origin.allSelectLines = originAllSelectLines
    origin.allRenderPlansConfig = originAllRenderPlansConfig
  }

  fun reset() {
    playerPlanTree =
      QuadTree<BuildPlan>(Rect(0f, 0f, tiles.unitWidth().toFloat(), tiles.unitHeight().toFloat()))
    selectPlanTree =
      QuadTree<BuildPlan>(Rect(0f, 0f, tiles.unitWidth().toFloat(), tiles.unitHeight().toFloat()))
    createPlanLists()
    needReset = false
  }

  fun updateSelectQuadtree() {
    selectPlanTree.clear()
    selectPlans.each(selectPlanTree::insert)
  }

  fun createPlanLists() {
    allPlans =
      object : QueryEachable(playerPlanTree, linePlans) {
        override fun find(
          x: Int,
          y: Int,
          size: Int,
          check: Boolf<BuildPlan>,
        ): BuildPlan? {
          val plan = super.find(x, y, size, check)
          if (plan != null) return plan

          return selectPlanTree.find(
            x * tilesize - tilesize / 2f * size,
            y * tilesize - tilesize / 2f * size,
            size * tilesize.toFloat(),
            size * tilesize.toFloat(),
            check
          )
        }
      }

    allSelectLines = QueryEachable(null, selectPlans, linePlans)
    allRenderPlansConfig = QueryEachable(playerPlanTree, selectPlans)
  }

  fun add() {
    detector = GestureDetector(20f, 0.5f, 0.3f, 0.15f, this)
    Core.input.inputMultiplexer.addProcessor(0, this)
    Core.input.inputMultiplexer.addProcessor(0, detector)
  }

  fun update() {
    val local = view.stageToLocalCoordinates(Core.input.mouse())
    if (view.hit(local.x, local.y, false) != null) {
      camera.unproject(mouseWorld.set(local), 0f, 0f, view.width, view.height)
      hit = true
    } else {
      hit = false
    }

    updateSelectQuadtree()
    playerPlanTree.clear()
    val unit = PonderCore.ponderer
    if (unit == null) return
    if (unit.isValid()) {
      unit.plans.each(playerPlanTree::insert)
    }
    if (unit.canBuild()) {
      unit.updateBuilding(input.isBuilding)
    }
  }

  fun world(vec: Vec2) =
    camera.unproject(vec.set(view.stageToLocalCoordinates(vec)), 0f, 0f, view.width, view.height)

  fun tileAt(vec: Vec2): Tile? {
    val pos = world(vec)
    return tiles.get(World.toTile(pos.x), World.toTile(pos.y))
  }

  override fun longPress(
    x: Float,
    y: Float,
  ): Boolean {
    if (!active) return false
    input.task(x, y) { x, y ->
      val tile = tileAt(mouseVec.set(x, y))
      val pos = mouseVec
      if (tile == null) return@task
      Fx.select.at(pos)
    }
    return hit
  }

  override fun keyDown(keycode: KeyCode): Boolean = false

  override fun keyUp(keycode: KeyCode): Boolean = false

  override fun keyTyped(character: Char): Boolean = false

  override fun touchDown(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun touchUp(
    screenX: Int,
    screenY: Int,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun touchDragged(
    screenX: Int,
    screenY: Int,
    pointer: Int,
  ): Boolean = false

  override fun mouseMoved(
    screenX: Int,
    screenY: Int,
  ): Boolean = false

  override fun scrolled(
    amountX: Float,
    amountY: Float,
  ): Boolean = false

  override fun touchDown(
    x: Float,
    y: Float,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun tap(
    x: Float,
    y: Float,
    count: Int,
    button: KeyCode,
  ): Boolean = false

  override fun fling(
    velocityX: Float,
    velocityY: Float,
    button: KeyCode,
  ): Boolean = false

  override fun pan(
    x: Float,
    y: Float,
    deltaX: Float,
    deltaY: Float,
  ): Boolean = false

  override fun panStop(
    x: Float,
    y: Float,
    pointer: Int,
    button: KeyCode,
  ): Boolean = false

  override fun zoom(
    initialDistance: Float,
    distance: Float,
  ): Boolean = false

  override fun pinch(
    initialPointer1: Vec2,
    initialPointer2: Vec2,
    pointer1: Vec2,
    pointer2: Vec2,
  ): Boolean = false

  override fun pinchStop() {}
}
