package expo.modules.audiostream

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

private const val TAG = "ExpoAudioStream"
private const val EVENT_AUDIO_DATA = "onAudioData"
private const val BUFFER_SIZE_MULTIPLIER = 2
private const val DEFAULT_SAMPLE_RATE = 16000
private const val DEFAULT_CHANNELS = 1
private const val DEFAULT_BIT_DEPTH = 16
private const val SUPPORTED_BIT_DEPTH = 16

class ExpoAudioStreamModule : Module() {
  private var audioRecord: AudioRecord? = null

  @Volatile
  private var isRecording = false

  private var recordingThread: Thread? = null

  override fun definition() = ModuleDefinition {
    Name("ExpoAudioStream")
    Events(EVENT_AUDIO_DATA)
    Function("start") { options: Map<String, Any?> ->
      start(options)
    }
    Function("stop") {
      stop()
    }
    OnDestroy {
      stop()
    }
  }

  private fun start(options: Map<String, Any?>) {
    try {
      if (isRecording) {
        Log.w(TAG, "start() called while already recording; ignoring")
        return
      }

      val sampleRate = options.optInt("sampleRate", DEFAULT_SAMPLE_RATE)
      val channels = options.optInt("channels", DEFAULT_CHANNELS)
      val bitDepth = options.optInt("bitDepth", DEFAULT_BIT_DEPTH)
      if (bitDepth != SUPPORTED_BIT_DEPTH) {
        Log.w(
          TAG,
          "bitDepth=$bitDepth is not supported; only $SUPPORTED_BIT_DEPTH-bit PCM is supported, using $SUPPORTED_BIT_DEPTH"
        )
      }

      val channelConfig = when (channels) {
        1 -> AudioFormat.CHANNEL_IN_MONO
        2 -> AudioFormat.CHANNEL_IN_STEREO
        else -> {
          Log.w(TAG, "Invalid channels=$channels; defaulting to mono")
          AudioFormat.CHANNEL_IN_MONO
        }
      }

      val audioFormat = AudioFormat.ENCODING_PCM_16BIT
      val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
        throw IllegalStateException(
          "AudioRecord.getMinBufferSize failed for sampleRate=$sampleRate, channelConfig=$channelConfig: code=$minBufferSize"
        )
      }

      val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

      val record = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
      )

      if (record.state != AudioRecord.STATE_INITIALIZED) {
        try {
          record.release()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to release uninitialized AudioRecord", e)
        }
        throw IllegalStateException("AudioRecord could not be initialized (state != STATE_INITIALIZED)")
      }

      audioRecord = record
      isRecording = true
      try {
        record.startRecording()
      } catch (e: IllegalStateException) {
        Log.e(TAG, "startRecording failed", e)
        cleanupRecord()
        throw e
      }

      val thread = Thread {
        recordingLoop(bufferSize)
      }
      recordingThread = thread
      thread.start()
    } catch (e: Exception) {
      Log.e(TAG, "start() failed", e)
      stop()
      throw e
    }
  }

  private fun recordingLoop(bufferSize: Int) {
    val record = audioRecord ?: return
    val buffer = ByteArray(bufferSize)
    while (isRecording) {
      val bytesRead = try {
        record.read(buffer, 0, bufferSize)
      } catch (e: IllegalStateException) {
        Log.e(TAG, "AudioRecord.read IllegalStateException", e)
        break
      } catch (e: Exception) {
        Log.e(TAG, "AudioRecord.read unexpected error", e)
        break
      }
      if (bytesRead < 0) {
        Log.e(TAG, "AudioRecord.read error code: $bytesRead")
        continue
      }
      if (bytesRead > 0) {
        try {
          val chunk = buffer.copyOf(bytesRead)
          val base64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
          sendEvent(EVENT_AUDIO_DATA, mapOf("data" to base64))
        } catch (e: Exception) {
          Log.e(TAG, "Failed to encode or send audio chunk", e)
        }
      }
    }
  }

  private fun stop() {
    isRecording = false
    val record = audioRecord
    audioRecord = null
    if (record != null) {
      try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          record.stop()
        }
      } catch (e: IllegalStateException) {
        Log.e(TAG, "AudioRecord.stop failed", e)
      } catch (e: Exception) {
        Log.e(TAG, "AudioRecord.stop unexpected error", e)
      }
      try {
        record.release()
      } catch (e: Exception) {
        Log.e(TAG, "AudioRecord.release failed", e)
      }
    }
    val thread = recordingThread
    recordingThread = null
    if (thread != null) {
      try {
        thread.join(2000)
      } catch (e: InterruptedException) {
        Log.w(TAG, "Interrupted while joining recording thread", e)
        Thread.currentThread().interrupt()
      }
    }
  }

  private fun cleanupRecord() {
    isRecording = false
    val record = audioRecord
    audioRecord = null
    if (record != null) {
      try {
        record.release()
      } catch (e: Exception) {
        Log.e(TAG, "cleanupRecord: release failed", e)
      }
    }
    recordingThread = null
  }
}

private fun Map<String, Any?>.optInt(key: String, default: Int): Int {
  val v = this[key] ?: return default
  return when (v) {
    is Int -> v
    is Double -> v.toInt()
    is Float -> v.toInt()
    is Long -> v.toInt()
    else -> {
      Log.w("ExpoAudioStream", "Could not parse '$key' as Int, using default=$default")
      default
    }
  }
}
