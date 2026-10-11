package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CWAVESHAPERFILTER =
  "CWaveShaperFilter"
    .nativeClassSDL("WaveShaperFilter", "WaveShaperFilter_") {
      EnumConstant(
        "",
        "WAVESHAPERFILTER_WET".enum("", "0"),
        "WAVESHAPERFILTER_AMOUNT".enum("", "1"),
      )

      void(
        "destroy",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
      )

      int(
        "setParams",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
        float("aAmount", ""),
      )

      WaveShaperFilter(
        "create",
        "",
      )

      int(
        "getParamCount",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        WaveShaperFilter("aWaveShaperFilter", ""),
        unsigned_int("aParamIndex", ""),
      )
    }
