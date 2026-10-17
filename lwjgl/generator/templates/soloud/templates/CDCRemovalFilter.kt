package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CDCREMOVALFILTER =
  "CDCRemovalFilter"
    .nativeClassSDL("DCRemovalFilter", "DCRemovalFilter_") {
      void(
        "destroy",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
      )

      DCRemovalFilter(
        "create",
        "",

        void(),
      )

      int(
        "setParams",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
      )

      int(
        "setParamsEx",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
        float("aLength", ""),
      )

      int(
        "getParamCount",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        DCRemovalFilter("aDCRemovalFilter", ""),
        unsigned_int("aParamIndex", ""),
      )
    }
