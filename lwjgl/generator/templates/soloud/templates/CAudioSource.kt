package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CAUDIOSOURCE =
  "CAudioSource"
    .nativeClassSDL("AudioSource", "AudioSource_") {
      // soloud_c_arc.h

      void(
        "setPriority",
        "",
        AudioSource("aSound", ""),
        float("priority", ""),
      )

      void(
        "setMinConcurrentInterrupt",
        "",
        AudioSource("aSound", ""),
        float("value", ""),
      )

      void(
        "setMaxConcurrent",
        "",
        AudioSource("aSound", ""),
        int("maxConcurrent", ""),
      )

      void(
        "setConcurrentGroup",
        "",
        AudioSource("aSound", ""),
        int("group", ""),
      )

      void(
        "setLooping",
        "",
        AudioSource("aSound", ""),
        bool("loop", ""),
      )

      void(
        "setSingleInstance",
        "",
        AudioSource("aSound", ""),
        bool("single", ""),
      )

      void(
        "stop",
        "",
        AudioSource("aSound", ""),
      )

      void(
        "setFilter",
        "",
        AudioSource("aSound", ""),
        int("index", ""),
        Filter("filter", ""),
      )
    }
