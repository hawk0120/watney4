# CI / Deploy Pipeline

Runs on Forgejo Actions (self-hosted at `100.114.88.76:30000`). Workflow lives in `.forgejo/workflows/deploy.yml`.

## Workflow: Deploy to bitnest5

**Trigger:** Push to `main`

**Steps:**

1. **Setup SSH** — loads `DEPLOY_SSH_KEY` secret into `~/.ssh/id_ed25519`, fingerprints `DEPLOY_HOST` into `known_hosts`
2. **Deploy & build** — SSHs into bitnest5 as `hawk0120`, `cd`s to `/home/hawk0120/dev/kotlin/watney4`, runs `git pull origin main && ./gradlew build`
3. **Restart bot** — `pkill` any existing `core.MainKt` or `watney4` process, then `nohup ./gradlew run > /tmp/watney4.log 2>&1 &`

## Secrets

Set in Forgejo repo → Settings → Actions → Secrets:

| Secret | Purpose |
|---|---|
| `DEPLOY_SSH_KEY` | SSH private key that can SSH into bitnest5 as `hawk0120` |
| `DEPLOY_HOST` | Hostname/IP of bitnest5 reachable from the Forgejo runner |

## Runner

Forgejo Actions needs a registered runner. The runner must have:
- Network access to both the Forgejo instance and bitnest5
- Java 21 / Gradle are not needed on the runner — the actual build happens on bitnest5
