package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CLOFIFILTER =
  "CLofiFilter"
    .nativeClassSDL("LofiFilter", "LofiFilter_") {
      EnumConstant(
        "",
        "LOFIFILTER_WET".enum("", "0"),
        "LOFIFILTER_SAMPLERATE".enum("", "1"),
        "LOFIFILTER_BITDEPTH".enum("", "2"),
      )

      void(
        "destroy",
        "",
        LofiFilter("aLofiFilter", ""),
      )

      int(
        "getParamCount",
        "",
        LofiFilter("aLofiFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        LofiFilter("aLofiFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        LofiFilter("aLofiFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        LofiFilter("aLofiFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        LofiFilter("aLofiFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      LofiFilter(
        "create",
        "",

        void(),
      )

      int(
        "setParams",
        "",
        LofiFilter("aLofiFilter", ""),
        float("aSampleRate", ""),
        float("aBitdepth", ""),
      )
    }
