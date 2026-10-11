package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CQUEUE =
  "CQueue"
    .nativeClassSDL("Queue", "Queue_") {
      void(
        "destroy",
        "",
        Queue("aQueue", ""),
      )

      Queue(
        "create",
        "",
      )

      int(
        "play",
        "",
        Queue("aQueue", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "getQueueCount",
        "",
        Queue("aQueue", ""),
      )

      int(
        "isCurrentlyPlaying",
        "",
        Queue("aQueue", ""),
        AudioSource("aSound", ""),
      )

      int(
        "setParamsFromAudioSource",
        "",
        Queue("aQueue", ""),
        AudioSource("aSound", ""),
      )

      int(
        "setParams",
        "",
        Queue("aQueue", ""),
        float("aSamplerate", ""),
      )

      int(
        "setParamsEx",
        "",
        Queue("aQueue", ""),
        float("aSamplerate", ""),
        unsigned_int("aChannels", ""),
      )

      void(
        "setVolume",
        "",
        Queue("aQueue", ""),
        float("aVolume", ""),
      )

      void(
        "setPriority",
        "",
        Queue("aQueue", ""),
        float("aPriority", ""),
      )

      void(
        "setConcurrentGroup",
        "",
        Queue("aQueue", ""),
        int("aConcurrentGroup", ""),
      )

      void(
        "setMaxConcurrent",
        "",
        Queue("aQueue", ""),
        int("aMaxConcurrent", ""),
      )

      void(
        "setMinConcurrentInterrupt",
        "",
        Queue("aQueue", ""),
        float("aMinConcurrentInterrupt", ""),
      )

      void(
        "setLooping",
        "",
        Queue("aQueue", ""),
        int("aLoop", ""),
      )

      void(
        "setAutoStop",
        "",
        Queue("aQueue", ""),
        int("aAutoStop", ""),
      )

      void(
        "set3dMinMaxDistance",
        "",
        Queue("aQueue", ""),
        float("aMinDistance", ""),
        float("aMaxDistance", ""),
      )

      void(
        "set3dAttenuation",
        "",
        Queue("aQueue", ""),
        unsigned_int("aAttenuationModel", ""),
        float("aAttenuationRolloffFactor", ""),
      )

      void(
        "set3dDopplerFactor",
        "",
        Queue("aQueue", ""),
        float("aDopplerFactor", ""),
      )

      void(
        "set3dListenerRelative",
        "",
        Queue("aQueue", ""),
        int("aListenerRelative", ""),
      )

      void(
        "set3dDistanceDelay",
        "",
        Queue("aQueue", ""),
        int("aDistanceDelay", ""),
      )

      void(
        "set3dCollider",
        "",
        Queue("aQueue", ""),
        AudioCollider("aCollider", ""),
      )

      void(
        "set3dColliderEx",
        "",
        Queue("aQueue", ""),
        AudioCollider("aCollider", ""),
        int("aUserData", ""),
      )

      void(
        "set3dAttenuator",
        "",
        Queue("aQueue", ""),
        AudioAttenuator("aAttenuator", ""),
      )

      void(
        "setInaudibleBehavior",
        "",
        Queue("aQueue", ""),
        int("aMustTick", ""),
        int("aKill", ""),
      )

      void(
        "setLoopPoint",
        "",
        Queue("aQueue", ""),
        double("aLoopPoint", ""),
      )

      double(
        "getLoopPoint",
        "",
        Queue("aQueue", ""),
      )

      void(
        "setFilter",
        "",
        Queue("aQueue", ""),
        unsigned_int("aFilterId", ""),
        Filter("aFilter", ""),
      )

      void(
        "stop",
        "",
        Queue("aQueue", ""),
      )
    }
