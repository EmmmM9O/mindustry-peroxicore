package peroxicore.ponder.ui

import arc.*
import arc.scene.*
import arc.scene.ui.layout.*
import arc.struct.*
import mindustry.*
import mindustry.game.EventType.Trigger
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.fragments.*
import mindustry.world.*
import peroxicore.ponder.api.*
import universe.util.reflect.*

object PonderUI {
  val labels = Seq<WorldLabel?>()
  val freeLabels = IntQueue()

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
  var lastTogger: Table? = null

  fun load() {
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
    val tab =
      frag.topTable.cells
        .first()
        .get() as Table
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
          }.size(8 * 5f)
          .padTop(-5f)
          .padRight(5f)
          .right()
        header
          .add(it)
          .size(8 * 5f)
          .padTop(-5f)
          .padLeft(5f)
          .padRight(-5f)
          .right()
      }
    }
  }
}
