#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "=== Watney4 Setup ==="
echo ""

# ── System packages ──
echo ">>> Installing system packages..."
sudo apt update
sudo apt install -y ffmpeg opus-tools python3 python3-pip

# ── Python packages ──
echo ">>> Installing Python packages..."
pip3 install vosk ddgs --break-system-packages

# ── Piper TTS ──
echo ">>> Setting up Piper TTS..."
mkdir -p piper

if [ ! -f piper/piper ]; then
    echo "    Downloading Piper binary..."
    curl -sL "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz" \
        | tar xz -C piper/ --strip-components=1
fi

if [ ! -f piper/en_US-lessac-medium.onnx ]; then
    echo "    Downloading Piper voice model (en_US-lessac-medium)..."
    curl -sLo piper/en_US-lessac-medium.onnx \
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx"
    curl -sLo piper/en_US-lessac-medium.onnx.json \
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"
fi

echo ""
echo ">>> Piper ready at $(realpath piper/piper)"

# ── Config ──
if [ ! -f src/main/resources/application.properties ]; then
    echo ""
    echo ">>> Creating config template..."
    cat > src/main/resources/application.properties << 'CFG'
log.level=info
app.provider=mistral
mistral.api-key=<your-mistral-api-key>
mistral.model=ministral-8b-2512
mistral.base-url=https://api.mistral.ai/v1/chat/completions
llama.base-url=http://127.0.0.1:8080/completion
llama.model=gemma4:e2b
openrouter.api-key=<your-openrouter-api-key>
openrouter.model=google/gemma-4-26b-a4b-it:free
openrouter.base-url=https://openrouter.ai/api/v1/chat/completions
discord.token=<your-discord-bot-token>
memory.db-path=watney4.db
CFG
    echo "    Edit src/main/resources/application.properties with your API keys"
else
    echo ""
    echo ">>> Config already exists, skipping"
fi

# ── Build ──
echo ""
echo ">>> Running build..."
./gradlew build

echo ""
echo "=== Setup complete ==="
echo "Next steps:"
echo "  1. Edit src/main/resources/application.properties"
echo "  2. Create a Discord app at https://discord.com/developers/applications"
echo "  3. Run: ./gradlew run"
