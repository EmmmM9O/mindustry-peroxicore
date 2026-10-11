package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CROBOTIZEFILTER =
  "CRobotizeFilter"
    .nativeClassSDL("RobotizeFilter", "RobotizeFilter_") {
      EnumConstant(
        "",
        "ROBOTIZEFILTER_WET".enum("", "0"),
        "ROBOTIZEFILTER_FREQ".enum("", "1"),
        "ROBOTIZEFILTER_WAVE".enum("", "2"),
      )

      void(
        "destroy",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
      )

      int(
        "getParamCount",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      void(
        "setParams",
        "",
        RobotizeFilter("aRobotizeFilter", ""),
        float("aFreq", ""),
        int("aWaveform", ""),
      )

      RobotizeFilter(
        "create",
        "",
      )
    }
