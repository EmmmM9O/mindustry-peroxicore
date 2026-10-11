package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CBUS =
  "CBus"
    .nativeClassSDL("Bus", "Bus_") {
      void(
        "destroy",
        "",
        Bus("aBus", ""),
      )

      Bus(
        "create",
        "",
      )

      void(
        "setFilter",
        "",
        Bus("aBus", ""),
        unsigned_int("aFilterId", ""),
        Filter("aFilter", ""),
      )

      unsigned_int(
        "play",
        "",
        Bus("aBus", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "playEx",
        "",
        Bus("aBus", ""),
        AudioSource("aSound", ""),
        float("aVolume", ""),
        float("aPan", ""),
        float("aPitch", ""),
        int("aPaused", ""),
        int("aLoop", ""),
      )

      unsigned_int(
        "playClocked",
        "",
        Bus("aBus", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "playClockedEx",
        "",
        Bus("aBus", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aVolume", ""),
        float("aPan", ""),
      )

      unsigned_int(
        "play3d",
        "",
        Bus("aBus", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      unsigned_int(
        "play3dEx",
        "",
        Bus("aBus", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aVelX", ""),
        float("aVelY", ""),
        float("aVelZ", ""),
        float("aVolume", ""),
        int("aPaused", ""),
      )

      unsigned_int(
        "play3dClocked",
        "",
        Bus("aBus", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      unsigned_int(
        "play3dClockedEx",
        "",
        Bus("aBus", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aVelX", ""),
        float("aVelY", ""),
        float("aVelZ", ""),
        float("aVolume", ""),
      )

      int(
        "setChannels",
        "",
        Bus("aBus", ""),
        unsigned_int("aChannels", ""),
      )

      void(
        "setVisualizationEnable",
        "",
        Bus("aBus", ""),
        int("aEnable", ""),
      )

      void(
        "annexSound",
        "",
        Bus("aBus", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getApproximateVolume",
        "",
        Bus("aBus", ""),
        unsigned_int("aChannel", ""),
      )

      unsigned_int(
        "getActiveVoiceCount",
        "",
        Bus("aBus", ""),
      )

      unsigned_int(
        "getResampler",
        "",
        Bus("aBus", ""),
      )

      void(
        "setResampler",
        "",
        Bus("aBus", ""),
        unsigned_int("aResampler", ""),
      )

      void(
        "setVolume",
        "",
        Bus("aBus", ""),
        float("aVolume", ""),
      )

      void(
        "setPriority",
        "",
        Bus("aBus", ""),
        float("aPriority", ""),
      )

      void(
        "setConcurrentGroup",
        "",
        Bus("aBus", ""),
        int("aConcurrentGroup", ""),
      )

      void(
        "setMaxConcurrent",
        "",
        Bus("aBus", ""),
        int("aMaxConcurrent", ""),
      )

      void(
        "setMinConcurrentInterrupt",
        "",
        Bus("aBus", ""),
        float("aMinConcurrentInterrupt", ""),
      )

      void(
        "setLooping",
        "",
        Bus("aBus", ""),
        int("aLoop", ""),
      )

      void(
        "setAutoStop",
        "",
        Bus("aBus", ""),
        int("aAutoStop", ""),
      )

      void(
        "set3dMinMaxDistance",
        "",
        Bus("aBus", ""),
        float("aMinDistance", ""),
        float("aMaxDistance", ""),
      )

      void(
        "set3dAttenuation",
        "",
        Bus("aBus", ""),
        unsigned_int("aAttenuationModel", ""),
        float("aAttenuationRolloffFactor", ""),
      )

      void(
        "set3dDopplerFactor",
        "",
        Bus("aBus", ""),
        float("aDopplerFactor", ""),
      )

      void(
        "set3dListenerRelative",
        "",
        Bus("aBus", ""),
        int("aListenerRelative", ""),
      )

      void(
        "set3dDistanceDelay",
        "",
        Bus("aBus", ""),
        int("aDistanceDelay", ""),
      )

      void(
        "set3dCollider",
        "",
        Bus("aBus", ""),
        AudioCollider("aCollider", ""),
      )

      void(
        "set3dColliderEx",
        "",
        Bus("aBus", ""),
        AudioCollider("aCollider", ""),
        int("aUserData", ""),
      )

      void(
        "set3dAttenuator",
        "",
        Bus("aBus", ""),
        AudioAttenuator("aAttenuator", ""),
      )

      void(
        "setInaudibleBehavior",
        "",
        Bus("aBus", ""),
        int("aMustTick", ""),
        int("aKill", ""),
      )

      void(
        "setLoopPoint",
        "",
        Bus("aBus", ""),
        double("aLoopPoint", ""),
      )

      double(
        "getLoopPoint",
        "",
        Bus("aBus", ""),
      )

      void(
        "stop",
        "",
        Bus("aBus", ""),
      )

      // soloud_c_arc.h

      void(
        "getWaveA",
        "",
        Bus("aBus", ""),
        Check(256)..float.p("aWaves", ""),
      )

      void(
        "calcFFTA",
        "",
        Bus("aBus", ""),
        Check(256)..float.p("aFFT", ""),
      )
    }
