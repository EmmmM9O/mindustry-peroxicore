package peroxicore.util

import kotlin.random.Random
import kotlin.test.*

class TileMergeTest {
  private fun tile(value: Int): TileData = TileData(value.toShort())

  private fun short(value: Int): Short = value.toShort()

  private fun assertValidCoverage(chunk: TileChunk, merged: List<MergedTile>) {
    val cover = Array(chunk.height.toInt()) { IntArray(chunk.width.toInt()) }
    for (rect in merged) {
      assertTrue(rect.width.toInt() > 0, "width must be positive")
      assertTrue(rect.height.toInt() > 0, "height must be positive")
      assertTrue(rect.x >= 0 && rect.y >= 0, "origin must be non-negative")
      assertTrue(rect.right <= chunk.width.toInt(), "right edge out of bounds")
      assertTrue(rect.bottom <= chunk.height.toInt(), "bottom edge out of bounds")
      assertEquals(rect.tile.data, chunk[rect.x, rect.y].data, "representative tile mismatch")
      for (y in rect.y until rect.bottom) {
        for (x in rect.x until rect.right) {
          cover[y][x]++
          assertEquals(rect.tile.data, chunk.dataAt(x, y), "data mismatch at ($x, $y)")
        }
      }
    }
    for (y in 0 until chunk.height.toInt()) {
      for (x in 0 until chunk.width.toInt()) {
        assertEquals(1, cover[y][x], "coverage mismatch at ($x, $y)")
      }
    }
  }

  @Test
  fun uniformChunkMergesIntoOneRectangle() {
    for (quality in MergeQuality.entries) {
      val chunk = TileChunk(5, 3)
      chunk.fill(tile(7))

      val merged = GreedyTileMerger(quality).merge(chunk)

      assertEquals(1, merged.size)
      val rect = merged.single()
      assertEquals(0, rect.x)
      assertEquals(0, rect.y)
      assertEquals(5, rect.width.toInt())
      assertEquals(3, rect.height.toInt())
      assertEquals(short(7), rect.tile.data)
      assertValidCoverage(chunk, merged)
    }
  }

  @Test
  fun separatesDifferentData() {
    val chunk = TileChunk(3, 3)
    for (y in 0 until 3) {
      for (x in 0 until 3) {
        chunk.setData(x, y, short((x + y) and 1))
      }
    }

    val merged = GreedyTileMerger(MergeQuality.QUALITY).merge(chunk)

    assertEquals(9, merged.size)
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun mergesVerticalRunsAcrossRows() {
    val chunk = TileChunk(4, 4)
    for (y in 0 until 4) {
      for (x in 0 until 2) chunk.setData(x, y, short(1))
      for (x in 2 until 4) chunk.setData(x, y, short(2))
    }

    val merged = GreedyTileMerger(MergeQuality.BALANCED).merge(chunk)

    assertEquals(2, merged.size)
    assertTrue(
      merged.any {
        it.x == 0 && it.y == 0 && it.width.toInt() == 2 && it.height.toInt() == 4 && it.tile.data == short(1)
      }
    )
    assertTrue(
      merged.any {
        it.x == 2 && it.y == 0 && it.width.toInt() == 2 && it.height.toInt() == 4 && it.tile.data == short(2)
      }
    )
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun handlesSingleTileChunk() {
    val chunk = TileChunk(1, 1)
    chunk.fill(tile(42))

    val merged = GreedyTileMerger(MergeQuality.QUALITY).merge(chunk)

    val rect = merged.single()
    assertEquals(0, rect.x)
    assertEquals(0, rect.y)
    assertEquals(1, rect.width.toInt())
    assertEquals(1, rect.height.toInt())
    assertEquals(short(42), rect.tile.data)
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun dynamicMergeReusesBufferAndObjects() {
    val chunk = TileChunk(4, 1)
    val merger = ScanlineTileMerger()
    val first = chunk.merged(merger)
    assertEquals(1, first.size)
    assertSame(first, chunk.merged(merger))
    val firstRect = first.single()

    chunk.setData(1, 0, short(9))

    val second = chunk.merged(merger)
    assertSame(first, second)
    assertSame(firstRect, second[0])
    assertValidCoverage(chunk, second)
    assertEquals(listOf(0, 9, 0), second.sortedBy { it.x }.map { it.tile.data.toInt() })

    val revision = chunk.revision
    chunk.setData(1, 0, short(9))
    assertEquals(revision, chunk.revision)
    assertSame(second, chunk.merged(merger))
  }

  @Test
  fun invalidationForcesRecompute() {
    val chunk = TileChunk(2, 1)
    var mergeCalls = 0
    val merger = TileMerger { chunk, state ->
      mergeCalls++
      ScanlineTileMerger().mergeInto(chunk, state)
    }

    val first = chunk.merged(merger)
    assertEquals(1, mergeCalls)

    chunk.invalidateMerged()
    val second = chunk.merged(merger)

    assertEquals(2, mergeCalls)
    assertSame(first, second)
    assertValidCoverage(chunk, second)
  }

  @Test
  fun qualityModesAreMonotonicAndValid() {
    val random = Random(1234)
    repeat(40) {
      val chunk = TileChunk(random.nextInt(1, 9), random.nextInt(1, 9))
      for (y in 0 until chunk.height.toInt()) {
        for (x in 0 until chunk.width.toInt()) {
          chunk.setData(x, y, short(random.nextInt(4)))
        }
      }

      val fast = GreedyTileMerger(MergeQuality.FAST).merge(chunk)
      val balanced = GreedyTileMerger(MergeQuality.BALANCED).merge(chunk)
      val quality = GreedyTileMerger(MergeQuality.QUALITY).merge(chunk)

      assertValidCoverage(chunk, fast)
      assertValidCoverage(chunk, balanced)
      assertValidCoverage(chunk, quality)
      assertTrue(balanced.size <= fast.size)
      assertTrue(quality.size <= balanced.size)
    }
  }

  @Test
  fun rejectsInvalidDimensions() {
    assertFailsWith<IllegalArgumentException> { TileChunk(0, 1) }
    assertFailsWith<IllegalArgumentException> { TileChunk(1, 0) }
    assertFailsWith<IllegalArgumentException> { TileChunk(-1, 1) }
    assertFailsWith<IllegalArgumentException> { TileChunk(Int.MAX_VALUE, 2) }
  }

  @Test
  fun chunkDimensionsAreShortAndSizeIsCapped() {
    val chunk = TileChunk(1024, 1024)
    assertEquals(1024.toShort(), chunk.width)
    assertEquals(1024.toShort(), chunk.height)
    assertEquals(TileChunk.MAX_CHUNK_SIZE, chunk.size)

    assertFailsWith<IllegalArgumentException> { TileChunk(1025, 1024) }
    assertFailsWith<IllegalArgumentException> { TileChunk(32768, 1) }
  }

  @Test
  fun validatesCoordinates() {
    val chunk = TileChunk(2, 2)
    assertFailsWith<IndexOutOfBoundsException> { chunk.get(-1, 0) }
    assertFailsWith<IndexOutOfBoundsException> { chunk.set(2, 0, tile(1)) }
  }

  @Test
  fun tileDataUsesShort() {
    val chunk = TileChunk(1, 1)
    chunk.setData(0, 0, short(123))

    assertEquals(short(123), chunk.dataAt(0, 0))
    assertEquals(short(123), chunk[0, 0].data)
    assertEquals(tile(123), chunk[0, 0])
  }

  @Test
  fun scanlineMergesUniformChunkIntoOneRectangle() {
    val chunk = TileChunk(5, 3)
    chunk.fill(tile(7))

    val merged = ScanlineTileMerger().merge(chunk)

    val rect = merged.single()
    assertEquals(0, rect.x)
    assertEquals(0, rect.y)
    assertEquals(5, rect.width.toInt())
    assertEquals(3, rect.height.toInt())
    assertEquals(short(7), rect.tile.data)
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun scanlineSeparatesDifferentData() {
    val chunk = TileChunk(3, 3)
    for (y in 0 until 3) {
      for (x in 0 until 3) {
        chunk.setData(x, y, short((x + y) and 1))
      }
    }

    val merged = ScanlineTileMerger().merge(chunk)

    assertEquals(9, merged.size)
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun scanlineMergesVerticalRunsAcrossRows() {
    val chunk = TileChunk(4, 4)
    for (y in 0 until 4) {
      for (x in 0 until 2) chunk.setData(x, y, short(1))
      for (x in 2 until 4) chunk.setData(x, y, short(2))
    }

    val merged = ScanlineTileMerger().merge(chunk)

    assertEquals(2, merged.size)
    assertTrue(
      merged.any {
        it.x == 0 && it.y == 0 && it.width.toInt() == 2 && it.height.toInt() == 4 && it.tile.data == short(1)
      }
    )
    assertTrue(
      merged.any {
        it.x == 2 && it.y == 0 && it.width.toInt() == 2 && it.height.toInt() == 4 && it.tile.data == short(2)
      }
    )
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun scanlineProducesValidCoverageOnRandomPatterns() {
    val random = Random(5678)
    repeat(40) {
      val chunk = TileChunk(random.nextInt(1, 9), random.nextInt(1, 9))
      for (y in 0 until chunk.height.toInt()) {
        for (x in 0 until chunk.width.toInt()) {
          chunk.setData(x, y, short(random.nextInt(4)))
        }
      }

      val merged = ScanlineTileMerger().merge(chunk)

      assertValidCoverage(chunk, merged)
    }
  }

  @Test
  fun scanlineIsDynamicThroughChunkCache() {
    val chunk = TileChunk(4, 1)
    val merger = ScanlineTileMerger()
    val first = chunk.merged(merger)
    assertSame(first, chunk.merged(merger))
    val firstRect = first.single()

    chunk.setData(1, 0, short(9))

    val second = chunk.merged(merger)
    assertSame(first, second)
    assertSame(firstRect, second[0])
    assertValidCoverage(chunk, second)
    assertEquals(listOf(0, 9, 0), second.sortedBy { it.x }.map { it.tile.data.toInt() })
  }

  @Test
  fun incrementalMergerUpdatesLocallyAndReusesObjects() {
    val chunk = TileChunk(16, 16)
    for (y in 0 until 16) {
      for (x in 0 until 16) {
        chunk.setData(x, y, short((x + y) and 1))
      }
    }

    val merger = IncrementalTileMerger()
    val first = chunk.merged(merger)
    assertEquals(256, first.size)
    assertEquals(1, merger.fullMerges)
    val far = first.first { it.x == 15 && it.y == 15 }

    chunk.setData(0, 0, short(2))
    val second = chunk.merged(merger)

    assertSame(first, second)
    assertTrue(merger.incrementalUpdates >= 1)
    assertValidCoverage(chunk, second)
    assertSame(far, second.first { it.x == 15 && it.y == 15 })
  }

  @Test
  fun incrementalMergerHandlesClusteredBatchesAndRebuildsPeriodically() {
    val random = Random(2024)
    val chunk = TileChunk(32, 32)
    for (y in 0 until 32) {
      for (x in 0 until 32) {
        chunk.setData(x, y, short((x + y) and 1))
      }
    }
    val merger = IncrementalTileMerger(fullRebuildInterval = 8)

    val initial = chunk.merged(merger)
    assertEquals(1024, initial.size)

    repeat(40) {
      val baseX = random.nextInt(0, 30)
      val baseY = random.nextInt(0, 30)
      chunk.setData(baseX, baseY, short(2))
      chunk.setData(baseX + 1, baseY, short(2))
      chunk.setData(baseX, baseY + 1, short(2))
      val merged = chunk.merged(merger)
      assertValidCoverage(chunk, merged)
    }

    assertTrue(merger.incrementalUpdates > 0)
    assertTrue(merger.fullMerges >= 2)
  }

  @Test
  fun incrementalMergerFallsBackForLargeBatch() {
    val chunk = TileChunk(32, 32)
    chunk.fill(tile(0))
    val merger = IncrementalTileMerger()
    chunk.merged(merger)
    assertEquals(1, merger.fullMerges)

    chunk.fill(tile(1))
    val merged = chunk.merged(merger)

    assertTrue(merger.fullMerges >= 2)
    assertValidCoverage(chunk, merged)
    assertEquals(1, merged.size)
    assertEquals(short(1), merged.single().tile.data)
  }

  @Test
  fun fullMergesReuseMergedTileObjects() {
    val chunk = TileChunk(4, 4)
    chunk.fill(tile(1))
    val merger = GreedyTileMerger(MergeQuality.FAST)
    val first = chunk.merged(merger)
    val firstRect = first.single()

    chunk.fill(tile(2))
    val second = chunk.merged(merger)

    assertSame(first, second)
    assertSame(firstRect, second.single())
    assertEquals(short(2), second.single().tile.data)
  }

  @Test
  fun incrementalMergerStaysValidAcrossRandomBatches() {
    val random = Random(777)
    val chunk = TileChunk(32, 32)
    for (y in 0 until 32) {
      for (x in 0 until 32) {
        chunk.setData(x, y, short(((x shr 2) + (y shr 2)) and 3))
      }
    }
    val merger = IncrementalTileMerger(fullRebuildInterval = 64)
    chunk.merged(merger)

    repeat(200) {
      repeat(3) {
        chunk.setData(random.nextInt(32), random.nextInt(32), short(random.nextInt(5)))
      }
      val merged = chunk.merged(merger)
      assertValidCoverage(chunk, merged)
    }

    assertTrue(merger.incrementalUpdates > 0)
  }

  @Test
  fun listenerReceivesFullMergeOnFirstMerge() {
    val chunk = TileChunk(4, 4)
    chunk.fill(tile(1))
    val listener = RecordingListener()
    chunk.mergedTileListener = listener

    chunk.merged(ScanlineTileMerger())

    assertEquals(1, listener.fullMerges)
    assertEquals(0, listener.incrementalMerges)
    assertEquals(
      listOf(RectRecord(short(1), 0, 0, short(4), short(4))),
      listener.lastMerged,
    )
  }

  @Test
  fun listenerGetsBaselineWhenSetAfterMerge() {
    val chunk = TileChunk(4, 4)
    chunk.fill(tile(2))
    val merger = ScanlineTileMerger()
    chunk.merged(merger)

    val listener = RecordingListener()
    chunk.mergedTileListener = listener

    assertEquals(1, listener.fullMerges)
    assertEquals(0, listener.incrementalMerges)
    assertEquals(1, listener.lastMerged.size)
    assertEquals(short(2), listener.lastMerged.single().data)
  }

  @Test
  fun listenerTracksIncrementalAddRemoveAndSizeChanges() {
    val chunk = TileChunk(8, 8)
    chunk.fill(tile(0))
    val merger = IncrementalTileMerger()
    val listener = RecordingListener()
    chunk.mergedTileListener = listener
    chunk.merged(merger)
    listener.clear()

    chunk.setData(3, 3, short(1))
    val merged = chunk.merged(merger)

    assertEquals(0, listener.fullMerges)
    assertEquals(1, listener.incrementalMerges)
    assertEquals(1, listener.removed.size)
    val removed = listener.removed.single()
    assertEquals(short(0), removed.data)
    assertEquals(short(8), removed.width)
    assertEquals(short(8), removed.height)
    assertTrue(listener.added.isNotEmpty())
    assertEquals(removed.area, listener.added.sumOf { it.area })
    assertTrue(listener.added.any { it.width.toInt() < 8 || it.height.toInt() < 8 })
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun listenerUsesFullCallbackOnFallback() {
    val chunk = TileChunk(32, 32)
    chunk.fill(tile(0))
    val merger = IncrementalTileMerger()
    val listener = RecordingListener()
    chunk.mergedTileListener = listener
    chunk.merged(merger)
    listener.clear()

    chunk.fill(tile(1))
    val merged = chunk.merged(merger)

    assertEquals(1, listener.fullMerges)
    assertEquals(0, listener.incrementalMerges)
    assertTrue(listener.added.isEmpty())
    assertTrue(listener.removed.isEmpty())
    assertEquals(short(1), listener.lastMerged.single().data)
    assertValidCoverage(chunk, merged)
  }

  @Test
  fun listenerIsNotCalledOnCacheHit() {
    val chunk = TileChunk(4, 4)
    chunk.fill(tile(1))
    val listener = RecordingListener()
    chunk.mergedTileListener = listener
    val merger = ScanlineTileMerger()
    chunk.merged(merger)
    listener.clear()

    chunk.merged(merger)

    assertEquals(0, listener.fullMerges)
    assertEquals(0, listener.incrementalMerges)
    assertTrue(listener.added.isEmpty())
    assertTrue(listener.removed.isEmpty())
  }
}

private data class RectRecord(
  val data: Short,
  val x: Int,
  val y: Int,
  val width: Short,
  val height: Short,
) {
  val area: Int get() = width.toInt() * height.toInt()
}

private class RecordingListener : MergedTileListener {
  val added = mutableListOf<RectRecord>()
  val removed = mutableListOf<RectRecord>()
  var fullMerges = 0
  var incrementalMerges = 0
  var lastMerged: List<RectRecord> = emptyList()

  fun clear() {
    added.clear()
    removed.clear()
    fullMerges = 0
    incrementalMerges = 0
    lastMerged = emptyList()
  }

  override fun onTileAdded(chunk: TileChunk, tile: MergedTile) {
    added += RectRecord(tile.data, tile.x, tile.y, tile.width, tile.height)
  }

  override fun onTileRemoved(chunk: TileChunk, tile: MergedTileSnapshot) {
    removed += RectRecord(tile.data, tile.x, tile.y, tile.width, tile.height)
  }

  override fun onIncrementalMerge(chunk: TileChunk, merged: List<MergedTile>) {
    incrementalMerges++
    lastMerged = merged.map { RectRecord(it.data, it.x, it.y, it.width, it.height) }
  }

  override fun onFullMerge(chunk: TileChunk, merged: List<MergedTile>) {
    fullMerges++
    lastMerged = merged.map { RectRecord(it.data, it.x, it.y, it.width, it.height) }
  }
}
