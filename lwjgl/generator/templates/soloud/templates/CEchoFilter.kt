package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CECHOFILTER =
  "CEchoFilter"
    .nativeClassSDL("EchoFilter", "EchoFilter_") {
      EnumConstant(
        "",
        "ECHOFILTER_WET".enum("", "0"),
        "ECHOFILTER_DELAY".enum("", "1"),
        "ECHOFILTER_DECAY".enum("", "2"),
        "ECHOFILTER_FILTER".enum("", "3"),
      )

      void(
        "destroy",
        "",
        EchoFilter("aEchoFilter", ""),
      )

      int(
        "getParamCount",
        "",
        EchoFilter("aEchoFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        EchoFilter("aEchoFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        EchoFilter("aEchoFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        EchoFilter("aEchoFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        EchoFilter("aEchoFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      EchoFilter(
        "create",
        "",
      )

      int(
        "setParams",
        "",
        EchoFilter("aEchoFilter", ""),
        float("aDelay", ""),
      )

      int(
        "setParamsEx",
        "",
        EchoFilter("aEchoFilter", ""),
        float("aDelay", ""),
        float("aDecay", ""),
        float("aFilter", ""),
      )
    }
