package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CFFTFILTER =
  "CFFTFilter"
    .nativeClassSDL("FFTFilter", "FFTFilter_") {
      void(
        "destroy",
        "",
        FFTFilter("aFFTFilter", ""),
      )

      FFTFilter(
        "create",
        "",
      )

      int(
        "getParamCount",
        "",
        FFTFilter("aFFTFilter", ""),
      )

      charUTF8.const.p(
        "getParamName",
        "",
        FFTFilter("aFFTFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      unsigned_int(
        "getParamType",
        "",
        FFTFilter("aFFTFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMax",
        "",
        FFTFilter("aFFTFilter", ""),
        unsigned_int("aParamIndex", ""),
      )

      float(
        "getParamMin",
        "",
        FFTFilter("aFFTFilter", ""),
        unsigned_int("aParamIndex", ""),
      )
    }
