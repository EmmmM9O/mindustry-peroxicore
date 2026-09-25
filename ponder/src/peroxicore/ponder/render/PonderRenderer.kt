package peroxicore.ponder.render

import arc.graphics.*

interface PonderRenderer {
  fun setup()

  fun init()

  fun render()

  fun begin()

  fun end()

  fun reload()

  fun beginWorld()

  fun beforeWorld()

  fun endWorld()

  val texture: Texture
  val camera: Camera
}
