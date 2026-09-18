package peroxicore.ponder

import arc.struct.*
import arc.util.pooling.*
import mindustry.entities.*
import mindustry.gen.*
import mindustry.gen.Unit
import peroxicore.util.reflect.*
import universe.UniverseActual.reflection
import universe.util.reflect.accessor.*
import kotlin.Unit as KUnit

object PonderGroups {
  lateinit var all: EntityGroup<Entityc>
  lateinit var build: EntityGroup<Building>
  lateinit var draw: EntityGroup<Drawc>
  lateinit var fire: EntityGroup<Fire>
  lateinit var player: EntityGroup<Player>
  lateinit var powerGraph: EntityGroup<PowerGraphUpdaterc>
  lateinit var puddle: EntityGroup<Puddle>
  lateinit var sync: EntityGroup<Syncc>
  lateinit var label: EntityGroup<WorldLabel>
  lateinit var unit: EntityGroup<Unit>
  lateinit var bullet: EntityGroup<Bullet>
  lateinit var weather: EntityGroup<WeatherState>
  var freeQueue: Seq<Pool.Poolable> = Seq.with()

  val originAll: EntityGroup<Entityc> = Groups.all
  val originBuild: EntityGroup<Building> = Groups.build
  val originDraw: EntityGroup<Drawc> = Groups.draw
  val originFire: EntityGroup<Fire> = Groups.fire
  val originPlayer: EntityGroup<Player> = Groups.player
  val originPowerGraph: EntityGroup<PowerGraphUpdaterc> = Groups.powerGraph
  val originPuddle: EntityGroup<Puddle> = Groups.puddle
  val originSync: EntityGroup<Syncc> = Groups.sync
  val originLabel: EntityGroup<WorldLabel> = Groups.label
  val originUnit: EntityGroup<Unit> = Groups.unit
  val originBullet: EntityGroup<Bullet> = Groups.bullet
  val originWeather: EntityGroup<WeatherState> = Groups.weather
  var freeQueue_ by Groups::class.accessFieldStatic<Seq<Pool.Poolable>>("freeQueue")
  val originFreeQueue = freeQueue_

  var isClearing = false

  fun indexMethod(type: String) =
    MethodInvoker1<Any, Int, KUnit>(
      reflection.findMethod(
        Class.forName("mindustry.gen.IndexableEntity__$type").kotlin,
        "setIndex__$type",
        Int::class
      )
    )

  val allI = indexMethod("all")
  val buildI = indexMethod("build")
  val drawI = indexMethod("draw")
  val powerGraphI = indexMethod("powerGraph")
  val puddleI = indexMethod("puddle")
  val syncI = indexMethod("sync")
  val labelI = indexMethod("label")
  val unitI = indexMethod("unit")
  val bulletI = indexMethod("bullet")
  val playerI = indexMethod("player")
  val fireI = indexMethod("fire")
  val weatherI = indexMethod("weather")

  fun begin() {
    Groups.all = all
    Groups.build = build
    Groups.draw = draw
    Groups.powerGraph = powerGraph
    Groups.puddle = puddle
    Groups.sync = sync
    Groups.label = label
    Groups.unit = unit
    Groups.bullet = bullet
    Groups.player = player
    Groups.fire = fire
    Groups.weather = weather
    freeQueue_ = freeQueue
  }

  fun end() {
    Groups.all = originAll
    Groups.build = originBuild
    Groups.draw = originDraw
    Groups.powerGraph = originPowerGraph
    Groups.puddle = originPuddle
    Groups.sync = originSync
    Groups.label = originLabel
    Groups.unit = originUnit
    Groups.bullet = originBullet
    Groups.player = originPlayer
    Groups.fire = originFire
    Groups.weather = originWeather
    freeQueue_ = originFreeQueue
  }

  fun setup() {
    all =
      EntityGroup(
        Entityc::class.java,
        false,
        false
      ) { e, pos ->
        allI(e, pos)
      }
    build =
      EntityGroup(
        Building::class.java,
        false,
        false
      ) { e, pos ->
        buildI(e, pos)
      }
    draw =
      EntityGroup(
        Drawc::class.java,
        false,
        false
      ) { e, pos ->
        drawI(e, pos)
      }
    powerGraph =
      EntityGroup(
        PowerGraphUpdaterc::class.java,
        false,
        false
      ) { e, pos ->
        powerGraphI(e, pos)
      }
    puddle =
      EntityGroup(
        Puddle::class.java,
        false,
        false
      ) { e, pos ->
        puddleI(e, pos)
      }
    sync =
      EntityGroup(
        Syncc::class.java,
        false,
        true
      ) { e, pos ->
        syncI(e, pos)
      }
    label =
      EntityGroup(
        WorldLabel::class.java,
        false,
        true
      ) { e, pos ->
        labelI(e, pos)
      }
    unit =
      EntityGroup(
        Unit::class.java,
        true,
        true
      ) { e, pos ->
        unitI(e, pos)
      }
    bullet =
      EntityGroup(
        Bullet::class.java,
        true,
        false
      ) { e, pos ->
        bulletI(e, pos)
      }
    weather =
      EntityGroup(
        WeatherState::class.java,
        false,
        false
      ) { e, pos ->
        weatherI(e, pos)
      }
    player =
      EntityGroup(
        Player::class.java,
        false,
        true
      ) { e, pos ->
        playerI(e, pos)
      }
    fire =
      EntityGroup(
        Fire::class.java,
        false,
        false
      ) { e, pos ->
        fireI(e, pos)
      }
  }

  fun queueFree(obj: Pool.Poolable) {
    freeQueue.add(obj)
  }

  fun clear() {
    isClearing = true
    all.clear()
    player.clear()
    bullet.clear()
    build.clear()
    sync.clear()
    draw.clear()
    puddle.clear()
    fire.clear()
    powerGraph.clear()
    label.clear()
    unit.clear()
    weather.clear()
    isClearing = false
  }

  fun resize(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
  ) {
    bullet.resize(x, y, w, h)
    unit.resize(x, y, w, h)
  }

  fun update() {
    updatePooling()
    bullet.updatePhysics()
    unit.updatePhysics()
    all.update()
    powerGraph.update()
    build.update()
    bullet.collide()
  }

  fun updatePooling() {
    for (p in freeQueue) {
      Pools.free(p)
    }
    freeQueue.clear()
  }
}
