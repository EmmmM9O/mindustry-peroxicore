package peroxicore.lwjgl

import arc.*
import arc.util.*
import org.lsposed.hiddenapibypass.*
import org.lwjgl.opengles.*
import org.lwjgl.system.*

object LwjglManager {
  lateinit var lwjglGL20: LwjglGL20
  var lwjglGL30: LwjglGL30? = null
  val gl20: arc.graphics.GL20? = Core.gl20
  val gl30: arc.graphics.GL30? = Core.gl30
  fun setup() {
    HiddenApiBypass.setHiddenApiExemptions("")
    Library.initialize()
    MemoryStack.stackPush().use { stack ->
      stack.nASCII("Test11", true)
      Log.info("Test address ${stack.pointerAddress}")
    }
  }

  fun initGL() {
    Configuration.OPENGLES_EXPLICIT_INIT.set(true)
    GLES.create()
    GLES.createCapabilities()
    if (Core.gl30 != null) {
      lwjglGL30 = LwjglGL30()
      lwjglGL20 = lwjglGL30!!
    } else {
      lwjglGL20 = LwjglGL20()
    }
  }

  fun setLwjglGL() {
    Core.gl20 = lwjglGL20
    Core.gl30 = lwjglGL30
    Core.gl = lwjglGL30 ?: lwjglGL20
  }

  fun setOriginGL() {
    Core.gl20 = gl20
    Core.gl30 = gl30
    Core.gl = gl30 ?: gl20
  }
}
