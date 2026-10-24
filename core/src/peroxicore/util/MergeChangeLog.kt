package peroxicore.util

internal class MergeChangeLog {
  internal val added = ArrayList<MergedTile>()

  internal val removed = ArrayList<MergedTileSnapshot>()

  private val removedPool = ArrayList<MergedTileSnapshot>()

  internal val isEmpty: Boolean
    get() = added.isEmpty() && removed.isEmpty()

  internal fun addAdded(tile: MergedTile) {
    added.add(tile)
  }

  internal fun addRemoved(tile: MergedTile) {
    val snapshot =
      if (removedPool.isEmpty()) MergedTileSnapshot()
      else removedPool.removeAt(removedPool.lastIndex)
    snapshot.set(tile)
    removed.add(snapshot)
  }

  internal fun clear() {
    added.clear()
    for (i in removed.indices) removedPool.add(removed[i])
    removed.clear()
  }
}
