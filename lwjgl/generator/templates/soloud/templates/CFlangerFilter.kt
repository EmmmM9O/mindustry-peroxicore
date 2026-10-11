package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CFLANGERFILTER =
  "CFlangerFilter"
    .nativeClassSDL("FlangerFilter", "FlangerFilter_") {
      EnumConstant(
        "",
        "FLANGERFILTER_WET".enum("", "0"),
        "FLANGERFILTER_DELAY".enum("", "1"),
        "FLANGERFILTER_FREQ".enum("", "2"),
      )

      void(
        "destroy",
        "",
        FlangerFilter("aFlangerFilter", ""),
      )

      int(
        "getParamCount",
        "",
        FlangerFilter("aFlangerFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        FlangerFilter("aFlangerFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        FlangerFilter("aFlangerFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        FlangerFilter("aFlangerFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        FlangerFilter("aFlangerFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      FlangerFilter(
        "create",
        "",
      )

      int(
        "setParams",
        "",
        FlangerFilter("aFlangerFilter", ""),
        float("aDelay", ""),
        float("aFreq", ""),
      )
    }
