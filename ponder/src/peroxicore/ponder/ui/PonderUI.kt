package peroxicore.ponder.ui

import arc.*
import arc.input.*
import arc.math.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.event.*
import arc.scene.ui.layout.*
import arc.struct.*
import arc.util.*
import mindustry.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.fragments.*
import mindustry.world.*
import peroxicore.ponder.*
import peroxicore.ponder.api.*
import peroxicore.util.reflect.*
import universe.util.reflect.*

var Scene.root_: Group by accessField("root")
var Scene.touchFocuses: Any by accessFieldAny("touchFocuses")

object PonderUI {
  val labels = Seq<WorldLabel?>()
  val freeLabels = IntQueue()

  val originScene = Core.scene
  val originTouchFocuses = Core.scene.touchFocuses

  val scene =
    object : Scene() {
      val touchFocusesA = this.touchFocuses
      val tempCoords = Vec2()

      override fun hit(stageX: Float, stageY: Float, touchable: Boolean): Element? {
        this.root.stageToLocalCoordinates(tempCoords.set(stageX, stageY))
        return this.root.hit(tempCoords.x, tempCoords.y, touchable)
      }

      override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: KeyCode): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        originScene.touchFocuses = touchFocusesA
        val res = super.touchDown(screenX, screenY, pointer, button)
        PonderCore.end()
        originScene.touchFocuses = originTouchFocuses
        return res
      }

      override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.touchDragged(screenX, screenY, pointer)
        PonderCore.end()
        return res
      }

      override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: KeyCode): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.touchUp(screenX, screenY, pointer, button)
        PonderCore.end()
        return res
      }

      override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.mouseMoved(screenX, screenY)
        PonderCore.end()
        return res
      }

      override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.scrolled(amountX, amountY)
        PonderCore.end()
        return res
      }

      override fun keyDown(keyCode: KeyCode): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.keyDown(keyCode)
        PonderCore.end()
        return res
      }

      override fun keyUp(keyCode: KeyCode): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.keyUp(keyCode)
        PonderCore.end()
        return res
      }

      override fun keyTyped(character: Char): Boolean {
        if (!PonderCore.ponder.display) return false
        PonderCore.begin()
        val res = super.keyTyped(character)
        PonderCore.end()
        return res
      }
    }
  val emptyRoot = scene.root
  var root: Group? = null
    set(value) {
      /*
      scene.root.clearChildren()
      value?.let {
        scene.root.addChild(it)
      }*/
      scene.root_ = value ?: emptyRoot
      field = value
    }

  fun update() {
    scene.root_ = emptyRoot
    scene.act()
    scene.root_ = root ?: emptyRoot
  }

  fun resize(width: Int, height: Int) {
    scene.resize(width, height)
  }

  //  lateinit var ponderingIcon: TextureRegion
  init {
    Events.run(Trigger.draw) {
      /*
      if (PonderCore.rendering) return@run
      Groups.player.each { player ->
        val unit = player.unit()
        if (unit == null) return@each

        if (player.pondering) {
          Draw.z(Layer.playerName)
          Draw.rect(ponderingIcon, unit.x + 4f, unit.y + 4f, 12f, 12f)
          Draw.reset()
        }
      }*/
    }
  }

  fun showLabel(
    info: String,
    duration: Float,
    worldx: Float,
    worldy: Float,
    flags: Byte,
  ): WorldLabel =
    WorldLabel.create().apply {
      val id =
        if (freeLabels.isEmpty) {
          labels.add(this).size - 1
        } else {
          freeLabels.removeFirst().also {
            labels.set(it, this)
          }
        }

      this.id = Int.MIN_VALUE
      this.x = worldx
      this.y = worldy
      text = info
      this.flags = flags
      this.duration = if (duration == Float.MAX_VALUE) -1f else duration
      if (this.duration >= 0 && expired == null) {
        expired = {
          removeLabel(id)
        }
      }
      add()
    }

  fun removeLabel(id: Int) {
    labels.set(id, null)
    freeLabels.addLast(id)
  }

  fun clearLabel() {
    labels.clear()
    freeLabels.clear()
  }

  fun reload() {
    clearLabel()
  }

  var PlacementFragment.topTable: Table by accessField("topTable")
  var PlacementFragment.toggler: Table by accessField("toggler")
  var PlacementFragment.menuHoverBlock: Block? by accessField("menuHoverBlock")
  var Element.update_: Runnable? by accessField("update")
  var Table.sizeInvalid by accessBoolean("sizeInvalid")
  var Element.needsLayout by accessBoolean("needsLayout")
  var lastTogger: Table? = null

  fun load() {
    Core.input.inputMultiplexer.addProcessor(0, scene)
    //    ponderingIcon = IFiles.getModAtlas("pondering")
    val frag = Vars.ui.hudfrag.blockfrag
    val tab = frag.toggler.parent
    val ori = tab.update_
    tab.update {
      ori?.run()
      if (lastTogger == frag.toggler) return@update
      lastTogger = frag.toggler
      rebuild()
    }
  }

  fun rebuild() {
    val frag = Vars.ui.hudfrag.blockfrag
    val tab = frag.topTable.cells.first().get() as Table
    val ori = tab.update_
    tab.update {
      val last = tab.cells.firstOpt()?.get()
      ori?.run()
      if (tab.cells.firstOpt()?.get() === last) return@update
      val content = frag.menuHoverBlock ?: Vars.control.input.block ?: return@update
      if (!PonderRegistry.has(content)) return@update
      tab.find<Element>("blockinfo")?.let {
        val header = it.parent as Table
        header.removeChild(it)
        header
          .button("W", Styles.flatBordert) {
            PonderRegistry.show(content)
          }
          .size(8 * 5f)
          .padTop(-5f)
          .padRight(5f)
          .right()
        header.add(it).size(8 * 5f).padTop(-5f).padLeft(5f).padRight(-5f).right()
      }
    }
  }

  fun begin() {
    Core.scene = scene
  }

  fun end() {
    Core.scene = originScene
  }
}
