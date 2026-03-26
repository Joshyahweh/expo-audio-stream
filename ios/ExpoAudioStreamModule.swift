import ExpoModulesCore
import AVFoundation
import Foundation

private let eventAudioData = "onAudioData"
private let tapBufferSize: AVAudioFrameCount = 4096
private let defaultSampleRate: Double = 16000
private let defaultChannels: Int = 1
private let defaultBitDepth: Int = 16
private let supportedBitDepth: Int = 16

public class ExpoAudioStreamModule: Module {
  private var audioEngine: AVAudioEngine?

  public func definition() -> ModuleDefinition {
    Name("ExpoAudioStream")
    Events("onAudioData")
    Function("start") { (options: [String: Any]) in
      self.start(options: options)
    }
    Function("stop") {
      self.stop()
    }
    OnDestroy {
      self.stop()
    }
  }

  private func start(options: [String: Any]) {
    let sampleRate = Self.optDouble(options["sampleRate"], default: defaultSampleRate)
    let channels = Self.optInt(options["channels"], default: defaultChannels)
    let bitDepth = Self.optInt(options["bitDepth"], default: defaultBitDepth)
    if bitDepth != supportedBitDepth {
      NSLog("[ExpoAudioStream] bitDepth=%d is not supported; only %d-bit PCM is supported, proceeding with %d-bit", bitDepth, supportedBitDepth, supportedBitDepth)
    }

    stop()

    let engine = AVAudioEngine()
    let inputNode = engine.inputNode

    guard let format = AVAudioFormat(
      commonFormat: .pcmFormatFloat32,
      sampleRate: sampleRate,
      channels: AVAudioChannelCount(channels),
      interleaved: false
    ) else {
      NSLog("[ExpoAudioStream] Failed to create AVAudioFormat (sampleRate=%f, channels=%d)", sampleRate, channels)
      return
    }

    inputNode.installTap(onBus: 0, bufferSize: tapBufferSize, format: format) { [weak self] buffer, _ in
      guard let self = self else { return }
      guard let channelData = buffer.floatChannelData?[0] else {
        NSLog("[ExpoAudioStream] Missing float channel data in tap buffer")
        return
      }
      let frameLength = Int(buffer.frameLength)
      var int16Buffer = [Int16](repeating: 0, count: frameLength)
      for i in 0..<frameLength {
        let clamped = max(-1.0, min(1.0, channelData[i]))
        int16Buffer[i] = Int16(clamped * Float(Int16.max))
      }
      let data = int16Buffer.withUnsafeBytes { Data($0) }
      let base64 = data.base64EncodedString()
      self.sendEvent(eventAudioData, ["data": base64])
    }

    engine.prepare()
    do {
      try engine.start()
      audioEngine = engine
    } catch {
      engine.inputNode.removeTap(onBus: 0)
      NSLog("[ExpoAudioStream] start() failed: %@", String(describing: error))
    }
  }

  private func stop() {
    if let engine = audioEngine {
      engine.inputNode.removeTap(onBus: 0)
      engine.stop()
    }
    audioEngine = nil
  }

  private static func optDouble(_ value: Any?, default defaultValue: Double) -> Double {
    if let d = value as? Double {
      return d
    }
    if let i = value as? Int {
      return Double(i)
    }
    if let n = value as? NSNumber {
      return n.doubleValue
    }
    return defaultValue
  }

  private static func optInt(_ value: Any?, default defaultValue: Int) -> Int {
    if let i = value as? Int {
      return i
    }
    if let d = value as? Double {
      return Int(d)
    }
    if let n = value as? NSNumber {
      return n.intValue
    }
    return defaultValue
  }
}
