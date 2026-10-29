const fs = require('fs');
const path = require('path');
const { JAVA_RUNTIME_INDEX, VERSION_MANIFEST, fallbackJavaComponent } = require('./config');
const { fetchJson, downloadFile, runPool } = require('./download');
const paths = require('./paths');

/** Mojang's platform keys in the Java runtime index. */
function platformKey() {
  const { platform, arch } = process;
  if (platform === 'win32') return arch === 'ia32' ? 'windows-x86' : arch === 'arm64' ? 'windows-arm64' : 'windows-x64';
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return arch === 'ia32' ? 'linux-i386' : 'linux';
}

function javaExecutable(dir) {
  if (process.platform === 'win32') return path.join(dir, 'bin', 'javaw.exe');
  if (process.platform === 'darwin') return path.join(dir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(dir, 'bin', 'java');
}

/** Friendly names for Mojang's runtime components. */
const JAVA_NAMES = {
  'jre-legacy': 'Java 8',
  'java-runtime-alpha': 'Java 16',
  'java-runtime-beta': 'Java 17',
  'java-runtime-gamma': 'Java 17',
  'java-runtime-gamma-snapshot': 'Java 17',
  'java-runtime-delta': 'Java 21',
  'java-runtime-epsilon': 'Java 25'
};

function javaName(component) {
  return JAVA_NAMES[component] || 'Java';
}

/** Cached answers from Mojang: { "<version>": "<component>" }. */
function componentCacheFile() {
  return path.join(paths.root(), 'astra-java-components.json');
}

function readCache() {
  try {
    return JSON.parse(fs.readFileSync(componentCacheFile(), 'utf8'));
  } catch {
    return {};
  }
}

/**
 * Which Java runtime a Minecraft version needs, as Mojang's version JSON says
 * (javaVersion.component, e.g. jre-legacy for 1.8.9, java-runtime-delta for 1.21).
 * Cached, and falls back to a built-in table when Mojang can't be reached.
 */
async function javaComponentFor(versionId) {
  const cache = readCache();
  if (cache[versionId]) return cache[versionId];
  try {
    const manifest = await fetchJson(VERSION_MANIFEST);
    const entry = (manifest.versions || []).find((v) => v.id === versionId);
    if (!entry) return fallbackJavaComponent(versionId);
    const json = await fetchJson(entry.url);
    const component = (json.javaVersion && json.javaVersion.component) || 'jre-legacy';
    cache[versionId] = component;
    fs.mkdirSync(paths.root(), { recursive: true });
    fs.writeFileSync(componentCacheFile(), JSON.stringify(cache, null, 2));
    return component;
  } catch {
    return fallbackJavaComponent(versionId);
  }
}

/**
 * Makes sure Mojang's Java runtime for `component` (e.g. jre-legacy = Java 8) is installed
 * and returns the path to its java executable.
 */
async function ensureJava(component, onProgress) {
  const dir = paths.runtimeDir(component);
  const exe = javaExecutable(dir);
  const marker = path.join(dir, '.astra-installed');

  if (fs.existsSync(exe) && fs.existsSync(marker)) {
    return exe;
  }

  const name = javaName(component);
  onProgress({ label: `Finding ${name}`, current: 0, total: 1 });
  let index;
  try {
    index = await fetchJson(JAVA_RUNTIME_INDEX);
  } catch (e) {
    throw new Error(`Couldn't reach Mojang to download Java (${e.message}). Check your internet, or set a Java path in Settings.`);
  }

  // Mojang has no Java 8 build for Apple Silicon; the Intel one runs under Rosetta.
  let platform = index[platformKey()];
  if ((!platform || !platform[component] || !platform[component].length) && platformKey() === 'mac-os-arm64') {
    platform = index['mac-os'];
  }
  const entry = platform && platform[component] && platform[component][0];
  if (!entry) {
    throw new Error(`Mojang doesn't provide ${component} for ${platformKey()}. Set a Java path in Settings.`);
  }

  const manifest = await fetchJson(entry.manifest.url);
  const files = Object.entries(manifest.files);

  // Directories first, then files in parallel.
  for (const [rel, info] of files) {
    if (info.type === 'directory') {
      fs.mkdirSync(path.join(dir, rel), { recursive: true });
    }
  }

  const jobs = files
    .filter(([, info]) => info.type === 'file')
    .map(([rel, info]) => async () => {
      const dest = path.join(dir, rel);
      await downloadFile(info.downloads.raw.url, dest, info.downloads.raw.sha1);
      if (info.executable && process.platform !== 'win32') {
        fs.chmodSync(dest, 0o755);
      }
    });

  await runPool(jobs, 8, (done, total) =>
    onProgress({ label: `Downloading ${name} (${entry.version.name})`, current: done, total })
  );

  if (process.platform !== 'win32') {
    for (const [rel, info] of files) {
      if (info.type === 'link') {
        const link = path.join(dir, rel);
        try {
          fs.rmSync(link, { force: true });
          fs.symlinkSync(info.target, link);
        } catch {
          // a missing symlink is not fatal for launching
        }
      }
    }
  }

  if (!fs.existsSync(exe)) {
    throw new Error('Java downloaded, but the executable is missing. Try again, or set a Java path in Settings.');
  }
  fs.writeFileSync(marker, entry.version.name);
  return exe;
}

module.exports = { ensureJava, javaComponentFor, javaName, platformKey, javaExecutable };
