const fs = require('fs');
const path = require('path');
const paths = require('./paths');
const { KNOWN_VERSIONS, MODRINTH_API, compareVersions, describe } = require('./config');
const { fetchJson, downloadFile } = require('./download');

const MOD_FILE = 'astra.jar';

/** Matches the Gradle output, e.g. astra-0.1.0+1.8.9.jar (not -sources / -dev jars). */
function isReleaseJar(name, gameVersion) {
  return name.startsWith('astra-') && name.endsWith(`+${gameVersion}.jar`);
}

/**
 * Finds the Astra jar for a version:
 *  1. In development, the freshest jar from versions/<mc>/build/libs (so `gradlew build` is enough).
 *  2. The copy bundled with the launcher (launcher/mods/<mc>/astra.jar, packed into the installer).
 */
function findModJar(gameVersion, { packaged, bundledDir, gradleDir }) {
  const candidates = [];

  if (!packaged) {
    const libs = path.join(gradleDir, 'versions', gameVersion, 'build', 'libs');
    try {
      for (const name of fs.readdirSync(libs)) {
        if (isReleaseJar(name, gameVersion)) {
          const file = path.join(libs, name);
          candidates.push({ file, mtime: fs.statSync(file).mtimeMs });
        }
      }
    } catch {
      // not built yet
    }
  }

  const bundled = path.join(bundledDir, gameVersion, MOD_FILE);
  if (fs.existsSync(bundled)) {
    candidates.push({ file: bundled, mtime: packaged ? Infinity : fs.statSync(bundled).mtimeMs });
  }

  candidates.sort((a, b) => b.mtime - a.mtime);
  return candidates.length ? candidates[0].file : null;
}

function listFiles(dir) {
  try {
    return fs.readdirSync(dir);
  } catch {
    return [];
  }
}

function listDirs(dir) {
  try {
    return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name);
  } catch {
    return [];
  }
}

/**
 * The versions that have an Astra build (newest first): every known release plus any other
 * version folder found in the project or the installer, so a new port shows up on its own.
 */
function availableVersions({ packaged, bundledDir, gradleDir }, serverVersions) {
  const ids = new Set(KNOWN_VERSIONS);
  for (const name of listDirs(bundledDir)) ids.add(name);
  if (!packaged) for (const name of listDirs(path.join(gradleDir, 'versions'))) ids.add(name);
  const server = new Set(serverVersions || []);
  for (const id of server) ids.add(id);
  return [...ids]
    .filter((id) => /^\d+(\.\d+){1,2}$/.test(id))
    .sort((a, b) => compareVersions(b, a))
    .map((id) => ({
      ...describe(id),
      // Every version can be played; "astra" says whether Astra's mods come with it yet.
      astra: server.has(id) || Boolean(findModJar(id, { packaged, bundledDir, gradleDir }))
    }));
}

let serverListCache = { at: 0, versions: [] };

/** The versions the Astra server has builds for (cached for a few minutes; empty when offline). */
async function serverVersions() {
  if (Date.now() - serverListCache.at < 5 * 60 * 1000) return serverListCache.versions;
  let base;
  try {
    base = require('./astraServer').serverUrl();
  } catch {
    return [];
  }
  if (!/^https?:\/\//.test(base || '')) return [];
  try {
    const res = await fetch(`${base}/api/client/mods`, { signal: AbortSignal.timeout(3000), headers: { 'User-Agent': 'AstraLauncher' } });
    if (!res.ok) return serverListCache.versions;
    const versions = Object.keys((await res.json()).mods || {});
    serverListCache = { at: Date.now(), versions };
    return versions;
  } catch {
    return serverListCache.versions;
  }
}

/** The newest modification time of any file under `dir` (0 if it doesn't exist). */
function newestFile(dir) {
  let newest = 0;
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return 0;
  }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    newest = Math.max(newest, e.isDirectory() ? newestFile(full) : fs.statSync(full).mtimeMs);
  }
  return newest;
}

/** Copies the Astra jar into instances/<mc>/mods/astra.jar, replacing any older Astra jar. */
/**
 * The newest Astra build for a version from the Astra server (the host puts it there every time
 * they build), downloaded once into .astra/client-mods and checked by sha1. This is how players get
 * client fixes without installing a new launcher. Returns null when the server has none or can't
 * be reached (the copy bundled with the launcher is used then).
 */
async function serverModJar(gameVersion, log) {
  let base;
  try {
    base = require('./astraServer').serverUrl();
  } catch {
    return null;
  }
  if (!/^https?:\/\//.test(base || '')) return null;
  let entry;
  try {
    const res = await fetch(`${base}/api/client/mods`, { signal: AbortSignal.timeout(4000), headers: { 'User-Agent': 'AstraLauncher' } });
    if (!res.ok) return null;
    entry = ((await res.json()).mods || {})[gameVersion];
  } catch {
    return null;
  }
  if (!entry || !/^[0-9a-f]{40}$/.test(String(entry.sha1))) return null;
  const dest = path.join(paths.root(), 'client-mods', gameVersion, `astra-${entry.sha1}.jar`);
  try {
    await downloadFile(`${base}/api/client/mods/${encodeURIComponent(gameVersion)}/astra.jar`, dest, entry.sha1);
  } catch (e) {
    log(`[Astra] Couldn't download the newest Astra from the server (${e.message}); using the one in the app.`);
    return null;
  }
  // Keep only this build and the one before it.
  try {
    const dir = path.dirname(dest);
    const old = fs.readdirSync(dir).filter((n) => n !== path.basename(dest))
      .map((n) => ({ n, t: fs.statSync(path.join(dir, n)).mtimeMs })).sort((a, b) => b.t - a.t).slice(1);
    for (const o of old) fs.rmSync(path.join(dir, o.n), { force: true });
  } catch {
    // tidy-up only
  }
  return dest;
}

/**
 * Puts the Astra jar for a version into the instance's mods folder: the newest build from the
 * Astra server when there is one (installed launchers), else the copy in the app (or, from the
 * project folder, the fresh Gradle build).
 */
async function installMod(gameVersion, log) {
  const { app } = require('electron');
  const fromServer = app.isPackaged ? await serverModJar(gameVersion, log) : null;
  const source = fromServer || findModJar(gameVersion, {
    packaged: app.isPackaged,
    bundledDir: paths.bundledModsDir(),
    gradleDir: paths.gradleProjectDir()
  });
  const modsDir = path.join(paths.instanceDir(gameVersion), 'mods');
  if (!source) {
    // In the project folder, a version being ported must be built first.
    if (!app.isPackaged && fs.existsSync(path.join(paths.gradleProjectDir(), 'versions', gameVersion, 'src'))) {
      throw new Error(`Astra for ${gameVersion} isn't built yet. Run "gradlew build" in the project folder, then launch again.`);
    }
    // No Astra for this version yet: play it on plain Fabric, with no old Astra jar lying around.
    for (const name of listFiles(modsDir)) {
      if (/^astra([-.].*)?\.jar$/i.test(name)) fs.rmSync(path.join(modsDir, name), { force: true });
    }
    log(`[Astra] Astra isn't out for ${gameVersion} yet, so this launch is Minecraft ${gameVersion} on Fabric without Astra.`);
    return false;
  }

  // In development, refuse to launch an old build: code changes only take effect after a rebuild.
  if (!app.isPackaged) {
    const gradleDir = paths.gradleProjectDir();
    const sources = Math.max(
      newestFile(path.join(gradleDir, 'common', 'src')),
      newestFile(path.join(gradleDir, 'versions', gameVersion, 'src'))
    );
    if (sources > fs.statSync(source).mtimeMs + 2000) {
      throw new Error(
        `The Astra code changed since it was last built. Run "gradlew build" in the project folder, then launch again.`
      );
    }
  }

  fs.mkdirSync(modsDir, { recursive: true });

  // Remove stale Astra jars with other names (other mods the player added are left alone).
  for (const name of fs.readdirSync(modsDir)) {
    if (name !== MOD_FILE && /^astra[-.].*\.jar$/i.test(name)) {
      fs.rmSync(path.join(modsDir, name), { force: true });
    }
  }

  // Not copyFile: that keeps the source's permissions, and the jar inside a Linux AppImage (or a
  // read-only install) is read-only, so the next launch couldn't replace it (EACCES).
  const target = path.join(modsDir, MOD_FILE);
  try {
    fs.chmodSync(target, 0o644);
  } catch {
    // not there yet
  }
  fs.rmSync(target, { force: true });
  fs.writeFileSync(target, fs.readFileSync(source), { mode: 0o644 });
  log(`[Astra] Installed ${fromServer ? 'the newest Astra from the server' : path.basename(source)} into ${modsDir}`);
}

const FABRIC_API_FILE = 'fabric-api.jar';

/**
 * Modern Astra builds need Fabric API. Downloads the newest release for the game version from
 * Modrinth into the instance's mods folder (checked by sha1), replacing other Fabric API jars.
 * When Modrinth can't be reached, an already installed copy is kept.
 */
async function ensureFabricApi(gameVersion, onProgress, log) {
  const modsDir = path.join(paths.instanceDir(gameVersion), 'mods');
  const dest = path.join(modsDir, FABRIC_API_FILE);
  const marker = path.join(modsDir, '.astra-fabric-api.json');
  onProgress({ label: 'Checking Fabric API', current: 0, total: 1 });

  let file;
  try {
    const q = `loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}`;
    const versions = await fetchJson(`${MODRINTH_API}/project/fabric-api/version?${q}`);
    const pick = versions.find((v) => v.version_type === 'release') || versions[0];
    file = pick && (pick.files.find((f) => f.primary) || pick.files[0]);
    if (!file) throw new Error(`no Fabric API for ${gameVersion} yet`);
    file.versionName = pick.version_number;
  } catch (e) {
    if (fs.existsSync(dest)) {
      log(`[Astra] Couldn't check Fabric API (${e.message}); using the installed copy.`);
      return;
    }
    throw new Error(`Couldn't download Fabric API for ${gameVersion} (${e.message}).`);
  }

  fs.mkdirSync(modsDir, { recursive: true });
  // Other Fabric API jars (from earlier downloads or added by hand) would be loaded twice.
  for (const name of fs.readdirSync(modsDir)) {
    if (name !== FABRIC_API_FILE && /^fabric-api.*\.jar$/i.test(name)) {
      fs.rmSync(path.join(modsDir, name), { force: true });
    }
  }
  onProgress({ label: `Downloading Fabric API ${file.versionName}`, current: 0, total: 1 });
  const changed = await downloadFile(file.url, dest, file.hashes && file.hashes.sha1);
  fs.writeFileSync(marker, JSON.stringify({ version: file.versionName, gameVersion }, null, 2));
  if (changed) log(`[Astra] Installed Fabric API ${file.versionName}`);
  onProgress({ label: `Downloading Fabric API ${file.versionName}`, current: 1, total: 1 });
}

module.exports = { installMod, findModJar, isReleaseJar, availableVersions, serverVersions, ensureFabricApi };
