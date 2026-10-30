package peroxicore

import arc.*
import arc.util.*
import mindustry.*
import mindustry.mod.*
import mindustry.game.*
import peroxicore.annotations.*
import peroxicore.ui.*
import peroxicore.mod.*
import peroxicore.util.*
import peroxicore.util.handler.*


@ModConfig(
  name = "peroxicore",
  displayName = "PeroxiCore",
)
@ImportPeroxiCore
class PeroxiCore : Mod() {
  companion object {
    var debug = Log.level == Log.LogLevel.debug || Core.settings.getBool("peroxicore-debug", false)
    val log: Logger =
      logConfig {}
      .logger {
        minimumLevel(if(debug) LogLevel.DEBUG else LogLevel.INFO)
//        formatter { "$level|$message|$location" }
        name("PeroxiCore")
        arcHandlerAppender()
      }

    lateinit var debugTools: DebugTools
  }

  init {
    EventsHandler.setup()
    if(Vars.platform.javaClass.kotlin.qualifiedName == "peroxicore.mod.POEmbedPlatform"){
      Log.info("PeroxiCore Skip platform replace")
    }
    else Vars.platform = POCPlatform(Vars.platform, this.javaClass.classLoader)

    Log.level = Log.LogLevel.debug
    Log.logger = Log.LogHandler { level, text ->
      log.at {
        level(level.toLogLevel())
        mark(Marks.log)
        message(text)
        skip(PeroxiCore::class)
      }
    }

    log.info { "Peroxicore Load" }

    Events.run(EventType.ClientLoadEvent::class.java) {
      debugTools = DebugTools()
      Time.run(1f) {
        debugTools.setPosition(
            Core.graphics.width.toFloat() / 2f,
            Core.graphics.height.toFloat() / 1.5f
        )
      }
      debugTools.visible = debug

      addSettings()
    }
  }

  fun addSettings(){
    Vars.ui.settings.apply{
      dev.checkPref("peroxicore-debug", false) {
        debug = it
        debugTools.visible = it
      }
    }
  }

  override fun init() {}

  override fun loadContent() {}
}
