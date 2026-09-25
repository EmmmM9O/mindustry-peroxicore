package peroxicore.ponder

import arc.*
import arc.func.*
import mindustry.ai.*
import mindustry.game.EventType.*
import peroxicore.ponder.world.*
import peroxicore.util.handler.*

class PonderBlockIndexer : BlockIndexer() {
  val worldLoad: Cons<WorldLoadEvent> = EventsHandler.removeLast<WorldLoadEvent>()!!

  init {
    val pre = EventsHandler.removeLast<TilePreChangeEvent>()!!
    val change = EventsHandler.removeLast<TileChangeEvent>()!!
    val floor = EventsHandler.removeLast<TileFloorChangeEvent>()!!

    Events.on(PonderTilePreChangeEvent::class.java) { event ->
      pre.get(PonderTile.oriPreChange.set(event.tile))
    }

    Events.on(PonderTileChangeEvent::class.java) { event ->
      change.get(PonderTile.oriTileChange.set(event.tile))
    }

    Events.on(PonderTileFloorChangeEvent::class.java) { event ->
      floor.get(PonderTile.oriFloorChange.set(event.tile, event.previous, event.floor))
    }
  }

  fun reload() {
    worldLoad.get(WorldLoadEvent())
  }
}
