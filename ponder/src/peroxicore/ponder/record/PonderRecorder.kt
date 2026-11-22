package peroxicore.ponder.record

import arc.*
import arc.graphics.*
import arc.math.geom.*
import arc.scene.style.*
import arc.util.*
import java.util.*
import mindustry.*
import mindustry.content.*
import mindustry.core.*
import mindustry.ctype.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.input.*
import mindustry.type.*
import mindustry.world.*
import mindustry.world.blocks.environment.*
import peroxicore.*
import peroxicore.ponder.*
import peroxicore.ponder.api.dsl.*
import peroxicore.ponder.scene.*
import peroxicore.ponder.world.*
import peroxicore.util.*

/**
 * 一次录制中的单个改动。
 *
 * [x] / [y] 是相对框选区域左上角的 tile 坐标，因此预览与导出时可以直接当作
 * `world(width, height)` 内的局部坐标使用。
 */
sealed class RecordedAction(open val x: Int, open val y: Int) {
  data class Place(
    override val x: Int,
    override val y: Int,
    val block: String,
    val rotation: Int,
    val config: Any? = null,
  ) : RecordedAction(x, y)

  data class Remove(override val x: Int, override val y: Int) : RecordedAction(x, y)

  data class Configure(override val x: Int, override val y: Int, val value: Any?) :
    RecordedAction(x, y)
}

/** 一个关键帧：在 [tick] 时刻瞬间发生的一组改动。设置面板可以直接修改 [tick] 与 [icon]。 */
data class PonderKeyframe(
  var tick: Float,
  val actions: List<RecordedAction>,
  var icon: Drawable? = null,
)

/** 合并后的地形矩形，坐标相对框选区域左上角，[right] / [bottom] 为不包含的右/下边界。 */
data class TileRect(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val data: Short,
) {
  val right: Int
    get() = x + width
  val bottom: Int
    get() = y + height
}

/** 合并后的建筑矩形；只描述同 id、同朝向、同配置的建筑中心格。 */
data class BlockRect(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val id: Int,
  val rotation: Int,
  val config: Any? = null,
) {
  val right: Int
    get() = x + width
  val bottom: Int
    get() = y + height
}

/** 录制区域的初始地图。 */
data class RecordedMap(
  val width: Int,
  val height: Int,
  val floors: List<TileRect>,
  val overlays: List<TileRect>,
  val blocks: List<BlockRect>,
)

/**
 * Ponder 录制器。
 *
 * 工作流：
 * 1. [start]：通过 `PeroxiCore.input` 框选一个区域，区域内容作为场景初始地图；
 * 2. [pause]：冻结游戏时间（暂停游戏），此后的规划会记录进同一个关键帧；
 * 3. 玩家在暂停期间规划建筑、修改 config；
 * 4. [resume]：提交关键帧、把计划直接写进地图并恢复游戏时间；
 * 5. 重复 2~4 多次后再次按 record，[end] 结束录制；
 * 6. [preview] 预览；[exporters] 负责导出（Kotlin 之外可以自由新增实现）。
 *
 * 冻结期间时间不前进，因此同一关键帧里的所有改动都发生在同一时刻。
 */
object PonderRecorder {
  private val col1 = Color.valueOf("#BBFF99")
  private val col2 = Color.valueOf("#99FFDD")
  private const val selectText = "Ponder"

  /** 框选边长上限，同时也是 [TileChunk] 单边允许的最大值。 */
  private const val MAX_SELECTION = 1024

  /** 最后一个关键帧之后额外保留的 tick 数。 */
  var tailTicks = 60f

  /** 所有可用的导出器；新增格式只需要往这里加一个实现。 */
  val exporters = mutableListOf<SceneExporter>(KotlinSceneExporter, CialloSceneExporter)

  /** 初始地图使用的合并器，可换成 [GreedyTileMerger] / [IncrementalTileMerger]。 */
  var merger: TileMerger = ScanlineTileMerger()

  private var initialized = false
  private var rendererRegistered = false

  /** 把计划写进地图时置位，用来忽略由此产生的 config 事件。 */
  private var applying = false

  /** 已录制的游戏时间。 */
  private var time = 0f
  private var passTick = 0f
  private var wasPaused = false

  /** 冻结期间新出现的建造计划；提交时一起结算。 */
  private val pendingPlans = LinkedHashSet<BuildPlan>()

  /** 冻结开始时已存在的计划，不参与本次录制。 */
  private val baselinePlans = HashSet<BuildPlan>()

  /** 冻结期间直接捕获的 config 改动。 */
  private val passActions = mutableListOf<RecordedAction>()

  /** 当前是否有录制会话。 */
  var active = false
    private set

  /** 当前关键帧是否处于冻结（暂停规划）状态。 */
  var frozen = false
    private set

  /** 框选区域，闭区间 tile 坐标。 */
  var x1 = 0
    private set
  var y1 = 0
    private set
  var x2 = -1
    private set
  var y2 = -1
    private set

  val width: Int
    get() = x2 - x1 + 1
  val height: Int
    get() = y2 - y1 + 1
  val hasArea: Boolean
    get() = width > 0 && height > 0

  /** 已提交的关键帧，按录制顺序排列；设置面板可以直接修改。 */
  val keyframes = mutableListOf<PonderKeyframe>()

  /** 录制区域初始地图，`null` 表示尚未框选。 */
  var initial: RecordedMap? = null
    private set

  /** 用作场景标题的内容；开始录制时从当前 Ponder 场景继承。 */
  var content: UnlockableContent? = null

  val hasRecording: Boolean
    get() = initial != null

  /** 时间轴长度。 */
  val duration: Float
    get() = (keyframes.maxOfOrNull { it.tick } ?: 0f) + tailTicks

  fun init() {
    if (initialized) return
    initialized = true
    Events.run(Trigger.update) { update() }
    Events.on(ConfigEvent::class.java) { event -> onConfig(event) }
  }

  private fun update() {
    if (!active || PonderCore.ponderEnv) return
    if (frozen) {
      scanPlans()
    } else if (!Vars.state.isPaused()) {
      time += Time.delta
    }
  }

  /** 冻结期间把新建造计划记下来；内容在提交时读取，以便捕获之后修改的 config。 */
  private fun scanPlans() {
    val plans = Vars.player?.unit()?.plans ?: return
    plans.each { plan ->
      if (plan == null || plan in baselinePlans) return@each
      if (!inArea(plan.x, plan.y)) return@each
      pendingPlans.add(plan)
    }
  }

  private fun onConfig(event: ConfigEvent) {
    if (!active || !frozen || applying) return
    if (event.player != null && event.player != Vars.player) return
    val tile = event.tile.tile ?: return
    val tx = tile.x.toInt()
    val ty = tile.y.toInt()
    if (!inArea(tx, ty)) return
    passActions += RecordedAction.Configure(tx - x1, ty - y1, event.value)
  }

  fun inArea(
    x: Int,
    y: Int,
  ) = hasArea && x >= x1 && x <= x2 && y >= y1 && y <= y2

  /** 开始录制：重置会话并进入框选。 */
  fun start() {
    init()
    if (active) return
    active = true
    keyframes.clear()
    initial = null
    passActions.clear()
    pendingPlans.clear()
    baselinePlans.clear()
    time = 0f
    frozen = false
    x1 = 0
    y1 = 0
    x2 = -1
    y2 = -1
    registerRenderer()
    Core.app.post {
      if (!active) return@post
      PeroxiCore.input.selectionTask(col1, col2, selectText, MAX_SELECTION) { ax, ay, bx, by ->
        select(ax, ay, bx, by)
        if (active && hasArea) beginSession()
      }
    }
  }

  /** 把框选结果规整为合法区域。 */
  fun select(
    ax: Int,
    ay: Int,
    bx: Int,
    by: Int,
  ) {
    val result = Placement.normalizeArea(ax, ay, bx, by, 0, false, MAX_SELECTION)
    x1 = result.x
    y1 = result.y
    x2 = result.x2
    y2 = result.y2
  }

  private fun beginSession() {
    content = (PonderCore.scene as? UnlockableContentPonder)?.content
    captureInitial()
    val map = initial
    Log.info(
      "Ponder 录制开始: ($x1, $y1)..($x2, $y2) = ${width}x$height, " +
        "矩形 地面=${map?.floors?.size ?: 0} 覆盖=${map?.overlays?.size ?: 0} 建筑=${map?.blocks?.size ?: 0}"
    )
  }

  /**
   * 捕获框选区域并合并成矩形。
   *
   * 地面/覆盖层各用一个 [TileChunk] 合并；建筑先按方块 id 合并，再按朝向与 config 切成行段，
   * 因为 [TileData] 只有 16 位，无法同时容纳 id、rotation 与 config。
   */
  fun captureInitial() {
    if (!hasArea) return
    val tiles = Vars.world.tiles
    val floors = TileChunk(width, height)
    val overlays = TileChunk(width, height)
    val blocks = TileChunk(width, height)
    val rotations = ShortArray(width * height)
    val configs = arrayOfNulls<Any?>(width * height)
    for (lx in 0 until width) {
      for (ly in 0 until height) {
        val tile = tiles.get(x1 + lx, y1 + ly) ?: continue
        floors.setData(lx, ly, tile.floor().id)
        overlays.setData(lx, ly, tile.overlay().id)
        if (tile.isCenter() && tile.block() !== Blocks.air) {
          blocks.setData(lx, ly, tile.block().id)
          val build = tile.build
          rotations[ly * width + lx] = (build?.rotation ?: 0).toShort()
          configs[ly * width + lx] = build?.config()
        }
      }
    }
    initial =
      RecordedMap(
        width,
        height,
        tileRects(floors),
        tileRects(overlays),
        blockRects(blocks, rotations, configs),
      )
  }

  private fun tileRects(chunk: TileChunk): List<TileRect> =
    chunk
      .merged(merger)
      .asSequence()
      .filter { it.data.toInt() != 0 }
      // MergedTile 会被合并器复用，必须立刻拷贝。
      .map { TileRect(it.x, it.y, it.width.toInt(), it.height.toInt(), it.data) }
      .toList()

  private fun blockRects(
    chunk: TileChunk,
    rotations: ShortArray,
    configs: Array<Any?>,
  ): List<BlockRect> {
    val out = mutableListOf<BlockRect>()
    chunk.merged(merger).forEach { merged ->
      val id = merged.data.toInt()
      if (id == 0) return@forEach
      val block = Vars.content.block(id)
      // 多格方块每个矩形格都是独立中心，不能按行合并，否则会漏掉相邻的同种多格建筑。
      if (block != null && block.size > 1) {
        for (y in merged.y until merged.y + merged.height.toInt()) {
          for (x in merged.x until merged.x + merged.width.toInt()) {
            val i = y * width + x
            out += BlockRect(x, y, 1, 1, id, rotations[i].toInt(), configs[i])
          }
        }
        return@forEach
      }
      val right = merged.x + merged.width.toInt()
      for (y in merged.y until merged.y + merged.height.toInt()) {
        var x = merged.x
        while (x < right) {
          val start = y * width + x
          val rotation = rotations[start].toInt()
          val config = configs[start]
          var end = x + 1
          while (
            end < right &&
              rotations[y * width + end].toInt() == rotation &&
              configs[y * width + end] == config
          ) {
            end++
          }
          out += BlockRect(x, y, end - x, 1, id, rotation, config)
          x = end
        }
      }
    }
    return out
  }

  /** 冻结时间并开启一个新关键帧。 */
  fun pause() {
    if (!active || frozen || !hasArea) return
    frozen = true
    passTick = time
    passActions.clear()
    pendingPlans.clear()
    baselinePlans.clear()
    Vars.player?.unit()?.plans?.each { baselinePlans.add(it) }
    wasPaused = Vars.state.isPaused()
    if (!wasPaused) {
      if (Vars.net.active() || Vars.state.rules.pauseDisabled) {
        // 游戏时间无法暂停，但录制 tick 仍冻结，因此关键帧仍然只包含同一个时刻。
        Log.warn("当前规则不允许暂停游戏，关键帧期间世界会继续运行")
      } else {
        Vars.state.set(GameState.State.paused)
      }
    }
  }

  /** 提交当前关键帧、把计划写进地图并恢复时间。 */
  fun resume() {
    if (!active || !frozen) return
    commitPass()
    frozen = false
    if (!wasPaused && Vars.state.isPaused()) Vars.state.set(GameState.State.playing)
  }

  /** 结束整个录制会话。 */
  fun end() {
    if (frozen) resume()
    active = false
    frozen = false
    passActions.clear()
    pendingPlans.clear()
    baselinePlans.clear()
  }

  /** 清空所有录制数据。 */
  fun clear() {
    if (frozen) resume()
    active = false
    frozen = false
    keyframes.clear()
    initial = null
    passActions.clear()
    pendingPlans.clear()
    baselinePlans.clear()
    time = 0f
    x1 = 0
    y1 = 0
    x2 = -1
    y2 = -1
  }

  private fun commitPass() {
    val actions = mutableListOf<RecordedAction>()
    actions += passActions
    // 提交时读取计划当前状态，这样冻结期间补上的 config 也能被记录。
    pendingPlans.forEach { plan ->
      val lx = plan.x - x1
      val ly = plan.y - y1
      if (plan.breaking) {
        actions += RecordedAction.Remove(lx, ly)
      } else {
        plan.block?.let {
          actions += RecordedAction.Place(lx, ly, it.name, plan.rotation, plan.config)
        }
      }
    }
    if (actions.isNotEmpty()) {
      // 关键帧时刻取自冻结时的录制时间，并保证严格递增，避免快速连按时两帧重合。
      val last = keyframes.maxOfOrNull { it.tick } ?: -1f
      keyframes += PonderKeyframe(passTick.coerceAtLeast(last + 1f), actions)
    }
    passActions.clear()
    applyPlans()
    pendingPlans.clear()
    baselinePlans.clear()
  }

  /** 把暂停期间规划的建筑直接写进地图，避免恢复时间后被建造器重复执行。 */
  private fun applyPlans() {
    if (pendingPlans.isEmpty()) return
    val unit = Vars.player?.unit()
    val team = Vars.player?.team() ?: Team.derelict
    val tiles = Vars.world.tiles
    applying = true
    try {
      pendingPlans.forEach { plan ->
        if (unit != null) unit.plans.remove(plan)
        val tile = tiles.get(plan.x, plan.y) ?: return@forEach
        if (plan.breaking) {
          tile.setBlock(Blocks.air)
        } else {
          val block = plan.block ?: return@forEach
          tile.setBlock(block, team, plan.rotation)
          plan.config?.let { tile.build?.configureAny(it) }
        }
      }
    } finally {
      applying = false
    }
  }

  private fun registerRenderer() {
    if (rendererRegistered) return
    rendererRegistered = true
    PeroxiCore.input.topRenderers.add { drawTop() }
  }

  fun drawTop() {
    if (!active || !hasArea) return
    PeroxiCore.input.drawSelection(x1, y1, x2, y2, col1, col2, selectText)
  }

  /** 用录制结果构建一个 Ponder 场景并显示。 */
  fun preview() {
    val map = initial
    if (map == null) {
      Vars.ui.showInfoFade("还没有框选录制区域")
      return
    }
    val sceneContent =
      content ?: (PonderCore.scene as? UnlockableContentPonder)?.content ?: Blocks.itemSource
    val frames = keyframes.sortedBy { it.tick }
    val timeline =
      KeyframeTimelineScope()
        .apply {
          loop(false)
          duration(PonderRecorder.duration)
          initializer(
            object : TimelineInitializer {
              override fun create(context: TimelineContext) =
                Tiles(map.width, map.height).apply(Tiles::fillPonder)

              override fun init(context: TimelineContext) {
                applyMap(context, map)
              }
            }
          )
          frames.forEach { keyframe ->
            frame(keyframe.tick, keyframe.icon) {
              val scope = this
              keyframe.actions.forEach { scope.emit(it) }
            }
          }
        }
        .build()
    PonderCore.scene = UnlockableContentPonder(sceneContent, timeline)
    Core.app.post {
      PonderCore.ponder.load()
      PonderCore.ponder.setup()
      PonderCore.ponder.show()
    }
  }

  private fun applyMap(
    context: TimelineContext,
    map: RecordedMap,
  ) {
    val tiles = context.tiles
    val team = context.team
    map.floors.forEach { rect ->
      val floor = Vars.content.block(rect.data.toInt()).asFloor()
      fillRect(rect) { x, y -> tiles.get(x, y)?.setFloor(floor) }
    }
    map.overlays.forEach { rect ->
      val overlay = Vars.content.block(rect.data.toInt())
      fillRect(rect) { x, y -> tiles.get(x, y)?.setOverlay(overlay) }
    }
    map.blocks.forEach { rect ->
      val block = Vars.content.block(rect.id) ?: return@forEach
      if (block.size > 1 || rect.width * rect.height == 1) {
        tiles.get(rect.x, rect.y)?.setBlock(block, team, rect.rotation)
        rect.config?.let { config -> tiles.get(rect.x, rect.y)?.build?.configureAny(config) }
      } else {
        for (x in rect.x until rect.right) {
          for (y in rect.y until rect.bottom) {
            val tile = tiles.get(x, y) ?: continue
            tile.setBlock(block, team, rect.rotation)
            rect.config?.let { config -> tile.build?.configureAny(config) }
          }
        }
      }
    }
  }

  private inline fun fillRect(
    rect: TileRect,
    action: (Int, Int) -> Unit,
  ) {
    for (x in rect.x until rect.right) {
      for (y in rect.y until rect.bottom) action(x, y)
    }
  }

  private fun WorldPonderActionScope.emit(action: RecordedAction) {
    when (action) {
      is RecordedAction.Place -> {
        at(action.x, action.y).setBlock(Vars.content.block(action.block), action.rotation)
        action.config?.let { at(action.x, action.y).configure(it) }
      }
      is RecordedAction.Remove -> at(action.x, action.y).setBlock(Blocks.air)
      is RecordedAction.Configure -> at(action.x, action.y).configure(action.value)
    }
  }

  /** 生成可直接粘贴的 Kotlin DSL 场景代码。 */
  fun buildKotlinDsl(): String {
    val map = initial ?: return "// 还没有框选录制区域\n"
    val out = StringBuilder()
    val sceneContent = content
    if (sceneContent !is Block) {
      out.append("// 原场景内容不是方块: ${sceneContent?.name ?: "unknown"}，请自行替换 header\n")
    }
    out.append("import arc.math.geom.*\n")
    out.append("import mindustry.content.*\n")
    out.append("import mindustry.type.*\n")
    out.append("import peroxicore.ponder.api.dsl.*\n")
    out.append("\n")
    val head =
      if (sceneContent is Block) "Blocks.${contentName(sceneContent.name)}" else "Blocks.itemSource"
    out.append("$head.scene {\n")
    out.append("  keyframes {\n")
    out.append("    world(${map.width}, ${map.height}) {\n")
    appendMapDsl(out, map)
    keyframes
      .sortedBy { it.tick }
      .forEach { keyframe ->
        out.append("    frame(${formatFloat(keyframe.tick)}) {\n")
        keyframe.actions.forEach { appendActionDsl(out, it) }
        out.append("    }\n")
      }
    out.append("    duration(${formatFloat(duration)})\n")
    out.append("  }\n")
    out.append("}\n")
    return out.toString()
  }

  private fun appendMapDsl(
    out: StringBuilder,
    map: RecordedMap,
  ) {
    val indent = "      "
    val floorArea = HashMap<Int, Int>()
    map.floors.forEach { rect ->
      val id = rect.data.toInt()
      floorArea[id] = (floorArea[id] ?: 0) + rect.width * rect.height
    }
    val common =
      floorArea.entries
        .maxByOrNull { it.value }
        ?.takeIf { it.value == map.width * map.height }
        ?.key
    if (common != null) out.append(indent).append("fillFloor(${blockRef(common)})\n")
    map.floors.forEach { rect ->
      if (rect.data.toInt() == common) return@forEach
      out
        .append(indent)
        .append(regionCall(rect))
        .append(".fillFloor(${blockRef(rect.data.toInt())})\n")
    }
    map.overlays.forEach { rect ->
      out
        .append(indent)
        .append(regionCall(rect))
        .append(".each { x, y -> tiles.get(x, y).setOverlay(${blockRef(rect.data.toInt())}) }\n")
    }
    map.blocks.forEach { rect ->
      val block = Vars.content.block(rect.id) ?: return@forEach
      if (block === Blocks.air) return@forEach
      val config = rect.config?.let { "configure(${configExpression(it)})" }
      if (block.size > 1 || rect.width * rect.height == 1) {
        out
          .append(indent)
          .append("at(${rect.x}, ${rect.y}).setBlock(${blockRef(rect.id)}, ${rect.rotation})\n")
        if (config != null) out.append(indent).append("at(${rect.x}, ${rect.y}).$config\n")
      } else {
        out
          .append(indent)
          .append(regionCall(rect.x, rect.y, rect.right, rect.bottom))
          .append(
            ".each { x, y -> tiles.get(x, y).setBlock(${blockRef(rect.id)}, team, ${rect.rotation}) }\n"
          )
        if (config != null) {
          out
            .append(indent)
            .append(regionCall(rect.x, rect.y, rect.right, rect.bottom))
            .append(".each { x, y -> tiles.get(x, y).build?.configureAny(${configExpression(rect.config)}) }\n")
        }
      }
    }
  }

  private fun regionCall(
    x: Int,
    y: Int,
    right: Int,
    bottom: Int,
  ) = "region($x, $y, ${right - 1}, ${bottom - 1})"

  private fun regionCall(rect: TileRect) = regionCall(rect.x, rect.y, rect.right, rect.bottom)

  private fun appendActionDsl(
    out: StringBuilder,
    action: RecordedAction,
  ) {
    val indent = "      "
    when (action) {
      is RecordedAction.Place -> {
        out
          .append(indent)
          .append(
            "at(${action.x}, ${action.y}).setBlock(${blockRef(action.block)}, ${action.rotation})\n"
          )
        action.config?.let {
          out
            .append(indent)
            .append("at(${action.x}, ${action.y}).configure(${configExpression(it)})\n")
        }
      }
      is RecordedAction.Remove ->
        out.append(indent).append("at(${action.x}, ${action.y}).setBlock(Blocks.air)\n")
      is RecordedAction.Configure ->
        out
          .append(indent)
          .append("at(${action.x}, ${action.y}).configure(${configExpression(action.value)})\n")
    }
  }

  private fun blockRef(id: Int): String = contentRef(Vars.content.block(id))

  private fun blockRef(name: String): String = "Blocks.${contentName(name)}"

  /** 把内容名转成 Kotlin 标识符，例如 `titanium-conveyor` -> `titaniumConveyor`。 */
  fun contentName(name: String): String = Strings.kebabToCamel(name)

  /** 把内容引用转成导出代码，不认识的类型会退化为 null 并留下注释。 */
  fun contentRef(content: UnlockableContent?): String =
    when (content) {
      null -> "null"
      is Block -> "Blocks.${contentName(content.name)}"
      is Item -> "Items.${contentName(content.name)}"
      is Liquid -> "Liquids.${contentName(content.name)}"
      is UnitType -> "UnitTypes.${contentName(content.name)}"
      is StatusEffect -> "StatusEffects.${contentName(content.name)}"
      else -> "null /* unsupported content: ${content.name} */"
    }

  /** 把任意 config 值转成导出代码。 */
  fun configExpression(value: Any?): String =
    when (value) {
      null -> "null"
      is String -> quote(value)
      is Boolean,
      is Int,
      is Double -> value.toString()
      is Float -> "${value}f"
      is Long -> "${value}L"
      is ItemStack -> "ItemStack(${contentRef(value.item)}, ${value.amount})"
      is IntArray -> "intArrayOf(${value.joinToString(", ")})"
      is Array<*> -> "arrayOf(${value.joinToString(", ") { configExpression(it) }})"
      is UnlockableContent -> contentRef(value)
      is Point2 -> "Point2(${value.x}, ${value.y})"
      else -> "null /* unsupported config: ${value.javaClass.simpleName} */"
    }

  private fun quote(value: String): String =
    buildString {
      append('"')
      value.forEach { c ->
        when (c) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '$' -> append("\\$")
          '\n' -> append("\\n")
          '\t' -> append("\\t")
          else -> append(c)
        }
      }
      append('"')
    }

  fun formatFloat(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}f"
    else String.format(Locale.ROOT, "%.2ff", value)
}
