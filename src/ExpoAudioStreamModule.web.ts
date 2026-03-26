import type { EventSubscription } from 'expo-modules-core';

import type { AudioDataEvent, AudioStreamOptions } from './ExpoAudioStreamModule.types';

export function start(_options?: AudioStreamOptions): void {
  if (__DEV__) {
    console.warn(
      'expo-audio-stream: Real-time microphone capture is not available on web. start() is a no-op.'
    );
  }
}

export function stop(): void {}

export function addListener(
  _event: 'onAudioData',
  _callback: (event: AudioDataEvent) => void
): EventSubscription {
  return { remove: () => {} };
}
