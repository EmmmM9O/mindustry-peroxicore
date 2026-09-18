package peroxicore.ponder

import mindustry.world.*
import mindustry.world.blocks.environment.*

class PonderTileChangeEvent {
  lateinit var tiles: Tiles
  lateinit var tile: Tile

  fun set(
    tiles: Tiles,
    tile: Tile,
  ): PonderTileChangeEvent {
    this.tiles = tiles
    this.tile = tile
    return this
  }
}

class PonderTilePreChangeEvent {
  lateinit var tiles: Tiles
  lateinit var tile: Tile

  fun set(
    tiles: Tiles,
    tile: Tile,
  ): PonderTilePreChangeEvent {
    this.tiles = tiles
    this.tile = tile
    return this
  }
}

class PonderTileFloorChangeEvent {
  lateinit var tiles: Tiles
  lateinit var tile: Tile
  lateinit var previous: Floor
  lateinit var floor: Floor

  fun set(
    tiles: Tiles,
    tile: Tile,
    previous: Floor,
    floor: Floor,
  ): PonderTileFloorChangeEvent {
    this.tiles = tiles
    this.tile = tile
    this.previous = previous
    this.floor = floor
    return this
  }
}

class PonderTileOverlayChangeEvent {
  lateinit var tiles: Tiles
  lateinit var tile: Tile
  lateinit var previous: Floor
  lateinit var overlay: Floor

  fun set(
    tiles: Tiles,
    tile: Tile,
    previous: Floor,
    overlay: Floor,
  ): PonderTileOverlayChangeEvent {
    this.tiles = tiles
    this.tile = tile
    this.previous = previous
    this.overlay = overlay
    return this
  }
}

enum class PonderTrigger {
  update,
}
