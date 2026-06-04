package utils

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class TtsGenerator(
    val maxChars: Int = 1000,
    private val piperPath: String = "/home/hawk0120/dev/Watney4/piper",
    private val voiceModel: String = "en_US-lessac-medium.onnx"
) {
    private val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))
    private val piperBin = "$piperPath/piper"
    private val modelPath = "$piperPath/$voiceModel"

    fun generate(text: String): ByteArray? {
        val clean = text.replace(Regex("[*_`\\[\\]()#]"), "")
            .take(maxChars)
            .ifBlank { return null }

        val id = UUID.randomUUID().toString().take(8)
        val wav = tmpDir.resolve("watney-tts-$id.wav")
        val ogg = tmpDir.resolve("watney-tts-$id.ogg")

        return try {
            val piper = ProcessBuilder(piperBin, "--model", modelPath, "--output-file", wav.toString())
                .redirectErrorStream(true)
                .start()
            piper.outputStream.bufferedWriter().use { it.write(clean) }
            piper.outputStream.close()
            val pc = piper.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!pc || !Files.exists(wav)) return null

            val ffmpeg = ProcessBuilder(
                "ffmpeg", "-y", "-i", wav.toString(), "-codec:a", "libvorbis", ogg.toString()
            ).start()
            val fc = ffmpeg.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!fc || !Files.exists(ogg)) return null

            Files.readAllBytes(ogg)
        } catch (_: Exception) {
            null
        } finally {
            try { Files.deleteIfExists(wav) } catch (_: Exception) {}
            try { Files.deleteIfExists(ogg) } catch (_: Exception) {}
        }
    }
}
