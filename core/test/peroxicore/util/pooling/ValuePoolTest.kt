package peroxicore.util.pooling

import arc.util.pooling.*
import kotlin.test.*

class ValuePoolTest {
  /** 测试用对象，记录创建次数以判断是否真的复用。 */
  private class Buffer(val feature: Int) : Pool.Poolable {
    var resets = 0
      private set

    override fun reset() {
      resets++
    }
  }

  private class Factory {
    val created = mutableListOf<Buffer>()

    fun create(feature: Int): Buffer = Buffer(feature).also { created.add(it) }
  }

  @Test
  fun useReturnsSameObjectForSameFeature() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }
    val screen = pool.hook(0)

    val first = screen.use { it }
    val second = screen.use { it }

    assertSame(first, second, "特征值相同的对象应当被复用")
    assertEquals(1, factory.created.size)
    assertEquals(0, first.feature)
    assertEquals(1, screen.free, "use 结束后对象应当已归还到桶里")
  }

  @Test
  fun differentFeaturesNeverShareObjects() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }
    val screen = pool.hook(0)
    val half = pool.hook(1)

    val a = screen.use { it }
    val b = half.use { it }

    assertNotSame(a, b, "不同特征值不应共用对象")
    assertEquals(0, a.feature)
    assertEquals(1, b.feature)
    assertEquals(2, factory.created.size)
    assertEquals(1, screen.free)
    assertEquals(1, half.free)
  }

  @Test
  fun hookDoesNotPreallocate() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }

    // 预先创建大量 hook，不应产生任何对象
    (0..ValuePool.MAX_FEATURE).forEach { pool.hook(it) }

    assertEquals(0, factory.created.size, "hook 不应预分配对象")
    assertEquals(0, pool.free)
  }

  @Test
  fun useWithFeatureMatchesHookUse() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }

    val viaPool = pool.use(2) { it }
    assertEquals(2, viaPool.feature, "工厂应当收到传入的特征值")
    assertEquals(0, factory.created.size - 1, "第一次 use 应当新建对象")
    assertEquals(1, pool.freeOf(2), "use 结束后对象应当已归还")

    // 第二次直接用 pool.use，应当复用同一个对象
    val again = pool.use(2) { it }
    assertSame(viaPool, again, "pool.use 与 hook.use 应当共用同一个桶")
    assertEquals(1, factory.created.size)

    // 返回值和 hook 版本一致
    assertEquals("ok", pool.use(2) { "ok" })
  }

  @Test
  fun useWithFeatureFreesOnException() {
    val pool = ValuePool.of { f: Int -> Buffer(f) }

    assertFailsWith<IllegalStateException> { pool.use(4) { error("boom") } }
    assertEquals(1, pool.freeOf(4), "抛异常时也必须归还对象")

    // 异常退出时 reset 过一次，这次 use 归还时再 reset 一次
    val buffer = pool.use(4) { it }
    assertEquals(2, buffer.resets, "异常归还和正常归还都应当调用 reset")
  }

  @Test
  fun useWithInvalidFeatureIsRejected() {
    val pool = ValuePool.of { f: Int -> Buffer(f) }
    assertFailsWith<IllegalArgumentException> { pool.use(-1) { it } }
    assertFailsWith<IllegalArgumentException> { pool.use(ValuePool.MAX_FEATURE + 1) { it } }
  }

  @Test
  fun nestedUseReusesAfterInnerFree() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }
    val hook = pool.hook(0)

    var inner: Buffer? = null
    val outer = hook.use {
      // 外层在用，桶是空的，内层必须新建
      inner = hook.use { it }
      it
    }

    assertNotSame(outer, inner!!)
    assertEquals(2, factory.created.size)

    // 两次 use 都归还之后，桶里应当有两个对象
    assertEquals(2, hook.free)
    val reused = hook.use { it }
    assertTrue(reused === outer || reused === inner)
  }

  @Test
  fun useFreesOnException() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }
    val hook = pool.hook(3)

    assertFailsWith<IllegalStateException> {
      hook.use { error("boom") }
    }

    assertEquals(1, hook.free, "抛异常时也必须归还对象")
    val reused = hook.use { it }
    assertEquals(1, factory.created.size, "归还后应当复用")
    assertEquals(3, reused.feature)
  }

  @Test
  fun useReturnsBlockResult() {
    val pool = ValuePool.of { f: Int -> Buffer(f) }
    assertEquals(42, pool.hook(0).use { 42 })
  }

  @Test
  fun freeCallsResetOnPoolable() {
    val pool = ValuePool.of { f: Int -> Buffer(f) }
    val hook = pool.hook(0)
    val buffer = hook.obtain()
    hook.free(buffer)

    assertEquals(1, buffer.resets, "归还 Poolable 时应当调用 reset")
    assertEquals(1, hook.free)
  }

  @Test
  fun maxPerFeatureDropsExcess() {
    val pool = ValuePool.of(maxPerFeature = 2) { f: Int -> Buffer(f) }
    val hook = pool.hook(0)

    val buffers = (0 until 5).map { hook.obtain() }
    buffers.forEach { hook.free(it) }

    assertEquals(2, hook.free, "超出上限的对象应被丢弃")
  }

  @Test
  fun invalidFeaturesAreRejected() {
    val pool = ValuePool.of { f: Int -> Buffer(f) }

    assertFailsWith<IllegalArgumentException> { pool.hook(-1) }
    assertFailsWith<IllegalArgumentException> { pool.hook(ValuePool.MAX_FEATURE + 1) }

    // 边界值应当可用
    assertNotNull(pool.hook(0))
    assertNotNull(pool.hook(ValuePool.MAX_FEATURE))
    assertEquals(ValuePool.MAX_FEATURE + 1, pool.size)
  }

  @Test
  fun clearDropsAllFreeObjects() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }
    pool.hook(0).use { }
    pool.hook(1).use { }
    assertEquals(2, pool.free)

    pool.clear()

    assertEquals(0, pool.free)
    assertEquals(0, pool.hook(0).free)
    // clear 之后仍然可以继续获取（新建）对象
    val buffer = pool.hook(0).use { it }
    assertEquals(0, buffer.feature)
  }

  @Test
  fun obtainAndFreeWithoutHook() {
    val factory = Factory()
    val pool = ValuePool.of { f: Int -> factory.create(f) }

    val buffer = pool.obtain(5)
    pool.free(5, buffer)

    assertEquals(1, pool.freeOf(5))
    assertSame(buffer, pool.obtain(5))
    assertEquals(0, pool.freeOf(5))
  }

  // ------------------------------------------------------------------ 空闲释放

  /** 可被池彻底释放的对象。 */
  private class DisposableBuffer(val feature: Int) : Pool.Poolable, ValuePool.Disposable {
    var disposed = false
      private set

    var resets = 0
      private set

    override fun reset() {
      resets++
    }

    override fun dispose() {
      disposed = true
    }
  }

  @Test
  fun idleObjectsAreDisposedAfterConfiguredTicks() {
    val created = mutableListOf<DisposableBuffer>()
    val pool = ValuePool.of(idleTicks = 3) { f: Int -> DisposableBuffer(f).also { created.add(it) } }

    val buffer = pool.use(0) { it }
    assertFalse(buffer.disposed, "使用中的对象不应被释放")

    // 刚归还：还没到空闲阈值
    repeat(3) { pool.nextTick() }
    assertFalse(buffer.disposed, "空闲未满 3 轮时不应释放")

    pool.nextTick()
    assertTrue(buffer.disposed, "空闲满 3 轮后应当释放")
    assertEquals(1, pool.disposed)
    assertEquals(0, pool.freeOf(0), "释放后桶应当被移除")
  }

  @Test
  fun usedObjectSurvivesIdleSweep() {
    val pool = ValuePool.of(idleTicks = 1) { f: Int -> DisposableBuffer(f) }

    val buffer = pool.use(5) { it }
    pool.nextTick()
    pool.nextTick()
    assertTrue(buffer.disposed, "归还后过 1 轮应当被释放")

    // 重新取用会创建新对象
    val fresh = pool.use(5) { it }
    assertNotSame(buffer, fresh)
    assertFalse(fresh.disposed)
  }

  @Test
  fun returningRefreshesIdleTimer() {
    val pool = ValuePool.of(idleTicks = 2) { f: Int -> DisposableBuffer(f) }

    // 先拿一个放回桶里
    val first = pool.use(0) { it }
    pool.nextTick()

    // 再取出来又放回去，空闲计时应当重置
    val second = pool.use(0) { it }
    assertSame(first, second)

    pool.nextTick()
    pool.nextTick()
    assertFalse(second.disposed, "重新归还后应当重新计时")

    pool.nextTick()
    assertTrue(second.disposed)
  }

  @Test
  fun idleSweepHandlesManyObjectsPerBucket() {
    // 超过初始容量（4），检验空闲时间数组是否跟着桶一起增长
    val pool = ValuePool.of(idleTicks = 1) { f: Int -> DisposableBuffer(f) }
    val buffers = (0 until 10).map { pool.obtain(0) }
    buffers.forEach { pool.free(0, it) }
    assertEquals(10, pool.freeOf(0))

    pool.nextTick()
    pool.nextTick()
    assertTrue(buffers.all { it.disposed }, "桶内所有对象都应当被释放")
    assertEquals(0, pool.freeOf(0))
    assertEquals(10, pool.disposed)
  }

  @Test
  fun idleTicksDisabledByDefault() {
    val pool = ValuePool.of { f: Int -> DisposableBuffer(f) }
    val buffer = pool.use(0) { it }

    repeat(100) { pool.nextTick() }
    assertFalse(buffer.disposed, "默认不自动释放")
    assertEquals(1, pool.freeOf(0))
  }

  @Test
  fun nonDisposableObjectsAreJustDropped() {
    val pool = ValuePool.of(idleTicks = 1) { f: Int -> Buffer(f) }
    val buffer = pool.use(0) { it }

    pool.nextTick()
    pool.nextTick()

    assertEquals(0, pool.freeOf(0), "未实现 Disposable 的对象仍应从桶中移除")
    assertEquals(0, pool.disposed, "未实现 Disposable 的对象不计数")
    assertEquals(1, buffer.resets, "归还时仍然调用过 reset")
  }

  @Test
  fun clearDisposesPooledObjects() {
    val pool = ValuePool.of { f: Int -> DisposableBuffer(f) }
    val kept = pool.use(0) { it }
    val alsoKept = pool.obtain(1)
    pool.free(1, alsoKept)

    pool.clear()

    assertTrue(kept.disposed, "clear 应当释放桶中的对象")
    assertTrue(alsoKept.disposed)
    assertEquals(0, pool.free)
  }

  @Test
  fun idleSweepHandlesBucketLargerThanInitialCapacity() {
    // 初始容量给得很小，桶扩容后容量会大于对象数，检验空闲时间数组不会越界
    val pool = ValuePool.of(initialCapacity = 1, idleTicks = 1) { f: Int -> DisposableBuffer(f) }
    val buffers = (0 until 32).map { pool.obtain(0) }
    buffers.forEach { pool.free(0, it) }
    assertEquals(32, pool.freeOf(0))

    pool.nextTick()
    pool.nextTick()

    assertTrue(buffers.all { it.disposed })
    assertEquals(0, pool.freeOf(0))
  }

  @Test
  fun sweepAllIgnoresIdleTicks() {
    val pool = ValuePool.of(idleTicks = 1000) { f: Int -> DisposableBuffer(f) }
    val buffer = pool.use(0) { it }

    assertEquals(1, pool.sweepAll())
    assertTrue(buffer.disposed, "sweepAll 应当无视空闲阈值")
  }
}
