const fs = require('fs');
const path = require('path');
const { app } = require('electron');

/**
 * Shares settings, resource packs, screenshots and the server list with the normal .minecraft
 * folder, so Astra uses the same ones as the official launcher.
 *
 * Astra still runs from its own instance folder (so its mods never end up in .minecraft/mods),
 * but inside it:
 *   resourcepacks/, screenshots/   are links to the folders in .minecraft (one copy, always shared)
 *   options.txt, servers.dat       are copied in from .minecraft before launch and copied back
 *                                  after the game closes
 *
 * options.txt needs care: newer Minecraft versions write a different format (keybinds, language
 * codes). If .minecraft's options.txt is from a newer version, only settings that mean the same
 * thing in both formats (FOV, brightness, render distance, volume, ...) are shared, so the
 * official game's own settings never get broken.
 */

const LINKED_FOLDERS = ['resourcepacks', 'screenshots'];

/** Settings that are written the same way by 1.7/1.8 and by modern versions. */
const PORTABLE_OPTIONS = new Set([
  'fov', 'gamma', 'renderDistance', 'guiScale', 'maxFps', 'mouseSensitivity', 'invertYMouse',
  'bobView', 'particles', 'enableVsync', 'chatOpacity', 'chatScale', 'chatWidth', 'chatColors',
  'chatLinks', 'chatLinksPrompt', 'autoJump', 'resourcePacks', 'incompatibleResourcePacks',
  'soundCategory_master', 'soundCategory_music', 'soundCategory_record', 'soundCategory_weather',
  'soundCategory_block', 'soundCategory_hostile', 'soundCategory_neutral', 'soundCategory_player',
  'soundCategory_ambient'
]);

function minecraftDir() {
  return path.join(app.getPath('appData'), '.minecraft');
}

function readOptions(file) {
  const map = new Map();
  if (!fs.existsSync(file)) return map;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const i = line.indexOf(':');
    if (i > 0) map.set(line.slice(0, i), line.slice(i + 1));
  }
  return map;
}

function writeOptions(file, map) {
  fs.writeFileSync(file, [...map].map(([k, v]) => `${k}:${v}`).join('\n') + '\n');
}

/** 1.9+ writes "version:<number>" at the top of options.txt; 1.7/1.8 don't. */
function isModernOptions(map) {
  return map.has('version');
}

/**
 * The resource pack list: 1.8 stores `["MyPack.zip"]`, modern versions `["vanilla","file/MyPack.zip"]`.
 * Converts between the two so the same packs stay selected in both.
 */
function packsToLegacy(value) {
  try {
    return JSON.stringify(JSON.parse(value).filter((p) => p.startsWith('file/')).map((p) => p.slice(5)));
  } catch {
    return null;
  }
}

function packsToModern(value, current) {
  try {
    const packs = JSON.parse(value).map((p) => `file/${p}`);
    const builtIn = (() => {
      try {
        return JSON.parse(current).filter((p) => !p.startsWith('file/'));
      } catch {
        return ['vanilla'];
      }
    })();
    return JSON.stringify([...builtIn, ...packs]);
  } catch {
    return null;
  }
}

function copyPortable(from, to, direction) {
  for (const [key, value] of from) {
    if (!PORTABLE_OPTIONS.has(key)) continue;
    let v = value;
    if (key === 'resourcePacks' || key === 'incompatibleResourcePacks') {
      v = direction === 'in' ? packsToLegacy(value) : packsToModern(value, to.get(key) || '["vanilla"]');
      if (v === null) continue;
    }
    to.set(key, v);
  }
}

/** Makes `link` a link to `target`, moving anything already in `link` into `target` first. */
function linkFolder(link, target, log) {
  fs.mkdirSync(target, { recursive: true });
  let stat = null;
  try {
    stat = fs.lstatSync(link);
  } catch {
    // doesn't exist yet
  }
  if (stat && stat.isSymbolicLink()) {
    if (path.resolve(path.dirname(link), fs.readlinkSync(link)) === path.resolve(target)) return;
    fs.unlinkSync(link);
  } else if (stat && stat.isDirectory()) {
    for (const name of fs.readdirSync(link)) {
      const dest = path.join(target, name);
      if (!fs.existsSync(dest)) fs.renameSync(path.join(link, name), dest);
    }
    if (fs.readdirSync(link).length) {
      // Name clashes: keep Astra's copies aside rather than losing them.
      fs.renameSync(link, `${link}-astra-${Date.now()}`);
    } else {
      fs.rmdirSync(link);
    }
  } else if (stat) {
    return log(`[Astra] ${link} isn't a folder; not sharing it with .minecraft.`);
  }
  // A junction works on Windows without admin rights; elsewhere this is a normal symlink.
  fs.symlinkSync(target, link, 'junction');
}

/** Before launch: link the shared folders and copy settings + server list in from .minecraft. */
function prepare(instanceDir, log) {
  const mc = minecraftDir();
  if (!fs.existsSync(mc)) {
    log('[Astra] No .minecraft folder found, so nothing is shared with it.');
    return;
  }
  for (const name of LINKED_FOLDERS) {
    try {
      linkFolder(path.join(instanceDir, name), path.join(mc, name), log);
    } catch (e) {
      log(`[Astra] Couldn't share ${name} with .minecraft: ${e.message}`);
    }
  }
  try {
    const servers = path.join(mc, 'servers.dat');
    if (fs.existsSync(servers)) fs.copyFileSync(servers, path.join(instanceDir, 'servers.dat'));
  } catch (e) {
    log(`[Astra] Couldn't copy the server list from .minecraft: ${e.message}`);
  }
  try {
    const mcOptions = readOptions(path.join(mc, 'options.txt'));
    if (!mcOptions.size) return;
    const target = path.join(instanceDir, 'options.txt');
    if (!isModernOptions(mcOptions)) {
      fs.copyFileSync(path.join(mc, 'options.txt'), target);
      log('[Astra] Using your settings from .minecraft.');
    } else {
      const mine = readOptions(target);
      copyPortable(mcOptions, mine, 'in');
      writeOptions(target, mine);
      log('[Astra] Using your video, sound and resource pack settings from .minecraft.');
    }
  } catch (e) {
    log(`[Astra] Couldn't read settings from .minecraft: ${e.message}`);
  }
}

/** After the game closes: copy settings and the server list back to .minecraft. */
function save(instanceDir, log) {
  const mc = minecraftDir();
  if (!fs.existsSync(mc)) return;
  try {
    const servers = path.join(instanceDir, 'servers.dat');
    if (fs.existsSync(servers)) fs.copyFileSync(servers, path.join(mc, 'servers.dat'));
  } catch (e) {
    log(`[Astra] Couldn't save the server list to .minecraft: ${e.message}`);
  }
  try {
    const mine = readOptions(path.join(instanceDir, 'options.txt'));
    if (!mine.size) return;
    const mcFile = path.join(mc, 'options.txt');
    const mcOptions = readOptions(mcFile);
    if (!mcOptions.size || !isModernOptions(mcOptions)) {
      fs.copyFileSync(path.join(instanceDir, 'options.txt'), mcFile);
    } else {
      copyPortable(mine, mcOptions, 'out');
      writeOptions(mcFile, mcOptions);
    }
  } catch (e) {
    log(`[Astra] Couldn't save settings to .minecraft: ${e.message}`);
  }
}

module.exports = { prepare, save, minecraftDir };
