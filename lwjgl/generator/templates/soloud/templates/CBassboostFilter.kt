package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CBASSBOOSTFILTER =
  "CBassboostFilter"
    .nativeClassSDL("BassboostFilter", "BassboostFilter_") {
      EnumConstant(
        "",
        "BASSBOOSTFILTER_WET".enum("", "0"),
        "BASSBOOSTFILTER_BOOST".enum("", "1"),
      )

      void(
        "destroy",
        "",
        BassboostFilter("aBassboostFilter", ""),
      )

      int(
        "getParamCount",
        "",
        BassboostFilter("aBassboostFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        BassboostFilter("aBassboostFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        BassboostFilter("aBassboostFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        BassboostFilter("aBassboostFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        BassboostFilter("aBassboostFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      int(
        "setParams",
        "",
        BassboostFilter("aBassboostFilter", ""),
        float("aBoost", ""),
      )

      BassboostFilter(
        "create",
        "",

        void(),
      )
    }
