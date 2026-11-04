'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { can } = require('./Ranks');

const TYPES = ['cape', 'wings'];
const RARITIES = ['common', 'rare', 'epic', 'legendary'];
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
const MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
const MAX_PENDING = 3;    // waiting for review, per player
const MAX_APPROVED = 10;  // approved uploads a player can keep

/** Uploads waiting for review (or rejected) can't be worn or listed. */
const live = (c) => !c.status || c.status === 'approved';

/** Detects PNG/JPEG/GIF from the file's first bytes (never trust the extension). */
function sniffImage(buf) {
  if (buf.length > 8 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return 'png';
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'jpg';
  if (buf.length > 13 && buf.toString('latin1', 0, 6).match(/^GIF8[79]a$/)) return 'gif';
  return null;
}

/** Width/height from a PNG or GIF header (JPEGs report null; the client handles any size). */
function pngSize(buf) {
  const kind = sniffImage(buf);
  if (kind === 'png' && buf.length >= 24) return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
  if (kind === 'gif') return { width: buf.readUInt16LE(6), height: buf.readUInt16LE(8) };
  return null;
}

/**
 * Counts the frames in a GIF by walking its blocks (stops at 2: all we need is "animated or not").
 * Returns 0 for a broken file.
 */
function gifFrames(buf) {
  try {
    let p = 13;
    const flags = buf[10];
    if (flags & 0x80) p += 3 * (1 << ((flags & 7) + 1)); // global colour table
    let frames = 0;
    const skipSubBlocks = () => {
      while (p < buf.length && buf[p] !== 0) p += buf[p] + 1;
      p += 1;
    };
    while (p < buf.length) {
      const b = buf[p];
      if (b === 0x3b) break; // trailer
      if (b === 0x21) { p += 2; skipSubBlocks(); continue; } // extension
      if (b !== 0x2c) return 0;
      frames += 1;
      if (frames >= 2) return frames;
      const lflags = buf[p + 9];
      p += 10;
      if (lflags & 0x80) p += 3 * (1 << ((lflags & 7) + 1)); // local colour table
      p += 1; // LZW minimum code size
      skipSubBlocks();
    }
    return frames;
  } catch {
    return 0;
  }
}

/**
 * Whether a cape image plays as an animation in game: an animated GIF, or a "strip" PNG of
 * several 2:1 cape templates stacked top to bottom.
 */
function isAnimated(buf, type) {
  const kind = sniffImage(buf);
  if (kind === 'gif') return gifFrames(buf) >= 2;
  const size = pngSize(buf);
  if (type !== 'cape' || kind !== 'png' || !size) return false;
  const frameH = size.width / 2;
  return size.width % 64 === 0 && size.height % frameH === 0 && size.height / frameH >= 2;
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
      .filter((c) => live(c) && (includeHidden || !c.hidden))
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
      animated: Boolean(c.animated),
      owners: this.ownerCount(c),
      uploadedBy: c.uploadedBy || null,
      createdAt: c.createdAt
    };
  }

  // ---------- player uploads ----------

  /** What a player sees about their own upload (and staff in the review queue). */
  uploadView(c) {
    return {
      id: c.id,
      name: c.name,
      type: c.type,
      status: c.status,
      reason: c.reason || null,
      image: c.status === 'rejected' ? null : `/api/cosmetics/${c.id}/image`,
      uploadedBy: c.uploadedBy,
      createdAt: c.createdAt,
      reviewedAt: c.reviewedAt || null
    };
  }

  uploadsOf(player) {
    return Object.values(this.store.cosmetics)
      .filter((c) => c.uploadedBy === player.uuid)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  pendingUploads() {
    return Object.values(this.store.cosmetics)
      .filter((c) => c.status === 'pending')
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  }

  /** A player's own cape or wings, hidden until staff approve it. */
  submit(player, { name, type, image }) {
    const mine = this.uploadsOf(player);
    if (mine.filter((c) => c.status === 'pending').length >= MAX_PENDING) {
      throw new Error(`You already have ${MAX_PENDING} uploads waiting for review. Wait for staff to check them.`);
    }
    if (mine.filter((c) => c.status === 'approved').length >= MAX_APPROVED) {
      throw new Error(`You can keep up to ${MAX_APPROVED} of your own cosmetics. Delete one first.`);
    }
    if (Buffer.isBuffer(image) && image.length > MAX_UPLOAD_BYTES) throw new Error('Images must be 2 MB or smaller.');
    const c = this.create({ name, type, rarity: 'common', image, createdBy: player.uuid });
    c.status = 'pending';
    c.hidden = true;
    c.uploadedBy = player.uuid;
    this.store.save();
    return c;
  }

  approve(id, reviewer, player) {
    const c = this.get(id);
    if (!c || c.status !== 'pending') throw new Error('That upload isn\'t waiting for review.');
    c.status = 'approved';
    c.reviewedBy = reviewer.uuid;
    c.reviewedAt = new Date().toISOString();
    if (player && !player.owned.includes(id)) player.owned.push(id);
    // Wear it straight away if nothing of that type is on.
    if (player && !player.equipped[c.type]) player.equipped[c.type] = id;
    this.store.save();
    return c;
  }

  /** Rejected images are deleted at once; the entry stays so the player sees why. */
  reject(id, reviewer, reason) {
    const c = this.get(id);
    if (!c || c.status !== 'pending') throw new Error('That upload isn\'t waiting for review.');
    c.status = 'rejected';
    c.reason = String(reason || '').replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, 200) || null;
    c.reviewedBy = reviewer.uuid;
    c.reviewedAt = new Date().toISOString();
    fs.rmSync(this.imagePath(c), { force: true });
    this.store.save();
    return c;
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
    return Boolean(c) && live(c) && (this.hasAll(player) || c.free || player.owned.includes(id));
  }

  /** Owners and Admins (anyone who can manage cosmetics) can wear every cosmetic. */
  hasAll(player) {
    return can(player, 'cosmetics.manage');
  }

  imagePath(c) {
    return path.join(this.dir, c.file);
  }

  /** Validates and stores a new cosmetic. `image` is a Buffer of a PNG, JPEG or GIF. */
  create({ name, type, rarity = 'common', free = false, image, createdBy = null }) {
    name = String(name || '').trim().slice(0, 40);
    if (!name) throw new Error('Give the cosmetic a name.');
    if (!TYPES.includes(type)) throw new Error('Type must be "cape" or "wings".');
    if (!RARITIES.includes(rarity)) throw new Error(`Rarity must be one of: ${RARITIES.join(', ')}.`);
    if (!Buffer.isBuffer(image) || !image.length) throw new Error('Choose an image.');
    if (image.length > MAX_IMAGE_BYTES) throw new Error('Images must be 4 MB or smaller.');
    const ext = sniffImage(image);
    if (!ext) throw new Error('Images must be PNG, JPEG or GIF.');
    if (ext === 'gif' && !gifFrames(image)) throw new Error('That GIF looks broken. Try saving it again.');

    const id = crypto.randomBytes(5).toString('hex');
    const file = `${id}.${ext}`;
    fs.writeFileSync(path.join(this.dir, file), image);
    const c = {
      id, name, type, rarity, free: Boolean(free), hidden: false, file,
      size: pngSize(image), animated: isAnimated(image, type), createdBy, createdAt: new Date().toISOString()
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

module.exports = { CosmeticService, TYPES, RARITIES, sniffImage, gifFrames, isAnimated, MAX_PENDING, MAX_APPROVED };
