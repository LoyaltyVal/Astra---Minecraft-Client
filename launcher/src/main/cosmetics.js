const fs = require('fs');
const path = require('path');
const { app, nativeImage, shell } = require('electron');
const paths = require('./paths');

/**
 * Capes and wings live in %APPDATA%/.astra/cosmetics, shared by the launcher and every game
 * version (the game gets -Dastra.cosmetics=<folder>). equipped.json is the single source of
 * truth for what's equipped; the game re-reads it every couple of seconds.
 */

const TYPES = { cape: 'capes', wings: 'wings' };
const IMAGE = /\.(png|jpe?g)$/i;

function root() {
  return path.join(paths.root(), 'cosmetics');
}

function folder(type) {
  const name = TYPES[type];
  if (!name) throw new Error(`Unknown cosmetic type: ${type}`);
  return path.join(root(), name);
}

function equippedFile() {
  return path.join(root(), 'equipped.json');
}

/** On first run, copies the starter capes and wings that ship with the launcher. */
function seedDefaults() {
  const marker = path.join(root(), '.seeded');
  for (const type of Object.keys(TYPES)) fs.mkdirSync(folder(type), { recursive: true });
  if (fs.existsSync(marker)) return;

  const bundled = app.isPackaged
    ? path.join(process.resourcesPath, 'cosmetics')
    : path.join(__dirname, '..', '..', 'resources', 'cosmetics');
  for (const type of Object.keys(TYPES)) {
    const from = path.join(bundled, TYPES[type]);
    if (!fs.existsSync(from)) continue;
    for (const name of fs.readdirSync(from)) {
      const dest = path.join(folder(type), name);
      if (IMAGE.test(name) && !fs.existsSync(dest)) fs.copyFileSync(path.join(from, name), dest);
    }
  }
  fs.writeFileSync(marker, new Date().toISOString());
}

function readEquipped() {
  try {
    const data = JSON.parse(fs.readFileSync(equippedFile(), 'utf8'));
    return { cape: data.cape || null, wings: data.wings || null };
  } catch {
    return { cape: null, wings: null };
  }
}

function writeEquipped(equipped) {
  fs.mkdirSync(root(), { recursive: true });
  fs.writeFileSync(equippedFile(), JSON.stringify({ cape: equipped.cape, wings: equipped.wings }));
}

/** A small preview: capes show the visible face (cropped for 64x32-style templates). */
function thumbnail(type, file) {
  let img = nativeImage.createFromPath(file);
  if (img.isEmpty()) return null;
  const { width, height } = img.getSize();
  if (type === 'cape' && width === height * 2 && width % 64 === 0) {
    const u = width / 64;
    img = img.crop({ x: u, y: u, width: 10 * u, height: 16 * u });
  }
  const size = img.getSize();
  const scale = Math.min(1, 240 / Math.max(size.width, size.height));
  if (scale < 1) {
    img = img.resize({ width: Math.round(size.width * scale), height: Math.round(size.height * scale), quality: 'good' });
  }
  return img.toDataURL();
}

function list() {
  seedDefaults();
  const equipped = readEquipped();
  const out = { equipped, folder: root(), cape: [], wings: [] };
  for (const type of Object.keys(TYPES)) {
    const dir = folder(type);
    const names = fs.readdirSync(dir).filter((n) => IMAGE.test(n)).sort((a, b) => a.localeCompare(b));
    out[type] = names.map((name) => ({
      name,
      label: name.replace(IMAGE, ''),
      thumbnail: thumbnail(type, path.join(dir, name)),
      equipped: equipped[type] === name
    }));
  }
  return out;
}

function equip(type, name) {
  folder(type); // validates type
  const equipped = readEquipped();
  if (name !== null && !fs.existsSync(path.join(folder(type), path.basename(name)))) {
    throw new Error(`${name} isn't in the ${TYPES[type]} folder any more.`);
  }
  equipped[type] = name === null ? null : path.basename(name);
  writeEquipped(equipped);
  return equipped;
}

/** Copies picked image files into the folder for `type`, avoiding name clashes. Returns the new names. */
function importFiles(type, files) {
  const dir = folder(type);
  fs.mkdirSync(dir, { recursive: true });
  const added = [];
  for (const file of files) {
    if (!IMAGE.test(file)) continue;
    const ext = path.extname(file).toLowerCase();
    const base = path.basename(file, path.extname(file)).replace(/[<>:"/\\|?*]/g, '_').slice(0, 60) || 'cosmetic';
    let name = base + ext;
    for (let i = 2; fs.existsSync(path.join(dir, name)); i++) name = `${base} (${i})${ext}`;
    fs.copyFileSync(file, path.join(dir, name));
    added.push(name);
  }
  return added;
}

async function remove(type, name) {
  const file = path.join(folder(type), path.basename(name));
  await shell.trashItem(file);
  const equipped = readEquipped();
  if (equipped[type] === path.basename(name)) {
    equipped[type] = null;
    writeEquipped(equipped);
  }
}

function openFolder(type) {
  const dir = type ? folder(type) : root();
  fs.mkdirSync(dir, { recursive: true });
  return shell.openPath(dir);
}

module.exports = { root, list, equip, importFiles, remove, openFolder, seedDefaults };
