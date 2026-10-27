const fs = require('fs');
const path = require('path');
const paths = require('./paths');

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

module.exports = { installMod, findModJar, isReleaseJar };
