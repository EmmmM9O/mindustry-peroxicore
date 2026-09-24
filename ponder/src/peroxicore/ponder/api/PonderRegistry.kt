package peroxicore.ponder.api

import arc.*
import arc.math.geom.*
import arc.struct.*
import mindustry.content.*
import mindustry.ctype.*
import mindustry.game.*
import mindustry.gen.*
import peroxicore.ponder.*
import peroxicore.ponder.api.dsl.*
import peroxicore.ponder.scene.*

object PonderRegistry {
  val contentScenes = ObjectMap<UnlockableContent, PonderScene>()

  fun load() {
    Blocks.electrolyzer.register {
      keyframes {
        speed(1f)
        world(13, 11) {
          fillFloor(Blocks.rhyolite)
          (4 at 3).around3().fill(Blocks.rhyoliteVent)
          (4 at 7).around3().fill(Blocks.rhyoliteVent)
          (8 at 5).focus(5)
        }
        frame(50f) {
          Weathers.rain.create()
          (8 at 5).build(Blocks.electrolyzer)
          (9 at 5).label("电解机", 4f)
          UnitTypes.flare.spawn(Team.get(2), 6 * 4f, 5 * 8f)
        }
        frame(200f) {
          (4 at 3).focus(5)
          (5 at 3).label("涡轮冷凝器 发电", 4f)
          (4 at 3)
            .build(Blocks.ventCondenser)
            .thenChain {
              (4 at 7).focus(5)
              (5 at 7).label("排气冷凝器 产生水", 4f)
              (4 at 7).build(Blocks.turbineCondenser)
            }.thenChain {
              full()
              (4 at 5).build(Blocks.beamNode)
            }
        }
        frame(400f, Icon.liquid) {
          (9 at 5).healEffect()
          (6 at 4).build(Blocks.reinforcedConduit)
          (6 at 5).label("输入水", 3f)
          (8 at 7).build(Blocks.reinforcedConduit, 1)
          (10 at 7).label("输出", 3f)
          (8 at 3).build(Blocks.reinforcedConduit, 3)
          (10 at 3).label("输出", 3f)
        }
        frame(500f) {
          (6 at 4).healEffect()
          (8 at 7).healEffect()
          (8 at 3).healEffect()
        }
      }
    }
  }

  fun has(content: UnlockableContent) = contentScenes.containsKey(content)

  fun show(content: UnlockableContent) {
    contentScenes.get(content)?.let {
      PonderCore.scene = it
      Core.app.post {
        PonderCore.ponder.load()
        PonderCore.ponder.setup()
        PonderCore.ponder.show()
      }
    }
  }
}

fun UnlockableContent.register(block: UnlockableContentPonderScope.() -> Unit) {
  PonderRegistry.contentScenes.put(this, scene(block))
}

@PonderDslMarker class PonderRegistryScope
