package peroxicore

import arc.*
import arc.util.*
import mindustry.mod.*
import org.lwjgl.system.*
import peroxicore.annotations.*
import peroxicore.lwjgl.*
import java.io.*
import java.util.function.*

class PODebugStream : Supplier<PrintStream> {
  override fun get() = PrintStream(object : OutputStream() {
    private val line = StringBuilder()

    override fun write(b: Int) {
      val c = b.toChar()
      if (c == '\n') {
        flushLine()
      } else {
        line.append(c)
      }
    }

    private fun flushLine() {
      val msg = line.toString()
      line.setLength(0)
      if (msg.isEmpty()) return

      Log.info(msg)
    }
  })
}

@ModConfig(
  name = "peroxicore-lwjgl",
  displayName = "PeroxiCore Lwjgl",
  dependencies = ["peroxicore"]
)
@ImportPeroxiCore
class PeroxiCoreLwjgl : Mod() {
  init {
    Core.app.post {
      Configuration.DEBUG.set(true)
      Configuration.DEBUG_LOADER.set(true)
      Configuration.DEBUG_FUNCTIONS.set(true)
      Configuration.DEBUG_STREAM.set("peroxicore.PODebugStream")
      LwjglManager.setup()
      LwjglManager.initGL()
      LwjglManager.setLwjglGL()
    }
  }

  override fun init() {
  }

  override fun loadContent() {

  }
}
