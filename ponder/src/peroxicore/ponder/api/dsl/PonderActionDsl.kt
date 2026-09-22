package peroxicore.ponder.api.dsl

import arc.*
import arc.graphics.*
import arc.util.*
import mindustry.*
import mindustry.content.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.world.*
import mindustry.world.blocks.*
import mindustry.world.blocks.environment.*
import peroxicore.ponder.*
import peroxicore.ponder.api.dsl.PositionScope.*
import peroxicore.ponder.scene.*
import peroxicore.ponder.ui.*
import kotlin.Unit

@PonderDslMarker
open class WorldPonderActionScope(
  var context: TimelineContext,
) : TimelineContext by context,
  PositionScope {
  inline fun tiles(crossinline action: Tiles.() -> Unit) {
    tiles.action()
  }

  fun fillFloor(floor: Block) {
    tiles {
      for (i in 0 until width * height) {
        geti(i).setFloor(floor as Floor)
      }
    }
  }

  inline fun Region.each(crossinline block: TimelineContext.(Int, Int) -> Unit) {
    for (x in xRange) {
      for (y in yRange) {
        block(x, y)
      }
    }
  }

  fun Region.fillFloor(floor: Block) {
    each { x, y ->
      tiles.get(x, y).setFloor(floor as Floor)
    }
  }

  fun Region.fill(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    if (block is Floor) fillFloor(block)
  }

  fun Point.setFloor(floor: Floor) {
    tiles.get(x, y).setFloor(floor)
  }

  fun Point.setBlock(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    tiles.get(x, y).setBlock(block, team ?: this@WorldPonderActionScope.team, rotation)
  }

  fun Point.set(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    if (block is Floor) setFloor(block) else setBlock(block, rotation, team)
  }

  fun text(newText: String) =
    newText
      .takeIf { it.isNotEmpty() && it[0] in "$@" }
      ?.let { Core.bundle.get(it.substring(1), it) }
      ?: newText

  fun Vec.label(
    info: String,
    duration: Float = 2f,
    flags: Byte = WorldLabel.flagOutline,
  ) {
    if (jump) return
    PonderUI.showLabel(
      text(info),
      duration,
      x,
      y,
      flags
    )
  }

  fun Point.label(
    info: String,
    duration: Float = 2f,
    flags: Byte = WorldLabel.flagOutline,
  ) {
    unit().label(info, duration, flags)
  }

  fun speed(value: Float) {
    this.speed = value
  }

  fun Point.placeEffect(size: Int = -1) {
    if (jump) return
    tiles.get(x, y)?.build?.also {
      it.block.placeEffect.at(it.x, it.y, (if (size == -1) it.block.size else size).toFloat())
    }
      ?: run {
        val rsize = if (size == -1) 1 else size
        val offset = ((rsize + 1) % 2) * 8f / 2f
        Fx.placeBlock.at(unit().add(offset, offset), rsize.toFloat())
      }
  }

  fun Point.healEffect(color: Color = Pal.accent) {
    if (jump) return
    tiles.get(x, y)?.build?.also {
      Fx.healBlockFull.at(it.x, it.y, it.block.size.toFloat(), color, it.block)
    }
  }

  fun building(value: Boolean) {
    isBuilding = value
  }

  fun Point.plan(
    block: Block,
    rotation: Int = 0,
    config: Any? = null,
  ) {
    if (input.validPlace(x, y, block, rotation)) {
      builder?.also { it.plans.add(BuildPlan(x, y, rotation, block, config ?: block.nextConfig())) }
    }
  }

  fun Point.build(
    block: Block,
    rotation: Int = 0,
    duration: Float = 30f,
    team: Team? = null,
    config: Any? = null,
  ): Promise<WorldPonderActionScope, Building?> {
    val promise = Promise<WorldPonderActionScope, Building?>()
    val teamv = team ?: this@WorldPonderActionScope.team
    Build.beginPlace(builder, block, teamv, x, y, rotation, config)
    (tiles.get(x, y).build as? ConstructBlock.ConstructBuild)?.let { target ->
      if (jump) {
        ConstructBlock.constructed(
          tiles.get(x, y),
          block,
          builder,
          rotation.toByte(),
          teamv,
          config
        )
        promise.resolve(this@WorldPonderActionScope, tiles.get(x, y).build)
      } else {
        val step = 1.1f / duration
        task(duration) {
          if (target.isAdded) target.progress += Time.delta * step
        }.end {
          if (target.isAdded) {
            ConstructBlock.constructed(
              tiles.get(x, y),
              block,
              builder,
              rotation.toByte(),
              teamv,
              config
            )
          }
          promise.resolve(this@WorldPonderActionScope, tiles.get(x, y).build)
        }
      }
    }
      ?: run {
        Log.warn("Could not place at $x,$y with $block")
      }
    return promise
  }

  fun camSpeed(value: Float) {
    this.camSpeed = value
  }

  fun camRect(value: Rect) {
    beginCam()
    this.camRect.set(value)
  }

  fun screenMax(max: Float) = toMax(ratio, 1f, max)

  fun screenMin(min: Float) = toMin(ratio, 1f, min)

  fun beginCam() {
    camControl = true
  }

  fun endCam() {
    camControl = false
  }

  fun Point.focus(range: Int) =
    apply {
      val (w, h) = screenMin(range.unit())
      camRect(unit().center(w, h))
    }

  fun fullTiles() =
    Rect(0f, 0f, Vars.world.unitWidth().toFloat(), Vars.world.unitHeight().toFloat())

  fun full() {
    camRect(fullTiles().camera())
  }
}
