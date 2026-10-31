package peroxicore.ui

import arc.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*

import mindustry.*
import mindustry.gen.*
import mindustry.ui.*

class DebugTools : FloatingTable() {
  var visibleC = false
  val visButton: ImageButton
  val contentTable: Table
  lateinit var buttonsBar: Table
  val defSize = 48f

  init {
    key = "peroxicore-debug-tools"
    reset()
    visButton =
      ImageButton(
        ImageButton.ImageButtonStyle(Styles.cleari).apply {
          imageUp = Icon.eyeOffSmall
          imageChecked = Icon.eyeSmall
        }
      )
    visButton.changed {
      visibleC = !visibleC
      contentTable.visible = visibleC
    }

    contentTable = Table(Styles.black6).apply {}
    contentTable.visible = visibleC

    table { table ->
        table.table().row()
        table
          .table { buttons ->
            buttonsBar = buttons
            buttons.add(dragButton).size(defSize)
            buttons.add(visButton).size(defSize)
          }
          .height(defSize)
          .center()
        table.table().row()
      }
      .uniformX()
      .uniformY()
      .height(defSize)
      .fill()
    row()


    add(contentTable).width(400f).height(400f)
    Core.scene.add(this)
    loadPosition()
  }
}
