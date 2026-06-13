package utils

import kotlinx.coroutines.channels.SendChannel
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.audio.UserAudio
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class VoiceChatManager(
    private val inbox: SendChannel<IncomingMessage>,
    private val replyTo: ChatInterface,
    private val logLevel: LogLevel = LogLevel.INFO,
    private val piperPath: String = "/home/hawk0120/dev/Watney4/piper",
    private val voiceModel: String = "en_US-lessac-medium.onnx"
) {
    private val log = Logger.getLogger("Voice", logLevel)
    private var currentChannel: AudioChannel? = null
    private val audioBuffer = ByteArrayOutputStream()
    private var silenceFrames = 0
    private var hasSpeech = false
    @Volatile private var isSpeaking = false
    private val voskModelPath = System.getProperty("user.home") + "/.cache/vosk/vosk-model-small-en-us-0.15"
    private val piperBin = "$piperPath/piper"
    private val modelPath = "$piperPath/$voiceModel"

    val isInVoice: Boolean get() = currentChannel != null

    fun join(channel: AudioChannel) {
        currentChannel = channel
        val mgr = channel.guild.audioManager
        mgr.setReceivingHandler(VoiceReceiveHandler())
        mgr.openAudioConnection(channel)
        log.info("Joined voice channel: ${channel.name}")
    }

    fun leave() {
        currentChannel?.guild?.audioManager?.closeAudioConnection()
        currentChannel = null
        audioBuffer.reset()
        silenceFrames = 0
        hasSpeech = false
        log.info("Left voice channel")
    }

    fun speak(text: String) {
        val ch = currentChannel ?: return
        val stripped = text.replace(Regex("[*_`~|]"), "").trim()
        if (stripped.length < 3) return

        val ttsText = stripped.take(500)
        try {
            val wavFile = File.createTempFile("tts_", ".wav")
            val rawFile = File.createTempFile("tts_", ".raw")

            val piper = ProcessBuilder(piperBin, "--model", modelPath, "--output-file", wavFile.absolutePath)
                .redirectErrorStream(true).start()
            piper.outputStream.bufferedWriter().use { it.write(ttsText) }
            piper.outputStream.close()
            val pc = piper.waitFor(30, TimeUnit.SECONDS)
            if (!pc || !wavFile.exists() || wavFile.length() == 0L) { wavFile.delete(); rawFile.delete(); return }

            val ffmpeg = ProcessBuilder(
                "ffmpeg", "-y", "-i", wavFile.absolutePath,
                "-ar", "48000", "-ac", "2", "-sample_fmt", "s16",
                "-f", "s16le", rawFile.absolutePath
            ).redirectErrorStream(true).start()
            ffmpeg.waitFor(10, TimeUnit.SECONDS)
            if (!rawFile.exists() || rawFile.length() == 0L) { wavFile.delete(); rawFile.delete(); return }

            val pcm = rawFile.readBytes()
            wavFile.delete(); rawFile.delete()
            if (pcm.size < 3840) return

            isSpeaking = true
            ch.guild.audioManager.setSendingHandler(TtsSendHandler(pcm))
            log.debug("Playing TTS in voice channel (${pcm.size} bytes)")

            val durationMs = (pcm.size.toLong() * 1000) / 192000
            Thread {
                try {
                    val elapsed = durationMs + 500L
                    val sleep = (elapsed / 20L) * 20L
                    Thread.sleep(sleep)
                } catch (_: InterruptedException) {}
                isSpeaking = false
            }.start()
        } catch (e: Exception) {
            log.error("TTS playback failed: ${e.message}")
            isSpeaking = false
        }
    }

    private inner class VoiceReceiveHandler : AudioReceiveHandler {
        override fun canReceiveCombined(): Boolean = true
        override fun canReceiveUser(): Boolean = true

        override fun handleUserAudio(audio: UserAudio) {
        }

        override fun handleCombinedAudio(audio: CombinedAudio) {
            if (isSpeaking) return
            val raw = audio.getAudioData(1.0)
            if (raw.size < 4) return
            val shorts = ShortArray(raw.size / 2)
            for (i in shorts.indices) {
                shorts[i] = ((raw[i * 2].toInt() and 0xFF) or (raw[i * 2 + 1].toInt() shl 8)).toShort()
            }

            val monoShorts = ShortArray(shorts.size / 2)
            for (i in monoShorts.indices) {
                monoShorts[i] = ((shorts[i * 2].toInt() + shorts[i * 2 + 1].toInt()) / 2).toShort()
            }

            val downsampled = ShortArray(monoShorts.size / 3 + 1)
            var dsIdx = 0
            for (i in monoShorts.indices step 3) downsampled[dsIdx++] = monoShorts[i]

            var sumSq = 0.0
            var peak = 0.0
            for (i in 0 until dsIdx) {
                val n = downsampled[i].toDouble() / 32767.0
                sumSq += n * n
                val abs = kotlin.math.abs(n)
                if (abs > peak) peak = abs
            }
            val rms = sqrt(sumSq / dsIdx.coerceAtLeast(1))
            if (rms > 0.0005) {
                log.trace("Audio frame: peak=$peak rms=$rms (${if (rms > 0.005) "above threshold" else "too quiet"})")
            }

            if (rms > 0.005) {
                for (i in 0 until dsIdx) {
                    audioBuffer.write(downsampled[i].toInt() and 0xFF)
                    audioBuffer.write((downsampled[i].toInt() shr 8) and 0xFF)
                }
                silenceFrames = 0
                hasSpeech = true
            } else if (hasSpeech) {
                for (i in 0 until dsIdx) {
                    audioBuffer.write(downsampled[i].toInt() and 0xFF)
                    audioBuffer.write((downsampled[i].toInt() shr 8) and 0xFF)
                }
                silenceFrames++
                if (silenceFrames >= 60) {
                    val pcm = audioBuffer.toByteArray()
                    audioBuffer.reset()
                    silenceFrames = 0
                    hasSpeech = false
                    Thread { processPcm(pcm) }.start()
                }
            }
        }
    }

    private fun processPcm(pcm: ByteArray) {
        log.debug("Processing utterance: ${pcm.size} bytes of PCM")
        if (pcm.size < 16000) {
            log.debug("Utterance too short (${pcm.size} bytes < 16000), skipping")
            return
        }

        try {
            val text = transcribe(pcm)
            log.debug("Transcription result: '${text}'")
            if (text.isNotBlank()) {
                log.info("Voice transcription: $text")
                inbox.trySend(IncomingMessage(text, replyTo))
            }
        } catch (e: Exception) {
            log.error("Transcription failed: ${e.message}")
        }
    }

    private fun transcribe(pcm: ByteArray): String {
        val wavFile = File.createTempFile("vosk_", ".wav")
        try {
            writeWav(wavFile, pcm, 16000)
            log.debug("WAV file written: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
            val script = """
import sys, json, wave
from vosk import Model, KaldiRecognizer
model = Model("$voskModelPath")
wf = wave.open("${wavFile.absolutePath}", "rb")
rec = KaldiRecognizer(model, wf.getframerate())
while True:
    data = wf.readframes(4000)
    if not data:
        break
    rec.AcceptWaveform(data)
result = json.loads(rec.FinalResult())
text = result.get("text", "")
print(text)
""".trimIndent()
            val proc = ProcessBuilder("python3", "-c", script).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            val finished = proc.waitFor(30, TimeUnit.SECONDS)
            log.debug("Python transcription finished=$finished output='$out'")
            return out
        } finally {
            wavFile.delete()
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        val dataSize = pcm.size
        file.outputStream().use { os ->
            os.write("RIFF".toByteArray())
            os.write(intLe(36 + dataSize))
            os.write("WAVE".toByteArray())
            os.write("fmt ".toByteArray())
            os.write(intLe(16))
            os.write(shortLe(1))
            os.write(shortLe(1))
            os.write(intLe(sampleRate))
            os.write(intLe(sampleRate * 2))
            os.write(shortLe(2))
            os.write(shortLe(16))
            os.write("data".toByteArray())
            os.write(intLe(dataSize))
            os.write(pcm)
        }
    }
    private class TtsSendHandler(private val pcm: ByteArray) : AudioSendHandler {
        private val frameSize = 3840
        private var pos = 0
        override fun canProvide(): Boolean = pos < pcm.size
        override fun provide20MsAudio(): ByteBuffer {
            val remaining = pcm.size - pos
            val chunk = minOf(frameSize, remaining)
            val buf = ByteBuffer.allocate(frameSize)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            buf.put(pcm, pos, chunk)
            pos += chunk
            buf.flip()
            return buf
        }
        override fun isOpus(): Boolean = false
    }

    companion object {
        private fun intLe(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
        )
        private fun shortLe(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()
        )
    }
}
