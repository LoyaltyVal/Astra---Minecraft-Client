const path = require('path');
const { app } = require('electron');

/**
 * Everything the launcher downloads lives in %APPDATA%/.astra so it never touches
 * your normal .minecraft folder:
 *
 *   .astra/
 *     assets/ libraries/ versions/   shared game files (managed by minecraft-launcher-core)
 *     runtime/jre-legacy/            Java 8 downloaded from Mojang
 *     instances/1.8.9/               game folder for 1.8.9 (saves, options, mods, config)
 *     instances/1.7.10/              game folder for 1.7.10
 */
function root() {
  return path.join(app.getPath('appData'), '.astra');
}

function runtimeDir(component) {
  return path.join(root(), 'runtime', component);
}

function instanceDir(versionId) {
  return path.join(root(), 'instances', versionId);
}

function versionsDir() {
  return path.join(root(), 'versions');
}

/** Where the built Astra mod jars are found (bundled in the installer, or next to the repo in dev). */
function bundledModsDir() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'mods')
    : path.join(__dirname, '..', '..', 'mods');
}

/** The Gradle project root (one folder above /launcher), used to find fresh jars during development. */
function gradleProjectDir() {
  return path.join(__dirname, '..', '..', '..');
}

module.exports = { root, runtimeDir, instanceDir, versionsDir, bundledModsDir, gradleProjectDir };
