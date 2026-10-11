package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CWAV =
  "CWav"
    .nativeClassSDL("Wav", "Wav_") {
      void(
        "destroy",
        "",
        Wav("aWav", ""),
      )

      Wav(
        "create",
        "",
        void(),
      )

      int(
        "load",
        "",
        Wav("aWav", ""),
        charUTF8.const.p("aFilename", ""),
      )

      int(
        "loadMem",
        "",
        Wav("aWav", ""),
        unsigned_char.const.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
      )

      int(
        "loadMemEx",
        "",
        Wav("aWav", ""),
        unsigned_char.const.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
        int("aCopy", ""),
        int("aTakeOwnership", ""),
      )

      int(
        "loadFile",
        "",
        Wav("aWav", ""),
        File("aFile", ""),
      )

      int(
        "loadRawWave8",
        "",
        Wav("aWav", ""),
        unsigned_char.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
      )

      int(
        "loadRawWave8Ex",
        "",
        Wav("aWav", ""),
        unsigned_char.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
        float("aSamplerate", ""),
        unsigned_int("aChannels", ""),
      )

      int(
        "loadRawWave16",
        "",
        Wav("aWav", ""),
        short.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
      )

      int(
        "loadRawWave16Ex",
        "",
        Wav("aWav", ""),
        short.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
        float("aSamplerate", ""),
        unsigned_int("aChannels", ""),
      )

      int(
        "loadRawWave",
        "",
        Wav("aWav", ""),
        float.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
      )

      int(
        "loadRawWaveEx",
        "",
        Wav("aWav", ""),
        float.p("aMem", ""),
        AutoSize("aMem")..unsigned_int("aLength", ""),
        float("aSamplerate", ""),
        unsigned_int("aChannels", ""),
        int("aCopy", ""),
        int("aTakeOwnership", ""),
      )

      double(
        "getLength",
        "",
        Wav("aWav", ""),
      )

      void(
        "setVolume",
        "",
        Wav("aWav", ""),
        float("aVolume", ""),
      )

      void(
        "setPriority",
        "",
        Wav("aWav", ""),
        float("aPriority", ""),
      )

      void(
        "setConcurrentGroup",
        "",
        Wav("aWav", ""),
        int("aConcurrentGroup", ""),
      )

      void(
        "setMaxConcurrent",
        "",
        Wav("aWav", ""),
        int("aMaxConcurrent", ""),
      )

      void(
        "setMinConcurrentInterrupt",
        "",
        Wav("aWav", ""),
        float("aMinConcurrentInterrupt", ""),
      )

      void(
        "setLooping",
        "",
        Wav("aWav", ""),
        int("aLoop", ""),
      )

      void(
        "setAutoStop",
        "",
        Wav("aWav", ""),
        int("aAutoStop", ""),
      )

      void(
        "set3dMinMaxDistance",
        "",
        Wav("aWav", ""),
        float("aMinDistance", ""),
        float("aMaxDistance", ""),
      )

      void(
        "set3dAttenuation",
        "",
        Wav("aWav", ""),
        unsigned_int("aAttenuationModel", ""),
        float("aAttenuationRolloffFactor", ""),
      )

      void(
        "set3dDopplerFactor",
        "",
        Wav("aWav", ""),
        float("aDopplerFactor", ""),
      )

      void(
        "set3dListenerRelative",
        "",
        Wav("aWav", ""),
        int("aListenerRelative", ""),
      )

      void(
        "set3dDistanceDelay",
        "",
        Wav("aWav", ""),
        int("aDistanceDelay", ""),
      )

      void(
        "set3dCollider",
        "",
        Wav("aWav", ""),
        AudioCollider("aCollider", ""),
      )

      void(
        "set3dColliderEx",
        "",
        Wav("aWav", ""),
        AudioCollider("aCollider", ""),
        int("aUserData", ""),
      )

      void(
        "set3dAttenuator",
        "",
        Wav("aWav", ""),
        AudioAttenuator("aAttenuator", ""),
      )

      void(
        "setInaudibleBehavior",
        "",
        Wav("aWav", ""),
        int("aMustTick", ""),
        int("aKill", ""),
      )

      void(
        "setLoopPoint",
        "",
        Wav("aWav", ""),
        double("aLoopPoint", ""),
      )

      double(
        "getLoopPoint",
        "",
        Wav("aWav", ""),
      )

      void(
        "setFilter",
        "",
        Wav("aWav", ""),
        unsigned_int("aFilterId", ""),
        Filter("aFilter", ""),
      )

      void(
        "stop",
        "",
        Wav("aWav", ""),
      )
    }
