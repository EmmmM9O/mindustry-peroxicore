@file:Suppress("UNCHECKED_CAST")

package peroxicore.ponder.api.template

import arc.*
import arc.graphics.*
import arc.math.geom.Point2
import arc.math.geom.Rect
import arc.math.geom.Vec2
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
import peroxicore.annotations.*
import peroxicore.ponder.*
import peroxicore.ponder.api.dsl.*
import peroxicore.ponder.scene.*
import peroxicore.ponder.ui.*

@ApiTemplate(
  targetPackage = "peroxicore.ponder.api.dsl",
  name = "WorldPonderActionScopeApi",
  generateJava = true,
  javaPackage = "peroxicore.ponder.api.java",
  javaName = "PonderJavaWorld",
  annotations = ["peroxicore.ponder.api.dsl.PonderDslMarker"],
)
@PonderDslMarker
interface WorldPonderActionScopeTemplate<Self : WorldPonderActionScopeTemplate<Self>> :
  PositionScopeTemplate,
  TimelineContext {
  @Actionable(
    javaName = "Tile",
  )
  typealias Point = Point2

  @Actionable(
    javaName = "Vec",
  )
  typealias Vec = Vec2

  @Actionable(
    javaName = "Region",
  )
  typealias Region = peroxicore.ponder.api.geom.Region

  fun tiles(action: Tiles.() -> Unit) {
    tiles.action()
  }

  fun fillFloor(floor: Block) {
    tiles {
      for (i in 0 until width * height) {
        geti(i).setFloor(floor as Floor)
      }
    }
  }

  fun Region.each(block: TimelineContext.(Int, Int) -> Unit) {
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

  fun Region.fill(block: Block): Unit = this.fill(block, 0, null)

  fun Region.fill(
    block: Block,
    rotation: Int,
  ): Unit = this.fill(block, rotation, null)

  fun Region.fill(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    if (block is Floor) fillFloor(block)
  }

  @ToAction
  fun Point.setFloor(floor: Floor) {
    tiles.get(x, y).setFloor(floor)
  }

  @ToAction
  fun Point.setBlock(block: Block): Unit = this.setBlock(block, 0, null)

  @ToAction
  fun Point.setBlock(
    block: Block,
    rotation: Int,
  ): Unit = this.setBlock(block, rotation, null)

  @ToAction(defaults = ["", "0", "null"])
  fun Point.setBlock(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    tiles.get(x, y).setBlock(block, team ?: this@WorldPonderActionScopeTemplate.team, rotation)
  }

  @ToAction
  fun Point.set(block: Block): Unit = this.set(block, 0, null)

  @ToAction
  fun Point.set(
    block: Block,
    rotation: Int,
  ): Unit = this.set(block, rotation, null)

  @ToAction(defaults = ["", "0", "null"])
  fun Point.set(
    block: Block,
    rotation: Int = 0,
    team: Team? = null,
  ) {
    if (block is Floor) setFloor(block) else setBlock(block, rotation, team)
  }

  /** Applies a building configuration captured by the recorder. */
  @ToAction
  fun Point.configure(value: Any?) {
    tiles.get(x, y)?.build?.configure(value)
  }

  fun text(newText: String): String =
    newText
      .takeIf { it.isNotEmpty() && it[0] in "$@" }
      ?.let { Core.bundle.get(it.substring(1), it) }
      ?: newText

  fun Vec.label(info: String): Unit = this.label(info, 2f, WorldLabel.flagOutline)

  fun Vec.label(
    info: String,
    duration: Float,
  ): Unit = this.label(info, duration, WorldLabel.flagOutline)

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

  @ToAction
  fun Point.label(info: String): Unit = this.label(info, 2f, WorldLabel.flagOutline)

  @ToAction
  fun Point.label(
    info: String,
    duration: Float,
  ): Unit = this.label(info, duration, WorldLabel.flagOutline)

  @ToAction(defaults = ["", "2f", "mindustry.gen.WorldLabel.flagOutline"])
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

  @ToAction
  fun Point.placeEffect(): Unit = this.placeEffect(-1)

  @ToAction(defaults = ["-1"])
  fun Point.placeEffect(size: Int = -1) {
    if (jump) return
    tiles.get(x, y)?.build?.also {
      it.block.placeEffect.at(
        it.x,
        it.y,
        (if (size == -1) it.block.size else size).toFloat()
      )
    }
      ?: run {
        val rsize = if (size == -1) 1 else size
        val offset = ((rsize + 1) % 2) * 8f / 2f
        Fx.placeBlock.at(unit().add(offset, offset), rsize.toFloat())
      }
  }

  @ToAction
  fun Point.healEffect(): Unit = this.healEffect(Pal.accent)

  @ToAction(defaults = ["mindustry.graphics.Pal.accent"])
  fun Point.healEffect(color: Color = Pal.accent) {
    if (jump) return
    tiles.get(x, y)?.build?.also {
      Fx.healBlockFull.at(it.x, it.y, it.block.size.toFloat(), color, it.block)
    }
  }

  fun building(value: Boolean) {
    isBuilding = value
  }

  @ToAction
  fun Point.plan(block: Block): Unit = this.plan(block, 0, null)

  @ToAction
  fun Point.plan(
    block: Block,
    rotation: Int,
  ): Unit = this.plan(block, rotation, null)

  @ToAction(defaults = ["", "0", "null"])
  fun Point.plan(
    block: Block,
    rotation: Int = 0,
    config: Any? = null,
  ) {
    if (input.validPlace(x, y, block, rotation)) {
      builder?.also {
        it.plans.add(
          BuildPlan(
            x,
            y,
            rotation,
            block,
            config ?: block.nextConfig()
          )
        )
      }
    }
  }

  @ToAction
  fun Point.build(block: Block): Promise<Self, Building?> =
    this.build(block, 0, 30f, null, null)

  @ToAction
  fun Point.build(
    block: Block,
    rotation: Int,
  ): Promise<Self, Building?> =
    this.build(block, rotation, 30f, null, null)

  @ToAction
  fun Point.build(
    block: Block,
    rotation: Int,
    duration: Float,
  ): Promise<Self, Building?> =
    this.build(block, rotation, duration, null, null)

  @ToAction
  fun Point.build(
    block: Block,
    rotation: Int,
    duration: Float,
    team: Team?,
  ): Promise<Self, Building?> =
    this.build(block, rotation, duration, team, null)

  @ToAction(defaults = ["", "0", "30f", "null", "null"])
  fun Point.build(
    block: Block,
    rotation: Int = 0,
    duration: Float = 30f,
    team: Team? = null,
    config: Any? = null,
  ): Promise<Self, Building?> {
    val promise = Promise<Self, Building?>()
    val teamv = team ?: this@WorldPonderActionScopeTemplate.team
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
        promise.resolve(this@WorldPonderActionScopeTemplate as Self, tiles.get(x, y).build)
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
          promise.resolve(this@WorldPonderActionScopeTemplate as Self, tiles.get(x, y).build)
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

  fun screenMax(max: Float): Pair<Float, Float> = toMax(ratio, 1f, max)

  fun screenMin(min: Float): Pair<Float, Float> = toMin(ratio, 1f, min)

  fun beginCam() {
    camControl = true
  }

  fun endCam() {
    camControl = false
  }

  @ToAction
  fun Point.focus(range: Int): Point =
    apply {
      val (w, h) = screenMin(range.unit())
      camRect(unit().center(w, h))
    }

  fun fullTiles(): Rect =
    Rect(0f, 0f, Vars.world.unitWidth().toFloat(), Vars.world.unitHeight().toFloat())

  fun full() {
    camRect(fullTiles().camera())
  }

  fun BuildPlan.build(team: Team? = null, duration: Float = 30f) {
    at(x, y).build(block, rotation, duration, team, config)
  }

  fun BuildPlan.set(team: Team? = null) {
    at(x, y).also {
      it.set(block, rotation, team)
      it.configure(config)
    }
  }

  @ToAction
  fun Point.set(schematic: Schematic) {
    Vars.schematics.toPlans(schematic, x, y, false).each { it.set() }
  }

  @ToAction
  fun Point.build(schematic: Schematic) {
    Vars.schematics.toPlans(schematic, x, y, false).each { it.build() }
  }

  @ToAction
  fun Point.lbBuild(schematic: Schematic) {
    at(x + schematic.width / 2, y + schematic.height / 2).build(schematic)
  }

  fun fill(schematic: Schematic) {
    at(schematic.width / 2, schematic.height / 2).set(schematic)
  }
}
