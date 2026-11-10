# Releasing Astra

Releases are built on GitHub's machines for Windows, macOS and Linux. Installed launchers update
themselves from the newest release, and before every launch they download the newest Astra mod
from it too (so Macs, which can't install launcher updates by themselves, still get every Astra fix).

## One-time setup

1. **Workflow:** double-click `Set Up GitHub Releases.bat` (it copies `tools/github/release.yml` into
   `.github/workflows/`), then commit and push it. Do this again whenever `tools/github/release.yml`
   changes. (Mac/Linux: `mkdir -p .github/workflows && cp tools/github/release.yml .github/workflows/`.)
2. **Repo access:** players' launchers read releases without signing in, so the repo has to be
   **public**. If you want the code private, make a second, public repo just for releases, and put
   its name in `launcher/package.json` → `astraUpdates`.
3. **Server address (optional):** GitHub repo → Settings → Secrets and variables → Actions →
   **Variables** → add `ASTRA_SERVER_URL` = your permanent address (e.g. `https://yourname.duckdns.org`).
   Without it (or while you only have a temporary trycloudflare address), players type the address
   in the launcher's Settings; updates come from GitHub either way.
4. **Website downloads:** in `server/config.json` set `"releasesRepo": "LoyaltyVal/Astra---Minecraft-Client"`.
   The site then offers Windows, macOS and Linux downloads and picks the visitor's OS.

## Every release

Nothing to do: **push to `main`** (GitHub Desktop: Commit, then Push origin). Every push that changes
the client or the launcher makes a release by itself. Pushes that only change docs, the website,
the server or the `.bat` files don't.

- The version is `launcher/package.json`'s plus the build number (1.0.1 → 1.0.2, 1.0.3, ...), so each
  release is newer than the last. To jump to a new version yourself, push a tag like `v1.2.0`
  (GitHub Desktop: Repository → Create tag, then Push), or raise the version in `package.json`.
- You can also start one by hand: **Actions → Release → Run workflow**.
- It takes about 15-20 minutes (watch the **Actions** tab). When it's green, the release is public
  and launchers start updating.
- Versions still being ported (1.20.4, 1.21.x) are built too when they can be; one that doesn't
  build yet shows a warning and is left out, without stopping the release.

## Code signing (optional, removes warnings over time)

Signing turns on by itself once the secrets exist (Settings → Secrets → Actions → **Secrets**):

- **Windows, Azure Artifact Signing** (~$9.99/month; individuals in the US and Canada):
  `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `ASTRA_AZURE_ENDPOINT`,
  `ASTRA_AZURE_ACCOUNT`, `ASTRA_AZURE_PROFILE`, `ASTRA_PUBLISHER_NAME` (the name on the certificate).
- **Windows, a .pfx certificate** instead: `WIN_CSC_LINK` (base64 of the .pfx), `WIN_CSC_KEY_PASSWORD`.
- **macOS** (Apple Developer Program, $99/year): `MAC_CSC_LINK`, `MAC_CSC_KEY_PASSWORD`,
  `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`.

Signing doesn't stop SmartScreen on day one: warnings fade as signed downloads build reputation.
Keep signing with the same identity so that reputation carries over between versions.

## Without signing

- **Windows:** players click *More info → Run anyway* once. Updates still install automatically.
- **macOS:** players open it via *System Settings → Privacy & Security → Open Anyway* once.
  Unsigned Macs can't install launcher updates themselves; the launcher shows "Astra x.y.z is out →
  Download" instead. The Astra mod itself still updates on every launch.
- **Linux (AppImage):** no signing needed. Updates install automatically.

## Building on your own Mac or Linux PC

`sh astra.sh installer` builds the launcher for the OS you're on (Mac: `.dmg` + `.zip` for Intel
and Apple Silicon; Linux: AppImage) into `launcher/dist`, and copies it to `server/downloads` for
the website. A Mac build can only be made on a Mac; GitHub's release builds all three.
