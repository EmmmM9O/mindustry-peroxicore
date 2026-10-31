package peroxicore

import arc.*
import arc.util.*
import arc.graphics.*
import arc.graphics.g2d.*

import mindustry.*
import mindustry.mod.*
import mindustry.graphics.*
import mindustry.game.*
import peroxicore.annotations.*
import peroxicore.ui.*
import peroxicore.mod.*
import peroxicore.input.*
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

    lateinit var input: PeroxiCoreInput
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

    Events.on(EventType.ClientLoadEvent::class.java) {
      addSettings()
      if (Vars.headless) return@on
      debugTools = DebugTools()
      debugTools.visible = debug
    }
  }

  fun addSettings(){
    Vars.ui.settings.apply {
      dev.checkPref("peroxicore-debug", false) {
        debug = it
        debugTools.visible = it
      }
    }
  }

  override fun init() {
    if (Vars.headless) return
    if (Vars.mobile) {
      input = PeroxiCoreMobileInput()
    }
    Events.run(EventType.Trigger.drawOver, this::drawOver)
    Events.run(EventType.Trigger.update, this::update)
    input.add()
  }

  fun update() {
    input.update()
  }

  fun drawOver() {
    Draw.draw(Layer.overlayUI) {
      input.drawTop()
    }
  }

  override fun loadContent() {}
}
