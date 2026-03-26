# expo-audio-stream

Real-time **16-bit PCM** capture from the device microphone on **Android** and **iOS**, delivered to JavaScript as **Base64-encoded chunks** via Expo’s event system. The module is implemented with **AudioRecord** (Android) and **AVAudioEngine** (iOS), targets **Expo SDK 55+** and **React Native’s New Architecture** (JSI / Turbo Modules), and works in **managed** and **bare** Expo workflows.

**Platform testing:** This library is **validated on Android** right now. The **iOS** implementation is included and intended to work, but it has **not been exercised in the same depth**; if you hit problems on iOS, please open an issue—**they will be fixed**.

Many older audio packages still assume the legacy bridge or lack first-class New Architecture support. This library exists to fill that gap for streaming-style use cases (e.g. live speech APIs) without pulling in extra native audio SDKs.

## Installation

In your app (Expo SDK 55+):

```bash
npx expo install expo-audio-stream
```

For a **bare** React Native app using Expo modules, follow [Expo’s native setup](https://docs.expo.dev/bare/installing-expo-modules/) so `expo-modules-core` is linked, then install the package as above.

## Permissions (app responsibility)

The native module **does not** request permissions. Your app **must** declare platform permissions and request them before calling `start()`.

### Android

Add to your **application** `AndroidManifest.xml` (or rely on this library’s merged manifest, which includes `RECORD_AUDIO`):

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

At runtime, request the permission with your preferred API (e.g. `PermissionsAndroid`, `expo-audio`’s `requestRecordingPermissionsAsync()`, or another permission helper).

### iOS

Set a microphone usage string in **Info.plist** (or `app.json` → `expo.ios.infoPlist`):

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Describe why you need the microphone.</string>
```

## API

### TypeScript types

```ts
export type AudioStreamOptions = {
  sampleRate?: number; // default: 16000
  channels?: number; // default: 1
  bitDepth?: number; // default: 16
};

export type AudioDataEvent = {
  data: string; // Base64-encoded PCM chunk (16-bit little-endian frames)
};
```

### Methods

| Export | Description |
| --- | --- |
| `start(options?: AudioStreamOptions): void` | Begins capture with optional sample rate, channel count, and bit depth (see limitations). |
| `stop(): void` | Stops capture and releases native resources. |
| `addListener('onAudioData', callback): EventSubscription` | Listens for Base64 PCM chunks. Call `subscription.remove()` to unsubscribe. |

### Default export

The default export is an object `{ start, stop, addListener }` for convenience.

### Example

```ts
import ExpoAudioStream from 'expo-audio-stream';

ExpoAudioStream.start({ sampleRate: 16000, channels: 1, bitDepth: 16 });

const subscription = ExpoAudioStream.addListener('onAudioData', (event) => {
  console.log(event.data); // Base64 PCM
});

ExpoAudioStream.stop();
subscription.remove();
```

## Deepgram live streaming example

Below is a minimal pattern: request the mic in your app, open a Deepgram **WebSocket** live endpoint, then forward each Base64 chunk as binary after decoding. Adjust URLs, model names, and auth to match [Deepgram’s current API](https://developers.deepgram.com/).

```ts
import * as FileSystem from 'expo-file-system';
import ExpoAudioStream from 'expo-audio-stream';

const DG_KEY = process.env.EXPO_PUBLIC_DEEPGRAM_KEY!; // never ship production keys in client apps

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export async function streamMicToDeepgram(wsUrl: string) {
  const ws = new WebSocket(wsUrl, undefined, {
    headers: { Authorization: `Token ${DG_KEY}` },
  });

  await new Promise<void>((resolve, reject) => {
    ws.onopen = () => resolve();
    ws.onerror = (e) => reject(e);
  });

  const sub = ExpoAudioStream.addListener('onAudioData', (e) => {
    const pcm = base64ToUint8Array(e.data);
    ws.send(pcm);
  });

  ExpoAudioStream.start({ sampleRate: 16000, channels: 1, bitDepth: 16 });

  return () => {
    sub.remove();
    ExpoAudioStream.stop();
    ws.close();
  };
}
```

**Security note:** embedding API keys in a client app is unsafe for production. Prefer a backend that issues short-lived tokens or proxies audio to Deepgram.

## Limitations

- **16-bit PCM only** — other `bitDepth` values are ignored with a native warning; output is always 16-bit PCM in the event payload.
- **Permissions** are not handled inside the module; the hosting app must declare and request them.
- **Web** — the JS API is stubbed; no microphone capture on web.
- **Testing** — developed against **Expo SDK 55** and **React Native 0.77+** (New Architecture). **Only Android has been tested end-to-end** so far; treat iOS as best-effort until reported issues are resolved. Validate on your target OS versions and devices.

## Example app

From the repository root:

```bash
cd example
npm install
npx expo run:android
# iOS is supported in code but not the primary tested path yet:
npx expo run:ios
```

The example uses `expo-audio`’s `requestRecordingPermissionsAsync()` before starting the stream.

## License

MIT
