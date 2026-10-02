package peroxicore

import mindustry.mod.*
import peroxicore.annotations.*

@ModConfig(
  name = "peroxicore-lwjgl-opus",
  displayName = "PeroxiCore Lwjgl Opus",
  dependencies = ["peroxicore", "peroxicore-lwjgl"]
)
@ImportPeroxiCore
class PeroxiCoreLwjglOpus : Mod() {
}