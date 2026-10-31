package peroxicore.ponder.record

import arc.*
import arc.math.geom.Point2
import arc.util.Time
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import java.util.Locale
import mindustry.*
import mindustry.content.Blocks
import mindustry.ctype.UnlockableContent
import mindustry.game.EventType.*
import mindustry.world.*
import peroxicore.*
import peroxicore.ponder.PonderCore
import peroxicore.ponder.api.dsl.*
import peroxicore.ponder.scene.*

/** A recorded change in the normal Mindustry world. Ticks are recording ticks. */
sealed class RecordedAction(open val tick: Float, open val x: Int, open val y: Int) {
  data class Place(
    override val tick: Float,
    override val x: Int,
    override val y: Int,
    val block: String,
    val rotation: Int,
  ) : RecordedAction(tick, x, y)

  data class Remove(override val tick: Float, override val x: Int, override val y: Int) :
    RecordedAction(tick, x, y)

  data class Floor(
    override val tick: Float,
    override val x: Int,
    override val y: Int,
    val floor: String,
  ) : RecordedAction(tick, x, y)

  data class Configure(
    override val tick: Float,
    override val x: Int,
    override val y: Int,
    val value: Any?,
  ) : RecordedAction(tick, x, y)
}

/**
 * Records the original Mindustry events while the player is outside the Ponder dialog. The dialog
 * only starts/stops this object; it never replaces the normal input handler.
 */
object PonderRecorder {
  private val blocksType = ClassName("mindustry.content", "Blocks")
  private val point2Type = ClassName("arc.math.geom", "Point2")
  private val sceneType = ClassName("peroxicore.ponder.scene", "UnlockableContentPonder")

  private data class State(val block: String, val floor: String, val rotation: Int)

  private var initialized = false
  private var width = 0
  private var height = 0
  private var initial = HashMap<Int, State>()
  private var time = 0f
  val actions = mutableListOf<RecordedAction>()
  var active = false
    private set

  val hasRecording: Boolean
    get() = width > 0 && height > 0 && initial.isNotEmpty()

  fun init() {
    if (initialized) return
    initialized = true
    Events.run(Trigger.update) { if (active) time += Time.delta }
    Events.on(BlockBuildEndEvent::class.java) { event ->
      if (!active || !isLocal(event.team)) return@on
      val tile = event.tile
      if (event.breaking) actions += RecordedAction.Remove(time, tile.x.toInt(), tile.y.toInt())
      else {
        actions +=
          RecordedAction.Place(
            time,
            tile.x.toInt(),
            tile.y.toInt(),
            tile.block().name,
            tile.build?.rotation ?: 0,
          )
        event.config?.let {
          actions += RecordedAction.Configure(time, tile.x.toInt(), tile.y.toInt(), it)
        }
      }
    }
    Events.on(BuildRotateEvent::class.java) { event ->
      if (active && (event.unit == null || event.unit.team == Vars.player?.team())) {
        val tile = event.build.tile
        actions +=
          RecordedAction.Place(
            time,
            tile.x.toInt(),
            tile.y.toInt(),
            event.build.block.name,
            event.build.rotation,
          )
      }
    }
    Events.on(ConfigEvent::class.java) { event ->
      if (active && (event.player == null || event.player == Vars.player)) {
        val tile = event.tile.tile
        actions += RecordedAction.Configure(time, tile.x.toInt(), tile.y.toInt(), event.value)
      }
    }
    Events.on(TileFloorChangeEvent::class.java) { event ->
      if (active)
        actions +=
          RecordedAction.Floor(time, event.tile.x.toInt(), event.tile.y.toInt(), event.floor.name)
    }
  }

  fun start() {
    init()
    val tiles = Vars.world.tiles ?: return
    width = tiles.width
    height = tiles.height
    initial = HashMap()
    for (x in 0 until width) for (y in 0 until height) {
      val tile = tiles.get(x, y) ?: continue
      initial[x + y * width] =
        State(tile.block().name, tile.floor().name, tile.build?.rotation ?: 0)
    }
    actions.clear()
    time = 0f
    active = true
  }

  fun stop() {
    active = false
  }

  fun clear() {
    active = false
    actions.clear()
    initial.clear()
  }

  /** Rebuilds the captured recording as a normal Ponder scene for in-game preview. */
  fun preview() {
    if (!hasRecording) return
    val content = (PonderCore.scene as? UnlockableContentPonder)?.content ?: return
    val initializer =
      BaseTimelineInitializer(width, height) {
        val scope = BaseTimelineInitializerScope(this)
        initial.forEach { (id, state) ->
          val point = Point2(id % width, id / width)
          if (state.floor != "air")
            scope.run { point.setFloor(Vars.content.block(state.floor).asFloor()) }
          if (state.block != "air") scope.run { point.setBlock(Vars.content.block(state.block)) }
        }
      }
    val timeline =
      BaseTimeline(initializer).apply {
        loop = false
        duration = (actions.maxOfOrNull { it.tick } ?: 0f) + 120f
        actions
          .groupBy { it.tick }
          .forEach { (tick, group) ->
            val mark =
              Keyframe(tick, null) {
                val scope = WorldPonderActionScope(this)
                group.forEach { action ->
                  val point = Point2(action.x, action.y)
                  when (action) {
                    is RecordedAction.Place ->
                      scope.run {
                        point.setBlock(Vars.content.block(action.block), action.rotation)
                      }
                    is RecordedAction.Remove -> scope.run { point.setBlock(Blocks.air) }
                    is RecordedAction.Floor ->
                      scope.run { point.setFloor(Vars.content.block(action.floor).asFloor()) }
                    is RecordedAction.Configure -> scope.run { point.configure(action.value) }
                  }
                }
              }
            (marks as MutableList<TimeMark>).add(mark)
          }
      }
    PonderCore.scene = UnlockableContentPonder(content, timeline)
    PonderCore.ponder.setup()
  }

  /** Returns a complete, pasteable Kotlin DSL scene. */
  fun exportKotlinDsl(): String {
    val out = StringBuilder()
    val sceneContent =
      (PonderCore.scene as? peroxicore.ponder.scene.UnlockableContentPonder)?.content
    val sceneHead =
      if (sceneContent != null) "Blocks.${sceneContent.name.replace('-', '_')}.scene" else "scene"
    out.append("$sceneHead {\n  keyframes {\n    world($width, $height) {\n")
    val floorCounts = HashMap<String, Int>()
    initial.values.forEach { state ->
      floorCounts[state.floor] = (floorCounts[state.floor] ?: 0) + 1
    }
    val commonFloor =
      floorCounts.maxByOrNull { it.value }?.takeIf { it.value == width * height }?.key
    if (commonFloor != null && commonFloor != "air")
      out.append("      fillFloor(${blockExpression(commonFloor)})\n")
    initial.forEach { (id, state) ->
      val x = id % width
      val y = id / width
      if (state.floor != "air" && state.floor != commonFloor)
        out.append("      at($x, $y).setFloor(${blockExpression(state.floor)})\n")
      if (state.block != "air")
        out.append(
          "      at($x, $y).setBlock(${blockExpression(state.block)}, ${state.rotation})\n"
        )
    }
    out.append("    }\n")
    actions
      .groupBy { it.tick }
      .toSortedMap()
      .forEach { (tick, group) ->
        out.append("    frame(${formatFloat(tick)}) {\n")
        group.forEach { action ->
          when (action) {
            is RecordedAction.Place ->
              out.append(
                "      at(${action.x}, ${action.y}).setBlock(${blockExpression(action.block)}, ${action.rotation})\n"
              )
            is RecordedAction.Remove ->
              out.append("      at(${action.x}, ${action.y}).setBlock(Blocks.air)\n")
            is RecordedAction.Floor ->
              out.append(
                "      at(${action.x}, ${action.y}).setFloor(${blockExpression(action.floor)})\n"
              )
            is RecordedAction.Configure ->
              out.append(
                "      at(${action.x}, ${action.y}).configure(${configExpression(action.value)})\n"
              )
          }
        }
        out.append("    }\n")
      }
    out.append("  }\n}\n")
    return FileSpec.builder("generated.ponder", "RecordedScene")
      .addImport("mindustry.content", "Blocks")
      .addImport("peroxicore.ponder.api.dsl", "scene")
      .addImport("peroxicore.ponder.api.dsl", "keyframes")
      .addImport("peroxicore.ponder.api.dsl", "world")
      .addProperty(
        PropertySpec.builder("recordedScene", sceneType)
          .initializer(CodeBlock.of("%L", out.toString()))
          .build()
      )
      .build()
      .toString()
  }

  private fun blockExpression(name: String) =
    CodeBlock.of("%T.%L", blocksType, name.substringAfterLast('-').replace('-', '_')).toString()

  private fun isLocal(team: mindustry.game.Team) = Vars.player == null || team == Vars.player.team()

  private fun formatFloat(value: Float) =
    if (value == value.toInt().toFloat()) "${value.toInt()}f"
    else String.format(Locale.ROOT, "%.3ff", value)

  private fun configExpression(value: Any?): String =
    when (value) {
      null -> "null"
      is String -> CodeBlock.of("%S", value).toString()
      is Boolean,
      is Int,
      is Float,
      is Double,
      is Long -> value.toString()
      is IntArray -> "intArrayOf(${value.joinToString()})"
      is Array<*> -> "arrayOf(${value.joinToString { configExpression(it) }})"
      is UnlockableContent -> blockExpression(value.name)
      is Point2 -> CodeBlock.of("%T(%L, %L)", point2Type, value.x, value.y).toString()
      else -> "null /* unsupported config: ${value.javaClass.simpleName} */"
    }
}
