package peroxicore

import mindustry.*
import mindustry.mod.*
import peroxicore.annotations.*
import peroxicore.mod.*
import peroxicore.util.handler.*

@ModConfig(
  name = "peroxicore",
  displayName = "PeroxiCore"
)
class PeroxiCore : Mod() {
  init {
    EventsHandler.setup()
    Vars.platform = POCPlatform(Vars.platform, this.javaClass.classLoader)
  }

  override fun init() {}

  override fun loadContent() {}
}
