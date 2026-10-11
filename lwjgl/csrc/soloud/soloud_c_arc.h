#ifndef SOLOUD_C_ARC_H_INCLUDED
#define SOLOUD_C_ARC_H_INCLUDED

#include "soloud_c.h"

#ifdef __cplusplus
extern "C" {
#endif
int Soloud_pauseDevice(Soloud *aSoloud);
int Soloud_resumeDevice(Soloud *aSoloud);
void Soloud_getWaveA(Soloud * aSoloud, float * aWaves);
void Soloud_calcFFTA(Soloud * aSoloud, float * aFFT);

void Bus_getWaveA(Bus * aBus, float * aWaves);
void Bus_calcFFTA(Bus * aBus, float * aFFT);

void AudioSource_setPriority(AudioSource *aSound, float priority);
void AudioSource_setMinConcurrentInterrupt(AudioSource *aSound, float value);
void AudioSource_setMaxConcurrent(AudioSource *aSound, int maxConcurrent);
void AudioSource_setConcurrentGroup(AudioSource *aSound, int group);
void AudioSource_setLooping(AudioSource *aSound, bool loop);
void AudioSource_setSingleInstance(AudioSource *aSound, bool single);
void AudioSource_stop(AudioSource *aSound);
void AudioSource_setFilter(AudioSource *aSound, int index, Filter *filter);
}
#endif
