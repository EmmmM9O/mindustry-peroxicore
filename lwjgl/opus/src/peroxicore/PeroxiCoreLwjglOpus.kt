package peroxicore

import arc.util.*
import java.io.*
import java.nio.*
import mindustry.*
import mindustry.mod.*
import org.lwjgl.system.*
import org.lwjgl.util.opus.*
import peroxicore.annotations.*
import peroxicore.audio.*

@ModConfig(
  name = "peroxicore-lwjgl-opus",
  displayName = "PeroxiCore Lwjgl Opus",
  dependencies = ["peroxicore", "peroxicore-lwjgl"],
)
@ImportPeroxiCore
class PeroxiCoreLwjglOpus : Mod() {
  override fun init() {
    val file = Vars.modDirectory.child("test.ogg")
    Time.run(1f) {
      play(file.absolutePath())
    }
  }

  var engine = 0L

  private fun check(result: Int) {
    if (result != 0) {
      throw ArcRuntimeException(CSoloud.Soloud_getErrorString(engine, result))
    }
  }

  fun play(path: String) {
    try {
      val wavBytes = decodeToWavBytes(path)
      if (wavBytes != null) {
        engine = CSoloud.Soloud_create()
        Log.info("Engine，handle = $engine")
        check(CSoloud.Soloud_init(engine))

        val wav = CWav.Wav_create()

        val buffer = MemoryUtil.memAlloc(wavBytes.size)
        buffer.put(wavBytes)
        buffer.flip()
        check(CWav.Wav_loadMemEx(wav, buffer, 1, 1))
        MemoryUtil.memFree(buffer)
        val handle = CSoloud.Soloud_play(engine, wav)
        Log.info("播放中，handle = $handle")
      } else {
        Log.info("解码失败，无法播放")
      }
    } catch (e: Throwable) {
      Log.err(e)
    }
  }

  private fun decodeToWavBytes(filePath: String): ByteArray? {
    MemoryStack.stackPush().use { stack ->
      val err = stack.mallocInt(1)

      val opusFile = OpusFile.op_open_file(filePath, err)
      if (opusFile == MemoryUtil.NULL) {
        val code = err.get(0)
        Log.info("op_open_file 失败，错误码: $code")
        return null
      }

      val head: OpusHead? = OpusFile.op_head(opusFile, -1)
      if (head == null) {
        OpusFile.op_free(opusFile)
        Log.info("op_head 返回 null")
        return null
      }

      val channels = head.channel_count().toInt()
      val sampleRate = 48000
      Log.info("声道数: $channels, 输出采样率: $sampleRate")

      val pcmShortBuffer = MemoryUtil.memAllocShort(5760 * channels)
      val pcmOutputStream = ByteArrayOutputStream()
      var totalSamplesPerChannel = 0

      try {
        while (true) {
          val samplesPerChannel = OpusFile.op_read(opusFile, pcmShortBuffer, null)
          if (samplesPerChannel < 0) {
            Log.info("op_read 返回错误: $samplesPerChannel")
            break
          }
          if (samplesPerChannel == 0) {
            break // EOF
          }

          val totalShorts = samplesPerChannel * channels
          pcmShortBuffer.rewind()
          for (i in 0 until totalShorts) {
            val sample = pcmShortBuffer.get(i)
            pcmOutputStream.write(sample.toInt() and 0xFF)
            pcmOutputStream.write((sample.toInt() shr 8) and 0xFF)
          }
          totalSamplesPerChannel += samplesPerChannel
        }
      } finally {
        MemoryUtil.memFree(pcmShortBuffer)
        OpusFile.op_free(opusFile)
      }

      if (pcmOutputStream.size() == 0) {
        Log.info("没有解码出任何 PCM 数据")
        return null
      }

      val pcmBytes = pcmOutputStream.toByteArray()
      return buildWavBytes(pcmBytes, sampleRate, channels)
    }
  }

  private fun buildWavBytes(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcmData.size
    val fileSize = dataSize + 36

    val output = ByteArrayOutputStream(dataSize + 44)

    // 用 ByteBuffer 明确指定小端序，比 DataOutputStream 更可靠
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

    // RIFF chunk
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(fileSize)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))

    // fmt subchunk
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // Subchunk1Size
    header.putShort(1) // AudioFormat = PCM
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign.toShort())
    header.putShort(bitsPerSample.toShort())

    // data subchunk
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataSize)

    output.write(header.array())
    output.write(pcmData)
    return output.toByteArray()
  }
}
