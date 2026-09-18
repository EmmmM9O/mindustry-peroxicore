package peroxicore.ponder.input

import arc.math.geom.*
import mindustry.ui.*
import mindustry.world.*
import peroxicore.ponder.*

interface PonderInput {
  val camera
    get() = PonderCore.camera

  val camRect: Rect
  var camSpeed: Float
  var camControl: Boolean

  var active: Boolean

  var isBuilding: Boolean

  fun setup()

  fun init()

  fun render()

  fun drawBottom()

  fun drawTop()

  fun update()

  fun begin()

  fun end()

  fun beginWorld()

  fun beforeWorld()

  fun endWorld()

  fun hovered(): Displayable?

  fun validPlace(
    x: Int,
    y: Int,
    type: Block,
    rotation: Int,
  ): Boolean
}
