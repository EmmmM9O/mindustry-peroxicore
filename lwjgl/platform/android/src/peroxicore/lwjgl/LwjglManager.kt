package peroxicore.lwjgl

import arc.*
import arc.util.*
import org.lsposed.hiddenapibypass.*
import org.lwjgl.opengles.*
import org.lwjgl.system.*

object LwjglManager {
  lateinit var lwjglGL20: LwjglGL20
  var lwjglGL30: LwjglGL30? = null
  val gl20: arc.graphics.GL20? = Core.gl20
  val gl30: arc.graphics.GL30? = Core.gl30

  fun setup() {
    HiddenApiBypass.setHiddenApiExemptions("")
    Library.initialize()
    /*
    MemoryStack.stackPush().use { stack ->
      Log.info("Test memUTF8 ${MemoryUtil.memUTF8(MemoryUtil.memAddress(stack.UTF8("Test11")))}")
      Log.info("Test memUTF8 ${MemoryUtil.memUTF8(MemoryUtil.memAddress(stack.UTF8("Test22")), 6)}")
      Log.info("Test memUTF8 ${MemoryUtil.memUTF8(MemoryUtil.memAddress(stack.UTF8("Test33")))}")
      Log.info("Stack Address 0x${MemoryUtil.memAddress(stack.UTF8("Test33")).toString(16)}")
    }
    val buffer = MemoryUtil.memAlloc(4)
    Log.info("Buffer Address 0x${MemoryUtil.memAddress(buffer).toString(16)}")
    MemoryUtil.memFree(buffer)

    run {
        val original = "hello_memalloc"
        val bytes = original.toByteArray(StandardCharsets.UTF_8)

        val buffer = MemoryUtil.memAlloc(bytes.size + 1)
        try {
     val addrBefore = MemoryUtil.memAddress(buffer)
            // 手动把字符串写进 memAlloc 分配的内存
            for (i in bytes.indices) {
                buffer.put(i, bytes[i])
            }
            buffer.put(bytes.size, 0)  // null terminator
     Log.info("memAddress   = 0x${MemoryUtil.memAddress(buffer).toString(16)}")

     val addrAfter = MemoryUtil.memAddress(buffer)
     Log.info("addrBefore = 0x${addrBefore.toString(16)}")
     Log.info("addrAfter  = 0x${addrAfter.toString(16)}")
     Log.info("buffer.get(0) = ${buffer.get(0)}")
     Log.info("buffer.get(1) = ${buffer.get(1)}")

            val result = MemoryUtil.memUTF8(addrAfter)
            Log.info("test1 original = $original")
            Log.info("test1 result   = $result")
            Log.info("test1 match    = ${original == result}")
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    // 测试 2：memAlloc + memUTF8(addr, length) 带长度版本
    run {
        val original = "abc中文def"
        val bytes = original.toByteArray(StandardCharsets.UTF_8)

        val buffer = MemoryUtil.memAlloc(bytes.size)
        try {
            for (i in bytes.indices) {
                buffer.put(i, bytes[i])
            }

            val addr = MemoryUtil.memAddress(buffer)
            Log.info("test2 addr = 0x${addr.toString(16)}")

            val result = MemoryUtil.memUTF8(addr, bytes.size)
            Log.info("test2 original = $original")
            Log.info("test2 result   = $result")
            Log.info("test2 match    = ${original == result}")
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    // 测试 3：用 memUTF8(CharSequence) 分配，再用 memUTF8(long) 读
    run {
        val original = "roundtrip_test"
        val buffer = MemoryUtil.memUTF8(original)
        try {
            val addr = MemoryUtil.memAddress(buffer)
            Log.info("test3 addr = 0x${addr.toString(16)}")

            val result = MemoryUtil.memUTF8(addr)
            Log.info("test3 original = $original")
            Log.info("test3 result   = $result")
            Log.info("test3 match    = ${original == result}")
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    // 测试 4：memAlloc + memUTF8(ByteBuffer, length) 读回
    run {
        val original = "buffer_overload"
        val bytes = original.toByteArray(StandardCharsets.UTF_8)

        val buffer = MemoryUtil.memAlloc(bytes.size)
        try {
            for (i in bytes.indices) {
                buffer.put(i, bytes[i])
            }

            val result = MemoryUtil.memUTF8(buffer, bytes.size)
            Log.info("test4 original = $original")
            Log.info("test4 result   = $result")
            Log.info("test4 match    = ${original == result}")
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    // 测试 5：对比 MemoryStack 和 memAlloc 的地址
    run {
        MemoryStack.stackPush().use { stack ->
            val stackBuffer = stack.UTF8("stack_test")
            val stackAddr = MemoryUtil.memAddress(stackBuffer)
            Log.info("test5 stack addr = 0x${stackAddr.toString(16)}")
        }

        val allocBuffer = MemoryUtil.memAlloc(16)
        try {
            val allocAddr = MemoryUtil.memAddress(allocBuffer)
            Log.info("test5 memAlloc addr = 0x${allocAddr.toString(16)}")
        } finally {
            MemoryUtil.memFree(allocBuffer)
        }
    }*/

  }

  fun initGL() {
    Configuration.OPENGLES_EXPLICIT_INIT.set(true)
    GLES.create()
    GLES.createCapabilities()
    if (Core.gl30 != null) {
      lwjglGL30 = LwjglGL30()
      lwjglGL20 = lwjglGL30!!
    } else {
      lwjglGL20 = LwjglGL20()
    }
  }

  fun setLwjglGL() {
    Core.gl20 = lwjglGL20
    Core.gl30 = lwjglGL30
    Core.gl = lwjglGL30 ?: lwjglGL20
  }

  fun setOriginGL() {
    Core.gl20 = gl20
    Core.gl30 = gl30
    Core.gl = gl30 ?: gl20
  }
}
