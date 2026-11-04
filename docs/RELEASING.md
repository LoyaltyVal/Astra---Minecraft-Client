# Releasing Astra

Releases are built on GitHub's machines for Windows, macOS and Linux. Installed launchers update
themselves from the newest release.

## One-time setup

1. **Workflow:** double-click `Set Up GitHub Releases.bat` (it copies `tools/github/release.yml` into
   `.github/workflows/`), then commit and push it.
2. **Repo access:** players' launchers read releases without signing in, so the repo has to be
   **public**. If you want the code private, make a second, public repo just for releases, and put
   its name in `launcher/package.json` → `astraUpdates`.
3. **Server address:** GitHub repo → Settings → Secrets and variables → Actions → **Variables** →
   add `ASTRA_SERVER_URL` = your permanent address (e.g. `https://yourname.duckdns.org`).
4. **Website downloads:** in `server/config.json` set `"releasesRepo": "LoyaltyVal/Astra---Minecraft-Client"`.
   The site then offers Windows, macOS and Linux downloads and picks the visitor's OS.

## Every release

1. Bump `"version"` in `launcher/package.json` (e.g. `1.0.1`) and commit.
2. Tag and push: `git tag v1.0.1` then `git push origin v1.0.1`
   (GitHub Desktop: Repository → Create tag, then Push).
3. Watch the **Actions** tab (about 15 minutes). When it's green, the release is public and
   launchers start updating.

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
  Unsigned Macs can't install updates themselves; the launcher shows "Astra x.y.z is out →
  Download" instead.
- **Linux (AppImage):** no signing needed. Updates install automatically.
