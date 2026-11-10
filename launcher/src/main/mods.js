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

/** Versions with an Astra build on the Astra server or in the newest GitHub release. */
async function serverVersions() {
  const [fromServer, fromGithub] = await Promise.all([astraServerVersions(), githubMods().then((m) => Object.keys(m ? m.mods : {}))]);
  return [...new Set([...fromServer, ...fromGithub])];
}

/** The versions the Astra server has builds for (cached for a few minutes; empty when offline). */
async function astraServerVersions() {
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

/** The GitHub repo releases come from (package.json "astraUpdates"), or null. */
function githubRepo() {
  try {
    const { owner, repo } = require('../../package.json').astraUpdates || {};
    return owner && repo ? { owner, repo } : null;
  } catch {
    return null;
  }
}

let githubCache = { at: 0, manifest: null };

/**
 * The list of Astra builds in the newest GitHub release (astra-mods.json, made by the release
 * workflow): { tag, mods: { "1.8.9": { file, sha1 } } }. Cached for 10 minutes; null when offline
 * or there's no release yet. Uses the plain download links, which aren't rate limited like the API.
 */
async function githubMods() {
  const gh = githubRepo();
  if (!gh) return null;
  if (Date.now() - githubCache.at < 10 * 60 * 1000) return githubCache.manifest;
  // The last list seen, kept on disk: offline, the builds already downloaded are still used.
  const saved = path.join(paths.root(), 'client-mods', 'github-mods.json');
  let manifest = githubCache.manifest;
  if (!manifest) {
    try {
      manifest = JSON.parse(fs.readFileSync(saved, 'utf8'));
    } catch {
      manifest = null;
    }
  }
  try {
    const res = await fetch(`https://github.com/${gh.owner}/${gh.repo}/releases/latest/download/astra-mods.json`, {
      signal: AbortSignal.timeout(6000), headers: { 'User-Agent': 'AstraLauncher' }
    });
    if (res.ok) {
      const json = await res.json();
      if (json && json.tag && json.mods && typeof json.mods === 'object') {
        manifest = json;
        fs.mkdirSync(path.dirname(saved), { recursive: true });
        fs.writeFileSync(saved, JSON.stringify(json));
      }
    } else if (res.status === 404) {
      manifest = null; // no release (with mods) yet
    }
  } catch {
    // offline: keep what we had
  }
  githubCache = { at: Date.now(), manifest };
  return manifest;
}

/**
 * The Astra build for a version from the newest GitHub release, downloaded once into
 * .astra/client-mods and checked by sha1. This keeps every launcher (Mac too, which can't install
 * launcher updates by itself) on the newest Astra even when the Astra server is offline.
 */
async function githubModJar(gameVersion, log) {
  const gh = githubRepo();
  const manifest = await githubMods();
  const entry = manifest && manifest.mods[gameVersion];
  if (!gh || !entry || !/^[0-9a-f]{40}$/.test(String(entry.sha1)) || !/^[\w.+-]+\.jar$/.test(String(entry.file))) return null;
  // The copy in the app is as new (it came with this release, or with an installer built later).
  const bundled = bundledInfo(gameVersion);
  if (entry.sha1 === bundled.sha1 || !(Number(entry.built) > bundled.built)) return null;
  const dest = path.join(paths.root(), 'client-mods', gameVersion, `astra-${entry.sha1}.jar`);
  try {
    const changed = await downloadFile(
      `https://github.com/${gh.owner}/${gh.repo}/releases/download/${encodeURIComponent(manifest.tag)}/${encodeURIComponent(entry.file)}`,
      dest, entry.sha1);
    if (changed) log(`[Astra] Updated Astra for ${gameVersion} from GitHub (${manifest.tag}).`);
  } catch (e) {
    log(`[Astra] Couldn't download the newest Astra from GitHub (${e.message}); using the one in the app.`);
    return null;
  }
  pruneOld(dest);
  return dest;
}

/** When the Astra jar bundled with the app was built (ms) and its sha1, from copy-mods.js. */
function bundledInfo(gameVersion) {
  try {
    const info = JSON.parse(fs.readFileSync(path.join(paths.bundledModsDir(), 'astra-build.json'), 'utf8'));
    return { built: Number((info.built || {})[gameVersion]) || 0, sha1: (info.sha1 || {})[gameVersion] || null };
  } catch {
    return { built: 0, sha1: null };
  }
}

/** Keeps only this build and the one before it in its client-mods folder. */
function pruneOld(dest) {
  try {
    const dir = path.dirname(dest);
    const old = fs.readdirSync(dir).filter((n) => n !== path.basename(dest))
      .map((n) => ({ n, t: fs.statSync(path.join(dir, n)).mtimeMs })).sort((a, b) => b.t - a.t).slice(1);
    for (const o of old) fs.rmSync(path.join(dir, o.n), { force: true });
  } catch {
    // tidy-up only
  }
}

/**
 * Puts the Astra jar for a version into the instance's mods folder. Installed launchers use, in
 * order: the newest build on the Astra server (the host's own builds), the newest GitHub release,
 * then the copy in the app. From the project folder it's the fresh Gradle build.
 */
async function installMod(gameVersion, log) {
  const { app } = require('electron');
  const fromServer = app.isPackaged ? await serverModJar(gameVersion, log) : null;
  const fromGithub = app.isPackaged && !fromServer ? await githubModJar(gameVersion, log) : null;
  const source = fromServer || fromGithub || findModJar(gameVersion, {
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

const SODIUM_FILE = 'sodium.jar';

/**
 * Sodium (a rendering mod for much higher FPS) for modern versions, from Modrinth, when the
 * "Sodium" setting is on. Unlike Fabric API it's optional: if Modrinth has no Sodium for this
 * version, or can't be reached with none installed yet, the game launches without it.
 * With the setting off, the copy the launcher put there is removed (one you added yourself stays).
 */
async function ensureSodium(gameVersion, enabled, onProgress, log) {
  const modsDir = path.join(paths.instanceDir(gameVersion), 'mods');
  const dest = path.join(modsDir, SODIUM_FILE);
  if (!enabled) {
    if (fs.existsSync(dest)) {
      fs.rmSync(dest, { force: true });
      log('[Astra] Sodium is off in Settings, so it was removed from this version.');
    }
    return;
  }
  onProgress({ label: 'Checking Sodium', current: 0, total: 1 });

  let file;
  try {
    const q = `loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}`;
    const versions = await fetchJson(`${MODRINTH_API}/project/sodium/version?${q}`);
    const pick = versions.find((v) => v.version_type === 'release') || versions[0];
    file = pick && (pick.files.find((f) => f.primary) || pick.files[0]);
    if (!file) {
      fs.rmSync(dest, { force: true });
      log(`[Astra] Sodium isn't out for ${gameVersion}, so this launch runs without it.`);
      return;
    }
    file.versionName = pick.version_number;
  } catch (e) {
    log(`[Astra] Couldn't check Sodium (${e.message}); ${fs.existsSync(dest) ? 'using the installed copy' : 'launching without it'}.`);
    return;
  }

  fs.mkdirSync(modsDir, { recursive: true });
  // Two Sodium jars (an older download, or one added by hand) would stop the game from starting.
  for (const name of fs.readdirSync(modsDir)) {
    if (name !== SODIUM_FILE && /^sodium.*\.jar$/i.test(name)) {
      fs.rmSync(path.join(modsDir, name), { force: true });
      log(`[Astra] Removed ${name} (the launcher keeps Sodium up to date itself).`);
    }
  }
  try {
    onProgress({ label: `Downloading Sodium ${file.versionName}`, current: 0, total: 1 });
    const changed = await downloadFile(file.url, dest, file.hashes && file.hashes.sha1);
    if (changed) log(`[Astra] Installed Sodium ${file.versionName}`);
  } catch (e) {
    log(`[Astra] Couldn't download Sodium (${e.message}); ${fs.existsSync(dest) ? 'using the installed copy' : 'launching without it'}.`);
  }
  onProgress({ label: 'Sodium ready', current: 1, total: 1 });
}

module.exports = { installMod, findModJar, isReleaseJar, availableVersions, serverVersions, ensureFabricApi, ensureSodium };
