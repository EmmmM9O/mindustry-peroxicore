
#include "soloud.h"
#include "soloud_audiosource.h"

using namespace SoLoud;

extern "C" {
int Soloud_pauseDevice(void *aSoloud) {
  Soloud *cl = (Soloud *)aSoloud;
  return cl->pause();
}
int Soloud_resumeDevice(void *aSoloud) {
  Soloud *cl = (Soloud *)aSoloud;
  return cl->resume();
}
void Soloud_getWaveA(void *aSoloud, float *aWaves) {
  Soloud *cl = (Soloud *)aSoloud;
  float *res = cl->getWave();
  for (int i = 0; i < 256; i++) {
    aWaves[i] = res[i];
  }
}
void Soloud_calcFFTA(Soloud * aSoloud, float *aFFT){
  Soloud *cl = (Soloud *)aSoloud;
  float *res = cl->calcFFT();
  for (int i = 0; i < 256; i++) {
    aFFT[i] = res[i];
  }
}

void Bus_getWaveA(void *aBus, float *aWaves) {
  Bus *cl = (Bus *)aBus;
  float *res = cl->getWave();
  for (int i = 0; i < 256; i++) {
    aWaves[i] = res[i];
  }
}
void Bus_calcFFTA(Bus * aBus, float *aFFT){
  Bus *cl = (Bus *)aBus;
  float *res = cl->calcFFT();
  for (int i = 0; i < 256; i++) {
    aFFT[i] = res[i];
  }
}

void AudioSource_setPriority(void *aSound, float priority) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setPriority(priority);
}
void AudioSource_setMinConcurrentInterrupt(void *aSound, float value) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setMinConcurrentInterrupt(value);
}

void AudioSource_setMaxConcurrent(void *aSound, int maxConcurrent) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setMaxConcurrent(maxConcurrent);
}

void AudioSource_setConcurrentGroup(void *aSound, int group) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setConcurrentGroup(group);
}

void AudioSource_setLooping(void *aSound, bool loop) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setLooping(loop);
}

void AudioSource_setSingleInstance(void *aSound, bool single) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->setSingleInstance(single);
}

void AudioSource_stop(void *aSound) {
  AudioSource *cl = (AudioSource *)aSound;
  cl->stop();
}

void AudioSource_setFilter(void *aSound, int index, Filter *filter){
  AudioSource *cl = (AudioSource *)aSound;
  cl->setFilter(index, filter);
}
}
