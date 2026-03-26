import { EventEmitter, requireNativeModule, type EventSubscription } from 'expo-modules-core';

import type { AudioDataEvent, AudioStreamOptions } from './ExpoAudioStreamModule.types';

interface ExpoAudioStreamNativeModule {
  start(options?: AudioStreamOptions): void;
  stop(): void;
}

const nativeModule = requireNativeModule<ExpoAudioStreamNativeModule>('ExpoAudioStream');

export function start(options?: AudioStreamOptions): void {
  nativeModule.start(options ?? {});
}

export function stop(): void {
  nativeModule.stop();
}

/**
 * Subscribes to native audio chunks. We use `EventEmitter.prototype.addListener.call(...)` so we
 * never read `nativeModule.addListener` directly. On Android (Hermes), JSI module objects can throw
 * `ReferenceError: Property 'addListener' doesn't exist` when accessing inherited methods on the
 * lazy HostObject; calling through the prototype avoids that.
 */
export function addListener(
  event: 'onAudioData',
  callback: (event: AudioDataEvent) => void
): EventSubscription {
  return EventEmitter.prototype.addListener.call(
    nativeModule,
    event,
    callback as (event: Record<string, unknown>) => void
  );
}
