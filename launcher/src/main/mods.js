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
function availableVersions({ packaged, bundledDir, gradleDir }) {
  const ids = new Set(KNOWN_VERSIONS);
  for (const name of listDirs(bundledDir)) ids.add(name);
  if (!packaged) for (const name of listDirs(path.join(gradleDir, 'versions'))) ids.add(name);
  return [...ids]
    .filter((id) => /^\d+(\.\d+){1,2}$/.test(id))
    .filter((id) => findModJar(id, { packaged, bundledDir, gradleDir }))
    .sort((a, b) => compareVersions(b, a))
    .map(describe);
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
function installMod(gameVersion, log) {
  const { app } = require('electron');
  const source = findModJar(gameVersion, {
    packaged: app.isPackaged,
    bundledDir: paths.bundledModsDir(),
    gradleDir: paths.gradleProjectDir()
  });
  if (!source) {
    throw new Error(
      `Astra for ${gameVersion} isn't built yet. Run "gradlew build" in the project folder, then launch again.`
    );
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

  const modsDir = path.join(paths.instanceDir(gameVersion), 'mods');
  fs.mkdirSync(modsDir, { recursive: true });

  // Remove stale Astra jars with other names (other mods the player added are left alone).
  for (const name of fs.readdirSync(modsDir)) {
    if (name !== MOD_FILE && /^astra[-.].*\.jar$/i.test(name)) {
      fs.rmSync(path.join(modsDir, name), { force: true });
    }
  }

  fs.copyFileSync(source, path.join(modsDir, MOD_FILE));
  log(`[Astra] Installed ${path.basename(source)} into ${modsDir}`);
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

module.exports = { installMod, findModJar, isReleaseJar, availableVersions, ensureFabricApi };
