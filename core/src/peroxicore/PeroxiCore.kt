package peroxicore

import arc.util.*
import mindustry.*
import mindustry.mod.*
import peroxicore.annotations.*
import peroxicore.mod.*
import peroxicore.util.*
import peroxicore.util.handler.*

@ModConfig(
  name = "peroxicore",
  displayName = "PeroxiCore",
)
class PeroxiCore : Mod() {
  companion object {
    val log: Logger =
      logConfig {}
      .logger {
        minimumLevel(LogLevel.DEBUG)
        formatter { "$level|$message|$location" }
        name("PeroxiCore")
        arcHandlerAppender()
      }
  }

  init {
    EventsHandler.setup()
    Vars.platform = POCPlatform(Vars.platform, this.javaClass.classLoader)

    Log.level = Log.LogLevel.debug
    Log.logger = Log.LogHandler { level, text ->
      log.at {
        level(level.toLogLevel())
        mark(Marks.log)
        message(text)
      }
    }

    log.info { "Peroxicore Load" }
  }

  override fun init() {}

  override fun loadContent() {}
}
