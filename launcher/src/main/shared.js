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

/** Keybinds that exist in 1.7/1.8 and in modern versions (same names, different value format). */
const PORTABLE_KEYS = [
  'attack', 'use', 'forward', 'left', 'back', 'right', 'jump', 'sneak', 'sprint', 'drop', 'inventory',
  'chat', 'playerlist', 'pickItem', 'command', 'screenshot', 'togglePerspective', 'smoothCamera',
  'fullscreen', 'spectatorOutlines', 'hotbar.1', 'hotbar.2', 'hotbar.3', 'hotbar.4', 'hotbar.5',
  'hotbar.6', 'hotbar.7', 'hotbar.8', 'hotbar.9'
].map((k) => `key_key.${k}`);

/** Modern key names (GLFW) → LWJGL 2 key codes used by 1.7/1.8. */
const KEYBOARD = (() => {
  const m = {
    space: 57, 'left.shift': 42, 'right.shift': 54, 'left.control': 29, 'right.control': 157,
    'left.alt': 56, 'right.alt': 184, tab: 15, enter: 28, escape: 1, backspace: 14, 'grave.accent': 41,
    minus: 12, equal: 13, 'left.bracket': 26, 'right.bracket': 27, backslash: 43, semicolon: 39,
    apostrophe: 40, comma: 51, period: 52, slash: 53, 'caps.lock': 58, up: 200, down: 208, left: 203,
    right: 205, insert: 210, delete: 211, home: 199, end: 207, 'page.up': 201, 'page.down': 209,
    'keypad.add': 78, 'keypad.subtract': 74, 'keypad.multiply': 55, 'keypad.divide': 181,
    'keypad.decimal': 83, 'keypad.enter': 156, 'left.win': 219, 'right.win': 220, menu: 221
  };
  'qwertyuiop'.split('').forEach((c, i) => { m[c] = 16 + i; });
  'asdfghjkl'.split('').forEach((c, i) => { m[c] = 30 + i; });
  'zxcvbnm'.split('').forEach((c, i) => { m[c] = 44 + i; });
  for (let d = 1; d <= 9; d++) m[String(d)] = 1 + d;
  m['0'] = 11;
  for (let f = 1; f <= 10; f++) m[`f${f}`] = 58 + f;
  m.f11 = 87; m.f12 = 88; m.f13 = 100; m.f14 = 101; m.f15 = 102;
  [82, 79, 80, 81, 75, 76, 77, 71, 72, 73].forEach((code, n) => { m[`keypad.${n}`] = code; });
  return m;
})();
const KEYBOARD_BACK = Object.fromEntries(Object.entries(KEYBOARD).map(([k, v]) => [v, k]));

/** "key.keyboard.left.shift" → 42, "key.mouse.4" → -97, unknown → 0. Returns null if not understood. */
function keyToLegacy(value) {
  if (value === 'key.keyboard.unknown') return '0';
  const mouse = /^key\.mouse\.(left|right|middle|\d+)$/.exec(value);
  if (mouse) {
    const n = { left: 1, right: 2, middle: 3 }[mouse[1]] || Number(mouse[1]);
    return String(-100 + n - 1);
  }
  const kb = /^key\.keyboard\.(.+)$/.exec(value);
  if (kb && KEYBOARD[kb[1]] !== undefined) return String(KEYBOARD[kb[1]]);
  return null;
}

/** 42 → "key.keyboard.left.shift", -97 → "key.mouse.4". Returns null if there's no modern name. */
function keyToModern(value) {
  const code = Number(value);
  if (!Number.isInteger(code)) return null;
  if (code === 0) return 'key.keyboard.unknown';
  if (code < 0) {
    const n = code + 100 + 1;
    return `key.mouse.${({ 1: 'left', 2: 'right', 3: 'middle' })[n] || n}`;
  }
  return KEYBOARD_BACK[code] ? `key.keyboard.${KEYBOARD_BACK[code]}` : null;
}

/** Values that need fixing up on the way in (1.8 has smaller ranges than modern versions). */
function toLegacyValue(key, value) {
  if (key === 'renderDistance') return String(Math.max(2, Math.min(16, Number(value) || 8)));
  if (key === 'guiScale') return String(Math.max(0, Math.min(3, Number(value) || 0)));
  if (key === 'maxFps') return String(Math.max(10, Math.min(260, Number(value) || 120)));
  return value;
}

/** ...and on the way out (Fullbright sets gamma far above what modern versions accept). */
function toModernValue(key, value) {
  if (key === 'gamma') return String(Math.max(0, Math.min(1, Number(value) || 0)));
  return value;
}

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

/**
 * Copies the settings both formats understand. Going out, only `onlyKeys` (the settings that
 * changed while playing) are written, so Astra never overwrites something you changed in the
 * official game since.
 */
function copyPortable(from, to, direction, onlyKeys) {
  for (const [key, value] of from) {
    if (onlyKeys && !onlyKeys.has(key)) continue;
    let v = value;
    if (PORTABLE_KEYS.includes(key)) {
      v = direction === 'in' ? keyToLegacy(value) : keyToModern(value);
      if (v === null) continue;
      to.set(key, v);
      continue;
    }
    if (!PORTABLE_OPTIONS.has(key)) continue;
    if (key === 'resourcePacks' || key === 'incompatibleResourcePacks') {
      v = direction === 'in' ? packsToLegacy(value) : packsToModern(value, to.get(key) || '["vanilla"]');
      if (v === null) continue;
    } else {
      v = direction === 'in' ? toLegacyValue(key, value) : toModernValue(key, value);
    }
    to.set(key, v);
  }
}

const SNAPSHOT = '.astra-options-snapshot.json';

/** Remembers the settings as they were at launch, to tell later which ones changed while playing. */
function writeSnapshot(instanceDir, map) {
  fs.writeFileSync(path.join(instanceDir, SNAPSHOT), JSON.stringify(Object.fromEntries(map)));
}

function changedSince(instanceDir, map) {
  let before = {};
  try {
    before = JSON.parse(fs.readFileSync(path.join(instanceDir, SNAPSHOT), 'utf8'));
  } catch {
    return new Set(map.keys()); // no snapshot: treat everything as changed
  }
  return new Set([...map.keys()].filter((k) => before[k] !== map.get(k)));
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
      log('[Astra] Using your keybinds, video, sound and resource pack settings from .minecraft.');
    }
    writeSnapshot(instanceDir, readOptions(target));
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
      const changed = changedSince(instanceDir, mine);
      if (changed.size) {
        copyPortable(mine, mcOptions, 'out', changed);
        writeOptions(mcFile, mcOptions);
      }
    }
  } catch (e) {
    log(`[Astra] Couldn't save settings to .minecraft: ${e.message}`);
  }
}

module.exports = { prepare, save, minecraftDir, keyToLegacy, keyToModern };
