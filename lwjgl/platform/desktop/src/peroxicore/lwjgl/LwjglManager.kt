package peroxicore.lwjgl

import org.lwjgl.opengl.*
import org.lwjgl.system.*

object LwjglManager {
  fun setup() {}

  fun initGL() {
    Configuration.OPENGL_EXPLICIT_INIT.set(true)
    GL.create()
    GL.createCapabilities()
  }

  fun setLwjglGL() {}

  fun setOriginGL() {}
}
