'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { can } = require('./Ranks');

const TYPES = ['cape', 'wings'];
const RARITIES = ['common', 'rare', 'epic', 'legendary'];
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;

/** Detects PNG/JPEG from the file's first bytes (never trust the extension). */
function sniffImage(buf) {
  if (buf.length > 8 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return 'png';
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'jpg';
  return null;
}

/** Reads width/height from a PNG header (JPEGs report null; the client handles any size). */
function pngSize(buf) {
  if (sniffImage(buf) !== 'png' || buf.length < 24) return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

/**
 * The cosmetics catalog. Images are stored as uploaded in data/cosmetics/<id>.<ext>; the client
 * turns any picture into a cape or a wing pair itself, so the server keeps the originals.
 */
class CosmeticService {
  constructor(store, dataDir) {
    this.store = store;
    this.dir = path.join(dataDir, 'cosmetics');
    fs.mkdirSync(this.dir, { recursive: true });
  }

  all({ includeHidden = false } = {}) {
    return Object.values(this.store.cosmetics)
      .filter((c) => includeHidden || !c.hidden)
      .sort((a, b) => RARITIES.indexOf(b.rarity) - RARITIES.indexOf(a.rarity) || a.name.localeCompare(b.name));
  }

  get(id) {
    return this.store.cosmetics[id] || null;
  }

  summary(id) {
    const c = this.get(id);
    return c ? { id: c.id, name: c.name, type: c.type, rarity: c.rarity } : null;
  }

  publicView(c) {
    return {
      id: c.id,
      name: c.name,
      type: c.type,
      rarity: c.rarity,
      free: Boolean(c.free),
      hidden: Boolean(c.hidden),
      image: `/api/cosmetics/${c.id}/image`,
      owners: this.ownerCount(c),
      createdAt: c.createdAt
    };
  }

  ownerCount(c) {
    if (c.free) return Object.keys(this.store.players).length;
    return Object.values(this.store.players).filter((p) => p.owned.includes(c.id)).length;
  }

  /** Everything a player can wear: free cosmetics plus what they were given. */
  ownedBy(player) {
    const everything = this.hasAll(player);
    return this.all({ includeHidden: true }).filter((c) => everything || c.free || player.owned.includes(c.id));
  }

  owns(player, id) {
    const c = this.get(id);
    return Boolean(c) && (this.hasAll(player) || c.free || player.owned.includes(id));
  }

  /** Owners and Admins (anyone who can manage cosmetics) can wear every cosmetic. */
  hasAll(player) {
    return can(player, 'cosmetics.manage');
  }

  imagePath(c) {
    return path.join(this.dir, c.file);
  }

  /** Validates and stores a new cosmetic. `image` is a Buffer of a PNG or JPEG. */
  create({ name, type, rarity = 'common', free = false, image, createdBy = null }) {
    name = String(name || '').trim().slice(0, 40);
    if (!name) throw new Error('Give the cosmetic a name.');
    if (!TYPES.includes(type)) throw new Error('Type must be "cape" or "wings".');
    if (!RARITIES.includes(rarity)) throw new Error(`Rarity must be one of: ${RARITIES.join(', ')}.`);
    if (!Buffer.isBuffer(image) || !image.length) throw new Error('Choose an image.');
    if (image.length > MAX_IMAGE_BYTES) throw new Error('Images must be 4 MB or smaller.');
    const ext = sniffImage(image);
    if (!ext) throw new Error('Images must be PNG or JPEG.');

    const id = crypto.randomBytes(5).toString('hex');
    const file = `${id}.${ext}`;
    fs.writeFileSync(path.join(this.dir, file), image);
    const c = {
      id, name, type, rarity, free: Boolean(free), hidden: false, file,
      size: pngSize(image), createdBy, createdAt: new Date().toISOString()
    };
    this.store.cosmetics[id] = c;
    this.store.save();
    return c;
  }

  update(id, patch) {
    const c = this.get(id);
    if (!c) throw new Error('That cosmetic no longer exists.');
    if (patch.name !== undefined) {
      const name = String(patch.name).trim().slice(0, 40);
      if (!name) throw new Error('The name can\'t be empty.');
      c.name = name;
    }
    if (patch.rarity !== undefined) {
      if (!RARITIES.includes(patch.rarity)) throw new Error('Unknown rarity.');
      c.rarity = patch.rarity;
    }
    if (patch.free !== undefined) c.free = Boolean(patch.free);
    if (patch.hidden !== undefined) c.hidden = Boolean(patch.hidden);
    this.store.save();
    return c;
  }

  /** Deletes a cosmetic, removes it from every player and unequips it. */
  remove(id) {
    const c = this.get(id);
    if (!c) return;
    for (const p of Object.values(this.store.players)) {
      p.owned = p.owned.filter((x) => x !== id);
      if (p.equipped.cape === id) p.equipped.cape = null;
      if (p.equipped.wings === id) p.equipped.wings = null;
    }
    delete this.store.cosmetics[id];
    fs.rmSync(this.imagePath(c), { force: true });
    this.store.save();
  }

  grant(player, id) {
    if (!this.get(id)) throw new Error('That cosmetic no longer exists.');
    if (!player.owned.includes(id)) player.owned.push(id);
    this.store.save();
  }

  revoke(player, id) {
    player.owned = player.owned.filter((x) => x !== id);
    const c = this.get(id);
    if (c && !c.free) {
      if (player.equipped.cape === id) player.equipped.cape = null;
      if (player.equipped.wings === id) player.equipped.wings = null;
    }
    this.store.save();
  }

  /** Equips (or with id null, unequips) a cosmetic of `type` for a player who owns it. */
  equip(player, type, id) {
    if (!TYPES.includes(type)) throw new Error('Type must be "cape" or "wings".');
    if (id !== null) {
      const c = this.get(id);
      if (!c || c.type !== type) throw new Error('That cosmetic doesn\'t exist.');
      if (!this.owns(player, id)) throw new Error('You don\'t own that cosmetic.');
    }
    player.equipped[type] = id;
    this.store.save();
    return player.equipped;
  }

  /** Imports the starter cosmetics from seed/ the first time the server runs. */
  seed(seedDir) {
    if (Object.keys(this.store.cosmetics).length || !fs.existsSync(path.join(seedDir, 'seed.json'))) return 0;
    const items = JSON.parse(fs.readFileSync(path.join(seedDir, 'seed.json'), 'utf8'));
    for (const item of items) {
      const image = fs.readFileSync(path.join(seedDir, item.file));
      this.create({ ...item, image });
    }
    return items.length;
  }
}

module.exports = { CosmeticService, TYPES, RARITIES, sniffImage };
