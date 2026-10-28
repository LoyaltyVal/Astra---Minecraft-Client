'use strict';

const { WebSocketConnection } = require('./WebSocket');

const PATH = '/api/live';
const PING_MS = 25_000;      // Cloudflare drops idle connections after 100 s
const DEAD_MS = 70_000;
const MAX_PER_PLAYER = 6;    // game + app + a few browser tabs

/**
 * Live updates over a WebSocket at /api/live: new messages, friend list changes and emotes arrive
 * instantly instead of being polled. Clients only listen; everything they do still goes through
 * the normal HTTP API.
 *
 * Sign-in: the game and app send their session token as the WebSocket sub-protocol
 * "astra-auth.<token>" (browsers can't set headers on WebSockets); the website uses its cookie,
 * and then the Origin must be this site.
 *
 * Messages sent to clients (JSON):
 *   { type: 'hello', uuid }
 *   { type: 'message', message: { id, from, to, text, at, fromName, toName } }
 *   { type: 'friends' }                       the friend list or requests changed: reload them
 *   { type: 'emote', uuid, id }               someone started an emote
 */
class LiveHub {
  constructor(ctx) {
    this.ctx = ctx;
    this.byPlayer = new Map(); // uuid -> Set<WebSocketConnection>
    this.timer = setInterval(() => this.heartbeat(), PING_MS);
    this.timer.unref();
  }

  /** Hooked to the HTTP server's 'upgrade' event. */
  handleUpgrade(req, socket) {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname !== PATH) return WebSocketConnection.reject(socket, 404, 'Not Found');
    const offered = String(req.headers['sec-websocket-protocol'] || '').split(',').map((s) => s.trim()).filter(Boolean);
    const authProto = offered.find((p) => p.startsWith('astra-auth.'));
    if (authProto) req.headers.authorization = `Bearer ${authProto.slice('astra-auth.'.length)}`;
    const player = this.ctx.auth.playerFor(req);
    if (!player) return WebSocketConnection.reject(socket, 401, 'Unauthorized');
    if (req.viaCookie && !this.sameOrigin(req)) return WebSocketConnection.reject(socket, 403, 'Forbidden');
    const set = this.byPlayer.get(player.uuid) || new Set();
    if (set.size >= MAX_PER_PLAYER) {
      // Drop the oldest so a reconnecting client is never locked out.
      const oldest = set.values().next().value;
      oldest.close(1008);
    }
    if (!WebSocketConnection.accept(req, socket, offered.includes('astra') ? 'astra' : null)) return;
    const conn = new WebSocketConnection(socket, {
      onText: (text) => {
        if (text === 'ping') conn.send('pong');
      },
      onClose: (c) => this.remove(player.uuid, c)
    });
    set.add(conn);
    this.byPlayer.set(player.uuid, set);
    conn.send({ type: 'hello', uuid: player.uuid });
  }

  sameOrigin(req) {
    try {
      return new URL(req.headers.origin).host === req.headers.host;
    } catch {
      return false;
    }
  }

  remove(uuid, conn) {
    const set = this.byPlayer.get(uuid);
    if (!set) return;
    set.delete(conn);
    if (!set.size) this.byPlayer.delete(uuid);
  }

  heartbeat() {
    const now = Date.now();
    for (const set of this.byPlayer.values()) {
      for (const conn of set) {
        if (now - conn.lastSeen > DEAD_MS) conn.closed();
        else conn.ping();
      }
    }
  }

  isConnected(uuid) {
    return this.byPlayer.has(uuid);
  }

  connectionCount() {
    let n = 0;
    for (const set of this.byPlayer.values()) n += set.size;
    return n;
  }

  // ---------- sending ----------

  sendTo(uuid, value) {
    const set = this.byPlayer.get(uuid);
    if (!set) return;
    const text = JSON.stringify(value);
    for (const conn of set) conn.send(text);
  }

  /** Tells these players to reload their friend lists. */
  friendsChanged(...uuids) {
    for (const u of new Set(uuids)) this.sendTo(u, { type: 'friends' });
  }

  /** A player's status changed (came online, switched server): their friends' lists update. */
  presenceChanged(player) {
    for (const u of player.friends || []) this.sendTo(u, { type: 'friends' });
  }

  message(msg) {
    this.sendTo(msg.to, { type: 'message', message: msg });
    this.sendTo(msg.from, { type: 'message', message: msg });
  }

  /** Emotes go to everyone connected; each game shows it if that player is nearby. */
  emote(player, id) {
    const text = JSON.stringify({ type: 'emote', uuid: player.uuid, id });
    for (const set of this.byPlayer.values()) {
      for (const conn of set) conn.send(text);
    }
  }

  close() {
    clearInterval(this.timer);
    for (const set of this.byPlayer.values()) {
      for (const conn of set) conn.close(1001);
    }
    this.byPlayer.clear();
  }
}

module.exports = { LiveHub, PATH };
