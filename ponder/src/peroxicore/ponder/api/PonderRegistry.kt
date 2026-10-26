package peroxicore.ponder.api

import arc.*
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
          at(4, 3).around3().fill(Blocks.rhyoliteVent)
          at(4, 7).around3().fill(Blocks.rhyoliteVent)
          at(8, 5).focus(5)
        }
        frame(50f) {
          Weathers.rain.create()
          at(8, 5).build(Blocks.electrolyzer, config = Blocks.electrolyzer.nextConfig())
          label("电解机", 4f).at(9, 5)
          UnitTypes.flare.spawn(Team.get(2), 6 * 4f, 5 * 8f)
        }
        frame(200f) {
          at(4, 3).focus(5)
          at(5, 3).label("涡轮冷凝器 发电", 4f)
          build(Blocks.ventCondenser)
            .at(4, 3)
            .thenChain {
              at(4, 7).focus(5)
              label("排气冷凝器 产生水", 4f).at(5, 7)
              at(4, 7).build(Blocks.turbineCondenser)
            }.thenChain {
              full()
              at(4, 5).build(Blocks.beamNode)
            }
        }
        frame(400f, Icon.liquid) {
          at(9, 5).healEffect()
          at(6, 4).build(Blocks.reinforcedConduit)
          at(6, 5).label("输入水", 3f)
          at(8, 7).build(Blocks.reinforcedConduit, 1)
          at(10, 7).label("输出", 3f)
          at(8, 3).build(Blocks.reinforcedConduit, 3)
          at(10, 3).label("输出", 3f)
        }
        frame(500f) {
          at(6, 4).healEffect()
          at(8, 7).healEffect()
          at(8, 3).healEffect()
        }
      }
    }
    PonderRegistryJava.init()
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

@PonderDslMarker
class PonderRegistryScope
