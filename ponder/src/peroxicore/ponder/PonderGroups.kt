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
  lateinit var effect: EntityGroup<EffectState>
  lateinit var build: EntityGroup<Building>
  lateinit var draw: EntityGroup<Drawc>
  lateinit var player: EntityGroup<Player>
  lateinit var powerGraph: EntityGroup<PowerGraphUpdaterc>
  lateinit var sync: EntityGroup<Syncc>
  lateinit var unit: EntityGroup<Unit>
  lateinit var bullet: EntityGroup<Bullet>
  lateinit var weather: EntityGroup<WeatherState>
  var freeQueue: Seq<Pool.Poolable> = Seq.with()

  val originAll: EntityGroup<Entityc> = Groups.all
  val originEffect: EntityGroup<EffectState> = Groups.effect
  val originBuild: EntityGroup<Building> = Groups.build
  val originDraw: EntityGroup<Drawc> = Groups.draw
  val originPlayer: EntityGroup<Player> = Groups.player
  val originPowerGraph: EntityGroup<PowerGraphUpdaterc> = Groups.powerGraph
  val originSync: EntityGroup<Syncc> = Groups.sync
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
  val effectI = indexMethod("effect")
  val buildI = indexMethod("build")
  val drawI = indexMethod("draw")
  val powerGraphI = indexMethod("powerGraph")
  val syncI = indexMethod("sync")
  val unitI = indexMethod("unit")
  val bulletI = indexMethod("bullet")
  val playerI = indexMethod("player")
  val weatherI = indexMethod("weather")

  fun begin() {
    Groups.all = all
    Groups.effect = effect
    Groups.build = build
    Groups.draw = draw
    Groups.powerGraph = powerGraph
    Groups.sync = sync
    Groups.unit = unit
    Groups.bullet = bullet
    Groups.player = player
    Groups.weather = weather
    freeQueue_ = freeQueue
  }

  fun end() {
    Groups.all = originAll
    Groups.effect = originEffect
    Groups.build = originBuild
    Groups.draw = originDraw
    Groups.powerGraph = originPowerGraph
    Groups.sync = originSync
    Groups.unit = originUnit
    Groups.bullet = originBullet
    Groups.player = originPlayer
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
    effect =
      EntityGroup(
        EffectState::class.java,
        false,
        false
      ) { e, pos ->
        effectI(e, pos)
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
    sync =
      EntityGroup(
        Syncc::class.java,
        false,
        true
      ) { e, pos ->
        syncI(e, pos)
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
  }

  fun queueFree(obj: Pool.Poolable) {
    freeQueue.add(obj)
  }

  fun clear() {
    isClearing = true
    all.clear()
    effect.clear()
    player.clear()
    bullet.clear()
    build.clear()
    sync.clear()
    draw.clear()
    powerGraph.clear()
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
    effect.update()
    all.update()
    unit.update()
    powerGraph.update()
    build.update()
    bullet.update()
    bullet.collide()
  }

  fun updatePooling() {
    for (p in freeQueue) {
      Pools.free(p)
    }
    freeQueue.clear()
  }
}
