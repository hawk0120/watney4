# Voice Chat & TTS

Two voice features: real-time voice chat in Discord voice channels, and text-to-speech file attachments in DMs.

## TTS Generator (`utils/TtsGenerator.kt`)

Wraps [Piper](https://github.com/rhasspy/piper) to produce spoken audio:

1. Sanitize input text (strip markdown, truncate to `maxChars`)
2. Pipe text to `piper --model` → WAV
3. Convert WAV to OGG via `ffmpeg -codec:a libvorbis`
4. Return byte array for attachment

Used by `DiscordBot` when voice mode is on (`/voice` toggle) to attach a `.ogg` file to DM responses.

## Voice Chat Manager (`utils/VoiceChatManager.kt`)

Manages real-time Discord voice connections.

### Joining/Leaving
`/join` and `/leave` slash commands call `VoiceChatManager.join()` / `leave()`, which open/close the audio connection via JDA's `AudioManager`.

### Speaking
`speak(text)` generates TTS via Piper, converts to 48kHz stereo 16-bit PCM via ffmpeg, and plays through a custom `AudioSendHandler`.

### Listening
The `VoiceReceiveHandler` collects raw 48kHz stereo PCM audio, downsamples to ~16kHz mono, performs RMS-based voice activity detection (threshold 0.005), and after 60 frames (~1.2s) of silence, transcribes the utterance using Vosk (via a Python subprocess). The transcribed text is sent to the agent inbox.

### Anti-feedback
An `isSpeaking` flag prevents audio processing while the bot is playing TTS, avoiding acoustic echo loops. The flag is cleared after the estimated TTS duration (PCM bytes / 192000 bytes/sec).

### Key Files
- `utils/VoiceChatManager.kt` — voice connection, STT, TTS playback
- `utils/TtsGenerator.kt` — Piper TTS for DM attachments
