'use strict';

const { RANKS, rankOf } = require('./Ranks');

const UUID_RE = /^[0-9a-f]{32}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const ONLINE_MS = 3 * 60 * 1000;

/** Minecraft UUIDs are stored without dashes, lower case. */
function normalizeUuid(value) {
  const u = String(value || '').toLowerCase().replace(/-/g, '');
  return UUID_RE.test(u) ? u : null;
}

function dashed(uuid) {
  return `${uuid.slice(0, 8)}-${uuid.slice(8, 12)}-${uuid.slice(12, 16)}-${uuid.slice(16, 20)}-${uuid.slice(20)}`;
}

/** Players: Minecraft identity, rank, owned and equipped cosmetics. */
const PRESENCE_MS = 75 * 1000;

/**
 * Normalises a server address for display: lowercase hostname without the default port. Raw IP
 * addresses and local/LAN hosts are shown as "a private server" so nobody's IP ends up on the site.
 */
function cleanServer(server) {
  if (server === null || server === undefined || server === '') return null;
  const s = String(server).trim().toLowerCase().slice(0, 100);
  if (s === 'singleplayer') return 'singleplayer';
  const host = s.replace(/:25565$/, '');
  const bare = host.replace(/:\d+$/, '');
  if (!/^[a-z0-9.-]+(:\d{1,5})?$/.test(host)) return 'a private server';
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(bare) || bare === 'localhost' || !bare.includes('.') || /\.(local|lan|internal)$/.test(bare)) {
    return 'a private server';
  }
  return host;
}

class PlayerService {
  constructor(store, config) {
    this.store = store;
    this.owners = (config.owners || []).map((o) => String(o).toLowerCase().replace(/-/g, ''));
  }

  get(uuid) {
    const u = normalizeUuid(uuid);
    return u ? this.store.players[u] || null : null;
  }

  byName(name) {
    const lower = String(name || '').toLowerCase();
    return Object.values(this.store.players).find((p) => p.name.toLowerCase() === lower) || null;
  }

  /** Creates or updates a player after a verified sign-in. Config owners become Owner automatically. */
  upsert(uuid, name) {
    const u = normalizeUuid(uuid);
    if (!u || !NAME_RE.test(name)) throw new Error('Invalid Minecraft profile.');
    const now = new Date().toISOString();
    let p = this.store.players[u];
    if (!p) {
      p = { uuid: u, name, rank: 'player', joinedAt: now, lastSeen: now, owned: [], equipped: { cape: null, wings: null }, banned: false };
      this.store.players[u] = p;
    }
    p.name = name;
    p.lastSeen = now;
    const firstTime = !p.joinedAt || p.joinedAt === now;
    if (!p.joinedAt) p.joinedAt = now;
    if (firstTime && this.onJoin) this.onJoin(p);
    if (this.owners.includes(u) || this.owners.includes(name.toLowerCase())) p.rank = 'owner';
    this.store.save();
    return p;
  }

  /** Adds a player who hasn't signed in yet (by their Mojang profile), so staff can set them up. */
  addPlaceholder(uuid, name) {
    const u = normalizeUuid(uuid);
    if (this.store.players[u]) return this.store.players[u];
    const p = { uuid: u, name, rank: 'player', joinedAt: null, lastSeen: null, owned: [], equipped: { cape: null, wings: null }, banned: false };
    this.store.players[u] = p;
    this.store.save();
    return p;
  }

  touch(player) {
    player.lastSeen = new Date().toISOString();
    this.store.save();
  }

  /**
   * What the game reports every 30 seconds: the server you're on ("singleplayer", a hostname, or
   * null in the menus) and the game version. `hidden` = the player turned off "Share My Server".
   */
  setPresence(player, { server, version, hidden }) {
    player.presence = {
      server: cleanServer(server),
      version: /^1\.\d+(\.\d+)?$/.test(String(version || '')) ? String(version) : null,
      hidden: Boolean(hidden),
      at: new Date().toISOString()
    };
    player.lastSeen = player.presence.at;
    this.store.save();
  }

  /** { server, version } while the player is in game (and sharing it), else null. */
  playing(player) {
    const pr = player.presence;
    if (!pr || !pr.at || Date.now() - Date.parse(pr.at) > PRESENCE_MS) return null;
    if (!pr.version) return null;
    return { server: pr.hidden ? null : pr.server, version: pr.version, hidden: pr.hidden };
  }

  /** Everyone in game right now, staff first. */
  inGame() {
    return Object.values(this.store.players)
      .filter((p) => !p.banned && this.playing(p))
      .sort((a, b) => rankOf(b).level - rankOf(a).level || a.name.localeCompare(b.name));
  }

  isOnline(player) {
    return Boolean(player.lastSeen) && Date.now() - Date.parse(player.lastSeen) < ONLINE_MS;
  }

  ownerCount() {
    return Object.values(this.store.players).filter((p) => p.rank === 'owner').length;
  }

  search(query, limit = 50) {
    const q = String(query || '').toLowerCase();
    return Object.values(this.store.players)
      .filter((p) => !q || p.name.toLowerCase().includes(q) || p.uuid.startsWith(q.replace(/-/g, '')))
      .sort((a, b) => rankOf(b).level - rankOf(a).level || String(b.lastSeen).localeCompare(String(a.lastSeen)))
      .slice(0, limit);
  }

  /** Everyone with a rank above Player (shown on the Team page). */
  staff() {
    return Object.values(this.store.players)
      .filter((p) => rankOf(p).level > 0 && !p.banned)
      .sort((a, b) => rankOf(b).level - rankOf(a).level || a.name.localeCompare(b.name));
  }

  count() {
    return Object.keys(this.store.players).length;
  }

  onlineCount() {
    return Object.values(this.store.players).filter((p) => this.isOnline(p)).length;
  }

  /** The public view of a player (safe to show anyone). */
  publicView(p, cosmetics) {
    const rank = rankOf(p);
    return {
      uuid: p.uuid,
      name: p.name,
      rank: { id: rank.id, name: rank.name, color: rank.color, mc: rank.mc, staff: rank.staff },
      online: this.isOnline(p),
      joinedAt: p.joinedAt,
      equipped: {
        cape: p.equipped.cape ? cosmetics.summary(p.equipped.cape) : null,
        wings: p.equipped.wings ? cosmetics.summary(p.equipped.wings) : null
      },
      ownedCount: cosmetics.ownedBy(p).length,
      playing: this.playing(p),
      banned: Boolean(p.banned)
    };
  }
}

module.exports = { cleanServer, PlayerService, normalizeUuid, dashed, NAME_RE, RANKS };
