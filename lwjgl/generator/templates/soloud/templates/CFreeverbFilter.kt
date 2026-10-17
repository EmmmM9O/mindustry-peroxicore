package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CFREEVERBFILTER =
  "CFreeverbFilter"
    .nativeClassSDL("FreeverbFilter", "FreeverbFilter_") {
      EnumConstant(
        "",
        "FREEVERBFILTER_WET".enum("", "0"),
        "FREEVERBFILTER_FREEZE".enum("", "1"),
        "FREEVERBFILTER_ROOMSIZE".enum("", "2"),
        "FREEVERBFILTER_DAMP".enum("", "3"),
        "FREEVERBFILTER_WIDTH".enum("", "4"),
      )

      void(
        "destroy",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
      )

      int(
        "getParamCount",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      FreeverbFilter(
        "create",
        "",

        void(),
      )

      int(
        "setParams",
        "",
        FreeverbFilter("aFreeverbFilter", ""),
        float("aMode", ""),
        float("aRoomSize", ""),
        float("aDamp", ""),
        float("aWidth", ""),
      )
    }
