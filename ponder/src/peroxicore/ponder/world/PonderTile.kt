package peroxicore.ponder.world

import arc.*
import mindustry.*
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.world.*
import peroxicore.ponder.*

class PonderTile : Tile {
  companion object {
    var tileChange = PonderTileChangeEvent()
    var preChange = PonderTilePreChangeEvent()
    var floorChange = PonderTileFloorChangeEvent()
    var overlayChange = PonderTileOverlayChangeEvent()

    val oriTileChange = TileChangeEvent()
    val oriFloorChange = TileFloorChangeEvent()
    val oriPreChange = TilePreChangeEvent()
    val oriOverlayChange = TileOverlayChangeEvent()
  }

  var tiles: Tiles

  constructor(x: Int, y: Int, tiles: Tiles) : super(x, y) {
    this.tiles = tiles
  }

  override fun fireChanged() {
    if (!Vars.world.isGenerating) {
      Events.fire(tileChange.set(tiles, this))
    }
  }

  override fun firePreChanged() {
    if (!Vars.world.isGenerating) {
      Events.fire(preChange.set(tiles, this))
    }
  }
}

fun Tiles.fillPonder() {
  for (i in 0 until width * height) {
    seti(i, PonderTile(i % width, i / width, this))
  }
}
