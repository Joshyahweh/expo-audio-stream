import { requestRecordingPermissionsAsync } from 'expo-audio';
import ExpoAudioStream, { type AudioDataEvent } from 'expo-audio-stream';
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

type Status = 'Idle' | 'Streaming...';

export default function App() {
  const [status, setStatus] = useState<Status>('Idle');
  const [chunkCount, setChunkCount] = useState(0);
  const [lastChunkBase64Length, setLastChunkBase64Length] = useState<number | null>(
    null
  );

  const handleAudioData = useCallback((event: AudioDataEvent) => {
    setChunkCount((c) => c + 1);
    setLastChunkBase64Length(event.data.length);
  }, []);

  useEffect(() => {
    const subscription = ExpoAudioStream.addListener('onAudioData', handleAudioData);
    return () => {
      subscription.remove();
      ExpoAudioStream.stop();
    };
  }, [handleAudioData]);

  const toggleStreaming = async () => {
    if (status === 'Streaming...') {
      ExpoAudioStream.stop();
      setStatus('Idle');
      return;
    }

    const permission = await requestRecordingPermissionsAsync();
    if (!permission.granted) {
      Alert.alert(
        'Microphone access required',
        'Grant microphone permission in Settings to stream audio.'
      );
      return;
    }

    ExpoAudioStream.start({ sampleRate: 16000, channels: 1, bitDepth: 16 });
    setChunkCount(0);
    setLastChunkBase64Length(null);
    setStatus('Streaming...');
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text style={styles.title}>expo-audio-stream</Text>
        <Text style={styles.status}>Status: {status}</Text>
        <Text style={styles.counter}>Chunks received: {chunkCount}</Text>
        <Text style={styles.meta}>
          Latest chunk (Base64 length):{' '}
          {lastChunkBase64Length == null ? '—' : String(lastChunkBase64Length)}
        </Text>
        <Button
          title={status === 'Streaming...' ? 'Stop' : 'Start'}
          onPress={() => {
            void toggleStreaming();
          }}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#f4f4f5',
  },
  container: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
  },
  title: {
    fontSize: 22,
    fontWeight: '600',
    marginBottom: 16,
    textAlign: 'center',
  },
  status: {
    fontSize: 16,
    marginBottom: 8,
  },
  counter: {
    fontSize: 16,
    marginBottom: 8,
  },
  meta: {
    fontSize: 14,
    color: '#52525b',
    marginBottom: 24,
  },
});
