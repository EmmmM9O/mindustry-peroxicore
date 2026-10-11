package soloud.templates

import org.lwjgl.generator.*
import soloud.*

val CSOLOUD =
  "CSoloud"
    .nativeClassSDL("Soloud", "Soloud_") {
      EnumConstant(
        "",
        "SOLOUD_AUTO".enum("", "0"),
        "SOLOUD_SDL1".enum("", "1"),
        "SOLOUD_SDL2".enum("", "2"),
        "SOLOUD_PORTAUDIO".enum("", "3"),
        "SOLOUD_WINMM".enum("", "4"),
        "SOLOUD_XAUDIO2".enum("", "5"),
        "SOLOUD_WASAPI".enum("", "6"),
        "SOLOUD_ALSA".enum("", "7"),
        "SOLOUD_JACK".enum("", "8"),
        "SOLOUD_OSS".enum("", "9"),
        "SOLOUD_OPENAL".enum("", "10"),
        "SOLOUD_COREAUDIO".enum("", "11"),
        "SOLOUD_OPENSLES".enum("", "12"),
        "SOLOUD_MINIAUDIO".enum("", "13"),
        "SOLOUD_NOSOUND".enum("", "14"),
        "SOLOUD_NULLDRIVER".enum("", "15"),
        "SOLOUD_BACKEND_MAX".enum("", "16"),
        "SOLOUD_CLIP_ROUNDOFF".enum("", "1"),
        "SOLOUD_ENABLE_VISUALIZATION".enum("", "2"),
        "SOLOUD_LEFT_HANDED_3D".enum("", "4"),
        "SOLOUD_NO_FPU_REGISTER_CHANGE".enum("", "8"),
        "SOLOUD_WAVE_SQUARE".enum("", "0"),
        "SOLOUD_WAVE_SAW".enum("", "1"),
        "SOLOUD_WAVE_SIN".enum("", "2"),
        "SOLOUD_WAVE_TRIANGLE".enum("", "3"),
        "SOLOUD_WAVE_BOUNCE".enum("", "4"),
        "SOLOUD_WAVE_JAWS".enum("", "5"),
        "SOLOUD_WAVE_HUMPS".enum("", "6"),
        "SOLOUD_WAVE_FSQUARE".enum("", "7"),
        "SOLOUD_WAVE_FSAW".enum("", "8"),
        "SOLOUD_RESAMPLER_POINT".enum("", "0"),
        "SOLOUD_RESAMPLER_LINEAR".enum("", "1"),
        "SOLOUD_RESAMPLER_CATMULLROM".enum("", "2"),
      )

      void(
        "destroy",
        "",
        Soloud("aSoloud", ""),
      )

      Soloud(
        "create",
        "",
        void(),
      )

      int(
        "init",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "initEx",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aFlags", ""),
        unsigned_int("aBackend", ""),
        unsigned_int("aSamplerate", ""),
        unsigned_int("aBufferSize", ""),
        unsigned_int("aChannels", ""),
      )

      int(
        "pause",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "resume",
        "",
        Soloud("aSoloud", ""),
      )

      void(
        "deinit",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getVersion",
        "",
        Soloud("aSoloud", ""),
      )

      charUTF8.const.p(
        "getErrorString",
        "",
        Soloud("aSoloud", ""),
        int("aErrorCode", ""),
      )

      unsigned_int(
        "getBackendId",
        "",
        Soloud("aSoloud", ""),
      )

      charUTF8.const.p(
        "getBackendString",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getBackendChannels",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getBackendSamplerate",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getBackendBufferSize",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "setSpeakerPosition",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aChannel", ""),
        float("aX", ""),
        float("aY", ""),
        float("aZ", ""),
      )

      int(
        "getSpeakerPosition",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aChannel", ""),
        Check(1)..float.p("aX", ""),
        Check(1)..float.p("aY", ""),
        Check(1)..float.p("aZ", ""),
      )

      unsigned_int(
        "play",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "playEx",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
        float("aVolume", ""),
        float("aPan", ""),
        float("aPitch", ""),
        int("aPaused", ""),
        int("aLoop", ""),
        unsigned_int("aBus", ""),
      )

      unsigned_int(
        "playClocked",
        "",
        Soloud("aSoloud", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "playClockedEx",
        "",
        Soloud("aSoloud", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aVolume", ""),
        float("aPan", ""),
        float("aPitch", ""),
        int("aLoop", ""),
        unsigned_int("aBus", ""),
      )

      unsigned_int(
        "play3d",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      unsigned_int(
        "play3dEx",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aVelX", ""),
        float("aVelY", ""),
        float("aVelZ", ""),
        float("aVolume", ""),
        int("aPaused", ""),
        unsigned_int("aBus", ""),
      )

      unsigned_int(
        "play3dClocked",
        "",
        Soloud("aSoloud", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      unsigned_int(
        "play3dClockedEx",
        "",
        Soloud("aSoloud", ""),
        double("aSoundTime", ""),
        AudioSource("aSound", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aVelX", ""),
        float("aVelY", ""),
        float("aVelZ", ""),
        float("aVolume", ""),
        unsigned_int("aBus", ""),
      )

      unsigned_int(
        "playBackground",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
      )

      unsigned_int(
        "playBackgroundEx",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
        float("aVolume", ""),
        float("aPitch", ""),
        int("aPaused", ""),
        int("aLoop", ""),
        unsigned_int("aBus", ""),
      )

      int(
        "seek",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        double("aSeconds", ""),
      )

      void(
        "stop",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      void(
        "stopAll",
        "",
        Soloud("aSoloud", ""),
      )

      void(
        "stopAudioSource",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
      )

      int(
        "countAudioSource",
        "",
        Soloud("aSoloud", ""),
        AudioSource("aSound", ""),
      )

      void(
        "setFilterParameter",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aFilterId", ""),
        unsigned_int("aAttributeId", ""),
        float("aValue", ""),
      )

      float(
        "getFilterParameter",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aFilterId", ""),
        unsigned_int("aAttributeId", ""),
      )

      void(
        "fadeFilterParameter",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aFilterId", ""),
        unsigned_int("aAttributeId", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "oscillateFilterParameter",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aFilterId", ""),
        unsigned_int("aAttributeId", ""),
        float("aFrom", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      double(
        "getStreamTime",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      double(
        "getStreamPosition",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      int(
        "getPause",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getOverallVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getPan",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getSamplerate",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      int(
        "getProtectVoice",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      unsigned_int(
        "getActiveVoiceCount",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getVoiceCount",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "isValidVoiceHandle",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getRelativePlaySpeed",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getPostClipScaler",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getMainResampler",
        "",
        Soloud("aSoloud", ""),
      )

      float(
        "getGlobalVolume",
        "",
        Soloud("aSoloud", ""),
      )

      unsigned_int(
        "getMaxActiveVoiceCount",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "getLooping",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      int(
        "getAutoStop",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      double(
        "getLoopPoint",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      void(
        "setLoopPoint",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        double("aLoopPoint", ""),
      )

      void(
        "setLooping",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        int("aLooping", ""),
      )

      void(
        "setAutoStop",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        int("aAutoStop", ""),
      )

      int(
        "setMaxActiveVoiceCount",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceCount", ""),
      )

      void(
        "setInaudibleBehavior",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        int("aMustTick", ""),
        int("aKill", ""),
      )

      void(
        "setGlobalVolume",
        "",
        Soloud("aSoloud", ""),
        float("aVolume", ""),
      )

      void(
        "setPostClipScaler",
        "",
        Soloud("aSoloud", ""),
        float("aScaler", ""),
      )

      void(
        "setMainResampler",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aResampler", ""),
      )

      void(
        "setPause",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        int("aPause", ""),
      )

      void(
        "setPauseAll",
        "",
        Soloud("aSoloud", ""),
        int("aPause", ""),
      )

      int(
        "setRelativePlaySpeed",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aSpeed", ""),
      )

      void(
        "setProtectVoice",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        int("aProtect", ""),
      )

      void(
        "setSamplerate",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aSamplerate", ""),
      )

      void(
        "setPan",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aPan", ""),
      )

      void(
        "setPanAbsolute",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aLVolume", ""),
        float("aRVolume", ""),
      )

      void(
        "setChannelVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aChannel", ""),
        float("aVolume", ""),
      )

      void(
        "setVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aVolume", ""),
      )

      void(
        "setDelaySamples",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aSamples", ""),
      )

      void(
        "fadeVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "fadePan",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "fadeRelativePlaySpeed",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "fadeGlobalVolume",
        "",
        Soloud("aSoloud", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "schedulePause",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        double("aTime", ""),
      )

      void(
        "scheduleStop",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        double("aTime", ""),
      )

      void(
        "oscillateVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aFrom", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "oscillatePan",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aFrom", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "oscillateRelativePlaySpeed",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aFrom", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "oscillateGlobalVolume",
        "",
        Soloud("aSoloud", ""),
        float("aFrom", ""),
        float("aTo", ""),
        double("aTime", ""),
      )

      void(
        "setGlobalFilter",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aFilterId", ""),
        Filter("aFilter", ""),
      )

      void(
        "setVisualizationEnable",
        "",
        Soloud("aSoloud", ""),
        int("aEnable", ""),
      )

      float(
        "getApproximateVolume",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aChannel", ""),
      )

      unsigned_int(
        "getLoopCount",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      float(
        "getInfo",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aInfoKey", ""),
      )

      unsigned_int(
        "createVoiceGroup",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "destroyVoiceGroup",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceGroupHandle", ""),
      )

      int(
        "addVoiceToGroup",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceGroupHandle", ""),
        unsigned_int("aVoiceHandle", ""),
      )

      int(
        "isVoiceGroup",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceGroupHandle", ""),
      )

      int(
        "isVoiceGroupEmpty",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceGroupHandle", ""),
      )

      void(
        "update3dAudio",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "set3dSoundSpeed",
        "",
        Soloud("aSoloud", ""),
        float("aSpeed", ""),
      )

      float(
        "get3dSoundSpeed",
        "",
        Soloud("aSoloud", ""),
      )

      void(
        "set3dListenerParameters",
        "",
        Soloud("aSoloud", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aAtX", ""),
        float("aAtY", ""),
        float("aAtZ", ""),
        float("aUpX", ""),
        float("aUpY", ""),
        float("aUpZ", ""),
      )

      void(
        "set3dListenerParametersEx",
        "",
        Soloud("aSoloud", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aAtX", ""),
        float("aAtY", ""),
        float("aAtZ", ""),
        float("aUpX", ""),
        float("aUpY", ""),
        float("aUpZ", ""),
        float("aVelocityX", ""),
        float("aVelocityY", ""),
        float("aVelocityZ", ""),
      )

      void(
        "set3dListenerPosition",
        "",
        Soloud("aSoloud", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      void(
        "set3dListenerAt",
        "",
        Soloud("aSoloud", ""),
        float("aAtX", ""),
        float("aAtY", ""),
        float("aAtZ", ""),
      )

      void(
        "set3dListenerUp",
        "",
        Soloud("aSoloud", ""),
        float("aUpX", ""),
        float("aUpY", ""),
        float("aUpZ", ""),
      )

      void(
        "set3dListenerVelocity",
        "",
        Soloud("aSoloud", ""),
        float("aVelocityX", ""),
        float("aVelocityY", ""),
        float("aVelocityZ", ""),
      )

      void(
        "set3dSourceParameters",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      void(
        "set3dSourceParametersEx",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
        float("aVelocityX", ""),
        float("aVelocityY", ""),
        float("aVelocityZ", ""),
      )

      void(
        "set3dSourcePosition",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aPosX", ""),
        float("aPosY", ""),
        float("aPosZ", ""),
      )

      void(
        "set3dSourceVelocity",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aVelocityX", ""),
        float("aVelocityY", ""),
        float("aVelocityZ", ""),
      )

      void(
        "set3dSourceMinMaxDistance",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aMinDistance", ""),
        float("aMaxDistance", ""),
      )

      void(
        "set3dSourceAttenuation",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        unsigned_int("aAttenuationModel", ""),
        float("aAttenuationRolloffFactor", ""),
      )

      void(
        "set3dSourceDopplerFactor",
        "",
        Soloud("aSoloud", ""),
        unsigned_int("aVoiceHandle", ""),
        float("aDopplerFactor", ""),
      )

      void(
        "mix",
        "",
        Soloud("aSoloud", ""),
        float.p("aBuffer", ""),
        AutoSize("aBuffer")..unsigned_int("aSamples", ""),
      )

      void(
        "mixSigned16",
        "",
        Soloud("aSoloud", ""),
        short.p("aBuffer", ""),
        AutoSize("aBuffer")..unsigned_int("aSamples", ""),
      )

      // soloud_c_arc.h

      int(
        "pauseDevice",
        "",
        Soloud("aSoloud", ""),
      )

      int(
        "resumeDevice",
        "",
        Soloud("aSoloud", ""),
      )

      void(
        "getWaveA",
        "",
        Soloud("aSoloud", ""),
        Check(256)..float.p("aWaves", ""),
      )

      void(
        "calcFFTA",
        "",
        Soloud("aSoloud", ""),
        Check(256)..float.p("aFFT", ""),
      )
    }
