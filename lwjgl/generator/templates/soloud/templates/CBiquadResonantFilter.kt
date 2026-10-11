package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CBIQUADRESONANTFILTER =
  "CBiquadResonantFilter"
    .nativeClassSDL("BiquadResonantFilter", "BiquadResonantFilter_") {
      EnumConstant(
        "",
        "BIQUADRESONANTFILTER_LOWPASS".enum("", "0"),
        "BIQUADRESONANTFILTER_HIGHPASS".enum("", "1"),
        "BIQUADRESONANTFILTER_BANDPASS".enum("", "2"),
        "BIQUADRESONANTFILTER_WET".enum("", "0"),
        "BIQUADRESONANTFILTER_TYPE".enum("", "1"),
        "BIQUADRESONANTFILTER_FREQUENCY".enum("", "2"),
        "BIQUADRESONANTFILTER_RESONANCE".enum("", "3"),
      )

      void(
        "destroy",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
      )

      int(
        "getParamCount",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      BiquadResonantFilter(
        "create",
        "",
      )

      int(
        "setParams",
        "",
        BiquadResonantFilter("aBiquadResonantFilter", ""),
        int("aType", ""),
        float("aFrequency", ""),
        float("aResonance", ""),
      )
    }
