package peroxicore.util

/**
 * 一个 tile 的合并数据。
 *
 * [data] 使用 Short 存储，以降低 tilemap 的内存占用；相同 [data] 的相邻 tile 可以合并。 [TileChunk] 内部同样使用 ShortArray 扁平存储。
 *
 * Short 的取值范围是 -32768..32767；如果 tile 数据超过这个范围，需要在写入前编码/映射。
 */
@JvmInline value class TileData(val data: Short)
