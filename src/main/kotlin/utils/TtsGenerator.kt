package utils

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class TtsGenerator(
    private val maxChars: Int = 1000,
    private val voice: String = "en"
) {
    private val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))

    fun generate(text: String): ByteArray? {
        val clean = text.replace(Regex("[*_`\\[\\]()#]"), "")
            .take(maxChars)
            .ifBlank { return null }

        val id = UUID.randomUUID().toString().take(8)
        val wav = tmpDir.resolve("watney-tts-$id.wav")
        val ogg = tmpDir.resolve("watney-tts-$id.ogg")

        return try {
            val espeak = ProcessBuilder(
                "espeak-ng", "-v", voice, "-w", wav.toString(), clean
            ).start()
            val ec = espeak.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            if (!ec || !Files.exists(wav)) return null

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
