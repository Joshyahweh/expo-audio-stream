export {
  start,
  stop,
  addListener,
} from './ExpoAudioStreamModule';
export type { AudioStreamOptions, AudioDataEvent } from './ExpoAudioStreamModule.types';

import { addListener, start, stop } from './ExpoAudioStreamModule';

const ExpoAudioStream = {
  start,
  stop,
  addListener,
};

export default ExpoAudioStream;
