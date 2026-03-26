export type AudioStreamOptions = {
  sampleRate?: number; // default: 16000
  channels?: number; // default: 1
  bitDepth?: number; // default: 16
};

export type AudioDataEvent = {
  data: string; // Base64-encoded PCM chunk
};
