package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CWAVSTREAM =
  "CWavStream"
    .nativeClassSDL("WavStream", "WavStream_") {
      void(
        "destroy",
        "",
        WavStream("aWavStream", ""),
      )

      WavStream(
        "create",
        "",
      )

      int(
        "load",
        "",
        WavStream("aWavStream", ""),
        charUTF8.const.p("aFilename", ""),
      )

      int(
        "loadMem",
        "",
        WavStream("aWavStream", ""),
        unsigned_char.const.p("aData", ""),
        AutoSize("aData")..unsigned_int("aDataLen", ""),
      )

      int(
        "loadMemEx",
        "",
        WavStream("aWavStream", ""),
        unsigned_char.const.p("aData", ""),
        AutoSize("aData")..unsigned_int("aDataLen", ""),
        int("aCopy", ""),
        int("aTakeOwnership", ""),
      )

      int(
        "loadToMem",
        "",
        WavStream("aWavStream", ""),
        charUTF8.const.p("aFilename", ""),
      )

      int(
        "loadFile",
        "",
        WavStream("aWavStream", ""),
        File("aFile", ""),
      )

      int(
        "loadFileToMem",
        "",
        WavStream("aWavStream", ""),
        File("aFile", ""),
      )

      double(
        "getLength",
        "",
        WavStream("aWavStream", ""),
      )

      void(
        "setVolume",
        "",
        WavStream("aWavStream", ""),
        float("aVolume", ""),
      )

      void(
        "setPriority",
        "",
        WavStream("aWavStream", ""),
        float("aPriority", ""),
      )

      void(
        "setConcurrentGroup",
        "",
        WavStream("aWavStream", ""),
        int("aConcurrentGroup", ""),
      )

      void(
        "setMaxConcurrent",
        "",
        WavStream("aWavStream", ""),
        int("aMaxConcurrent", ""),
      )

      void(
        "setMinConcurrentInterrupt",
        "",
        WavStream("aWavStream", ""),
        float("aMinConcurrentInterrupt", ""),
      )

      void(
        "setLooping",
        "",
        WavStream("aWavStream", ""),
        int("aLoop", ""),
      )

      void(
        "setAutoStop",
        "",
        WavStream("aWavStream", ""),
        int("aAutoStop", ""),
      )

      void(
        "set3dMinMaxDistance",
        "",
        WavStream("aWavStream", ""),
        float("aMinDistance", ""),
        float("aMaxDistance", ""),
      )

      void(
        "set3dAttenuation",
        "",
        WavStream("aWavStream", ""),
        unsigned_int("aAttenuationModel", ""),
        float("aAttenuationRolloffFactor", ""),
      )

      void(
        "set3dDopplerFactor",
        "",
        WavStream("aWavStream", ""),
        float("aDopplerFactor", ""),
      )

      void(
        "set3dListenerRelative",
        "",
        WavStream("aWavStream", ""),
        int("aListenerRelative", ""),
      )

      void(
        "set3dDistanceDelay",
        "",
        WavStream("aWavStream", ""),
        int("aDistanceDelay", ""),
      )

      void(
        "set3dCollider",
        "",
        WavStream("aWavStream", ""),
        AudioCollider("aCollider", ""),
      )

      void(
        "set3dColliderEx",
        "",
        WavStream("aWavStream", ""),
        AudioCollider("aCollider", ""),
        int("aUserData", ""),
      )

      void(
        "set3dAttenuator",
        "",
        WavStream("aWavStream", ""),
        AudioAttenuator("aAttenuator", ""),
      )

      void(
        "setInaudibleBehavior",
        "",
        WavStream("aWavStream", ""),
        int("aMustTick", ""),
        int("aKill", ""),
      )

      void(
        "setLoopPoint",
        "",
        WavStream("aWavStream", ""),
        double("aLoopPoint", ""),
      )

      double(
        "getLoopPoint",
        "",
        WavStream("aWavStream", ""),
      )

      void(
        "setFilter",
        "",
        WavStream("aWavStream", ""),
        unsigned_int("aFilterId", ""),
        Filter("aFilter", ""),
      )

      void(
        "stop",
        "",
        WavStream("aWavStream", ""),
      )
    }
