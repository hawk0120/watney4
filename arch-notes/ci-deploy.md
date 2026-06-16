# CI / Deploy Pipeline

Runs on Forgejo Actions (self-hosted at `100.114.88.76:30000`). Workflow lives in `.forgejo/workflows/deploy.yml`.

## Workflow: Deploy to bitnest5

**Trigger:** Push to `main`

**Steps:**

1. **Bump version and tag** — finds the latest `v*` tag, increments the patch version (e.g. `v1.0.1` → `v1.0.2`), updates `version` in `build.gradle.kts`, commits with `[skip ci]`, tags, and pushes back via `GITHUB_TOKEN`. First deploy starts at `v1.0.1`.
2. **Setup SSH** — loads `DEPLOY_SSH_KEY` secret into `~/.ssh/id_ed25519`, fingerprints `DEPLOY_HOST` into `known_hosts`
3. **Deploy & build** — SSHs into bitnest5 as `hawk0120`, `cd`s to `/home/hawk0120/dev/kotlin/watney4`, runs `git pull origin main && ./gradlew build`
4. **Restart bot** — `pkill` any existing `core.MainKt` or `watney4` process, then `nohup ./gradlew run > /tmp/watney4.log 2>&1 &`

## Secrets

Set in Forgejo repo → Settings → Actions → Secrets:

| Secret | Purpose |
|---|---|
| `DEPLOY_SSH_KEY` | SSH private key that can SSH into bitnest5 as `hawk0120` |
| `DEPLOY_HOST` | Hostname/IP of bitnest5 reachable from the Forgejo runner |
| `GITHUB_TOKEN` | Auto-provided by Forgejo Actions; used for pushing the version bump commit and tag back to the repo |

## Runner

Forgejo Actions needs a registered runner. The runner must have:
- Network access to both the Forgejo instance and bitnest5
- Java 21 / Gradle are not needed on the runner — the actual build happens on bitnest5

### Install runner on bitnest5

Find the correct download URL from <https://code.forgejo.org/forgejo/runner/releases/latest> (the `latest/download` pattern doesn't work for this repo — use the actual release tag URL):

```bash
# Download forgejo-runner (check latest version tag from releases page)
sudo curl -sLo /usr/local/bin/forgejo-runner \
  https://code.forgejo.org/forgejo/runner/releases/download/v12.11.1/forgejo-runner-linux-amd64
sudo chmod +x /usr/local/bin/forgejo-runner

# Generate config
mkdir -p ~/.forgejo-runner
forgejo-runner generate-config > ~/.forgejo-runner/config.yml
```

Edit `~/.forgejo-runner/config.yml` and add the connection under `server.connections`:

```yaml
server:
  connections:
    forgejo:
      url: http://localhost:3000/
      uuid: <UUID_FROM_WEB_UI>
      token: <TOKEN_FROM_WEB_UI>
      labels:
        - ubuntu-latest:docker://node:20-bookworm
```

**Important:** Forgejo runs in K8s on bitnest5 with NodePort 3000:30000. The runner must use `http://localhost:3000/` (matches the K8s cluster service port) rather than the external NodePort URL from the web UI.

Then start the daemon:

```bash
cd ~/.forgejo-runner
forgejo-runner daemon -c config.yml
```

### systemd service

Create `/etc/systemd/system/forgejo-runner.service`:

```ini
[Unit]
Description=Forgejo Actions Runner (deploy_watney4)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=hawk0120
Group=hawk0120
ExecStart=/usr/local/bin/forgejo-runner daemon -c /home/hawk0120/.forgejo-runner/config.yml
Restart=always
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

```bash
sudo cp forgejo-runner.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now forgejo-runner
```
